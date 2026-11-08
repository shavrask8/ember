package ua.shavra.claudelimits

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Види плиток у швидких налаштуваннях. */
enum class TileKind(val title: Int, val desc: Int, val cls: Class<out TileService>, val label: Int) {
    SESSION(R.string.session, R.string.p_codex_session, UsageTileService::class.java, R.string.tile_session),
    WEEK(R.string.week, R.string.tile_week, WeekTileService::class.java, R.string.tile_week),
    BOTH(R.string.tk_both, R.string.tk_both_d, ComboTileService::class.java, R.string.tile_both),
    PACE(R.string.tk_pace, R.string.tk_pace_d, PaceTileService::class.java, R.string.tile_pace),
}

/** Спільна логіка плиток. Натискання — оновити, довге натискання — відкрити застосунок. */
abstract class BaseTile : TileService() {
    /** Повертає (назва, підпис) або null, якщо даних для цієї плитки немає. */
    abstract fun text(p: Provider, u: Usage, now: Long): Pair<String, String>?

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    private fun provider(): Provider {
        val p = Prefs.tileProvider(this)
        return if (Repo.isLoggedIn(this, p)) p else Provider.CLAUDE
    }

    override fun onStartListening() {
        render()
        if (Repo.isStale(this, 5)) Repo.refresh(this) { render() }
    }

    override fun onClick() {
        if (!Repo.isLoggedIn(this, provider())) {
            openApp()
            return
        }
        qsTile?.let {
            setSubtitle(it, getString(R.string.updating))
            it.updateTile()
        }
        Repo.refresh(this) { render() }
    }

    private fun render() {
        val t = qsTile ?: return
        val p = provider()
        val now = System.currentTimeMillis()
        t.icon = Icon.createWithResource(this, p.icon)
        val u = Prefs.loadUsage(this, p)
        val content = u?.let { text(p, it, now) }
        when {
            !Repo.isLoggedIn(this, p) -> {
                t.state = Tile.STATE_INACTIVE
                t.label = p.title
                setSubtitle(t, getString(R.string.sign_in_app))
            }
            content == null -> {
                t.state = Tile.STATE_ACTIVE
                t.label = p.title
                setSubtitle(t, Prefs.lastError(this, p) ?: getString(R.string.no_data))
            }
            else -> {
                t.state = Tile.STATE_ACTIVE
                t.label = content.first
                setSubtitle(t, content.second)
            }
        }
        t.updateTile()
    }

    private fun setSubtitle(t: Tile, text: String) {
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = text
    }

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(i)
        }
    }
}

/** Сесія. Назва класу збережена, щоб уже додана плитка не зникла. */
class UsageTileService : BaseTile() {
    override fun text(p: Provider, u: Usage, now: Long) = u.session?.let {
        "${p.title} · ${Fmt.shown(this, it, now)}%" to "${Fmt.word(this)} · ${Fmt.tileReset(this, it, now)}"
    }
}

class WeekTileService : BaseTile() {
    override fun text(p: Provider, u: Usage, now: Long) = u.week?.let {
        "${p.title} · ${getString(R.string.week_short)} ${Fmt.shown(this, it, now)}%" to
            "${Fmt.word(this)} · ${Fmt.tileReset(this, it, now)}"
    }
}

class ComboTileService : BaseTile() {
    override fun text(p: Provider, u: Usage, now: Long): Pair<String, String>? {
        val s = u.session ?: return null
        val w = u.week?.let { "${Fmt.shown(this, it, now)}%" } ?: "—"
        return "${p.title} ${Fmt.shown(this, s, now)}% · $w" to getString(R.string.tile_both_sub, Fmt.word(this))
    }
}

class PaceTileService : BaseTile() {
    override fun text(p: Provider, u: Usage, now: Long): Pair<String, String>? {
        val s = u.session ?: return null
        val label = getString(R.string.tile_pace_label, p.title)
        val pace = Fmt.sessionPace(this, s, now) ?: return label to getString(R.string.full_limit)
        return label to (if (pace.ok) "✓ " else "⚠ ") + pace.short
    }
}
