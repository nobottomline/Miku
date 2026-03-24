package miku.extension.runner

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

class ExtensionRepository(
    private val repoUrl: String = DEFAULT_REPO_URL,
    private val apkCacheDir: File,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val logger = LoggerFactory.getLogger(ExtensionRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var cachedIndex: List<ExtensionRepoEntry>? = null
    private var lastFetchTime: Long = 0
    private val cacheTtl = 5 * 60 * 1000L // 5 minutes

    init {
        apkCacheDir.mkdirs()
    }

    fun fetchIndex(forceRefresh: Boolean = false): List<ExtensionRepoEntry> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedIndex != null && (now - lastFetchTime) < cacheTtl) {
            return cachedIndex!!
        }

        logger.info("Fetching extension index from $repoUrl")

        val request = Request.Builder()
            .url("$repoUrl/index.min.json")
            .header("User-Agent", "Miku-Server/1.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to fetch extension index: HTTP ${response.code}")
        }

        val body = response.body.string()
        val entries = json.decodeFromString<List<ExtensionRepoEntry>>(body)

        cachedIndex = entries
        lastFetchTime = now

        logger.info("Fetched ${entries.size} extensions from repository")
        return entries
    }

    fun downloadApk(entry: ExtensionRepoEntry): File {
        val apkFile = File(apkCacheDir, entry.apk)

        if (apkFile.exists()) {
            logger.debug("APK already cached: ${entry.apk}")
            return apkFile
        }

        logger.info("Downloading APK: ${entry.apk}")

        val url = "$repoUrl/apk/${entry.apk}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Miku-Server/1.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to download APK ${entry.apk}: HTTP ${response.code}")
        }

        apkFile.parentFile.mkdirs()
        apkFile.outputStream().use { out ->
            response.body.byteStream().copyTo(out)
        }

        logger.info("Downloaded: ${entry.apk} (${apkFile.length() / 1024} KB)")
        return apkFile
    }

    fun findExtension(packageName: String): ExtensionRepoEntry? {
        return fetchIndex().find { it.pkg == packageName }
    }

    fun searchExtensions(query: String): List<ExtensionRepoEntry> {
        return fetchIndex().filter { entry ->
            entry.name.contains(query, ignoreCase = true) ||
                entry.pkg.contains(query, ignoreCase = true) ||
                entry.sources.any { it.name.contains(query, ignoreCase = true) }
        }
    }

    fun getExtensionsByLang(lang: String): List<ExtensionRepoEntry> {
        return fetchIndex().filter { it.lang == lang }
    }

    fun getAvailableLanguages(): Set<String> {
        return fetchIndex().map { it.lang }.toSet()
    }

    fun deleteApk(apkFileName: String) {
        val apkFile = File(apkCacheDir, apkFileName)
        if (apkFile.exists()) {
            apkFile.delete()
            logger.info("Deleted cached APK: $apkFileName")
        }
    }

    companion object {
        const val DEFAULT_REPO_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
    }
}
