package miku.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import miku.server.config.configureAuth
import miku.server.config.configureCors
import miku.server.config.configureDI
import miku.server.config.configureErrorHandling
import miku.server.config.configureRateLimiting
import miku.server.config.configureRouting
import miku.server.config.configureSecurity
import miku.server.config.configureSerialization
import miku.server.config.configureMetrics
import miku.server.config.configureSwagger
import miku.server.config.configureWebSocket
import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory

fun main() {
    // Load .env file into system properties (before any config reads)
    val env = dotenv {
        ignoreIfMissing = true
        systemProperties = true
    }

    val logger = LoggerFactory.getLogger("miku.server")
    val config = ServerConfig.load()

    logger.info("Starting Miku Server v${BuildInfo.VERSION}")
    logger.info("Environment: ${config.environment}")

    embeddedServer(Netty, port = config.port, host = config.host) {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: ServerConfig = ServerConfig.load()) {
    configureDI(config)
    configureSerialization()
    configureSecurity(config)
    configureCors(config)
    configureAuth(config)
    configureRateLimiting()
    configureErrorHandling()
    configureMetrics()
    configureWebSocket()
    configureRouting()
    configureSwagger()
}
