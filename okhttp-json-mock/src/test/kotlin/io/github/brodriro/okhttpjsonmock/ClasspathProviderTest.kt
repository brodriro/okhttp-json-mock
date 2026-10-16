package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.providers.ClasspathInputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.ListableInputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.ListedClasspathInputStreamProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.Collections
import java.util.Enumeration
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mocks read from the classpath: `src/test/resources/classpath-mocks` and jars built by the tests. */
class ClasspathProviderTest {

    private val tempDir: File = Files.createTempDirectory("classpath-mocks").toFile()

    @AfterTest
    fun deleteTempDir() {
        tempDir.deleteRecursively()
    }

    private val expectedFiles = setOf(
        "classpath-mocks/index.json",
        "classpath-mocks/api/v1/users.json",
        "classpath-mocks/api/v1/users.post.json",
        "classpath-mocks/api/v1/users/1.json",
        "classpath-mocks/api/v1/users/{id}.json",
    )

    private val requests = listOf(
        "GET" to "",
        "GET" to "api/v1/users",
        "POST" to "api/v1/users",
        "GET" to "api/v1/users/1",
        "GET" to "api/v1/users/42",
        "GET" to "api/v1/orders",
    )

    /** Counts the `provide` calls of [provider], keeping it listable when it is. */
    private class Counted(val provider: InputStreamProvider) {
        var calls = 0
        val counted: InputStreamProvider = if (provider is ListableInputStreamProvider) {
            object : ListableInputStreamProvider {
                override fun provide(path: String): InputStream? = provider.provide(path).also { calls++ }
                override fun list(): Collection<String> = provider.list()
            }
        } else {
            InputStreamProvider { path -> provider.provide(path).also { calls++ } }
        }
    }

    private fun interceptor(provider: InputStreamProvider, basePath: String = "classpath-mocks/") =
        OkHttpMockInterceptor.Builder(provider).basePath(basePath).noDelay().logger(MockLogger.NONE).recordCalls()
            .build()

    private fun OkHttpMockInterceptor.send(method: String, path: String): Response {
        val body = if (method == "POST") "{}".toRequestBody("application/json".toMediaType()) else null
        val request = Request.Builder().url("http://example.com/$path").method(method, body).build()
        return OkHttpClient.Builder().addInterceptor(this).build().newCall(request).execute()
    }

    private fun OkHttpMockInterceptor.answers() =
        requests.map { (method, path) -> send(method, path).use { it.code to it.body!!.string() } }

    /** A jar with [entries] (name to text); folder entries are added for every parent, as Gradle does. */
    private fun jar(name: String, entries: Map<String, String>, folderEntries: Boolean = true): File {
        val file = File(tempDir, name)
        JarOutputStream(file.outputStream()).use { jar ->
            val folders = entries.keys.flatMap { entry ->
                entry.split('/').dropLast(1).runningReduce { parent, segment -> "$parent/$segment" }
            }.toSortedSet()
            if (folderEntries) folders.forEach { jar.putNextEntry(JarEntry("$it/")) }
            entries.forEach { (entry, text) ->
                jar.putNextEntry(JarEntry(entry))
                jar.write(text.toByteArray())
            }
        }
        return file
    }

    private fun loaderOf(vararg jars: File) = URLClassLoader(jars.map { it.toURI().toURL() }.toTypedArray(), null)

    @Test
    fun `the classpath provider serves the test resources`() {
        val answers = interceptor(ClasspathInputStreamProvider()).answers()

        assertEquals(200 to """{"api":"v1"}""" + "\n", answers[0])
        assertEquals(201 to """{"id":3}""", answers[2])
        assertEquals(200 to """{"id":1,"name":"John"}""" + "\n", answers[3])
        assertEquals(200 to """{"id":0,"name":"any"}""" + "\n", answers[4])
        assertEquals(404, answers[5].first)
    }

