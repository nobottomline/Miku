package miku.domain.service

import miku.domain.model.Chapter
import miku.domain.model.FilterData
import miku.domain.model.Manga
import miku.domain.model.MangaPageResult
import miku.domain.model.PageData
import miku.domain.model.SourceInfo

interface SourceService {
    fun getAllSources(): List<SourceInfo>
    fun getSource(sourceId: Long): SourceInfo?
    fun getSourcesByLang(lang: String): List<SourceInfo>
    fun getAvailableLanguages(): Set<String>
    fun getFilters(sourceId: Long): List<FilterData>

    suspend fun getPopularManga(sourceId: Long, page: Int): MangaPageResult
    suspend fun getLatestUpdates(sourceId: Long, page: Int): MangaPageResult
    suspend fun searchManga(sourceId: Long, page: Int, query: String): MangaPageResult
    suspend fun getMangaDetails(sourceId: Long, mangaUrl: String): Manga
    suspend fun getChapterList(sourceId: Long, mangaUrl: String): List<Chapter>
    suspend fun getPageList(sourceId: Long, chapterUrl: String): List<PageData>
    suspend fun getImageUrl(sourceId: Long, pageUrl: String): String
    fun getImageHeaders(sourceId: Long): Map<String, String>
}
