package ua.shavra.claudelimits

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Редагування віджета з живим попереднім переглядом справжнього розміру. */
class WidgetConfigActivity : Activity() {
    private var id = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var kind: WidgetKind
    private lateinit var cfg: WidgetCfg
    private lateinit var preview: FrameLayout
    private lateinit var options: LinearLayout

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) ?: id
        cfg = WidgetPrefs.get(this, id)
        // новий віджет одразу показує обраний у застосунку сервіс
        if (!WidgetPrefs.has(this, id)) cfg = cfg.copy(provider = Prefs.selected(this).ordinal)
        Ui.applyTheme(this, Provider.of(cfg.provider))
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        kind = WidgetKind.of(AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className) ?: WidgetKind.FULL
        Ui.edgeToEdge(window)
        buildUi()
        buildOptions()
        updatePreview()
    }

    private fun d(v: Int) = Ui.dp(this, v)

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(18), d(8), d(18), d(24))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(Ui.color(context, R.attr.cText))
            setBackgroundResource(TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true) }.resourceId)
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(d(44), d(44)).apply { marginStart = -d(10) })
        header.addView(TextView(this).apply {
            text = getString(R.string.wc_title, getString(kind.title))
            textSize = 22f
            setTextColor(Ui.color(context, R.attr.cText))
            typeface = Typeface.create(titleFont(), Typeface.NORMAL)
            setPadding(d(6), 0, 0, 0)
        })
        root.addView(header)

        val (w, h) = Widgets.size(this, kind, id)
        preview = FrameLayout(this).apply { setBackgroundResource(R.drawable.preview_bg) }
        root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, d(h.coerceIn(80, 300) + 40)).apply { topMargin = d(14) })
        root.addView(TextView(this).apply {
            text = "${w}×${h} dp"
            textSize = 11f
            gravity = Gravity.END
            setTextColor(Ui.color(context, R.attr.cText2))
            setPadding(0, d(4), d(4), 0)
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card)
            setPadding(d(18), d(4), d(18), d(18))
        }
        options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(options)
        root.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = d(10) })

        root.addView(Button(this).apply {
            text = getString(R.string.save)
            isAllCaps = false
            textSize = 15f
            stateListAnimator = null
            setTextColor(Ui.color(context, R.attr.cOnAccent))
            setBackgroundResource(R.drawable.btn_primary)
            setOnClickListener { save() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, d(48)).apply { topMargin = d(16) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.color(context, R.attr.cBg))
            addView(root)
        })
        Ui.insetScroll(this)
    }

    private fun titleFont(): String {
        val tv = TypedValue()
        theme.resolveAttribute(R.attr.titleFont, tv, true)
        return tv.string?.toString() ?: "serif"
    }

    private fun s(id: Int) = getString(id)

    private fun buildOptions() {
        options.removeAllViews()
        val providers = Provider.values().filter { Prefs.enabled(this, it) }
        val p = Provider.of(cfg.provider)
        if (providers.size > 1 && kind != WidgetKind.OVERVIEW) {
            options.addView(Ui.chips(this, s(R.string.service), providers.map { it.title }, providers.indexOf(p).coerceAtLeast(0)) { i ->
                cfg = cfg.copy(provider = providers[i].ordinal, limit = if (providers[i] == Provider.CLAUDE) cfg.limit else cfg.limit.coerceAtMost(1))
                buildOptions(); updatePreview()
            })
        }
        if (kind.hasLimit) {
            val names = mutableListOf(s(if (p == Provider.CLAUDE) R.string.session else R.string.h5), s(R.string.week))
            if (p == Provider.CLAUDE && kind != WidgetKind.TIMER) names += listOf("Opus", "Sonnet")
            options.addView(Ui.chips(this, s(R.string.limit), names, cfg.limit.coerceIn(0, names.size - 1)) { i ->
                cfg = cfg.copy(limit = i); updatePreview()
            })
        }
        options.addView(Ui.chips(this, s(R.string.show), listOf(s(R.string.opt_auto), s(R.string.opt_left), s(R.string.opt_used)), cfg.mode, 3) { i ->
            cfg = cfg.copy(mode = i); updatePreview()
        })
        options.addView(Ui.chips(this, s(R.string.theme), listOf(s(R.string.opt_auto), s(R.string.th_light), s(R.string.th_dark), s(R.string.th_accent)), cfg.theme) { i ->
            cfg = cfg.copy(theme = i); updatePreview()
        })
        options.addView(Ui.chips(this, s(R.string.accent),
            listOf(s(R.string.ac_service), s(R.string.ac_terracotta), s(R.string.ac_blue), s(R.string.ac_green), s(R.string.ac_purple), s(R.string.ac_pink)),
            cfg.accent, 3) { i -> cfg = cfg.copy(accent = i); updatePreview() })
        val alphas = listOf(100, 75, 50, 0)
        options.addView(Ui.chips(this, s(R.string.background), alphas.map { "$it%" }, alphas.indexOf(cfg.opacity).coerceAtLeast(0)) { i ->
            cfg = cfg.copy(opacity = alphas[i]); updatePreview()
        })
        options.addView(Ui.chips(this, s(R.string.corners), listOf(s(R.string.c_square), s(R.string.c_soft), s(R.string.c_round), s(R.string.c_max)), cfg.corners) { i ->
            cfg = cfg.copy(corners = i); updatePreview()
        })
        options.addView(Ui.chips(this, s(R.string.text_size), listOf(s(R.string.ts_s), s(R.string.ts_m), s(R.string.ts_l)), cfg.textSize, 3) { i ->
            cfg = cfg.copy(textSize = i); updatePreview()
        })
        options.addView(switch(R.string.show_header, cfg.showHeader) { cfg = cfg.copy(showHeader = it) })
        if (kind != WidgetKind.STATS) {
            options.addView(switch(R.string.show_reset, cfg.showReset) { cfg = cfg.copy(showReset = it) })
        }
    }

    private fun switch(text: Int, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        setText(text)
        textSize = 15f
        setTextColor(Ui.color(context, R.attr.cText))
        isChecked = checked
        setPadding(0, d(16), 0, 0)
        setOnCheckedChangeListener { _, on -> onChange(on); updatePreview() }
    }

    private fun updatePreview() {
        preview.removeAllViews()
        runCatching {
            val (w, h) = Widgets.size(this, kind, id)
            val rv = Widgets.build(this, kind, id, override = cfg)
            val v = rv.apply(applicationContext, preview)
            val maxW = resources.displayMetrics.widthPixels / resources.displayMetrics.density - 56
            preview.addView(v, FrameLayout.LayoutParams(d(w.coerceAtMost(maxW.toInt())), d(h.coerceIn(40, 300)), Gravity.CENTER))
        }
    }

    private fun save() {
        WidgetPrefs.put(this, id, cfg)
        runCatching { AppWidgetManager.getInstance(this).updateAppWidget(id, Widgets.build(this, kind, id)) }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
