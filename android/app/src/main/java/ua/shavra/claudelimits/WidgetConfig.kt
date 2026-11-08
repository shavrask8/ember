package ua.shavra.claudelimits

import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.res.Configuration

/** Види віджетів. previewW/H — розмір за замовчуванням (dp), якщо лаунчер не повідомив свій. */
enum class WidgetKind(
    val title: Int,
    val desc: Int,
    val cls: Class<out AppWidgetProvider>,
    val previewW: Int,
    val previewH: Int,
    /** Чи можна обрати, який ліміт показувати. */
    val hasLimit: Boolean = false,
) {
    FULL(R.string.wk_full, R.string.wk_full_d, UsageWidget::class.java, 300, 150),
    RING(R.string.wk_ring, R.string.wk_ring_d, RingWidget::class.java, 150, 150, true),
    DUAL(R.string.wk_dual, R.string.wk_dual_d, DualWidget::class.java, 240, 150),
    MINI(R.string.wk_mini, R.string.wk_mini_d, MiniWidget::class.java, 80, 80, true),
    TIMER(R.string.wk_timer, R.string.wk_timer_d, TimerWidget::class.java, 180, 90, true),
    STRIP(R.string.wk_strip, R.string.wk_strip_d, StripWidget::class.java, 300, 64),
    STATS(R.string.wk_stats, R.string.wk_stats_d, StatsWidget::class.java, 300, 170),
    OVERVIEW(R.string.wk_overview, R.string.wk_overview_d, OverviewWidget::class.java, 300, 170);

    companion object {
        fun of(className: String?): WidgetKind? = values().firstOrNull { it.cls.name == className }
    }
}

/**
 * Налаштування одного віджета.
 * theme: 0 авто, 1 світла, 2 темна, 3 акцентна
 * mode: 0 як у застосунку, 1 залишок, 2 використано
 * limit: 0 сесія, 1 тиждень, 2 Opus, 3 Sonnet
 * accent: 0 колір сервісу, далі — WIDGET_ACCENTS
 * textSize: 0 малий, 1 середній, 2 великий
 * corners: 0 прямі, 1 м’які, 2 круглі, 3 макс
 */
data class WidgetCfg(
    val provider: Int = 0,
    val theme: Int = 0,
    val opacity: Int = 100,
    val mode: Int = 0,
    val limit: Int = 0,
    val showReset: Boolean = true,
    val accent: Int = 0,
    val textSize: Int = 1,
    val corners: Int = 2,
    val showHeader: Boolean = true,
)

/** Кольори акценту на вибір (індекс 0 — колір сервісу). */
val WIDGET_ACCENTS = intArrayOf(0, 0xFFD97757.toInt(), 0xFF4A7BD0.toInt(), 0xFF3F9A6B.toInt(), 0xFF8B6CD9.toInt(), 0xFFD9668F.toInt())

object WidgetPrefs {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun has(c: Context, id: Int) = sp(c).contains("${id}_p")

    fun get(c: Context, id: Int): WidgetCfg {
        val s = sp(c)
        return WidgetCfg(
            provider = s.getInt("${id}_p", 0),
            theme = s.getInt("${id}_t", 0),
            opacity = s.getInt("${id}_o", 100),
            mode = s.getInt("${id}_m", 0),
            limit = s.getInt("${id}_l", 0),
            showReset = s.getBoolean("${id}_r", true),
            accent = s.getInt("${id}_a", 0),
            textSize = s.getInt("${id}_s", 1),
            corners = s.getInt("${id}_c", 2),
            showHeader = s.getBoolean("${id}_h", true),
        )
    }

    fun put(c: Context, id: Int, w: WidgetCfg) {
        sp(c).edit()
            .putInt("${id}_p", w.provider)
            .putInt("${id}_t", w.theme)
            .putInt("${id}_o", w.opacity)
            .putInt("${id}_m", w.mode)
            .putInt("${id}_l", w.limit)
            .putBoolean("${id}_r", w.showReset)
            .putInt("${id}_a", w.accent)
            .putInt("${id}_s", w.textSize)
            .putInt("${id}_c", w.corners)
            .putBoolean("${id}_h", w.showHeader)
            .apply()
    }

    fun remove(c: Context, id: Int) {
        val e = sp(c).edit()
        listOf("p", "t", "o", "m", "l", "r", "a", "s", "c", "h").forEach { e.remove("${id}_$it") }
        e.apply()
    }
}

/** Кольори віджета. auto = кольори тексту з ресурсів (самі перемикаються з темою системи). */
data class Pal(
    val bg: Int,
    val text: Int,
    val text2: Int,
    val accent: Int,
    val track: Int,
    val danger: Int,
    val auto: Boolean,
    val onAccent: Boolean,
)

fun palette(c: Context, theme: Int, p: Provider, accentIdx: Int = 0): Pal {
    val night = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val t = if (theme == 0) (if (night) 2 else 1) else theme
    val custom = WIDGET_ACCENTS.getOrElse(accentIdx) { 0 }.takeIf { it != 0 }
    val white = 0xFFFFFFFF.toInt()

    // Codex — чорно-білий стиль ChatGPT; кольори задаємо явно
    val base = if (p == Provider.CODEX) when (t) {
        2 -> Pal(0xFF2F2F2F.toInt(), 0xFFECECEC.toInt(), 0xFFB4B4B4.toInt(), 0xFFECECEC.toInt(), 0xFF424242.toInt(), 0xFFE0735F.toInt(), false, false)
        3 -> Pal(0xFF0D0D0D.toInt(), white, 0xE6FFFFFF.toInt(), white, 0x55FFFFFF, white, false, true)
        else -> Pal(white, 0xFF0D0D0D.toInt(), 0xFF5D5D5D.toInt(), 0xFF0D0D0D.toInt(), 0xFFECECEC.toInt(), 0xFFB5463C.toInt(), false, false)
    } else when (t) {
        2 -> Pal(0xFF2B2A27.toInt(), 0xFFF5F4EE.toInt(), 0xFFA6A39A.toInt(), p.accent, 0xFF3D3B37.toInt(), 0xFFE0735F.toInt(), theme == 0, false)
        3 -> Pal(p.accent, white, 0xE6FFFFFF.toInt(), white, 0x55FFFFFF, white, false, true)
        else -> Pal(0xFFFAF9F5.toInt(), 0xFF141413.toInt(), 0xFF73726C.toInt(), p.accent, 0xFFE3DFD3.toInt(), 0xFFB5463C.toInt(), theme == 0, false)
    }
    if (custom == null) return base
    return if (base.onAccent) base.copy(bg = custom) else base.copy(accent = custom)
}
