package miku.database.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object LibraryTable : Table("library") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val mangaId = long("manga_id").references(MangaTable.id)
    val categoryId = long("category_id").nullable()
    val addedAt = timestamp("added_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, mangaId)
        index(false, userId)
    }
}
