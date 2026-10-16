package io.github.brodriro.okhttpjsonmock.sample.mock

import android.content.res.AssetManager
import io.github.brodriro.okhttpjsonmock.providers.ListableInputStreamProvider
import java.io.IOException
import java.io.InputStream

/**
 * The assets under [root], listed so the interceptor only opens the file that answers.
 *
 * `AssetManager.list` returns the children of a folder and nothing for a file, so walking it once gives every
 * path. The walk is kept here because the sample builds a new interceptor for each request; an app that keeps one
 * interceptor can walk inside [list], which the interceptor already calls only once.
 */
class ListedAssets(private val assets: AssetManager, private val root: String = "") : ListableInputStreamProvider {

    private val files: List<String> by lazy { mutableListOf<String>().also { walk(root, it) } }

    override fun provide(path: String): InputStream? = try {
        assets.open(path)
    } catch (e: IOException) {
        null
    }

    override fun list(): Collection<String> = files

    private fun walk(path: String, into: MutableList<String>) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            into += path
        } else {
            children.forEach { walk(if (path.isEmpty()) it else "$path/$it", into) }
        }
    }
}
