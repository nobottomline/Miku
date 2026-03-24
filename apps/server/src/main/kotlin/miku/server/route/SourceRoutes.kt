package miku.server.route

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import miku.server.service.MikuSourceService
import org.koin.java.KoinJavaComponent.inject

fun Route.sourceRoutes() {
    val sourceService : MikuSourceService by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }

    route("/sources") {
        get {
            val lang = call.parameters["lang"]
            val sources = if (lang != null) {
                sourceService.getSourcesByLang(lang)
            } else {
                sourceService.getAllSources()
            }
            call.respond(sources)
        }

        get("/languages") {
            call.respond(sourceService.getAvailableLanguages())
        }

        get("/{sourceId}") {
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
            val source = sourceService.getSource(sourceId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Source not found"))
            call.respond(source)
        }

        get("/{sourceId}/filters") {
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
            val filters = sourceService.getFilters(sourceId)
            call.respond(filters)
        }

        get("/{sourceId}/popular") {
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val result = sourceService.getPopularManga(sourceId, page)
            call.respond(result)
        }

        get("/{sourceId}/latest") {
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val result = sourceService.getLatestUpdates(sourceId, page)
            call.respond(result)
        }

        get("/{sourceId}/search") {
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
            val query = call.parameters["q"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing query parameter 'q'"))
            val page = call.parameters["page"]?.toIntOrNull() ?: 1

            // Input validation
            if (query.length > 200) {
                return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Query too long (max 200 chars)"))
            }

            val result = sourceService.searchManga(sourceId, page, query)
            call.respond(result)
        }
    }
}
