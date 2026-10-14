package io.github.brodriro.okhttpjsonmock.providers

import java.io.InputStream

/**
 * Decides where mock files come from.
 *
 * On Android this is usually backed by `context.assets.open(path)`.
 *
 * The interceptor asks for several files per request (one per HTTP method, query and `{id}`
 * variant) and most of them do not exist, so a missing file must be reported by returning `null`,
 * quickly and without logging. Open exactly the file that is asked for: answering an unknown name
 * with some other file breaks the order in which mocks are chosen.
 */
public fun interface InputStreamProvider {
    /**
     * Opens the mock file at [path], a relative path such as `api/v1/users/1.json`.
     *
     * @return the file contents, or `null` when there is no file at [path].
     */
    public fun provide(path: String): InputStream?
}
