package miku.database.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object UsersTable : Table("users") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 20).default("USER")
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val lastLoginAt = timestamp("last_login_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(true, username)
        index(true, email)
    }
}
