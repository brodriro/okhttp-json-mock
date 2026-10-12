package io.github.brodriro.okhttpjsonmock.providers

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/** Reads mock files from the filesystem, resolving paths against the working directory. */
public class DefaultInputStreamProvider : InputStreamProvider {
    override fun provide(path: String): InputStream? {
        try {
            return FileInputStream(File(path))
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        return null
    }
}
