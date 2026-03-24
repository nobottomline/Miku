package miku.database.repository

import kotlinx.datetime.Clock
import miku.database.table.CategoryTable
import miku.database.table.LibraryTable
import miku.database.table.MangaTable
import miku.database.table.ChapterTable
import miku.database.table.ReadStatusTable
import miku.domain.model.Category
import miku.domain.model.LibraryManga
import miku.domain.model.Manga
import miku.domain.model.MangaStatus
import miku.domain.repository.LibraryRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedLibraryRepository : LibraryRepository {

    override suspend fun getLibrary(userId: Long, categoryId: Long?): List<LibraryManga> = newSuspendedTransaction {
        val query = LibraryTable.innerJoin(MangaTable, { mangaId }, { MangaTable.id })
            .selectAll()
            .where { LibraryTable.userId eq userId }

        if (categoryId != null) {
            query.andWhere { LibraryTable.categoryId eq categoryId }
        }

        query.map { row ->
            val manga = Manga(
                id = row[MangaTable.id],
                sourceId = row[MangaTable.sourceId],
                url = row[MangaTable.url],
                title = row[MangaTable.title],
                artist = row[MangaTable.artist],
                author = row[MangaTable.author],
                description = row[MangaTable.description],
                genres = row[MangaTable.genres].split(", ").filter { it.isNotBlank() },
                status = MangaStatus.fromInt(row[MangaTable.status]),
                thumbnailUrl = row[MangaTable.thumbnailUrl],
                initialized = row[MangaTable.initialized],
                inLibrary = true,
            )

            val totalChapters = ChapterTable.selectAll()
                .where { ChapterTable.mangaId eq manga.id }.count()
            val readChapters = ReadStatusTable.selectAll()
                .where { (ReadStatusTable.userId eq userId) and (ReadStatusTable.mangaId eq manga.id) }.count()

            val category = row[LibraryTable.categoryId]?.let { catId ->
                CategoryTable.selectAll().where { CategoryTable.id eq catId }
                    .map { Category(it[CategoryTable.id], it[CategoryTable.name], it[CategoryTable.order]) }
                    .singleOrNull()
            }

            LibraryManga(
                manga = manga,
                unreadCount = (totalChapters - readChapters).toInt(),
                category = category,
                addedAt = row[LibraryTable.addedAt],
            )
        }
    }

    override suspend fun addToLibrary(userId: Long, mangaId: Long, categoryId: Long?) = newSuspendedTransaction {
        LibraryTable.insert {
            it[LibraryTable.userId] = userId
            it[LibraryTable.mangaId] = mangaId
            it[LibraryTable.categoryId] = categoryId
            it[addedAt] = Clock.System.now()
        }
        Unit
    }

    override suspend fun removeFromLibrary(userId: Long, mangaId: Long) = newSuspendedTransaction {
        LibraryTable.deleteWhere {
            (LibraryTable.userId eq userId) and (LibraryTable.mangaId eq mangaId)
        }
        Unit
    }

    override suspend fun isInLibrary(userId: Long, mangaId: Long): Boolean = newSuspendedTransaction {
        LibraryTable.selectAll().where {
            (LibraryTable.userId eq userId) and (LibraryTable.mangaId eq mangaId)
        }.count() > 0
    }

    override suspend fun getCategories(userId: Long): List<Category> = newSuspendedTransaction {
        CategoryTable.selectAll().where { CategoryTable.userId eq userId }
            .orderBy(CategoryTable.order)
            .map { Category(it[CategoryTable.id], it[CategoryTable.name], it[CategoryTable.order]) }
    }

    override suspend fun createCategory(userId: Long, name: String): Category = newSuspendedTransaction {
        val maxOrder = CategoryTable.selectAll().where { CategoryTable.userId eq userId }
            .maxOfOrNull { it[CategoryTable.order] } ?: -1

        val id = CategoryTable.insert {
            it[CategoryTable.userId] = userId
            it[CategoryTable.name] = name
            it[order] = maxOrder + 1
        }[CategoryTable.id]

        Category(id, name, maxOrder + 1)
    }

    override suspend fun updateCategory(category: Category): Category = newSuspendedTransaction {
        CategoryTable.update({ CategoryTable.id eq category.id }) {
            it[name] = category.name
            it[order] = category.order
        }
        category
    }

    override suspend fun deleteCategory(userId: Long, categoryId: Long) = newSuspendedTransaction {
        // Move manga in this category to uncategorized
        LibraryTable.update({ (LibraryTable.userId eq userId) and (LibraryTable.categoryId eq categoryId) }) {
            it[LibraryTable.categoryId] = null
        }
        CategoryTable.deleteWhere { (CategoryTable.id eq categoryId) and (CategoryTable.userId eq userId) }
        Unit
    }

    override suspend fun reorderCategories(userId: Long, categoryIds: List<Long>) = newSuspendedTransaction {
        categoryIds.forEachIndexed { index, id ->
            CategoryTable.update({ (CategoryTable.id eq id) and (CategoryTable.userId eq userId) }) {
                it[order] = index
            }
        }
        Unit
    }
}