    @Test
    fun `a missing resource is null`() {
        assertNull(ClasspathInputStreamProvider().provide("classpath-mocks/nothing.json"))
    }

    @Test
    fun `the listed provider lists the folder on disk`() {
        assertEquals(expectedFiles, ListedClasspathInputStreamProvider("classpath-mocks").list().toSet())
    }

    @Test
    fun `the listed provider answers like the plain one and opens only the file that answers`() {
        val plain = Counted(ClasspathInputStreamProvider())
        val listed = Counted(ListedClasspathInputStreamProvider("classpath-mocks"))

        assertEquals(interceptor(plain.counted).answers(), interceptor(listed.counted).answers())
        assertEquals(5, listed.calls)
        assertTrue(plain.calls > listed.calls, "plain: ${plain.calls}, listed: ${listed.calls}")
    }

    @Test
    fun `the listed provider lists a folder inside a jar`() {
        val jar = jar(
            "mocks.jar",
            mapOf(
                "mocks/users.json" to "[]",
                "mocks/users/{id}.json" to "{}",
                "other/a.json" to "x",
            ),
        )
        val provider = ListedClasspathInputStreamProvider("mocks", loaderOf(jar))

        assertEquals(setOf("mocks/users.json", "mocks/users/{id}.json"), provider.list().toSet())
        val interceptor = interceptor(provider, basePath = "mocks/")
        assertEquals("{}", interceptor.send("GET", "users/7").body!!.string())
        assertEquals("mocks/users/{id}.json", interceptor.recordedCalls.single().mockFile)
    }

    @Test
    fun `a folder in several places of the classpath is listed from all of them`() {
        val main = jar("main.jar", mapOf("mocks/users.json" to "[]"))
        val extra = jar("extra.jar", mapOf("mocks/orders.json" to "[]", "mocks/users.json" to "[1]"))

        val provider = ListedClasspathInputStreamProvider("mocks", loaderOf(main, extra))

        assertEquals(listOf("mocks/users.json", "mocks/orders.json"), provider.list().toList())
        // Like the class loader, the first jar wins for a name that is in both.
        assertEquals("[]", provider.provide("mocks/users.json")!!.use { it.readBytes().decodeToString() })
    }

    @Test
    fun `slashes around the root are ignored and an empty root is rejected`() {
        assertEquals("mocks", ListedClasspathInputStreamProvider("/mocks/").root)
        assertFailsWith<IllegalArgumentException> { ListedClasspathInputStreamProvider("/") }
        assertFailsWith<IllegalArgumentException> { ListedClasspathInputStreamProvider("") }
    }

    @Test
    fun `a root that is not on the classpath fails the request with a clear message`() {
        val interceptor =
            interceptor(ListedClasspathInputStreamProvider("no-such-folder"), basePath = "no-such-folder/")

        val error = assertFailsWith<IOException> { interceptor.send("GET", "users") }

        assertEquals("No classpath folder named 'no-such-folder'", error.message)
    }

    @Test
    fun `a jar without folder entries cannot be listed`() {
        val jar = jar("flat.jar", mapOf("mocks/users.json" to "[]"), folderEntries = false)

        assertFailsWith<IOException> { ListedClasspathInputStreamProvider("mocks", loaderOf(jar)).list() }
        // The plain provider does not need the folder entry.
        assertTrue(ClasspathInputStreamProvider(loaderOf(jar)).provide("mocks/users.json") != null)
    }

    @Test
    fun `a class loader that does not expose files or jars cannot be listed`() {
        val remote = object : ClassLoader(null) {
            override fun getResources(name: String): Enumeration<URL> =
                Collections.enumeration(listOf(URL("http://example.com/$name")))
        }

        val error = assertFailsWith<IOException> { ListedClasspathInputStreamProvider("mocks", remote).list() }

        assertTrue("use ClasspathInputStreamProvider" in error.message!!, error.message)
    }
}
