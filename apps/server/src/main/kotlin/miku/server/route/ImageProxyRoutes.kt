package miku.server.route

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import miku.server.service.MikuSourceService
import org.koin.java.KoinJavaComponent.inject
import java.net.InetAddress
import java.net.URI

fun Route.imageProxyRoutes() {
    val sourceService : MikuSourceService by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }

    val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
                followSslRedirects(true)
            }
        }
    }

    route("/image") {
        get("/proxy") {
            val imageUrl = call.parameters["url"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing 'url' parameter"))
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()

            // SSRF protection: validate URL
            if (!isValidImageUrl(imageUrl)) {
                return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Invalid image URL"))
            }

            // Get source-specific headers
            val sourceHeaders = sourceId?.let { sourceService.getImageHeaders(it) } ?: emptyMap()

            try {
                val response = httpClient.get(imageUrl) {
                    sourceHeaders.forEach { (key, value) ->
                        header(key, value)
                    }
                    header("Referer", URI(imageUrl).let { "${it.scheme}://${it.host}/" })
                }

                val contentType = response.contentType() ?: ContentType.Image.Any

                // Validate response is actually an image
                if (!contentType.match(ContentType.Image.Any)) {
                    return@get call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Response is not an image"))
                }

                call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                call.response.header(HttpHeaders.ContentType, contentType.toString())
                call.respondBytes(
                    bytes = response.bodyAsBytes(),
                    contentType = contentType,
                )
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Failed to fetch image"))
            }
        }
    }
}

private fun isValidImageUrl(url: String): Boolean {
    return try {
        val uri = URI(url)

        // Must be HTTP or HTTPS
        if (uri.scheme !in listOf("http", "https")) return false

        // Block private/internal IPs (SSRF protection)
        val host = uri.host ?: return false
        val address = InetAddress.getByName(host)

        if (address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isAnyLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }

        // Block common internal hostnames
        val blockedHosts = listOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "metadata.google.internal")
        if (host.lowercase() in blockedHosts) return false

        // Block AWS metadata endpoint
        if (host == "169.254.169.254") return false

        true
    } catch (_: Exception) {
        false
    }
}
