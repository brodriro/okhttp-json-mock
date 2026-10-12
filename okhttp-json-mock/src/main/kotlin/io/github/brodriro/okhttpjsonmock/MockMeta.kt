package io.github.brodriro.okhttpjsonmock

import okhttp3.Headers
import java.io.IOException

/**
 * Status code and headers of a mocked response, read from the optional `.meta` file that sits next
 * to a mock (`users.post.meta` for `users.post.json`).
 *
 * The file is plain text with one `Name: value` pair per line. `status` sets the HTTP status code
 * and every other name is sent as a response header. Blank lines and lines starting with `#` are
 * ignored:
 *
 * ```
 * # A resource was created
 * status: 201
 * Location: /api/v1/users/4
 * ```
 *
 * @property status HTTP status code of the response.
 * @property contentType value of the `Content-Type` line, or `null` to keep the default.
 * @property headers every other header, in file order.
 */
internal class MockMeta(
    val status: Int,
    val contentType: String?,
    val headers: List<Pair<String, String>>,
) {
    companion object {
        private const val DEFAULT_STATUS = 200
        private const val STATUS_KEY = "status"
        private const val CONTENT_TYPE_KEY = "Content-Type"
        private const val BYTE_ORDER_MARK = "﻿"

        /** What a mock without a `.meta` file answers with. */
        val DEFAULT = MockMeta(DEFAULT_STATUS, contentType = null, headers = emptyList())

        /**
         * Parses the contents of a `.meta` file.
         *
         * @param source name of the file, used in error messages.
         * @throws IOException when a line is not a `Name: value` pair, the status is not an HTTP
         * status code or a header is not valid.
         */
        fun parse(text: String, source: String): MockMeta {
            var status = DEFAULT_STATUS
            var contentType: String? = null
            val headers = mutableListOf<Pair<String, String>>()
            text.removePrefix(BYTE_ORDER_MARK).lineSequence().forEachIndexed { index, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val where = "$source:${index + 1}"
                val colon = line.indexOf(':')
                if (colon <= 0) throw IOException("$where: expected 'Name: value' but found '$line'")
                val name = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                when {
                    name.equals(STATUS_KEY, ignoreCase = true) -> status = parseStatus(value, where)
                    name.equals(CONTENT_TYPE_KEY, ignoreCase = true) -> contentType = validated(name, value, where)
                    else -> headers += name to validated(name, value, where)
                }
            }
            return MockMeta(status, contentType, headers)
        }

        private fun parseStatus(value: String, where: String): Int =
            value.toIntOrNull()?.takeIf { it in 100..599 }
                ?: throw IOException("$where: status must be an HTTP status code between 100 and 599 but was '$value'")

        /** Returns [value] when OkHttp accepts the header, so a bad file fails here with its name and line. */
        private fun validated(name: String, value: String, where: String): String {
            try {
                Headers.Builder().add(name, value)
            } catch (e: IllegalArgumentException) {
                throw IOException("$where: invalid header '$name': ${e.message}", e)
            }
            return value
        }
    }
}
