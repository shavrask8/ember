package ua.shavra.claudelimits

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Сповіщення: ліміт сесії / тижня знову повний, мало ліміту.
 * Час скидання відомий заздалегідь, тож ставимо будильник (AlarmManager) на цей момент.
 */
object Notify {
    const val SESSION = "session"
    const val WEEK = "week"
    const val LOW = "low"
    private const val CH_RESETS = "resets"
    private const val CH_LOW = "low"

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("claude_limits", Context.MODE_PRIVATE)
    fun enabled(c: Context, key: String) = sp(c).getBoolean("notify_$key", false)
    fun setEnabled(c: Context, key: String, v: Boolean) = sp(c).edit().putBoolean("notify_$key", v).apply()
    fun any(c: Context) = enabled(c, SESSION) || enabled(c, WEEK) || enabled(c, LOW)

    fun permitted(c: Context) = Build.VERSION.SDK_INT < 33 ||
        c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_RESETS, c.getString(R.string.ch_resets), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_LOW, c.getString(R.string.ch_low), NotificationManager.IMPORTANCE_HIGH))
    }

    /** Ставить (або знімає) будильники на моменти скидання для всіх сервісів. */
    fun schedule(c0: Context) {
        val c = c0.applicationContext
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        Provider.values().forEach { p ->
            val u = if (Repo.active(c).contains(p)) Prefs.loadUsage(c, p) else null
            listOf(SESSION to u?.session, WEEK to u?.week).forEachIndexed { i, (key, l) ->
                val pi = PendingIntent.getBroadcast(
                    c, 300 + p.ordinal * 10 + i,
                    Intent(c, ResetReceiver::class.java).putExtra("p", p.id).putExtra("k", key),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                // сповіщаємо лише якщо ліміт справді витрачався
                if (enabled(c, key) && l != null && l.resetsAt > now && l.utilization > 0) {
                    val at = l.resetsAt + 20_000
                    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                        am.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pi)
                    } else {
                        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
                            .onFailure { am.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pi) }
                    }
                } else {
                    am.cancel(pi)
                }
            }
        }
    }

    /** Після оновлення: попередити, якщо в сесії лишилось ≤ 10% (раз на вікно сесії). */
    fun checkLow(c: Context, p: Provider, u: Usage) {
        if (!enabled(c, LOW)) return
        val s = u.session ?: return
        val now = System.currentTimeMillis()
        val left = 100 - s.used(now)
        val key = "notified_low_${p.id}"
        if (left > 10 || s.resetsAt <= now || sp(c).getLong(key, 0) == s.resetsAt) return
        sp(c).edit().putLong(key, s.resetsAt).apply()
        show(
            c, p, 400 + p.ordinal, CH_LOW,
            c.getString(R.string.nt_low_t, p.title, left),
            c.getString(R.string.nt_low_b, Fmt.resetAt(c, s.resetsAt, now)),
        )
    }

    fun show(c: Context, p: Provider, id: Int, channel: String, title: String, body: String) {
        if (!permitted(c)) return
        channels(c)
        val open = PendingIntent.getActivity(
            c, 500 + p.ordinal,
            Intent(c, MainActivity::class.java).putExtra(MainActivity.EXTRA_PROVIDER, p.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(c, channel)
            .setSmallIcon(p.icon)
            .setColor(if (p == Provider.CLAUDE) p.accent else 0xFF0D0D0D.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { c.getSystemService(NotificationManager::class.java)?.notify(id, n) }
    }

    fun showReset(c: Context, p: Provider, key: String) {
        if (key == SESSION) {
            show(c, p, 600 + p.ordinal, CH_RESETS, c.getString(R.string.nt_session_t, p.title), c.getString(R.string.nt_session_b))
        } else {
            show(c, p, 700 + p.ordinal, CH_RESETS, c.getString(R.string.nt_week_t, p.title), c.getString(R.string.nt_week_b))
        }
    }
}

/** Спрацьовує в момент скидання ліміту. */
class ResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = Lang.wrap(context)
        val p = Provider.byId(intent.getStringExtra("p"))
        val key = intent.getStringExtra("k") ?: return
        if (!Notify.enabled(c, key) || !Repo.isLoggedIn(c, p)) return
        Notify.showReset(c, p, key)
        // одразу підтягуємо свіжі дані для плиток і віджетів
        val pending = goAsync()
        Repo.refresh(c) { pending.finish() }
    }
}
