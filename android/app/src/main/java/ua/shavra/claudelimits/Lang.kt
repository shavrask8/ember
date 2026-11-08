package ua.shavra.claudelimits

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.LocaleList
import java.util.Locale

/**
 * Мова застосунку: за замовчуванням — як у системі, або обрана вручну.
 * Android 13+: системний механізм «мова застосунку» (видно й у налаштуваннях Android).
 * Старіші: підміняємо конфігурацію контексту.
 */
object Lang {
    /** "" — як у системі. */
    val CODES = listOf("", "en", "uk", "pl", "de", "es")
    private val NATIVE = listOf("", "English", "Українська", "Polski", "Deutsch", "Español")

    fun names(c: Context) = NATIVE.mapIndexed { i, n -> if (i == 0) c.getString(R.string.lang_system) else n }

    private fun sp(c: Context) = c.getSharedPreferences("claude_limits", Context.MODE_PRIVATE)

    fun current(c: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val l = c.getSystemService(LocaleManager::class.java)?.applicationLocales
            return if (l == null || l.isEmpty) "" else l[0].language
        }
        return sp(c).getString("lang", "") ?: ""
    }

    fun set(a: Activity, code: String) {
        sp(a).edit().putString("lang", code).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            a.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (code.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(code)
        } else {
            a.recreate()
        }
        // віджети й плитки — уже новою мовою
        Handler(Looper.getMainLooper()).postDelayed({ Repo.notifyViews(a.applicationContext) }, 600)
    }

    /** Контекст обраною мовою (для Android < 13; на новіших нічого не робить). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val code = sp(base).getString("lang", "") ?: ""
        if (code.isEmpty()) return base
        val loc = Locale.forLanguageTag(code)
        Locale.setDefault(loc)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        return base.createConfigurationContext(cfg)
    }

    fun locale(c: Context): Locale = c.resources.configuration.locales[0]
}
