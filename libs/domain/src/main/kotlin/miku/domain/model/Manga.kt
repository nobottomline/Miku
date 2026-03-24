package miku.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Manga(
    val id: Long = 0,
    val sourceId: Long,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val thumbnailUrl: String? = null,
    val initialized: Boolean = false,
    val inLibrary: Boolean = false,
    val lastUpdated: Instant? = null,
)

@Serializable
enum class MangaStatus(val value: Int) {
    UNKNOWN(0),
    ONGOING(1),
    COMPLETED(2),
    LICENSED(3),
    PUBLISHING_FINISHED(4),
    CANCELLED(5),
    ON_HIATUS(6);

    companion object {
        fun fromInt(value: Int): MangaStatus =
            entries.find { it.value == value } ?: UNKNOWN
    }
}
