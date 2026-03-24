package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = url.host
        store.getOrPut(key) { mutableListOf() }.apply {
            removeAll { existing -> cookies.any { it.name == existing.name } }
            addAll(cookies.filter { !it.hasExpired() })
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val key = url.host
        val cookies = store[key] ?: return emptyList()
        val (valid, expired) = cookies.partition { !it.hasExpired() }
        if (expired.isNotEmpty()) {
            cookies.removeAll(expired)
        }
        return valid.filter { it.matches(url) }
    }

    fun clear() {
        store.clear()
    }

    private fun Cookie.hasExpired(): Boolean {
        return expiresAt < System.currentTimeMillis()
    }
}
