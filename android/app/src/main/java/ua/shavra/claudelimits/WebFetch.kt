package ua.shavra.claudelimits

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray

/**
 * Відкриває адресу в невидимому WebView (справжній Chrome — його пропускає Cloudflare)
 * і повертає текст сторінки. Викликати лише з головного потоку.
 */
object WebFetch {
    private val h = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun load(
        ctx: Context,
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 25_000,
        cb: (String?) -> Unit,
    ) {
        val w = WebView(ctx.applicationContext)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        Prefs.userAgent(ctx)?.let { w.settings.userAgentString = it }
        w.webViewClient = WebViewClient()

        var finished = false
        var lastText: String? = null
        val start = System.currentTimeMillis()

        fun done(r: String?) {
            if (finished) return
            finished = true
            w.stopLoading()
            w.destroy()
            cb(r)
        }

        lateinit var probe: Runnable
        probe = Runnable {
            if (finished) return@Runnable
            w.evaluateJavascript("document.body ? document.body.innerText : ''") { raw ->
                val text = runCatching { JSONArray("[$raw]").optString(0, "") }.getOrDefault("").trim()
                if (text.isNotEmpty()) lastText = text
                when {
                    text.startsWith("{") || text.startsWith("[") -> done(text)
                    System.currentTimeMillis() - start > timeoutMs -> done(lastText)
                    else -> h.postDelayed(probe, 700)
                }
            }
        }
        if (headers.isEmpty()) w.loadUrl(url) else w.loadUrl(url, headers)
        h.postDelayed(probe, 700)
    }
}
