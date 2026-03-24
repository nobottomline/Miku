package miku.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long = 0,
    val username: String,
    val email: String,
    val passwordHash: String = "",
    val role: UserRole = UserRole.USER,
    val createdAt: Instant? = null,
    val lastLoginAt: Instant? = null,
    val isActive: Boolean = true,
)

@Serializable
enum class UserRole {
    ADMIN,
    USER,
    READONLY,
}
