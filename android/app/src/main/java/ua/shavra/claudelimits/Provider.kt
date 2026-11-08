package ua.shavra.claudelimits

/** Сервіси, ліміти яких уміє показувати застосунок. Тексти — ресурси (перекладаються). */
enum class Provider(
    val id: String,
    val title: String,
    val loginUrl: String,
    val cookieUrl: String,
    /** Cookie, поява якої означає успішний вхід. */
    val loginCookie: String,
    val accent: Int,
    val icon: Int,
    val sessionTitle: Int,
    val sessionShort: Int,
    val weekRow: Int,
    val note: Int,
) {
    CLAUDE(
        "claude", "Claude", "https://claude.ai/login", "https://claude.ai", "sessionKey=",
        0xFFD97757.toInt(), R.drawable.ic_spark,
        R.string.p_claude_session, R.string.session, R.string.all_models, R.string.p_claude_note,
    ),
    CODEX(
        "codex", "Codex", "https://chatgpt.com/auth/login", "https://chatgpt.com", "next-auth.session-token",
        0xFF0D0D0D.toInt(), R.drawable.ic_codex,
        R.string.p_codex_session, R.string.h5, R.string.all_models, R.string.p_codex_note,
    );

    /** Префікс ключів у налаштуваннях (у Claude порожній — для сумісності). */
    val prefix: String get() = if (this == CLAUDE) "" else "${id}_"

    companion object {
        fun of(i: Int): Provider = values().getOrElse(i) { CLAUDE }
        fun byId(s: String?): Provider = values().firstOrNull { it.id == s } ?: CLAUDE
    }
}
