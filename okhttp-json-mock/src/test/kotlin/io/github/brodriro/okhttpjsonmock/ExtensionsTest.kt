package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Mock files with extensions other than `.json`, set with [OkHttpMockInterceptor.Builder.extensions]. */
class ExtensionsTest {

    /** Serves mocks from memory and records every path the interceptor asks for. */
    private class FakeProvider(private val files: Map<String, ByteArray>) : InputStreamProvider {
        val requestedPaths = mutableListOf<String>()

        override fun provide(path: String) = files[path]
            .also { requestedPaths += path }
            ?.inputStream()
    }

    private fun provider(vararg files: Pair<String, String>) =
        FakeProvider(files.associate { (path, text) -> path to text.toByteArray() })

    private fun builder(provider: InputStreamProvider) =
        OkHttpMockInterceptor.Builder(provider).noDelay().logger(MockLogger.NONE)

    private fun OkHttpMockInterceptor.get(url: String): Response = OkHttpClient.Builder()
        .addInterceptor(this)
        .build()
        .newCall(Request.Builder().url(url).build())
        .execute()

    @Test
    fun `only json files are looked up by default`() {
        val provider = provider("feed.xml" to XML)

        val response = builder(provider).build().get("http://example.com/feed")

        assertEquals(404, response.code)
        assertEquals(listOf("json"), builder(provider).build().extensions)
        assertEquals(listOf("json"), OkHttpMockInterceptor(provider, 0).extensions)
        assertEquals(0, provider.requestedPaths.count { !it.endsWith(".json") })
    }

    @Test
    fun `a configured extension is served with its content type`() {
        val interceptor = builder(provider("feed.xml" to XML)).extensions("json", "xml").build()

        val response = interceptor.get("http://example.com/feed")

        assertEquals(200, response.code)
        assertEquals("application/xml", response.header("Content-Type"))
        assertEquals("application/xml", response.body!!.contentType().toString())
        assertEquals(XML, response.body!!.string())
    }

    @Test
    fun `json mocks keep their content type when other extensions are configured`() {
        val interceptor = builder(provider("users.json" to """[{"id":1}]""")).extensions("xml", "json").build()

        val response = interceptor.get("http://example.com/users")

        assertEquals("application/json", response.header("Content-Type"))
        assertEquals("""[{"id":1}]""", response.body!!.string())
    }

    @Test
    fun `for the same file name the first extension in the list wins`() {
        val files = arrayOf("feed.json" to """{"from":"json"}""", "feed.xml" to XML)

        val jsonFirst = builder(provider(*files)).extensions("json", "xml").build().get("http://example.com/feed")
        val xmlFirst = builder(provider(*files)).extensions("xml", "json").build().get("http://example.com/feed")

        assertEquals("""{"from":"json"}""", jsonFirst.body!!.string())
        assertEquals(XML, xmlFirst.body!!.string())
    }

    @Test
    fun `a more specific file name wins over the extension order`() {
        val interceptor = builder(provider("feed.json" to """{"from":"json"}""", "feed.get.xml" to XML))
            .extensions("json", "xml")
            .build()

        val response = interceptor.get("http://example.com/feed")

        assertEquals(XML, response.body!!.string())
    }

