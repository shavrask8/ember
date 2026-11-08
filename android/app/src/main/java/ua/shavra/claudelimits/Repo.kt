package ua.shavra.claudelimits

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService

/** Оновлення даних усіх підключених сервісів і розсилка в плитки та віджети. */
object Repo {
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    private val waiters = mutableListOf<(String?) -> Unit>()

    fun isLoggedIn(c: Context, p: Provider = Provider.CLAUDE) = Prefs.cookie(c, p) != null

    /** Увімкнені сервіси, у які виконано вхід. */
    fun active(c: Context): List<Provider> =
        Provider.values().filter { Prefs.enabled(c, it) && isLoggedIn(c, it) }

    fun isStale(c: Context, minutes: Int): Boolean = active(c).any { p ->
        val u = Prefs.loadUsage(c, p)
        u == null || System.currentTimeMillis() - u.updatedAt > minutes * 60_000L
    }

    /** Оновлює всі сервіси по черзі; onDone отримує помилку обраного сервісу або null. */
    fun refresh(c: Context, onDone: ((String?) -> Unit)? = null) {
        val app = Lang.wrap(c.applicationContext)
        main.post {
            onDone?.let { waiters += it }
            if (busy) return@post
            val list = active(app)
            if (list.isEmpty()) {
                finish(app)
                return@post
            }
            busy = true
            next(app, list, 0)
        }
    }

    private fun next(app: Context, list: List<Provider>, i: Int) {
        if (i >= list.size) {
            busy = false
            finish(app)
            return
        }
        val p = list[i]
        try {
            UsageApi.fetch(app, p) { u, err ->
                if (u != null) {
                    Prefs.saveUsage(app, p, u)
                    History.add(app, p, u)
                    Notify.checkLow(app, p, u)
                }
                Prefs.setLastError(app, p, err)
                next(app, list, i + 1)
            }
        } catch (e: Exception) {
            Prefs.setLastError(app, p, app.getString(R.string.err_browser))
            next(app, list, i + 1)
        }
    }

    private fun finish(app: Context) {
        notifyViews(app)
        Notify.schedule(app)
        val err = Prefs.lastError(app, Prefs.selected(app))
        val w = waiters.toList()
        waiters.clear()
        w.forEach { it(err) }
    }

    fun notifyViews(c: Context) {
        Widgets.updateAll(c)
        TileKind.values().forEach { k ->
            if (Features.enabled(c, k.cls)) {
                runCatching { TileService.requestListeningState(c, ComponentName(c, k.cls)) }
            }
        }
    }
}

/** Вмикання/вимикання плиток, віджетів та фонового оновлення. */
object Features {
    private const val JOB_ID = 42

    fun enabled(c: Context, cls: Class<*>): Boolean {
        val s = c.packageManager.getComponentEnabledSetting(ComponentName(c, cls))
        return s != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun set(c: Context, cls: Class<*>, on: Boolean) {
        c.packageManager.setComponentEnabledSetting(
            ComponentName(c, cls),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Кроки повзунка автооновлення, у хвилинах. */
    val INTERVALS = intArrayOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1440)

    /** Android не дає періодичних завдань частіше ніж раз на 15 хв — тоді плануємо разові «ланцюжком». */
    fun isChained(c: Context) = Prefs.intervalMin(c) < 15

    fun scheduleJob(c: Context) {
        val js = c.getSystemService(JobScheduler::class.java) ?: return
        if (!Prefs.autoRefresh(c) || Repo.active(c).isEmpty()) {
            js.cancel(JOB_ID)
            return
        }
        val ms = Prefs.intervalMin(c) * 60_000L
        val b = JobInfo.Builder(JOB_ID, ComponentName(c, RefreshJob::class.java))
            .setRequiredNetworkType(if (Prefs.wifiOnly(c)) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
            .setRequiresCharging(Prefs.chargingOnly(c))
            .setPersisted(true)
        if (isChained(c)) b.setMinimumLatency(ms) else b.setPeriodic(ms)
        runCatching { js.schedule(b.build()) }
    }
}
