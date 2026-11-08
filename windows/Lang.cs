using System.Collections.Generic;
using System.Globalization;

namespace Ember
{
    /// <summary>Тексти: українська та англійська. Мова — як у Windows або обрана в налаштуваннях.</summary>
    public static class L
    {
        public static CultureInfo Culture = CultureInfo.CurrentUICulture;
        static Dictionary<string, string> cur;

        public static void Init(string lang)
        {
            string code = string.IsNullOrEmpty(lang) ? CultureInfo.CurrentUICulture.TwoLetterISOLanguageName : lang;
            bool uk = code == "uk" || code == "ru" || code == "be";
            cur = uk ? UK : EN;
            Culture = new CultureInfo(uk ? "uk-UA" : "en-US");
        }

        public static string T(string key)
        {
            if (cur == null) Init("");
            if (cur.TryGetValue(key, out var v)) return v;
            if (EN.TryGetValue(key, out v)) return v;
            return key;
        }

        static readonly Dictionary<string, string> UK = new Dictionary<string, string>
        {
            {"subtitle", "Ліміти ШІ"},
            {"used", "використано"}, {"left", "залишилось"},
            {"session", "Сесія · 5 год"}, {"week", "Тиждень"}, {"h5", "5 годин"},
            {"at", "о {0}"}, {"tomorrow_at", "завтра о {0}"},
            {"dur_dh", "{0} дн {1} год"}, {"dur_hm", "{0} год {1} хв"}, {"dur_h", "{0} год"}, {"dur_m", "{0} хв"}, {"tiny_d", "{0}д"},
            {"reset_in", "Скидання {0}"}, {"in", "через {0}"}, {"reset_short", "скидання {0}"},
            {"full", "Ліміт повний"}, {"not_started", "Відлік ще не почався"},
            {"updated_at", "Оновлено о {0}"}, {"updating", "Оновлення…"},
            {"not_connected", "Не підключено. Увійдіть, щоб бачити ліміти."},
            {"no_data", "Ще немає даних"},
            {"err_conn", "Немає з’єднання"}, {"err_cf", "Cloudflare просить перевірку — увійдіть знову"},
            {"err_unexpected", "Неочікувана відповідь сервера"}, {"err_login", "Сесія недійсна — увійдіть знову"},
            {"err_org", "Організацію не знайдено"}, {"err_rate", "Забагато запитів, спробуйте пізніше"},
            {"err_codex", "Codex недоступний для цього акаунта"}, {"err_browser", "Не вдалося запустити браузер"},
            {"no_browser", "Не знайдено Microsoft Edge або Google Chrome"},
            {"login_failed", "Вхід не завершено — спробуйте ще раз і закрийте вікно, коли побачите свої чати"}, {"login_wait", "Увійдіть у вікні браузера, потім закрийте його"},
            {"w_opus", "Opus · тиждень"}, {"w_sonnet", "Sonnet · тиждень"},
            // меню
            {"m_open", "Відкрити панель"}, {"m_refresh", "Оновити зараз"}, {"m_badge", "Плаваючий значок"},
            {"m_settings", "Налаштування"}, {"m_exit", "Вийти з Ember"}, {"m_lock", "Закріпити значок"},
            // налаштування
            {"settings", "Налаштування"},
            {"s_services", "Акаунти"}, {"s_services_d", "Ember читає ліміти з ваших акаунтів через окремий захищений профіль браузера."},
            {"s_connected", "Підключено"}, {"s_not_connected", "Не підключено"},
            {"s_sign_in", "Увійти"}, {"s_sign_in_again", "Увійти знову"}, {"s_sign_out", "Вийти"},
            {"s_claude_d", "Ліміти claude.ai: сесія, тиждень, Opus"},
            {"s_codex_d", "Ліміти Codex у ChatGPT · експериментально"},
            {"s_track_codex", "Відстежувати Codex"}, {"s_track_codex_d", "Показувати Codex у панелі, треї та значку"},
            {"s_login_hint", "Відкриється окреме вікно браузера. Увійдіть і просто закрийте його — Ember підхопить вхід."},
            {"s_tray", "Трей і панель"}, {"s_tray_d", "Значок біля годинника і панель, яка відкривається кліком по ньому."},
            {"s_show", "Відсотки показують"}, {"s_show_d", "Скільки ще можна використати або скільки вже витрачено"},
            {"s_left", "Залишок"}, {"s_used", "Використане"},
            {"s_tray_shows", "Число на значку"}, {"s_tray_shows_d", "Який ліміт показувати в треї"},
            {"s_tray_service", "Сервіс у треї"}, {"s_tray_service_d", "Чиї ліміти показує значок"},
            {"s_badge", "Плаваючий значок"}, {"s_badge_d", "Маленьке віконце поверх усіх програм. Перетягуйте мишею, двічі клацніть — відкриється панель."},
            {"s_badge_show", "Показувати значок"}, {"s_badge_show_d", "Завжди поверх інших вікон"},
            {"s_style", "Стиль"}, {"s_look", "Вигляд"}, {"s_behavior", "Поведінка"}, {"st_ring", "Кільце"}, {"st_compact", "Компактний"}, {"st_detail", "Детальний"}, {"st_minimal", "Мінімальний"},
            {"s_size", "Розмір"}, {"sz_s", "Малий"}, {"sz_m", "Середній"}, {"sz_l", "Великий"},
            {"s_theme", "Тема"}, {"th_auto", "Авто"}, {"th_light", "Світла"}, {"th_dark", "Темна"}, {"th_accent", "Акцент"},
            {"s_opacity", "Непрозорість"}, {"s_opacity_d", "При наведенні миші значок стає повністю видимим"},
            {"s_lock", "Закріпити"}, {"s_lock_d", "Щоб випадково не зсунути. Клік відкриє панель"},
            {"s_service", "Сервіс"}, {"s_service_d", "Чиї ліміти показує значок"},
            {"s_reset_pos", "Повернути на місце"}, {"s_reset_pos_d", "У правий верхній кут екрана"}, {"s_reset", "Повернути"},
            {"s_updates", "Оновлення"}, {"s_updates_d", "Як часто перевіряти ліміти і про що повідомляти."},
            {"s_every", "Перевіряти кожні"}, {"s_every_d", "Кожна перевірка на кілька секунд запускає прихований браузер з низьким пріоритетом"},
            {"s_refresh_now", "Оновити зараз"}, {"s_refresh", "Оновити"},
            {"s_notify", "Сповіщення"},
            {"s_n_session", "Ліміт сесії знову повний"}, {"s_n_session_d", "Коли 5-годинне вікно скинулось"},
            {"s_n_week", "Тижневий ліміт оновився"}, {"s_n_week_d", "Коли почався новий тиждень"},
            {"s_n_low", "Ліміт закінчується"}, {"s_n_low_d", "Коли в сесії залишилось 10% або менше"},
            {"s_system", "Система"}, {"s_system_d", "Запуск, мова та інформація про програму."},
            {"s_autostart", "Запускати разом з Windows"}, {"s_autostart_d", "Ember тихо з’явиться в треї після входу"},
            {"s_lang", "Мова"}, {"s_lang_d", "Мова інтерфейсу Ember"}, {"lang_auto", "Як у Windows"},
            {"s_about", "Про програму"},
            {"s_footer", "Неофіційний застосунок. Дані беруться з ваших облікових записів і зберігаються лише на цьому комп’ютері."},
            {"s_quit", "Закрити Ember"}, {"s_quit_d", "Програма зникне з трею до наступного запуску"},
            {"s_preview", "Так значок виглядатиме на робочому столі"},
            // сповіщення
            {"n_session_t", "{0}: ліміт сесії знову повний"}, {"n_session_b", "Знову доступно 100% — можна працювати."},
            {"n_week_t", "{0}: тижневий ліміт оновився"}, {"n_week_b", "Новий тиждень — повний ліміт."},
            {"n_low_t", "{0}: залишилось {1}% сесії"}, {"n_low_b", "Скидання {0}"},
            {"welcome_t", "Ember працює в треї"}, {"welcome_b", "Увійдіть у Claude в налаштуваннях, щоб бачити ліміти."},
            {"tip_week", "тиждень"},
        };

