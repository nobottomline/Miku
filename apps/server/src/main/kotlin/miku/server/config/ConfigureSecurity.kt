package miku.server.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import miku.server.ServerConfig
import org.slf4j.event.Level

fun Application.configureSecurity(config: ServerConfig) {
    // Security headers
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("X-XSS-Protection", "1; mode=block")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
        header(
            "Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'"
        )
        header("X-Powered-By", "Miku")
    }

    // Compression
    install(Compression) {
        gzip { priority = 1.0 }
        deflate { priority = 0.9 }
    }

    // Logging
    install(CallLogging) {
        level = if (config.isProduction) Level.INFO else Level.DEBUG
        filter { call -> call.request.path().startsWith("/api") }
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val uri = call.request.uri
            val duration = call.processingTimeMillis()
            "$method $uri -> $status (${duration}ms)"
        }
    }
}
