package miku.extension.runner

import eu.kanade.tachiyomi.source.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ExtensionInfo(
    val packageName: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val lang: String,
    val isNsfw: Boolean,
    @Transient
    val sources: List<Source> = emptyList(),
    @Transient
    val classLoader: ClassLoader? = null,
) {
    val iconUrl: String? = null
}
