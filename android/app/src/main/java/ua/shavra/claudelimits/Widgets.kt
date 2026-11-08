package ua.shavra.claudelimits

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlin.math.min

/** Спільна логіка всіх віджетів. */
abstract class BaseWidget : AppWidgetProvider() {
    abstract val kind: WidgetKind

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> runCatching { mgr.updateAppWidget(id, Widgets.build(context, kind, id)) } }
        if (Repo.isStale(context, 10)) {
            val pending = goAsync()
            Repo.refresh(context) { pending.finish() }
        }
    }

    /** Розмір віджета змінили — перемальовуємо під новий розмір. */
    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        runCatching { mgr.updateAppWidget(id, Widgets.build(context, kind, id)) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.remove(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Widgets.ACTION_REFRESH) return
        Widgets.updateAll(context, refreshing = true)
        val pending = goAsync()
        Repo.refresh(context) { pending.finish() }
    }
}

/** «Повний». Назва класу збережена, щоб уже розміщений віджет не зник. */
class UsageWidget : BaseWidget() { override val kind get() = WidgetKind.FULL }
class RingWidget : BaseWidget() { override val kind get() = WidgetKind.RING }
class DualWidget : BaseWidget() { override val kind get() = WidgetKind.DUAL }
class MiniWidget : BaseWidget() { override val kind get() = WidgetKind.MINI }
class TimerWidget : BaseWidget() { override val kind get() = WidgetKind.TIMER }
class StripWidget : BaseWidget() { override val kind get() = WidgetKind.STRIP }
class StatsWidget : BaseWidget() { override val kind get() = WidgetKind.STATS }
class OverviewWidget : BaseWidget() { override val kind get() = WidgetKind.OVERVIEW }

object Widgets {
    const val ACTION_REFRESH = "ua.shavra.claudelimits.WIDGET_REFRESH"

    private val BG_AUTO = intArrayOf(R.drawable.widget_bg_r0, R.drawable.widget_bg_r12, R.drawable.widget_bg, R.drawable.widget_bg_r36)
    private val BG_WHITE = intArrayOf(R.drawable.widget_bg_white_r0, R.drawable.widget_bg_white_r12, R.drawable.widget_bg_white, R.drawable.widget_bg_white_r36)
    private val SCALE = floatArrayOf(0.85f, 1f, 1.2f)

