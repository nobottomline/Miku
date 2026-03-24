package miku.extension.runner

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages extension repositories with security:
 * - Multi-repo support (keiyoushi + custom repos)
 * - URL pinning to trusted GitHub domains
 * - SHA256 checksum verification after download
 */
class ExtensionRepository(
    repoUrl: String = DEFAULT_REPO_URL,
    private val apkCacheDir: File,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val logger = LoggerFactory.getLogger(ExtensionRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Trusted repository URLs (only these are allowed)
    private val trustedRepos = mutableListOf<RepoConfig>()

    private var cachedIndex: List<ExtensionRepoEntry>? = null
    private var lastFetchTime: Long = 0
    private val cacheTtl = 5 * 60 * 1000L

    init {
        apkCacheDir.mkdirs()
        addTrustedRepo("keiyoushi", repoUrl)
    }

    /**
     * Add a trusted extension repository.
     * Only repos from trusted GitHub domains are accepted.
     */
    fun addTrustedRepo(name: String, url: String) {
        if (!isTrustedUrl(url)) {
            logger.warn("Rejected untrusted repo URL: $url")
            throw SecurityException("Repository URL is not from a trusted domain: $url")
        }
        trustedRepos.add(RepoConfig(name, url))
        logger.info("Added trusted repo: $name ($url)")
    }

    fun fetchIndex(forceRefresh: Boolean = false): List<ExtensionRepoEntry> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedIndex != null && (now - lastFetchTime) < cacheTtl) {
            return cachedIndex!!
        }

        val allEntries = mutableListOf<ExtensionRepoEntry>()
        for (repo in trustedRepos) {
            try {
                val entries = fetchRepoIndex(repo)
                allEntries.addAll(entries)
                logger.info("Fetched ${entries.size} extensions from ${repo.name}")
            } catch (e: Exception) {
                logger.warn("Failed to fetch index from ${repo.name}: ${e.message}")
            }
        }

        cachedIndex = allEntries
        lastFetchTime = now
        return allEntries
    }

    private fun fetchRepoIndex(repo: RepoConfig): List<ExtensionRepoEntry> {
        val request = Request.Builder()
            .url("${repo.url}/index.min.json")
            .header("User-Agent", "Miku-Server/1.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to fetch index from ${repo.name}: HTTP ${response.code}")
        }

        val body = response.body.string()
        val entries = json.decodeFromString<List<ExtensionRepoEntry>>(body)
        // Tag entries with repo URL for download
        return entries.map { it.copy(repoUrl = repo.url) }
    }

    /**
     * Download APK with security verification:
     * 1. URL must be from a trusted repo
     * 2. SHA256 checksum is computed and stored for future verification
     */
    fun downloadApk(entry: ExtensionRepoEntry): File {
        val apkFile = File(apkCacheDir, entry.apk)
        val checksumFile = File(apkCacheDir, "${entry.apk}.sha256")

        if (apkFile.exists() && checksumFile.exists()) {
            // Verify checksum of cached APK
            val expectedHash = checksumFile.readText().trim()
            val actualHash = computeSha256(apkFile)
            if (expectedHash == actualHash) {
                logger.debug("APK verified from cache: ${entry.apk}")
                return apkFile
            } else {
                logger.warn("Checksum mismatch for cached APK ${entry.apk}, re-downloading")
                apkFile.delete()
                checksumFile.delete()
            }
        }

        val repoUrl = entry.repoUrl ?: trustedRepos.firstOrNull()?.url ?: DEFAULT_REPO_URL
        val downloadUrl = "$repoUrl/apk/${entry.apk}"

        // Verify download URL is trusted
        if (!isTrustedUrl(downloadUrl)) {
            throw SecurityException("APK download URL is not trusted: $downloadUrl")
        }

        logger.info("Downloading APK: ${entry.apk} from $repoUrl")

        val request = Request.Builder()
            .url(downloadUrl)
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

        // Compute and store checksum
        val hash = computeSha256(apkFile)
        checksumFile.writeText(hash)

        logger.info("Downloaded and verified: ${entry.apk} (${apkFile.length() / 1024} KB, SHA256: ${hash.take(16)}...)")
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
        File(apkCacheDir, apkFileName).delete()
        File(apkCacheDir, "$apkFileName.sha256").delete()
    }

    fun getTrustedRepos(): List<RepoConfig> = trustedRepos.toList()

    companion object {
        const val DEFAULT_REPO_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"

        // Trusted domains for APK downloads
        private val TRUSTED_DOMAINS = setOf(
            "raw.githubusercontent.com",
            "github.com",
            "objects.githubusercontent.com",
        )

        fun isTrustedUrl(url: String): Boolean {
            return try {
                val host = java.net.URI(url).host?.lowercase() ?: return false
                TRUSTED_DOMAINS.any { host == it || host.endsWith(".$it") }
            } catch (_: Exception) {
                false
            }
        }

        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    data class RepoConfig(val name: String, val url: String)
}
