package miku.server.route

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import miku.domain.repository.UserRepository
import miku.server.config.AuthenticationException
import miku.server.service.MikuAuthService
import org.koin.java.KoinJavaComponent.inject

fun Route.authRoutes() {
    val authService : MikuAuthService by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }
    val userRepository : UserRepository by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }

    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val user = authService.register(request.username, request.email, request.password)
            call.respond(HttpStatusCode.Created, UserResponse(user.id, user.username, user.email, user.role.name))
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val tokens = authService.login(request.username, request.password)
            call.respond(TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresIn))
        }

        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            val tokens = authService.refreshToken(request.refreshToken)
            call.respond(TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresIn))
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asLong()
                val user = userRepository.findById(userId)
                    ?: throw AuthenticationException("User not found")
                call.respond(UserResponse(user.id, user.username, user.email, user.role.name))
            }

            post("/change-password") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asLong()
                val request = call.receive<ChangePasswordRequest>()
                authService.changePassword(userId, request.oldPassword, request.newPassword)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Password changed successfully"))
            }
        }
    }
}

@Serializable data class RegisterRequest(val username: String, val email: String, val password: String)
@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class ChangePasswordRequest(val oldPassword: String, val newPassword: String)
@Serializable data class TokenResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long)
@Serializable data class UserResponse(val id: Long, val username: String, val email: String, val role: String)
