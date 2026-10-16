package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.providers.DefaultInputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString
import java.io.InterruptedIOException
import kotlin.random.Random

/**
 * Answers every request with the contents of a local file, JSON by default, instead of hitting the network.
 *
 * The file is chosen from the request method and URL. `GET /api/v1/users?page=1` is looked up as
 * `api/v1/users/page=1.get.json`, `api/v1/users.get.json`, `api/v1/users/page=1.json` and
 * `api/v1/users.json`, in that order. A file with a method suffix (`users.post.json`) only answers
 * that method, so one URL can have a different mock per method; a file without it answers any
 * method. Host, headers and body are ignored.
 *
 * A mock is answered as it is with status 200. To answer with another status, wrap it:
 * `{ "statusCode": 201, "response": { "id": 4 } }` answers 201 with `{"id":4}` as the body, and
 * `{ "statusCode": 204 }` answers 204 with no body. The wrapper can also carry response headers
 * in an optional `"headers": { "Location": "/users/4" }` object, and an optional `"delayMs": 2000`
 * replaces the simulated latency for that mock. A file is only treated as a wrapper when it has
 * `statusCode` and no keys other than `statusCode`, `headers`, `delayMs` and `response`, so real
 * payloads that happen to have a `statusCode` field are left alone.
 * A file or folder named `{id}` matches any value of a path segment, so `users/{id}.json` answers
 * `/users/1` and `/users/42` when there is no file for that exact path.
 * A trailing slash is ignored and the root path `/` is looked up as `index`, so `index.json` answers it.
 * The file is returned byte for byte; a wrapper is read as UTF-8.
 *
 * Mock files are `.json` unless [Builder.extensions] lists others, such as `xml`, `txt` or `png`. Each
 * file name is then tried with every extension, in that order, and the response gets the
 * `Content-Type` of the extension that matched (`application/octet-stream` for an unknown one). Only
 * `.json` files are read as wrappers; any other file is served as it is with status 200.
 * When no file matches, the answer is a 404 whose body lists the exact-path files that were tried and
 * counts the `{id}` ones.
 *
 * Every call waits before it is answered, to imitate a network: a random time between 500 and
 * 1500 ms unless another window is given. Use [Builder.noDelay] in tests.
 *
 * For tests, [Builder.failOnMissingMock] turns a missing mock into a [MissingMockException] and
 * [Builder.recordCalls] keeps the requests that were received in [recordedCalls].
 *
 * The configuration is immutable and instances are safe to share between threads. Create them with
 * [Builder] or with one of the constructors kept from 3.0.
 *
 * @param inputStreamProvider where mock files are read from.
 * @property failurePercentage chance, from 0 to 100, of answering with [failureCode] and an empty body.
 * @property basePath prefix added to every resolved file path.
 * @property minDelayMilliseconds lower bound of the simulated latency.
 * @property maxDelayMilliseconds upper bound, exclusive, of the simulated latency. Equal bounds give a fixed latency.
 * @property failureCode HTTP status of a simulated failure, 504 unless set through [Builder].
 * @property extensions extensions of mock files, in the order they are tried. Only `json` unless set through [Builder].
 */
