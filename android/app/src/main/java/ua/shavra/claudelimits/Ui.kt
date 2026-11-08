package ua.shavra.claudelimits

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    /** Малюємо під системними панелями (рядок стану і навігація — прозорі). */
    fun edgeToEdge(w: Window) {
        if (Build.VERSION.SDK_INT >= 29) {
            // для кнопкової навігації система сама додасть ледь помітну підкладку; для жестів — прозоро
            w.isNavigationBarContrastEnforced = true
            w.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = w.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    /**
     * Edge-to-edge для екрана з прокруткою: вміст прокручується під рядком стану й панеллю
     * навігації, а відступи від системних панелей і вирізу камери додаються всередині.
     * Під рядком стану — напівпрозора підкладка кольору фону, щоб годинник лишався читабельним.
     */
    fun insetScroll(a: Activity) {
        val host = a.findViewById<ViewGroup>(android.R.id.content)
        val scroll = host.getChildAt(0) as ViewGroup
        val content = scroll.getChildAt(0)
        scroll.fitsSystemWindows = false
        scroll.clipToPadding = false
        val l = content.paddingLeft; val t = content.paddingTop
        val r = content.paddingRight; val b = content.paddingBottom
        val bg = color(a, R.attr.cBg)
        val scrim = View(a).apply {
            setBackgroundColor((bg and 0x00FFFFFF) or (0xE6 shl 24))
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        host.addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP))
        scroll.setOnApplyWindowInsetsListener { _, ins ->
            val top: Int; val bottom: Int; val left: Int; val right: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val i = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = i.top; bottom = i.bottom; left = i.left; right = i.right
            } else {
                @Suppress("DEPRECATION")
                run { top = ins.systemWindowInsetTop; bottom = ins.systemWindowInsetBottom; left = ins.systemWindowInsetLeft; right = ins.systemWindowInsetRight }
            }
            content.setPadding(l + left, t + top, r + right, b + bottom)
            if (scrim.layoutParams.height != top) {
                scrim.layoutParams = scrim.layoutParams.apply { height = top }
            }
            ins
        }
        scroll.requestApplyInsets()
    }

    /** Рідний стиль сервісу; якщо його немає — стиль Claude. */
    fun themeFor(p: Provider) = when (p) {
        Provider.CODEX -> R.style.AppTheme_Codex
        else -> R.style.AppTheme
    }

    fun applyTheme(a: Activity, p: Provider = Prefs.selected(a)) = a.setTheme(themeFor(p))

    /** Колір з поточної теми (?attr/...). */
    fun color(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ctx.getColor(tv.resourceId) else tv.data
    }

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    /** Рядок «чіпів» — вибір одного варіанта. */
    fun chips(ctx: Context, title: String?, options: List<String>, selected: Int, perRow: Int = 4, onPick: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(ctx, 14), 0, 0)
        }
        if (title != null) {
            box.addView(TextView(ctx).apply {
                text = title
                setTextColor(color(ctx, R.attr.cText2))
                textSize = 13f
            })
        }
        var row = LinearLayout(ctx)
        val views = mutableListOf<TextView>()
        fun style(i: Int, sel: Int) {
            val on = i == sel
            views[i].setBackgroundResource(if (on) R.drawable.chip_on else R.drawable.chip_off)
            views[i].setTextColor(color(ctx, if (on) R.attr.cOnAccent else R.attr.cText))
        }
        options.forEachIndexed { i, s ->
            if (i % perRow == 0) {
                row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(ctx, 6), 0, 0)
                }
                box.addView(row)
            }
            val t = TextView(ctx).apply {
                text = s
                gravity = Gravity.CENTER
                textSize = 13f
                maxLines = 1
                setPadding(dp(ctx, 4), dp(ctx, 8), dp(ctx, 4), dp(ctx, 8))
                setOnClickListener {
                    views.indices.forEach { j -> style(j, i) }
                    onPick(i)
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (i % perRow > 0) lp.marginStart = dp(ctx, 6)
            row.addView(t, lp)
            views += t
        }
        // вирівнюємо останній неповний ряд
        val rest = (perRow - options.size % perRow) % perRow
        repeat(rest) {
            row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(ctx, 6) })
        }
        views.indices.forEach { style(it, selected) }
        return box
    }

    /** Видаляє cookie лише одного сайту (щоб вихід з одного сервісу не зачіпав інший). */
    fun clearCookies(url: String) {
        val cm = CookieManager.getInstance()
        val host = url.removePrefix("https://")
        val names = cm.getCookie(url)?.split(';')?.map { it.substringBefore('=').trim() } ?: return
        names.filter { it.isNotEmpty() }.forEach { n ->
            cm.setCookie(url, "$n=; Max-Age=0; Path=/")
            cm.setCookie(url, "$n=; Max-Age=0; Path=/; Domain=.$host")
            cm.setCookie(url, "$n=; Max-Age=0; Path=/; Domain=$host")
        }
        cm.flush()
    }
}
