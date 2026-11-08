package ua.shavra.claudelimits

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/** Малюнки для віджетів (RemoteViews не вміють власні View, тож малюємо в Bitmap). */
object WidgetArt {

    fun ring(sizePx: Int, value: Float, color: Int, track: Int): Bitmap {
        val b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val sw = sizePx * 0.09f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = sw
        }
        val r = RectF(sw / 2, sw / 2, sizePx - sw / 2, sizePx - sw / 2)
        p.color = track
        c.drawArc(r, 0f, 360f, false, p)
        if (value > 0.001f) {
            p.color = color
            c.drawArc(r, -90f, 360f * value.coerceIn(0f, 1f), false, p)
        }
        return b
    }

    /** Стовпчики по днях; підписано лише сьогоднішнє значення. */
    fun bars(wPx: Int, hPx: Int, density: Float, days: List<DayBar>, pal: Pal): Bitmap {
        val b = Bitmap.createBitmap(max(wPx, 10), max(hPx, 10), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.text2; textSize = 10 * density }
        val strong = Paint(label).apply { color = pal.text; typeface = Typeface.DEFAULT_BOLD }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accent }
        val base = Paint().apply { color = pal.track; strokeWidth = density }

        val top = 14 * density
        val bottom = b.height - 14 * density
        c.drawLine(0f, bottom, b.width.toFloat(), bottom, base)
        if (days.isEmpty()) return b
        val maxV = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0)
            .firstOrNull { it >= days.maxOf { d -> d.value } } ?: 100.0
        val slot = b.width.toFloat() / days.size
        val barW = min(slot * 0.55f, 22 * density)
        val rr = 3 * density
        val path = Path()
        val rect = RectF()
        days.forEachIndexed { i, d ->
            val cx = slot * i + slot / 2
            val lp = if (d.today) strong else label
            c.drawText(d.label, cx - lp.measureText(d.label) / 2, b.height - 2 * density, lp)
            if (d.value <= 0) return@forEachIndexed
            val h = max(((d.value / maxV) * (bottom - top)).toFloat(), 2 * density)
            rect.set(cx - barW / 2, bottom - h, cx + barW / 2, bottom)
            val r = min(rr, h / 2)
            path.reset()
            path.addRoundRect(rect, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
            c.drawPath(path, fill)
            if (d.today) {
                val s = pctText(d.value)
                c.drawText(s, (cx - strong.measureText(s) / 2).coerceIn(0f, b.width - strong.measureText(s)), bottom - h - 3 * density, strong)
            }
        }
        return b
    }
}
