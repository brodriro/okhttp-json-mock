package io.github.brodriro.okhttpjsonmock

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.IOException

/**
 * A mock file that sets the status code of its response:
 *
 * ```json
 * { "statusCode": 201, "response": { "id": 4, "name": "Ana" } }
 * ```
 *
 * @property statusCode HTTP status code to answer with.
 * @property body the `response` value serialised as JSON, or nothing when the file has no `response`.
 */
internal class MockEnvelope(val statusCode: Int, val body: ByteArray) {
    companion object {
        private const val STATUS_KEY = "statusCode"
        private const val RESPONSE_KEY = "response"
        private val ENVELOPE_KEYS = setOf(STATUS_KEY, RESPONSE_KEY)
        private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()

        /**
         * Reads [mock] as an envelope, or returns `null` when it is a plain mock to be served as it is.
         *
         * A file is an envelope only when it is a JSON object that has `statusCode` and no keys
         * other than `statusCode` and `response`. Anything else, including files that are not valid
         * JSON, is a plain mock.
         *
         * @param source name of the file, used in error messages.
         * @throws IOException when the file is an envelope but its `statusCode` is not an HTTP status code.
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
            return MockEnvelope(statusCodeOf(root.get(STATUS_KEY), source), body)
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
