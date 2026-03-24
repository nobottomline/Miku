package miku.database.repository

import kotlinx.datetime.Clock
import miku.database.table.UsersTable
import miku.domain.model.User
import miku.domain.model.UserRole
import miku.domain.repository.UserRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedUserRepository : UserRepository {

    override suspend fun findById(id: Long): User? = newSuspendedTransaction {
        UsersTable.selectAll().where { UsersTable.id eq id }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun findByUsername(username: String): User? = newSuspendedTransaction {
        UsersTable.selectAll().where { UsersTable.username eq username }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun findByEmail(email: String): User? = newSuspendedTransaction {
        UsersTable.selectAll().where { UsersTable.email eq email }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun create(user: User): User = newSuspendedTransaction {
        val id = UsersTable.insert {
            it[username] = user.username
            it[email] = user.email
            it[passwordHash] = user.passwordHash
            it[role] = user.role.name
            it[isActive] = user.isActive
            it[createdAt] = Clock.System.now()
        }[UsersTable.id]
        user.copy(id = id, createdAt = Clock.System.now())
    }

    override suspend fun update(user: User): User = newSuspendedTransaction {
        UsersTable.update({ UsersTable.id eq user.id }) {
            it[username] = user.username
            it[email] = user.email
            it[role] = user.role.name
            it[isActive] = user.isActive
        }
        user
    }

    override suspend fun delete(id: Long) = newSuspendedTransaction {
        UsersTable.deleteWhere { UsersTable.id eq id }
        Unit
    }

    override suspend fun updateLastLogin(id: Long) = newSuspendedTransaction {
        UsersTable.update({ UsersTable.id eq id }) {
            it[lastLoginAt] = Clock.System.now()
        }
        Unit
    }

    override suspend fun countUsers(): Long = newSuspendedTransaction {
        UsersTable.selectAll().count()
    }

    private fun ResultRow.toUser() = User(
        id = this[UsersTable.id],
        username = this[UsersTable.username],
        email = this[UsersTable.email],
        passwordHash = this[UsersTable.passwordHash],
        role = UserRole.valueOf(this[UsersTable.role]),
        isActive = this[UsersTable.isActive],
        createdAt = this[UsersTable.createdAt],
        lastLoginAt = this[UsersTable.lastLoginAt],
    )
}
