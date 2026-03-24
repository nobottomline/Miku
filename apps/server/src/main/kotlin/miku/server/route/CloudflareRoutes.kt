package miku.server.route

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import miku.server.service.CloudflareBypassService

fun Route.cloudflareRoutes() {
    val cfService: CloudflareBypassService by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }

    route("/cloudflare") {
        // Get status of Cloudflare bypass
        get("/status") {
            call.respond(cfService.getStatus())
        }

        // User provides cookies for a domain
        post("/cookies") {
            val request = call.receive<SetCookiesRequest>()
            if (request.domain.isBlank() || request.cookies.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "domain and cookies required"))
            }
            cfService.setUserCookies(request.domain, request.cookies)
            call.respond(mapOf("message" to "Cookies set for ${request.domain}"))
        }

        // Clear cookies for a domain
        delete("/cookies/{domain}") {
            val domain = call.parameters["domain"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing domain"))
            cfService.clearCookies(domain)
            call.respond(mapOf("message" to "Cookies cleared for $domain"))
        }
    }
}

@Serializable
data class SetCookiesRequest(
    val domain: String,
    val cookies: String,
)
