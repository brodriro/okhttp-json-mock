package io.github.brodriro.okhttpjsonmock.providers

import java.io.InputStream

/**
 * Reads mock files from the classpath, for example `src/test/resources` in a Gradle project or the resources of a
 * jar. `ClassLoader.getResourceAsStream` already returns `null` for a missing file.
 *
 * Paths are resource names, relative to the classpath root: with the mocks in `src/test/resources/mocks`, use
 * `basePath("mocks/")`. It does not list its files, so it works with any class loader, the Java resources of an
 * Android APK included.
 *
 * @param classLoader where resources are looked up. The class loader of the library unless given.
 */
public class ClasspathInputStreamProvider @JvmOverloads constructor(
    private val classLoader: ClassLoader = ClasspathInputStreamProvider::class.java.classLoader,
) : InputStreamProvider {
    override fun provide(path: String): InputStream? = classLoader.getResourceAsStream(path)
}
