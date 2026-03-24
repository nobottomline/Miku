package miku.domain.usecase.manga

import miku.domain.model.MangaPageResult
import miku.domain.service.SourceService

class SearchMangaUseCase(
    private val sourceService: SourceService,
) {
    suspend operator fun invoke(sourceId: Long, query: String, page: Int): MangaPageResult {
        require(query.isNotBlank()) { "Search query must not be blank" }
        require(query.length <= 200) { "Search query too long" }
        return sourceService.searchManga(sourceId, page, query)
    }
}
