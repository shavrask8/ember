package ua.shavra.claudelimits

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var p: Provider
    private lateinit var ring: RingView
    private lateinit var ringPct: TextView
    private lateinit var ringWord: TextView
    private lateinit var status: TextView
    private lateinit var refreshBtn: View
    private lateinit var sessionPace: TextView
    private lateinit var weekPace: TextView
    private lateinit var barChart: BarChartView
    private lateinit var lineChart: LineChartView
    private lateinit var statsNote: TextView

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Lang.wrap(base))

    private var eggTaps = 0
    private var eggStart = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        intent?.getStringExtra(EXTRA_PROVIDER)?.let { Prefs.setSelected(this, Provider.byId(it)) }
        p = Prefs.selected(this)
        Ui.applyTheme(this, p)
        super.onCreate(savedInstanceState)

        // Перший запуск — вибір сервісу та вхід
        if (!Prefs.onboarded(this)) {
            if (Repo.isLoggedIn(this)) {
                Prefs.setOnboarded(this, true) // уже входили раніше
            } else {
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
                return
            }
        }

        Ui.edgeToEdge(window)
        setContentView(R.layout.activity_main)
        Ui.insetScroll(this)

        ring = findViewById(R.id.ring)
        ringPct = findViewById(R.id.ringPct)
        ringWord = findViewById(R.id.ringWord)
        status = findViewById(R.id.status)
        refreshBtn = findViewById(R.id.refresh)
        sessionPace = findViewById(R.id.sessionPace)
        weekPace = findViewById(R.id.weekPace)
        barChart = findViewById(R.id.barChart)
        lineChart = findViewById(R.id.lineChart)
        statsNote = findViewById(R.id.statsNote)

        findViewById<ImageView>(R.id.logo).apply {
            setImageResource(p.icon)
            setOnClickListener { easterEgg(this) }
        }
        findViewById<TextView>(R.id.appTitle).text = p.title
        findViewById<TextView>(R.id.sessionTitle).setText(p.sessionTitle)

        refreshBtn.setOnClickListener { doRefresh() }
        findViewById<View>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        buildProviderBar()
        Features.scheduleJob(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val want = intent.getStringExtra(EXTRA_PROVIDER)?.let { Provider.byId(it) } ?: return
        if (want != p) {
            Prefs.setSelected(this, want)
            recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        // сервіс змінили в налаштуваннях — перемальовуємо в його стилі
        if (Prefs.selected(this) != p) {
            recreate()
            return
        }
        buildProviderBar()
        render(animate = true)
        if (Repo.isStale(this, 1)) doRefresh()
    }

    /** Перемикач між сервісами — лише коли їх увімкнено кілька. */
    private fun buildProviderBar() {
        val bar = findViewById<LinearLayout>(R.id.providerBar)
        bar.removeAllViews()
        val list = Provider.values().filter { Prefs.enabled(this, it) }
        if (list.size < 2) {
            bar.visibility = View.GONE
            return
        }
        bar.visibility = View.VISIBLE
        bar.addView(Ui.chips(this, null, list.map { it.title }, list.indexOf(p)) { i ->
            if (list[i] != p) {
                Prefs.setSelected(this, list[i])
                recreate()
            }
        })
    }

    private fun doRefresh() {
        if (Repo.active(this).isEmpty()) {
            render(animate = false)
            return
        }
        status.setTextColor(Ui.color(this, R.attr.cText2))
        status.text = getString(R.string.updating_cap)
        refreshBtn.animate().rotationBy(360f).setDuration(700).start()
        Repo.refresh(this) { if (!isDestroyed) render(animate = true) }
    }

    private fun mod(key: String) = Prefs.module(this, key)

    private fun render(animate: Boolean) {
        val now = System.currentTimeMillis()
        val logged = Repo.isLoggedIn(this, p)
        val u = Prefs.loadUsage(this, p)
        val showUsed = Prefs.showUsed(this)
        val pace = mod("pace")
        val accent = Ui.color(this, R.attr.cAccent)
        val danger = getColor(R.color.danger)

        // ---- статус у шапці ----
        val err = Prefs.lastError(this, p)
        status.setTextColor(if (logged && err != null) danger else Ui.color(this, R.attr.cText2))
        status.text = when {
            !logged -> getString(R.string.sign_in_settings)
            err != null -> "⚠ $err"
            u != null -> getString(R.string.updated_at, Fmt.clock(u.updatedAt))
            else -> getString(R.string.usage_limits)
        }

        // ---- сесія ----
        val s = u?.session
        val details = mod("details")
        findViewById<View>(R.id.detailsRow).visibility = vis(details)
        findViewById<View>(R.id.sessionReset).visibility = vis(!details)
        if (s != null) {
            val pct = Fmt.shown(this, s, now)
            val f = Fmt.elapsed(s, Fmt.SESSION, now)
            ring.color = if (Fmt.isLow(s, now)) danger else accent
            ring.setProgress(pct / 100f, animate)
            ring.marker = if (!pace || f < 0) -1f else if (showUsed) f else 1 - f
            ringPct.text = "$pct%"
            ringWord.text = Fmt.word(this)
            val active = s.resetsAt > now
            findViewById<TextView>(R.id.sessionReset).text = Fmt.resetLine(this, s, now)
            tile(R.id.tileReset, if (active) Fmt.resetShort(this, s.resetsAt, now) else "—", getString(R.string.reset_lbl))
            tile(R.id.tileLeft, if (active) Fmt.until(this, s.resetsAt, now) else "—", getString(R.string.until_reset_lbl))
            pace(sessionPace, if (pace) Fmt.sessionPace(this, s, now) else null)
        } else {
            ring.setProgress(0f, animate)
            ring.marker = -1f
            ringPct.text = "—"
            ringWord.text = getString(if (logged) R.string.no_data else R.string.not_signed)
            findViewById<TextView>(R.id.sessionReset).text =
                if (logged) getString(R.string.tap_refresh) else getString(R.string.sign_in_to, p.title)
            tile(R.id.tileReset, "—", getString(R.string.reset_lbl))
            tile(R.id.tileLeft, "—", getString(R.string.until_reset_lbl))
            pace(sessionPace, null)
        }
        findViewById<View>(R.id.sessionPaceBox).visibility = vis(sessionPace.visibility == View.VISIBLE)

        // ---- тиждень ----
        val models = mod("models") && p == Provider.CLAUDE
        bindRow(R.id.rowWeek, getString(p.weekRow), u?.week, always = true, now, pace)
        bindRow(R.id.rowOpus, "Opus", if (models) u?.opus else null, always = false, now, pace)
        bindRow(R.id.rowSonnet, "Sonnet", if (models) u?.sonnet else null, always = false, now, pace)
        pace(weekPace, if (pace) u?.week?.let { Fmt.weekPace(this, it, now) } else null)
        findViewById<View>(R.id.weekPaceBox).visibility = vis(weekPace.visibility == View.VISIBLE)

        // ---- статистика ----
        val statsOn = mod("stats")
        findViewById<View>(R.id.statsCard).visibility = vis(statsOn)
        if (statsOn) {
            val st = History.stats(this, p, now)
            tile(R.id.stSessions, "${st.sessions}", getString(R.string.st_sessions))
            tile(R.id.stHit, "${st.hitLimit}", resources.getQuantityString(R.plurals.st_hit, st.hitLimit))
            tile(R.id.stAvg, st.avgPeak?.let { "$it%" } ?: "—", getString(R.string.st_avg))
            tile(R.id.stMaxDay, if (st.maxDay > 0) pctText(st.maxDay) else "—", getString(R.string.st_maxday))
            barChart.setData(st.days)
            lineChart.setData(st.line, now - 24 * 3600_000L, now)
            statsNote.text = st.since?.let {
                val d = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Lang.locale(this))
                    .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
                getString(R.string.st_since, d)
            } ?: getString(R.string.st_none)
        }
    }

    private fun tile(id: Int, value: String, label: String) {
        val t = findViewById<View>(id)
        t.findViewById<TextView>(R.id.tileValue).text = value
        t.findViewById<TextView>(R.id.tileLabel).text = label
    }

    /** Плашка з темпом: колір статусу + значок, а не лише колір. */
    private fun pace(v: TextView, pc: Fmt.Pace?) {
        if (pc == null) {
            v.visibility = View.GONE
            return
        }
        v.visibility = View.VISIBLE
        val c = getColor(if (pc.ok) R.color.good else R.color.warn)
        v.text = (if (pc.ok) "✓  " else "⚠  ") + pc.text
        v.setTextColor(c)
        v.backgroundTintList = ColorStateList.valueOf((c and 0x00FFFFFF) or 0x1F000000)
    }

    private fun bindRow(id: Int, title: String, l: Limit?, always: Boolean, now: Long, pace: Boolean) {
        val row = findViewById<View>(id)
        if (l == null && !always) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        row.findViewById<TextView>(R.id.rowTitle).text = title
        val pct = row.findViewById<TextView>(R.id.rowPct)
        val bar = row.findViewById<PaceBarView>(R.id.rowBar)
        val sub = row.findViewById<TextView>(R.id.rowSub)
        val accent = Ui.color(this, R.attr.cAccent)
        if (l == null) {
            pct.text = "—"
            bar.set(0f, -1f, accent)
            sub.text = getString(R.string.no_data_cap)
            return
        }
        val v = Fmt.shown(this, l, now)
        val f = Fmt.elapsed(l, Fmt.WEEK, now)
        pct.text = "$v% ${Fmt.word(this)}"
        bar.set(
            v / 100f,
            if (!pace || f < 0) -1f else if (Prefs.showUsed(this)) f else 1 - f,
            if (Fmt.isLow(l, now)) getColor(R.color.danger) else accent,
        )
        sub.text = Fmt.resetLine(this, l, now)
    }

    /** 🤫 Сім торкань по логотипу. */
    private fun easterEgg(logo: View) {
        val now = System.currentTimeMillis()
        if (now - eggStart > 3000) {
            eggStart = now
            eggTaps = 0
        }
        eggTaps++
        logo.animate().rotationBy(30f).setDuration(120).start()
        if (eggTaps >= 7) {
            eggTaps = 0
            logo.animate().rotationBy(720f).scaleX(1.4f).scaleY(1.4f).setDuration(700)
                .withEndAction { logo.animate().scaleX(1f).scaleY(1f).setDuration(200).start() }
                .start()
            AlertDialog.Builder(this)
                .setTitle(R.string.egg_title)
                .setMessage("Стас ЛОХ")
                .setPositiveButton(R.string.egg_ok, null)
                .show()
        }
    }

    private fun vis(on: Boolean) = if (on) View.VISIBLE else View.GONE

    companion object {
        const val EXTRA_PROVIDER = "provider"
    }
}
