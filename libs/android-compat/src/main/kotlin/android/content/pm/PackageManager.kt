package android.content.pm

open class PackageManager {
    open fun getApplicationInfo(packageName: String, flags: Int): ApplicationInfo = ApplicationInfo()
}
