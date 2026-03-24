package miku.server.service

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloadService(
    private val sourceService: MikuSourceService,
    private val storageService: MinioStorageService,
    private val cfBypass: CloudflareBypassService,
) {
    private val logger = LoggerFactory.getLogger(DownloadService::class.java)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val activeDownloads = ConcurrentHashMap<String, DownloadProgress>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Serializable
    class DownloadProgress(
        val mangaTitle: String = "",
        val chapterName: String = "",
        val sourceId: Long = 0,
        val chapterUrl: String = "",
        var totalPages: Int = 0,
        var downloadedPages: Int = 0,
        var status: String = "pending",
        var error: String? = null,
    )

    fun startChapterDownload(sourceId: Long, mangaTitle: String, chapterUrl: String, chapterName: String): String {
        val downloadId = "dl_${sourceId}_${chapterUrl.hashCode()}"

        if (activeDownloads.containsKey(downloadId)) return downloadId

        val progress = DownloadProgress(
            mangaTitle = mangaTitle,
            chapterName = chapterName,
            sourceId = sourceId,
            chapterUrl = chapterUrl,
            status = "pending",
        )
        activeDownloads[downloadId] = progress

        scope.launch {
            try {
                progress.status = "downloading"
                EventBus.tryEmit(ServerEvent.downloadStarted(downloadId, mangaTitle, chapterName))

                val pages = sourceService.getPageList(sourceId, chapterUrl)
                progress.totalPages = pages.size
                val headers = sourceService.getImageHeaders(sourceId)

                for (page in pages) {
                    val imageUrl = page.imageUrl ?: page.url
                    if (imageUrl.isBlank()) continue
                    try {
                        val imageBytes = downloadImage(imageUrl, headers)
                        val ext = guessExtension(imageUrl)
                        val objectName = "downloads/$sourceId/${sanitize(mangaTitle)}/${sanitize(chapterName)}/page_${"%04d".format(page.index)}.$ext"
                        storageService.uploadBytes(objectName, imageBytes, "image/$ext")
                        progress.downloadedPages++

                        // Push progress via WebSocket
                        EventBus.tryEmit(ServerEvent.downloadProgress(
                            downloadId, progress.downloadedPages, progress.totalPages, mangaTitle, chapterName
                        ))
                    } catch (e: Exception) {
                        logger.warn("Failed to download page ${page.index}: ${e.message}")
                    }
                }

                progress.status = "completed"
                EventBus.tryEmit(ServerEvent.downloadCompleted(downloadId, mangaTitle, chapterName, progress.downloadedPages))
                logger.info("Download completed: $mangaTitle - $chapterName (${progress.downloadedPages}/${progress.totalPages})")
            } catch (e: Exception) {
                progress.status = "failed"
                progress.error = e.message
                EventBus.tryEmit(ServerEvent.downloadFailed(downloadId, mangaTitle, chapterName, e.message ?: "Unknown error"))
                logger.error("Download failed: $mangaTitle - $chapterName", e)
            }
        }
        return downloadId
    }

    fun getProgress(downloadId: String): DownloadProgress? = activeDownloads[downloadId]
    fun getAllDownloads(): Map<String, DownloadProgress> = activeDownloads.toMap()

    fun generateZip(sourceId: Long, mangaTitle: String, chapterName: String): ByteArray? {
        val prefix = "downloads/$sourceId/${sanitize(mangaTitle)}/${sanitize(chapterName)}/"
        val baos = ByteArrayOutputStream()
        var count = 0
        ZipOutputStream(baos).use { zip ->
            for (i in 0..999) {
                val data = findPage(prefix, i) ?: if (i > 0) break else continue
                zip.putNextEntry(ZipEntry("page_${"%04d".format(i)}.${data.second}"))
                data.first.use { it.copyTo(zip) }
                zip.closeEntry()
                count++
            }
        }
        return if (count > 0) baos.toByteArray() else null
    }

    fun generatePdf(sourceId: Long, mangaTitle: String, chapterName: String): ByteArray? {
        val prefix = "downloads/$sourceId/${sanitize(mangaTitle)}/${sanitize(chapterName)}/"
        val pages = mutableListOf<ByteArray>()

        for (i in 0..999) {
            val data = findPage(prefix, i)
            if (data != null) pages.add(data.first.use { it.readBytes() })
            else if (i > 0) break
        }
        if (pages.isEmpty()) return null

        val baos = ByteArrayOutputStream()
        try {
            val writer = com.itextpdf.kernel.pdf.PdfWriter(baos)
            val pdf = com.itextpdf.kernel.pdf.PdfDocument(writer)
            val document = com.itextpdf.layout.Document(pdf)

            for (pageBytes in pages) {
                // iText doesn't support WebP — convert to PNG if needed
                val pngBytes = convertToPngIfNeeded(pageBytes)
                val imageData = com.itextpdf.io.image.ImageDataFactory.create(pngBytes)
                val image = com.itextpdf.layout.element.Image(imageData)
                val pageSize = com.itextpdf.kernel.geom.PageSize(imageData.width, imageData.height)
                pdf.addNewPage(pageSize)
                image.setFixedPosition(pdf.numberOfPages, 0f, 0f)
                image.scaleToFit(pageSize.width, pageSize.height)
                document.add(image)
            }
            document.close()
        } catch (e: Exception) {
            logger.error("PDF generation failed: ${e.message}", e)
            return null
        }
        return baos.toByteArray()
    }

    private fun findPage(prefix: String, index: Int): Pair<java.io.InputStream, String>? {
        for (ext in listOf("webp", "jpg", "png", "gif")) {
            val obj = storageService.getObject("${prefix}page_${"%04d".format(index)}.$ext")
            if (obj != null) return obj to ext
        }
        return null
    }

    private fun downloadImage(url: String, headers: Map<String, String>): ByteArray {
        val encoded = url.replace(" ", "%20")
        val domain = try { java.net.URI(encoded).host } catch (_: Exception) { null }

        // Try OkHttp first
        val rb = Request.Builder().url(encoded)
        headers.forEach { (k, v) -> rb.header(k, v) }

        // Inject CF cookies if available
        val cfCookies = domain?.let { cfBypass.getCookiesForDomain(it) }
        if (cfCookies != null) {
            rb.header("Cookie", cfCookies.cookies)
            rb.header("User-Agent", cfCookies.userAgent)
        }
        try { rb.header("Referer", "${java.net.URI(encoded).scheme}://${java.net.URI(encoded).host}/") } catch (_: Exception) {}

        val response = httpClient.newCall(rb.build()).execute()

        if (response.isSuccessful && response.header("Content-Type")?.startsWith("text/html") != true) {
            val bytes = response.body.bytes()
            if (bytes.size > 20 * 1024 * 1024) throw RuntimeException("Image too large")
            return bytes
        }
        response.close()

        // OkHttp failed (403 or HTML) — fallback to curl (different TLS fingerprint)
        if (cfCookies != null || domain != null) {
            val cookies = cfCookies ?: run {
                cfBypass.proxyRequest("https://$domain/")
                cfBypass.getCookiesForDomain(domain!!)
            }
            if (cookies != null) {
                val bytes = fetchWithCurl(encoded, cookies)
                if (bytes != null) return bytes
            }
        }

        throw RuntimeException("HTTP ${response.code} (CF bypass failed)")
    }

    private fun fetchWithCurl(url: String, cookies: CloudflareBypassService.CookieSet): ByteArray? {
        return try {
            val referer = try { "${java.net.URI(url).scheme}://${java.net.URI(url).host}/" } catch (_: Exception) { "" }
            val process = ProcessBuilder(
                "curl", "-s", "-L", "--max-time", "30",
                "-H", "Cookie: ${cookies.cookies}",
                "-H", "User-Agent: ${cookies.userAgent}",
                "-H", "Referer: $referer",
                "-o", "-", url,
            ).redirectErrorStream(true).start()
            val bytes = process.inputStream.readBytes()
            val exit = process.waitFor()
            if (exit == 0 && bytes.isNotEmpty() && !String(bytes.take(15).toByteArray()).contains("<html")) bytes else null
        } catch (_: Exception) { null }
    }

    private fun guessExtension(url: String): String {
        val l = url.lowercase().substringBefore("?")
        return when { l.endsWith(".webp") -> "webp"; l.endsWith(".png") -> "png"; l.endsWith(".gif") -> "gif"; else -> "jpg" }
    }

    /**
     * Convert image to JPEG for PDF embedding.
     * - WebP/unsupported → JPEG 85% quality (high quality, ~5-8x smaller than PNG)
     * - JPEG/PNG that iText already supports → use as-is
     *
     * Uses sips (macOS) or ImageMagick convert (Linux) for WebP conversion.
     */
    private fun convertToPngIfNeeded(imageBytes: ByteArray): ByteArray {
        // Check if iText can handle it directly (JPEG, PNG, GIF, TIFF, BMP)
        return try {
            com.itextpdf.io.image.ImageDataFactory.create(imageBytes)
            imageBytes
        } catch (_: Exception) {
            // iText can't parse — convert to high-quality JPEG
            convertToJpeg(imageBytes)
        }
    }

    private fun convertToJpeg(imageBytes: ByteArray, quality: Int = 85): ByteArray {
        try {
            val tempIn = java.io.File.createTempFile("miku_convert_", ".webp")
            val tempOut = java.io.File.createTempFile("miku_convert_", ".jpg")
            tempIn.writeBytes(imageBytes)

            val isMac = System.getProperty("os.name").lowercase().contains("mac")
            val cmd = if (isMac) {
                // sips: macOS built-in, fast, high quality
                listOf("sips", "-s", "format", "jpeg", "-s", "formatOptions", quality.toString(), tempIn.absolutePath, "--out", tempOut.absolutePath)
            } else {
                // ImageMagick: cross-platform
                listOf("convert", tempIn.absolutePath, "-quality", quality.toString(), tempOut.absolutePath)
            }

            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            process.inputStream.readBytes() // drain output
            process.waitFor()

            val result = if (tempOut.exists() && tempOut.length() > 0) {
                tempOut.readBytes()
            } else {
                imageBytes
            }

            tempIn.delete()
            tempOut.delete()
            return result
        } catch (e: Exception) {
            logger.warn("Image conversion failed: ${e.message}")
            return imageBytes
        }
    }

    private fun sanitize(name: String) = name.replace(Regex("[^a-zA-Z0-9\\s\\-_]"), "").trim().replace(Regex("\\s+"), "_").take(100)
}
