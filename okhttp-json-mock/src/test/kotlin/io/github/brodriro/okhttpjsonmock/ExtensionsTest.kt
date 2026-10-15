package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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

    @Test
    fun `each known extension has its content type and any other is binary`() {
        val expected = mapOf(
            "txt" to "text/plain; charset=utf-8",
            "html" to "text/html; charset=utf-8",
            "htm" to "text/html; charset=utf-8",
            "csv" to "text/csv; charset=utf-8",
            "xml" to "application/xml",
            "svg" to "image/svg+xml",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "JPG" to "image/jpeg",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "pdf" to "application/pdf",
            "yaml" to "application/octet-stream",
        )

        expected.forEach { (extension, contentType) ->
            val interceptor = builder(provider("file.$extension" to "x")).extensions(extension).build()

            assertEquals(contentType, interceptor.get("http://example.com/file").header("Content-Type"), extension)
        }
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
