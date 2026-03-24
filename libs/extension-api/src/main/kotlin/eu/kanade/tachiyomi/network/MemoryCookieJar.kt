package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Cookie jar that persists cookies to disk.
 * Survives server restarts — important for Cloudflare sessions and extension auth.
 */
class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()
    private var persistDir: File? = null

    /**
     * Enable persistence to disk. Call after construction.
     */
    fun enablePersistence(dir: File) {
        persistDir = dir.also { it.mkdirs() }
        loadFromDisk()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = url.host
        store.getOrPut(key) { mutableListOf() }.apply {
            removeAll { existing -> cookies.any { it.name == existing.name } }
            addAll(cookies.filter { !it.hasExpired() })
        }
        saveToDisk(key)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val key = url.host
        val cookies = store[key] ?: return emptyList()
        val (valid, expired) = cookies.partition { !it.hasExpired() }
        if (expired.isNotEmpty()) {
            cookies.removeAll(expired)
            saveToDisk(key)
        }
        return valid.filter { it.matches(url) }
    }

    fun clear() {
        store.clear()
        persistDir?.listFiles()?.forEach { it.delete() }
    }

    private fun Cookie.hasExpired(): Boolean = expiresAt < System.currentTimeMillis()

    private fun saveToDisk(host: String) {
        val dir = persistDir ?: return
        try {
            val file = File(dir, "${host.replace(".", "_")}.cookies")
            val cookies = store[host] ?: return
            file.writeText(cookies.joinToString("\n") { serializeCookie(it) })
        } catch (_: Exception) {}
    }

    private fun loadFromDisk() {
        val dir = persistDir ?: return
        dir.listFiles()?.forEach { file ->
            try {
                val host = file.nameWithoutExtension.replace("_", ".")
                val cookies = file.readLines().mapNotNull { deserializeCookie(it) }.toMutableList()
                if (cookies.isNotEmpty()) store[host] = cookies
            } catch (_: Exception) {}
        }
    }

    private fun serializeCookie(c: Cookie): String {
        return "${c.name}\t${c.value}\t${c.domain}\t${c.path}\t${c.expiresAt}\t${c.secure}\t${c.httpOnly}"
    }

    private fun deserializeCookie(line: String): Cookie? {
        val parts = line.split("\t")
        if (parts.size < 7) return null
        return try {
            Cookie.Builder()
                .name(parts[0])
                .value(parts[1])
                .domain(parts[2])
                .path(parts[3])
                .expiresAt(parts[4].toLong())
                .apply {
                    if (parts[5].toBoolean()) secure()
                    if (parts[6].toBoolean()) httpOnly()
                }
                .build()
        } catch (_: Exception) { null }
    }
}
