package miku.extension.runner

import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import net.dongliu.apk.parser.ApkFile
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class ApkExtractor(
    private val cacheDir: File,
) {
    private val logger = LoggerFactory.getLogger(ApkExtractor::class.java)

    init {
        cacheDir.mkdirs()
        File(cacheDir, "jar").mkdirs()
    }

    data class ExtractedExtension(
        val jarFile: File,
        val packageName: String,
        val name: String,
        val versionCode: Long,
        val versionName: String,
        val sourceClassName: String?,
        val isNsfw: Boolean,
        val lang: String,
    )

    fun extractApk(apkFile: File): ExtractedExtension {
        logger.info("Extracting APK: ${apkFile.name}")

        val jarFile = File(cacheDir, "jar/${apkFile.nameWithoutExtension}.jar")

        // If JAR already exists and is newer than APK, skip conversion
        if (jarFile.exists() && jarFile.lastModified() >= apkFile.lastModified()) {
            logger.info("Using cached JAR: ${jarFile.name}")
            return parseApkMetadata(apkFile, jarFile)
        }

        // Convert DEX to JAR
        convertDexToJar(apkFile, jarFile)

        return parseApkMetadata(apkFile, jarFile)
    }

    private fun convertDexToJar(apkFile: File, outputJar: File) {
        logger.info("Converting DEX to JAR: ${apkFile.name}")

        try {
            val tempJar = Files.createTempFile("miku-dex2jar-", ".jar")

            // Read all DEX bytes from APK (which is a ZIP)
            val allDexBytes = ByteArrayOutputStream()
            ZipFile(apkFile).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .filter { it.name.endsWith(".dex") }
                    .sortedBy { it.name } // classes.dex, classes2.dex, etc.
                    .toList()

                if (dexEntries.isEmpty()) {
                    throw IllegalStateException("No DEX files found in APK: ${apkFile.name}")
                }

                // For single DEX, use it directly; for multi-dex use MultiDexFileReader
                val reader = if (dexEntries.size == 1) {
                    val bytes = zip.getInputStream(dexEntries[0]).readBytes()
                    MultiDexFileReader.open(bytes)
                } else {
                    // Combine all DEX files
                    val readers = dexEntries.map { entry ->
                        val bytes = zip.getInputStream(entry).readBytes()
                        com.googlecode.d2j.reader.DexFileReader(bytes)
                    }
                    MultiDexFileReader(readers)
                }

                Dex2jar.from(reader)
                    .skipDebug(true)
                    .optimizeSynchronized(false)
                    .printIR(false)
                    .noCode(false)
                    .skipExceptions(false)
                    .to(tempJar)
            }

            // Move to final location
            outputJar.parentFile.mkdirs()
            Files.move(tempJar, outputJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)

            logger.info("Successfully converted: ${apkFile.name} -> ${outputJar.name}")
        } catch (e: Exception) {
            logger.error("Failed to convert DEX to JAR: ${apkFile.name}", e)
            throw ExtensionConversionException("Failed to convert ${apkFile.name}: ${e.message}", e)
        }
    }

    private fun parseApkMetadata(apkFile: File, jarFile: File): ExtractedExtension {
        return try {
            ApkFile(apkFile).use { apk ->
                val meta = apk.apkMeta

                // Parse manifest XML to extract tachiyomi extension metadata
                val manifestXml = apk.manifestXml
                var sourceClass: String? = null
                var isNsfw = false

                // Parse meta-data from manifest XML
                val metaDataRegex = """<meta-data[^>]*android:name="([^"]*)"[^>]*android:value="([^"]*)"[^>]*/>""".toRegex()
                metaDataRegex.findAll(manifestXml).forEach { match ->
                    val name = match.groupValues[1]
                    val value = match.groupValues[2]
                    when (name) {
                        "tachiyomi.extension.class" -> sourceClass = value
                        "tachiyomi.extension.nsfw" -> isNsfw = value == "1"
                    }
                }

                // Also try reversed attribute order
                val metaDataRegex2 = """<meta-data[^>]*android:value="([^"]*)"[^>]*android:name="([^"]*)"[^>]*/>""".toRegex()
                metaDataRegex2.findAll(manifestXml).forEach { match ->
                    val value = match.groupValues[1]
                    val name = match.groupValues[2]
                    when (name) {
                        "tachiyomi.extension.class" -> sourceClass = sourceClass ?: value
                        "tachiyomi.extension.nsfw" -> isNsfw = isNsfw || value == "1"
                    }
                }

                // Infer lang from package name
                val lang = meta.packageName.split(".").let { parts ->
                    if (parts.size >= 5) parts[4] else "all"
                }

                ExtractedExtension(
                    jarFile = jarFile,
                    packageName = meta.packageName,
                    name = meta.label ?: meta.name ?: apkFile.nameWithoutExtension,
                    versionCode = meta.versionCode ?: 1,
                    versionName = meta.versionName ?: "1.0",
                    sourceClassName = sourceClass,
                    isNsfw = isNsfw,
                    lang = lang,
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse APK metadata for ${apkFile.name}, using fallback", e)

            ExtractedExtension(
                jarFile = jarFile,
                packageName = apkFile.nameWithoutExtension.replace("-", "."),
                name = apkFile.nameWithoutExtension,
                versionCode = 1,
                versionName = "1.0",
                sourceClassName = null,
                isNsfw = false,
                lang = "en",
            )
        }
    }
}

class ExtensionConversionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
