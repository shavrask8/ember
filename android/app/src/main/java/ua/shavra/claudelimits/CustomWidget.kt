package ua.shavra.claudelimits

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** «Свій» — повністю налаштовуваний віджет. Малюється цілком як зображення, тож обмежень RemoteViews немає. */
class CustomWidget : BaseWidget() { override val kind get() = WidgetKind.CUSTOM }

/**
 * Налаштування віджета «Свій».
 * order — порядок усіх блоків, on — які з них показано.
 * style: 0 кільця, 1 смужки, 2 числа · font: 0 як у сервісу, 1 без зарубок, 2 із зарубками, 3 моноширинний, 4 вузький
 * align: 0 ліворуч, 1 по центру · tap: 0 відкрити застосунок, 1 оновити
 */
data class CustomCfg(
    val provider: Int = 0,
    val mode: Int = 0,
    val order: List<Int> = CustomArt.DEFAULT_ORDER,
    val on: Set<Int> = setOf(CustomArt.B_HEADER, CustomArt.B_SESSION, CustomArt.B_WEEK),
    val style: Int = 1,
    val bg: Int = 0,
    val opacity: Int = 100,
    val gradient: Boolean = false,
    val border: Boolean = false,
    val accent: Int = 0,
    val font: Int = 0,
    val scale: Int = 100,
    val radius: Int = 24,
    val thickness: Int = 6,
    val align: Int = 0,
    val showReset: Boolean = true,
    val labels: Boolean = true,
    val tap: Int = 0,
) {
    fun encode(): String = listOf(
        "p=$provider", "m=$mode", "o=${order.joinToString(".")}", "e=${on.joinToString(".")}", "st=$style",
        "bg=$bg", "op=$opacity", "gr=${b(gradient)}", "bd=${b(border)}", "ac=$accent", "f=$font", "sc=$scale",
        "r=$radius", "t=$thickness", "al=$align", "rs=${b(showReset)}", "lb=${b(labels)}", "tp=$tap",
    ).joinToString(";")

    private fun b(v: Boolean) = if (v) 1 else 0

    companion object {
        fun decode(s: String?, fallbackProvider: Int): CustomCfg {
            val d = CustomCfg(provider = fallbackProvider)
            if (s.isNullOrEmpty()) return d
            val m = s.split(';').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 } }.associate { it[0] to it[1] }
            fun i(k: String, def: Int) = m[k]?.toIntOrNull() ?: def
            fun list(k: String) = m[k]?.split('.')?.mapNotNull { it.toIntOrNull() }
            val order = (list("o") ?: d.order).filter { it in CustomArt.DEFAULT_ORDER }.distinct()
            return CustomCfg(
                provider = i("p", d.provider), mode = i("m", d.mode),
                order = order + CustomArt.DEFAULT_ORDER.filter { it !in order },
                on = list("e")?.toSet() ?: d.on,
                style = i("st", d.style), bg = i("bg", d.bg), opacity = i("op", d.opacity),
                gradient = i("gr", 0) == 1, border = i("bd", 0) == 1, accent = i("ac", d.accent), font = i("f", d.font),
                scale = i("sc", d.scale), radius = i("r", d.radius), thickness = i("t", d.thickness), align = i("al", d.align),
                showReset = i("rs", 1) == 1, labels = i("lb", 1) == 1, tap = i("tp", d.tap),
            )
        }
    }
}

object CustomPrefs {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    fun get(c: Context, id: Int) = CustomCfg.decode(sp(c).getString("${id}_x", null), Prefs.selected(c).ordinal)
    fun put(c: Context, id: Int, cfg: CustomCfg) = sp(c).edit().putString("${id}_x", cfg.encode()).apply()
}

