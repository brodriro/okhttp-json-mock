import io.github.brodriro.okhttpjsonmock.MockLogger
import io.github.brodriro.okhttpjsonmock.OkHttpMockInterceptor
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.system.exitProcess

private val files = mapOf(
    "api/v1/users.json" to """[{"id":1}]""",
    "api/v1/users.post.json" to
        """{"statusCode":201,"headers":{"Location":"/api/v1/users/4"},"response":{"id":4}}""",
    "api/v1/users/{id}.json" to """{"id":0}""",
    "api/v1/users/{id}.delete.json" to """{"statusCode":204}""",
)

private var failures = 0

private fun check(ok: Boolean, what: String) {
    println((if (ok) "ok: " else "FAILED: ") + what)
    if (!ok) failures++
}

/** Uses the published library from Kotlin and, through [JavaConsumer], from Java. */
fun main() {
    val provider = InputStreamProvider { path -> files[path]?.byteInputStream() }
    val logged = mutableListOf<String>()
    val client = OkHttpClient.Builder()
        .addInterceptor(OkHttpMockInterceptor.Builder(provider).noDelay().logger(MockLogger { logged += it }).build())
        .build()
    val url = "http://example.com/api/v1/users"

    client.newCall(Request.Builder().url(url).build()).execute().use {
        check(it.code == 200 && it.body!!.string() == """[{"id":1}]""", "GET plain array answers 200")
    }
    val body = "{}".toRequestBody("application/json".toMediaType())
    client.newCall(Request.Builder().url(url).post(body).build()).execute().use {
        check(
            it.code == 201 && it.header("Location") == "/api/v1/users/4" && it.body!!.string() == """{"id":4}""",
            "POST envelope answers 201 with a Location header",
        )
    }
    client.newCall(Request.Builder().url("$url/42").build()).execute().use {
        check(it.code == 200 && it.body!!.string() == """{"id":0}""", "GET with an {id} file answers any id")
    }
    client.newCall(Request.Builder().url("$url/42").delete().build()).execute().use {
        check(it.code == 204 && it.body!!.string().isEmpty(), "DELETE with an {id} method file answers 204")
    }
    client.newCall(Request.Builder().url("http://example.com/missing").build()).execute().use {
        check(it.code == 404 && it.body!!.string().contains("Tried:"), "missing mock answers 404")
    }
    check(logged.size == 5, "custom logger receives one line per call")

    val failing = OkHttpClient.Builder()
        .addInterceptor(OkHttpMockInterceptor.Builder(provider).noDelay().failurePercentage(100).failureCode(503).build())
        .build()
    failing.newCall(Request.Builder().url(url).build()).execute().use {
        check(it.code == 503, "simulated failure answers the configured code")
    }

    JavaConsumer.run().forEach { (what, ok) -> check(ok, "Java: $what") }

    if (failures > 0) exitProcess(1)
}
