package miku.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class LibraryManga(
    val manga: Manga,
    val unreadCount: Int = 0,
    val category: Category? = null,
    val addedAt: Instant? = null,
)
