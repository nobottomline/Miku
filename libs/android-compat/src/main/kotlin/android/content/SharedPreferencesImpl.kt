package android.content

import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

class SharedPreferencesImpl(private val name: String) : SharedPreferences {
    private val data = ConcurrentHashMap<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    init {
        load()
    }

    private fun getFile(): File {
        val dir = File(System.getProperty("user.dir"), "data/prefs")
        dir.mkdirs()
        return File(dir, "$name.properties")
    }

    private fun load() {
        val file = getFile()
        if (file.exists()) {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            props.forEach { (k, v) -> data[k as String] = v as String }
        }
    }

    private fun save() {
        val props = Properties()
        data.forEach { (k, v) -> if (v != null) props.setProperty(k, v.toString()) }
        getFile().outputStream().use { props.store(it, null) }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        data[key]?.toString()?.toBooleanStrictOrNull() ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        data[key]?.toString()?.toFloatOrNull() ?: defValue

    override fun getInt(key: String, defValue: Int): Int =
        data[key]?.toString()?.toIntOrNull() ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        data[key]?.toString()?.toLongOrNull() ?: defValue

    override fun getString(key: String, defValue: String?): String? =
        data[key]?.toString() ?: defValue

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val value = data[key]?.toString() ?: return defValues
        return if (value.isEmpty()) emptySet() else value.split("\u001F").toSet()
    }

    override fun getAll(): Map<String, *> = data.toMap()

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply {
            changes[key] = values?.joinToString("\u001F")
        }
        override fun remove(key: String) = apply { changes[key] = this }
        override fun clear() = apply { clear = true }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            if (clear) data.clear()
            changes.forEach { (key, value) ->
                if (value === this) {
                    data.remove(key)
                } else {
                    data[key] = value
                }
            }
            save()
            changes.keys.forEach { key ->
                listeners.forEach { it.onSharedPreferenceChanged(this@SharedPreferencesImpl, key) }
            }
        }
    }
}
