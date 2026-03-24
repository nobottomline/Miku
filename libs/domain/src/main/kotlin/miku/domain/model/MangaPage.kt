package miku.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MangaPageResult(
    val mangas: List<Manga>,
    val hasNextPage: Boolean,
    val page: Int,
)
