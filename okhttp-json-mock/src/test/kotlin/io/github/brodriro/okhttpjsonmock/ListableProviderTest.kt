package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.providers.DefaultInputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.ListableInputStreamProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A [ListableInputStreamProvider] answers like any provider, but is only asked for the files it listed. */
class ListableProviderTest {

    private val files = mapOf(
        "api/v1/users.json" to """[{"id":1}]""",
        "api/v1/users.post.json" to """{"statusCode":201,"response":{"id":4}}""",
        "api/v1/users/1.json" to """{"id":1}""",
        "api/v1/users/{id}.json" to """{"id":0}""",
        "api/v1/users/{id}/posts.get.json" to """[]""",
        "api/v1/users/page=2.get.json" to """[{"id":3}]""",
        "index.json" to """{"api":"v1"}""",
    )

    /** Serves [served] and counts how many times it was asked. */
    private open class Counting(private val served: Map<String, String>) : InputStreamProvider {
        val provideCalls = AtomicInteger()

        override fun provide(path: String): InputStream? {
            provideCalls.incrementAndGet()
            return served[path]?.byteInputStream()
        }
    }

    private class Listable(listed: Collection<String>, served: Map<String, String>) :
        Counting(served),
        ListableInputStreamProvider {
        private val entries = listed.toList()
        val listCalls = AtomicInteger()

        override fun list(): Collection<String> {
            listCalls.incrementAndGet()
            return entries
        }
    }

    private fun listable(served: Map<String, String> = files, listed: Collection<String> = served.keys) =
        Listable(listed, served)

    private fun builder(provider: InputStreamProvider) =
        OkHttpMockInterceptor.Builder(provider).noDelay().logger(MockLogger.NONE).recordCalls()

    private fun OkHttpMockInterceptor.send(method: String, path: String): Response {
        val body = if (method in setOf("POST", "PUT")) "{}".toRequestBody("application/json".toMediaType()) else null
        val request = Request.Builder().url("http://example.com/$path").method(method, body).build()
        return OkHttpClient.Builder().addInterceptor(this).build().newCall(request).execute()
    }

    private val requests = listOf(
        "GET" to "api/v1/users",
        "POST" to "api/v1/users",
        "GET" to "api/v1/users/1",
        "GET" to "api/v1/users/42",
        "DELETE" to "api/v1/users/42",
        "GET" to "api/v1/users/42/posts",
        "GET" to "api/v1/users?page=2",
        "GET" to "api/v1/users?page=9",
        "GET" to "",
        "GET" to "api/v1/orders/7/items?sort=date",
    )

    @Test
    fun `every request is answered by the same file as with a plain provider`() {
        val plain = builder(Counting(files)).build()
        val indexed = builder(listable()).build()

        requests.forEach { (method, path) ->
            val expected = plain.send(method, path).use { it.code to it.body!!.string() }
            val actual = indexed.send(method, path).use { it.code to it.body!!.string() }
            assertEquals(expected, actual, "$method /$path")
        }
        assertEquals(plain.recordedCalls.map { it.mockFile }, indexed.recordedCalls.map { it.mockFile })
    }

    @Test
    fun `the provider is only asked for the file that answers`() {
        val plain = Counting(files)
        val provider = listable()
        val plainInterceptor = builder(plain).build()
        val interceptor = builder(provider).build()

        requests.forEach { (method, path) ->
            plainInterceptor.send(method, path).close()
            interceptor.send(method, path).close()
        }

        val answered = interceptor.recordedCalls.count { it.mockFile != null }
        assertEquals(answered, provider.provideCalls.get())
        // 89 calls against 9 for these requests.
        assertTrue(plain.provideCalls.get() > 5 * answered, "plain provider: ${plain.provideCalls.get()} calls")
    }

    @Test
    fun `a request without mock never calls provide and still lists what was tried`() {
        val provider = listable()
        val plain = builder(Counting(files)).build()
        val interceptor = builder(provider).build()

        val expected = plain.send("GET", "api/v1/orders/7/items").use { it.body!!.string() }
        val response = interceptor.send("GET", "api/v1/orders/7/items")

        assertEquals(404, response.code)
        assertEquals(expected, response.body!!.string())
        assertEquals(0, provider.provideCalls.get())
    }