public class OkHttpMockInterceptor private constructor(
    private val inputStreamProvider: InputStreamProvider,
    public val failurePercentage: Int,
    public val basePath: String,
    public val minDelayMilliseconds: Int,
    public val maxDelayMilliseconds: Int,
    public val failureCode: Int,
    public val extensions: List<String>,
    private val logger: MockLogger,
    private val failOnMissingMock: Boolean,
    private val recordCalls: Boolean,
) : Interceptor {

    private val resolver = MockFileResolver(basePath, extensions)
    private val calls = mutableListOf<RecordedCall>()

    /**
     * The requests received so far, oldest first. Always empty unless the interceptor was built
     * with [Builder.recordCalls]. The list is a snapshot: later calls do not change it.
     */
    public val recordedCalls: List<RecordedCall>
        get() = synchronized(calls) { calls.toList() }

    /** Forgets the requests recorded so far, for example between two tests. */
    public fun clearRecordedCalls() {
        synchronized(calls) { calls.clear() }
    }

    init {
        require(failureCode in 400..599) { "failureCode must be a 4xx or 5xx status: $failureCode" }
        require(failurePercentage in 0..100) { "failurePercentage must be between 0 and 100: $failurePercentage" }
        require(minDelayMilliseconds >= 0) { "minDelayMilliseconds must not be negative: $minDelayMilliseconds" }
        require(maxDelayMilliseconds >= minDelayMilliseconds) {
            "maxDelayMilliseconds ($maxDelayMilliseconds) must not be lower than " +
                "minDelayMilliseconds ($minDelayMilliseconds)"
        }
        require(extensions.isNotEmpty()) { "extensions must not be empty" }
        extensions.forEach { extension ->
            require(extension.matches(EXTENSION_PATTERN)) {
                "extensions must be letters and digits, without the dot: '$extension'"
            }
        }
    }

    /** Reads mocks from [inputStreamProvider] under [basePath] with a custom latency window. */
    public constructor(
        inputStreamProvider: InputStreamProvider,
        failurePercentage: Int,
        basePath: String,
        minDelayMilliseconds: Int,
        maxDelayMilliseconds: Int,
    ) : this(
        inputStreamProvider,
        failurePercentage,
        basePath,
        minDelayMilliseconds,
        maxDelayMilliseconds,
        DEFAULT_FAILURE_CODE,
        listOf(MockFileResolver.DEFAULT_EXTENSION),
        MockLogger.STDOUT,
        failOnMissingMock = false,
        recordCalls = false,
    )

    /** Reads mocks from the filesystem with the default latency of 500 to 1500 ms. */
    public constructor(failurePercentage: Int) : this(DefaultInputStreamProvider(), failurePercentage)

    /** Reads mocks from [inputStreamProvider] with the default latency of 500 to 1500 ms. */
    public constructor(inputStreamProvider: InputStreamProvider, failurePercentage: Int) : this(
        inputStreamProvider,
        failurePercentage,
        DEFAULT_BASE_PATH,
        DELAY_DEFAULT_MIN,
        DELAY_DEFAULT_MAX,
    )

    /** Reads mocks from [inputStreamProvider] with a custom latency window. */
    public constructor(
        inputStreamProvider: InputStreamProvider,
        failurePercentage: Int,
        minDelayMilliseconds: Int,
        maxDelayMilliseconds: Int,
    ) : this(
        inputStreamProvider,
        failurePercentage,
        DEFAULT_BASE_PATH,
        minDelayMilliseconds,
        maxDelayMilliseconds,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Read before anything else: the mock never calls proceed, so even a one-shot body is ours to consume.
        val body = if (recordCalls) bodyOf(request) else null
        val candidates = resolver.candidates(request.method, request.url)
        val match = candidates.firstNotNullOfOrNull { path -> readMock(path)?.let { path to it } }
        val envelope = match
            ?.takeIf { (path, _) -> extensionOf(path).equals(MockFileResolver.DEFAULT_EXTENSION, ignoreCase = true) }
            ?.let { (path, mock) -> MockEnvelope.parseOrNull(mock, path) }
        try {
            Thread.sleep(envelope?.delayMilliseconds?.toLong() ?: nextDelayMilliseconds())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted while simulating the network delay").apply { initCause(e) }
        }
        val call = "${request.method} ${request.url.encodedPath}" + request.url.encodedQuery?.let { "?$it" }.orEmpty()
        if (match == null) {
            val exact = candidates.filterNot { MockFileResolver.PLACEHOLDER in it }
            val withPlaceholder = candidates.size - exact.size
            val tried = exact.joinToString() +
                if (withPlaceholder > 0) " and $withPlaceholder more with {id} in place of a path segment" else ""
            val explanation = "No mock file found for $call. Tried: $tried"
            if (failOnMissingMock) {
                logger.log("$call -> failed, no mock found. Tried: $tried")
                record(request, body, mockFile = null, statusCode = null)
                throw MissingMockException(request.method, request.url, candidates, explanation)
            }
            logger.log("$call -> $HTTP_NOT_FOUND, no mock found. Tried: $tried")
            record(request, body, mockFile = null, statusCode = HTTP_NOT_FOUND)
            return response(request, HTTP_NOT_FOUND, explanation.toByteArray(), CONTENT_TYPE_TEXT)
        }
        val (path, mock) = match
        if (Random.nextInt(100) < failurePercentage) {
            logger.log("$call -> $failureCode, simulated failure")
            record(request, body, path, failureCode)
            return response(request, failureCode, ByteArray(0), CONTENT_TYPE_JSON)
        }
        val statusCode = envelope?.statusCode ?: HTTP_OK
        logger.log("$call -> $statusCode from $path")
        record(request, body, path, statusCode)
        return response(
            request,
            statusCode,
            envelope?.body ?: mock,
            envelope?.contentType ?: ContentTypes.of(extensionOf(path)),
            envelope?.headers.orEmpty(),
        )
    }

    private fun record(request: Request, body: ByteString?, mockFile: String?, statusCode: Int?) {
        if (!recordCalls) return
        synchronized(calls) { calls += RecordedCall(request.method, request.url, mockFile, statusCode, body) }
    }

    /** The bytes of the request body, or `null` when there is none or it is duplex and cannot be buffered. */
    private fun bodyOf(request: Request): ByteString? {
        val body = request.body ?: return null
        if (body.isDuplex()) return null
        return Buffer().also(body::writeTo).readByteString()
    }

    private fun extensionOf(path: String): String = path.substringAfterLast('.')

    /** The raw bytes of the mock at [path], or `null` when the provider has no such file. */
    private fun readMock(path: String): ByteArray? = inputStreamProvider.provide(path)?.use { it.readBytes() }

    private fun response(
        request: Request,
        code: Int,
        body: ByteArray,
        contentType: String,
        headers: List<Pair<String, String>> = emptyList(),
    ): Response {
        val builder = Response.Builder()
            .code(code)
            .message(ReasonPhrases.of(code))
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .body(body.toResponseBody(contentType.toMediaTypeOrNull()))
            .addHeader("content-type", contentType)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        return builder.build()
    }

    /** A latency in `[min, max)`, or exactly `min` when both bounds are equal. */
    private fun nextDelayMilliseconds(): Long {
        val window = maxDelayMilliseconds - minDelayMilliseconds
        val jitter = if (window > 0) Random.nextInt(window) else 0
        return (minDelayMilliseconds + jitter).toLong()
    }

    /**
     * Configures an [OkHttpMockInterceptor].
     *
     * Defaults: mocks are never failed on purpose, there is no base path, mock files are `.json`, the
     * latency is between 500 and 1500 ms and each call is logged to standard output.
     *
     * @param inputStreamProvider where mock files are read from.
     */
    public class Builder(private val inputStreamProvider: InputStreamProvider) {
        private var failurePercentage = 0
        private var basePath = DEFAULT_BASE_PATH
        private var minDelayMilliseconds = DELAY_DEFAULT_MIN
        private var maxDelayMilliseconds = DELAY_DEFAULT_MAX
        private var failureCode = DEFAULT_FAILURE_CODE
        private var extensions = listOf(MockFileResolver.DEFAULT_EXTENSION)
        private var logger = MockLogger.STDOUT
        private var failOnMissingMock = false
        private var recordCalls = false

        /** Reads mocks from the filesystem. */
        public constructor() : this(DefaultInputStreamProvider())

        /** Chance, from 0 to 100, of answering with a failure instead of the mock. */
        public fun failurePercentage(failurePercentage: Int): Builder = apply {
            this.failurePercentage = failurePercentage
        }

        /** HTTP status of a simulated failure. Must be a 4xx or 5xx code; 504 by default. */
        public fun failureCode(failureCode: Int): Builder = apply { this.failureCode = failureCode }

        /**
         * Extensions of mock files, in the order they are tried for each file name; only `json` when not
         * set. The list replaces that default, so include `json` to keep finding JSON mocks:
         * `extensions("json", "xml")` answers `GET /feed` with `feed.get.json`, `feed.get.xml`,
         * `feed.json` or `feed.xml`, the first that exists. A leading dot is ignored.
         *
         * Each extension adds one lookup per file name, so list only the ones in use.
         */
        public fun extensions(vararg extensions: String): Builder = apply {
            this.extensions = extensions.map { it.removePrefix(".") }.distinct()
        }

        /** Where the one-line summary of each call goes. Standard output by default. */
        public fun logger(logger: MockLogger): Builder = apply { this.logger = logger }

        /** Prefix added to every resolved file path, for example `mocks/`. */
        public fun basePath(basePath: String): Builder = apply { this.basePath = basePath }

        /**
         * Simulated latency in `[minMilliseconds, maxMilliseconds)`. Equal bounds give a fixed latency.
         * Between 500 and 1500 ms when not set. A mock that sets `delayMs` waits that long instead.
         */
        public fun delay(minMilliseconds: Int, maxMilliseconds: Int): Builder = apply {
            minDelayMilliseconds = minMilliseconds
            maxDelayMilliseconds = maxMilliseconds
        }

        /**
         * Answers immediately instead of waiting 500 to 1500 ms, except for mocks that set `delayMs`.
         * Useful in tests.
         */
        public fun noDelay(): Builder = delay(0, 0)

        /**
         * Fails a request that has no mock file with a [MissingMockException] instead of answering
         * 404. Meant for tests, where a forgotten mock should not look like a server error.
         */
        public fun failOnMissingMock(): Builder = apply { failOnMissingMock = true }

        /**
         * Keeps every request in [OkHttpMockInterceptor.recordedCalls], body included, so a test can
         * check what was called and what was sent. Off by default, because the list grows for as long
         * as the interceptor is used.
         */
        public fun recordCalls(): Builder = apply { recordCalls = true }

        /** Creates the interceptor, validating the configuration. */
        public fun build(): OkHttpMockInterceptor = OkHttpMockInterceptor(
            inputStreamProvider,
            failurePercentage,
            basePath,
            minDelayMilliseconds,
            maxDelayMilliseconds,
            failureCode,
            extensions,
            logger,
            failOnMissingMock,
            recordCalls,
        )
    }

    private companion object {
        const val DEFAULT_BASE_PATH = ""
        const val DELAY_DEFAULT_MIN = 500
        const val DELAY_DEFAULT_MAX = 1500
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        const val DEFAULT_FAILURE_CODE = 504
        const val CONTENT_TYPE_JSON = "application/json"
        const val CONTENT_TYPE_TEXT = "text/plain"
        val EXTENSION_PATTERN = Regex("[A-Za-z0-9]+")
    }
}
