package io.github.brodriro.okhttpjsonmock.sample.mock

import android.content.res.AssetManager
import io.github.brodriro.okhttpjsonmock.MockLogger
import io.github.brodriro.okhttpjsonmock.OkHttpMockInterceptor
import io.github.brodriro.okhttpjsonmock.providers.ClasspathInputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.ListableInputStreamProvider
import io.github.brodriro.okhttpjsonmock.providers.ListedClasspathInputStreamProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/** How the simulated network behaves. Every request is sent with the settings in place at that moment. */
data class NetworkSettings(
    val latency: Latency = Latency.SHORT,
    val failurePercentage: Int = 0,
    val failureCode: Int = 504,
    val failOnMissingMock: Boolean = false,
)

/** Latency windows offered by the sample, passed to [OkHttpMockInterceptor.Builder.delay]. */
enum class Latency(val label: String, val minMillis: Int, val maxMillis: Int) {
    NONE("None", 0, 0),
    SHORT("Short", 200, 600),
    DEFAULT("Default", 500, 1500),
    SLOW("Slow", 2000, 4000),
    ;

    val window: String get() = if (this == NONE) "Answers immediately" else "$minMillis–$maxMillis ms"
}

/**
 * One request of the catalog.
 *
 * @property extensions passed to [OkHttpMockInterceptor.Builder.extensions].
 * @property strict sends it with [OkHttpMockInterceptor.Builder.failOnMissingMock] whatever the settings say.
 * @property source where the interceptor reads the mock files from.
 */
data class SampleRequest(
    val method: String,
    val path: String,
    val body: String? = null,
    val extensions: List<String> = listOf("json"),
    val strict: Boolean = false,
    val source: MockSource = MockSource.ASSETS,
)

/**
 * The providers the sample can read mocks with.
 *
 * @property basePath passed to [OkHttpMockInterceptor.Builder.basePath]: Java resources live under `mocks/`.
 */
enum class MockSource(val basePath: String = "") {
    ASSETS,
    LISTED_ASSETS,
    CLASSPATH("mocks/"),
    LISTED_CLASSPATH("mocks/"),
}

/** Builds an interceptor for each request and runs it against the mock files in `assets/`. */
class Playground(private val assets: AssetManager) {

    /** The provider every Android app writes: `null` when there is no asset at that path. */
    val provider = InputStreamProvider { path ->
        try {
            assets.open(path)
        } catch (e: IOException) {
            null
        }
    }

    /** The same assets, listed once so the interceptor only opens the file that answers. */
    val listedProvider = ListedAssets(assets)

    /**
     * The Java resources of the APK (`src/main/resources/mocks`), with the providers built into the library. The
     * listed one fails: an APK has no folder entries to find `mocks` with (kept to show it in the tests).
     */
    private val classpathProvider = ClasspathInputStreamProvider()
    private val listedClasspathProvider = ListedClasspathInputStreamProvider("mocks")

    private fun providerFor(source: MockSource): InputStreamProvider = when (source) {
        MockSource.ASSETS -> provider
        MockSource.LISTED_ASSETS -> listedProvider
        MockSource.CLASSPATH -> classpathProvider
        MockSource.LISTED_CLASSPATH -> listedClasspathProvider
    }

    fun interceptor(
        settings: NetworkSettings,
        provider: InputStreamProvider = this.provider,
        extensions: List<String> = listOf("json"),
        strict: Boolean = false,
        basePath: String = "",
        logger: MockLogger = MockLogger.NONE,
    ): OkHttpMockInterceptor = OkHttpMockInterceptor.Builder(provider)
        .basePath(basePath)
        .delay(settings.latency.minMillis, settings.latency.maxMillis)
        .failurePercentage(settings.failurePercentage)
        .failureCode(settings.failureCode)
        .extensions(*extensions.toTypedArray())
        .logger(logger)
        .recordCalls()
        .apply { if (strict || settings.failOnMissingMock) failOnMissingMock() }
        .build()

    /** Sends [request] through a fresh interceptor and describes what came back. Never throws. */
    suspend fun send(request: SampleRequest, settings: NetworkSettings): Exchange = withContext(Dispatchers.IO) {
        val logLines = mutableListOf<String>()
        val providerCalls = AtomicInteger()
        val counted = counting(providerFor(request.source), providerCalls)
        val interceptor = interceptor(settings, counted, request.extensions, request.strict, request.source.basePath) {
            logLines += it
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val body = request.body?.toRequestBody(JSON)
            ?: if (request.method in METHODS_WITH_BODY) "{}".toRequestBody(JSON) else null
        val httpRequest = Request.Builder().url(BASE_URL + request.path).method(request.method, body).build()
        val start = System.nanoTime()
        try {
            client.newCall(httpRequest).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                val contentType = response.header("Content-Type")
                Exchange(
                    request = request,
                    statusCode = response.code,
                    reason = response.message,
                    contentType = contentType,
                    headers = response.headers.map { it.first to it.second },
                    body = bytes,
                    preview = BodyPreview.of(bytes, contentType),
                    mockFile = interceptor.recordedCalls.lastOrNull()?.mockFile,
                    sentBody = interceptor.recordedCalls.lastOrNull()?.body?.utf8(),
                    elapsedMillis = (System.nanoTime() - start) / 1_000_000,
                    providerCalls = providerCalls.get(),
                    error = null,
                    log = logLines.toList(),
                )
            }
        } catch (e: IOException) {
            Exchange(
                request = request,
                statusCode = null,
                reason = null,
                contentType = null,
                headers = emptyList(),
                body = ByteArray(0),
                preview = BodyPreview.Empty,
                mockFile = null,
                // A call that fails for a missing mock is recorded too, with what was sent.
                sentBody = interceptor.recordedCalls.lastOrNull()?.body?.utf8(),
                elapsedMillis = (System.nanoTime() - start) / 1_000_000,
                providerCalls = providerCalls.get(),
                error = "${e.javaClass.simpleName}: ${e.message}",
                log = logLines.toList(),
            )
        }
    }

    /** Counts the `provide` calls of [provider], keeping it listable when it is. */
    private fun counting(provider: InputStreamProvider, calls: AtomicInteger): InputStreamProvider =
        if (provider is ListableInputStreamProvider) {
            object : ListableInputStreamProvider {
                override fun provide(path: String): InputStream? =
                    provider.provide(path).also { calls.incrementAndGet() }

                override fun list(): Collection<String> = provider.list()
            }
        } else {
            InputStreamProvider { path -> provider.provide(path).also { calls.incrementAndGet() } }
        }

    companion object {
        const val BASE_URL = "https://api.example.com/"
        private val JSON = "application/json".toMediaType()
        private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")
    }
}
