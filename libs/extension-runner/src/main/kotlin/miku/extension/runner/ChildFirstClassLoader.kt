package miku.extension.runner

import java.net.URL
import java.net.URLClassLoader

/**
 * A ClassLoader that checks the child (extension JAR) first before delegating to the parent.
 * This is critical for Kotlin compatibility — extensions may include patched kotlin.Result
 * classes that must override the parent classloader's Kotlin stdlib.
 *
 * Only kotlin.Result and related compat classes are loaded child-first.
 * Everything else follows normal parent-first delegation.
 */
class ChildFirstClassLoader(
    urls: Array<URL>,
    parent: ClassLoader,
) : URLClassLoader(urls, parent) {

    private val childFirstPrefixes = setOf(
        "kotlin.Result",
        "kotlin.ResultKt",
    )

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            // Check if already loaded
            var c = findLoadedClass(name)
            if (c != null) return c

            // For specific compat classes, try child first
            if (childFirstPrefixes.any { name.startsWith(it) }) {
                try {
                    c = findClass(name)
                    if (resolve) resolveClass(c)
                    return c
                } catch (_: ClassNotFoundException) {
                    // Fall through to parent
                }
            }

            // Default: parent first
            return super.loadClass(name, resolve)
        }
    }
}
