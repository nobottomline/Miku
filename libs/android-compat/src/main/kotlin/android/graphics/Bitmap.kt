package android.graphics

class Bitmap {
    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap = Bitmap()
    }

    enum class Config {
        ARGB_8888,
        RGB_565
    }

    val width: Int = 0
    val height: Int = 0
}

class BitmapFactory {
    companion object {
        @JvmStatic
        fun decodeStream(input: java.io.InputStream): Bitmap? = Bitmap()

        @JvmStatic
        fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? = Bitmap()
    }
}
