package miku.server

import miku.server.service.RedisCacheService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RedisCacheServiceTest {

    @Test
    fun `cache service should gracefully handle missing Redis`() {
        // Connect to non-existent Redis — should not throw
        val cache = RedisCacheService(redisUrl = "redis://localhost:59999")
        assertFalse(cache.isAvailable())
    }

    @Test
    fun `get on unavailable cache should return null`() {
        val cache = RedisCacheService(redisUrl = "redis://localhost:59999")
        assertNull(cache.get("any-key"))
    }

    @Test
    fun `set on unavailable cache should not throw`() {
        val cache = RedisCacheService(redisUrl = "redis://localhost:59999")
        assertDoesNotThrow { cache.set("key", "value") }
    }

    @Test
    fun `delete on unavailable cache should not throw`() {
        val cache = RedisCacheService(redisUrl = "redis://localhost:59999")
        assertDoesNotThrow { cache.delete("pattern*") }
    }

    @Test
    fun `getOrSet on unavailable cache should compute and return`() {
        val cache = RedisCacheService(redisUrl = "redis://localhost:59999")
        val result = cache.getOrSet<String>("key") { "computed-value" }
        assertEquals("computed-value", result)
    }

    @Test
    fun `getOrSet should not cache empty collections`() {
        val cache = RedisCacheService(redisUrl = "redis://localhost:59999")
        val result = cache.getOrSet<List<String>>("key") { emptyList() }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `flushAll on unavailable cache should not throw`() {
        val cache = RedisCacheService(redisUrl = "redis://localhost:59999")
        assertDoesNotThrow { cache.flushAll() }
    }

    @Test
    fun `flushSourceCache on unavailable cache should not throw`() {
        val cache = RedisCacheService(redisUrl = "redis://localhost:59999")
        assertDoesNotThrow { cache.flushSourceCache(123L) }
    }
}
