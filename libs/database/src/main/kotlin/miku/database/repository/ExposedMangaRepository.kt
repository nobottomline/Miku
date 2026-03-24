package miku.database.repository

import kotlinx.datetime.Clock
import miku.database.table.MangaTable
import miku.domain.model.Manga
import miku.domain.model.MangaStatus
import miku.domain.repository.MangaRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedMangaRepository : MangaRepository {

    override suspend fun getManga(id: Long): Manga? = newSuspendedTransaction {
        MangaTable.selectAll().where { MangaTable.id eq id }
            .map { it.toManga() }
            .singleOrNull()
    }

    override suspend fun getMangaBySourceAndUrl(sourceId: Long, url: String): Manga? = newSuspendedTransaction {
        MangaTable.selectAll().where {
            (MangaTable.sourceId eq sourceId) and (MangaTable.url eq url)
        }.map { it.toManga() }.singleOrNull()
    }

    override suspend fun upsertManga(manga: Manga): Manga = newSuspendedTransaction {
        val existing = MangaTable.selectAll().where {
            (MangaTable.sourceId eq manga.sourceId) and (MangaTable.url eq manga.url)
        }.singleOrNull()

        if (existing != null) {
            MangaTable.update({ MangaTable.id eq existing[MangaTable.id] }) {
                it[title] = manga.title
                it[artist] = manga.artist
                it[author] = manga.author
                it[description] = manga.description
                it[genres] = manga.genres.joinToString(", ")
                it[status] = manga.status.value
                it[thumbnailUrl] = manga.thumbnailUrl
                it[initialized] = manga.initialized
                it[lastUpdated] = Clock.System.now()
            }
            manga.copy(id = existing[MangaTable.id])
        } else {
            val id = MangaTable.insert {
                it[sourceId] = manga.sourceId
                it[url] = manga.url
                it[title] = manga.title
                it[artist] = manga.artist
                it[author] = manga.author
                it[description] = manga.description
                it[genres] = manga.genres.joinToString(", ")
                it[status] = manga.status.value
                it[thumbnailUrl] = manga.thumbnailUrl
                it[initialized] = manga.initialized
                it[lastUpdated] = Clock.System.now()
            }[MangaTable.id]
            manga.copy(id = id)
        }
    }

    override suspend fun deleteManga(id: Long) = newSuspendedTransaction {
        MangaTable.deleteWhere { MangaTable.id eq id }
        Unit
    }

    override suspend fun getLibraryManga(userId: Long): List<Manga> = newSuspendedTransaction {
        MangaTable.selectAll().where { MangaTable.id inList
            miku.database.table.LibraryTable.select(miku.database.table.LibraryTable.mangaId)
                .where { miku.database.table.LibraryTable.userId eq userId }
                .map { it[miku.database.table.LibraryTable.mangaId] }
        }.map { it.toManga() }
    }

    override suspend fun searchLocalManga(query: String, limit: Int): List<Manga> = newSuspendedTransaction {
        MangaTable.selectAll().where {
            MangaTable.title.lowerCase() like "%${query.lowercase()}%"
        }.limit(limit).map { it.toManga() }
    }

    private fun ResultRow.toManga() = Manga(
        id = this[MangaTable.id],
        sourceId = this[MangaTable.sourceId],
        url = this[MangaTable.url],
        title = this[MangaTable.title],
        artist = this[MangaTable.artist],
        author = this[MangaTable.author],
        description = this[MangaTable.description],
        genres = this[MangaTable.genres].split(", ").filter { it.isNotBlank() },
        status = MangaStatus.fromInt(this[MangaTable.status]),
        thumbnailUrl = this[MangaTable.thumbnailUrl],
        initialized = this[MangaTable.initialized],
        lastUpdated = this[MangaTable.lastUpdated],
    )
}
