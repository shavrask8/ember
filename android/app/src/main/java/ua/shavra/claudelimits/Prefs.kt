package ua.shavra.claudelimits

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

/** Один ліміт: відсоток використання (0–100) і час скидання (epoch ms, 0 — невідомо). */
class Limit(val utilization: Double, val resetsAt: Long) {
    /** Використано у відсотках; якщо час скидання вже минув — ліміт вважається порожнім. */
    fun used(now: Long): Int =
        if (resetsAt in 1..now) 0 else utilization.coerceIn(0.0, 100.0).roundToInt()
}

class Usage(
    val session: Limit?,
    val week: Limit?,
    val opus: Limit?,
    val sonnet: Limit?,
    val updatedAt: Long,
)

object Prefs {
    private val C = Provider.CLAUDE

    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("claude_limits", Context.MODE_PRIVATE)

    // ---- обліковий запис (для кожного сервісу окремо) ----
    fun cookie(c: Context, p: Provider = C): String? = sp(c).getString("${p.prefix}cookie", null)
    fun setCookie(c: Context, p: Provider, v: String?) = sp(c).edit().putString("${p.prefix}cookie", v).apply()

    fun lastError(c: Context, p: Provider = C): String? = sp(c).getString("${p.prefix}error", null)
    fun setLastError(c: Context, p: Provider, v: String?) = sp(c).edit().putString("${p.prefix}error", v).apply()

    fun userAgent(c: Context): String? = sp(c).getString("ua", null)
    fun setUserAgent(c: Context, v: String?) = sp(c).edit().putString("ua", v).apply()

    fun orgId(c: Context): String? = sp(c).getString("org", null)
    fun setOrgId(c: Context, v: String?) = sp(c).edit().putString("org", v).apply()

    // ---- сервіси ----
    fun enabled(c: Context, p: Provider) = p == C || sp(c).getBoolean("${p.id}_enabled", false)
    fun setEnabled(c: Context, p: Provider, v: Boolean) = sp(c).edit().putBoolean("${p.id}_enabled", v).apply()

    fun selected(c: Context): Provider =
        Provider.byId(sp(c).getString("selected", null)).takeIf { enabled(c, it) } ?: C
    fun setSelected(c: Context, p: Provider) = sp(c).edit().putString("selected", p.id).apply()

    fun tileProvider(c: Context): Provider =
        Provider.byId(sp(c).getString("tile_provider", null)).takeIf { enabled(c, it) } ?: C
    fun setTileProvider(c: Context, p: Provider) = sp(c).edit().putString("tile_provider", p.id).apply()

    fun onboarded(c: Context) = sp(c).getBoolean("onboarded", false)
    fun setOnboarded(c: Context, v: Boolean) = sp(c).edit().putBoolean("onboarded", v).apply()

    // ---- головний екран: модулі (за замовчуванням вимкнені — чистий екран) ----
    fun module(c: Context, key: String) = sp(c).getBoolean("mod_$key", false)
    fun setModule(c: Context, key: String, v: Boolean) = sp(c).edit().putBoolean("mod_$key", v).apply()

    // ---- відображення й оновлення ----
    fun showUsed(c: Context) = sp(c).getBoolean("show_used", false)
    fun setShowUsed(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_used", v).apply()

    fun autoRefresh(c: Context) = sp(c).getBoolean("auto", true)
    fun setAutoRefresh(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto", v).apply()

    fun intervalMin(c: Context) = sp(c).getInt("interval", 30)
    fun setIntervalMin(c: Context, v: Int) = sp(c).edit().putInt("interval", v).apply()

    fun wifiOnly(c: Context) = sp(c).getBoolean("wifi_only", false)
    fun setWifiOnly(c: Context, v: Boolean) = sp(c).edit().putBoolean("wifi_only", v).apply()

    fun chargingOnly(c: Context) = sp(c).getBoolean("charging_only", false)
    fun setChargingOnly(c: Context, v: Boolean) = sp(c).edit().putBoolean("charging_only", v).apply()

    // ---- дані лімітів ----
    private val usageKeys = listOf("s", "w", "o", "n")

    fun saveUsage(c: Context, p: Provider, u: Usage) {
        val x = p.prefix
        sp(c).edit().apply {
            putLimit("${x}s", u.session)
            putLimit("${x}w", u.week)
            putLimit("${x}o", u.opus)
            putLimit("${x}n", u.sonnet)
            putLong("${x}updated", u.updatedAt)
        }.apply()
    }

    fun loadUsage(c: Context, p: Provider = C): Usage? {
        val s = sp(c)
        val x = p.prefix
        if (!s.contains("${x}updated")) return null
        return Usage(s.limit("${x}s"), s.limit("${x}w"), s.limit("${x}o"), s.limit("${x}n"), s.getLong("${x}updated", 0))
    }

    fun clearAccount(c: Context, p: Provider) {
        val x = p.prefix
        sp(c).edit().apply {
            remove("${x}cookie"); remove("${x}error"); remove("${x}updated")
            if (p == C) remove("org")
            usageKeys.forEach { remove("$x${it}_u"); remove("$x${it}_r") }
        }.apply()
    }

    private fun SharedPreferences.Editor.putLimit(k: String, l: Limit?) {
        if (l == null) {
            remove("${k}_u"); remove("${k}_r")
        } else {
            putFloat("${k}_u", l.utilization.toFloat()); putLong("${k}_r", l.resetsAt)
        }
    }

    private fun SharedPreferences.limit(k: String): Limit? =
        if (contains("${k}_u")) Limit(getFloat("${k}_u", 0f).toDouble(), getLong("${k}_r", 0)) else null
}
