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
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.SeekBar
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
    private lateinit var custom: CustomCfg
    private var blocksBox: LinearLayout? = null

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
        custom = CustomPrefs.get(this, id)
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
        if (kind == WidgetKind.CUSTOM) {
            buildCustom()
            return
        }
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
            val rv = Widgets.build(this, kind, id, override = cfg, custom = if (kind == WidgetKind.CUSTOM) custom else null)
            val v = rv.apply(applicationContext, preview)
            val maxW = resources.displayMetrics.widthPixels / resources.displayMetrics.density - 56
            preview.addView(v, FrameLayout.LayoutParams(d(w.coerceAtMost(maxW.toInt())), d(h.coerceIn(40, 300)), Gravity.CENTER))
        }
    }

    // ===================== віджет «Свій» =====================

    private fun heading(text: Int) = TextView(this).apply {
        setText(text)
        textSize = 17f
        setTextColor(Ui.color(context, R.attr.cText))
        typeface = Typeface.create(titleFont(), Typeface.NORMAL)
        setPadding(0, d(22), 0, 0)
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Ui.color(context, R.attr.cText2))
    }

    private fun buildCustom() {
        val c = custom
        val p = Provider.of(c.provider)
        val providers = Provider.values().filter { Prefs.enabled(this, it) }
        if (providers.size > 1) {
            options.addView(Ui.chips(this, s(R.string.service), providers.map { it.title }, providers.indexOf(p).coerceAtLeast(0)) { i ->
                custom = custom.copy(provider = providers[i].ordinal); buildOptions(); updatePreview()
            })
        }
        options.addView(Ui.chips(this, s(R.string.show), listOf(s(R.string.opt_auto), s(R.string.opt_left), s(R.string.opt_used)), c.mode, 3) { i ->
            custom = custom.copy(mode = i); updatePreview()
        })

        // блоки
        options.addView(heading(R.string.cw_blocks))
        options.addView(caption(s(R.string.cw_blocks_d)).apply { setPadding(0, d(2), 0, d(4)) })
        blocksBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        options.addView(blocksBox)
        fillBlocks()

        // вигляд
        options.addView(heading(R.string.cw_look))
        options.addView(Ui.chips(this, s(R.string.cw_style), listOf(s(R.string.cw_st_rings), s(R.string.cw_st_bars), s(R.string.cw_st_numbers)), c.style, 3) { i ->
            custom = custom.copy(style = i); updatePreview()
        })
        options.addView(Ui.chips(this, s(R.string.cw_font),
            listOf(s(R.string.cw_f_service), s(R.string.cw_f_sans), s(R.string.cw_f_serif), s(R.string.cw_f_mono), s(R.string.cw_f_condensed)), c.font, 3) { i ->
            custom = custom.copy(font = i); updatePreview()
        })
        options.addView(Ui.chips(this, s(R.string.cw_align), listOf(s(R.string.cw_left), s(R.string.cw_center)), c.align, 2) { i ->
            custom = custom.copy(align = i); updatePreview()
        })
        options.addView(slider(s(R.string.cw_text), 70, 150, 5, c.scale, { "$it%" }) { custom = custom.copy(scale = it) })
        options.addView(slider(s(R.string.cw_radius), 0, 40, 2, c.radius, { "$it dp" }) { custom = custom.copy(radius = it) })
        options.addView(slider(s(R.string.cw_thickness), 2, 16, 1, c.thickness, { "$it dp" }) { custom = custom.copy(thickness = it) })
        options.addView(switch(R.string.cw_labels, c.labels) { custom = custom.copy(labels = it) })
        options.addView(switch(R.string.cw_reset, c.showReset) { custom = custom.copy(showReset = it) })

        // кольори
        options.addView(heading(R.string.cw_colors))
        options.addView(swatches(s(R.string.cw_bg), CustomArt.BGS.toList(), c.bg) { custom = custom.copy(bg = it) })
        options.addView(swatches(s(R.string.cw_accent), CustomArt.ACCENTS.toList(), c.accent) { custom = custom.copy(accent = it) })
        options.addView(slider(s(R.string.cw_opacity), 0, 100, 5, c.opacity, { "$it%" }) { custom = custom.copy(opacity = it) })
        options.addView(switch(R.string.cw_gradient, c.gradient) { custom = custom.copy(gradient = it) })
        options.addView(switch(R.string.cw_border, c.border) { custom = custom.copy(border = it) })

        // поведінка
        options.addView(heading(R.string.cw_behavior))
        options.addView(Ui.chips(this, s(R.string.cw_tap), listOf(s(R.string.cw_tap_open), s(R.string.cw_tap_refresh)), c.tap, 2) { i ->
            custom = custom.copy(tap = i); updatePreview()
        })
    }

    private fun blockName(b: Int) = when (b) {
        CustomArt.B_HEADER -> s(R.string.cw_b_header)
        CustomArt.B_SESSION -> s(if (Provider.of(custom.provider) == Provider.CLAUDE) R.string.session else R.string.h5)
        CustomArt.B_WEEK -> s(R.string.week)
        CustomArt.B_OPUS -> "Opus"
        CustomArt.B_SONNET -> "Sonnet"
        CustomArt.B_COUNTDOWN -> s(R.string.cw_b_countdown)
        CustomArt.B_CHART -> s(R.string.cw_b_chart)
        else -> s(R.string.cw_b_updated)
    }

    /** Список блоків: перемикач і стрілки для зміни порядку. */
    private fun fillBlocks() {
        val box = blocksBox ?: return
        box.removeAllViews()
        val claude = Provider.of(custom.provider) == Provider.CLAUDE
        val list = custom.order.filter { claude || (it != CustomArt.B_OPUS && it != CustomArt.B_SONNET) }
        list.forEachIndexed { i, b ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(6), 0, 0) }
            row.addView(Switch(this).apply {
                text = blockName(b)
                textSize = 15f
                setTextColor(Ui.color(context, R.attr.cText))
                isChecked = b in custom.on
                setOnCheckedChangeListener { _, on ->
                    custom = custom.copy(on = if (on) custom.on + b else custom.on - b)
                    updatePreview()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = d(8) })
            fun arrow(glyph: String, desc: Int, enabled: Boolean, dir: Int) = TextView(this).apply {
                text = glyph
                textSize = 18f
                gravity = Gravity.CENTER
                contentDescription = s(desc)
                setTextColor(Ui.color(context, if (enabled) R.attr.cAccent else R.attr.cText2))
                alpha = if (enabled) 1f else 0.35f
                isEnabled = enabled
                setBackgroundResource(TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true) }.resourceId)
                setOnClickListener {
                    val o = custom.order.toMutableList()
                    val a = o.indexOf(b)
                    val other = list[i + dir]
                    val bIdx = o.indexOf(other)
                    o[a] = other; o[bIdx] = b
                    custom = custom.copy(order = o)
                    fillBlocks(); updatePreview()
                }
            }
            row.addView(arrow("↑", R.string.cw_up, i > 0, -1), LinearLayout.LayoutParams(d(40), d(40)))
            row.addView(arrow("↓", R.string.cw_down, i < list.size - 1, 1), LinearLayout.LayoutParams(d(40), d(40)))
            box.addView(row)
        }
    }

    /** Повзунок з назвою і значенням. */
    private fun slider(title: String, min: Int, max: Int, step: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, d(14), 0, 0) }
        val head = LinearLayout(this)
        head.addView(caption(title), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val v = caption(fmt(value)).apply { setTextColor(Ui.color(context, R.attr.cText)) }
        head.addView(v)
        box.addView(head)
        val accent = ColorStateList.valueOf(Ui.color(this, R.attr.cAccent))
        box.addView(SeekBar(this).apply {
            this.max = (max - min) / step
            progress = ((value - min) / step).coerceIn(0, this.max)
            progressTintList = accent
            thumbTintList = accent
            setPadding(d(4), d(10), d(4), d(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val x = min + p * step
                    v.text = fmt(x)
                    onChange(x)
                    updatePreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })
        return box
    }

    /** Кружечки кольорів. 0 — авто/колір сервісу, 1 серед акцентів — монохром. */
    private fun swatches(title: String, colors: List<Int>, selected: Int, onPick: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, d(14), 0, 0) }
        box.addView(caption(title))
        val views = mutableListOf<View>()
        val accent = Ui.color(this, R.attr.cAccent)
        val line = Ui.color(this, R.attr.cText2) and 0x00FFFFFF or 0x55000000
        val isAccent = colors === CustomArt.ACCENTS.toList() || colors.size == CustomArt.ACCENTS.size
        fun draw(i: Int, sel: Boolean): GradientDrawable {
            val col = colors[i]
            val g = when {
                col == 0 && isAccent -> GradientDrawable().apply { setColor(Provider.of(custom.provider).accent) }
                col == 0 -> GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFFAF9F5.toInt(), 0xFFFAF9F5.toInt(), 0xFF2B2A27.toInt(), 0xFF2B2A27.toInt()))
                col == 1 -> GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF141413.toInt(), 0xFF141413.toInt(), 0xFFF5F4EE.toInt(), 0xFFF5F4EE.toInt()))
                else -> GradientDrawable().apply { setColor(col) }
            }
            g.shape = GradientDrawable.OVAL
            g.setStroke(d(if (sel) 3 else 1), if (sel) accent else line)
            return g
        }
        var row = LinearLayout(this)
        colors.indices.forEach { i ->
            if (i % 6 == 0) {
                row = LinearLayout(this).apply { setPadding(0, d(8), 0, 0) }
                box.addView(row)
            }
            val v = View(this).apply {
                background = draw(i, i == selected)
                setOnClickListener {
                    views.forEachIndexed { k, x -> x.background = draw(k, k == i) }
                    onPick(i)
                    updatePreview()
                }
            }
            views += v
            row.addView(v, LinearLayout.LayoutParams(d(36), d(36)).apply { marginEnd = d(12) })
        }
        return box
    }

    private fun save() {
        WidgetPrefs.put(this, id, if (kind == WidgetKind.CUSTOM) cfg.copy(provider = custom.provider) else cfg)
        if (kind == WidgetKind.CUSTOM) CustomPrefs.put(this, id, custom)
        runCatching { AppWidgetManager.getInstance(this).updateAppWidget(id, Widgets.build(this, kind, id)) }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
