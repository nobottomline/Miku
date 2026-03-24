package miku.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class FilterData {
    abstract val name: String

    @Serializable
    data class Header(override val name: String) : FilterData()

    @Serializable
    data class Separator(override val name: String = "") : FilterData()

    @Serializable
    data class Select(
        override val name: String,
        val values: List<String>,
        val state: Int = 0,
    ) : FilterData()

    @Serializable
    data class Text(
        override val name: String,
        val state: String = "",
    ) : FilterData()

    @Serializable
    data class CheckBox(
        override val name: String,
        val state: Boolean = false,
    ) : FilterData()

    @Serializable
    data class TriState(
        override val name: String,
        val state: Int = 0,
    ) : FilterData()

    @Serializable
    data class Sort(
        override val name: String,
        val values: List<String>,
        val selection: SortSelection? = null,
    ) : FilterData()

    @Serializable
    data class SortSelection(
        val index: Int,
        val ascending: Boolean,
    )

    @Serializable
    data class Group(
        override val name: String,
        val filters: List<FilterData>,
    ) : FilterData()
}
