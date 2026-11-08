package ua.shavra.claudelimits

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun pctText(v: Double): String =
    if (v < 10 && v > 0) String.format(Locale.getDefault(), "%.1f%%", v) else "${v.toInt()}%"

/** Спільні фарби та розміри для графіків. */
abstract class ChartBase(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    protected val dp = resources.displayMetrics.density
    protected val accent = Ui.color(context, R.attr.cAccent)
    protected val surface = Ui.color(context, R.attr.cSurface)

    protected val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.color(context, R.attr.cDivider); strokeWidth = 1 * dp
    }
    protected val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.color(context, R.attr.cText2); textSize = 11 * resources.displayMetrics.scaledDensity
    }
    protected val labelStrong = Paint(label).apply {
        color = Ui.color(context, R.attr.cText); typeface = Typeface.DEFAULT_BOLD
    }
    protected val tipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.color(context, R.attr.cText) }
    protected val tipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = surface; textSize = 12 * resources.displayMetrics.scaledDensity; typeface = Typeface.DEFAULT_BOLD
    }
    private val tipRect = RectF()

    /** Підказка над точкою (cx, bottomY), не виходить за краї. */
    protected fun drawTip(c: Canvas, text: String, cx: Float, bottomY: Float) {
        val padH = 8 * dp
        val w = tipText.measureText(text) + padH * 2
        val h = 22 * dp
        val left = (cx - w / 2).coerceIn(0f, width - w)
        val top = max(0f, bottomY - h)
        tipRect.set(left, top, left + w, top + h)
        c.drawRoundRect(tipRect, 6 * dp, 6 * dp, tipBg)
        c.drawText(text, left + padH, top + h / 2 - (tipText.descent() + tipText.ascent()) / 2, tipText)
    }

    protected fun drawEmpty(c: Canvas, text: String) {
        val w = label.measureText(text)
        c.drawText(text, (width - w) / 2, height / 2f, label)
    }
}

/** Стовпчики: витрата тижневого ліміту по днях. Натискання показує значення. */
class BarChartView(context: Context, attrs: AttributeSet?) : ChartBase(context, attrs) {
    private var bars: List<DayBar> = emptyList()
    private var sel = -1
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val path = Path()
    private val rect = RectF()

    fun setData(b: List<DayBar>) {
        bars = b
        sel = b.indexOfLast { it.value > 0 }.takeIf { it >= 0 } ?: -1
        invalidate()
    }

    private val left get() = 34 * dp
    private val top get() = 28 * dp
    private val bottom get() = height - 22 * dp

    private fun niceMax(v: Double): Double =
        listOf(1.0, 2.0, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0).firstOrNull { it >= v } ?: 100.0

    override fun onDraw(c: Canvas) {
        if (bars.isEmpty()) return
        val maxV = niceMax(bars.maxOf { it.value })
        val w = width - left
        val slot = w / bars.size
        val barW = min(slot * 0.56f, 28 * dp)
        val r = 4 * dp

        // сітка: основа і верх шкали
        c.drawLine(left, bottom, width.toFloat(), bottom, grid)
        c.drawLine(left, top, width.toFloat(), top, grid)
        c.drawText(pctText(maxV), 0f, top + label.textSize / 3, label)
        c.drawText("0", 0f, bottom + label.textSize / 3, label)

        if (bars.all { it.value <= 0 }) {
            drawEmpty(c, context.getString(R.string.chart_empty_bars))
        }

        bars.forEachIndexed { i, b ->
            val cx = left + slot * i + slot / 2
            // підпис дня
            val p = if (b.today) labelStrong else label
            c.drawText(b.label, cx - p.measureText(b.label) / 2, height - 6 * dp, p)
            if (b.value <= 0) return@forEachIndexed
            val bh = max(((b.value / maxV) * (bottom - top)).toFloat(), 2 * dp)
            rect.set(cx - barW / 2, bottom - bh, cx + barW / 2, bottom)
            val rr = min(r, bh / 2)
            path.reset()
            path.addRoundRect(rect, floatArrayOf(rr, rr, rr, rr, 0f, 0f, 0f, 0f), Path.Direction.CW)
            c.drawPath(path, fill)
        }

        if (sel in bars.indices && bars[sel].value > 0) {
            val b = bars[sel]
            val cx = left + slot * sel + slot / 2
            val bh = max(((b.value / maxV) * (bottom - top)).toFloat(), 2 * dp)
            drawTip(c, "${b.label}: ${pctText(b.value)}", cx, bottom - bh - 4 * dp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bars.isEmpty()) return false
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
            val slot = (width - left) / bars.size
            sel = ((e.x - left) / slot).toInt().coerceIn(0, bars.size - 1)
            invalidate()
            return true
        }
        if (e.action == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

/** Лінія: використання сесії за останні 24 години. Дотик показує значення. */
class LineChartView(context: Context, attrs: AttributeSet?) : ChartBase(context, attrs) {
    private var pts: List<Pair<Long, Double>> = emptyList()
    private var from = 0L
    private var to = 1L
    private var sel = -1

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; style = Paint.Style.STROKE; strokeWidth = 2 * dp
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val area = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; alpha = 36 }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surface }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.color(context, R.attr.cText2); strokeWidth = 1 * dp
    }
    private val p = Path()
    private val a = Path()

