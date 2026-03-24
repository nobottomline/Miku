package miku.domain.repository

import miku.domain.model.Chapter

interface ChapterRepository {
    suspend fun getChapter(id: Long): Chapter?
    suspend fun getChaptersByManga(mangaId: Long): List<Chapter>
    suspend fun upsertChapters(chapters: List<Chapter>): List<Chapter>
    suspend fun markChapterRead(chapterId: Long, userId: Long, lastPageRead: Int = 0)
    suspend fun getReadChapterIds(mangaId: Long, userId: Long): Set<Long>
    suspend fun deleteChaptersByManga(mangaId: Long)
}
