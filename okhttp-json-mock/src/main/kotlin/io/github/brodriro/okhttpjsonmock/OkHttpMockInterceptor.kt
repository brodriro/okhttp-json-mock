package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.helpers.ResourcesHelper
import io.github.brodriro.okhttpjsonmock.providers.DefaultInputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.Random
import kotlin.math.abs

/**
 * Answers every request with the contents of a local JSON file instead of hitting the network.
 *
 * The file is chosen from the request URL: `/api/v1/users?page=1` is looked up as
 * `api/v1/users/page=1.json` and then as `api/v1/users.json`. Host, headers and body are ignored.
 *
 * @param inputStreamProvider where mock files are read from.
 * @param failurePercentage chance, from 0 to 100, of answering with a failure status.
 * @param basePath prefix added to every resolved file path.
 * @param minDelayMilliseconds lower bound of the simulated latency.
 * @param maxDelayMilliseconds upper bound, exclusive, of the simulated latency.
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
        val url = chain.request().url
        val query = url.encodedQuery.orEmpty()
        val separator = if (query.isEmpty()) "" else "/"
        val path = url.encodedPath + separator + query
        val responseString = ResourcesHelper.loadFileAsString(
            inputStreamProvider,
            basePath + path.substring(1) + ".json",
        ) ?: ResourcesHelper.loadFileAsString(
            inputStreamProvider,
            basePath + url.encodedPath.substring(1) + ".json",
        )
        val result = responseString.orEmpty()
        try {
            Thread.sleep(
                (abs(Random().nextInt() % (maxDelayMilliseconds - minDelayMilliseconds)) + minDelayMilliseconds)
                    .toLong(),
            )
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        val failure = abs(Random().nextInt() % 100) < failurePercentage
        val statusCode = if (failure) 504 else 200
        print("JsonMockServer: Returning result from $path\t\tStatusCode : $statusCode")

        return Response.Builder()
            .code(statusCode)
            .message(checkNotNull(responseString) { "message == null" })
            .request(chain.request())
            .protocol(Protocol.HTTP_1_0)
            .body(result.toResponseBody("application/json".toMediaType()))
            .addHeader("content-type", "application/json")
            .build()
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
    }
}
