package android.content

import android.content.pm.PackageManager
import java.io.File

open class Context {
    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return SharedPreferencesImpl(name)
    }

    open fun getPackageManager(): PackageManager = PackageManager()
    open fun getPackageName(): String = "miku.server"
    open fun getApplicationInfo(): android.content.pm.ApplicationInfo = android.content.pm.ApplicationInfo()

    open val cacheDir: File
        get() = File(System.getProperty("java.io.tmpdir"), "miku-cache").also { it.mkdirs() }

    open val filesDir: File
        get() = File(System.getProperty("user.dir"), "data/files").also { it.mkdirs() }

    companion object {
        const val MODE_PRIVATE = 0
    }
}
