package miku.server.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import miku.server.graphql.configureGraphQL
import miku.server.route.authRoutes
import miku.server.route.extensionRoutes
import miku.server.route.imageProxyRoutes
import miku.server.route.libraryRoutes
import miku.server.route.mangaRoutes
import miku.server.route.sourceRoutes
import miku.server.route.healthRoutes

fun Application.configureRouting() {
    // GraphQL has its own routing block to avoid plugin conflicts
    configureGraphQL()

    // REST API
    routing {
        healthRoutes()

        route("/api/v1") {
            authRoutes()
            sourceRoutes()
            mangaRoutes()
            libraryRoutes()
            extensionRoutes()
            imageProxyRoutes()
        }
    }
}
