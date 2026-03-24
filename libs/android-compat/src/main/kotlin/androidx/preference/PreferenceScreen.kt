package androidx.preference

open class PreferenceScreen {
    fun addPreference(preference: Preference) {}
}

open class Preference(val context: Any? = null) {
    var key: String = ""
    var title: CharSequence = ""
    var summary: CharSequence? = null
    var isVisible: Boolean = true
    var isEnabled: Boolean = true

    fun interface OnPreferenceChangeListener {
        fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean
    }

    fun interface OnPreferenceClickListener {
        fun onPreferenceClick(preference: Preference): Boolean
    }

    open fun setOnPreferenceChangeListener(listener: OnPreferenceChangeListener?) {}
    open fun setOnPreferenceClickListener(listener: OnPreferenceClickListener?) {}
    open fun setDefaultValue(value: Any?) {}
}

open class ListPreference(context: Any? = null) : Preference(context) {
    var entries: Array<CharSequence> = emptyArray()
    var entryValues: Array<CharSequence> = emptyArray()
    var value: String = ""
    var entry: CharSequence? = null

    fun findIndexOfValue(value: String?): Int {
        return entryValues.indexOfFirst { it.toString() == value }
    }
}

open class SwitchPreferenceCompat(context: Any? = null) : Preference(context) {
    var isChecked: Boolean = false
}

open class CheckBoxPreference(context: Any? = null) : Preference(context) {
    var isChecked: Boolean = false
}

open class EditTextPreference(context: Any? = null) : Preference(context) {
    var text: String = ""
}

open class MultiSelectListPreference(context: Any? = null) : Preference(context) {
    var entries: Array<CharSequence> = emptyArray()
    var entryValues: Array<CharSequence> = emptyArray()
    var values: Set<String> = emptySet()
}

open class PreferenceCategory(context: Any? = null) : Preference(context) {
    fun addPreference(preference: Preference) {}
}

open class PreferenceGroup(context: Any? = null) : Preference(context) {
    fun addPreference(preference: Preference) {}
}

open class TwoStatePreference(context: Any? = null) : Preference(context) {
    var isChecked: Boolean = false
}

open class DialogPreference(context: Any? = null) : Preference(context)
