package miku.database.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import miku.database.table.ChapterTable
import miku.database.table.ReadStatusTable
import miku.domain.model.Chapter
import miku.domain.repository.ChapterRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedChapterRepository : ChapterRepository {

    override suspend fun getChapter(id: Long): Chapter? = newSuspendedTransaction {
        ChapterTable.selectAll().where { ChapterTable.id eq id }
            .map { it.toChapter() }
            .singleOrNull()
    }

    override suspend fun getChaptersByManga(mangaId: Long): List<Chapter> = newSuspendedTransaction {
        ChapterTable.selectAll().where { ChapterTable.mangaId eq mangaId }
            .orderBy(ChapterTable.chapterNumber, SortOrder.DESC)
            .map { it.toChapter() }
    }

    override suspend fun upsertChapters(chapters: List<Chapter>): List<Chapter> = newSuspendedTransaction {
        chapters.map { chapter ->
            val existing = ChapterTable.selectAll().where {
                (ChapterTable.mangaId eq chapter.mangaId) and (ChapterTable.url eq chapter.url)
            }.singleOrNull()

            if (existing != null) {
                ChapterTable.update({ ChapterTable.id eq existing[ChapterTable.id] }) {
                    it[name] = chapter.name
                    it[chapterNumber] = chapter.chapterNumber
                    it[scanlator] = chapter.scanlator
                    it[dateUpload] = chapter.dateUpload
                    it[dateFetch] = Clock.System.now()
                }
                chapter.copy(id = existing[ChapterTable.id])
            } else {
                val id = ChapterTable.insert {
                    it[mangaId] = chapter.mangaId
                    it[sourceId] = chapter.sourceId
                    it[url] = chapter.url
                    it[name] = chapter.name
                    it[chapterNumber] = chapter.chapterNumber
                    it[scanlator] = chapter.scanlator
                    it[dateUpload] = chapter.dateUpload
                    it[dateFetch] = Clock.System.now()
                }[ChapterTable.id]
                chapter.copy(id = id)
            }
        }
    }

    override suspend fun markChapterRead(chapterId: Long, userId: Long, lastPageRead: Int) = newSuspendedTransaction {
        val chapter = ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.single()

        val existing = ReadStatusTable.selectAll().where {
            (ReadStatusTable.userId eq userId) and (ReadStatusTable.chapterId eq chapterId)
        }.singleOrNull()

        if (existing != null) {
            ReadStatusTable.update({
                (ReadStatusTable.userId eq userId) and (ReadStatusTable.chapterId eq chapterId)
            }) {
                it[ReadStatusTable.lastPageRead] = lastPageRead
                it[readAt] = Clock.System.now()
            }
        } else {
            ReadStatusTable.insert {
                it[ReadStatusTable.userId] = userId
                it[ReadStatusTable.chapterId] = chapterId
                it[mangaId] = chapter[ChapterTable.mangaId]
                it[ReadStatusTable.lastPageRead] = lastPageRead
                it[readAt] = Clock.System.now()
            }
        }
        Unit
    }

    override suspend fun getReadChapterIds(mangaId: Long, userId: Long): Set<Long> = newSuspendedTransaction {
        ReadStatusTable.selectAll().where {
            (ReadStatusTable.userId eq userId) and (ReadStatusTable.mangaId eq mangaId)
        }.map { it[ReadStatusTable.chapterId] }.toSet()
    }

    override suspend fun deleteChaptersByManga(mangaId: Long) = newSuspendedTransaction {
        ChapterTable.deleteWhere { ChapterTable.mangaId eq mangaId }
        Unit
    }

    private fun ResultRow.toChapter() = Chapter(
        id = this[ChapterTable.id],
        mangaId = this[ChapterTable.mangaId],
        sourceId = this[ChapterTable.sourceId],
        url = this[ChapterTable.url],
        name = this[ChapterTable.name],
        chapterNumber = this[ChapterTable.chapterNumber],
        scanlator = this[ChapterTable.scanlator],
        dateUpload = this[ChapterTable.dateUpload],
        dateFetch = this[ChapterTable.dateFetch],
    )
}
