package miku.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ExtensionData(
    val packageName: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val lang: String,
    val isNsfw: Boolean,
    val sourceCount: Int,
)
