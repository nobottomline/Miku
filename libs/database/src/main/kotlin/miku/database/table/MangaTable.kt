package miku.database.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object MangaTable : Table("manga") {
    val id = long("id").autoIncrement()
    val sourceId = long("source_id")
    val url = varchar("url", 2048)
    val title = varchar("title", 512)
    val artist = varchar("artist", 512).nullable()
    val author = varchar("author", 512).nullable()
    val description = text("description").nullable()
    val genres = text("genres").default("") // Comma-separated
    val status = integer("status").default(0)
    val thumbnailUrl = varchar("thumbnail_url", 2048).nullable()
    val initialized = bool("initialized").default(false)
    val lastUpdated = timestamp("last_updated").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(sourceId, url)
        index(false, sourceId)
        index(false, title)
    }
}
