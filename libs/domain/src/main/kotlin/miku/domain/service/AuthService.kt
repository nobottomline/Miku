package miku.domain.service

import miku.domain.model.User

interface AuthService {
    suspend fun register(username: String, email: String, password: String): User
    suspend fun login(username: String, password: String): TokenPair
    suspend fun refreshToken(refreshToken: String): TokenPair
    suspend fun validateToken(token: String): User?
    suspend fun changePassword(userId: Long, oldPassword: String, newPassword: String)
}

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)
