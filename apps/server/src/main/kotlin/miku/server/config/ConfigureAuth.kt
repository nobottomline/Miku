package miku.server.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import miku.server.ServerConfig

fun Application.configureAuth(config: ServerConfig) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "miku-server"

            verifier(
                JWT.require(Algorithm.HMAC256(config.jwtSecret))
                    .withIssuer(config.jwtIssuer)
                    .withAudience(config.jwtAudience)
                    .build()
            )

            validate { credential ->
                val userId = credential.payload.getClaim("userId")?.asLong()
                val role = credential.payload.getClaim("role")?.asString()

                if (userId != null && role != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired token"))
            }
        }

        // Optional auth - allows both authenticated and anonymous access
        jwt("auth-optional") {
            realm = "miku-server"

            verifier(
                JWT.require(Algorithm.HMAC256(config.jwtSecret))
                    .withIssuer(config.jwtIssuer)
                    .withAudience(config.jwtAudience)
                    .build()
            )

            validate { credential ->
                val userId = credential.payload.getClaim("userId")?.asLong()
                if (userId != null) JWTPrincipal(credential.payload) else null
            }

            // No challenge - allows anonymous access
        }
    }
}
