package miku.server.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import miku.server.ServerConfig

fun Application.configureCors(config: ServerConfig) {
    install(CORS) {
        if (config.allowedOrigins.contains("*")) {
            anyHost()
        } else {
            config.allowedOrigins.forEach { origin ->
                allowHost(origin, schemes = listOf("http", "https"))
            }
        }

        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Options)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader("X-Request-ID")

        allowCredentials = true
        maxAgeInSeconds = 3600
    }
}
