package androidx.preference

open class PreferenceScreen
open class Preference(val context: Any? = null) {
    var key: String = ""
    var title: CharSequence = ""
    var summary: CharSequence? = null
}

open class ListPreference(context: Any? = null) : Preference(context) {
    var entries: Array<CharSequence> = emptyArray()
    var entryValues: Array<CharSequence> = emptyArray()
    var value: String = ""
    fun setDefaultValue(value: Any?) {}
    fun setOnPreferenceChangeListener(listener: OnPreferenceChangeListener?) {}
}

open class SwitchPreferenceCompat(context: Any? = null) : Preference(context) {
    fun setDefaultValue(value: Any?) {}
    fun setOnPreferenceChangeListener(listener: OnPreferenceChangeListener?) {}
}

open class EditTextPreference(context: Any? = null) : Preference(context) {
    var text: String = ""
    fun setDefaultValue(value: Any?) {}
    fun setOnPreferenceChangeListener(listener: OnPreferenceChangeListener?) {}
}

open class MultiSelectListPreference(context: Any? = null) : Preference(context) {
    var entries: Array<CharSequence> = emptyArray()
    var entryValues: Array<CharSequence> = emptyArray()
    var values: Set<String> = emptySet()
    fun setDefaultValue(value: Any?) {}
}

open class PreferenceCategory(context: Any? = null) : Preference(context)

fun interface OnPreferenceChangeListener {
    fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean
}
