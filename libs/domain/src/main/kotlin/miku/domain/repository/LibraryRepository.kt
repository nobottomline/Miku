package miku.domain.repository

import miku.domain.model.Category
import miku.domain.model.LibraryManga

interface LibraryRepository {
    suspend fun getLibrary(userId: Long, categoryId: Long? = null): List<LibraryManga>
    suspend fun addToLibrary(userId: Long, mangaId: Long, categoryId: Long? = null)
    suspend fun removeFromLibrary(userId: Long, mangaId: Long)
    suspend fun isInLibrary(userId: Long, mangaId: Long): Boolean
    suspend fun getCategories(userId: Long): List<Category>
    suspend fun createCategory(userId: Long, name: String): Category
    suspend fun updateCategory(category: Category): Category
    suspend fun deleteCategory(userId: Long, categoryId: Long)
    suspend fun reorderCategories(userId: Long, categoryIds: List<Long>)
}
