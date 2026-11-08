package ua.shavra.claudelimits

import android.app.Activity
import android.app.AlertDialog
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebSettings
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private var loginFor: Provider? = null

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.applyTheme(this)
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(window)
        setContentView(R.layout.activity_settings)
        Ui.insetScroll(this)

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        buildLanguage()
        buildModules()
        buildWidgets()
        setupUpdates()
    }

    override fun onResume() {
        super.onResume()
        buildServices()
        buildNotifications()
        buildTiles()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_LOGIN && resultCode == RESULT_OK) {
            loginFor?.let { Prefs.setSelected(this, it) }
            Features.scheduleJob(this)
            Repo.refresh(this)
            toast(getString(R.string.signed_in))
        }
    }

    // ---------- рядок налаштування ----------

    private class Row(val view: View) {
        val icon: ImageView = view.findViewById(R.id.frIcon)
        val title: TextView = view.findViewById(R.id.frTitle)
        val desc: TextView = view.findViewById(R.id.frDesc)
        val sw: Switch = view.findViewById(R.id.frSwitch)
        val add: TextView = view.findViewById(R.id.frAdd)
        val extra: TextView = view.findViewById(R.id.frExtra)
        val extra2: TextView = view.findViewById(R.id.frExtra2)
    }

    private fun row(container: LinearLayout, title: String, desc: String): Row {
        val v = LayoutInflater.from(this).inflate(R.layout.feature_row, container, false)
        container.addView(v)
        return Row(v).also {
            it.title.text = title
            it.desc.text = desc
        }
    }

    private fun link(t: TextView, text: String, onClick: () -> Unit) {
        t.text = text
        t.visibility = View.VISIBLE
        t.setOnClickListener { onClick() }
    }

    // ---------- сервіси ----------

    private fun buildServices() {
        val box = findViewById<LinearLayout>(R.id.servicesList)
        box.removeAllViews()
        Provider.values().forEach { p ->
            val logged = Repo.isLoggedIn(this, p)
            val enabled = Prefs.enabled(this, p)
            val r = row(box, p.title, getString(if (logged) R.string.connected else R.string.not_connected) + " · " + getString(p.note))
            r.icon.visibility = View.VISIBLE
            r.icon.setImageResource(p.icon)
            if (p == Provider.CLAUDE) {
                r.sw.visibility = View.GONE // Claude — основний сервіс
            } else {
                r.sw.isChecked = enabled
                r.sw.setOnCheckedChangeListener { _, on ->
                    Prefs.setEnabled(this, p, on)
                    if (!on && Prefs.selected(this) == p) Prefs.setSelected(this, Provider.CLAUDE)
                    if (on && !Repo.isLoggedIn(this, p)) login(p)
                    Features.scheduleJob(this)
                    Repo.notifyViews(this)
                    buildServices()
                    buildTiles()
                }
            }
            if (enabled) {
                link(r.add, getString(if (logged) R.string.sign_in_again else R.string.sign_in)) { login(p) }
                if (logged) {
                    link(r.extra, getString(R.string.sign_out)) { confirmLogout(p) }
                    r.extra.setTextColor(getColor(R.color.danger))
                }
                if (p == Provider.CLAUDE) link(r.extra2, getString(R.string.manual_key)) { pasteDialog() }
            }
        }
    }

    private fun login(p: Provider) {
        loginFor = p
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_PROVIDER, p.id), REQ_LOGIN)
    }

    private fun confirmLogout(p: Provider) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sign_out_q, p.title))
            .setMessage(R.string.sign_out_msg)
            .setPositiveButton(R.string.sign_out) { _, _ ->
                Prefs.clearAccount(this, p)
                runCatching { Ui.clearCookies(p.cookieUrl) }
                Features.scheduleJob(this)
                Repo.notifyViews(this)
                buildServices()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pasteDialog() {
        val pad = Ui.dp(this, 20)
        val input = EditText(this).apply {
            hint = "sk-ant-sid01-…"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.paste_title)
            .setMessage(R.string.paste_msg)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val v = input.text.toString().trim()
                if (v.isEmpty()) return@setPositiveButton
                val key = if (v.contains("sessionKey=")) v.substringAfter("sessionKey=").substringBefore(';').trim() else v
                UsageApi.setSessionKey(key)
                Prefs.setCookie(this, Provider.CLAUDE, "sessionKey=$key")
                Prefs.setOrgId(this, null)
                if (Prefs.userAgent(this) == null) {
                    Prefs.setUserAgent(this, runCatching { WebSettings.getDefaultUserAgent(this).replace("; wv", "") }.getOrNull())
                }
                Features.scheduleJob(this)
                Repo.refresh(this)
                buildServices()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- мова ----------

    private fun buildLanguage() {
        val box = findViewById<LinearLayout>(R.id.languageBox)
        box.removeAllViews()
        val codes = Lang.CODES
        val names = Lang.names(this)
        val cur = codes.indexOf(Lang.current(this)).coerceAtLeast(0)
        val r = row(box, getString(R.string.language), names[cur])
        r.sw.visibility = View.GONE
        r.view.isClickable = true
        r.view.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setSingleChoiceItems(names.toTypedArray(), cur) { d, i ->
                    d.dismiss()
                    if (i != cur) Lang.set(this, codes[i])
                }
                .show()
        }
    }

    // ---------- головний екран ----------

    private fun buildModules() {
        val box = findViewById<LinearLayout>(R.id.modulesList)
        listOf(
            Triple("details", R.string.mod_details, R.string.mod_details_d),
            Triple("pace", R.string.mod_pace, R.string.mod_pace_d),
            Triple("models", R.string.mod_models, R.string.mod_models_d),
            Triple("stats", R.string.wk_stats, R.string.mod_stats_d),
        ).forEach { (key, title, desc) ->
            val r = row(box, getString(title), getString(desc))
            r.sw.isChecked = Prefs.module(this, key)
            r.sw.setOnCheckedChangeListener { _, on -> Prefs.setModule(this, key, on) }
        }
    }

    // ---------- віджети ----------

    private fun buildWidgets() {
        val box = findViewById<LinearLayout>(R.id.widgetsList)
        box.removeAllViews()
        val dm = resources.displayMetrics
        val avail = (dm.widthPixels / dm.density).toInt() - 36 - 36 - 24
        WidgetKind.values().forEach { k ->
            // живе прев'ю віджета з вашими даними
            val frame = FrameLayout(this).apply { setBackgroundResource(R.drawable.preview_bg) }
            box.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, k.previewH + 28)).apply {
                topMargin = Ui.dp(this@SettingsActivity, 18)
            })
            runCatching {
                val cfg = WidgetCfg(provider = Prefs.selected(this).ordinal)
                val v = Widgets.build(this, k, AppWidgetManager.INVALID_APPWIDGET_ID, override = cfg).apply(applicationContext, frame)
                frame.addView(v, FrameLayout.LayoutParams(Ui.dp(this, k.previewW.coerceAtMost(avail)), Ui.dp(this, k.previewH), Gravity.CENTER))
            }
            val r = row(box, getString(k.title), getString(k.desc))
            r.view.setPadding(0, Ui.dp(this, 6), 0, 0)
            val on = Features.enabled(this, k.cls)
            r.sw.isChecked = on
            link(r.add, getString(R.string.add)) { requestWidget(k) }
            r.add.visibility = if (on) View.VISIBLE else View.GONE
            r.sw.setOnCheckedChangeListener { _, v ->
                Features.set(this, k.cls, v)
                r.add.visibility = if (v) View.VISIBLE else View.GONE
                if (!v) toast(getString(R.string.widget_off, getString(k.title)))
            }
        }
        findViewById<View>(R.id.editWidgets).setOnClickListener { editPlaced() }
    }

    private fun requestWidget(k: WidgetKind) {
        val awm = getSystemService(AppWidgetManager::class.java)
        if (awm != null && awm.isRequestPinAppWidgetSupported) {
            awm.requestPinAppWidget(ComponentName(this, k.cls), null, null)
        } else {
            toast(getString(R.string.widget_manual, getString(R.string.app_name)))
        }
    }

    private fun editPlaced() {
        val list = Widgets.placed(this)
        if (list.isEmpty()) {
            toast(getString(R.string.no_widgets))
            return
        }
        val names = list.mapIndexed { i, (k, id) ->
            "${i + 1}. ${getString(k.title)} · ${Provider.of(WidgetPrefs.get(this, id).provider).title}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.which_widget)
            .setItems(names) { _, i ->
                startActivity(
                    Intent(this, WidgetConfigActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, list[i].second),
                )
            }
            .show()
    }

    // ---------- сповіщення ----------

    private fun buildNotifications() {
        val box = findViewById<LinearLayout>(R.id.notifyList)
        box.removeAllViews()
        listOf(
            Triple(Notify.SESSION, R.string.n_session, R.string.n_session_d),
            Triple(Notify.WEEK, R.string.n_week, R.string.n_week_d),
            Triple(Notify.LOW, R.string.n_low, R.string.n_low_d),
        ).forEach { (key, title, desc) ->
            val r = row(box, getString(title), getString(desc))
            r.sw.isChecked = Notify.enabled(this, key)
            r.sw.setOnCheckedChangeListener { _, on ->
                Notify.setEnabled(this, key, on)
                if (on && !Notify.permitted(this) && Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
                }
                Notify.schedule(this)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFY && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            toast(getString(R.string.n_denied))
        }
    }

    // ---------- плитки ----------

    private fun buildTiles() {
        val box = findViewById<LinearLayout>(R.id.tilesList)
        box.removeAllViews()
        TileKind.values().forEach { k ->
            val r = row(box, getString(k.title), getString(k.desc))
            val on = Features.enabled(this, k.cls)
            r.sw.isChecked = on
            link(r.add, getString(R.string.add_shade)) { requestTile(k) }
            r.add.visibility = if (on) View.VISIBLE else View.GONE
            r.sw.setOnCheckedChangeListener { _, v ->
                Features.set(this, k.cls, v)
                r.add.visibility = if (v) View.VISIBLE else View.GONE
                if (v) Repo.notifyViews(this) else toast(getString(R.string.tile_off, getString(k.title)))
            }
        }
        val pbox = findViewById<LinearLayout>(R.id.tileProviderBox)
        pbox.removeAllViews()
        val providers = Provider.values().filter { Prefs.enabled(this, it) }
        if (providers.size > 1) {
            pbox.addView(Ui.chips(this, getString(R.string.tile_service), providers.map { it.title }, providers.indexOf(Prefs.tileProvider(this)).coerceAtLeast(0)) { i ->
                Prefs.setTileProvider(this, providers[i])
                Repo.notifyViews(this)
            })
        }
    }

    private fun requestTile(k: TileKind) {
        if (Build.VERSION.SDK_INT >= 33) {
            val p = Prefs.tileProvider(this)
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, k.cls),
                getString(k.label),
                Icon.createWithResource(this, p.icon),
                mainExecutor,
            ) { code ->
                if (code == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) toast(getString(R.string.tile_exists))
            }
        } else {
            toast(getString(R.string.tile_manual, getString(k.label)))
        }
    }

    // ---------- оновлення та відображення ----------

    private fun setupUpdates() {
        val swUsed = findViewById<Switch>(R.id.swUsed)
        swUsed.isChecked = Prefs.showUsed(this)
        swUsed.setOnCheckedChangeListener { _, on ->
            Prefs.setShowUsed(this, on)
            Repo.notifyViews(this)
        }

        val swAuto = findViewById<Switch>(R.id.swAuto)
        val box = findViewById<View>(R.id.autoBox)
        val seek = findViewById<SeekBar>(R.id.seekInterval)
        val value = findViewById<TextView>(R.id.autoValue)
        val note = findViewById<TextView>(R.id.autoNote)
        val steps = Features.INTERVALS

        fun show(min: Int) {
            value.text = getString(R.string.every, Fmt.intervalText(this, min))
            note.text = when {
                min < 15 -> getString(R.string.note_fast)
                min <= 30 -> getString(R.string.note_good)
                min >= 360 -> getString(R.string.note_rare)
                else -> getString(R.string.note_mid)
            }
        }

        swAuto.isChecked = Prefs.autoRefresh(this)
        box.visibility = if (swAuto.isChecked) View.VISIBLE else View.GONE
        swAuto.setOnCheckedChangeListener { _, on ->
            Prefs.setAutoRefresh(this, on)
            box.visibility = if (on) View.VISIBLE else View.GONE
            Features.scheduleJob(this)
        }

        seek.max = steps.size - 1
        val cur = Prefs.intervalMin(this)
        seek.progress = steps.indices.minByOrNull { kotlin.math.abs(steps[it] - cur) } ?: 4
        show(steps[seek.progress])
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) = show(steps[p])
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {
                Prefs.setIntervalMin(this@SettingsActivity, steps[s.progress])
                Features.scheduleJob(this@SettingsActivity)
            }
        })

        val swWifi = findViewById<Switch>(R.id.swWifi)
        swWifi.isChecked = Prefs.wifiOnly(this)
        swWifi.setOnCheckedChangeListener { _, on -> Prefs.setWifiOnly(this, on); Features.scheduleJob(this) }
        val swCharging = findViewById<Switch>(R.id.swCharging)
        swCharging.isChecked = Prefs.chargingOnly(this)
        swCharging.setOnCheckedChangeListener { _, on -> Prefs.setChargingOnly(this, on); Features.scheduleJob(this) }

        findViewById<View>(R.id.clearHistory).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_q)
                .setMessage(R.string.clear_msg)
                .setPositiveButton(R.string.clear) { _, _ -> History.clear(this); toast(getString(R.string.cleared)) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQ_LOGIN = 1
        private const val REQ_NOTIFY = 2
    }
}
