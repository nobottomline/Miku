package miku.extension.runner

import kotlinx.serialization.Serializable

@Serializable
data class ExtensionRepoIndex(
    val extensions: List<ExtensionRepoEntry> = emptyList(),
)

@Serializable
data class ExtensionRepoEntry(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Int,
    val version: String,
    val nsfw: Int = 0,
    val sources: List<ExtensionRepoSource> = emptyList(),
) {
    val isNsfw: Boolean get() = nsfw == 1
}

@Serializable
data class ExtensionRepoSource(
    val name: String,
    val lang: String,
    val id: Long,
    val baseUrl: String = "",
)
