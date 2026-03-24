package miku.extension.runner

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SourceExecutor(
    private val registry: ExtensionRegistry,
    private val defaultTimeout: Duration = 30.seconds,
) {
    private val logger = LoggerFactory.getLogger(SourceExecutor::class.java)

    suspend fun getPopularManga(sourceId: Long, page: Int): MangasPage {
        val source = requireCatalogueSource(sourceId)
        return executeWithTimeout("getPopularManga") {
            source.getPopularManga(page)
        }
    }

    suspend fun getLatestUpdates(sourceId: Long, page: Int): MangasPage {
        val source = requireCatalogueSource(sourceId)
        if (!source.supportsLatest) {
            throw UnsupportedOperationException("Source '${source.name}' does not support latest updates")
        }
        return executeWithTimeout("getLatestUpdates") {
            source.getLatestUpdates(page)
        }
    }

    suspend fun searchManga(sourceId: Long, page: Int, query: String, filters: FilterList = FilterList()): MangasPage {
        val source = requireCatalogueSource(sourceId)
        return executeWithTimeout("searchManga") {
            source.getSearchManga(page, query, filters)
        }
    }

    suspend fun getMangaDetails(sourceId: Long, manga: SManga): SManga {
        val source = requireCatalogueSource(sourceId)
        return executeWithTimeout("getMangaDetails") {
            source.getMangaDetails(manga)
        }
    }

    suspend fun getChapterList(sourceId: Long, manga: SManga): List<SChapter> {
        val source = requireCatalogueSource(sourceId)
        return executeWithTimeout("getChapterList") {
            source.getChapterList(manga)
        }
    }

    suspend fun getPageList(sourceId: Long, chapter: SChapter): List<Page> {
        val source = requireCatalogueSource(sourceId)
        return executeWithTimeout("getPageList") {
            source.getPageList(chapter)
        }
    }

    suspend fun getImageUrl(sourceId: Long, page: Page): String {
        val source = requireCatalogueSource(sourceId)
        return executeWithTimeout("getImageUrl") {
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

    private suspend fun <T> executeWithTimeout(
        operation: String,
        timeout: Duration = defaultTimeout,
        block: suspend () -> T,
    ): T {
        return try {
            withTimeout(timeout) {
                block()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.error("Timeout executing $operation after $timeout")
            throw SourceTimeoutException("Operation '$operation' timed out after $timeout")
        } catch (e: Exception) {
            logger.error("Error executing $operation", e)
            throw SourceExecutionException("Failed to execute '$operation': ${e.message}", e)
        }
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
class SourceExecutionException(message: String, cause: Throwable) : RuntimeException(message, cause)
