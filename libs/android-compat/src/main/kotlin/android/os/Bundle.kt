package android.os

class Bundle {
    private val data = mutableMapOf<String, Any?>()

    fun putString(key: String, value: String?) { data[key] = value }
    fun getString(key: String): String? = data[key] as? String
    fun getString(key: String, defaultValue: String): String = data[key] as? String ?: defaultValue
    fun putInt(key: String, value: Int) { data[key] = value }
    fun getInt(key: String, defaultValue: Int = 0): Int = data[key] as? Int ?: defaultValue
    fun putBoolean(key: String, value: Boolean) { data[key] = value }
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = data[key] as? Boolean ?: defaultValue
    fun putLong(key: String, value: Long) { data[key] = value }
    fun getLong(key: String, defaultValue: Long = 0L): Long = data[key] as? Long ?: defaultValue
    fun containsKey(key: String): Boolean = data.containsKey(key)
    fun isEmpty(): Boolean = data.isEmpty()
    fun size(): Int = data.size
    fun remove(key: String) { data.remove(key) }
    fun clear() { data.clear() }
    fun keySet(): Set<String> = data.keys
}
