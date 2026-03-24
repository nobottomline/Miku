package miku.extension.runner

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.NetworkHelperHolder
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import java.io.File

class ExtensionManager(
    private val extensionsDir: File = File(System.getProperty("user.dir"), "extensions"),
    private val cacheDir: File = File(System.getProperty("user.dir"), "data/cache"),
    private val networkHelper: NetworkHelper = NetworkHelper(),
    repoUrl: String = ExtensionRepository.DEFAULT_REPO_URL,
) {
    private val logger = LoggerFactory.getLogger(ExtensionManager::class.java)
    private val apkExtractor = ApkExtractor(cacheDir)
    private val loader = ExtensionLoader(extensionsDir, apkExtractor)
    val registry = ExtensionRegistry()
    val repository = ExtensionRepository(
        repoUrl = repoUrl,
        apkCacheDir = File(cacheDir, "apk"),
    )

    fun initialize() {
        logger.info("Initializing extension manager...")

        // Register dependencies for extensions via Injekt
        NetworkHelperHolder.networkHelper = networkHelper
        Injekt.addSingleton(fullType<NetworkHelper>(), networkHelper)

        // Register default Json instance for keiyoushi utils
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
        Injekt.addSingleton(fullType<Json>(), json)

        // Register Application for getPreferences
        val app = android.app.Application()
        Injekt.addSingleton(fullType<android.app.Application>(), app)

        // Load locally installed extensions (JARs and APKs)
        val extensions = loader.loadAllExtensions()
        extensions.forEach { registry.register(it) }

        logger.info(
            "Extension manager initialized: {} extensions, {} sources",
            registry.getExtensionCount(),
            registry.getSourceCount(),
        )
    }

    /**
     * Install extension by package name from keiyoushi repository.
     * Downloads APK, converts DEX→JAR, loads sources.
     */
    fun installFromRepo(packageName: String): ExtensionInfo {
        val entry = repository.findExtension(packageName)
            ?: throw IllegalArgumentException("Extension not found in repository: $packageName")

        return installFromRepo(entry)
    }

    fun installFromRepo(entry: ExtensionRepoEntry): ExtensionInfo {
        logger.info("Installing extension: ${entry.name} (${entry.pkg})")

        // Unregister old version if exists
        registry.getExtension(entry.pkg)?.let {
            registry.unregister(entry.pkg)
        }

        // Download APK
        val apkFile = repository.downloadApk(entry)

        // Copy APK to extensions directory for persistence
        val targetApk = File(extensionsDir, entry.apk)
        if (!targetApk.exists() || targetApk.length() != apkFile.length()) {
            apkFile.copyTo(targetApk, overwrite = true)
        }

        // Load extension from APK
        val extension = loader.loadApk(targetApk)
            ?: throw RuntimeException("Failed to load extension: ${entry.name}")

        registry.register(extension)

        logger.info("Installed: ${extension.name} with ${extension.sources.size} source(s)")
        return extension
    }

    /**
     * Install from a local APK file (e.g. uploaded by user)
     */
    fun installFromFile(apkFile: File): ExtensionInfo {
        val targetFile = File(extensionsDir, apkFile.name)
        if (targetFile.absolutePath != apkFile.absolutePath) {
            apkFile.copyTo(targetFile, overwrite = true)
        }

        val extension = loader.loadApk(targetFile)
            ?: throw RuntimeException("Failed to load extension from: ${apkFile.name}")

        // Unregister old version
        registry.getExtension(extension.packageName)?.let {
            registry.unregister(extension.packageName)
        }

        registry.register(extension)
        return extension
    }

    fun uninstallExtension(packageName: String): Boolean {
        val extension = registry.getExtension(packageName) ?: return false
        registry.unregister(packageName)

        // Delete APK/JAR files
        extensionsDir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension.contains(packageName.substringAfterLast("."))) {
                file.delete()
                logger.info("Deleted: ${file.name}")
            }
        }

        logger.info("Uninstalled: ${extension.name}")
        return true
    }

    fun updateExtension(packageName: String): ExtensionInfo? {
        val repoEntry = repository.findExtension(packageName) ?: return null
        val installed = registry.getExtension(packageName)

        if (installed != null && installed.versionCode >= repoEntry.code) {
            logger.info("Extension ${packageName} is already up to date (v${installed.versionCode} >= v${repoEntry.code})")
            return null
        }

        return installFromRepo(repoEntry)
    }

    /**
     * Check for updates for all installed extensions
     */
    fun checkUpdates(): List<ExtensionRepoEntry> {
        val index = repository.fetchIndex(forceRefresh = true)
        val installed = registry.getAllExtensions()

        return installed.mapNotNull { ext ->
            val repoEntry = index.find { it.pkg == ext.packageName }
            if (repoEntry != null && repoEntry.code > ext.versionCode) {
                repoEntry
            } else null
        }
    }

    fun getAvailableExtensions(): List<ExtensionRepoEntry> {
        return repository.fetchIndex()
    }

    fun reloadExtensions() {
        logger.info("Reloading all extensions...")
        registry.getAllExtensions().forEach { registry.unregister(it.packageName) }
        val extensions = loader.loadAllExtensions()
        extensions.forEach { registry.register(it) }
        logger.info("Reloaded ${extensions.size} extensions")
    }
}
