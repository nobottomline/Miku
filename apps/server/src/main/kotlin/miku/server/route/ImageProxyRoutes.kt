package miku.server.route

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import miku.server.service.CloudflareBypassService
import miku.server.service.MikuSourceService
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

private const val MAX_IMAGE_SIZE = 20 * 1024 * 1024L // 20 MB

fun Route.imageProxyRoutes() {
    val sourceService: MikuSourceService by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }
    val cfBypass: CloudflareBypassService by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }

    val okClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    route("/image") {
        get("/proxy") {
            val imageUrl = call.parameters["url"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing 'url' parameter"))
            val sourceId = call.parameters["sourceId"]?.toLongOrNull()

            if (!isValidImageUrl(imageUrl)) {
                return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Invalid image URL"))
            }

            val sourceHeaders = sourceId?.let { sourceService.getImageHeaders(it) } ?: emptyMap()

            try {
                val encodedUrl = encodeUrl(imageUrl)
                val domain = try { URI(encodedUrl).host } catch (_: Exception) { null }

                // Build request with source headers + CF cookies
                val cfCookies = domain?.let { cfBypass.getCookiesForDomain(it) }

                fun buildRequest(cookies: CloudflareBypassService.CookieSet?): Request {
                    val rb = Request.Builder().url(encodedUrl)
                    sourceHeaders.forEach { (k, v) ->
                        rb.header(k.replace(Regex("[\r\n]"), ""), v.replace(Regex("[\r\n]"), ""))
                    }
                    if (cookies != null) {
                        rb.header("Cookie", cookies.cookies)
                        rb.header("User-Agent", cookies.userAgent)
                    }
                    try { rb.header("Referer", "${URI(encodedUrl).scheme}://${URI(encodedUrl).host}/") } catch (_: Exception) {}
                    return rb.build()
                }

                // Attempt 1: direct OkHttp with CF cookies
                var response = okClient.newCall(buildRequest(cfCookies)).execute()

                // If 403 — get CF cookies and try curl fallback (different TLS fingerprint)
                if (response.code == 403 && domain != null) {
                    response.close()

                    // Ensure we have CF cookies
                    var cookies = cfCookies
                    if (cookies == null) {
                        cfBypass.proxyRequest("https://$domain/")
                        cookies = cfBypass.getCookiesForDomain(domain)
                    }

                    if (cookies != null) {
                        // OkHttp retry
                        response = okClient.newCall(buildRequest(cookies)).execute()

                        // If still 403 — TLS fingerprint mismatch. Use curl as fallback.
                        if (response.code == 403 || response.header("Content-Type")?.startsWith("text/html") == true) {
                            response.close()
                            val curlBytes = fetchWithCurl(encodedUrl, cookies)
                            if (curlBytes != null && curlBytes.size <= MAX_IMAGE_SIZE) {
                                val ct = guessContentType(encodedUrl)
                                call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                                call.respondBytes(bytes = curlBytes, contentType = ContentType.parse(ct))
                                return@get
                            }
                        }
                    }
                }

                if (!response.isSuccessful) {
                    response.close()
                    return@get call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Failed to fetch image: HTTP ${response.code}"))
                }

                val contentType = response.header("Content-Type") ?: "image/jpeg"
                if (contentType.startsWith("text/html")) {
                    response.close()
                    return@get call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Cloudflare blocked image"))
                }

                val bytes = response.body.bytes()
                if (bytes.size > MAX_IMAGE_SIZE) {
                    return@get call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Image too large"))
                }

                call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                call.respondBytes(bytes = bytes, contentType = ContentType.parse(contentType))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Failed to fetch image"))
            }
        }
    }
}

/**
 * Fallback: fetch image using curl (different TLS fingerprint than OkHttp).
 * Needed for strict Cloudflare domains that check JA3 TLS fingerprint.
 */
private fun fetchWithCurl(url: String, cookies: CloudflareBypassService.CookieSet): ByteArray? {
    return try {
        val process = ProcessBuilder(
            "curl", "-s", "-L", "--max-time", "30",
            "-H", "Cookie: ${cookies.cookies}",
            "-H", "User-Agent: ${cookies.userAgent}",
            "-H", "Referer: ${URI(url).let { "${it.scheme}://${it.host}/" }}",
            "-o", "-",
            url,
        ).redirectErrorStream(true).start()

        val bytes = process.inputStream.readBytes()
        val exitCode = process.waitFor()

        if (exitCode == 0 && bytes.isNotEmpty() && !String(bytes.take(15).toByteArray()).contains("<html")) {
            bytes
        } else null
    } catch (_: Exception) { null }
}

private fun guessContentType(url: String): String {
    val l = url.lowercase().substringBefore("?")
    return when {
        l.endsWith(".webp") -> "image/webp"
        l.endsWith(".png") -> "image/png"
        l.endsWith(".gif") -> "image/gif"
        l.endsWith(".svg") -> "image/svg+xml"
        else -> "image/jpeg"
    }
}

private fun encodeUrl(url: String): String {
    return try {
        val uri = URI(url.replace(" ", "%20"))
        uri.toASCIIString()
    } catch (_: Exception) {
        url.replace(" ", "%20")
            .replace("[", "%5B").replace("]", "%5D")
            .replace("{", "%7B").replace("}", "%7D")
            .replace("|", "%7C").replace("^", "%5E")
    }
}

private fun isValidImageUrl(url: String): Boolean {
    return try {
        val safeUrl = url.replace(" ", "%20")
        val uri = URI(safeUrl)
        if (uri.scheme !in listOf("http", "https")) return false
        val host = uri.host ?: return false
        val blockedHosts = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "metadata.google.internal", "169.254.169.254")
        if (host.lowercase() in blockedHosts) return false
        val address = try { InetAddress.getByName(host) } catch (_: Exception) { return true }
        !(address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isAnyLocalAddress || address.isMulticastAddress)
    } catch (_: Exception) { false }
}
