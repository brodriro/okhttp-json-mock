package io.github.brodriro.okhttpjsonmock.sample.mock

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException

/**
 * A request of the catalog and what the interceptor answered.
 *
 * @property statusCode `null` when the call failed instead of answering, see [error].
 * @property mockFile the file that answered, from [io.github.brodriro.okhttpjsonmock.RecordedCall.mockFile].
 * @property log the lines the interceptor logged through its `MockLogger`.
 */
class Exchange(
    val request: SampleRequest,
    val statusCode: Int?,
    val reason: String?,
    val contentType: String?,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
    val preview: BodyPreview,
    val mockFile: String?,
    val elapsedMillis: Long,
    val error: String?,
    val log: List<String>,
)

/** How a response body is shown: decoded with the Android API that matches its `Content-Type`. */
sealed interface BodyPreview {
    data object Empty : BodyPreview

    class Image(val bitmap: ImageBitmap, val width: Int, val height: Int) : BodyPreview

    class Pdf(val firstPage: ImageBitmap, val pageCount: Int) : BodyPreview

    class Text(val text: String) : BodyPreview

    class Binary(val size: Int, val hex: String) : BodyPreview

    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create()

        fun of(bytes: ByteArray, contentType: String?): BodyPreview {
            if (bytes.isEmpty()) return Empty
            val type = contentType?.substringBefore(';')?.trim().orEmpty()
            return when {
                type.startsWith("image/") && type != "image/svg+xml" -> image(bytes) ?: binary(bytes)
                type == "application/pdf" -> pdf(bytes) ?: binary(bytes)
                type == "application/json" || type.endsWith("+json") -> Text(prettyJson(bytes.decodeToString()))
                type.startsWith("text/") || type.endsWith("xml") -> Text(bytes.decodeToString())
                else -> binary(bytes)
            }
        }

        private fun image(bytes: ByteArray): Image? {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            return Image(bitmap.asImageBitmap(), bitmap.width, bitmap.height)
        }

        private fun pdf(bytes: ByteArray): Pdf? {
            val file = File.createTempFile("mock", ".pdf")
            return try {
                file.writeBytes(bytes)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            Pdf(bitmap.asImageBitmap(), renderer.pageCount)
                        }
                    }
                }
            } catch (e: IOException) {
                null
            } finally {
                file.delete()
            }
        }

        private fun prettyJson(text: String): String = try {
            gson.toJson(JsonParser.parseString(text))
        } catch (e: JsonParseException) {
            text
        }

        private fun binary(bytes: ByteArray) = Binary(
            size = bytes.size,
            hex = bytes.take(48).chunked(16).joinToString("\n") { row ->
                row.joinToString(" ") { "%02x".format(it) }
            },
        )
    }
}
