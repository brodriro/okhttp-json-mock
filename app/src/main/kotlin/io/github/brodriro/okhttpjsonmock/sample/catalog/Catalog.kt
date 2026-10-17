package io.github.brodriro.okhttpjsonmock.sample.catalog

import io.github.brodriro.okhttpjsonmock.sample.mock.MockSource
import io.github.brodriro.okhttpjsonmock.sample.mock.SampleRequest

/** What a feature screen shows besides its description. */
enum class FeatureKind { REQUESTS, RETROFIT, NETWORK, CALLS }

/**
 * One feature of the library, as the catalog presents it.
 *
 * @property glyph short monospace label of the tile in the catalog.
 */
data class Feature(
    val id: String,
    val glyph: String,
    val title: String,
    val summary: String,
    val description: String,
    val kind: FeatureKind = FeatureKind.REQUESTS,
    val examples: List<Example> = emptyList(),
)

/**
 * A request that shows the feature.
 *
 * @property answeredBy the mock file expected to answer, shown before the request is sent.
 */
data class Example(val id: String, val title: String, val request: SampleRequest, val answeredBy: String)

/** Every feature of the library. The catalog lists them in this order. */
object Catalog {

    /** Extensions configured for the "Not only JSON" examples. */
    val FILE_EXTENSIONS = listOf(
        "json", "txt", "html", "htm", "csv", "xml", "svg", "png", "jpg", "jpeg", "gif", "webp", "pdf", "yaml",
    )

    private fun get(path: String) = SampleRequest("GET", path)

    private fun file(name: String, title: String) = Example(
        id = "file-$name",
        title = title,
        request = SampleRequest(
            "GET",
            "api/v1/files/${name.substringBefore('.')}",
            extensions = listOf(name.substringAfter('.')),
        ),
        answeredBy = "api/v1/files/$name",
    )

