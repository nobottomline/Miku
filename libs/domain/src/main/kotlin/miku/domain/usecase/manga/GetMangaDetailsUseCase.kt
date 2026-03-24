package miku.domain.usecase.manga

import miku.domain.model.Manga
import miku.domain.repository.MangaRepository
import miku.domain.service.SourceService

class GetMangaDetailsUseCase(
    private val sourceService: SourceService,
    private val mangaRepository: MangaRepository,
) {
    suspend operator fun invoke(sourceId: Long, mangaUrl: String): Manga {
        // Check if we have cached details
        val cached = mangaRepository.getMangaBySourceAndUrl(sourceId, mangaUrl)
        if (cached != null && cached.initialized) {
            return cached
        }

        // Fetch from source
        val manga = sourceService.getMangaDetails(sourceId, mangaUrl)

        // Cache in database
        return mangaRepository.upsertManga(manga)
    }
}
