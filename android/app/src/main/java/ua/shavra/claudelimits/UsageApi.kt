package ua.shavra.claudelimits

import android.content.Context
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * Читання лімітів через неофіційні ендпоінти, які використовують самі вебсайти:
 *  - Claude:  claude.ai/api/organizations/{org}/usage  (сторінка Settings → Usage)
 *  - Codex:   chatgpt.com/backend-api/wham/usage        (те, що показує Codex у /status)
 * Запити йдуть через WebView, тому cookie беруться з CookieManager.
 */
object UsageApi {
    private const val CLAUDE = "https://claude.ai"
    private const val CHATGPT = "https://chatgpt.com"

    /** Викликати з головного потоку. cb(usage, помилка). */
    fun fetch(ctx: Context, p: Provider, cb: (Usage?, String?) -> Unit) = when (p) {
        Provider.CLAUDE -> fetchClaude(ctx, cb)
        Provider.CODEX -> fetchCodex(ctx, cb)
    }

    // ===================== Claude =====================

    private fun fetchClaude(ctx: Context, cb: (Usage?, String?) -> Unit) {
        val org = Prefs.orgId(ctx) ?: cookieOrg()
        if (org != null) {
            loadClaude(ctx, org, retry = true, cb)
        } else {
            findOrg(ctx) { id, err -> if (id == null) cb(null, err) else loadClaude(ctx, id, false, cb) }
        }
    }

    private fun loadClaude(ctx: Context, org: String, retry: Boolean, cb: (Usage?, String?) -> Unit) {
        WebFetch.load(ctx, "$CLAUDE/api/organizations/$org/usage") { text ->
            val o = obj(text)
            when {
                o != null && (o.has("five_hour") || o.has("seven_day")) -> cb(parseClaude(o), null)
                retry && o != null && !isAuthError(o) -> {
                    // організація могла змінитися — шукаємо заново
                    Prefs.setOrgId(ctx, null)
                    findOrg(ctx) { id, err -> if (id == null) cb(null, err) else loadClaude(ctx, id, false, cb) }
                }
                else -> cb(null, describe(ctx, text, o, "claude.ai"))
            }
        }
    }

    private fun findOrg(ctx: Context, cb: (String?, String?) -> Unit) {
        WebFetch.load(ctx, "$CLAUDE/api/organizations") { text ->
            val arr = text?.let { runCatching { JSONArray(it) }.getOrNull() }
            if (arr == null) {
                cb(null, describe(ctx, text, obj(text), "claude.ai"))
                return@load
            }
            if (arr.length() == 0) {
                cb(null, ctx.getString(R.string.err_org))
                return@load
            }
            var pick = arr.getJSONObject(0)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if ((o.optJSONArray("capabilities")?.toString() ?: "").contains("\"chat\"")) { pick = o; break }
            }
            val id = pick.optString("uuid").takeIf { it.isNotEmpty() }
            if (id != null) Prefs.setOrgId(ctx, id)
            cb(id, if (id == null) ctx.getString(R.string.err_org) else null)
        }
    }

    private fun parseClaude(o: JSONObject) = Usage(
        session = claudeLimit(o, "five_hour"),
        week = claudeLimit(o, "seven_day"),
        opus = claudeLimit(o, "seven_day_opus"),
        sonnet = claudeLimit(o, "seven_day_sonnet"),
        updatedAt = System.currentTimeMillis(),
    )

    private fun claudeLimit(o: JSONObject, key: String): Limit? {
        val x = o.optJSONObject(key) ?: return null
        val u = x.optDouble("utilization", Double.NaN)
        if (u.isNaN()) return null
        val r = x.optString("resets_at", "")
            .takeIf { it.isNotEmpty() && it != "null" }
            ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            ?: 0L
        return Limit(u, r)
    }

    private fun cookieOrg(): String? =
        runCatching { CookieManager.getInstance().getCookie(CLAUDE) }.getOrNull()
            ?.split(';')?.map { it.trim() }
            ?.firstOrNull { it.startsWith("lastActiveOrg=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }

    /** Для ручного введення ключа: кладемо cookie у WebView. */
    fun setSessionKey(key: String) {
        val cm = CookieManager.getInstance()
        cm.setCookie(CLAUDE, "sessionKey=$key; Domain=.claude.ai; Path=/; Secure")
        cm.flush()
    }

    // ===================== Codex (ChatGPT) =====================

    private fun fetchCodex(ctx: Context, cb: (Usage?, String?) -> Unit) {
        WebFetch.load(ctx, "$CHATGPT/api/auth/session") { text ->
            val s = obj(text)
            val token = s?.optString("accessToken")?.takeIf { it.isNotEmpty() }
            if (token == null) {
                cb(null, if (text == null) ctx.getString(R.string.err_no_conn, "chatgpt.com") else ctx.getString(R.string.err_chatgpt))
                return@load
            }
            val headers = mutableMapOf("Authorization" to "Bearer $token")
            s.optJSONObject("account")?.optString("id")?.takeIf { it.isNotEmpty() }
                ?.let { headers["ChatGPT-Account-Id"] = it }

            WebFetch.load(ctx, "$CHATGPT/backend-api/wham/usage", headers) { t2 ->
                val o = obj(t2)
                val rl = o?.optJSONObject("rate_limit")
                if (rl == null) {
                    cb(null, when {
                        t2 == null -> ctx.getString(R.string.err_no_conn, "chatgpt.com")
                        o != null && o.has("detail") -> ctx.getString(R.string.err_codex_na)
                        else -> describe(ctx, t2, o, "chatgpt.com")
                    })
                    return@load
                }
                val now = System.currentTimeMillis()
                cb(Usage(codexLimit(rl.optJSONObject("primary_window"), now), codexLimit(rl.optJSONObject("secondary_window"), now), null, null, now), null)
            }
        }
    }

    private fun codexLimit(x: JSONObject?, now: Long): Limit? {
        if (x == null) return null
        val used = x.optDouble("used_percent", Double.NaN)
        if (used.isNaN()) return null
        val at = x.optLong("reset_at", 0L)
        val reset = when {
            at > 10_000_000_000L -> at                // вже в мілісекундах
            at > 0 -> at * 1000                       // секунди
            x.optLong("reset_after_seconds", 0L) > 0 -> now + x.optLong("reset_after_seconds") * 1000
            else -> 0L
        }
        return Limit(used, reset)
    }

    // ===================== спільне =====================

    private fun obj(text: String?): JSONObject? = text?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun isAuthError(o: JSONObject): Boolean {
        val t = o.optJSONObject("error")?.optString("type") ?: ""
        return t == "authentication_error" || t == "permission_error" && o.toString().contains("auth", true)
    }

    private fun describe(c: Context, text: String?, o: JSONObject?, site: String): String {
        if (text == null) return c.getString(R.string.err_no_conn, site)
        if (o != null) {
            val type = o.optJSONObject("error")?.optString("type") ?: ""
            return when {
                type == "authentication_error" || type == "permission_error" -> c.getString(R.string.err_session)
                type == "rate_limit_error" -> c.getString(R.string.err_rate)
                else -> c.getString(R.string.err_unexpected)
            }
        }
        return if (text.contains("human", true) || text.contains("moment", true) || text.contains("cloudflare", true))
            c.getString(R.string.err_cf)
        else c.getString(R.string.err_unexpected)
    }
}
