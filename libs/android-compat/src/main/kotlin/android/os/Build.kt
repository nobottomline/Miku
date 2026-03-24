package android.os

object Build {
    object VERSION {
        @JvmField
        val SDK_INT: Int = 33 // Simulate Android 13

        @JvmField
        val RELEASE: String = "13"
    }

    @JvmField
    val MODEL: String = "Miku Server"

    @JvmField
    val MANUFACTURER: String = "Miku"

    @JvmField
    val PRODUCT: String = "miku-server"
}
