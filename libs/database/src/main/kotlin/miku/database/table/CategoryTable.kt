package miku.database.table

import org.jetbrains.exposed.sql.Table

object CategoryTable : Table("categories") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val name = varchar("name", 100)
    val order = integer("sort_order").default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, name)
        index(false, userId)
    }
}
