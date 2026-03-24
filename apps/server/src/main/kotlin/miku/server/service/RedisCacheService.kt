package miku.server.service

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

class RedisCacheService(
    redisUrl: String = System.getenv("REDIS_URL") ?: "redis://localhost:6379",
    @PublishedApi internal val defaultTtlSeconds: Long = (System.getenv("REDIS_CACHE_TTL_MINUTES")?.toLongOrNull() ?: 30) * 60,
) {
    private val logger = LoggerFactory.getLogger(RedisCacheService::class.java)
    private val pool: JedisPool?
    @PublishedApi internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Stampede protection: only one computation per cache key at a time
    @PublishedApi internal val computeLocks = ConcurrentHashMap<String, Any>()

    init {
        pool = try {
            val config = JedisPoolConfig().apply {
                maxTotal = 16
                maxIdle = 8
                minIdle = 2
                testOnBorrow = true
            }
            JedisPool(config, java.net.URI(redisUrl)).also {
                // Test connection
                it.resource.use { jedis -> jedis.ping() }
                logger.info("Redis connected at $redisUrl (TTL: ${defaultTtlSeconds}s)")
            }
        } catch (e: Exception) {
            logger.warn("Redis not available at $redisUrl — caching disabled: ${e.message}")
            null
        }
    }

    fun get(key: String): String? {
        if (pool == null) return null
        return try {
            pool.resource.use { it.get("miku:$key") }
        } catch (e: Exception) {
            logger.debug("Redis GET error for key=$key: ${e.message}")
            null
        }
    }

    fun set(key: String, value: String, ttlSeconds: Long = defaultTtlSeconds) {
        if (pool == null) return
        try {
            pool.resource.use { jedis ->
                jedis.setex("miku:$key", ttlSeconds, value)
            }
        } catch (e: Exception) {
            logger.debug("Redis SET error for key=$key: ${e.message}")
        }
    }

    fun delete(pattern: String) {
        if (pool == null) return
        try {
            pool.resource.use { jedis ->
                val keys = jedis.keys("miku:$pattern")
                if (keys.isNotEmpty()) {
                    jedis.del(*keys.toTypedArray())
                    logger.debug("Redis DEL ${keys.size} keys matching $pattern")
                }
            }
        } catch (e: Exception) {
            logger.debug("Redis DEL error for pattern=$pattern: ${e.message}")
        }
    }

    fun flushSourceCache(sourceId: Long) {
        delete("source:$sourceId:*")
    }

    fun flushAll() {
        delete("*")
    }

    inline fun <reified T> getOrSet(key: String, ttlSeconds: Long = defaultTtlSeconds, compute: () -> T): T {
        val cached = get(key)
        if (cached != null) {
            miku.server.config.MikuMetrics.recordCacheHit()
            return try {
                json.decodeFromString<T>(cached)
            } catch (e: Exception) {
                delete(key)
                val result = compute()
                cacheIfNotEmpty(key, result, ttlSeconds)
                result
            }
        }
        miku.server.config.MikuMetrics.recordCacheMiss()
        // Note: stampede protection is handled by SourceExecutor's request coalescing
        // (only one in-flight request per source+operation key)
        val result = compute()
        cacheIfNotEmpty(key, result, ttlSeconds)
        return result
    }

    @PublishedApi
    internal inline fun <reified T> cacheIfNotEmpty(key: String, result: T, ttlSeconds: Long) {
        // Don't cache empty results — they may be transient errors
        val isEmpty = when (result) {
            is Collection<*> -> result.isEmpty()
            is miku.domain.model.MangaPageResult -> result.mangas.isEmpty()
            else -> false
        }
        if (!isEmpty) {
            try { set(key, json.encodeToString(result), ttlSeconds) } catch (_: Exception) {}
        }
    }

    fun isAvailable(): Boolean = pool != null

    fun close() {
        pool?.close()
    }
}
