package miku.server.route

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import miku.extension.runner.ExtensionManager
import miku.server.config.AuthorizationException

fun Route.repoRoutes() {
    val extensionManager: ExtensionManager by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }

    route("/repos") {
        // List trusted repos
        get {
            call.respond(extensionManager.repository.getTrustedRepos().map {
                RepoInfo(it.name, it.url)
            })
        }

        // Add trusted repo (admin only)
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()!!
                if (principal.payload.getClaim("role").asString() != "ADMIN") {
                    throw AuthorizationException("Only admins can manage repos")
                }

                val request = call.receive<AddRepoRequest>()
                try {
                    extensionManager.repository.addTrustedRepo(request.name, request.url)
                    call.respond(HttpStatusCode.Created, mapOf("message" to "Repository added: ${request.name}"))
                } catch (e: SecurityException) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to e.message))
                }
            }
        }
    }
}

@Serializable data class RepoInfo(val name: String, val url: String)
@Serializable data class AddRepoRequest(val name: String, val url: String)