object CustomWidgets {
    fun build(c0: Context, id: Int, refreshing: Boolean, override: CustomCfg?): RemoteViews {
        val c = Lang.wrap(c0)
        val cfg = override ?: CustomPrefs.get(c, id)
        val (w, h) = Widgets.size(c, WidgetKind.CUSTOM, id)
        val v = RemoteViews(c.packageName, R.layout.widget_custom)
        v.setImageViewBitmap(R.id.w_img, CustomArt.render(c, cfg, w, h, refreshing))
        val p = Provider.of(cfg.provider)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pi = if (cfg.tap == 1) {
            PendingIntent.getBroadcast(c, 300 + id.coerceAtLeast(0) % 10000, Intent(c, CustomWidget::class.java).setAction(Widgets.ACTION_REFRESH), flags)
        } else {
            PendingIntent.getActivity(c, 100 + p.ordinal, Intent(c, MainActivity::class.java).putExtra(MainActivity.EXTRA_PROVIDER, p.id), flags)
        }
        v.setOnClickPendingIntent(android.R.id.background, pi)
        return v
    }
}

/** Малювання віджета «Свій». Усі розміри — у dp × масштаб (u). */
object CustomArt {
    const val B_HEADER = 0
    const val B_SESSION = 1
    const val B_WEEK = 2
    const val B_OPUS = 3
    const val B_SONNET = 4
    const val B_COUNTDOWN = 5
    const val B_CHART = 6
    const val B_UPDATED = 7
    val DEFAULT_ORDER = listOf(B_HEADER, B_SESSION, B_WEEK, B_OPUS, B_SONNET, B_COUNTDOWN, B_CHART, B_UPDATED)

    /** Кольори фону (0 — авто, за темою системи). */
    val BGS = intArrayOf(
        0, 0xFFFAF9F5.toInt(), 0xFFFFFFFF.toInt(), 0xFF2B2A27.toInt(), 0xFF0D0D0D.toInt(),
        0xFFD97757.toInt(), 0xFF1F2A44.toInt(), 0xFF1E3B2F.toInt(), 0xFF3B2340.toInt(), 0xFFE9DCC9.toInt(),
    )

    /** Кольори акценту (0 — колір сервісу, 1 — монохром). */
    val ACCENTS = intArrayOf(
        0, 1, 0xFFD97757.toInt(), 0xFF4A7BD0.toInt(), 0xFF3F9A6B.toInt(), 0xFF8B6CD9.toInt(), 0xFFD9668F.toInt(), 0xFFE0A43B.toInt(),
    )

    class Colors(val bg: Int, val bg2: Int, val text: Int, val text2: Int, val accent: Int, val track: Int, val danger: Int)

