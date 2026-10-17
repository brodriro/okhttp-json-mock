package io.github.brodriro.okhttpjsonmock

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class MockFileResolverTest {

    /** Candidates for the path exactly as requested, which is what most tests are about. */
    private fun candidates(method: String, url: String, basePath: String = "") =
        allCandidates(method, url, basePath).filterNot { "{id}" in it }

    private fun allCandidates(
        method: String,
        url: String,
        basePath: String = "",
        extensions: List<String> = listOf("json"),
    ) = MockFileResolver(basePath, extensions).candidates(method, url.toHttpUrl())

    @Test
    fun `method file comes before the file shared by every method`() {
        assertEquals(
            listOf("api/v1/users/1.get.json", "api/v1/users/1.json"),
            candidates("GET", "http://example.com/api/v1/users/1"),
        )
    }

    @Test
    fun `each method has its own file for the same url`() {
        val url = "http://example.com/api/v1/users"

        assertEquals("api/v1/users.get.json", candidates("GET", url).first())
        assertEquals("api/v1/users.post.json", candidates("POST", url).first())
        assertEquals("api/v1/users.put.json", candidates("PUT", url).first())
        assertEquals("api/v1/users.delete.json", candidates("DELETE", url).first())
        assertEquals("api/v1/users.patch.json", candidates("PATCH", url).first())
    }

    @Test
    fun `with a query the order is method then shared, query file before path file`() {
        assertEquals(
            listOf(
                "api/v1/users/page=1.get.json",
                "api/v1/users.get.json",
                "api/v1/users/page=1.json",
                "api/v1/users.json",
            ),
            candidates("GET", "http://example.com/api/v1/users?page=1"),
        )
    }

    @Test
    fun `query with unsorted parameters also tries the sorted form`() {
        assertEquals(
            listOf(
                "api/v1/posts/page=1&categoryId=5.get.json",
                "api/v1/posts/categoryId=5&page=1.get.json",
                "api/v1/posts.get.json",
                "api/v1/posts/page=1&categoryId=5.json",
                "api/v1/posts/categoryId=5&page=1.json",
                "api/v1/posts.json",
            ),
            candidates("GET", "http://example.com/api/v1/posts?page=1&categoryId=5"),
        )
    }

    @Test
    fun `query already sorted is not tried twice`() {
        assertEquals(4, candidates("GET", "http://example.com/api/v1/posts?categoryId=5&page=1").size)
    }

    @Test
    fun `method suffix is lower case whatever the request uses`() {
        assertEquals("api/v1/users.post.json", candidates("post", "http://example.com/api/v1/users").first())
        assertEquals("api/v1/users.post.json", candidates("Post", "http://example.com/api/v1/users").first())
    }

    @Test
    fun `base path prefixes every candidate`() {
        assertEquals(
            listOf("mocks/api/v1/users.delete.json", "mocks/api/v1/users.json"),
            candidates("DELETE", "http://example.com/api/v1/users", basePath = "mocks/"),
        )
    }

    @Test
    fun `root path and trailing slash are normalised`() {
        assertEquals(listOf("index.get.json", "index.json"), candidates("GET", "http://example.com/"))
        assertEquals(
            listOf("api/v1/users.get.json", "api/v1/users.json"),
            candidates("GET", "http://example.com/api/v1/users/"),
        )
    }

    @Test
    fun `a path segment can be replaced by the id placeholder, last segments first`() {
        assertEquals(
            listOf(
                "users/7/posts.get.json",
                "users/7/{id}.get.json",
                "users/{id}/posts.get.json",
                "{id}/7/posts.get.json",
                "users/{id}/{id}.get.json",
                "{id}/7/{id}.get.json",
                "{id}/{id}/posts.get.json",
            ),
            allCandidates("GET", "http://example.com/users/7/posts").filter { it.endsWith(".get.json") },
        )
    }

    @Test
    fun `exact path comes before the placeholder paths, method before shared for each path`() {
        assertEquals(
            listOf(
                "users/1.delete.json",
                "users/1.json",
                "users/{id}.delete.json",
                "users/{id}.json",
                "{id}/1.delete.json",
                "{id}/1.json",
                "{id}/{id}.delete.json",
                "{id}/{id}.json",
            ),
            allCandidates("DELETE", "http://example.com/users/1"),
        )
    }

    @Test
    fun `placeholder paths keep the query files`() {
        assertEquals(
            listOf("users/{id}/page=2.get.json", "users/{id}.get.json", "users/{id}/page=2.json", "users/{id}.json"),
            allCandidates("GET", "http://example.com/users/1?page=2").filter { it.startsWith("users/{id}") },
        )
    }

    @Test
    fun `at most two segments are replaced`() {
        val candidates = allCandidates("GET", "http://example.com/a/b/c/d")

        assertEquals(0, candidates.count { it.split("{id}").size - 1 > 2 })
        assertEquals(2 * (1 + 4 + 6), candidates.size)
    }

    @Test
    fun `root path has no placeholder candidates`() {
        assertEquals(listOf("index.get.json", "index.json"), allCandidates("GET", "http://example.com/"))
    }

    @Test
    fun `base path is never replaced`() {
        assertEquals(
            listOf("mocks/users.get.json", "mocks/users.json", "mocks/{id}.get.json", "mocks/{id}.json"),
            allCandidates("GET", "http://example.com/users", basePath = "mocks/"),
        )
    }

    @Test
    fun `each file name is tried with every extension before the next name`() {
        assertEquals(
            listOf(
                "feed/page=1.get.json",
                "feed/page=1.get.xml",
                "feed.get.json",
                "feed.get.xml",
                "feed/page=1.json",
                "feed/page=1.xml",
                "feed.json",
                "feed.xml",
            ),
            allCandidates("GET", "http://example.com/feed?page=1", extensions = listOf("json", "xml"))
                .filterNot { "{id}" in it },
        )
    }

    @Test
    fun `extensions are tried in the order they were given`() {
        assertEquals(
            listOf("feed.get.xml", "feed.get.json", "feed.xml", "feed.json"),
            allCandidates("GET", "http://example.com/feed", extensions = listOf("xml", "json"))
                .filterNot { "{id}" in it },
        )
    }

    @Test
    fun `placeholder and base path candidates also get every extension`() {
        assertEquals(
            listOf("mocks/images/{id}.get.png", "mocks/images/{id}.png"),
            allCandidates("GET", "http://example.com/images/1", basePath = "mocks/", extensions = listOf("png"))
                .filter { it.startsWith("mocks/images/{id}") },
        )
    }
}
