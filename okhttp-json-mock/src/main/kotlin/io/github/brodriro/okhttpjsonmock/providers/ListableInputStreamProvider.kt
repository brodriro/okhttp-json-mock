package io.github.brodriro.okhttpjsonmock.providers

import java.io.IOException

/**
 * An [InputStreamProvider] that can also say which files it has, so the interceptor stops asking for files that do
 * not exist.
 *
 * Without it, every name the interceptor tries costs one [provide] call: up to six for a request whose exact file
 * exists, and from a handful to over a hundred for one answered by an `{id}` file or by nothing, multiplied by the
 * number of extensions. With it, the interceptor calls [list] once, on its first request, and then picks the first
 * name that is in that list without any I/O, so [provide] is only called for the file that answers.
 *
 * The list is read once and kept for the life of the interceptor: files added afterwards are not seen. Implement
 * only [InputStreamProvider] when the files can change while the interceptor is in use.
 *
 * If [provide] returns `null` for a listed file, the interceptor moves on to the next name, so a list with extra
 * entries is harmless; a file missing from the list is never served.
 */
public interface ListableInputStreamProvider : InputStreamProvider {
    /**
     * Every path that [provide] can open, written exactly as the interceptor asks for it: relative, with the
     * `basePath` in front, such as `mocks/api/v1/users/1.json`.
     *
     * Called once, on the first request, from the thread of that request. If it throws, that request fails with the
     * exception and the next one calls it again.
     */
    @Throws(IOException::class)
    public fun list(): Collection<String>
}
