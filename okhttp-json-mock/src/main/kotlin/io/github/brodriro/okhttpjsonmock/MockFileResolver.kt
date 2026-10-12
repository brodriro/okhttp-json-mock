package io.github.brodriro.okhttpjsonmock

import okhttp3.HttpUrl

/** Turns a request into the list of mock files that can answer it. */
internal class MockFileResolver(private val basePath: String) {

    /** Files that can answer [url], most specific first. */
    fun candidates(url: HttpUrl): List<String> {
        val path = url.encodedPath.trim('/').ifEmpty { ROOT_FILE_NAME }
        val query = url.encodedQuery.orEmpty()
        val queryFile = if (query.isEmpty()) null else "$basePath$path/$query.json"
        return listOfNotNull(queryFile, "$basePath$path.json")
    }

    private companion object {
        const val ROOT_FILE_NAME = "index"
    }
}
