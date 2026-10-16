package io.github.brodriro.okhttpjsonmock.sample

import android.content.res.AssetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.brodriro.okhttpjsonmock.OkHttpMockInterceptor
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.ListableInputStreamProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.IOException
import java.io.InputStream

/**
 * The Android snippets of the README, copied as they are, run on a device. The mock of the quick start lives in
 * `src/androidTest/assets`, so `context` is the one of the test APK. Keep both in sync with the README.
 */
@RunWith(AndroidJUnit4::class)
class ReadmeSnippetsTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    data class User(val id: Int, val name: String)

    interface UsersService {
        @GET("api/v1/users/{id}")
        suspend fun getUser(@Path("id") id: Int): User
    }

    // "Quick start", step 2.
    private val assets = InputStreamProvider { path ->
        try {
            context.assets.open(path)
        } catch (e: IOException) {
            null // no mock at this path
        }
    }

    @Test
    fun quickStart() {
        // Step 3.
        val client = OkHttpClient.Builder()
            .addInterceptor(OkHttpMockInterceptor.Builder(assets).build())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val user = runBlocking { retrofit.create(UsersService::class.java).getUser(1) } // User(id=1, name=John)

        assertEquals("User(id=1, name=John)", user.toString())
    }

    @Test
    fun assetsProviderReturnsNullForAMissingFile() {
        assertNull(assets.provide("api/v1/users/1.get.json"))
    }

    @Test
    fun androidSetupAddsTheInterceptorOnlyInDebug() {
        val builder = OkHttpClient.Builder()
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(OkHttpMockInterceptor.Builder(assets).build())
        }

        // Instrumented tests run against the debug build.
        assertTrue(builder.interceptors().single() is OkHttpMockInterceptor)
    }

    @Test
    fun theBaseUrlIsPartOfThePath() {
        val mocks = mapOf("mocks/users.json" to """[{ "id": 2, "name": "Ana" }]""")
        val interceptor = OkHttpMockInterceptor.Builder { path -> mocks[path]?.byteInputStream() }
            .noDelay()
            .recordCalls()
            .build()

        val service = Retrofit.Builder()
            .baseUrl("https://example.com/mocks/")
            .client(OkHttpClient.Builder().addInterceptor(interceptor).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UsersList::class.java)

        assertEquals(listOf(User(2, "Ana")), runBlocking { service.users() })
        assertEquals("mocks/users.json", interceptor.recordedCalls.single().mockFile)
    }

    interface UsersList {
        @GET("users")
        suspend fun users(): List<User>
    }

    // "Fewer lookups: a provider that lists its files".
    class ListedAssets(private val assets: AssetManager) : ListableInputStreamProvider {
        override fun provide(path: String): InputStream? = try {
            assets.open(path)
        } catch (e: IOException) {
            null
        }

        override fun list(): Collection<String> = mutableListOf<String>().also { walk("", it) }

        private fun walk(path: String, into: MutableList<String>) {
            val children = assets.list(path).orEmpty()
            if (children.isEmpty()) {
                into += path
            } else {
                children.forEach { walk(if (path.isEmpty()) it else "$path/$it", into) }
            }
        }
    }

    @Test
    fun listedAssetsAnswerTheQuickStart() {
        val listedAssets = ListedAssets(context.assets)
        val interceptor = OkHttpMockInterceptor.Builder(listedAssets).noDelay().recordCalls().build()
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        val user = runBlocking {
            Retrofit.Builder()
                .baseUrl("https://example.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(UsersService::class.java)
                .getUser(1)
        }

        assertTrue("api/v1/users/1.json" in listedAssets.list())
        assertEquals(User(1, "John"), user)
        assertEquals("api/v1/users/1.json", interceptor.recordedCalls.single().mockFile)
    }

    @Test
    fun listedFilesInMemory() {
        val mocks = mapOf("api/v1/users/1.json" to """{ "id": 1 }""")
        val listed = object : ListableInputStreamProvider {
            override fun provide(path: String) = mocks[path]?.byteInputStream()
            override fun list() = mocks.keys
        }

        val interceptor = OkHttpMockInterceptor.Builder(listed).noDelay().build()
        val request = okhttp3.Request.Builder().url("https://example.com/api/v1/users/1").build()

        OkHttpClient.Builder().addInterceptor(interceptor).build().newCall(request).execute().use {
            assertEquals(200, it.code)
            assertEquals("""{ "id": 1 }""", it.body!!.string())
        }
    }
}
