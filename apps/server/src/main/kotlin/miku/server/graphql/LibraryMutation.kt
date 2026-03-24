package miku.server.graphql

import miku.domain.model.Category
import miku.domain.repository.LibraryRepository
import miku.domain.usecase.library.ManageLibraryUseCase
import org.koin.java.KoinJavaComponent.inject

class LibraryMutation : Mutation {
    private val libraryUseCase: ManageLibraryUseCase by inject(ManageLibraryUseCase::class.java)
    private val libraryRepository: LibraryRepository by inject(LibraryRepository::class.java)

    suspend fun addToLibrary(userId: Long, mangaId: Long, categoryId: Long? = null): Boolean {
        libraryUseCase.addToLibrary(userId, mangaId, categoryId)
        return true
    }

    suspend fun removeFromLibrary(userId: Long, mangaId: Long): Boolean {
        libraryUseCase.removeFromLibrary(userId, mangaId)
        return true
    }

    suspend fun createCategory(userId: Long, name: String): Category {
        return libraryRepository.createCategory(userId, name)
    }

    suspend fun deleteCategory(userId: Long, categoryId: Long): Boolean {
        libraryRepository.deleteCategory(userId, categoryId)
        return true
    }
}
