package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.providers.DefaultInputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.InterruptedIOException
import java.util.Random
import kotlin.math.abs

/**
 * Answers every request with the contents of a local JSON file instead of hitting the network.
 *
 * The file is chosen from the request URL: `/api/v1/users?page=1` is looked up as
 * `api/v1/users/page=1.json` and then as `api/v1/users.json`. Host, headers and body are ignored.
 * A trailing slash is ignored and the root path `/` is looked up as `index.json`.
 * The file is returned byte for byte and is expected to be UTF-8.
 * When no file matches, the answer is a 404 whose body lists the files that were tried.
 *
 * @param inputStreamProvider where mock files are read from.
 * @param failurePercentage chance, from 0 to 100, of answering with a 504 and an empty body.
 * @param basePath prefix added to every resolved file path.
 * @param minDelayMilliseconds lower bound of the simulated latency.
 * @param maxDelayMilliseconds upper bound, exclusive, of the simulated latency. Equal bounds give a fixed latency.
 */
public class OkHttpMockInterceptor(
    private val inputStreamProvider: InputStreamProvider,
    failurePercentage: Int,
    basePath: String,
    minDelayMilliseconds: Int,
    maxDelayMilliseconds: Int,
) : Interceptor {

    /** Chance, from 0 to 100, of answering with a failure status. */
    public var failurePercentage: Int = failurePercentage
        private set

    /** Prefix added to every resolved file path. */
    public var basePath: String = basePath
        private set

    /** Lower bound of the simulated latency. */
    public var minDelayMilliseconds: Int = minDelayMilliseconds
        private set

    /** Upper bound, exclusive, of the simulated latency. */
    public var maxDelayMilliseconds: Int = maxDelayMilliseconds
        private set

    init {
        require(failurePercentage in 0..100) { "failurePercentage must be between 0 and 100: $failurePercentage" }
        require(minDelayMilliseconds >= 0) { "minDelayMilliseconds must not be negative: $minDelayMilliseconds" }
        require(maxDelayMilliseconds >= minDelayMilliseconds) {
            "maxDelayMilliseconds ($maxDelayMilliseconds) must not be lower than " +
                "minDelayMilliseconds ($minDelayMilliseconds)"
        }
    }

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
        val candidates = candidatePaths(request.url)
        val mock = candidates.firstNotNullOfOrNull(::readMock)
        try {
            Thread.sleep(nextDelayMilliseconds())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted while simulating the network delay").apply { initCause(e) }
        }
        if (mock == null) {
            print("JsonMockServer: No mock found for ${request.url.encodedPath}\t\tStatusCode : $HTTP_NOT_FOUND")
            val explanation = "No mock file found for ${request.method} ${request.url.encodedPath}. " +
                "Tried: ${candidates.joinToString()}"
            return response(request, HTTP_NOT_FOUND, explanation.toByteArray(), CONTENT_TYPE_TEXT)
        }
        val failure = abs(Random().nextInt() % 100) < failurePercentage
        if (failure) {
            print("JsonMockServer: Simulated failure for ${candidates.first()}\t\tStatusCode : $HTTP_GATEWAY_TIMEOUT")
            return response(request, HTTP_GATEWAY_TIMEOUT, ByteArray(0), CONTENT_TYPE_JSON)
        }
        print("JsonMockServer: Returning result from ${candidates.first()}\t\tStatusCode : $HTTP_OK")
        return response(request, HTTP_OK, mock, CONTENT_TYPE_JSON)
    }

    /** Files that can answer [url], most specific first. */
    private fun candidatePaths(url: HttpUrl): List<String> {
        val path = url.encodedPath.trim('/').ifEmpty { ROOT_FILE_NAME }
        val query = url.encodedQuery.orEmpty()
        val queryFile = if (query.isEmpty()) null else "$basePath$path/$query.json"
        return listOfNotNull(queryFile, "$basePath$path.json")
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
        val jitter = if (window > 0) Random().nextInt(window) else 0
        return (minDelayMilliseconds + jitter).toLong()
    }

    /** Changes [failurePercentage]. */
    public fun setFailurePercentage(failurePercentage: Int): OkHttpMockInterceptor = apply {
        this.failurePercentage = failurePercentage
    }

    /** Changes [basePath]. */
    public fun setBasePath(basePath: String): OkHttpMockInterceptor = apply {
        this.basePath = basePath
    }

    /** Changes [minDelayMilliseconds]. */
    public fun setMinDelayMilliseconds(minDelayMilliseconds: Int): OkHttpMockInterceptor = apply {
        this.minDelayMilliseconds = minDelayMilliseconds
    }

    /** Changes [maxDelayMilliseconds]. */
    public fun setMaxDelayMilliseconds(maxDelayMilliseconds: Int): OkHttpMockInterceptor = apply {
        this.maxDelayMilliseconds = maxDelayMilliseconds
    }

    private companion object {
        const val DEFAULT_BASE_PATH = ""
        const val DELAY_DEFAULT_MIN = 500
        const val DELAY_DEFAULT_MAX = 1500
        const val ROOT_FILE_NAME = "index"
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GATEWAY_TIMEOUT = 504
        const val CONTENT_TYPE_JSON = "application/json"
        const val CONTENT_TYPE_TEXT = "text/plain"
    }
}
