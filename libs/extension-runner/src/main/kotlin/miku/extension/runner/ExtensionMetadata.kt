package miku.extension.runner

import kotlinx.serialization.Serializable

@Serializable
data class ExtensionMetadata(
    val packageName: String,
    val name: String,
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val lang: String = "en",
    val isNsfw: Boolean = false,
    val sourceClasses: List<String> = emptyList(),
)
