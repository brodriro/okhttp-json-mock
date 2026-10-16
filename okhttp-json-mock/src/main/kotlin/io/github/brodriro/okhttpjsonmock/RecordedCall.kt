package io.github.brodriro.okhttpjsonmock

import okhttp3.HttpUrl
import okio.ByteString

/**
 * A request the interceptor received, kept when it was built with
 * [OkHttpMockInterceptor.Builder.recordCalls].
 *
 * @property method HTTP method of the request.
 * @property url URL of the request.
 * @property mockFile the mock file that matched, or `null` when there was none.
 * @property statusCode status of the answer, or `null` when the call failed with [MissingMockException].
 * @property body bytes of the request body as they were sent, or `null` when the request had none. An empty body
 * is [ByteString.EMPTY]. A duplex body cannot be read without consuming the call and is also `null`. Use
 * [ByteString.utf8] to read it as text.
 */
public class RecordedCall internal constructor(
    public val method: String,
    public val url: HttpUrl,
    public val mockFile: String?,
    public val statusCode: Int?,
    public val body: ByteString?,
) {
    override fun toString(): String =
        "$method $url -> ${statusCode ?: "no mock"}" + mockFile?.let { " from $it" }.orEmpty()
}
