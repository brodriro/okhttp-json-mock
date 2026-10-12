package io.github.brodriro.okhttpjsonmock.providers

import java.io.InputStream

/**
 * Decides where mock files come from.
 *
 * On Android this is usually backed by `context.assets.open(path)`.
 */
public fun interface InputStreamProvider {
    /**
     * Opens the mock file at [path], a relative path such as `api/v1/users/1.json`.
     *
     * @return the file contents, or `null` when there is no file at [path].
     */
    public fun provide(path: String): InputStream?
}
