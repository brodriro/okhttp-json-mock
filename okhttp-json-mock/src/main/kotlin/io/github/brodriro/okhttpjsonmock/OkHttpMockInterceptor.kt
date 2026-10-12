package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.providers.DefaultInputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.InterruptedIOException
import kotlin.random.Random

/**
 * Answers every request with the contents of a local JSON file instead of hitting the network.
 *
 * The file is chosen from the request method and URL. `GET /api/v1/users?page=1` is looked up as
 * `api/v1/users/page=1.get.json`, `api/v1/users.get.json`, `api/v1/users/page=1.json` and
 * `api/v1/users.json`, in that order. A file with a method suffix (`users.post.json`) only answers
 * that method, so one URL can have a different mock per method; a file without it answers any
 * method. Host, headers and body are ignored.
 * A trailing slash is ignored and the root path `/` is looked up as `index.json`.
 * The file is returned byte for byte and is expected to be UTF-8.
 * When no file matches, the answer is a 404 whose body lists the files that were tried.
 *
 * Instances are immutable and safe to share between threads. Create them with [Builder] or with one
 * of the constructors kept from 3.0.
 *
 * @param inputStreamProvider where mock files are read from.
 * @property failurePercentage chance, from 0 to 100, of answering with [failureCode] and an empty body.
 * @property basePath prefix added to every resolved file path.
 * @property minDelayMilliseconds lower bound of the simulated latency.
 * @property maxDelayMilliseconds upper bound, exclusive, of the simulated latency. Equal bounds give a fixed latency.
 * @property failureCode HTTP status of a simulated failure, 504 unless set through [Builder].
 */
public class OkHttpMockInterceptor private constructor(
    private val inputStreamProvider: InputStreamProvider,
    public val failurePercentage: Int,
    public val basePath: String,
    public val minDelayMilliseconds: Int,
    public val maxDelayMilliseconds: Int,
    public val failureCode: Int,
    private val logger: MockLogger,
) : Interceptor {

    private val resolver = MockFileResolver(basePath)

    init {
        require(failureCode in 400..599) { "failureCode must be a 4xx or 5xx status: $failureCode" }
        require(failurePercentage in 0..100) { "failurePercentage must be between 0 and 100: $failurePercentage" }
        require(minDelayMilliseconds >= 0) { "minDelayMilliseconds must not be negative: $minDelayMilliseconds" }
        require(maxDelayMilliseconds >= minDelayMilliseconds) {
            "maxDelayMilliseconds ($maxDelayMilliseconds) must not be lower than " +
                "minDelayMilliseconds ($minDelayMilliseconds)"
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
        MockLogger.STDOUT,
    )

    /** Reads mocks from the filesystem with the default latency. */
    public constructor(failurePercentage: Int) : this(DefaultInputStreamProvider(), failurePercentage)

    /** Reads mocks from [inputStreamProvider] with the default latency. */
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
        val candidates = resolver.candidates(request.method, request.url)
        val match = candidates.firstNotNullOfOrNull { path -> readMock(path)?.let { path to it } }
        try {
            Thread.sleep(nextDelayMilliseconds())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted while simulating the network delay").apply { initCause(e) }
        }
        val call = "${request.method} ${request.url.encodedPath}" + request.url.encodedQuery?.let { "?$it" }.orEmpty()
        if (match == null) {
            val tried = candidates.joinToString()
            logger.log("$call -> $HTTP_NOT_FOUND, no mock found. Tried: $tried")
            val explanation = "No mock file found for $call. Tried: $tried"
            return response(request, HTTP_NOT_FOUND, explanation.toByteArray(), CONTENT_TYPE_TEXT)
        }
        val (path, mock) = match
        if (Random.nextInt(100) < failurePercentage) {
            logger.log("$call -> $failureCode, simulated failure")
            return response(request, failureCode, ByteArray(0), CONTENT_TYPE_JSON)
        }
        logger.log("$call -> $HTTP_OK from $path")
        return response(request, HTTP_OK, mock, CONTENT_TYPE_JSON)
    }

    /** The raw bytes of the mock at [path], or `null` when the provider has no such file. */
    private fun readMock(path: String): ByteArray? = inputStreamProvider.provide(path)?.use { it.readBytes() }

    private fun response(request: Request, code: Int, body: ByteArray, contentType: String): Response =
        Response.Builder()
            .code(code)
            .message(reasonPhrase(code))
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .body(body.toResponseBody(contentType.toMediaType()))
            .addHeader("content-type", contentType)
            .build()

    private fun reasonPhrase(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        422 -> "Unprocessable Entity"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        504 -> "Gateway Timeout"
        else -> "Mock Response"
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
     * Defaults: mocks are never failed on purpose, there is no base path, the latency is
     * between 500 and 1500 ms and each call is logged to standard output.
     *
     * @param inputStreamProvider where mock files are read from.
     */
    public class Builder(private val inputStreamProvider: InputStreamProvider) {
        private var failurePercentage = 0
        private var basePath = DEFAULT_BASE_PATH
        private var minDelayMilliseconds = DELAY_DEFAULT_MIN
        private var maxDelayMilliseconds = DELAY_DEFAULT_MAX
        private var failureCode = DEFAULT_FAILURE_CODE
        private var logger = MockLogger.STDOUT

        /** Reads mocks from the filesystem. */
        public constructor() : this(DefaultInputStreamProvider())

        /** Chance, from 0 to 100, of answering with a failure instead of the mock. */
        public fun failurePercentage(failurePercentage: Int): Builder = apply {
            this.failurePercentage = failurePercentage
        }

        /** HTTP status of a simulated failure. Must be a 4xx or 5xx code; 504 by default. */
        public fun failureCode(failureCode: Int): Builder = apply { this.failureCode = failureCode }

        /** Where the one-line summary of each call goes. Standard output by default. */
        public fun logger(logger: MockLogger): Builder = apply { this.logger = logger }

        /** Prefix added to every resolved file path, for example `mocks/`. */
        public fun basePath(basePath: String): Builder = apply { this.basePath = basePath }

        /** Simulated latency in `[minMilliseconds, maxMilliseconds)`. Equal bounds give a fixed latency. */
        public fun delay(minMilliseconds: Int, maxMilliseconds: Int): Builder = apply {
            minDelayMilliseconds = minMilliseconds
            maxDelayMilliseconds = maxMilliseconds
        }

        /** Answers immediately. Useful in tests. */
        public fun noDelay(): Builder = delay(0, 0)

        /** Creates the interceptor, validating the configuration. */
        public fun build(): OkHttpMockInterceptor = OkHttpMockInterceptor(
            inputStreamProvider,
            failurePercentage,
            basePath,
            minDelayMilliseconds,
            maxDelayMilliseconds,
            failureCode,
            logger,
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
    }
}