        static readonly Dictionary<string, string> EN = new Dictionary<string, string>
        {
            {"subtitle", "AI usage limits"},
            {"used", "used"}, {"left", "left"},
            {"session", "Session · 5h"}, {"week", "Week"}, {"h5", "5 hours"},
            {"at", "at {0}"}, {"tomorrow_at", "tomorrow at {0}"},
            {"dur_dh", "{0}d {1}h"}, {"dur_hm", "{0}h {1}m"}, {"dur_h", "{0}h"}, {"dur_m", "{0} min"}, {"tiny_d", "{0}d"},
            {"reset_in", "Resets {0}"}, {"in", "in {0}"}, {"reset_short", "resets {0}"},
            {"full", "Full limit"}, {"not_started", "Countdown not started"},
            {"updated_at", "Updated at {0}"}, {"updating", "Updating…"},
            {"not_connected", "Not connected. Sign in to see your limits."},
            {"no_data", "No data yet"},
            {"err_conn", "No connection"}, {"err_cf", "Cloudflare wants a check — sign in again"},
            {"err_unexpected", "Unexpected server response"}, {"err_login", "Session expired — sign in again"},
            {"err_org", "Organization not found"}, {"err_rate", "Too many requests, try later"},
            {"err_codex", "Codex isn’t available for this account"}, {"err_browser", "Couldn’t start the browser"},
            {"no_browser", "Microsoft Edge or Google Chrome not found"},
            {"login_failed", "Sign-in wasn’t completed — try again and close the window once you see your chats"}, {"login_wait", "Sign in in the browser window, then close it"},
            {"w_opus", "Opus · week"}, {"w_sonnet", "Sonnet · week"},
            {"m_open", "Open panel"}, {"m_refresh", "Refresh now"}, {"m_badge", "Floating badge"},
            {"m_settings", "Settings"}, {"m_exit", "Quit Ember"}, {"m_lock", "Lock badge"},
            {"settings", "Settings"},
            {"s_services", "Accounts"}, {"s_services_d", "Ember reads limits from your accounts through a separate, private browser profile."},
            {"s_connected", "Connected"}, {"s_not_connected", "Not connected"},
            {"s_sign_in", "Sign in"}, {"s_sign_in_again", "Sign in again"}, {"s_sign_out", "Sign out"},
            {"s_claude_d", "claude.ai limits: session, week, Opus"},
            {"s_codex_d", "Codex limits in ChatGPT · experimental"},
            {"s_track_codex", "Track Codex"}, {"s_track_codex_d", "Show Codex in the panel, tray and badge"},
            {"s_login_hint", "A separate browser window will open. Sign in and simply close it — Ember picks it up."},
            {"s_tray", "Tray & panel"}, {"s_tray_d", "The icon next to the clock and the panel it opens."},
            {"s_show", "Percentages show"}, {"s_show_d", "What’s left to use, or how much is already used"},
            {"s_left", "Remaining"}, {"s_used", "Used"},
            {"s_tray_shows", "Number on the icon"}, {"s_tray_shows_d", "Which limit the tray shows"},
            {"s_tray_service", "Tray service"}, {"s_tray_service_d", "Whose limits the icon shows"},
            {"s_badge", "Floating badge"}, {"s_badge_d", "A tiny window above all apps. Drag it with the mouse, double-click to open the panel."},
            {"s_badge_show", "Show badge"}, {"s_badge_show_d", "Always on top of other windows"},
            {"s_style", "Style"}, {"s_look", "Appearance"}, {"s_behavior", "Behavior"}, {"st_ring", "Ring"}, {"st_compact", "Compact"}, {"st_detail", "Detailed"}, {"st_minimal", "Minimal"},
            {"s_size", "Size"}, {"sz_s", "Small"}, {"sz_m", "Medium"}, {"sz_l", "Large"},
            {"s_theme", "Theme"}, {"th_auto", "Auto"}, {"th_light", "Light"}, {"th_dark", "Dark"}, {"th_accent", "Accent"},
            {"s_opacity", "Opacity"}, {"s_opacity_d", "The badge becomes fully visible on hover"},
            {"s_lock", "Lock"}, {"s_lock_d", "So it can’t be moved by accident. Click opens the panel"},
            {"s_service", "Service"}, {"s_service_d", "Whose limits the badge shows"},
            {"s_reset_pos", "Reset position"}, {"s_reset_pos_d", "Back to the top-right corner"}, {"s_reset", "Reset"},
            {"s_updates", "Updates"}, {"s_updates_d", "How often to check limits and what to tell you about."},
            {"s_every", "Check every"}, {"s_every_d", "Each check briefly runs a hidden low-priority browser"},
            {"s_refresh_now", "Refresh now"}, {"s_refresh", "Refresh"},
            {"s_notify", "Notifications"},
            {"s_n_session", "Session limit is full again"}, {"s_n_session_d", "When the 5-hour window resets"},
            {"s_n_week", "Weekly limit renewed"}, {"s_n_week_d", "When a new week starts"},
            {"s_n_low", "Running low"}, {"s_n_low_d", "When 10% or less of the session is left"},
            {"s_system", "System"}, {"s_system_d", "Startup, language and about."},
            {"s_autostart", "Start with Windows"}, {"s_autostart_d", "Ember quietly appears in the tray after sign-in"},
            {"s_lang", "Language"}, {"s_lang_d", "Ember interface language"}, {"lang_auto", "Like Windows"},
            {"s_about", "About"},
            {"s_footer", "Unofficial app. Data comes from your own accounts and stays on this computer."},
            {"s_quit", "Quit Ember"}, {"s_quit_d", "Removes it from the tray until next launch"},
            {"s_preview", "This is how the badge will look on your desktop"},
            {"n_session_t", "{0}: session limit is full again"}, {"n_session_b", "100% available — back to work."},
            {"n_week_t", "{0}: weekly limit renewed"}, {"n_week_b", "A new week with the full limit."},
            {"n_low_t", "{0}: {1}% of the session left"}, {"n_low_b", "Resets {0}"},
            {"welcome_t", "Ember is in the tray"}, {"welcome_b", "Sign in to Claude in settings to see your limits."},
            {"tip_week", "week"},
        };
    }
}
