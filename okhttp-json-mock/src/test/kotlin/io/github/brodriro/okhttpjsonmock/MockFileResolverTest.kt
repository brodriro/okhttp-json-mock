package io.github.brodriro.okhttpjsonmock

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class MockFileResolverTest {

    private fun candidates(url: String, basePath: String = "") =
        MockFileResolver(basePath).candidates(url.toHttpUrl())

    @Test
    fun `path without query has a single candidate`() {
        assertEquals(listOf("api/v1/users/1.json"), candidates("http://example.com/api/v1/users/1"))
    }

    @Test
    fun `query file comes before the path file`() {
        assertEquals(
            listOf("api/v1/users/page=1.json", "api/v1/users.json"),
            candidates("http://example.com/api/v1/users?page=1"),
        )
    }

    @Test
    fun `base path prefixes every candidate`() {
        assertEquals(
            listOf("mocks/api/v1/users/page=1.json", "mocks/api/v1/users.json"),
            candidates("http://example.com/api/v1/users?page=1", basePath = "mocks/"),
        )
    }

    @Test
    fun `root path and trailing slash are normalised`() {
        assertEquals(listOf("index.json"), candidates("http://example.com/"))
        assertEquals(listOf("api/v1/users.json"), candidates("http://example.com/api/v1/users/"))
    }
}