    fun ids(c: Context, k: WidgetKind): IntArray = runCatching {
        AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, k.cls))
    }.getOrDefault(IntArray(0))

    fun placed(c: Context): List<Pair<WidgetKind, Int>> =
        WidgetKind.values().flatMap { k -> ids(c, k).map { k to it } }

    fun updateAll(c: Context, refreshing: Boolean = false) {
        val m = AppWidgetManager.getInstance(c)
        placed(c).forEach { (k, id) -> runCatching { m.updateAppWidget(id, build(c, k, id, refreshing)) } }
    }

    /** Розмір віджета в dp (портретна орієнтація), або типовий для виду. */
    fun size(c: Context, kind: WidgetKind, id: Int): Pair<Int, Int> {
        val o = runCatching { AppWidgetManager.getInstance(c).getAppWidgetOptions(id) }.getOrNull()
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 } ?: kind.previewW
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)?.takeIf { it > 0 } ?: kind.previewH
        return w to h
    }

    /** Усе, що потрібно для малювання одного віджета. */
    private class W(
        val c: Context, val cfg: WidgetCfg, val p: Provider, val pal: Pal, val now: Long,
        val u: Usage?, val logged: Boolean, val used: Boolean, val refreshing: Boolean,
        val wDp: Int, val hDp: Int,
    ) {
        val scale = SCALE[cfg.textSize.coerceIn(0, 2)]
        val density get() = c.resources.displayMetrics.density
        val word get() = Fmt.word(c, used)
        fun s(id: Int, vararg a: Any) = c.getString(id, *a)
        fun pct(l: Limit) = if (used) l.used(now) else 100 - l.used(now)
        fun color(l: Limit) = if (!pal.onAccent && 100 - l.used(now) <= 15) pal.danger else pal.accent
        fun empty() = s(if (logged) R.string.no_data else R.string.sign_in_app)
        fun updated(): String = when {
            refreshing -> s(R.string.updating)
            !logged -> ""
            Prefs.lastError(c, p) != null -> s(R.string.w_error)
            u != null -> Fmt.clock(u.updatedAt)
            else -> ""
        }
        /** Ліміт за індексом (0 сесія, 1 тиждень, 2 Opus, 3 Sonnet) та його назва. */
        fun limit(idx: Int): Pair<Limit?, String> {
            val claude = p == Provider.CLAUDE
            val i = idx.coerceIn(0, if (claude) 3 else 1)
            val names = listOf(s(if (claude) R.string.session else R.string.h5), s(R.string.week), "Opus", "Sonnet")
            return listOf(u?.session, u?.week, u?.opus, u?.sonnet)[i] to names[i]
        }
    }

    fun build(c0: Context, kind: WidgetKind, id: Int, refreshing: Boolean = false, override: WidgetCfg? = null, custom: CustomCfg? = null): RemoteViews {
        if (kind == WidgetKind.CUSTOM) return CustomWidgets.build(c0, id, refreshing, custom ?: override?.let { CustomCfg(provider = it.provider) })
        val c = Lang.wrap(c0)
        val cfg = override ?: WidgetPrefs.get(c, id)
        val p = Provider.of(cfg.provider)
        val (wDp, hDp) = size(c, kind, id)
        val w = W(
            c, cfg, p, palette(c, cfg.theme, p, cfg.accent), System.currentTimeMillis(),
            Prefs.loadUsage(c, p), Repo.isLoggedIn(c, p),
            when (cfg.mode) { 1 -> false; 2 -> true; else -> Prefs.showUsed(c) },
            refreshing, wDp, hDp,
        )
        val v = when (kind) {
            WidgetKind.FULL -> full(w)
            WidgetKind.RING -> ring(w)
            WidgetKind.DUAL -> dual(w)
            WidgetKind.MINI -> mini(w)
            WidgetKind.TIMER -> timer(w)
            WidgetKind.STRIP -> strip(w)
            WidgetKind.STATS -> stats(w)
            WidgetKind.OVERVIEW -> overview(w)
            WidgetKind.CUSTOM -> error("custom")
        }

        // фон: тема, кути, прозорість
        val corner = cfg.corners.coerceIn(0, 3)
        if (w.pal.auto) {
            v.setImageViewResource(R.id.w_bg, BG_AUTO[corner])
        } else {
            v.setImageViewResource(R.id.w_bg, BG_WHITE[corner])
            v.setInt(R.id.w_bg, "setColorFilter", w.pal.bg)
        }
        v.setInt(R.id.w_bg, "setImageAlpha", cfg.opacity * 255 / 100)
        if (kind != WidgetKind.OVERVIEW) v.setImageViewResource(R.id.w_icon, p.icon)
        v.setInt(R.id.w_icon, "setColorFilter", w.pal.accent)

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = Intent(c, MainActivity::class.java).putExtra(MainActivity.EXTRA_PROVIDER, p.id)
        v.setOnClickPendingIntent(android.R.id.background, PendingIntent.getActivity(c, 100 + p.ordinal, open, flags))
        if (kind in listOf(WidgetKind.FULL, WidgetKind.STATS, WidgetKind.DUAL, WidgetKind.OVERVIEW)) {
            val r = Intent(c, kind.cls).setAction(ACTION_REFRESH)
            v.setOnClickPendingIntent(R.id.w_refresh, PendingIntent.getBroadcast(c, 200 + kind.ordinal, r, flags))
            if (!w.pal.auto) v.setInt(R.id.w_refresh, "setColorFilter", w.pal.text2)
        }
        return v
    }

    // ===================== види =====================

    private fun full(w: W): RemoteViews {
        val v = RemoteViews(w.c.packageName, R.layout.widget_usage)
        val compact = w.hDp < 120
        header(v, w, w.p.title, "· " + w.word, w.hDp >= 90)
        v.setTextViewText(R.id.w_s_label, w.s(if (w.p == Provider.CLAUDE) R.string.w_session_5h else R.string.w_limit_5h))
        v.setTextViewText(R.id.w_w_label, w.s(R.string.week))
        row(v, w, w.u?.session, R.id.w_s_pct, R.id.w_s_bar, R.id.w_s_sub, !compact)
        row(v, w, w.u?.week, R.id.w_w_pct, R.id.w_w_bar, R.id.w_w_sub, !compact)
        sizes(v, w, R.id.w_title to 15f, R.id.w_mode to 12f, R.id.w_updated to 11f,
            R.id.w_s_label to 13f, R.id.w_s_pct to 13f, R.id.w_w_label to 13f, R.id.w_w_pct to 13f,
            R.id.w_s_sub to 11f, R.id.w_w_sub to 11f)
        paint(v, w,
            intArrayOf(R.id.w_title, R.id.w_s_label, R.id.w_s_pct, R.id.w_w_label, R.id.w_w_pct),
            intArrayOf(R.id.w_mode, R.id.w_updated, R.id.w_s_sub, R.id.w_w_sub))
        return v
    }

    private fun ring(w: W): RemoteViews {
        val v = RemoteViews(w.c.packageName, R.layout.widget_ring)
        val (l, name) = w.limit(w.cfg.limit)
        val showLabel = w.cfg.showHeader && w.hDp >= 90
        val ringDp = min(w.wDp - 16, w.hDp - (if (showLabel) 34 else 16)).coerceIn(44, 200)
        ringInto(v, w, l, R.id.w_ring, R.id.w_pct, R.id.w_word, ringDp)
        v.setViewVisibility(R.id.w_header, vis(showLabel))
        val reset = if (w.cfg.showReset && l != null && l.resetsAt > w.now) " · " + Fmt.resetShort(w.c, l.resetsAt, w.now) else ""
        v.setTextViewText(R.id.w_label, name + reset)
        sizes(v, w, R.id.w_label to 11f)
        paint(v, w, intArrayOf(R.id.w_pct), intArrayOf(R.id.w_word, R.id.w_label))
        return v
    }

    private fun dual(w: W): RemoteViews {
        val v = RemoteViews(w.c.packageName, R.layout.widget_dual)
        val showHeader = w.cfg.showHeader && w.hDp >= 110
        header(v, w, w.p.title, "", showHeader)
        val ringDp = min((w.wDp - 48) / 2, w.hDp - (if (showHeader) 64 else 34)).coerceIn(40, 160)
        val (s, sName) = w.limit(0)
        val (wk, wName) = w.limit(1)
        ringInto(v, w, s, R.id.w_ring1, R.id.w_pct1, R.id.w_word1, ringDp)
        ringInto(v, w, wk, R.id.w_ring2, R.id.w_pct2, R.id.w_word2, ringDp)
        fun label(l: Limit?, n: String) =
            if (w.cfg.showReset && l != null && l.resetsAt > w.now) "$n · ${Fmt.resetShort(w.c, l.resetsAt, w.now)}" else n
        v.setTextViewText(R.id.w_label1, label(s, sName))
        v.setTextViewText(R.id.w_label2, label(wk, wName))
        sizes(v, w, R.id.w_title to 15f, R.id.w_updated to 11f, R.id.w_label1 to 11f, R.id.w_label2 to 11f)
        paint(v, w, intArrayOf(R.id.w_title, R.id.w_pct1, R.id.w_pct2), intArrayOf(R.id.w_updated, R.id.w_word1, R.id.w_word2, R.id.w_label1, R.id.w_label2))
        return v
    }

    private fun mini(w: W): RemoteViews {
        val v = RemoteViews(w.c.packageName, R.layout.widget_mini)
        val (l, name) = w.limit(w.cfg.limit)
        val side = min(w.wDp, w.hDp)
        v.setTextViewText(R.id.w_pct, l?.let { "${w.pct(it)}%" } ?: "—")
        v.setTextViewTextSize(R.id.w_pct, TypedValue.COMPLEX_UNIT_SP, (side * 0.30f).coerceIn(16f, 64f) * w.scale)
        v.setTextViewText(R.id.w_label, if (side >= 90) "$name · ${w.word}" else name)
        v.setViewVisibility(R.id.w_label, vis(w.cfg.showHeader))
        v.setViewVisibility(R.id.w_icon, vis(side >= 70))
        if (l != null && !w.pal.onAccent && 100 - l.used(w.now) <= 15) v.setTextColor(R.id.w_pct, w.pal.danger)
        else if (!w.pal.auto) v.setTextColor(R.id.w_pct, w.pal.text)
        sizes(v, w, R.id.w_label to 10f)
        paint(v, w, intArrayOf(), intArrayOf(R.id.w_label))
        return v
    }

    private fun timer(w: W): RemoteViews {
        val v = RemoteViews(w.c.packageName, R.layout.widget_timer)
        val idx = w.cfg.limit.coerceIn(0, 1)
        val (l, name) = w.limit(idx)
        val active = l != null && l.resetsAt > w.now
        v.setTextViewText(R.id.w_label, if (idx == 0) w.s(R.string.timer_left) else "$name · ${w.s(R.string.until_reset_lbl)}")
        v.setViewVisibility(R.id.w_header, vis(w.cfg.showHeader))
        v.setTextViewText(R.id.w_time, if (active) Fmt.until(w.c, l!!.resetsAt, w.now) else if (l != null) w.s(R.string.full_limit) else w.empty())
        v.setTextViewTextSize(R.id.w_time, TypedValue.COMPLEX_UNIT_SP, min(w.wDp * 0.13f, w.hDp * 0.30f).coerceIn(15f, 44f) * w.scale)
        v.setTextViewText(R.id.w_pct, l?.let { if (w.wDp >= 240) "${w.pct(it)}% ${w.word}" else "${w.pct(it)}%" } ?: "—")
        bar(v, w, R.id.w_bar, l?.let { w.pct(it) } ?: 0, l?.let { w.color(it) } ?: w.pal.accent)
        v.setViewVisibility(R.id.w_bar, vis(w.hDp >= 70))
        v.setTextViewText(R.id.w_sub, if (active) Fmt.resetLine(w.c, l!!, w.now) else "")
        v.setViewVisibility(R.id.w_sub, vis(w.cfg.showReset && active && w.hDp >= 100))
        sizes(v, w, R.id.w_label to 11f, R.id.w_pct to 12f, R.id.w_sub to 11f)
        paint(v, w, intArrayOf(R.id.w_time, R.id.w_pct), intArrayOf(R.id.w_label, R.id.w_sub))
        return v
    }

    private fun strip(w: W): RemoteViews {
        val v = RemoteViews(w.c.packageName, R.layout.widget_strip)
        fun part(l: Limit?, name: String, label: Int, pct: Int, barId: Int) {
            val reset = if (w.cfg.showReset && w.wDp >= 260 && l != null && l.resetsAt > w.now)
                " · " + Fmt.resetShort(w.c, l.resetsAt, w.now) else ""
            v.setTextViewText(label, name + reset)
            v.setTextViewText(pct, l?.let { "${w.pct(it)}%" } ?: "—")
            bar(v, w, barId, l?.let { w.pct(it) } ?: 0, l?.let { w.color(it) } ?: w.pal.accent)
        }
        val (s, sName) = w.limit(0)
        val (wk, wName) = w.limit(1)
        part(s, sName, R.id.w_s_label, R.id.w_s_pct, R.id.w_s_bar)
        part(wk, wName, R.id.w_w_label, R.id.w_w_pct, R.id.w_w_bar)
        v.setViewVisibility(R.id.w_icon, vis(w.cfg.showHeader && w.wDp >= 200))
        sizes(v, w, R.id.w_s_label to 11f, R.id.w_w_label to 11f, R.id.w_s_pct to 13f, R.id.w_w_pct to 13f)
        paint(v, w, intArrayOf(R.id.w_s_pct, R.id.w_w_pct), intArrayOf(R.id.w_s_label, R.id.w_w_label))
        return v
    }

    private fun stats(w: W): RemoteViews {
        val v = RemoteViews(w.c.packageName, R.layout.widget_stats)
        val st = History.stats(w.c, w.p, w.now)
        val showHeader = w.cfg.showHeader && w.hDp >= 100
        header(v, w, w.p.title, w.s(R.string.w_stats), showHeader)
        v.setTextViewText(R.id.w_today, pctText(st.days.lastOrNull()?.value ?: 0.0))
        v.setTextViewText(R.id.w_today_l, w.s(R.string.w_today_l))
        v.setTextViewText(R.id.w_sess, w.u?.session?.let { "${w.pct(it)}%" } ?: "—")
        v.setTextViewText(R.id.w_sess_l, w.s(R.string.w_sess_l, w.word))
        v.setTextViewText(R.id.w_week, w.u?.week?.let { "${w.pct(it)}%" } ?: "—")
        v.setTextViewText(R.id.w_week_l, w.s(R.string.w_week_l, w.word))
        val showRow = w.hDp >= 140
        v.setViewVisibility(R.id.w_stats_row, vis(showRow))
        val used = (if (showHeader) 40 else 10) + (if (showRow) 44 else 0) + 20
        val d = w.density
        val cw = ((w.wDp - 28).coerceIn(100, 520) * d).toInt()
        val ch = ((w.hDp - used).coerceIn(36, 260) * d).toInt()
        v.setImageViewBitmap(R.id.w_chart, WidgetArt.bars(cw, ch, d, st.days, w.pal))
        sizes(v, w, R.id.w_title to 15f, R.id.w_mode to 12f, R.id.w_today to 18f, R.id.w_sess to 18f, R.id.w_week to 18f,
            R.id.w_today_l to 10f, R.id.w_sess_l to 10f, R.id.w_week_l to 10f)
        paint(v, w,
            intArrayOf(R.id.w_title, R.id.w_today, R.id.w_sess, R.id.w_week),
            intArrayOf(R.id.w_mode, R.id.w_updated, R.id.w_today_l, R.id.w_sess_l, R.id.w_week_l))
        return v
    }

    /** Усі підключені сервіси одразу (до двох рядків). */
    private fun overview(w: W): RemoteViews {
        val v = RemoteViews(w.c.packageName, R.layout.widget_overview)
        header(v, w, w.s(R.string.app_name), "", w.cfg.showHeader && w.hDp >= 100)
        v.setImageViewResource(R.id.w_icon, R.drawable.ic_spark)
        val list = Repo.active(w.c).ifEmpty { listOf(Provider.CLAUDE) }.take(2)
        val rows = listOf(
            intArrayOf(R.id.w_r1, R.id.w_r1_icon, R.id.w_r1_name, R.id.w_r1_pct, R.id.w_r1_bar, R.id.w_r1_sub),
            intArrayOf(R.id.w_r2, R.id.w_r2_icon, R.id.w_r2_name, R.id.w_r2_pct, R.id.w_r2_bar, R.id.w_r2_sub),
        )
        rows.forEachIndexed { i, r ->
            val p = list.getOrNull(i)
            v.setViewVisibility(r[0], vis(p != null))
            if (p == null) return@forEachIndexed
            val u = Prefs.loadUsage(w.c, p)
            val pal = palette(w.c, w.cfg.theme, p, w.cfg.accent)
            v.setImageViewResource(r[1], p.icon)
            v.setInt(r[1], "setColorFilter", pal.accent)
            v.setTextViewText(r[2], p.title)
            val s = u?.session
            v.setTextViewText(r[3], s?.let { "${w.pct(it)}% ${w.word}" } ?: "—")
            bar(v, w, r[4], s?.let { w.pct(it) } ?: 0, s?.let { if (100 - it.used(w.now) <= 15 && !pal.onAccent) pal.danger else pal.accent } ?: pal.accent)
            val wk = u?.week
            val sub = buildString {
                append(w.s(R.string.week)).append(" ").append(wk?.let { "${w.pct(it)}%" } ?: "—")
                if (w.cfg.showReset && s != null && s.resetsAt > w.now) append(" · ").append(Fmt.tileReset(w.c, s, w.now))
            }
            v.setTextViewText(r[5], sub)
            v.setViewVisibility(r[5], vis(w.hDp >= 110))
            sizes(v, w, r[2] to 13f, r[3] to 13f, r[5] to 11f)
            paint(v, w, intArrayOf(r[2], r[3]), intArrayOf(r[5]))
        }
        sizes(v, w, R.id.w_title to 15f)
        paint(v, w, intArrayOf(R.id.w_title), intArrayOf(R.id.w_updated))
        return v
    }

    // ===================== допоміжне =====================

    private fun header(v: RemoteViews, w: W, title: String, mode: String, show: Boolean) {
        v.setViewVisibility(R.id.w_header, vis(w.cfg.showHeader && show))
        v.setTextViewText(R.id.w_title, title)
        if (mode.isNotEmpty()) v.setTextViewText(R.id.w_mode, if (w.wDp >= 220) mode else "")
        v.setTextViewText(R.id.w_updated, if (w.wDp >= 200) w.updated() else "")
    }

    private fun row(v: RemoteViews, w: W, l: Limit?, pct: Int, barId: Int, sub: Int, roomForSub: Boolean) {
        if (l == null) {
            v.setTextViewText(pct, "—")
            bar(v, w, barId, 0, w.pal.accent)
            v.setTextViewText(sub, w.empty())
        } else {
            val p = w.pct(l)
            v.setTextViewText(pct, "$p%")
            bar(v, w, barId, p, w.color(l))
            v.setTextViewText(sub, Fmt.resetLine(w.c, l, w.now))
        }
        v.setViewVisibility(sub, vis(roomForSub && (w.cfg.showReset || l == null)))
    }

    private fun ringInto(v: RemoteViews, w: W, l: Limit?, ringId: Int, pctId: Int, wordId: Int, ringDp: Int) {
        val value = l?.let { w.pct(it) }
        v.setImageViewBitmap(
            ringId,
            WidgetArt.ring((ringDp * w.density).toInt(), (value ?: 0) / 100f, l?.let { w.color(it) } ?: w.pal.accent, w.pal.track),
        )
        v.setTextViewText(pctId, value?.let { "$it%" } ?: "—")
        v.setTextViewTextSize(pctId, TypedValue.COMPLEX_UNIT_SP, ringDp * 0.25f * w.scale)
        v.setTextViewText(wordId, if (l == null) w.empty() else w.word)
        v.setTextViewTextSize(wordId, TypedValue.COMPLEX_UNIT_SP, (ringDp * 0.1f).coerceIn(7f, 13f) * w.scale)
        v.setViewVisibility(wordId, vis(ringDp >= 60))
    }

    private fun sizes(v: RemoteViews, w: W, vararg ids: Pair<Int, Float>) {
        if (w.scale == 1f) return
        ids.forEach { (id, sp) -> v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * w.scale) }
    }

    private fun paint(v: RemoteViews, w: W, primary: IntArray, secondary: IntArray) {
        if (w.pal.auto) return
        primary.forEach { v.setTextColor(it, w.pal.text) }
        secondary.forEach { v.setTextColor(it, w.pal.text2) }
    }

    private fun bar(v: RemoteViews, w: W, id: Int, value: Int, color: Int) {
        v.setProgressBar(id, 100, value, false)
        if (Build.VERSION.SDK_INT >= 31) {
            v.setColorStateList(id, "setProgressTintList", ColorStateList.valueOf(color))
            if (!w.pal.auto) v.setColorStateList(id, "setProgressBackgroundTintList", ColorStateList.valueOf(w.pal.track))
        }
    }

    private fun vis(on: Boolean) = if (on) View.VISIBLE else View.GONE
}
