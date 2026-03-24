package miku.server.graphql

import miku.domain.model.FilterData
import miku.domain.model.MangaPageResult
import miku.domain.model.SourceInfo
import miku.server.service.MikuSourceService
import org.koin.java.KoinJavaComponent.inject

class SourceQuery : Query {
    private val sourceService: MikuSourceService by inject(MikuSourceService::class.java)

    fun sources(lang: String? = null): List<SourceInfo> {
        return if (lang != null) {
            sourceService.getSourcesByLang(lang)
        } else {
            sourceService.getAllSources()
        }
    }

    fun source(id: Long): SourceInfo? {
        return sourceService.getSource(id)
    }

    fun languages(): List<String> {
        return sourceService.getAvailableLanguages().toList()
    }

    fun filters(sourceId: Long): List<FilterData> {
        return sourceService.getFilters(sourceId)
    }

    suspend fun popularManga(sourceId: Long, page: Int = 1): MangaPageResult {
        return sourceService.getPopularManga(sourceId, page)
    }

    suspend fun latestManga(sourceId: Long, page: Int = 1): MangaPageResult {
        return sourceService.getLatestUpdates(sourceId, page)
    }

    suspend fun searchManga(sourceId: Long, query: String, page: Int = 1): MangaPageResult {
        require(query.length <= 200) { "Query too long" }
        return sourceService.searchManga(sourceId, page, query)
    }
}
