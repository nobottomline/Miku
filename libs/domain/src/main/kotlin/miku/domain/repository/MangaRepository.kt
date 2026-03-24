package miku.domain.repository

import miku.domain.model.Manga

interface MangaRepository {
    suspend fun getManga(id: Long): Manga?
    suspend fun getMangaBySourceAndUrl(sourceId: Long, url: String): Manga?
    suspend fun upsertManga(manga: Manga): Manga
    suspend fun deleteManga(id: Long)
    suspend fun getLibraryManga(userId: Long): List<Manga>
    suspend fun searchLocalManga(query: String, limit: Int = 20): List<Manga>
}
