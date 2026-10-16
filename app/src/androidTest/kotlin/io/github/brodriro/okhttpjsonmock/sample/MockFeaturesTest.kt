package io.github.brodriro.okhttpjsonmock.sample

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.brodriro.okhttpjsonmock.MissingMockException
import io.github.brodriro.okhttpjsonmock.OkHttpMockInterceptor
import io.github.brodriro.okhttpjsonmock.sample.mock.Playground
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Each feature of the library on a device, answered from the real `assets/` of the sample. */
@RunWith(AndroidJUnit4::class)
class MockFeaturesTest {

    private val provider = Playground(InstrumentationRegistry.getInstrumentation().targetContext.assets).provider

    private fun builder() = OkHttpMockInterceptor.Builder(provider).noDelay().logger { }.recordCalls()

    private fun OkHttpMockInterceptor.send(method: String, path: String): Response {
        val body = if (method in setOf("POST", "PUT")) "{}".toRequestBody("application/json".toMediaType()) else null
        val request = Request.Builder().url("https://api.example.com/$path").method(method, body).build()
        return OkHttpClient.Builder().addInterceptor(this).build().newCall(request).execute()
    }

    private fun OkHttpMockInterceptor.lastMock() = recordedCalls.last().mockFile

    @Test
    fun queryFileFallbackAndSortedParameters() {
        val interceptor = builder().build()

        assertEquals(3, JSONArray(interceptor.send("GET", "api/v1/users?page=1").body!!.string()).length())
        assertEquals("api/v1/users/page=1.json", interceptor.lastMock())

        val page2 = interceptor.send("GET", "api/v1/users?page=2")
        assertEquals("3", page2.header("X-Total-Count"))
        assertEquals("[]", page2.body!!.string())

        interceptor.send("GET", "api/v1/users?page=7").close()
        assertEquals("api/v1/users.json", interceptor.lastMock())

        interceptor.send("GET", "api/v1/users?sort=name&role=admin").close()
        assertEquals("api/v1/users/role=admin&sort=name.get.json", interceptor.lastMock())

        interceptor.send("GET", "api/v1/users/1/phoneNumbers").close()
        assertEquals("api/v1/users/1/phoneNumbers.json", interceptor.lastMock())
    }

    @Test
    fun eachMethodHasItsOwnMock() {
        val interceptor = builder().build()

        assertEquals("Doe", JSONObject(interceptor.send("GET", "api/v1/users/1").body!!.string()).getString("lastName"))
        assertEquals(
            "Smith",
            JSONObject(interceptor.send("PUT", "api/v1/users/1").body!!.string()).getString("lastName"),
        )
        assertEquals(204, interceptor.send("DELETE", "api/v1/users/1").code)
        assertEquals(201, interceptor.send("POST", "api/v1/users").code)
        assertEquals(
            listOf(
                "api/v1/users/1.json",
                "api/v1/users/1.put.json",
                "api/v1/users/1.delete.json",
                "api/v1/users.post.json",
            ),
            interceptor.recordedCalls.map { it.mockFile },
        )
    }

    @Test
    fun idFileAnswersAnyIdAndTheExactFileWins() {
        val interceptor = builder().build()

        assertEquals(
            "Generic",
            JSONObject(interceptor.send("GET", "api/v1/users/42").body!!.string()).getString("name"),
        )
        assertEquals("api/v1/users/{id}.json", interceptor.lastMock())
        interceptor.send("GET", "api/v1/users/3").close()
        assertEquals("api/v1/users/3.json", interceptor.lastMock())
    }

    @Test
    fun envelopesSetStatusHeadersAndBody() {
        val interceptor = builder().build()

        val created = interceptor.send("POST", "api/v1/users")
        assertEquals("Created", created.message)
        assertEquals("/api/v1/users/4", created.header("Location"))

        val deleted = interceptor.send("DELETE", "api/v1/users/1")
        assertEquals(0L, deleted.body!!.contentLength())

        val missing = interceptor.send("GET", "api/v1/users/9")
        assertEquals(404, missing.code)
        assertEquals("User 9 not found", JSONObject(missing.body!!.string()).getString("error"))

        val invalid = interceptor.send("POST", "api/v1/sessions")
        assertEquals(422, invalid.code)
        assertEquals("invalid_credentials", JSONObject(invalid.body!!.string()).getString("error"))
    }

    @Test
    fun envelopeDelayReplacesTheLatencyEvenWithNoDelay() {
        val interceptor = builder().build()

        val start = System.nanoTime()
        val report = interceptor.send("GET", "api/v1/reports/monthly")
        val elapsed = (System.nanoTime() - start) / 1_000_000

        assertTrue("took $elapsed ms", elapsed >= 2500)
        assertEquals("2026-10", report.header("X-Report-Id"))
        assertEquals(182, JSONObject(report.body!!.string()).getInt("orders"))
    }

    @Test
    fun missingMockAnswers404OrThrowsWhenStrict() {
        val missing = builder().build().send("GET", "api/v1/orders")
        assertEquals(404, missing.code)
        assertTrue(missing.body!!.string().contains("Tried: api/v1/orders.get.json, api/v1/orders.json"))

        val strict = builder().failOnMissingMock().build()
        val error = assertThrows(MissingMockException::class.java) { strict.send("GET", "api/v1/orders") }
        assertEquals("api/v1/orders.get.json", error.triedFiles.first())
        assertNull(strict.recordedCalls.single().statusCode)
    }

    @Test
    fun simulatedFailuresUseTheConfiguredStatus() {
        val interceptor = builder().failurePercentage(100).failureCode(503).build()

        val response = interceptor.send("GET", "api/v1/ping")

        assertEquals(503, response.code)
        assertEquals("Service Unavailable", response.message)
        assertEquals("", response.body!!.string())
    }

    @Test
    fun latencyWindowIsApplied() {
        val interceptor = OkHttpMockInterceptor.Builder(provider).delay(300, 300).logger { }.build()

        val start = System.nanoTime()
        interceptor.send("GET", "api/v1/ping").close()

        assertTrue((System.nanoTime() - start) / 1_000_000 >= 300)
    }

    @Test
    fun recordedCallsAndLogger() {
        val lines = mutableListOf<String>()
        val interceptor = OkHttpMockInterceptor.Builder(provider).noDelay().logger { lines += it }.recordCalls().build()

        interceptor.send("GET", "api/v1/ping").close()
        interceptor.send("GET", "api/v1/orders").close()

        assertEquals(listOf(200, 404), interceptor.recordedCalls.map { it.statusCode })
        assertEquals(listOf("api/v1/ping.json", null), interceptor.recordedCalls.map { it.mockFile })
        assertEquals("GET /api/v1/ping -> 200 from api/v1/ping.json", lines.first())
        interceptor.clearRecordedCalls()
        assertTrue(interceptor.recordedCalls.isEmpty())
    }
}
