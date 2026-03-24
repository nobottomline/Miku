package miku.domain.usecase.manga

import miku.domain.model.Manga
import miku.domain.model.MangaPageResult
import miku.domain.model.MangaStatus
import miku.domain.service.SourceService

class GetPopularMangaUseCase(
    private val sourceService: SourceService,
) {
    suspend operator fun invoke(sourceId: Long, page: Int): MangaPageResult {
        return sourceService.getPopularManga(sourceId, page)
    }
}
