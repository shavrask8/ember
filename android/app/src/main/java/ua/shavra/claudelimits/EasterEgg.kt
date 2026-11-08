package ua.shavra.claudelimits

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 🤫 Секретне повідомлення: вибух іскор, картка й печатка. */
object EasterEgg {

    fun show(a: Activity) {
        val d = { v: Int -> Ui.dp(a, v) }
        val accent = Ui.color(a, R.attr.cAccent)
        val text = Ui.color(a, R.attr.cText)
        val text2 = Ui.color(a, R.attr.cText2)
        val surface = Ui.color(a, R.attr.cSurface)
        val divider = Ui.color(a, R.attr.cDivider)
        val serif = Typeface.create(titleFont(a), Typeface.BOLD)

        val dialog = Dialog(a, android.R.style.Theme_Translucent_NoTitleBar)
        val root = FrameLayout(a).apply { setBackgroundColor(Color.TRANSPARENT) }

        // затемнення
        val dim = View(a).apply { setBackgroundColor(0xB3000000.toInt()); alpha = 0f }
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))

        // іскри
        val confetti = Confetti(a, intArrayOf(accent, 0xFFFAF9F5.toInt(), 0xFFE0A43B.toInt(), 0xFFE8A08A.toInt(), 0xFFB5463C.toInt()))
        root.addView(confetti, FrameLayout.LayoutParams(-1, -1))

        // картка
        val card = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(d(28), d(30), d(28), d(24))
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = d(32).toFloat()
                setStroke(d(1), divider)
            }
            elevation = d(24).toFloat()
            alpha = 0f
            scaleX = 0.7f
            scaleY = 0.7f
        }
        val spark = ImageView(a).apply {
            setImageResource(R.drawable.ic_spark)
            setColorFilter(accent)
        }
        card.addView(spark, LinearLayout.LayoutParams(d(64), d(64)))

        card.addView(TextView(a).apply {
            setText(R.string.egg_title)
            textSize = 13f
            letterSpacing = 0.08f
            setTextColor(text2)
            gravity = Gravity.CENTER
            setPadding(0, d(14), 0, 0)
        })

        // заголовок з окремими анімованими словами
        val head = FrameLayout(a)
        val title = TextView(a).apply {
            val s = SpannableString("Стас\nЛОХ")
            s.setSpan(ForegroundColorSpan(accent), 5, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            this.text = s
            setTextColor(text)
            typeface = serif
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 58f)
            setLineSpacing(0f, 0.9f)
            gravity = Gravity.CENTER
            setPadding(0, d(6), 0, d(6))
        }
        head.addView(title, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        // печатка
        val stamp = TextView(a).apply {
            setText(R.string.egg_stamp)
            setTextColor(accent)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textSize = 15f
            letterSpacing = 0.12f
            setPadding(d(12), d(5), d(12), d(5))
            background = GradientDrawable().apply {
                cornerRadius = d(8).toFloat()
                setStroke(d(3), accent)
                setColor(Color.TRANSPARENT)
            }
            rotation = -14f
            alpha = 0f
            scaleX = 3f
            scaleY = 3f
        }
        card.addView(head, LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(4) })
        card.clipChildren = false
        card.clipToPadding = false
        card.addView(stamp, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = -d(14); marginEnd = d(2) })

        card.addView(TextView(a).apply {
            setText(R.string.egg_sub)
            textSize = 14f
            setTextColor(text2)
            gravity = Gravity.CENTER
            setPadding(0, d(10), 0, 0)
        })

        val ok = Button(a).apply {
            setText(R.string.egg_ok)
            isAllCaps = false
            textSize = 15f
            stateListAnimator = null
            setTextColor(Ui.color(a, R.attr.cOnAccent))
            setBackgroundResource(R.drawable.btn_primary)
        }
        card.addView(ok, LinearLayout.LayoutParams(-1, d(48)).apply { topMargin = d(22) })

        root.addView(card, FrameLayout.LayoutParams(d(300), -2, Gravity.CENTER))

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setDimAmount(0f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            if (Build.VERSION.SDK_INT >= 28) attributes = attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            Ui.edgeToEdge(this)
            decorView.setPadding(0, 0, 0, 0)
        }

        fun close() {
            card.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(160).start()
            dim.animate().alpha(0f).setDuration(200).withEndAction { dialog.dismiss() }.start()
        }
        ok.setOnClickListener { close() }
        root.setOnClickListener { close() }
        card.setOnClickListener { }

        dialog.setOnShowListener {
            dim.animate().alpha(1f).setDuration(220).start()
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(OvershootInterpolator(1.6f)).start()
            spark.rotation = -180f
            spark.animate().rotation(0f).setDuration(700).setInterpolator(DecelerateInterpolator()).withEndAction {
                // далі — повільно обертається, поки вікно відкрите
                spark.animate().rotationBy(360f * 40).setDuration(40 * 6000L).setInterpolator(LinearInterpolator()).start()
            }.start()
            title.alpha = 0f
            title.translationY = d(16).toFloat()
            title.animate().alpha(1f).translationY(0f).setStartDelay(150).setDuration(380).setInterpolator(DecelerateInterpolator()).start()
            root.post { confetti.burst() }
            stamp.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(650).setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    stamp.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                    card.animate().translationX(d(4).toFloat()).setDuration(40).withEndAction {
                        card.animate().translationX(-d(3).toFloat()).setDuration(40).withEndAction {
                            card.animate().translationX(0f).setDuration(60).start()
                        }.start()
                    }.start()
                }.start()
        }
        dialog.setOnDismissListener { confetti.stop() }
        dialog.show()
    }

    private fun titleFont(c: Context): String {
        val tv = TypedValue()
        c.theme.resolveAttribute(R.attr.titleFont, tv, true)
        return tv.string?.toString() ?: "serif"
    }

    /** Іскри, кружечки й конфеті, що розлітаються від центру й падають. */
    private class Confetti(c: Context, val colors: IntArray) : View(c) {
        private class P(
            var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, val vr: Float,
            val size: Float, val color: Int, val kind: Int,
        )

        private val parts = mutableListOf<P>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private var anim: ValueAnimator? = null
        private var t = 0f
        private val density = resources.displayMetrics.density

        init { isClickable = false }

        fun burst() {
            parts.clear()
            val cx = width / 2f
            val cy = height * 0.42f
            repeat(130) {
                val a = Random.nextDouble(0.0, Math.PI * 2)
                val speed = Random.nextDouble(260.0, 1150.0).toFloat() * density / 3f
                parts += P(
                    cx, cy,
                    (cos(a) * speed).toFloat(), (sin(a) * speed).toFloat() - 260 * density / 3f,
                    Random.nextFloat() * 360, Random.nextFloat() * 720 - 360,
                    (Random.nextFloat() * 6 + 5) * density, colors[Random.nextInt(colors.size)], Random.nextInt(3),
                )
            }
            anim?.cancel()
            var last = 0L
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 3200
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val now = it.currentPlayTime
                    val dt = ((now - last).coerceIn(0, 50)) / 1000f
                    last = now
                    t = it.animatedValue as Float
                    step(dt)
                    invalidate()
                }
                start()
            }
        }

        fun stop() = anim?.cancel()

        private fun step(dt: Float) {
            val g = 900f * density / 3f
            parts.forEach { p ->
                p.vx *= 0.985f
                p.vy = p.vy * 0.985f + g * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rot += p.vr * dt
            }
        }

        override fun onDraw(canvas: Canvas) {
            val fade = if (t < 0.65f) 1f else ((1f - t) / 0.35f).coerceIn(0f, 1f)
            parts.forEach { p ->
                paint.color = p.color
                paint.alpha = (255 * fade).toInt()
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.rot)
                when (p.kind) {
                    0 -> { // маленька іскра Ember
                        paint.style = Paint.Style.STROKE
                        paint.strokeCap = Paint.Cap.ROUND
                        paint.strokeWidth = p.size * 0.22f
                        for (i in 0 until 8) {
                            val a = Math.PI * 2 * i / 8
                            val l = if (i % 2 == 0) p.size else p.size * 0.6f
                            canvas.drawLine(0f, 0f, (cos(a) * l).toFloat(), (sin(a) * l).toFloat(), paint)
                        }
                    }
                    1 -> { // кружечок
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(0f, 0f, p.size * 0.45f, paint)
                    }
                    else -> { // конфеті
                        paint.style = Paint.Style.FILL
                        rect.set(-p.size * 0.7f, -p.size * 0.3f, p.size * 0.7f, p.size * 0.3f)
                        canvas.drawRoundRect(rect, p.size * 0.15f, p.size * 0.15f, paint)
                    }
                }
                canvas.restore()
            }
        }
    }
}
