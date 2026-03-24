package miku.server.service

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.datetime.Instant
import miku.domain.model.*
import miku.domain.service.SourceService
import miku.extension.runner.ExtensionRegistry
import miku.extension.runner.SourceExecutor

class MikuSourceService(
    private val executor: SourceExecutor,
    private val registry: ExtensionRegistry,
) : SourceService {

    override fun getAllSources(): List<SourceInfo> {
        return registry.getAllSources().map { source ->
            SourceInfo(
                id = source.id,
                name = source.name,
                lang = source.lang,
                supportsLatest = (source as? CatalogueSource)?.supportsLatest ?: false,
                baseUrl = (source as? HttpSource)?.baseUrl,
            )
        }
    }

    override fun getSource(sourceId: Long): SourceInfo? {
        val source = registry.getSource(sourceId) ?: return null
        return SourceInfo(
            id = source.id,
            name = source.name,
            lang = source.lang,
            supportsLatest = (source as? CatalogueSource)?.supportsLatest ?: false,
            baseUrl = (source as? HttpSource)?.baseUrl,
        )
    }

    override fun getSourcesByLang(lang: String): List<SourceInfo> {
        return registry.getSourcesByLang(lang).map { source ->
            SourceInfo(
                id = source.id,
                name = source.name,
                lang = source.lang,
                supportsLatest = (source as? CatalogueSource)?.supportsLatest ?: false,
                baseUrl = (source as? HttpSource)?.baseUrl,
            )
        }
    }

    override fun getAvailableLanguages(): Set<String> = registry.getAvailableLanguages()

    override fun getFilters(sourceId: Long): List<FilterData> {
        val filters = executor.getFilters(sourceId)
        return filters.map { it.toFilterData() }
    }

    override suspend fun getPopularManga(sourceId: Long, page: Int): MangaPageResult {
        val result = executor.getPopularManga(sourceId, page)
        return MangaPageResult(
            mangas = result.mangas.map { it.toManga(sourceId) },
            hasNextPage = result.hasNextPage,
            page = page,
        )
    }

    override suspend fun getLatestUpdates(sourceId: Long, page: Int): MangaPageResult {
        val result = executor.getLatestUpdates(sourceId, page)
        return MangaPageResult(
            mangas = result.mangas.map { it.toManga(sourceId) },
            hasNextPage = result.hasNextPage,
            page = page,
        )
    }

    override suspend fun searchManga(sourceId: Long, page: Int, query: String): MangaPageResult {
        val result = executor.searchManga(sourceId, page, query)
        return MangaPageResult(
            mangas = result.mangas.map { it.toManga(sourceId) },
            hasNextPage = result.hasNextPage,
            page = page,
        )
    }

    override suspend fun getMangaDetails(sourceId: Long, mangaUrl: String): Manga {
        val sManga = SManga.create().apply { url = mangaUrl; title = "" }
        val details = executor.getMangaDetails(sourceId, sManga)
        // mangaDetailsParse creates new SManga without url — ensure it's set
        try { details.url } catch (_: UninitializedPropertyAccessException) { details.url = mangaUrl }
        return details.toManga(sourceId)
    }

    override suspend fun getChapterList(sourceId: Long, mangaUrl: String): List<Chapter> {
        val sManga = SManga.create().apply { url = mangaUrl }
        val chapters = executor.getChapterList(sourceId, sManga)
        return chapters.mapIndexed { index, ch ->
            Chapter(
                sourceId = sourceId,
                mangaId = 0,
                url = ch.url,
                name = ch.name,
                chapterNumber = ch.chapter_number,
                scanlator = ch.scanlator,
                dateUpload = if (ch.date_upload > 0) Instant.fromEpochMilliseconds(ch.date_upload) else null,
            )
        }
    }

    override suspend fun getPageList(sourceId: Long, chapterUrl: String): List<PageData> {
        val sChapter = eu.kanade.tachiyomi.source.model.SChapter.create().apply { url = chapterUrl }
        val pages = executor.getPageList(sourceId, sChapter)
        return pages.map { page ->
            PageData(
                index = page.index,
                url = page.url,
                imageUrl = page.imageUrl,
            )
        }
    }

    override suspend fun getImageUrl(sourceId: Long, pageUrl: String): String {
        val page = eu.kanade.tachiyomi.source.model.Page(0, pageUrl)
        return executor.getImageUrl(sourceId, page)
    }

    override fun getImageHeaders(sourceId: Long): Map<String, String> {
        return executor.getImageHeaders(sourceId)
    }

    // Conversion helpers
    private fun SManga.toManga(sourceId: Long): Manga {
        val safeUrl = try { url } catch (_: UninitializedPropertyAccessException) { "" }
        val safeTitle = try { title } catch (_: UninitializedPropertyAccessException) { "" }
        return Manga(
            sourceId = sourceId,
            url = safeUrl,
            title = safeTitle,
            artist = artist,
            author = author,
            description = description,
            genres = genre?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
            status = MangaStatus.fromInt(status),
            thumbnailUrl = thumbnail_url,
            initialized = initialized,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun Filter<*>.toFilterData(): FilterData = when (this) {
        is Filter.Header -> FilterData.Header(name)
        is Filter.Separator -> FilterData.Separator(name)
        is Filter.Select<*> -> FilterData.Select(name, values.map { it.toString() }, state)
        is Filter.Text -> FilterData.Text(name, state)
        is Filter.CheckBox -> FilterData.CheckBox(name, state)
        is Filter.TriState -> FilterData.TriState(name, state)
        is Filter.Sort -> FilterData.Sort(
            name, values.toList(),
            state?.let { FilterData.SortSelection(it.index, it.ascending) }
        )
        is Filter.Group<*> -> FilterData.Group(
            name,
            (state as List<Filter<*>>).map { it.toFilterData() }
        )
    }
}
