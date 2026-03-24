package miku.domain.repository

import miku.domain.model.User

interface UserRepository {
    suspend fun findById(id: Long): User?
    suspend fun findByUsername(username: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun create(user: User): User
    suspend fun update(user: User): User
    suspend fun delete(id: Long)
    suspend fun updateLastLogin(id: Long)
    suspend fun countUsers(): Long
}
