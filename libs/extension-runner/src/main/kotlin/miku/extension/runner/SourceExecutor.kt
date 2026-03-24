package miku.extension.runner

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SourceExecutor(
    private val registry: ExtensionRegistry,
    private val defaultTimeout: Duration = 30.seconds,
    private val maxRetries: Int = 1,
) {
    private val logger = LoggerFactory.getLogger(SourceExecutor::class.java)

    // Request coalescing: only one in-flight request per unique key
    private val inFlight = ConcurrentHashMap<String, Deferred<Any?>>()

    suspend fun getPopularManga(sourceId: Long, page: Int): MangasPage {
        val source = requireCatalogueSource(sourceId)
        return coalesce("popular:$sourceId:$page") {
            source.getPopularManga(page)
        }
    }

    suspend fun getLatestUpdates(sourceId: Long, page: Int): MangasPage {
        val source = requireCatalogueSource(sourceId)
        if (!source.supportsLatest) {
            throw UnsupportedOperationException("Source '${source.name}' does not support latest updates")
        }
        return coalesce("latest:$sourceId:$page") {
            source.getLatestUpdates(page)
        }
    }

    suspend fun searchManga(sourceId: Long, page: Int, query: String, filters: FilterList = FilterList()): MangasPage {
        val source = requireCatalogueSource(sourceId)
        return coalesce("search:$sourceId:${query.hashCode()}:$page") {
            source.getSearchManga(page, query, filters)
        }
    }

    suspend fun getMangaDetails(sourceId: Long, manga: SManga): SManga {
        val source = requireCatalogueSource(sourceId)
        return coalesce("details:$sourceId:${manga.url.hashCode()}") {
            source.getMangaDetails(manga)
        }
    }

    suspend fun getChapterList(sourceId: Long, manga: SManga): List<SChapter> {
        val source = requireCatalogueSource(sourceId)
        return coalesce("chapters:$sourceId:${manga.url.hashCode()}") {
            source.getChapterList(manga)
        }
    }

    suspend fun getPageList(sourceId: Long, chapter: SChapter): List<Page> {
        val source = requireCatalogueSource(sourceId)
        return coalesce("pages:$sourceId:${chapter.url.hashCode()}") {
            source.getPageList(chapter)
        }
    }

    suspend fun getImageUrl(sourceId: Long, page: Page): String {
        val source = requireCatalogueSource(sourceId)
        return executeWithRetry("getImageUrl") {
            if (source is HttpSource) {
                source.getImageUrl(page)
            } else {
                page.imageUrl ?: throw IllegalStateException("Page has no image URL")
            }
        }
    }

    fun getFilters(sourceId: Long): FilterList {
        val source = requireCatalogueSource(sourceId)
        return source.getFilterList()
    }

    fun getImageHeaders(sourceId: Long): Map<String, String> {
        val source = registry.getSource(sourceId) as? HttpSource ?: return emptyMap()
        return source.headers.toMap()
    }

    private fun requireCatalogueSource(sourceId: Long): CatalogueSource {
        return registry.getCatalogueSource(sourceId)
            ?: throw SourceNotFoundException("Source not found: $sourceId")
    }

    /**
     * Request coalescing: if an identical request is already in-flight,
     * wait for its result instead of making a duplicate request.
     * Prevents flooding the source when multiple clients request the same data.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> coalesce(key: String, block: suspend () -> T): T {
        // Check if there's already an in-flight request for this key
        val existing = inFlight[key]
        if (existing != null && existing.isActive) {
            return try {
                existing.await() as T
            } catch (e: Exception) {
                // If the original request failed, try ourselves
                executeWithRetry(key, block = block)
            }
        }

        // Start new request
        val deferred = coroutineScope {
            async {
                executeWithRetry(key, block = block)
            }
        }

        inFlight[key] = deferred as Deferred<Any?>

        return try {
            deferred.await()
        } finally {
            inFlight.remove(key)
        }
    }

    /**
     * Execute with timeout and retry on transient failures.
     * Retries on IOException (network issues) and timeout.
     */
    private suspend fun <T> executeWithRetry(
        operation: String,
        timeout: Duration = defaultTimeout,
        block: suspend () -> T,
    ): T {
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            try {
                return withTimeout(timeout) { block() }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                lastException = SourceTimeoutException("Operation '$operation' timed out after $timeout")
                if (attempt < maxRetries) {
                    logger.warn("Timeout on $operation (attempt ${attempt + 1}/$maxRetries), retrying...")
                    delay(500) // Brief delay before retry
                }
            } catch (e: IOException) {
                lastException = SourceExecutionException("Network error in '$operation': ${e.message}", e)
                if (attempt < maxRetries) {
                    logger.warn("IO error on $operation (attempt ${attempt + 1}/$maxRetries), retrying: ${e.message}")
                    delay(500)
                }
            } catch (e: Exception) {
                // Non-transient errors — don't retry
                logger.error("Error executing $operation", e)
                throw SourceExecutionException("Failed to execute '$operation': ${e.message}", e)
            }
        }

        logger.error("All retries exhausted for $operation")
        throw lastException ?: SourceExecutionException("Failed to execute '$operation'", RuntimeException("Unknown error"))
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until size) {
            map[name(i)] = value(i)
        }
        return map
    }
}

class SourceNotFoundException(message: String) : RuntimeException(message)
class SourceTimeoutException(message: String) : RuntimeException(message)
class SourceExecutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
