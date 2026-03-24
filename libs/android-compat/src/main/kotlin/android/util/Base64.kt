package android.util

object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val URL_SAFE = 8

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray {
        val decoder = if (flags and URL_SAFE != 0) {
            java.util.Base64.getUrlDecoder()
        } else {
            java.util.Base64.getDecoder()
        }
        return decoder.decode(str.trim())
    }

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray {
        return decode(String(input), flags)
    }

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray {
        val encoder = when {
            flags and URL_SAFE != 0 && flags and NO_PADDING != 0 ->
                java.util.Base64.getUrlEncoder().withoutPadding()
            flags and URL_SAFE != 0 ->
                java.util.Base64.getUrlEncoder()
            flags and NO_PADDING != 0 ->
                java.util.Base64.getEncoder().withoutPadding()
            else -> java.util.Base64.getEncoder()
        }
        val result = encoder.encode(input)
        return if (flags and NO_WRAP != 0) result
        else result
    }

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        return String(encode(input, flags))
    }
}
