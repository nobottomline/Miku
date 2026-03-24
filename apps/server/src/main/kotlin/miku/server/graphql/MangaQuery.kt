package miku.server.graphql

import miku.domain.model.Chapter
import miku.domain.model.Manga
import miku.domain.model.PageData
import miku.server.service.MikuSourceService
import org.koin.java.KoinJavaComponent.inject

class MangaQuery : Query {
    private val sourceService: MikuSourceService by inject(MikuSourceService::class.java)

    suspend fun mangaDetails(sourceId: Long, mangaUrl: String): Manga {
        return sourceService.getMangaDetails(sourceId, mangaUrl)
    }

    suspend fun chapters(sourceId: Long, mangaUrl: String): List<Chapter> {
        return sourceService.getChapterList(sourceId, mangaUrl)
    }

    suspend fun pages(sourceId: Long, chapterUrl: String): List<PageData> {
        return sourceService.getPageList(sourceId, chapterUrl)
    }
}