    private fun alpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = ((a shr s and 255) * (1 - t) + (b shr s and 255) * t).toInt() shl s
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = Color.luminance(a) + 0.05
        val lb = Color.luminance(b) + 0.05
        return max(la, lb) / min(la, lb)
    }

    fun colors(c: Context, cfg: CustomCfg, p: Provider): Colors {
        val night = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = BGS.getOrElse(cfg.bg) { 0 }.takeIf { it != 0 } ?: when {
            p == Provider.CODEX -> if (night) 0xFF2F2F2F.toInt() else 0xFFFFFFFF.toInt()
            night -> 0xFF2B2A27.toInt()
            else -> 0xFFFAF9F5.toInt()
        }
        val dark = Color.luminance(bg) < 0.4
        val text = if (dark) 0xFFF5F4EE.toInt() else 0xFF141413.toInt()
        var accent = when (val a = ACCENTS.getOrElse(cfg.accent) { 0 }) {
            0 -> if (p == Provider.CODEX) text else p.accent
            1 -> text
            else -> a
        }
        if (contrast(accent, bg) < 1.5) accent = text
        var danger = if (dark) 0xFFE8806B.toInt() else 0xFFB5463C.toInt()
        if (contrast(danger, bg) < 1.5 || accent == text) danger = accent
        return Colors(bg, blend(bg, accent, 0.25f), text, alpha(text, 0xA6), accent, alpha(text, if (dark) 0x33 else 0x1F), danger)
    }

    fun typeface(cfg: CustomCfg, p: Provider, bold: Boolean): Typeface {
        val family = when (cfg.font) {
            1 -> "sans-serif"
            2 -> "serif"
            3 -> "monospace"
            4 -> "sans-serif-condensed"
            else -> if (p == Provider.CODEX) "sans-serif" else "serif"
        }
        return Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Один блок вмісту: висота залежить від масштабу u і ширини. */
    private abstract class Item {
        abstract fun height(u: Float, w: Float): Float
        abstract fun draw(cv: Canvas, x: Float, y: Float, w: Float, u: Float)
    }

    private class Ctx(
        val c: Context, val cfg: CustomCfg, val p: Provider, val col: Colors, val u: Usage?, val logged: Boolean,
        val used: Boolean, val now: Long, val refreshing: Boolean,
    ) {
        val numFace = typeface(cfg, p, false)
        val numBold = typeface(cfg, p, true)
        val sans: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val sansMed: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val center get() = cfg.align == 1

        fun paint(size: Float, color: Int, face: Typeface = sans) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = face
        }

        fun pct(l: Limit) = if (used) l.used(now) else 100 - l.used(now)
        fun color(l: Limit) = if (100 - l.used(now) <= 15) col.danger else col.accent
        fun reset(l: Limit): String? =
            if (cfg.showReset && l.resetsAt > now) Fmt.resetShort(c, l.resetsAt, now) + " · " + Fmt.until(c, l.resetsAt, now) else null

        fun limit(b: Int): Pair<Limit?, String> {
            val claude = p == Provider.CLAUDE
            return when (b) {
                B_SESSION -> u?.session to c.getString(if (claude) R.string.session else R.string.h5)
                B_WEEK -> u?.week to c.getString(R.string.week)
                B_OPUS -> (if (claude) u?.opus else null) to "Opus"
                else -> (if (claude) u?.sonnet else null) to "Sonnet"
            }
        }

        /** Текст, обрізаний «…» під ширину. */
        fun fit(s: String, p: Paint, w: Float): String {
            if (p.measureText(s) <= w) return s
            var t = s
            while (t.length > 1 && p.measureText("$t…") > w) t = t.dropLast(1)
            return t.trimEnd() + "…"
        }

        fun text(cv: Canvas, s: String, p: Paint, x: Float, baseline: Float, w: Float, center: Boolean = this.center) {
            val t = fit(s, p, w)
            val tx = if (center) x + (w - p.measureText(t)) / 2 else x
            cv.drawText(t, tx, baseline, p)
        }
    }

    // ---------- блоки ----------

    private class Header(val k: Ctx) : Item() {
        override fun height(u: Float, w: Float) = 20 * u
        override fun draw(cv: Canvas, x: Float, y: Float, w: Float, u: Float) {
            val icon = 15 * u
            val title = k.paint(15.5f * u, k.col.text, k.numBold)
            val word = k.paint(12 * u, k.col.text2)
            val ws = if (k.logged) "  ·  " + Fmt.word(k.c, k.used) else ""
            val total = icon + 7 * u + title.measureText(k.p.title) + word.measureText(ws)
            var cx = if (k.center) x + max(0f, (w - total) / 2) else x
            k.c.getDrawable(k.p.icon)?.mutate()?.let {
                it.setTint(k.col.accent)
                val top = (y + (20 * u - icon) / 2).toInt()
                it.setBounds(cx.toInt(), top, (cx + icon).toInt(), (top + icon).toInt())
                it.draw(cv)
            }
            cx += icon + 7 * u
            val base = y + 15 * u
            cv.drawText(k.p.title, cx, base, title)
            cx += title.measureText(k.p.title)
            if (ws.isNotEmpty()) cv.drawText(k.fit(ws, word, x + w - cx), cx, base, word)
        }
    }

    private class BarRow(val k: Ctx, val b: Int) : Item() {
        override fun height(u: Float, w: Float): Float {
            val (l, _) = k.limit(b)
            val t = k.cfg.thickness * u
            return 16 * u + 6 * u + t + (if (l != null && k.reset(l) != null) 17 * u else 0f)
        }

        override fun draw(cv: Canvas, x: Float, y: Float, w: Float, u: Float) {
            val (l, name) = k.limit(b)
            val label = k.paint(13 * u, k.col.text)
            val pctP = k.paint(13.5f * u, k.col.text, k.numBold)
            val ps = if (l == null) "—" else "${k.pct(l)}%"
            val pw = pctP.measureText(ps)
            if (k.cfg.labels) k.text(cv, name, label, x, y + 13 * u, w - pw - 8 * u, false)
            cv.drawText(ps, if (k.cfg.labels || !k.center) x + w - pw else x + (w - pw) / 2, y + 13 * u, pctP)
            val t = k.cfg.thickness * u
            val top = y + 22 * u
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = k.col.track
            cv.drawRoundRect(RectF(x, top, x + w, top + t), t / 2, t / 2, p)
            if (l != null) {
                val v = k.pct(l) / 100f
                if (v > 0.003f) {
                    p.color = k.color(l)
                    cv.drawRoundRect(RectF(x, top, x + max(t, w * v), top + t), t / 2, t / 2, p)
                }
                k.reset(l)?.let { k.text(cv, it, k.paint(11 * u, k.col.text2), x, top + t + 15 * u, w) }
            }
        }
    }

    private class NumberRow(val k: Ctx, val b: Int) : Item() {
        override fun height(u: Float, w: Float): Float {
            val (l, _) = k.limit(b)
            return (if (k.cfg.labels) 16 * u else 0f) + 32 * u + (if (l != null && k.reset(l) != null) 15 * u else 0f)
        }

        override fun draw(cv: Canvas, x: Float, y: Float, w: Float, u: Float) {
            val (l, name) = k.limit(b)
            var yy = y
            if (k.cfg.labels) {
                k.text(cv, name, k.paint(12 * u, k.col.text2, k.sansMed), x, yy + 12 * u, w)
                yy += 16 * u
            }
            val big = k.paint(30 * u, if (l == null) k.col.text2 else k.color(l), k.numFace)
            val word = k.paint(12 * u, k.col.text2)
            val ps = if (l == null) "—" else "${k.pct(l)}%"
            val ws = if (l == null) "" else "  " + Fmt.word(k.c, k.used)
            val total = big.measureText(ps) + word.measureText(ws)
            val sx = if (k.center) x + max(0f, (w - total) / 2) else x
            cv.drawText(ps, sx, yy + 27 * u, big)
            cv.drawText(k.fit(ws, word, x + w - sx - big.measureText(ps)), sx + big.measureText(ps), yy + 27 * u, word)
            yy += 32 * u
            if (l != null) k.reset(l)?.let { k.text(cv, it, k.paint(11 * u, k.col.text2), x, yy + 11 * u, w) }
        }
    }

    /** Кілька лімітів поруч у вигляді кілець. */
    private class RingRow(val k: Ctx, val blocks: List<Int>) : Item() {
        private fun ringD(u: Float, w: Float): Float {
            val n = blocks.size
            return min((w - (n - 1) * 12 * u) / n * 0.9f, 86 * u)
        }

        private fun hasReset() = blocks.any { b -> k.limit(b).first?.let { k.reset(it) != null } == true }

        override fun height(u: Float, w: Float) =
            ringD(u, w) + (if (k.cfg.labels) 18 * u else 0f) + (if (hasReset()) 14 * u else 0f)

        override fun draw(cv: Canvas, x: Float, y: Float, w: Float, u: Float) {
            val n = blocks.size
            val d = ringD(u, w)
            val col = (w - (n - 1) * 12 * u) / n
            val t = min(k.cfg.thickness * u * 1.2f, d * 0.16f)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = t }
            blocks.forEachIndexed { i, b ->
                val (l, name) = k.limit(b)
                val cx = x + i * (col + 12 * u) + col / 2
                val rect = RectF(cx - d / 2 + t / 2, y + t / 2, cx + d / 2 - t / 2, y + d - t / 2)
                p.color = k.col.track
                cv.drawArc(rect, 0f, 360f, false, p)
                if (l != null && k.pct(l) > 0) {
                    p.color = k.color(l)
                    cv.drawArc(rect, -90f, 360f * k.pct(l) / 100f, false, p)
                }
                val big = k.paint(d * 0.27f, k.col.text, k.numFace)
                val ps = if (l == null) "—" else "${k.pct(l)}%"
                val small = d >= 58 * u
                val cy = y + d / 2 + big.textSize * 0.35f - (if (small) d * 0.05f else 0f)
                cv.drawText(ps, cx - big.measureText(ps) / 2, cy, big)
                if (small && l != null) {
                    val wp = k.paint(d * 0.115f, k.col.text2)
                    val ws = k.fit(Fmt.word(k.c, k.used), wp, d - 2 * t - 6 * u)
                    cv.drawText(ws, cx - wp.measureText(ws) / 2, cy + wp.textSize * 1.25f, wp)
                }
                var yy = y + d
                if (k.cfg.labels) {
                    yy += 15 * u
                    k.text(cv, name, k.paint(12 * u, k.col.text, k.sansMed), cx - col / 2, yy, col, true)
                }
                if (l != null) k.reset(l)?.let {
                    k.text(cv, it.substringBefore(" · "), k.paint(10.5f * u, k.col.text2), cx - col / 2, yy + 14 * u, col, true)
                }
            }
        }
    }

    private class Countdown(val k: Ctx) : Item() {
        override fun height(u: Float, w: Float) = 32 * u + (if (k.cfg.labels) 15 * u else 0f)
        override fun draw(cv: Canvas, x: Float, y: Float, w: Float, u: Float) {
            val s = k.u?.session
            val active = s != null && s.resetsAt > k.now
            val big = k.paint(28 * u, k.col.text, k.numFace)
            val t = when {
                active -> Fmt.until(k.c, s!!.resetsAt, k.now)
                s != null -> k.c.getString(R.string.cw_full)
                else -> "—"
            }
            k.text(cv, t, big, x, y + 26 * u, w)
            if (k.cfg.labels) {
                val cap = if (active) k.c.getString(R.string.cw_until, Fmt.clock(s!!.resetsAt)) else k.c.getString(R.string.cw_b_countdown)
                k.text(cv, cap, k.paint(11.5f * u, k.col.text2), x, y + 43 * u, w)
            }
        }
    }

    private class Chart(val k: Ctx) : Item() {
        override fun height(u: Float, w: Float) = 66 * u
        override fun draw(cv: Canvas, x: Float, y: Float, w: Float, u: Float) {
            val days = History.stats(k.c, k.p, k.now).days
            val label = k.paint(9.5f * u, k.col.text2)
            val strong = k.paint(9.5f * u, k.col.text, k.sansMed)
            val bottom = y + 66 * u - 14 * u
            val top = y + 4 * u
            val line = Paint().apply { color = k.col.track; strokeWidth = max(1f, u) }
            cv.drawLine(x, bottom, x + w, bottom, line)
            if (days.isEmpty()) return
            val maxV = max(days.maxOf { it.value }, 0.5)
            val slot = w / days.size
            val bw = min(slot * 0.55f, 18 * u)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            days.forEachIndexed { i, d ->
                val cx = x + slot * i + slot / 2
                val lp = if (d.today) strong else label
                cv.drawText(d.label, cx - lp.measureText(d.label) / 2, y + 64 * u, lp)
                if (d.value <= 0) return@forEachIndexed
                val h = max(((d.value / maxV) * (bottom - top)).toFloat(), 2 * u)
                fill.color = if (d.today) k.col.accent else alpha(k.col.accent, 0x8C)
                val r = min(3 * u, bw / 2)
                cv.drawRoundRect(RectF(cx - bw / 2, bottom - h, cx + bw / 2, bottom), r, r, fill)
            }
        }
    }

    private class Updated(val k: Ctx) : Item() {
        override fun height(u: Float, w: Float) = 13 * u
        override fun draw(cv: Canvas, x: Float, y: Float, w: Float, u: Float) {
            val s = when {
                k.refreshing -> k.c.getString(R.string.updating)
                !k.logged -> ""
                Prefs.lastError(k.c, k.p) != null -> k.c.getString(R.string.w_error)
                k.u != null -> k.c.getString(R.string.cw_updated, Fmt.clock(k.u.updatedAt))
                else -> ""
            }
            k.text(cv, s, k.paint(10.5f * u, k.col.text2), x, y + 10 * u, w)
        }
    }

    private class Message(val k: Ctx, val s: String) : Item() {
        override fun height(u: Float, w: Float) = 18 * u
        override fun draw(cv: Canvas, x: Float, y: Float, w: Float, u: Float) =
            k.text(cv, s, k.paint(13 * u, k.col.text2), x, y + 14 * u, w)
    }

    // ---------- збирання ----------

    private fun items(k: Ctx): List<Item> {
        val on = k.cfg.order.filter { it in k.cfg.on }
        val out = mutableListOf<Item>()
        val limits = setOf(B_SESSION, B_WEEK, B_OPUS, B_SONNET)
        val ready = k.logged && k.u != null
        if (!ready) {
            if (B_HEADER in on) out += Header(k)
            out += Message(k, k.c.getString(if (k.logged) R.string.no_data else R.string.sign_in_app))
            return out
        }
        var i = 0
        while (i < on.size) {
            val b = on[i]
            when {
                b in limits && k.limit(b).first == null && b != B_SESSION && b != B_WEEK -> {} // немає такого ліміту
                b in limits && k.cfg.style == 0 -> {
                    val group = mutableListOf<Int>()
                    while (i < on.size && on[i] in limits && group.size < 4) {
                        if (on[i] == B_SESSION || on[i] == B_WEEK || k.limit(on[i]).first != null) group += on[i]
                        i++
                    }
                    if (group.isNotEmpty()) out += RingRow(k, group)
                    continue
                }
                b in limits && k.cfg.style == 2 -> out += NumberRow(k, b)
                b in limits -> out += BarRow(k, b)
                b == B_HEADER -> out += Header(k)
                b == B_COUNTDOWN -> out += Countdown(k)
                b == B_CHART -> out += Chart(k)
                b == B_UPDATED -> out += Updated(k)
            }
            i++
        }
        return out
    }

    fun render(c: Context, cfg: CustomCfg, wDp: Int, hDp: Int, refreshing: Boolean): Bitmap {
        val dm = c.resources.displayMetrics
        var d = min(dm.density, 2.75f)
        val maxPx = 1_300_000f
        if (wDp * hDp * d * d > maxPx) d = sqrt(maxPx / (wDp * hDp))
        val bw = max((wDp * d).toInt(), 20)
        val bh = max((hDp * d).toInt(), 20)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val p = Provider.of(cfg.provider)
        val col = colors(c, cfg, p)

        // фон
        val r = min(cfg.radius * d, min(bw, bh) / 2f)
        val rect = RectF(0f, 0f, bw.toFloat(), bh.toFloat())
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = col.bg
            if (cfg.gradient) shader = LinearGradient(0f, 0f, bw * 0.4f, bh.toFloat(), col.bg, col.bg2, Shader.TileMode.CLAMP)
            alpha = cfg.opacity.coerceIn(0, 100) * 255 / 100
        }
        cv.drawRoundRect(rect, r, r, bgPaint)
        if (cfg.border) {
            val sw = max(1f, d)
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw; color = alpha(col.text, 0x40) }
            cv.drawRoundRect(RectF(sw / 2, sw / 2, bw - sw / 2, bh - sw / 2), r, r, bp)
        }

        val used = when (cfg.mode) { 1 -> false; 2 -> true; else -> Prefs.showUsed(c) }
        val k = Ctx(c, cfg, p, col, Prefs.loadUsage(c, p), Repo.isLoggedIn(c, p), used, System.currentTimeMillis(), refreshing)
        val list = items(k)
        if (list.isEmpty()) return bmp

        // поля і масштаб так, щоб усе влізло
        val pad = (12 + cfg.radius * 0.18f) * d
        val x = pad
        val w = bw - 2 * pad
        val top = pad - d
        val avail = bh - 2 * pad + 2 * d
        val gap = 9f
        val u0 = d * cfg.scale / 100f
        val minU = d * 0.5f

        fun stack(items: List<Item>, u: Float, cw: Float) =
            if (items.isEmpty()) 0f else items.sumOf { it.height(u, cw).toDouble() }.toFloat() + gap * u * (items.size - 1)

        /** Найбільший масштаб (не більше u0), за якого висота влізає. */
        fun fit(h: (Float) -> Float): Float {
            var u = u0
            repeat(4) { val t = h(u); if (t > avail) u *= avail / t }
            return max(u, minU)
        }

        /** Малює стовпчик: зайве місце — трохи в проміжки, решта — по центру. Те, що не влазить, відкидає. */
        fun column(items: List<Item>, u: Float, cx: Float, cw: Float, y0: Float, room: Float) {
            val shown = items.toMutableList()
            while (stack(shown, u, cw) > room && shown.size > 1) shown.removeAt(shown.size - 1)
            val extra = max(0f, room - stack(shown, u, cw))
            val gapExtra = if (shown.size > 1) min(extra / (shown.size - 1), 10 * u) else 0f
            var y = y0 + (extra - gapExtra * (shown.size - 1)) / 2
            shown.forEach {
                it.draw(cv, cx, y, cw, u)
                y += it.height(u, cw) + gap * u + gapExtra
            }
        }

        val u1 = fit { stack(list, it, w) }

        // широкий віджет і вміст не влазить — розкладаємо у дві колонки (заголовок — на всю ширину)
        val head = list.firstOrNull() as? Header
        val rest = if (head != null) list.drop(1) else list
        val colGap = 16 * d
        val colW = (w - colGap) / 2
        if (u1 < u0 * 0.9f && w > 230 * d && rest.size >= 2) {
            // ділимо так, щоб колонки були якомога рівнішими
            var bestK = 1
            var bestH = Float.MAX_VALUE
            for (k in 1 until rest.size) {
                val h = max(stack(rest.subList(0, k), u0, colW), stack(rest.subList(k, rest.size), u0, colW))
                if (h < bestH) { bestH = h; bestK = k }
            }
            val left = rest.subList(0, bestK)
            val right = rest.subList(bestK, rest.size)
            fun headH(u: Float) = if (head != null) head.height(u, w) + gap * u else 0f
            val u2 = fit { headH(it) + max(stack(left, it, colW), stack(right, it, colW)) }
            if (u2 > u1 * 1.08f) {
                var y = top
                if (head != null) {
                    // невеликий запас зверху/знизу, якщо місця більше, ніж треба
                    val need = headH(u2) + max(stack(left, u2, colW), stack(right, u2, colW))
                    y += max(0f, (avail - need) / 2) * 0.35f
                    head.draw(cv, x, y, w, u2)
                    y += headH(u2)
                }
                val room = top + avail - y - (if (head != null) (y - top - headH(u2)) else 0f)
                column(left, u2, x, colW, y, room)
                column(right, u2, x + colW + colGap, colW, y, room)
                return bmp
            }
        }

        column(list, u1, x, w, top, avail)
        return bmp
    }
}
