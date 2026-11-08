package ua.shavra.claudelimits

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/** Кільцевий індикатор для поточної сесії. */
class RingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Ui.color(context, R.attr.cTrack)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Ui.color(context, R.attr.cAccent)
    }
    private val rect = RectF()
    private var progress = 0f
    private var anim: ValueAnimator? = null

    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Ui.color(context, R.attr.cText)
    }

    var color: Int
        get() = arc.color
        set(v) { arc.color = v; invalidate() }

    /** Мітка «скільки часу минуло» (0..1), -1 — немає. */
    var marker: Float = -1f
        set(v) { field = v; invalidate() }

    fun setProgress(p: Float, animate: Boolean) {
        anim?.cancel()
        val target = p.coerceIn(0f, 1f)
        if (!animate) {
            progress = target
            invalidate()
            return
        }
        anim = ValueAnimator.ofFloat(progress, target).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val sw = size * 0.075f
        track.strokeWidth = sw
        arc.strokeWidth = sw
        val r = size / 2f - sw / 2f
        val cx = width / 2f
        val cy = height / 2f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, 0f, 360f, false, track)
        if (progress > 0.001f) canvas.drawArc(rect, -90f, 360f * progress, false, arc)
        if (marker in 0f..1f) {
            val a = Math.toRadians((-90 + 360 * marker).toDouble())
            val r1 = r - sw / 2 - sw * 0.25f
            val r2 = r + sw / 2 + sw * 0.25f
            mark.strokeWidth = sw * 0.22f
            canvas.drawLine(
                cx + r1 * Math.cos(a).toFloat(), cy + r1 * Math.sin(a).toFloat(),
                cx + r2 * Math.cos(a).toFloat(), cy + r2 * Math.sin(a).toFloat(), mark,
            )
        }
    }
}
