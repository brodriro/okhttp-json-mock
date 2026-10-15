package io.github.brodriro.okhttpjsonmock.sample

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Html
import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.brodriro.okhttpjsonmock.OkHttpMockInterceptor
import io.github.brodriro.okhttpjsonmock.sample.mock.Playground
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * One test per extension of `Builder.extensions`: each file of `assets/api/v1/files` is served through the
 * interceptor, checked for its `Content-Type` and its bytes, and read with the Android API for its type.
 */
@RunWith(AndroidJUnit4::class)
class MockExtensionsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val provider = Playground(context.assets).provider

    private class Served(val contentType: String?, val charset: String?, val bytes: ByteArray) {
        val text: String get() = bytes.decodeToString()
    }

    private fun serve(file: String): Served {
        val (name, extension) = file.split('.')
        val interceptor = OkHttpMockInterceptor.Builder(provider).extensions(extension).noDelay().logger { }.build()
        val request = Request.Builder().url("https://api.example.com/api/v1/files/$name").build()
        OkHttpClient.Builder().addInterceptor(interceptor).build().newCall(request).execute().use {
            assertEquals(200, it.code)
            val body = it.body!!
            val served = Served(it.header("Content-Type"), body.contentType()?.charset()?.name(), body.bytes())
            assertArrayEquals(file, context.assets.open("api/v1/files/$file").readBytes(), served.bytes)
            return served
        }
    }

    /** Decodes with BitmapFactory, which must detect the same type as the `Content-Type`. */
    private fun assertImage(file: String, contentType: String, width: Int, height: Int) {
        val served = serve(file)
        assertEquals(contentType, served.contentType)
        val options = BitmapFactory.Options()
        val bitmap = BitmapFactory.decodeByteArray(served.bytes, 0, served.bytes.size, options)
        assertEquals(contentType, options.outMimeType)
        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)
    }

    private fun assertHtml(file: String) {
        val served = serve(file)
        assertEquals("text/html; charset=utf-8", served.contentType)
        assertTrue(Html.fromHtml(served.text, Html.FROM_HTML_MODE_LEGACY).toString().contains("Hola, señor"))
    }

    @Test
    fun png() = assertImage("logo.png", "image/png", 640, 320)

    @Test
    fun jpg() = assertImage("photo.jpg", "image/jpeg", 640, 320)

    @Test
    fun jpeg() = assertImage("avatar.jpeg", "image/jpeg", 640, 320)

    @Test
    fun webp() = assertImage("banner.webp", "image/webp", 640, 320)

    @Test
    fun gif() = assertImage("sticker.gif", "image/gif", 320, 160)

    @Test
    fun pdf() {
        val served = serve("manual.pdf")
        assertEquals("application/pdf", served.contentType)
        val file = File(context.cacheDir, "manual.pdf").apply { writeBytes(served.bytes) }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { assertEquals(1, it.pageCount) }
        }
        file.delete()
    }

    @Test
    fun svg() {
        val served = serve("icon.svg")
        assertEquals("image/svg+xml", served.contentType)
        val parser = Xml.newPullParser().apply { setInput(served.bytes.inputStream(), "UTF-8") }
        parser.nextTag()
        assertEquals("svg", parser.name)
        assertEquals("64", parser.getAttributeValue(null, "width"))
    }

    @Test
    fun xml() {
        val served = serve("feed.xml")
        assertEquals("application/xml", served.contentType)
        val parser = Xml.newPullParser().apply { setInput(served.bytes.inputStream(), "UTF-8") }
        val titles = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "title") titles += parser.nextText()
        }
        assertEquals(listOf("Café con leche", "Ñandú"), titles)
    }

    @Test
    fun html() = assertHtml("terms.html")

    @Test
    fun htm() = assertHtml("terms.htm")

    @Test
    fun csv() {
        val served = serve("prices.csv")
        assertEquals("text/csv; charset=utf-8", served.contentType)
        val rows = served.text.trim().lines().map { it.split(",") }
        assertEquals(listOf("id", "name", "price"), rows.first())
        assertEquals("Café", rows[1][1])
        assertEquals(4, rows.size)
    }

    @Test
    fun txt() {
        val served = serve("notes.txt")
        assertEquals("text/plain; charset=utf-8", served.contentType)
        assertEquals("UTF-8", served.charset)
        assertTrue(served.text.contains("áéíóú ñ €"))
    }

    @Test
    fun json() {
        val served = serve("data.json")
        assertEquals("application/json", served.contentType)
        assertEquals("Ñandú", JSONObject(served.text).getString("name"))
    }

    @Test
    fun unknownExtensionIsBinary() {
        val served = serve("config.yaml")
        assertEquals("application/octet-stream", served.contentType)
        assertTrue(served.text.startsWith("name: sample"))
    }
}
