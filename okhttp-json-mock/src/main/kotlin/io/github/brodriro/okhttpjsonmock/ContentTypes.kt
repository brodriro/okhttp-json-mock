package io.github.brodriro.okhttpjsonmock

import java.util.Locale

/**
 * The `Content-Type` a mock file is served with, chosen from its extension.
 *
 * A fixed table instead of `URLConnection.guessContentTypeFromName`, whose answers differ between
 * the JDK and Android. Text types declare UTF-8 because mock files are expected to be UTF-8.
 */
internal object ContentTypes {
    private const val BINARY = "application/octet-stream"

    private val byExtension = mapOf(
        "json" to "application/json",
        "txt" to "text/plain; charset=utf-8",
        "html" to "text/html; charset=utf-8",
        "htm" to "text/html; charset=utf-8",
        "csv" to "text/csv; charset=utf-8",
        "xml" to "application/xml",
        "svg" to "image/svg+xml",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "pdf" to "application/pdf",
    )

    /** The type for a file with this [extension], or `application/octet-stream` for one not in the table. */
    fun of(extension: String): String = byExtension[extension.lowercase(Locale.ROOT)] ?: BINARY
}
