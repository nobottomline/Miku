package miku.domain.usecase.manga

import miku.domain.model.Chapter
import miku.domain.repository.ChapterRepository
import miku.domain.repository.MangaRepository
import miku.domain.service.SourceService

class GetChapterListUseCase(
    private val sourceService: SourceService,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
) {
    suspend operator fun invoke(sourceId: Long, mangaUrl: String): List<Chapter> {
        val manga = mangaRepository.getMangaBySourceAndUrl(sourceId, mangaUrl)
            ?: throw IllegalArgumentException("Manga not found in database")

        val chapters = sourceService.getChapterList(sourceId, mangaUrl)

        // Associate chapters with manga and save
        val chaptersWithManga = chapters.map { it.copy(mangaId = manga.id) }
        return chapterRepository.upsertChapters(chaptersWithManga)
    }
}
