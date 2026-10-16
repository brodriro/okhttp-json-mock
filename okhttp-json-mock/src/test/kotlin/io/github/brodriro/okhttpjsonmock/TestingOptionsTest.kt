package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The options meant for tests: failing on a missing mock and recording the calls. */
class TestingOptionsTest {

    private val provider = InputStreamProvider { path ->
        mapOf(
            "api/v1/users.json" to """[{"id":1}]""",
            "api/v1/users.post.json" to """{"statusCode":201,"response":{"id":4}}""",
            "api/v1/users/{id}.json" to """{"id":0}""",
        )[path]?.byteInputStream()
    }

    private fun builder() = OkHttpMockInterceptor.Builder(provider).noDelay().logger(MockLogger.NONE)

    private fun clientFor(interceptor: OkHttpMockInterceptor) =
        OkHttpClient.Builder().addInterceptor(interceptor).build()

    private fun OkHttpClient.send(method: String, url: String): Response {
        val body = if (method == "GET") null else "{}".toRequestBody("application/json".toMediaType())
        return newCall(Request.Builder().url(url).method(method, body).build()).execute()
    }

    @Test
    fun `missing mock throws when failOnMissingMock is set`() {
        val client = clientFor(builder().failOnMissingMock().build())

        val error =
            assertFailsWith<MissingMockException> { client.send("GET", "http://example.com/api/v1/orders?page=2") }

        assertEquals("GET", error.method)
        assertEquals("http://example.com/api/v1/orders?page=2".toHttpUrl(), error.url)
        assertEquals("api/v1/orders/page=2.get.json", error.triedFiles.first())
        assertTrue("api/v1/{id}.json" in error.triedFiles)
        assertEquals(
            "No mock file found for GET /api/v1/orders?page=2. Tried: api/v1/orders/page=2.get.json, " +
                "api/v1/orders.get.json, api/v1/orders/page=2.json, api/v1/orders.json " +
                "and 24 more with {id} in place of a path segment",
            error.message,
        )
    }

    @Test
    fun `missing mock exception is an IOException`() {
        val client = clientFor(builder().failOnMissingMock().build())

        assertFailsWith<IOException> { client.send("GET", "http://example.com/unknown") }
    }

