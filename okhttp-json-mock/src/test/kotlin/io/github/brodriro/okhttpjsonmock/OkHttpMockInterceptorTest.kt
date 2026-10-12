package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins the behaviour inherited from 3.0 so the Kotlin migration and the
 * HTTP-method support can be checked against it.
 */
class OkHttpMockInterceptorTest {

    /** Serves mocks from memory and records every path the interceptor asks for. */
    private class FakeProvider(private val files: Map<String, String>) : InputStreamProvider {
        val requestedPaths = mutableListOf<String>()

        override fun provide(path: String) = files[path]
            .also { requestedPaths += path }
            ?.byteInputStream()
    }

    private fun clientFor(
        provider: InputStreamProvider,
        failurePercentage: Int = 0,
        basePath: String = "",
    ): OkHttpClient {
        // A [0, 1) ms window keeps the simulated latency out of the tests.
        val interceptor = OkHttpMockInterceptor(provider, failurePercentage, basePath, 0, 1)
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    private fun OkHttpClient.get(url: String): Response =
        newCall(Request.Builder().url(url).build()).execute()

    @Test
    fun `path without query resolves to the json file with the same path`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))

        val response = clientFor(provider).get("http://example.com/api/v1/users/1")

        assertEquals(200, response.code)
        assertEquals(USER, response.body!!.string())
        assertEquals(listOf("api/v1/users/1.json"), provider.requestedPaths)
    }

    @Test
    fun `nested path resolves to a file inside the matching folder`() {
        val provider = FakeProvider(mapOf("api/v1/users/1/phoneNumbers.json" to NUMBERS))

        val response = clientFor(provider).get("http://example.com/api/v1/users/1/phoneNumbers")

        assertEquals(NUMBERS, response.body!!.string())
    }

    @Test
    fun `query string resolves to a file named after the query inside the path folder`() {
        val provider = FakeProvider(mapOf("api/v1/users/page=1.json" to USERS))

        val response = clientFor(provider).get("http://example.com/api/v1/users?page=1")

        assertEquals(USERS, response.body!!.string())
        assertEquals(listOf("api/v1/users/page=1.json"), provider.requestedPaths)
    }

    @Test
    fun `multiple query parameters keep the order of the url`() {
        val provider = FakeProvider(mapOf("api/v1/posts/3/page=1&categoryId=5.json" to USERS))

        val response = clientFor(provider).get("http://example.com/api/v1/posts/3?page=1&categoryId=5")

        assertEquals(USERS, response.body!!.string())
    }

    @Test
    fun `query file wins over the path file when both exist`() {
        val provider = FakeProvider(
            mapOf(
                "api/v1/users/page=1.json" to USERS,
                "api/v1/users.json" to USER,
            ),
        )

        val response = clientFor(provider).get("http://example.com/api/v1/users?page=1")

        assertEquals(USERS, response.body!!.string())
    }

    @Test
    fun `falls back to the path file when there is no file for the query`() {
        val provider = FakeProvider(mapOf("api/v1/users.json" to USERS))

        val response = clientFor(provider).get("http://example.com/api/v1/users?page=7")

        assertEquals(USERS, response.body!!.string())
        assertEquals(listOf("api/v1/users/page=7.json", "api/v1/users.json"), provider.requestedPaths)
    }

    @Test
    fun `base path is prepended to the resolved file`() {
        val provider = FakeProvider(mapOf("mocks/api/v1/users/1.json" to USER))

        val response = clientFor(provider, basePath = "mocks/").get("http://example.com/api/v1/users/1")

        assertEquals(USER, response.body!!.string())
    }

    @Test
    fun `host and port do not take part in the resolution`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))

        val response = clientFor(provider).get("https://another-host.test:8443/api/v1/users/1")

        assertEquals(USER, response.body!!.string())
    }

    @Test
    fun `a mock without method suffix answers any http method`() {
        val provider = FakeProvider(mapOf("api/v1/users.json" to USER))
        val request = Request.Builder()
            .url("http://example.com/api/v1/users")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        val response = clientFor(provider).newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(USER, response.body!!.string())
    }

    @Test
    fun `response is served as json and echoes the request`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))

        val response = clientFor(provider).get("http://example.com/api/v1/users/1")

        assertEquals("application/json", response.header("content-type"))
        assertEquals("application/json", response.body!!.contentType()!!.toString().substringBefore(';'))
        assertEquals("http://example.com/api/v1/users/1", response.request.url.toString())
    }

    @Test
    fun `multi-line json is returned verbatim`() {
        val provider = FakeProvider(mapOf("api/v1/users/page=1.json" to USERS))

        val body = clientFor(provider).get("http://example.com/api/v1/users?page=1").body!!.string()

        assertEquals(USERS, body)
        assertEquals(4, body.lines().size)
    }

    @Test
    fun `failure percentage of zero never fails`() {
        val client = clientFor(FakeProvider(mapOf("api/v1/users/1.json" to USER)), failurePercentage = 0)

        val codes = List(50) { client.get("http://example.com/api/v1/users/1").use { it.code } }

        assertEquals(setOf(200), codes.toSet())
    }

    @Test
    fun `failure percentage of one hundred always answers 504`() {
        val client = clientFor(FakeProvider(mapOf("api/v1/users/1.json" to USER)), failurePercentage = 100)

        val codes = List(50) { client.get("http://example.com/api/v1/users/1").use { it.code } }

        assertEquals(setOf(504), codes.toSet())
    }

    @Test
    fun `equal delay bounds give a fixed delay instead of failing`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))
        val client = OkHttpClient.Builder()
            .addInterceptor(OkHttpMockInterceptor(provider, 0, 0, 0))
            .build()

        val response = client.get("http://example.com/api/v1/users/1")

        assertEquals(200, response.code)
    }

    @Test
    fun `delay bounds are validated on construction`() {
        val provider = FakeProvider(emptyMap())

        assertFailsWith<IllegalArgumentException> { OkHttpMockInterceptor(provider, 0, 10, 5) }
        assertFailsWith<IllegalArgumentException> { OkHttpMockInterceptor(provider, 0, -1, 5) }
    }

    @Test
    fun `missing mock answers 404 and names the files that were tried`() {
        val provider = FakeProvider(emptyMap())

        val response = clientFor(provider).get("http://example.com/api/v1/users?page=1")

        assertEquals(404, response.code)
        val body = response.body!!.string()
        assertContains(body, "GET /api/v1/users")
        assertContains(body, "api/v1/users/page=1.json")
        assertContains(body, "api/v1/users.json")
        assertEquals(listOf("api/v1/users/page=1.json", "api/v1/users.json"), provider.requestedPaths)
    }

    @Test
    fun `missing mock answers 404 even when every call is set to fail`() {
        val response = clientFor(FakeProvider(emptyMap()), failurePercentage = 100)
            .get("http://example.com/api/v1/users/1")

        assertEquals(404, response.code)
    }

    @Test
    fun `status line carries the reason phrase and not the file contents`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))

        val ok = clientFor(provider).get("http://example.com/api/v1/users/1")
        val failed = clientFor(provider, failurePercentage = 100).get("http://example.com/api/v1/users/1")
        val missing = clientFor(provider).get("http://example.com/api/v1/unknown")

        assertEquals("OK", ok.message)
        assertEquals("Gateway Timeout", failed.message)
        assertEquals("Not Found", missing.message)
    }

    @Test
    fun `simulated failure does not return the mock body`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))

        val response = clientFor(provider, failurePercentage = 100).get("http://example.com/api/v1/users/1")

        assertEquals(504, response.code)
        assertEquals("", response.body!!.string())
    }

    @Test
    fun `failure percentage outside 0 to 100 is rejected`() {
        val provider = FakeProvider(emptyMap())

        assertFailsWith<IllegalArgumentException> { OkHttpMockInterceptor(provider, -1) }
        assertFailsWith<IllegalArgumentException> { OkHttpMockInterceptor(provider, 101) }
    }

    private companion object {
        const val USER = """{"id":1,"name":"John"}"""
        const val NUMBERS = """["0123456789"]"""
        const val USERS = "[\n  {\"id\":1},\n  {\"id\":2}\n]"
    }
}
