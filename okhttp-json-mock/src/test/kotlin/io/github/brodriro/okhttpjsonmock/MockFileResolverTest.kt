package io.github.brodriro.okhttpjsonmock

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class MockFileResolverTest {

    private fun candidates(method: String, url: String, basePath: String = "") =
        MockFileResolver(basePath).candidates(method, url.toHttpUrl())

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
    fun `meta file shares the name of its mock`() {
        val resolver = MockFileResolver("")

        assertEquals("api/v1/users.post.meta", resolver.metaFileFor("api/v1/users.post.json"))
        assertEquals("api/v1/users/page=1.meta", resolver.metaFileFor("api/v1/users/page=1.json"))
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
}
