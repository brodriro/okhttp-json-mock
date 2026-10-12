package io.github.brodriro.okhttpjsonmock.helpers

import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import java.io.IOException

/** Reads mock files through an [InputStreamProvider]. */
public object ResourcesHelper {
    /**
     * Reads the file called [name] as text.
     *
     * @return the file contents, or `null` when the file does not exist or cannot be read.
     */
    @JvmStatic
    public fun loadFileAsString(inputStreamProvider: InputStreamProvider, name: String): String? {
        val inputStream = inputStreamProvider.provide(name) ?: return null
        return try {
            inputStream.bufferedReader(java.nio.charset.Charset.defaultCharset()).use { reader ->
                reader.lineSequence().joinToString("\n")
            }
        } catch (e: IOException) {
            print("JsonMockServer: Error opening asset $name")
            null
        }
    }
}
