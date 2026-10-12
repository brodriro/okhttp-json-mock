package io.github.brodriro.okhttpjsonmock.providers

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** Reads mock files from the filesystem, resolving paths against the working directory. */
public class DefaultInputStreamProvider : InputStreamProvider {
    override fun provide(path: String): InputStream? {
        val file = File(path)
        return if (file.isFile) FileInputStream(file) else null
    }
}
