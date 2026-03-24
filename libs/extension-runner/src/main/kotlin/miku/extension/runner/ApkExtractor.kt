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

            // Copy resource files from APK into JAR (assets, i18n, etc.)
            copyApkResources(apkFile, outputJar)

            // Patch for Kotlin version compatibility (inject Result compat for Kotlin 1.x extensions)
            KotlinCompatPatcher.patchJar(outputJar)

            logger.info("Successfully converted: ${apkFile.name} -> ${outputJar.name}")
        } catch (e: Exception) {
            logger.error("Failed to convert DEX to JAR: ${apkFile.name}", e)
            throw ExtensionConversionException("Failed to convert ${apkFile.name}: ${e.message}", e)
        }
    }

    /**
     * Copy non-DEX resources from APK into the converted JAR.
     * Extensions may bundle assets (i18n, configs, etc.) that they load via ClassLoader.getResource().
     */
    private fun copyApkResources(apkFile: File, jarFile: File) {
        val skipPrefixes = setOf("classes", "META-INF/", "AndroidManifest", "res/", "resources.arsc", "lib/")
        val skipSuffixes = setOf(".dex", ".SF", ".RSA", ".MF")

        try {
            val tempJar = Files.createTempFile("miku-resources-", ".jar")

            java.util.jar.JarFile(jarFile).use { existingJar ->
                java.util.jar.JarOutputStream(java.io.FileOutputStream(tempJar.toFile())).use { output ->
                    val existingEntries = mutableSetOf<String>()

                    // Copy existing JAR entries
                    existingJar.entries().asSequence().forEach { entry ->
                        existingEntries.add(entry.name)
                        output.putNextEntry(java.util.jar.JarEntry(entry.name))
                        existingJar.getInputStream(entry).copyTo(output)
                        output.closeEntry()
                    }

                    // Copy resources from APK
                    ZipFile(apkFile).use { apk ->
                        apk.entries().asSequence()
                            .filter { entry ->
                                !entry.isDirectory &&
                                    skipPrefixes.none { entry.name.startsWith(it) } &&
                                    skipSuffixes.none { entry.name.endsWith(it) } &&
                                    entry.name !in existingEntries
                            }
                            .forEach { entry ->
                                try {
                                    output.putNextEntry(java.util.jar.JarEntry(entry.name))
                                    apk.getInputStream(entry).copyTo(output)
                                    output.closeEntry()
                                    logger.debug("Copied resource: ${entry.name}")
                                } catch (_: Exception) {}
                            }
                    }
                }
            }

            Files.move(tempJar, jarFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            logger.warn("Failed to copy APK resources: ${e.message}")
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
                var libVersion = 0.0

                // Parse meta-data from manifest XML
                val metaDataRegex = """<meta-data[^>]*android:name="([^"]*)"[^>]*android:value="([^"]*)"[^>]*/>""".toRegex()
                metaDataRegex.findAll(manifestXml).forEach { match ->
                    val name = match.groupValues[1]
                    val value = match.groupValues[2]
                    when (name) {
                        "tachiyomi.extension.class" -> sourceClass = value
                        "tachiyomi.extension.nsfw" -> isNsfw = value == "1"
                        "tachiyomi.extension.lib_version" -> libVersion = value.toDoubleOrNull() ?: 0.0
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
                        "tachiyomi.extension.lib_version" -> if (libVersion == 0.0) libVersion = value.toDoubleOrNull() ?: 0.0
                    }
                }

                // Validate lib version (must be between 1.3 and 1.5)
                if (libVersion > 0 && (libVersion < 1.3 || libVersion > 1.5)) {
                    logger.warn("Extension ${apkFile.name} has incompatible lib version $libVersion (expected 1.3-1.5)")
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
