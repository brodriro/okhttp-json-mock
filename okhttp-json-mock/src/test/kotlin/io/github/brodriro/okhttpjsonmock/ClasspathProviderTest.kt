package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.providers.ClasspathInputStreamProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mocks read from the classpath: `src/test/resources/classpath-mocks` and a jar built by the test. */
class ClasspathProviderTest {

    private val tempDir: File = Files.createTempDirectory("classpath-mocks").toFile()

    @AfterTest
    fun deleteTempDir() {
        tempDir.deleteRecursively()
    }

    private fun interceptor(provider: ClasspathInputStreamProvider, basePath: String = "classpath-mocks/") =
        OkHttpMockInterceptor.Builder(provider).basePath(basePath).noDelay().logger(MockLogger.NONE).recordCalls()
            .build()

    private fun OkHttpMockInterceptor.send(method: String, path: String): Response {
        val body = if (method == "POST") "{}".toRequestBody("application/json".toMediaType()) else null
        val request = Request.Builder().url("http://example.com/$path").method(method, body).build()
        return OkHttpClient.Builder().addInterceptor(this).build().newCall(request).execute()
    }

    @Test
    fun `the classpath provider serves the test resources`() {
        val interceptor = interceptor(ClasspathInputStreamProvider())
        val answers = listOf(
            "GET" to "",
            "POST" to "api/v1/users",
            "GET" to "api/v1/users/1",
            "GET" to "api/v1/users/42",
        )
            .map { (method, path) -> interceptor.send(method, path).use { it.code to it.body!!.string() } }

        assertEquals(
            listOf(
                200 to """{"api":"v1"}""" + "\n",
                201 to """{"id":3}""",
                200 to """{"id":1,"name":"John"}""" + "\n",
                200 to """{"id":0,"name":"any"}""" + "\n",
            ),
            answers,
        )
        assertEquals("classpath-mocks/api/v1/users/{id}.json", interceptor.recordedCalls.last().mockFile)
        assertEquals(404, interceptor.send("GET", "api/v1/orders").code)
    }

    @Test
    fun `a missing resource is null`() {
        assertNull(ClasspathInputStreamProvider().provide("classpath-mocks/nothing.json"))
    }

    @Test
    fun `a given class loader is used, here a jar without folder entries`() {
        val jar = File(tempDir, "mocks.jar")
        JarOutputStream(jar.outputStream()).use { out ->
            out.putNextEntry(JarEntry("mocks/users/{id}.json"))
            out.write("{}".toByteArray())
        }
        val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), null)

        val interceptor = interceptor(ClasspathInputStreamProvider(loader), basePath = "mocks/")

        assertEquals("{}", interceptor.send("GET", "users/7").body!!.string())
        assertEquals("mocks/users/{id}.json", interceptor.recordedCalls.single().mockFile)
        // Not in the jar, and the class loader has no parent that could have it.
        assertNull(ClasspathInputStreamProvider(loader).provide("classpath-mocks/index.json"))
    }
}