    @Test
    fun `binary mocks are returned byte for byte`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0xFF.toByte())
        val interceptor = builder(FakeProvider(mapOf("images/{id}.png" to png))).extensions("png").build()

        val response = interceptor.get("http://example.com/images/42")

        assertEquals("image/png", response.header("Content-Type"))
        assertContentEquals(png, response.body!!.bytes())
    }

    /**
     * One case per extension, each served from a real file of that type in `src/test/resources/extensions`
     * (`yaml` stands for an extension that is not in the table). [signature] is how the file starts.
     */
    @ParameterizedTest(name = "{0} is served as {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "json | application/json          | {",
            "txt  | text/plain; charset=utf-8 | Texto",
            "html | text/html; charset=utf-8  | <!DOCTYPE html>",
            "htm  | text/html; charset=utf-8  | <!DOCTYPE html>",
            "csv  | text/csv; charset=utf-8   | id,name,price",
            "xml  | application/xml           | <?xml",
            "svg  | image/svg+xml             | <svg",
            "png  | image/png                 | \u0089PNG",
            "jpg  | image/jpeg                | \u00FF\u00D8\u00FF",
            "jpeg | image/jpeg                | \u00FF\u00D8\u00FF",
            "gif  | image/gif                 | GIF89a",
            "webp | image/webp                | RIFF",
            "pdf  | application/pdf           | %PDF-",
            "yaml | application/octet-stream  | name:",
        ],
    )
    fun `each extension is served byte for byte with its content type`(
        extension: String,
        contentType: String,
        signature: String,
    ) {
        val file = javaClass.classLoader.getResource("extensions/sample.$extension")!!.readBytes()
        val resources = InputStreamProvider { path -> javaClass.classLoader.getResourceAsStream(path) }
        val interceptor = builder(resources).basePath("extensions/").extensions(extension).build()

        val response = interceptor.get("http://example.com/sample")

        assertEquals(200, response.code)
        assertEquals(contentType, response.header("Content-Type"))
        assertEquals(contentType.toMediaType(), response.body!!.contentType())
        val body = response.body!!.bytes()
        assertContentEquals(file, body)
        val start = signature.toByteArray(Charsets.ISO_8859_1)
        assertContentEquals(start, body.copyOf(start.size))
    }

    @Test
    fun `the content type does not depend on the case of the extension`() {
        val interceptor = builder(provider("photo.JPG" to "x")).extensions("JPG").build()

        assertEquals("image/jpeg", interceptor.get("http://example.com/photo").header("Content-Type"))
    }

    @Test
    fun `only json files are read as envelopes`() {
        val envelope = """{"statusCode":201,"response":{"id":4}}"""
        val interceptor = builder(provider("users.txt" to envelope)).extensions("json", "txt").build()

        val response = interceptor.get("http://example.com/users")

        assertEquals(200, response.code)
        assertEquals("text/plain; charset=utf-8", response.header("Content-Type"))
        assertEquals(envelope, response.body!!.string())
    }

    @Test
    fun `json envelopes still work when other extensions are configured`() {
        val envelope =
            """{"statusCode":201,"headers":{"Content-Type":"application/vnd.api+json"},"response":{"id":4}}"""
        val interceptor = builder(provider("users.json" to envelope)).extensions("xml", "json").build()

        val response = interceptor.get("http://example.com/users")

        assertEquals(201, response.code)
        assertEquals("application/vnd.api+json", response.header("Content-Type"))
        assertEquals("""{"id":4}""", response.body!!.string())
    }

    @Test
    fun `missing mock lists the file names with every extension`() {
        val interceptor = builder(provider()).extensions("json", "xml").build()

        val response = interceptor.get("http://example.com/feed")

        assertEquals(404, response.code)
        assertContains(
            response.body!!.string(),
            "Tried: feed.get.json, feed.get.xml, feed.json, feed.xml and 4 more with {id}",
        )
    }

    @Test
    fun `a leading dot and repeated extensions are ignored`() {
        val interceptor = builder(provider()).extensions(".xml", "json", "xml").build()

        assertEquals(listOf("xml", "json"), interceptor.extensions)
    }

    @Test
    fun `extensions are validated on build`() {
        val builder = builder(provider())

        assertFailsWith<IllegalArgumentException> { builder.extensions().build() }
        assertFailsWith<IllegalArgumentException> { builder.extensions("").build() }
        assertFailsWith<IllegalArgumentException> { builder.extensions("tar.gz").build() }
        assertFailsWith<IllegalArgumentException> { builder.extensions("x/y").build() }
    }

    private companion object {
        const val XML = """<?xml version="1.0" encoding="UTF-8"?><feed><entry id="1"/></feed>"""
    }
}
