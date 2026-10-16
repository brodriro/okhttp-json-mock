package io.github.brodriro.okhttpjsonmock.providers

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.JarURLConnection
import java.net.URL

/**
 * Reads mock files from the classpath folder [root] and lists them, so the interceptor opens only the file that
 * answers (see [ListableInputStreamProvider]).
 *
 * The folder is found with `ClassLoader.getResources(root)` and walked wherever it is: a folder on disk, such as
 * `src/test/resources/mocks` in a Gradle build, or a folder inside a jar. When it appears in several places of
 * the classpath, all of them are listed. Only files under [root] are served, so the interceptor's paths must start
 * with it: use `basePath("$root/")`.
 *
 * [list] fails with an [IOException] when [root] is not on the classpath or is somewhere it cannot walk: a class
 * loader that does not expose files or jars, or a jar without folder entries, such as an Android APK. Use
 * [ClasspathInputStreamProvider] there: it does not list, so it works with any class loader.
 *
 * @param root classpath folder that holds the mocks, such as `mocks`. Leading and trailing slashes are ignored.
 * @param classLoader where resources are looked up. The class loader of the library unless given.
 */
public class ListedClasspathInputStreamProvider @JvmOverloads constructor(
    root: String,
    private val classLoader: ClassLoader = ListedClasspathInputStreamProvider::class.java.classLoader,
) : ListableInputStreamProvider {

    /** The classpath folder that is listed, without leading or trailing slashes. */
    public val root: String = root.trim('/')

    init {
        require(this.root.isNotEmpty()) { "root must name a classpath folder, such as \"mocks\"" }
    }

    override fun provide(path: String): InputStream? = classLoader.getResourceAsStream(path)

    override fun list(): Collection<String> {
        val folders = classLoader.getResources(root).toList()
        if (folders.isEmpty()) throw IOException("No classpath folder named '$root'")
        return folders.flatMapTo(LinkedHashSet()) { folder -> listFolder(folder) }
    }

    private fun listFolder(folder: URL): List<String> = when (folder.protocol) {
        "file" -> {
            val directory = File(folder.toURI())
            directory.walk().filter { it.isFile }.map { "$root/${it.relativeTo(directory).invariantSeparatorsPath}" }
                .toList()
        }
        "jar" -> {
            val connection = folder.openConnection() as JarURLConnection
            // Not cached, so closing the jar here does not close it for anyone else.
            connection.useCaches = false
            connection.jarFile.use { jar ->
                jar.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("$root/") }
                    .map { it.name }
                    .toList()
            }
        }
        else -> throw IOException(
            "Cannot list the classpath folder $folder; use ClasspathInputStreamProvider, which does not list",
        )
    }
}
