package miku.server.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import miku.domain.model.User
import miku.server.ServerConfig
import java.util.*

class JwtService(private val config: ServerConfig) {

    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    fun generateAccessToken(user: User): String {
        return JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .withClaim("userId", user.id)
            .withClaim("username", user.username)
            .withClaim("role", user.role.name)
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + config.jwtAccessExpireMinutes * 60 * 1000))
            .sign(algorithm)
    }

    fun generateRefreshToken(user: User): String {
        return JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .withClaim("userId", user.id)
            .withClaim("type", "refresh")
            .withExpiresAt(Date(System.currentTimeMillis() + config.jwtRefreshExpireDays * 24 * 60 * 60 * 1000))
            .sign(algorithm)
    }

    fun validateToken(token: String): Long? {
        return try {
            val verifier = JWT.require(algorithm)
                .withIssuer(config.jwtIssuer)
                .withAudience(config.jwtAudience)
                .build()
            val decoded = verifier.verify(token)
            decoded.getClaim("userId").asLong()
        } catch (_: Exception) {
            null
        }
    }

    fun isRefreshToken(token: String): Boolean {
        return try {
            val decoded = JWT.decode(token)
            decoded.getClaim("type").asString() == "refresh"
        } catch (_: Exception) {
            false
        }
    }
}