    @Test
    fun `asynchronous call receives the missing mock as a failure`() {
        val client = clientFor(builder().failOnMissingMock().build())
        val done = CountDownLatch(1)
        var failure: IOException? = null

        client.newCall(Request.Builder().url("http://example.com/unknown").build()).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    failure = e
                    done.countDown()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    done.countDown()
                }
            },
        )

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertIs<MissingMockException>(failure)
    }

    @Test
    fun `existing mocks are answered as usual when failOnMissingMock is set`() {
        val client = clientFor(builder().failOnMissingMock().build())

        assertEquals(200, client.send("GET", "http://example.com/api/v1/users").code)
        assertEquals(201, client.send("POST", "http://example.com/api/v1/users").code)
        assertEquals(200, client.send("GET", "http://example.com/api/v1/users/7").code)
    }

    @Test
    fun `missing mock still answers 404 by default`() {
        assertEquals(404, clientFor(builder().build()).send("GET", "http://example.com/unknown").code)
    }

    @Test
    fun `missing mock is logged before failing`() {
        val lines = mutableListOf<String>()
        val interceptor = OkHttpMockInterceptor.Builder(provider).noDelay().logger {
            lines += it
        }.failOnMissingMock().build()

        assertFailsWith<MissingMockException> { clientFor(interceptor).send("GET", "http://example.com/unknown") }

        assertEquals(
            listOf(
                "GET /unknown -> failed, no mock found. Tried: unknown.get.json, unknown.json " +
                    "and 2 more with {id} in place of a path segment",
            ),
            lines,
        )
    }

    @Test
    fun `calls are recorded in order with their file and status`() {
        val interceptor = builder().recordCalls().build()
        val client = clientFor(interceptor)

        client.send("GET", "http://example.com/api/v1/users?page=1")
        client.send("POST", "http://example.com/api/v1/users")
        client.send("GET", "http://example.com/api/v1/users/7")
        client.send("GET", "http://example.com/unknown")

        assertEquals(
            listOf(
                "GET http://example.com/api/v1/users?page=1 -> 200 from api/v1/users.json",
                "POST http://example.com/api/v1/users -> 201 from api/v1/users.post.json",
                "GET http://example.com/api/v1/users/7 -> 200 from api/v1/users/{id}.json",
                "GET http://example.com/unknown -> 404",
            ),
            interceptor.recordedCalls.map { it.toString() },
        )
        val post = interceptor.recordedCalls[1]
        assertEquals("POST", post.method)
        assertEquals("http://example.com/api/v1/users".toHttpUrl(), post.url)
        assertEquals("api/v1/users.post.json", post.mockFile)
        assertEquals(201, post.statusCode)
    }

    @Test
    fun `a call that fails for a missing mock is recorded without status`() {
        val interceptor = builder().recordCalls().failOnMissingMock().build()

        assertFailsWith<MissingMockException> { clientFor(interceptor).send("DELETE", "http://example.com/unknown") }

        val call = interceptor.recordedCalls.single()
        assertEquals("DELETE", call.method)
        assertNull(call.mockFile)
        assertNull(call.statusCode)
        assertEquals("DELETE http://example.com/unknown -> no mock", call.toString())
    }

    @Test
    fun `a simulated failure is recorded with the failure code and the file`() {
        val interceptor = builder().recordCalls().failurePercentage(100).failureCode(503).build()

        clientFor(interceptor).send("GET", "http://example.com/api/v1/users")

        val call = interceptor.recordedCalls.single()
        assertEquals(503, call.statusCode)
        assertEquals("api/v1/users.json", call.mockFile)
    }

    @Test
    fun `nothing is recorded unless recordCalls is set`() {
        val interceptor = builder().build()

        clientFor(interceptor).send("GET", "http://example.com/api/v1/users")

        assertTrue(interceptor.recordedCalls.isEmpty())
    }

    @Test
    fun `recorded calls are a snapshot and can be cleared`() {
        val interceptor = builder().recordCalls().build()
        val client = clientFor(interceptor)
        client.send("GET", "http://example.com/api/v1/users")
        val snapshot = interceptor.recordedCalls

        client.send("GET", "http://example.com/api/v1/users")
        assertEquals(1, snapshot.size)
        assertEquals(2, interceptor.recordedCalls.size)

        interceptor.clearRecordedCalls()
        assertTrue(interceptor.recordedCalls.isEmpty())
    }

    @Test
    fun `interceptors built with the 3_0 constructors neither fail nor record`() {
        val interceptor = OkHttpMockInterceptor(provider, 0, 0, 1)

        assertEquals(404, clientFor(interceptor).send("GET", "http://example.com/unknown").code)
        assertTrue(interceptor.recordedCalls.isEmpty())
    }

    private fun OkHttpClient.send(method: String, url: String, body: RequestBody?): Response =
        newCall(Request.Builder().url(url).method(method, body).build()).execute()

    /** A body that counts how many times it was written and can be one-shot or duplex. */
    private class CountingBody(
        private val bytes: ByteString,
        private val oneShot: Boolean = false,
        private val duplex: Boolean = false,
    ) : RequestBody() {
        var writes = 0

        override fun contentType(): MediaType = "application/octet-stream".toMediaType()

        override fun isOneShot(): Boolean = oneShot

        override fun isDuplex(): Boolean = duplex

        override fun writeTo(sink: BufferedSink) {
            check(!oneShot || writes == 0) { "one-shot body written twice" }
            writes++
            sink.write(bytes)
        }
    }

    @Test
    fun `the body of a request is recorded as it was sent`() {
        val interceptor = builder().recordCalls().build()
        val json = """{"name":"Ana","age":30}"""

        clientFor(interceptor).send(
            "POST",
            "http://example.com/api/v1/users",
            json.toRequestBody("application/json".toMediaType()),
        )

        val call = interceptor.recordedCalls.single()
        assertEquals(json.encodeUtf8(), call.body)
        assertEquals(json, call.body?.utf8())
    }

    @Test
    fun `a request without body is recorded with a null body`() {
        val interceptor = builder().recordCalls().build()

        clientFor(interceptor).send("GET", "http://example.com/api/v1/users")

        assertNull(interceptor.recordedCalls.single().body)
    }

    @Test
    fun `an empty body is recorded as an empty ByteString`() {
        val interceptor = builder().recordCalls().build()

        clientFor(interceptor).send("POST", "http://example.com/api/v1/users", ByteArray(0).toRequestBody())

        assertEquals(ByteString.EMPTY, interceptor.recordedCalls.single().body)
    }

    @Test
    fun `a binary multipart body is recorded byte for byte`() {
        val interceptor = builder().recordCalls().build()
        val image = ByteArray(256) { it.toByte() }
        val multipart = MultipartBody.Builder("boundary")
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", "Ana")
            .addFormDataPart("avatar", "avatar.png", image.toRequestBody("image/png".toMediaType()))
            .build()
        val expected = Buffer().also(multipart::writeTo).readByteString()

        clientFor(interceptor).send("POST", "http://example.com/api/v1/users", multipart)

        val body = interceptor.recordedCalls.single().body!!
        assertEquals(expected, body)
        assertTrue(body.indexOf(image.toByteString()) > 0)
    }

    @Test
    fun `a one-shot body is read once and the call is still answered`() {
        val interceptor = builder().recordCalls().build()
        val body = CountingBody("payload".encodeUtf8(), oneShot = true)

        val response = clientFor(interceptor).send("POST", "http://example.com/api/v1/users", body)

        assertEquals(201, response.code)
        assertEquals(1, body.writes)
        assertEquals("payload", interceptor.recordedCalls.single().body?.utf8())
    }

    @Test
    fun `a duplex body is not read and is recorded as null`() {
        val interceptor = builder().recordCalls().build()
        val body = CountingBody("stream".encodeUtf8(), duplex = true)

        clientFor(interceptor).send("POST", "http://example.com/api/v1/users", body)

        assertEquals(0, body.writes)
        assertNull(interceptor.recordedCalls.single().body)
    }

    @Test
    fun `the body is not read unless recordCalls is set`() {
        val body = CountingBody("payload".encodeUtf8())

        clientFor(builder().build()).send("POST", "http://example.com/api/v1/users", body)

        assertEquals(0, body.writes)
    }

    @Test
    fun `the body is recorded for missing mocks and simulated failures too`() {
        val strict = builder().recordCalls().failOnMissingMock().build()
        assertFailsWith<MissingMockException> {
            clientFor(strict).send("PUT", "http://example.com/unknown", "a".toRequestBody())
        }
        assertEquals("a", strict.recordedCalls.single().body?.utf8())

        val lenient = builder().recordCalls().build()
        clientFor(lenient).send("PUT", "http://example.com/unknown", "b".toRequestBody())
        assertEquals("b", lenient.recordedCalls.single().body?.utf8())

        val failing = builder().recordCalls().failurePercentage(100).build()
        clientFor(failing).send("POST", "http://example.com/api/v1/users", "c".toRequestBody())
        assertEquals("c", failing.recordedCalls.single().body?.utf8())
    }

    @Test
    fun `the body does not appear in toString`() {
        val interceptor = builder().recordCalls().build()

        clientFor(interceptor).send("POST", "http://example.com/api/v1/users", "secret".toRequestBody())

        assertEquals(
            "POST http://example.com/api/v1/users -> 201 from api/v1/users.post.json",
            interceptor.recordedCalls.single().toString(),
        )
    }
}
