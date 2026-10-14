package io.github.brodriro.okhttpjsonmock

import okhttp3.HttpUrl
import java.io.IOException

/**
 * Thrown for a request that has no mock file when the interceptor was built with
 * [OkHttpMockInterceptor.Builder.failOnMissingMock].
 *
 * It is an [IOException], so OkHttp delivers it to the caller like any failed call, also for
 * asynchronous calls.
 *
 * @property method HTTP method of the request.
 * @property url URL of the request.
 * @property triedFiles every file that was looked up, in order.
 */
public class MissingMockException(
    public val method: String,
    public val url: HttpUrl,
    public val triedFiles: List<String>,
    message: String,
) : IOException(message)
