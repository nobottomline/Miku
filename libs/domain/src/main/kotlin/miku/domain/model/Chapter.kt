package miku.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Chapter(
    val id: Long = 0,
    val mangaId: Long,
    val sourceId: Long,
    val url: String,
    val name: String,
    val chapterNumber: Float = -1f,
    val scanlator: String? = null,
    val dateUpload: Instant? = null,
    val read: Boolean = false,
    val lastPageRead: Int = 0,
    val dateFetch: Instant? = null,
)
