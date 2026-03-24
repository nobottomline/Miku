package miku.database.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object ChapterTable : Table("chapters") {
    val id = long("id").autoIncrement()
    val mangaId = long("manga_id").references(MangaTable.id)
    val sourceId = long("source_id")
    val url = varchar("url", 2048)
    val name = varchar("name", 512)
    val chapterNumber = float("chapter_number").default(-1f)
    val scanlator = varchar("scanlator", 256).nullable()
    val dateUpload = timestamp("date_upload").nullable()
    val dateFetch = timestamp("date_fetch").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(mangaId, url)
        index(false, mangaId)
    }
}
