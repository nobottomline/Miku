package miku.server.route

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import miku.server.service.DownloadService

fun Route.downloadRoutes() {
    val downloadService: DownloadService by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }

    route("/downloads") {
        // Start chapter download
        post("/chapter") {
            val request = call.receive<DownloadChapterRequest>()
            val downloadId = downloadService.startChapterDownload(
                sourceId = request.sourceId,
                mangaTitle = request.mangaTitle,
                chapterUrl = request.chapterUrl,
                chapterName = request.chapterName,
            )
            call.respond(HttpStatusCode.Accepted, mapOf("downloadId" to downloadId, "message" to "Download started"))
        }

        // Get download progress
        get("/status/{downloadId}") {
            val downloadId = call.parameters["downloadId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing downloadId"))
            val progress = downloadService.getProgress(downloadId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Download not found"))
            call.respond(progress)
        }

        // Get all active downloads
        get("/status") {
            call.respond(downloadService.getAllDownloads())
        }

        // Download as ZIP
        get("/zip") {
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing sourceId"))
            val mangaTitle = call.parameters["manga"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing manga"))
            val chapterName = call.parameters["chapter"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing chapter"))

            val zip = downloadService.generateZip(sourceId, mangaTitle, chapterName)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Chapter not downloaded"))

            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${mangaTitle} - ${chapterName}.zip\"")
            call.respondBytes(zip, ContentType.Application.Zip)
        }

        // Download as PDF
        get("/pdf") {
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing sourceId"))
            val mangaTitle = call.parameters["manga"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing manga"))
            val chapterName = call.parameters["chapter"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing chapter"))

            val pdf = downloadService.generatePdf(sourceId, mangaTitle, chapterName)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Chapter not downloaded or PDF failed"))

            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${mangaTitle} - ${chapterName}.pdf\"")
            call.respondBytes(pdf, ContentType.Application.Pdf)
        }
    }
}

@Serializable
data class DownloadChapterRequest(
    val sourceId: Long,
    val mangaTitle: String,
    val chapterUrl: String,
    val chapterName: String,
)
