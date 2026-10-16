package io.github.brodriro.okhttpjsonmock.providers

import java.io.InputStream

/**
 * Reads mock files from the classpath, for example `src/test/resources` in a Gradle project or the resources of a
 * jar. `ClassLoader.getResourceAsStream` already returns `null` for a missing file.
 *
 * Paths are resource names, relative to the classpath root: with the mocks in `src/test/resources/mocks`, use
 * `basePath("mocks/")`. To have the interceptor open only the file that answers, use
 * [ListedClasspathInputStreamProvider].
 *
 * @param classLoader where resources are looked up. The class loader of the library unless given.
 */
public class ClasspathInputStreamProvider @JvmOverloads constructor(
    private val classLoader: ClassLoader = ClasspathInputStreamProvider::class.java.classLoader,
) : InputStreamProvider {
    override fun provide(path: String): InputStream? = classLoader.getResourceAsStream(path)
}
