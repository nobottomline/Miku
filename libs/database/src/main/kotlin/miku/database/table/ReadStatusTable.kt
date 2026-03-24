package miku.database.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object ReadStatusTable : Table("read_status") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val chapterId = long("chapter_id").references(ChapterTable.id)
    val mangaId = long("manga_id").references(MangaTable.id)
    val lastPageRead = integer("last_page_read").default(0)
    val readAt = timestamp("read_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, chapterId)
        index(false, userId, mangaId)
    }
}