    val features = listOf(
        Feature(
            id = "paths",
            glyph = "/?",
            title = "Paths and queries",
            summary = "The URL path is the file path",
            description = "A request is answered by the file whose path mirrors its URL. A query string is a " +
                "file inside the folder of the path, with a fallback to the file for the path and parameters " +
                "accepted in any order. The root path / is looked up as index.",
            examples = listOf(
                Example("query", "File for this query", get("api/v1/users?page=1"), "api/v1/users/page=1.json"),
                Example(
                    "query-method",
                    "Query file tied to GET",
                    get("api/v1/users?page=2"),
                    "api/v1/users/page=2.get.json",
                ),
                Example("query-fallback", "Any other page", get("api/v1/users?page=7"), "api/v1/users.json"),
                Example(
                    "query-sorted",
                    "Parameters in any order",
                    get("api/v1/users?sort=name&role=admin"),
                    "api/v1/users/role=admin&sort=name.get.json",
                ),
                Example(
                    "nested",
                    "Nested path",
                    get("api/v1/users/1/phoneNumbers"),
                    "api/v1/users/1/phoneNumbers.json",
                ),
                Example("root", "Root path", get(""), "index.json"),
                Example("root-post", "Root path, its own POST mock", SampleRequest("POST", ""), "index.post.json"),
            ),
        ),
        Feature(
            id = "methods",
            glyph = "GET",
            title = "One URL, many methods",
            summary = "users.get.json, users.post.json…",
            description = "The HTTP method in lower case before the extension ties a file to that method, so one " +
                "URL answers each method with its own mock. A file without it answers every method.",
            examples = listOf(
                Example("get", "Read", get("api/v1/users/1"), "api/v1/users/1.json"),
                Example(
                    "put",
                    "Update",
                    SampleRequest("PUT", "api/v1/users/1", """{"lastName":"Smith"}"""),
                    "api/v1/users/1.put.json",
                ),
                Example("delete", "Delete", SampleRequest("DELETE", "api/v1/users/1"), "api/v1/users/1.delete.json"),
                Example(
                    "post",
                    "Create on the collection",
                    SampleRequest("POST", "api/v1/users", """{"name":"Ana"}"""),
                    "api/v1/users.post.json",
                ),
            ),
        ),
        Feature(
            id = "ids",
            glyph = "{id}",
            title = "Paths with ids",
            summary = "users/{id}.json answers any id",
            description = "A file or folder named {id} answers any value of that path segment when there is no " +
                "file for the exact path. A file for the exact path always wins.",
            examples = listOf(
                Example("any-id", "Any id", get("api/v1/users/42"), "api/v1/users/{id}.json"),
                Example("exact-id", "Exact file wins", get("api/v1/users/3"), "api/v1/users/3.json"),
            ),
        ),
        Feature(
            id = "responses",
            glyph = "201",
            title = "Status, headers and latency",
            summary = "Written inside the mock file",
            description = "A file that wraps its response in { \"statusCode\", \"headers\", \"delayMs\", " +
                "\"response\" } answers with that status, those headers and that latency.",
            examples = listOf(
                Example(
                    "created",
                    "201 with a Location header",
                    SampleRequest("POST", "api/v1/users", """{"name":"Ana"}"""),
                    "api/v1/users.post.json",
                ),
                Example(
                    "no-content",
                    "204 without a body",
                    SampleRequest("DELETE", "api/v1/users/1"),
                    "api/v1/users/1.delete.json",
                ),
                Example("not-found", "404 with an error body", get("api/v1/users/9"), "api/v1/users/9.get.json"),
                Example(
                    "unprocessable",
                    "422 on a form",
                    SampleRequest("POST", "api/v1/sessions", """{"email":"ana@example.com"}"""),
                    "api/v1/sessions.post.json",
                ),
                Example(
                    "slow",
                    "2.5 s latency and custom headers",
                    get("api/v1/reports/monthly"),
                    "api/v1/reports/monthly.json",
                ),
            ),
        ),
        Feature(
            id = "files",
            glyph = ".png",
            title = "Not only JSON",
            summary = "Images, XML, text, PDF…",
            description = "Builder.extensions(…) lists the extensions of mock files. The response gets the " +
                "Content-Type of the extension that matched and the file is returned byte for byte. Each " +
                "example below lists only its own extension.",
            examples = listOf(
                file("logo.png", "PNG image"),
                file("photo.jpg", "JPEG image"),
                file("avatar.jpeg", "JPEG image, .jpeg"),
                file("banner.webp", "WebP image"),
                file("sticker.gif", "GIF image"),
                file("manual.pdf", "PDF document"),
                file("icon.svg", "SVG image"),
                file("feed.xml", "XML feed"),
                file("terms.html", "HTML page"),
                file("terms.htm", "HTML page, .htm"),
                file("prices.csv", "CSV table"),
                file("notes.txt", "Plain text"),
                file("data.json", "JSON"),
                file("config.yaml", "Unknown extension"),
            ),
        ),
        Feature(
            id = "classpath",
            glyph = "cp",
            title = "Classpath resources",
            summary = "ClasspathInputStreamProvider",
            description = "A provider built into the library reads mocks from the classpath: src/test/resources " +
                "in JVM tests, or the Java resources of a jar or an APK. These examples read the Java resources of " +
                "this app (src/main/resources/mocks) with basePath(\"mocks/\").",
            examples = listOf(
                Example(
                    "cp-plain",
                    "ClasspathInputStreamProvider",
                    SampleRequest("GET", "api/v1/ping", source = MockSource.CLASSPATH),
                    "mocks/api/v1/ping.json",
                ),
                Example(
                    "cp-id",
                    "ClasspathInputStreamProvider, {id} file",
                    SampleRequest("GET", "api/v1/users/42", source = MockSource.CLASSPATH),
                    "mocks/api/v1/users/{id}.json",
                ),
            ),
        ),
        Feature(
            id = "missing",
            glyph = "404",
            title = "When no mock matches",
            summary = "A 404 that lists what was tried",
            description = "A request without a mock answers 404 with the files that were looked for. With " +
                "failOnMissingMock() it fails with a MissingMockException instead, which is clearer in tests.",
            examples = listOf(
                Example("missing-404", "Default: 404", get("api/v1/orders"), "nothing"),
                Example(
                    "missing-strict",
                    "failOnMissingMock()",
                    SampleRequest("GET", "api/v1/orders", strict = true),
                    "nothing",
                ),
            ),
        ),
        Feature(
            id = "network",
            glyph = "ms",
            title = "Simulated network",
            summary = "Latency and random failures",
            description = "Every call waits a random time to imitate a network, and a percentage of calls can " +
                "fail with a chosen status. These settings apply to every request of the sample.",
            kind = FeatureKind.NETWORK,
            examples = listOf(Example("ping", "Ping", get("api/v1/ping"), "api/v1/ping.json")),
        ),
        Feature(
            id = "calls",
            glyph = "log",
            title = "Recorded calls and log",
            summary = "recordCalls() and MockLogger",
            description = "recordCalls() keeps every request with the file that answered it, its status and " +
                "the body that was sent. Each call also logs one line through the MockLogger. Both are " +
                "collected here.",
            kind = FeatureKind.CALLS,
        ),
        Feature(
            id = "retrofit",
            glyph = "kt",
            title = "With Retrofit",
            summary = "Typed suspend calls on mocks",
            description = "The interceptor sits in the OkHttpClient of Retrofit, so the service is unchanged: " +
                "these calls are the UsersService of the sample, answered from the same files.",
            kind = FeatureKind.RETROFIT,
        ),
    )

    fun feature(id: String): Feature = features.first { it.id == id }
}
