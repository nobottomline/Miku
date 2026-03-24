package miku.domain.usecase.library

import miku.domain.model.LibraryManga
import miku.domain.repository.LibraryRepository
import miku.domain.repository.MangaRepository

class ManageLibraryUseCase(
    private val libraryRepository: LibraryRepository,
    private val mangaRepository: MangaRepository,
) {
    suspend fun getLibrary(userId: Long, categoryId: Long? = null): List<LibraryManga> {
        return libraryRepository.getLibrary(userId, categoryId)
    }

    suspend fun addToLibrary(userId: Long, mangaId: Long, categoryId: Long? = null) {
        val manga = mangaRepository.getManga(mangaId)
            ?: throw IllegalArgumentException("Manga not found")
        libraryRepository.addToLibrary(userId, mangaId, categoryId)
    }

    suspend fun removeFromLibrary(userId: Long, mangaId: Long) {
        libraryRepository.removeFromLibrary(userId, mangaId)
    }

    suspend fun isInLibrary(userId: Long, mangaId: Long): Boolean {
        return libraryRepository.isInLibrary(userId, mangaId)
    }
}