    fun setData(points: List<Pair<Long, Double>>, from: Long, to: Long) {
        pts = points; this.from = from; this.to = to; sel = -1
        invalidate()
    }

    private val left get() = 34 * dp
    private val top get() = 28 * dp
    private val bottom get() = height - 22 * dp

    private fun x(t: Long) = left + (width - left - 4 * dp) * ((t - from).toFloat() / (to - from))
    private fun y(v: Double) = bottom - (bottom - top) * (v / 100.0).toFloat()

    override fun onDraw(c: Canvas) {
        // сітка 0 / 50 / 100
        for (v in listOf(0.0, 50.0, 100.0)) {
            c.drawLine(left, y(v), width.toFloat(), y(v), grid)
            c.drawText(if (v == 0.0) "0" else "${v.toInt()}%", 0f, y(v) + label.textSize / 3, label)
        }
        // підписи часу
        for (i in 0..4) {
            val t = from + (to - from) * i / 4
            val s = if (i == 4) context.getString(R.string.now) else Fmt.clock(t)
            val tw = label.measureText(s)
            val xx = when (i) { 0 -> left; 4 -> width - tw; else -> x(t) - tw / 2 }
            c.drawText(s, xx, height - 6 * dp, label)
        }
        if (pts.size < 2) {
            drawEmpty(c, context.getString(R.string.chart_empty_line))
            return
        }

        // лінія з розривами там, де довго не було даних
        p.reset(); a.reset()
        var segStart = -1
        fun closeArea(endIdx: Int) {
            if (segStart < 0) return
            a.lineTo(x(pts[endIdx].first), bottom)
            a.lineTo(x(pts[segStart].first), bottom)
            a.close()
        }
        pts.forEachIndexed { i, (t, v) ->
            val gap = i > 0 && t - pts[i - 1].first > 90 * 60_000
            if (i == 0 || gap) {
                if (gap) closeArea(i - 1)
                p.moveTo(x(t), y(v)); a.moveTo(x(t), y(v)); segStart = i
            } else {
                p.lineTo(x(t), y(v)); a.lineTo(x(t), y(v))
            }
        }
        closeArea(pts.size - 1)
        c.drawPath(a, area)
        c.drawPath(p, line)

        if (sel in pts.indices) {
            val (t, v) = pts[sel]
            val cx = x(t); val cy = y(v)
            c.drawLine(cx, top, cx, bottom, cross)
            c.drawCircle(cx, cy, 6 * dp, ring)
            c.drawCircle(cx, cy, 4 * dp, dot)
            drawTip(c, "${Fmt.clock(t)} · ${pctText(v)}", cx, top - 4 * dp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (pts.size < 2) return false
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                sel = pts.indices.minByOrNull { abs(x(pts[it].first) - e.x) } ?: -1
                invalidate()
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

/** Смуга ліміту з міткою «скільки часу минуло». */
class PaceBarView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val dp = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.color(context, R.attr.cTrack) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.color(context, R.attr.cAccent) }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.color(context, R.attr.cText); strokeWidth = 2 * dp; strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private var value = 0f
    private var marker = -1f

    fun set(value: Float, marker: Float, color: Int) {
        this.value = value.coerceIn(0f, 1f)
        this.marker = marker
        fill.color = color
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val bh = 8 * dp
        val top = (height - bh) / 2
        val r = bh / 2
        rect.set(0f, top, width.toFloat(), top + bh)
        c.drawRoundRect(rect, r, r, track)
        if (value > 0) {
            rect.set(0f, top, max(width * value, bh), top + bh)
            c.drawRoundRect(rect, r, r, fill)
        }
        if (marker in 0f..1f) {
            val x = (width * marker).coerceIn(dp, width - dp)
            c.drawLine(x, 1 * dp, x, height - 1 * dp, mark)
        }
    }
}
