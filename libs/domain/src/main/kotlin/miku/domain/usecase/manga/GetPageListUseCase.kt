package miku.domain.usecase.manga

import miku.domain.model.PageData
import miku.domain.service.SourceService

class GetPageListUseCase(
    private val sourceService: SourceService,
) {
    suspend operator fun invoke(sourceId: Long, chapterUrl: String): List<PageData> {
        return sourceService.getPageList(sourceId, chapterUrl)
    }
}
