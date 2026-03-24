package android.text

import org.jsoup.Jsoup

object Html {
    @JvmStatic
    fun fromHtml(source: String, flags: Int = 0): Spanned {
        return SpannedString(Jsoup.parse(source).text())
    }
}

interface Spanned : CharSequence
class SpannedString(private val text: String) : Spanned {
    override val length: Int get() = text.length
    override fun get(index: Int): Char = text[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)
    override fun toString(): String = text
}
