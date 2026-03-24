package miku.extension.runner

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class ExtensionRegistry {
    private val logger = LoggerFactory.getLogger(ExtensionRegistry::class.java)

    private val extensions = ConcurrentHashMap<String, ExtensionInfo>()
    private val sourcesById = ConcurrentHashMap<Long, Source>()
    private val sourcesByLang = ConcurrentHashMap<String, MutableList<Source>>()

    fun register(extension: ExtensionInfo) {
        extensions[extension.packageName] = extension

        extension.sources.forEach { source ->
            sourcesById[source.id] = source
            sourcesByLang.getOrPut(source.lang) { mutableListOf() }.add(source)
            logger.info("Registered source: ${source.name} (${source.id}) [${source.lang}]")
        }
    }

    fun unregister(packageName: String) {
        val extension = extensions.remove(packageName) ?: return
        extension.sources.forEach { source ->
            sourcesById.remove(source.id)
            sourcesByLang[source.lang]?.remove(source)
        }
        // Close classloader if possible
        (extension.classLoader as? AutoCloseable)?.close()
        logger.info("Unregistered extension: ${extension.name}")
    }

    fun getSource(sourceId: Long): Source? = sourcesById[sourceId]

    fun getCatalogueSource(sourceId: Long): CatalogueSource? =
        sourcesById[sourceId] as? CatalogueSource

    fun getAllSources(): List<Source> = sourcesById.values.toList()

    fun getSourcesByLang(lang: String): List<Source> =
        sourcesByLang[lang]?.toList() ?: emptyList()

    fun getAllExtensions(): List<ExtensionInfo> = extensions.values.toList()

    fun getExtension(packageName: String): ExtensionInfo? = extensions[packageName]

    fun getAvailableLanguages(): Set<String> = sourcesByLang.keys.toSet()

    fun getSourceCount(): Int = sourcesById.size
    fun getExtensionCount(): Int = extensions.size
}
