package android.util

import java.util.LinkedHashMap

open class LruCache<K, V>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>(0, 0.75f, true)
    private var size = 0

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? {
        val previous = map.put(key, value)
        size = map.size
        trimToSize(maxSize)
        return previous
    }

    @Synchronized
    fun remove(key: K): V? {
        val previous = map.remove(key)
        if (previous != null) size = map.size
        return previous
    }

    private fun trimToSize(maxSize: Int) {
        while (map.size > maxSize) {
            val eldest = map.entries.first()
            map.remove(eldest.key)
        }
        size = map.size
    }

    @Synchronized
    fun evictAll() {
        map.clear()
        size = 0
    }

    @Synchronized
    fun size(): Int = map.size
}
