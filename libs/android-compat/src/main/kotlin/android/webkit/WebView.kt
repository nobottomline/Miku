package android.webkit

class WebView(context: Any? = null) {
    var settings: WebSettings = WebSettings()
}

class WebSettings {
    var javaScriptEnabled: Boolean = false
    var userAgentString: String = ""
    var domStorageEnabled: Boolean = false
}

open class WebViewClient
open class WebChromeClient
open class CookieManager {
    companion object {
        @JvmStatic
        fun getInstance(): CookieManager = CookieManager()
    }
    fun getCookie(url: String): String? = null
    fun setCookie(url: String, value: String) {}
    fun removeAllCookies(callback: ((Boolean) -> Unit)?) {}
    fun flush() {}
}
