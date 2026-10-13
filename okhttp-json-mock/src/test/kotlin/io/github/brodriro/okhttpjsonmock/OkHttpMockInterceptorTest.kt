package io.github.brodriro.okhttpjsonmock

import com.google.gson.JsonParser
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.PrintStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun OkHttpClient.get(url: String): Response = newCall(Request.Builder().url(url).build()).execute()

    @Test
    fun `path without query resolves to the json file with the same path`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))

        val response = clientFor(provider).get("http://example.com/api/v1/users/1")

        assertEquals(200, response.code)
        assertEquals(USER, response.body!!.string())
        assertEquals(listOf("api/v1/users/1.get.json", "api/v1/users/1.json"), provider.requestedPaths)
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
        assertEquals("api/v1/users/page=1.json", provider.requestedPaths.last())
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
        assertEquals(
            listOf(
                "api/v1/users/page=7.get.json",
                "api/v1/users.get.json",
                "api/v1/users/page=7.json",
                "api/v1/users.json",
            ),
            provider.requestedPaths,
        )
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
        assertContains(body, "api/v1/users.get.json")
        assertContains(body, "more with {id} in place of a path segment")
        assertEquals(4, provider.requestedPaths.count { "{id}" !in it })
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

    @Test
    fun `interruption during the delay fails the call and keeps the interrupt flag`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))
        val client = OkHttpClient.Builder()
            .addInterceptor(OkHttpMockInterceptor(provider, 0, 5_000, 5_000))
            .build()

        Thread.currentThread().interrupt()
        try {
            assertFailsWith<InterruptedIOException> { client.get("http://example.com/api/v1/users/1") }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `responses report http 1_1`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))

        val response = clientFor(provider).get("http://example.com/api/v1/users/1")

        assertEquals(Protocol.HTTP_1_1, response.protocol)
    }

    @Test
    fun `mock bytes are returned untouched`() {
        val content = "{\r\n  \"name\": \"Ñandú 日本\"\r\n}\n"
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to content))

        val response = clientFor(provider).get("http://example.com/api/v1/users/1")

        assertContentEquals(content.toByteArray(Charsets.UTF_8), response.body!!.bytes())
    }

    @Test
    fun `non ascii mocks decode as utf-8`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to """{"name":"Ñandú 日本"}"""))

        val response = clientFor(provider).get("http://example.com/api/v1/users/1")

        assertEquals("""{"name":"Ñandú 日本"}""", response.body!!.string())
    }

    @Test
    fun `root path resolves to index json`() {
        val provider = FakeProvider(mapOf("index.json" to USER))

        val response = clientFor(provider).get("http://example.com/")

        assertEquals(USER, response.body!!.string())
        assertEquals("index.json", provider.requestedPaths.last())
    }

    @Test
    fun `trailing slash resolves to the same file as the path without it`() {
        val provider = FakeProvider(mapOf("api/v1/users.json" to USERS))

        val response = clientFor(provider).get("http://example.com/api/v1/users/")

        assertEquals(USERS, response.body!!.string())
        assertEquals("api/v1/users.json", provider.requestedPaths.last())
    }

    @Test
    fun `one instance serves concurrent calls`() {
        val provider = InputStreamProvider { path -> USER.takeIf { path == "api/v1/users/1.json" }?.byteInputStream() }
        val client = clientFor(provider)
        val pool = Executors.newFixedThreadPool(8)

        val codes = try {
            pool.invokeAll(List(200) { Callable { client.get("http://example.com/api/v1/users/1").use { it.code } } })
                .map { it.get() }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(setOf(200), codes.toSet())
    }

    @Test
    fun `builder defaults match the 3_0 constructor defaults`() {
        val interceptor = OkHttpMockInterceptor.Builder(FakeProvider(emptyMap())).build()

        assertEquals(0, interceptor.failurePercentage)
        assertEquals("", interceptor.basePath)
        assertEquals(500, interceptor.minDelayMilliseconds)
        assertEquals(1500, interceptor.maxDelayMilliseconds)
    }

    @Test
    fun `builder options are applied`() {
        val provider = FakeProvider(mapOf("mocks/api/v1/users/1.json" to USER))
        val interceptor = OkHttpMockInterceptor.Builder(provider)
            .basePath("mocks/")
            .failurePercentage(100)
            .noDelay()
            .build()
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        val response = client.get("http://example.com/api/v1/users/1")

        assertEquals(504, response.code)
        assertEquals("mocks/api/v1/users/1.json", provider.requestedPaths.last())
        assertEquals(0, interceptor.maxDelayMilliseconds)
    }

    @Test
    fun `builder validates on build`() {
        val builder = OkHttpMockInterceptor.Builder(FakeProvider(emptyMap()))

        assertFailsWith<IllegalArgumentException> { builder.failurePercentage(150).build() }
    }

    @Test
    fun `simulated failures use the configured status code`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))
        val interceptor = OkHttpMockInterceptor.Builder(provider)
            .failurePercentage(100)
            .failureCode(503)
            .noDelay()
            .build()

        val response = OkHttpClient.Builder().addInterceptor(interceptor).build()
            .get("http://example.com/api/v1/users/1")

        assertEquals(503, response.code)
        assertEquals("Service Unavailable", response.message)
    }

    @Test
    fun `failure code must be an error status`() {
        val builder = OkHttpMockInterceptor.Builder(FakeProvider(emptyMap()))

        assertEquals(504, builder.build().failureCode)
        assertFailsWith<IllegalArgumentException> { builder.failureCode(200).build() }
    }

    @Test
    fun `each call is logged as one line with method, status and file`() {
        val lines = mutableListOf<String>()
        val provider = FakeProvider(mapOf("api/v1/users/page=1.json" to USERS))
        val interceptor = OkHttpMockInterceptor.Builder(provider).noDelay().logger { lines += it }.build()
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        client.get("http://example.com/api/v1/users?page=1")
        client.get("http://example.com/api/v1/unknown")

        assertEquals(
            listOf(
                "GET /api/v1/users?page=1 -> 200 from api/v1/users/page=1.json",
                "GET /api/v1/unknown -> 404, no mock found. Tried: api/v1/unknown.get.json, api/v1/unknown.json " +
                    "and 12 more with {id} in place of a path segment",
            ),
            lines,
        )
    }

    @Test
    fun `default logger ends every line and the silent logger prints nothing`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))
        val silent = OkHttpMockInterceptor.Builder(provider).noDelay().logger(MockLogger.NONE).build()

        val printedByDefault = captureStandardOutput { clientFor(provider).get("http://example.com/api/v1/users/1") }
        val printedBySilent = captureStandardOutput {
            OkHttpClient.Builder().addInterceptor(silent).build().get("http://example.com/api/v1/users/1")
        }

        assertEquals(
            "JsonMockServer: GET /api/v1/users/1 -> 200 from api/v1/users/1.json" + System.lineSeparator(),
            printedByDefault,
        )
        assertEquals("", printedBySilent)
    }

    private fun captureStandardOutput(block: () -> Unit): String {
        val original = System.out
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return captured.toString()
    }

    @Test
    fun `same url answers each http method with its own mock`() {
        val provider = FakeProvider(
            mapOf(
                "api/v1/users.get.json" to """["list"]""",
                "api/v1/users.post.json" to """{"created":true}""",
                "api/v1/users.put.json" to """{"replaced":true}""",
                "api/v1/users.delete.json" to """{"deleted":true}""",
            ),
        )
        val client = clientFor(provider)
        val url = "http://example.com/api/v1/users"

        assertEquals("""["list"]""", client.call("GET", url))
        assertEquals("""{"created":true}""", client.call("POST", url))
        assertEquals("""{"replaced":true}""", client.call("PUT", url))
        assertEquals("""{"deleted":true}""", client.call("DELETE", url))
    }

    @Test
    fun `method mock wins over the mock shared by every method`() {
        val provider = FakeProvider(
            mapOf(
                "api/v1/users.json" to """["shared"]""",
                "api/v1/users.post.json" to """{"created":true}""",
            ),
        )
        val client = clientFor(provider)
        val url = "http://example.com/api/v1/users"

        assertEquals("""{"created":true}""", client.call("POST", url))
        assertEquals("""["shared"]""", client.call("GET", url))
        assertEquals("""["shared"]""", client.call("DELETE", url))
    }

    @Test
    fun `method mock can be specific to a query string`() {
        val provider = FakeProvider(
            mapOf(
                "api/v1/users/page=1.get.json" to """["page 1"]""",
                "api/v1/users.get.json" to """["any page"]""",
            ),
        )
        val client = clientFor(provider)

        assertEquals("""["page 1"]""", client.call("GET", "http://example.com/api/v1/users?page=1"))
        assertEquals("""["any page"]""", client.call("GET", "http://example.com/api/v1/users?page=2"))
    }

    @Test
    fun `a mock for another method does not answer the request`() {
        val provider = FakeProvider(mapOf("api/v1/users.post.json" to """{"created":true}"""))

        val response = clientFor(provider).get("http://example.com/api/v1/users")

        assertEquals(404, response.code)
    }

    private fun OkHttpClient.call(method: String, url: String): String {
        val body = if (method == "GET") null else "{}".toRequestBody("application/json".toMediaType())
        return newCall(Request.Builder().url(url).method(method, body).build()).execute().body!!.string()
    }

    @Test
    fun `a mock named with sorted parameters answers any parameter order`() {
        val provider = FakeProvider(mapOf("api/v1/posts/categoryId=5&page=1.json" to USERS))
        val client = clientFor(provider)

        assertEquals(USERS, client.get("http://example.com/api/v1/posts?page=1&categoryId=5").body!!.string())
        assertEquals(USERS, client.get("http://example.com/api/v1/posts?categoryId=5&page=1").body!!.string())
    }

    @Test
    fun `a mock named with the exact parameter order wins over the sorted one`() {
        val provider = FakeProvider(
            mapOf(
                "api/v1/posts/page=1&categoryId=5.json" to USER,
                "api/v1/posts/categoryId=5&page=1.json" to USERS,
            ),
        )

        val response = clientFor(provider).get("http://example.com/api/v1/posts?page=1&categoryId=5")

        assertEquals(USER, response.body!!.string())
    }

    @Test
    fun `mock with a statusCode envelope answers with that status and the wrapped response`() {
        val provider = FakeProvider(
            mapOf("api/v1/users.post.json" to """{ "statusCode": 201, "response": { "id": 4, "name": "Ana" } }"""),
        )

        val response = clientFor(provider).send("POST", "http://example.com/api/v1/users")

        assertEquals(201, response.code)
        assertEquals("Created", response.message)
        assertEquals("""{"id":4,"name":"Ana"}""", response.body!!.string())
    }

    @Test
    fun `envelope without response answers with an empty body`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.delete.json" to """{ "statusCode": 204 }"""))

        val response = clientFor(provider).send("DELETE", "http://example.com/api/v1/users/1")

        assertEquals(204, response.code)
        assertEquals("", response.body!!.string())
    }

    @Test
    fun `envelope can describe an error response`() {
        val provider = FakeProvider(
            mapOf("api/v1/login.post.json" to """{"statusCode":401,"response":{"error":"bad credentials"}}"""),
        )

        val response = clientFor(provider).send("POST", "http://example.com/api/v1/login")

        assertEquals(401, response.code)
        assertEquals("""{"error":"bad credentials"}""", response.body!!.string())
    }

    @Test
    fun `real payload that has a statusCode field is served as it is`() {
        val payload = """{ "statusCode": 404, "message": "Not found" }"""
        val provider = FakeProvider(mapOf("api/v1/errors/last.json" to payload))

        val response = clientFor(provider).get("http://example.com/api/v1/errors/last")

        assertEquals(200, response.code)
        assertEquals(payload, response.body!!.string())
    }

    @Test
    fun `envelope with an invalid status code fails the call naming the file`() {
        val provider = FakeProvider(mapOf("api/v1/users.post.json" to """{"statusCode":"created"}"""))

        val error = assertFailsWith<IOException> {
            clientFor(provider).send("POST", "http://example.com/api/v1/users")
        }

        assertContains(error.message!!, "api/v1/users.post.json")
    }

    @Test
    fun `simulated failure wins over the status code of the envelope`() {
        val provider = FakeProvider(mapOf("api/v1/users.post.json" to """{"statusCode":201,"response":{"id":4}}"""))

        val response = clientFor(provider, failurePercentage = 100).send("POST", "http://example.com/api/v1/users")

        assertEquals(504, response.code)
        assertEquals("", response.body!!.string())
    }

    @Test
    fun `log line reports the status code of the envelope`() {
        val lines = mutableListOf<String>()
        val provider = FakeProvider(mapOf("api/v1/users.post.json" to """{"statusCode":201,"response":{"id":4}}"""))
        val interceptor = OkHttpMockInterceptor.Builder(provider).noDelay().logger { lines += it }.build()

        OkHttpClient.Builder().addInterceptor(interceptor).build().send("POST", "http://example.com/api/v1/users")

        assertEquals(listOf("POST /api/v1/users -> 201 from api/v1/users.post.json"), lines)
    }

    private fun OkHttpClient.send(method: String, url: String): Response {
        val body = if (method == "GET") null else "{}".toRequestBody("application/json".toMediaType())
        return newCall(Request.Builder().url(url).method(method, body).build()).execute()
    }

    @Test
    fun `envelope headers are sent with the response`() {
        val provider = FakeProvider(
            mapOf(
                "api/v1/users.post.json" to
                    """{"statusCode":201,"headers":{"Location":"/api/v1/users/4","X-Total-Count":4},"response":{"id":4}}""",
            ),
        )

        val response = clientFor(provider).send("POST", "http://example.com/api/v1/users")

        assertEquals(201, response.code)
        assertEquals("/api/v1/users/4", response.header("Location"))
        assertEquals("4", response.header("X-Total-Count"))
        assertEquals("application/json", response.header("content-type"))
        assertEquals("""{"id":4}""", response.body!!.string())
    }

    @Test
    fun `envelope without headers only sends the content type`() {
        val provider = FakeProvider(mapOf("api/v1/users.post.json" to """{"statusCode":201,"response":{"id":4}}"""))

        val response = clientFor(provider).send("POST", "http://example.com/api/v1/users")

        assertEquals(listOf("content-type"), response.headers.names().toList())
    }

    @Test
    fun `envelope content type header replaces the default`() {
        val provider = FakeProvider(
            mapOf(
                "api/v1/health.json" to
                    """{"statusCode":200,"headers":{"Content-Type":"text/plain"},"response":"ok"}""",
            ),
        )

        val response = clientFor(provider).get("http://example.com/api/v1/health")

        assertEquals(listOf("text/plain"), response.headers("content-type"))
        assertEquals("text", response.body!!.contentType()!!.type)
    }

    @Test
    fun `simulated failure does not send the envelope headers`() {
        val provider = FakeProvider(
            mapOf("api/v1/users.post.json" to """{"statusCode":201,"headers":{"Location":"/api/v1/users/4"}}"""),
        )

        val response = clientFor(provider, failurePercentage = 100).send("POST", "http://example.com/api/v1/users")

        assertEquals(504, response.code)
        assertNull(response.header("Location"))
    }

    @Test
    fun `plain mock can be a json object or a json array`() {
        val provider = FakeProvider(
            mapOf(
                "api/v1/users/1.json" to """{ "id": 1, "tags": ["a", "b"], "address": { "city": "Lima" } }""",
                "api/v1/users.json" to """[ { "id": 1 }, { "id": 2 } ]""",
                "api/v1/tags.json" to "[]",
                "api/v1/settings.json" to "{}",
            ),
        )
        val client = clientFor(provider)

        val user = JsonParser.parseString(client.get("http://example.com/api/v1/users/1").body!!.string())
        val users = JsonParser.parseString(client.get("http://example.com/api/v1/users").body!!.string())

        assertTrue(user.isJsonObject)
        assertEquals("Lima", user.asJsonObject.getAsJsonObject("address").get("city").asString)
        assertTrue(users.isJsonArray)
        assertEquals(2, users.asJsonArray.size())
        assertEquals("[]", client.get("http://example.com/api/v1/tags").body!!.string())
        assertEquals("{}", client.get("http://example.com/api/v1/settings").body!!.string())
    }

    @Test
    fun `envelope response can be a json object or a json array`() {
        val provider = FakeProvider(
            mapOf(
                "api/v1/users.post.json" to
                    """{ "statusCode": 201, "response": { "id": 4, "tags": ["a"], "address": { "city": "Lima" } } }""",
                "api/v1/users.get.json" to """{ "statusCode": 200, "response": [ { "id": 1 }, { "id": 2 } ] }""",
                "api/v1/tags.get.json" to """{ "statusCode": 200, "response": [] }""",
                "api/v1/settings.get.json" to """{ "statusCode": 200, "response": {} }""",
            ),
        )
        val client = clientFor(provider)

        val created = JsonParser.parseString(client.send("POST", "http://example.com/api/v1/users").body!!.string())
        val users = JsonParser.parseString(client.get("http://example.com/api/v1/users").body!!.string())

        assertTrue(created.isJsonObject)
        assertEquals("Lima", created.asJsonObject.getAsJsonObject("address").get("city").asString)
        assertTrue(users.isJsonArray)
        assertEquals(listOf(1, 2), users.asJsonArray.map { it.asJsonObject.get("id").asInt })
        assertEquals("[]", client.get("http://example.com/api/v1/tags").body!!.string())
        assertEquals("{}", client.get("http://example.com/api/v1/settings").body!!.string())
    }

    @Test
    fun `plain array whose items have a statusCode field is served as it is`() {
        val payload = """[ { "statusCode": 201 }, { "statusCode": 404 } ]"""
        val provider = FakeProvider(mapOf("api/v1/events.json" to payload))

        val response = clientFor(provider).get("http://example.com/api/v1/events")

        assertEquals(200, response.code)
        assertEquals(payload, response.body!!.string())
    }

    private companion object {
        const val USER = """{"id":1,"name":"John"}"""
        const val NUMBERS = """["0123456789"]"""
        const val USERS = "[\n  {\"id\":1},\n  {\"id\":2}\n]"
    }

    @Test
    fun `id placeholder file answers any value of that segment`() {
        val client = clientFor(FakeProvider(mapOf("api/v1/users/{id}.json" to USER)))

        assertEquals(USER, client.get("http://example.com/api/v1/users/1").body!!.string())
        assertEquals(USER, client.get("http://example.com/api/v1/users/42").body!!.string())
    }

    @Test
    fun `id placeholder folder answers nested paths`() {
        val provider = FakeProvider(mapOf("api/v1/users/{id}/phoneNumbers.json" to "[]"))

        val response = clientFor(provider).get("http://example.com/api/v1/users/7/phoneNumbers")

        assertEquals(200, response.code)
        assertEquals("[]", response.body!!.string())
    }

    @Test
    fun `file for the exact path wins over the id placeholder`() {
        val provider = FakeProvider(
            mapOf("api/v1/users/{id}.get.json" to """{"id":0}""", "api/v1/users/search.json" to "[]"),
        )
        val client = clientFor(provider)

        assertEquals("[]", client.get("http://example.com/api/v1/users/search").body!!.string())
        assertEquals("""{"id":0}""", client.get("http://example.com/api/v1/users/9").body!!.string())
    }

    @Test
    fun `id placeholder can be tied to a method and carry an envelope`() {
        val provider = FakeProvider(
            mapOf("api/v1/users/{id}.delete.json" to """{"statusCode":204}""", "api/v1/users/{id}.json" to USER),
        )
        val client = clientFor(provider)

        val deleted = client.send("DELETE", "http://example.com/api/v1/users/5")

        assertEquals(204, deleted.code)
        assertEquals(USER, client.get("http://example.com/api/v1/users/5").body!!.string())
    }

    @Test
    fun `two path segments can be placeholders`() {
        val provider = FakeProvider(mapOf("orders/{id}/items/{id}.json" to USER))

        assertEquals(USER, clientFor(provider).get("http://example.com/orders/3/items/8").body!!.string())
    }

    @Test
    fun `a file for the exact path is found without asking for placeholder files`() {
        val provider = FakeProvider(mapOf("api/v1/users/1.json" to USER))

        clientFor(provider).get("http://example.com/api/v1/users/1")

        assertEquals(0, provider.requestedPaths.count { "{id}" in it })
    }
}
