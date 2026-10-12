package io.github.brodriro.okhttpjsonmock

import okhttp3.HttpUrl
import java.util.Locale

/**
 * Turns a request into the list of mock files that can answer it.
 *
 * A file can be tied to one HTTP method by putting the lower-case method before the extension:
 * `users.post.json` only answers `POST /users`. A file without that suffix answers any method.
 */
internal class MockFileResolver(private val basePath: String) {

    /**
     * Files that can answer a [method] request to [url], most specific first: the files for that
     * method, then the files shared by every method. Within each group the file for the exact query
     * string comes first, then the file for the query with its parameters sorted, then the file for
     * the path.
     */
    fun candidates(method: String, url: HttpUrl): List<String> {
        val path = url.encodedPath.trim('/').ifEmpty { ROOT_FILE_NAME }
        val stems = queryVariants(url.encodedQuery).map { "$path/$it" } + path
        val suffix = method.lowercase(Locale.ROOT)
        return stems.map { "$basePath$it.$suffix.json" } + stems.map { "$basePath$it.json" }
    }

    /**
     * The query string as sent and, when that is a different string, with its parameters sorted.
     * The sorted form lets one file answer a request whatever order the client builds the query in.
     */
    private fun queryVariants(query: String?): List<String> {
        if (query.isNullOrEmpty()) return emptyList()
        val sorted = query.split('&').sorted().joinToString("&")
        return listOf(query, sorted).distinct()
    }

    private companion object {
        const val ROOT_FILE_NAME = "index"
    }
}
