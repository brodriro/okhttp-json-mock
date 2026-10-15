package io.github.brodriro.okhttpjsonmock

import okhttp3.HttpUrl

/**
 * A request the interceptor received, kept when it was built with
 * [OkHttpMockInterceptor.Builder.recordCalls].
 *
 * @property method HTTP method of the request.
 * @property url URL of the request.
 * @property mockFile the mock file that matched, or `null` when there was none.
 * @property statusCode status of the answer, or `null` when the call failed with [MissingMockException].
 */
public class RecordedCall internal constructor(
    public val method: String,
    public val url: HttpUrl,
    public val mockFile: String?,
    public val statusCode: Int?,
) {
    override fun toString(): String =
        "$method $url -> ${statusCode ?: "no mock"}" + mockFile?.let { " from $it" }.orEmpty()
}
