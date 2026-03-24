package miku.server.route

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import miku.server.service.MikuSourceService
import org.koin.java.KoinJavaComponent.inject

fun Route.mangaRoutes() {
    val sourceService : MikuSourceService by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }

    route("/manga") {
        get("/{sourceId}/details") {
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
            val mangaUrl = call.parameters["url"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing manga URL"))

            val manga = sourceService.getMangaDetails(sourceId, mangaUrl)
            call.respond(manga)
        }

        get("/{sourceId}/chapters") {
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
            val mangaUrl = call.parameters["url"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing manga URL"))

            val chapters = sourceService.getChapterList(sourceId, mangaUrl)
            call.respond(chapters)
        }

        get("/{sourceId}/pages") {
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
            val chapterUrl = call.parameters["url"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing chapter URL"))

            val pages = sourceService.getPageList(sourceId, chapterUrl)
            call.respond(pages)
        }
    }
}
