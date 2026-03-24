package android.text

object TextUtils {
    @JvmStatic
    fun isEmpty(str: CharSequence?): Boolean = str.isNullOrEmpty()

    @JvmStatic
    fun join(delimiter: CharSequence, tokens: Iterable<*>): String =
        tokens.joinToString(delimiter)

    @JvmStatic
    fun htmlEncode(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
