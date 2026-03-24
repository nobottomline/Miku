package miku.server.graphql

import miku.domain.model.Category
import miku.domain.model.LibraryManga
import miku.domain.repository.LibraryRepository
import miku.domain.usecase.library.ManageLibraryUseCase
import org.koin.java.KoinJavaComponent.inject

class LibraryQuery : Query {
    private val libraryUseCase: ManageLibraryUseCase by inject(ManageLibraryUseCase::class.java)
    private val libraryRepository: LibraryRepository by inject(LibraryRepository::class.java)

    suspend fun library(userId: Long, categoryId: Long? = null): List<LibraryManga> {
        return libraryUseCase.getLibrary(userId, categoryId)
    }

    suspend fun categories(userId: Long): List<Category> {
        return libraryRepository.getCategories(userId)
    }
}
