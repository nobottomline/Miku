package miku.server.graphql

import kotlinx.serialization.Serializable
import miku.server.service.MikuAuthService
import org.koin.java.KoinJavaComponent.inject

class AuthMutation : Mutation {
    private val authService: MikuAuthService by inject(MikuAuthService::class.java)

    suspend fun register(username: String, email: String, password: String): AuthPayload {
        val user = authService.register(username, email, password)
        val tokens = authService.login(username, password)
        return AuthPayload(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            userId = user.id,
            username = user.username,
        )
    }

    suspend fun login(username: String, password: String): AuthPayload {
        val tokens = authService.login(username, password)
        val user = authService.validateToken(tokens.accessToken)!!
        return AuthPayload(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            userId = user.id,
            username = user.username,
        )
    }

    suspend fun refreshToken(refreshToken: String): TokenPayload {
        val tokens = authService.refreshToken(refreshToken)
        return TokenPayload(tokens.accessToken, tokens.refreshToken, tokens.expiresIn)
    }
}

data class AuthPayload(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
    val username: String,
)

data class TokenPayload(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)
