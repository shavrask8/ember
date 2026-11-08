package ua.shavra.claudelimits

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Знімок лімітів на момент оновлення. -1 — немає даних. */
class Snap(val t: Long, val sU: Double, val sR: Long, val wU: Double, val wR: Long)

class DayBar(val label: String, val value: Double, val today: Boolean)

class Stats(
    val days: List<DayBar>,      // витрата тижневого ліміту по днях (останні 7 днів)
    val sessions: Int,           // сесій із використанням за 7 днів
    val hitLimit: Int,           // скільки разів сесія дійшла до 100%
    val avgPeak: Int?,           // середній пік сесії, %
    val maxDay: Double,          // найбільша денна витрата, %
    val line: List<Pair<Long, Double>>, // використання сесії за 24 год
    val since: Long?,            // з якого часу є історія
)

/** Локальна історія знімків (файл CSV у пам'яті застосунку, ~15 днів). */
object History {
    private const val KEEP = 15L * 24 * 3600_000
    private const val H = 3600_000L

    private fun file(c: Context, p: Provider) =
        File(c.filesDir, if (p == Provider.CLAUDE) "history.csv" else "history_${p.id}.csv")

    fun load(c: Context, p: Provider): List<Snap> = runCatching {
        val f = file(c, p)
        if (!f.exists()) return emptyList()
        f.readLines().mapNotNull { line ->
            val p = line.split(',')
            if (p.size < 5) null
            else runCatching { Snap(p[0].toLong(), p[1].toDouble(), p[2].toLong(), p[3].toDouble(), p[4].toLong()) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun add(c: Context, p: Provider, u: Usage) {
        val s = Snap(
            u.updatedAt,
            u.session?.utilization ?: -1.0, u.session?.resetsAt ?: 0,
            u.week?.utilization ?: -1.0, u.week?.resetsAt ?: 0,
        )
        val list = load(c, p).filter { s.t - it.t < KEEP }.toMutableList()
        val last = list.lastOrNull()
        // не плодимо однакові знімки при частих оновленнях
        if (last != null && s.t - last.t < 120_000 && last.sU == s.sU && last.wU == s.wU) return
        list += s
        runCatching { file(c, p).writeText(list.joinToString("\n") { "${it.t},${it.sU},${it.sR},${it.wU},${it.wR}" }) }
    }

    fun clear(c: Context) {
        Provider.values().forEach { runCatching { file(c, it).delete() } }
    }

    /** Значення з урахуванням того, що час скидання вже минув. */
    private fun eff(u: Double, r: Long, t: Long) = if (r in 1..t) 0.0 else u.coerceIn(0.0, 100.0)

    fun stats(c: Context, p: Provider, now: Long): Stats {
        val h = load(c, p).sortedBy { it.t }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun date(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

        // --- витрата тижневого ліміту по днях ---
        val dayVals = DoubleArray(7)
        for (i in 1 until h.size) {
            val a = h[i - 1]
            val b = h[i]
            if (a.wU < 0 || b.wU < 0) continue
            val au = eff(a.wU, a.wR, a.t)
            val bu = eff(b.wU, b.wR, b.t)
            val newWindow = b.wR - a.wR > H || (a.wR in 1..b.t)
            val d = (if (newWindow) bu else bu - au).coerceAtLeast(0.0)
            val idx = ChronoUnit.DAYS.between(date(b.t), today).toInt()
            if (idx in 0..6) dayVals[6 - idx] += d
        }
        val dayFmt = DateTimeFormatter.ofPattern("EEE", Lang.locale(c))
        val days = (0..6).map { i ->
            val d = today.minusDays((6 - i).toLong())
            DayBar(dayFmt.format(d).trimEnd('.'), dayVals[i], i == 6)
        }

        // --- сесії: групуємо за часом скидання ---
        val peaks = h.filter { it.sR > 0 && it.sU >= 0 && it.sR - 5 * H >= now - 7 * 24 * H }
            .groupBy { it.sR / (10 * 60_000) }
            .values.map { g -> g.maxOf { eff(it.sU, it.sR, it.t) } }
            .filter { it > 0 }
        val avg = if (peaks.isEmpty()) null else peaks.average().roundToInt()

        // --- сесія за останні 24 години ---
        val line = h.filter { it.t >= now - 24 * H && it.sU >= 0 }.map { it.t to eff(it.sU, it.sR, it.t) }

        return Stats(
            days = days,
            sessions = peaks.size,
            hitLimit = peaks.count { it >= 99.5 },
            avgPeak = avg,
            maxDay = dayVals.maxOrNull() ?: 0.0,
            line = line,
            since = h.firstOrNull()?.t,
        )
    }
}
