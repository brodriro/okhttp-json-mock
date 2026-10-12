package io.github.brodriro.okhttpjsonmock

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import okhttp3.Headers
import java.io.IOException

/**
 * A mock file that sets the status code, and optionally the headers, of its response:
 *
 * ```json
 * {
 *   "statusCode": 201,
 *   "headers": { "Location": "/api/v1/users/4" },
 *   "response": { "id": 4, "name": "Ana" }
 * }
 * ```
 *
 * @property statusCode HTTP status code to answer with.
 * @property body the `response` value serialised as JSON, or nothing when the file has no `response`.
 * @property contentType the `Content-Type` given in `headers`, or `null` to keep the default.
 * @property headers every other entry of `headers`, in file order. Empty when the file has none.
 */
internal class MockEnvelope(
    val statusCode: Int,
    val body: ByteArray,
    val contentType: String?,
    val headers: List<Pair<String, String>>,
) {
    companion object {
        private const val STATUS_KEY = "statusCode"
        private const val HEADERS_KEY = "headers"
        private const val RESPONSE_KEY = "response"
        private const val CONTENT_TYPE = "Content-Type"
        private val ENVELOPE_KEYS = setOf(STATUS_KEY, HEADERS_KEY, RESPONSE_KEY)
        private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()

        /**
         * Reads [mock] as an envelope, or returns `null` when it is a plain mock to be served as it is.
         *
         * A file is an envelope only when it is a JSON object that has `statusCode` and no keys
         * other than `statusCode`, `headers` and `response`. Anything else, including files that
         * are not valid JSON, is a plain mock.
         *
         * @param source name of the file, used in error messages.
         * @throws IOException when the file is an envelope but its `statusCode` is not an HTTP
         * status code or its `headers` is not an object of valid header names and values.
         */
        fun parseOrNull(mock: ByteArray, source: String): MockEnvelope? {
            val text = mock.decodeToString()
            // Cheap check that keeps plain mocks, the common case, from being parsed at all.
            if (STATUS_KEY !in text) return null
            val root = try {
                JsonParser.parseString(text)
            } catch (_: JsonParseException) {
                return null
            }
            if (root !is JsonObject || !root.has(STATUS_KEY) || !ENVELOPE_KEYS.containsAll(root.keySet())) return null
            val response = root.get(RESPONSE_KEY)
            val body = if (response == null) ByteArray(0) else gson.toJson(response).toByteArray()
            val headers = headersOf(root.get(HEADERS_KEY), source)
            return MockEnvelope(
                statusCode = statusCodeOf(root.get(STATUS_KEY), source),
                body = body,
                contentType = headers.lastOrNull { (name, _) -> name.equals(CONTENT_TYPE, ignoreCase = true) }?.second,
                headers = headers.filterNot { (name, _) -> name.equals(CONTENT_TYPE, ignoreCase = true) },
            )
        }

        /** The entries of the optional `headers` object. Values may be strings, numbers or booleans. */
        private fun headersOf(value: JsonElement?, source: String): List<Pair<String, String>> {
            if (value == null) return emptyList()
            if (value !is JsonObject) throw IOException("$source: headers must be an object of name and value pairs")
            return value.entrySet().map { (name, headerValue) ->
                if (!headerValue.isJsonPrimitive) {
                    throw IOException("$source: header '$name' must be a string, a number or a boolean")
                }
                val text = headerValue.asString
                try {
                    Headers.Builder().add(name, text)
                } catch (e: IllegalArgumentException) {
                    throw IOException("$source: invalid header '$name': ${e.message}", e)
                }
                name to text
            }
        }

        private fun statusCodeOf(value: JsonElement, source: String): Int {
            val code = value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asString
                ?.toIntOrNull()
            return code?.takeIf { it in 100..599 }
                ?: throw IOException("$source: statusCode must be an HTTP status code between 100 and 599 but was $value")
        }
    }
}
