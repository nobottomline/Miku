package miku.extension.runner

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLClassLoader

class ExtensionLoader(
    private val extensionsDir: File,
    private val apkExtractor: ApkExtractor,
    private val parentClassLoader: ClassLoader = ExtensionLoader::class.java.classLoader!!,
) {
    private val logger = LoggerFactory.getLogger(ExtensionLoader::class.java)

    init {
        extensionsDir.mkdirs()
    }

    fun loadAllExtensions(): List<ExtensionInfo> {
        val jarFiles = extensionsDir.listFiles { f -> f.extension == "jar" } ?: emptyArray()
        val apkFiles = extensionsDir.listFiles { f -> f.extension == "apk" } ?: emptyArray()

        logger.info("Found ${jarFiles.size} JARs and ${apkFiles.size} APKs in ${extensionsDir.absolutePath}")

        val fromJars = jarFiles.mapNotNull { loadJar(it) }
        val fromApks = apkFiles.mapNotNull { loadApk(it) }

        return fromJars + fromApks
    }

    fun loadApk(apkFile: File): ExtensionInfo? {
        return try {
            logger.info("Processing APK: ${apkFile.name}")
            val extracted = apkExtractor.extractApk(apkFile)
            loadFromExtracted(extracted)
        } catch (e: Exception) {
            logger.error("Failed to load APK: ${apkFile.name}", e)
            null
        }
    }

    fun loadJar(jarFile: File): ExtensionInfo? {
        return try {
            logger.info("Loading JAR: ${jarFile.name}")

            val classLoader = URLClassLoader(
                arrayOf(jarFile.toURI().toURL()),
                parentClassLoader,
            )

            // Try to read extension.json metadata
            val metadataStream = classLoader.getResourceAsStream("extension.json")
            if (metadataStream != null) {
                val metadata = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }.decodeFromString<ExtensionMetadata>(metadataStream.bufferedReader().readText())

                val sources = loadSourceClasses(metadata.sourceClasses, classLoader)

                return ExtensionInfo(
                    packageName = metadata.packageName,
                    name = metadata.name,
                    versionCode = metadata.versionCode,
                    versionName = metadata.versionName,
                    lang = metadata.lang,
                    isNsfw = metadata.isNsfw,
                    sources = sources,
                    classLoader = classLoader,
                )
            }

            // Fallback: scan for Source classes
            val sources = scanForSources(jarFile, classLoader)
            if (sources.isEmpty()) {
                logger.warn("No sources found in JAR: ${jarFile.name}")
                return null
            }

            ExtensionInfo(
                packageName = "jar.${jarFile.nameWithoutExtension}",
                name = jarFile.nameWithoutExtension,
                versionCode = 1,
                versionName = "1.0",
                lang = "en",
                isNsfw = false,
                sources = sources,
                classLoader = classLoader,
            )
        } catch (e: Exception) {
            logger.error("Failed to load JAR: ${jarFile.name}", e)
            null
        }
    }

    private fun loadFromExtracted(extracted: ApkExtractor.ExtractedExtension): ExtensionInfo? {
        val classLoader = URLClassLoader(
            arrayOf(extracted.jarFile.toURI().toURL()),
            parentClassLoader,
        )

        val sources = if (extracted.sourceClassName != null) {
            // sourceClassName can be comma-separated or dot-prefixed
            val classNames = extracted.sourceClassName.split(";", ",")
                .map { it.trim() }
                .map { name ->
                    if (name.startsWith(".")) {
                        "${extracted.packageName}$name"
                    } else {
                        name
                    }
                }
            loadSourceClasses(classNames, classLoader)
        } else {
            scanForSources(extracted.jarFile, classLoader)
        }

        if (sources.isEmpty()) {
            logger.warn("No sources found in extension: ${extracted.name}")
            return null
        }

        logger.info("Loaded extension '${extracted.name}' with ${sources.size} source(s)")

        return ExtensionInfo(
            packageName = extracted.packageName,
            name = extracted.name,
            versionCode = extracted.versionCode.toInt(),
            versionName = extracted.versionName,
            lang = extracted.lang,
            isNsfw = extracted.isNsfw,
            sources = sources,
            classLoader = classLoader,
        )
    }

    private fun loadSourceClasses(classNames: List<String>, classLoader: ClassLoader): List<Source> {
        return classNames.flatMap { className ->
            try {
                val clazz = Class.forName(className, true, classLoader)
                val instance = clazz.getDeclaredConstructor().newInstance()

                when (instance) {
                    is SourceFactory -> instance.createSources()
                    is Source -> listOf(instance)
                    else -> {
                        logger.warn("Class $className is not a Source or SourceFactory")
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to instantiate source: $className", e)
                emptyList()
            }
        }
    }

    private fun scanForSources(file: File, classLoader: ClassLoader): List<Source> {
        val sources = mutableListOf<Source>()
        try {
            val jar = java.util.jar.JarFile(file)
            jar.entries().asSequence()
                .filter { it.name.endsWith(".class") && !it.name.contains('$') }
                .forEach { entry ->
                    val className = entry.name.removeSuffix(".class").replace('/', '.')
                    try {
                        val clazz = Class.forName(className, false, classLoader)
                        if (Source::class.java.isAssignableFrom(clazz) &&
                            !clazz.isInterface &&
                            !java.lang.reflect.Modifier.isAbstract(clazz.modifiers)
                        ) {
                            val instance = clazz.getDeclaredConstructor().newInstance()
                            when (instance) {
                                is SourceFactory -> sources.addAll(instance.createSources())
                                is Source -> sources.add(instance)
                            }
                        }
                    } catch (_: Exception) {}
                }
            jar.close()
        } catch (e: Exception) {
            logger.error("Failed to scan JAR: ${file.name}", e)
        }
        return sources
    }
}
