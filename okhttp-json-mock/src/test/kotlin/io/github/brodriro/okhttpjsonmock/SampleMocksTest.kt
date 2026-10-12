package io.github.brodriro.okhttpjsonmock

import com.google.gson.JsonParser
import io.github.brodriro.okhttpjsonmock.providers.DefaultInputStreamProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the example mocks shipped with the sample app through the interceptor, so the examples the
 * README points to are known to work.
 */
class SampleMocksTest {

    private val client = OkHttpClient.Builder()
        .addInterceptor(
            OkHttpMockInterceptor.Builder(DefaultInputStreamProvider())
                .basePath(SAMPLE_ASSETS)
                .noDelay()
                .logger(MockLogger.NONE)
                .build(),
        )
        .build()

    private fun send(method: String, path: String): Response {
        val body = if (method == "GET") null else "{}".toRequestBody("application/json".toMediaType())
        return client.newCall(Request.Builder().url("http://example.com/$path").method(method, body).build()).execute()
    }

    @Test
    fun `get with a query returns the plain array`() {
        val response = send("GET", "api/v1/users?page=1")

        assertEquals(200, response.code)
        assertEquals(3, JsonParser.parseString(response.body!!.string()).asJsonArray.size())
    }

    @Test
    fun `get of an empty page returns a wrapped empty array with a header`() {
        val response = send("GET", "api/v1/users?page=2")

        assertEquals(200, response.code)
        assertEquals("3", response.header("X-Total-Count"))
        assertEquals("[]", response.body!!.string())
    }

    @Test
    fun `get of one user returns the plain object`() {
        val user = JsonParser.parseString(send("GET", "api/v1/users/1").body!!.string())

        assertTrue(user.isJsonObject)
        assertEquals("John", user.asJsonObject.get("name").asString)
    }

    @Test
    fun `post on the collection answers 201 with a location header`() {
        val response = send("POST", "api/v1/users")

        assertEquals(201, response.code)
        assertEquals("/api/v1/users/4", response.header("Location"))
        assertEquals(4, JsonParser.parseString(response.body!!.string()).asJsonObject.get("id").asInt)
    }

    @Test
    fun `put on the url of a user answers with its own mock`() {
        val response = send("PUT", "api/v1/users/1")

        assertEquals(200, response.code)
        assertEquals("Smith", JsonParser.parseString(response.body!!.string()).asJsonObject.get("lastName").asString)
    }

    @Test
    fun `delete on the url of a user answers 204 without a body`() {
        val response = send("DELETE", "api/v1/users/1")

        assertEquals(204, response.code)
        assertEquals("", response.body!!.string())
    }

    @Test
    fun `mocked error keeps its json body`() {
        val response = send("GET", "api/v1/users/9")

        assertEquals(404, response.code)
        assertEquals("""{"error":"User 9 not found"}""", response.body!!.string())
    }

    private companion object {
        /** The test task runs in the library module folder. */
        const val SAMPLE_ASSETS = "../app/src/main/assets/"
    }
}
