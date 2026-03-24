package miku.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PageData(
    val index: Int,
    val url: String = "",
    val imageUrl: String? = null,
)
