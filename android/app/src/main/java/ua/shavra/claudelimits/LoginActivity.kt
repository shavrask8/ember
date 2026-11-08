package ua.shavra.claudelimits

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.TypedValue
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Вхід на сайт сервісу у вбудованому браузері; після входу забираємо cookie сесії.
 * Підтримує спливні вікна — через них працює «Увійти через Google».
 */
class LoginActivity : Activity() {
    private lateinit var p: Provider
    private lateinit var web: WebView
    private lateinit var frame: FrameLayout
    private var popup: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var done = false
    private var ua = ""

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Lang.wrap(base))

    private val poll = object : Runnable {
        override fun run() {
            if (!check()) handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        p = Provider.byId(intent.getStringExtra(EXTRA_PROVIDER))
        Ui.applyTheme(this, p)
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(window)

        val pad = Ui.dp(this, 16)
        val hint = TextView(this).apply {
            text = getString(R.string.login_hint, p.title)
            setTextColor(Ui.color(context, R.attr.cText2))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        frame = FrameLayout(this)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(Ui.color(context, R.attr.cBg))
            addView(hint)
            addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })

        CookieManager.getInstance().setAcceptCookie(true)
        web = newWebView()
        // Прибираємо позначку WebView з User-Agent і використовуємо той самий UA для запитів
        ua = web.settings.userAgentString.replace("; wv", "")
        web.settings.userAgentString = ua
        Prefs.setUserAgent(this, ua)
        frame.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))

        web.loadUrl(p.loginUrl)
        handler.postDelayed(poll, 1000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        if (ua.isNotEmpty()) settings.userAgentString = ua
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                check()
            }
        }
        webChromeClient = chrome
    }

    private val chrome = object : WebChromeClient() {
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            closePopup()
            val pw = newWebView()
            pw.setBackgroundColor(Ui.color(this@LoginActivity, R.attr.cSurface))
            frame.addView(pw, FrameLayout.LayoutParams(MATCH, MATCH))
            popup = pw
            (resultMsg.obj as WebView.WebViewTransport).webView = pw
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            if (window == popup) closePopup()
        }
    }

    private fun closePopup() {
        popup?.let {
            frame.removeView(it)
            it.destroy()
        }
        popup = null
    }

    private fun check(): Boolean {
        if (done) return true
        val c = CookieManager.getInstance().getCookie(p.cookieUrl) ?: return false
        if (!c.contains(p.loginCookie)) return false
        done = true
        CookieManager.getInstance().flush()
        Prefs.setCookie(this, p, c)
        Prefs.setEnabled(this, p, true)
        if (p == Provider.CLAUDE) Prefs.setOrgId(this, null)
        setResult(RESULT_OK)
        finish()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val pw = popup
        when {
            pw != null && pw.canGoBack() -> pw.goBack()
            pw != null -> closePopup()
            web.canGoBack() -> web.goBack()
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        closePopup()
        if (::web.isInitialized) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PROVIDER = "provider"
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
    }
}
