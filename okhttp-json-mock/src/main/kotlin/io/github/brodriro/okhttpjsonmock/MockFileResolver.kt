package io.github.brodriro.okhttpjsonmock

import okhttp3.HttpUrl
import java.util.Locale

/**
 * Turns a request into the list of mock files that can answer it.
 *
 * A file can be tied to one HTTP method by putting the lower-case method before the extension:
 * `users.post.json` only answers `POST /users`. A file without that suffix answers any method.
 *
 * A file or folder named `{id}` stands for any value of that path segment: `users/{id}.json`
 * answers `/users/1` and `/users/42`, and `users/{id}/posts.json` answers `/users/7/posts`.
 */
internal class MockFileResolver(private val basePath: String) {

    /**
     * Files that can answer a [method] request to [url], most specific first. The path exactly as
     * requested comes before the paths with `{id}` segments, so a file written for one URL always
     * wins over a generic one. For each path, the files for that method come before the files
     * shared by every method, and within each of those groups the file for the exact query string
     * comes first, then the file for the query with its parameters sorted, then the file for the path.
     */
    fun candidates(method: String, url: HttpUrl): List<String> {
        val queries = queryVariants(url.encodedQuery)
        val suffix = method.lowercase(Locale.ROOT)
        return pathVariants(url.encodedPath.trim('/')).flatMap { path ->
            val stems = queries.map { "$path/$it" } + path
            stems.map { "$basePath$it.$suffix.json" } + stems.map { "$basePath$it.json" }
        }
    }

    /**
     * The path as requested, then with one segment replaced by `{id}`, then with two. Later
     * segments are replaced first, so `users/{id}` is preferred over `{id}/1`. No more than two
     * segments are replaced, which keeps the number of lookups small.
     */
    private fun pathVariants(path: String): List<String> {
        if (path.isEmpty()) return listOf(ROOT_FILE_NAME)
        val segments = path.split('/')
        val last = segments.indices.reversed()
        val one = last.map { i -> segments.replacing(i) }
        val two = last.flatMap { j -> (j - 1 downTo 0).map { i -> segments.replacing(i, j) } }
        return listOf(path) + one + two
    }

    private fun List<String>.replacing(vararg positions: Int): String =
        mapIndexed { index, segment -> if (index in positions) PLACEHOLDER else segment }.joinToString("/")

    /**
     * The query string as sent and, when that is a different string, with its parameters sorted.
     * The sorted form lets one file answer a request whatever order the client builds the query in.
     */
    private fun queryVariants(query: String?): List<String> {
        if (query.isNullOrEmpty()) return emptyList()
        val sorted = query.split('&').sorted().joinToString("&")
        return listOf(query, sorted).distinct()
    }

    internal companion object {
        const val ROOT_FILE_NAME = "index"

        /** File or folder name that matches any value of a path segment. */
        const val PLACEHOLDER = "{id}"
    }
}