    @Test
    fun `failOnMissingMock reports the same tried files`() {
        val plain = builder(Counting(files)).failOnMissingMock().build()
        val interceptor = builder(listable()).failOnMissingMock().build()

        val expected = assertFailsWith<MissingMockException> { plain.send("GET", "api/v2/ping") }
        val error = assertFailsWith<MissingMockException> { interceptor.send("GET", "api/v2/ping") }

        assertEquals(expected.triedFiles, error.triedFiles)
        assertEquals(expected.message, error.message)
    }

    @Test
    fun `list is called once, also from parallel requests`() {
        val provider = listable()
        val interceptor = builder(provider).build()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val results = (1..32).map { i ->
                pool.submit<Int> {
                    start.await()
                    interceptor.send("GET", "api/v1/users/$i").use { it.code }
                }
            }
            start.countDown()
            assertTrue(results.all { it.get(5, TimeUnit.SECONDS) == 200 })
        } finally {
            pool.shutdownNow()
        }

        assertEquals(1, provider.listCalls.get())
    }

    @Test
    fun `a listed file the provider cannot open falls through to the next one`() {
        val served = files - "api/v1/users/1.json"
        val provider = listable(served = served, listed = files.keys)
        val interceptor = builder(provider).build()

        val response = interceptor.send("GET", "api/v1/users/1")

        assertEquals("""{"id":0}""", response.body!!.string())
        assertEquals("api/v1/users/{id}.json", interceptor.recordedCalls.single().mockFile)
        assertEquals(2, provider.provideCalls.get())
    }

    @Test
    fun `a file that is not listed is never served`() {
        val provider = listable(served = files, listed = files.keys - "api/v1/users/1.json")
        val interceptor = builder(provider).build()

        interceptor.send("GET", "api/v1/users/1").close()

        assertEquals("api/v1/users/{id}.json", interceptor.recordedCalls.single().mockFile)
    }

    @Test
    fun `a failing list fails that request and is tried again on the next one`() {
        val attempts = AtomicInteger()
        val provider = object : ListableInputStreamProvider {
            override fun provide(path: String): InputStream? = files[path]?.byteInputStream()

            override fun list(): Collection<String> {
                if (attempts.incrementAndGet() == 1) throw IOException("assets not ready")
                return files.keys
            }
        }
        val interceptor = builder(provider).build()

        val error = assertFailsWith<IOException> { interceptor.send("GET", "api/v1/users/1") }
        assertEquals("assets not ready", error.message)
        assertEquals(200, interceptor.send("GET", "api/v1/users/1").code)
        assertEquals(2, attempts.get())
    }

    @Test
    fun `listed paths include the basePath`() {
        val served = files.mapKeys { (path, _) -> "mocks/$path" }
        val provider = listable(served = served)
        val interceptor = builder(provider).basePath("mocks/").build()

        interceptor.send("GET", "api/v1/users/42").close()

        assertEquals("mocks/api/v1/users/{id}.json", interceptor.recordedCalls.single().mockFile)
        assertEquals(1, provider.provideCalls.get())
    }

    @Test
    fun `with several extensions only the matching file is opened`() {
        val served = mapOf("images/{id}.png" to "png", "images/1.jpg" to "jpg")
        val provider = listable(served = served)
        val interceptor = builder(provider).extensions("json", "jpg", "png").build()

        val first = interceptor.send("GET", "images/1")
        val other = interceptor.send("GET", "images/2")

        assertEquals("image/jpeg", first.header("Content-Type"))
        assertEquals("image/png", other.header("Content-Type"))
        assertEquals(2, provider.provideCalls.get())
    }

    @Test
    fun `the interceptors built with the 3_0 constructors use the list too`() {
        val provider = listable()
        val interceptor = OkHttpMockInterceptor(provider, 0, 0, 0)

        assertEquals(200, interceptor.send("GET", "api/v1/users/42").code)
        assertEquals(1, provider.provideCalls.get())
    }

    @Test
    fun `the filesystem provider does not list, so files added later are still found`() {
        val provider: InputStreamProvider = DefaultInputStreamProvider()

        assertFalse(provider is ListableInputStreamProvider)
    }
}
