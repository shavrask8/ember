package ua.shavra.claudelimits

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Форматування відсотків, часу й темпу мовою застосунку. */
object Fmt {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    private fun zoned(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    fun clock(ms: Long): String = hm.format(zoned(ms))

    /** Відсоток, який показуємо: залишок або використане — залежно від налаштування. */
    fun shown(c: Context, l: Limit, now: Long): Int =
        if (Prefs.showUsed(c)) l.used(now) else 100 - l.used(now)

    fun word(c: Context, used: Boolean = Prefs.showUsed(c)) =
        c.getString(if (used) R.string.w_used else R.string.w_left)

    fun isLow(l: Limit, now: Long) = 100 - l.used(now) <= 15

    private fun days(ms: Long, now: Long) =
        ChronoUnit.DAYS.between(zoned(now).toLocalDate(), zoned(ms).toLocalDate())

    /** «о 19:59», «завтра о 19:59», «сб, 11:59», «15 лист., 11:59». */
    fun resetAt(c: Context, ms: Long, now: Long): String {
        val t = zoned(ms)
        val d = days(ms, now)
        val loc = Lang.locale(c)
        return when {
            d == 0L -> c.getString(R.string.at_time, hm.format(t))
            d == 1L -> c.getString(R.string.tomorrow_at, hm.format(t))
            d < 7 -> DateTimeFormatter.ofPattern("EEE, HH:mm", loc).format(t)
            else -> DateTimeFormatter.ofPattern("d MMM, HH:mm", loc).format(t)
        }
    }

    /** Коротко, без прийменника: «19:59», «сб, 11:59». */
    fun resetShort(c: Context, ms: Long, now: Long): String {
        val t = zoned(ms)
        val d = days(ms, now)
        val loc = Lang.locale(c)
        return when {
            d == 0L -> hm.format(t)
            d < 7 -> DateTimeFormatter.ofPattern("EEE, HH:mm", loc).format(t)
            else -> DateTimeFormatter.ofPattern("d MMM", loc).format(t)
        }
    }

    fun until(c: Context, ms: Long, now: Long): String {
        val m = ((ms - now + 59_999) / 60_000).coerceAtLeast(0)
        val d = (m / 1440).toInt()
        val h = ((m % 1440) / 60).toInt()
        val mm = (m % 60).toInt()
        return when {
            d > 0 -> c.getString(R.string.dur_dh, d, h)
            h > 0 -> c.getString(R.string.dur_hm, h, mm)
            else -> c.getString(R.string.dur_m, mm)
        }
    }

    fun intervalText(c: Context, min: Int): String = when {
        min < 60 -> c.getString(R.string.dur_m, min)
        min % 60 == 0 -> c.getString(R.string.dur_h, min / 60)
        else -> c.getString(R.string.dur_hm, min / 60, min % 60)
    }

    fun resetLine(c: Context, l: Limit, now: Long): String = when {
        l.resetsAt <= 0 -> c.getString(R.string.reset_not_started)
        l.resetsAt <= now -> c.getString(R.string.reset_done)
        else -> c.getString(R.string.reset_line, resetAt(c, l.resetsAt, now), until(c, l.resetsAt, now))
    }

    /** Коротко для плитки: «до 19:59». */
    fun tileReset(c: Context, l: Limit, now: Long): String = when {
        l.resetsAt <= now -> c.getString(R.string.full_limit)
        else -> c.getString(R.string.until_time, resetShort(c, l.resetsAt, now))
    }

    // ---------- темп витрати ----------

    class Pace(val ok: Boolean, val text: String, val short: String)

    private const val H = 3600_000L
    const val SESSION = 5 * H
    const val WEEK = 7 * 24 * H

    /** Яка частка вікна ліміту вже минула (0..1), або -1. */
    fun elapsed(l: Limit, window: Long, now: Long): Float {
        if (l.resetsAt <= now) return -1f
        return ((now - (l.resetsAt - window)).toFloat() / window).coerceIn(0f, 1f)
    }

    fun sessionPace(c: Context, l: Limit, now: Long): Pace? {
        if (l.resetsAt <= now) return null
        val used = l.used(now)
        if (used >= 100) {
            val t = c.getString(R.string.pace_exhausted, clock(l.resetsAt))
            return Pace(false, t, t)
        }
        if (used <= 0) {
            val t = c.getString(R.string.pace_unused)
            return Pace(true, t, t)
        }
        val elapsed = now - (l.resetsAt - SESSION)
        if (elapsed < 5 * 60_000) return null
        val exhaust = now + ((100 - used) * elapsed.toDouble() / used).toLong()
        return if (exhaust < l.resetsAt) {
            Pace(false, c.getString(R.string.pace_runs_out, clock(exhaust)), c.getString(R.string.pace_runs_out_short, clock(exhaust)))
        } else {
            Pace(true, c.getString(R.string.pace_enough), c.getString(R.string.pace_enough_short))
        }
    }

    fun weekPace(c: Context, l: Limit, now: Long): Pace? {
        val f = elapsed(l, WEEK, now)
        if (f < 0) return null
        val used = l.used(now)
        val time = (f * 100).toInt()
        val t = c.getString(R.string.week_pace, used, time)
        return Pace(used <= time + 10, t, t)
    }
}
