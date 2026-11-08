// Ember для Windows — дані, налаштування, читання лімітів через браузер.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using Microsoft.Win32;

namespace Ember
{
    // ===================== налаштування =====================

    public class Settings
    {
        public bool CodexEnabled { get; set; }
        public List<string> Logged { get; set; } = new List<string>();
        public bool ShowUsed { get; set; }
        public int IntervalMin { get; set; } = 15;

        public bool BadgeVisible { get; set; } = true;
        public int BadgeStyle { get; set; } = 1;      // 0 кільце, 1 компактний, 2 детальний, 3 мінімальний
        public int BadgeSize { get; set; } = 1;       // 0 малий, 1 середній, 2 великий
        public int BadgeOpacity { get; set; } = 100;
        public int BadgeTheme { get; set; }           // 0 авто, 1 світла, 2 темна, 3 акцент
        public bool BadgeLocked { get; set; }
        public int BadgeX { get; set; } = -1;
        public int BadgeY { get; set; } = -1;
        public string BadgeProvider { get; set; } = "claude";

        public string TrayProvider { get; set; } = "claude";
        public int TrayShows { get; set; }            // 0 сесія, 1 тиждень

        public bool NotifySession { get; set; } = true;
        public bool NotifyWeek { get; set; } = true;
        public bool NotifyLow { get; set; } = true;
        public bool Autostart { get; set; }
        public string Lang { get; set; } = "";        // "" — як у системі

        public static string Dir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Ember");
        static string FilePath => Path.Combine(Dir, "settings.json");
        static readonly JsonSerializerOptions Opt = new JsonSerializerOptions { WriteIndented = true };

        public static Settings Load()
        {
            try
            {
                if (File.Exists(FilePath))
                {
                    var s = JsonSerializer.Deserialize<Settings>(File.ReadAllText(FilePath, Encoding.UTF8), Opt);
                    if (s != null) { s.Logged ??= new List<string>(); return s; }
                }
            }
            catch { }
            return new Settings();
        }

        /// <summary>Демо-режим (для перевірки інтерфейсу): нічого не зберігається.</summary>
        public static bool Demo;

        public void Save()
        {
            if (Demo) return;
            try
            {
                Directory.CreateDirectory(Dir);
                File.WriteAllText(FilePath, JsonSerializer.Serialize(this, Opt), Encoding.UTF8);
            }
            catch { }
        }

        public bool IsLogged(string id) => Logged.Contains(id);
        public bool IsEnabled(string id) => id == "claude" || (id == "codex" && CodexEnabled);

        /// <summary>Автозапуск з Windows (HKCU\...\Run).</summary>
        public static void SetAutostart(bool on)
        {
            try
            {
                using var k = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run", true);
                if (k == null) return;
                if (on) k.SetValue("Ember", "\"" + Environment.ProcessPath + "\" --tray");
                else k.DeleteValue("Ember", false);
            }
            catch { }
        }
    }

    // ===================== сервіси =====================

    public class Prov
    {
        public string Id, Title, Origin, LoginUrl, Js;
        public uint Accent;
    }

    public static class Provs
    {
        public static readonly Prov Claude = new Prov
        {
            Id = "claude",
            Title = "Claude",
            Origin = "https://claude.ai/api/organizations",
            LoginUrl = "https://claude.ai/login",
            Accent = 0xD97757,
            Js = "(async()=>{try{const r=await fetch('/api/organizations');if(!r.ok)return JSON.stringify({__error:'http '+r.status});" +
                 "const o=await r.json();const org=o.find(x=>JSON.stringify(x.capabilities||[]).includes('\"chat\"'))||o[0];" +
                 "if(!org)return JSON.stringify({__error:'org'});const u=await fetch('/api/organizations/'+org.uuid+'/usage');" +
                 "if(!u.ok)return JSON.stringify({__error:'http '+u.status});return await u.text();}" +
                 "catch(e){return JSON.stringify({__error:String(e)})}})()",
        };

        public static readonly Prov Codex = new Prov
        {
            Id = "codex",
            Title = "Codex",
            Origin = "https://chatgpt.com/api/auth/session",
            LoginUrl = "https://chatgpt.com/auth/login",
            Accent = 0x0D0D0D,
            Js = "(async()=>{try{const s=await (await fetch('/api/auth/session')).json();if(!s.accessToken)return JSON.stringify({__error:'auth'});" +
                 "const h={'Authorization':'Bearer '+s.accessToken};if(s.account&&s.account.id)h['ChatGPT-Account-Id']=s.account.id;" +
                 "const r=await fetch('/backend-api/wham/usage',{headers:h});if(!r.ok)return JSON.stringify({__error:'http '+r.status});" +
                 "return await r.text();}catch(e){return JSON.stringify({__error:String(e)})}})()",
        };

        public static readonly Prov[] All = { Claude, Codex };

        public static Prov Get(string id)
        {
            foreach (var p in All) if (p.Id == id) return p;
            return Claude;
        }
    }

    // ===================== дані =====================

    public class Limit
    {
        public double Util;
        public DateTime? Reset;

        public int Used(DateTime now)
        {
            if (Reset.HasValue && Reset.Value <= now) return 0;
            return (int)Math.Round(Math.Max(0, Math.Min(100, Util)));
        }

        public int Shown(bool showUsed, DateTime now) => showUsed ? Used(now) : 100 - Used(now);
        public bool Low(DateTime now) => 100 - Used(now) <= 15;
        public bool Active(DateTime now) => Reset.HasValue && Reset.Value > now;
    }

    public class Usage
    {
        public Limit Session, Week, Opus, Sonnet;
        public DateTime Updated;
    }

    // ===================== розбір відповідей =====================

    public static class Api
    {
        static string Str(JsonNode n) => n is JsonValue v && v.TryGetValue<string>(out var s) ? s : null;

        static double? Num(JsonNode n)
        {
            if (n is not JsonValue v) return null;
            if (v.TryGetValue<double>(out var d)) return d;
            if (v.TryGetValue<long>(out var l)) return l;
            if (v.TryGetValue<string>(out var s) && double.TryParse(s, NumberStyles.Float, CultureInfo.InvariantCulture, out d)) return d;
            return null;
        }

        public static Usage Parse(Prov p, string text, out string error)
        {
            error = null;
            if (string.IsNullOrEmpty(text)) { error = L.T("err_conn"); return null; }
            if (text.TrimStart().StartsWith("<") || text.Contains("Just a moment")) { error = L.T("err_cf"); return null; }
            JsonObject o;
            try { o = JsonNode.Parse(text) as JsonObject; } catch { o = null; }
            if (o == null) { error = L.T("err_unexpected"); return null; }

            var err = Str(o["__error"]);
            if (err != null)
            {
                if (err.Contains("401") || err.Contains("403") || err == "auth") error = L.T("err_login");
                else if (err == "org") error = L.T("err_org");
                else if (err.Contains("429")) error = L.T("err_rate");
                else if (err.Contains("Failed to fetch") || err.Contains("NetworkError")) error = L.T("err_conn");
                else error = L.T("err_unexpected");
                return null;
            }
            if (Str(o["type"]) == "error") { error = L.T("err_login"); return null; }

            var u = new Usage { Updated = DateTime.Now };
            if (p.Id == "claude")
            {
                u.Session = ClaudeLimit(o, "five_hour");
                u.Week = ClaudeLimit(o, "seven_day");
                u.Opus = ClaudeLimit(o, "seven_day_opus");
                u.Sonnet = ClaudeLimit(o, "seven_day_sonnet");
            }
            else
            {
                if (o["rate_limit"] is not JsonObject rl) { error = L.T("err_codex"); return null; }
                u.Session = CodexLimit(rl["primary_window"] as JsonObject);
                u.Week = CodexLimit(rl["secondary_window"] as JsonObject);
            }
            if (u.Session == null && u.Week == null) { error = L.T("err_unexpected"); return null; }
            return u;
        }

        static Limit ClaudeLimit(JsonObject o, string key)
        {
            if (o[key] is not JsonObject d) return null;
            var util = Num(d["utilization"]);
            if (util == null) return null;
            var l = new Limit { Util = util.Value };
            var r = Str(d["resets_at"]);
            if (r != null && DateTimeOffset.TryParse(r, CultureInfo.InvariantCulture, DateTimeStyles.None, out var dt)) l.Reset = dt.LocalDateTime;
            return l;
        }

        static Limit CodexLimit(JsonObject d)
        {
            if (d == null) return null;
            var used = Num(d["used_percent"]);
            if (used == null) return null;
            var l = new Limit { Util = used.Value };
            var at = Num(d["reset_at"]);
            var after = Num(d["reset_after_seconds"]);
            if (at != null)
            {
                long s = (long)at.Value;
                if (s > 10000000000L) s /= 1000;
                if (s > 0) l.Reset = DateTimeOffset.FromUnixTimeSeconds(s).LocalDateTime;
            }
            else if (after != null) l.Reset = DateTime.Now.AddSeconds(after.Value);
            return l;
        }
    }

    // ===================== браузер (Edge / Chrome) =====================

    /// <summary>
    /// Справжній браузер у невидимому режимі: його пропускає Cloudflare, а cookie беруться
    /// з окремого профілю Ember, у який користувач один раз входить. Браузер живе лише
    /// кілька секунд під час оновлення і одразу закривається.
    /// </summary>
    public static class Browser
    {
        static Process login;
        static readonly HttpClient http = new HttpClient { Timeout = TimeSpan.FromSeconds(2) };

        public static string Exe()
        {
            string[] c =
            {
                @"%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe",
                @"%ProgramFiles%\Microsoft\Edge\Application\msedge.exe",
                @"%ProgramFiles%\Google\Chrome\Application\chrome.exe",
                @"%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe",
                @"%LocalAppData%\Google\Chrome\Application\chrome.exe",
            };
            foreach (var p in c)
            {
                var x = Environment.ExpandEnvironmentVariables(p);
                if (File.Exists(x)) return x;
            }
            return null;
        }

        public static string Profile =>
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Ember", "browser");

        public static bool LoginRunning
        {
            get { try { return login != null && !login.HasExited; } catch { return false; } }
        }

        /// <summary>User-Agent як у звичайного (не headless) браузера тієї ж версії.</summary>
        static string Ua(string exe)
        {
            string ver = "142.0.0.0";
            try
            {
                Version best = null;
                foreach (var d in Directory.GetDirectories(Path.GetDirectoryName(exe)))
                    if (Version.TryParse(Path.GetFileName(d), out var v) && (best == null || v > best)) best = v;
                if (best != null) ver = best.Major + ".0.0.0";
            }
            catch { }
            bool edge = exe.ToLowerInvariant().Contains("msedge");
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + ver +
                   " Safari/537.36" + (edge ? " Edg/" + ver : "");
        }

        public static void Login(string url, EventHandler exited)
        {
            var exe = Exe() ?? throw new Exception(L.T("no_browser"));
            Directory.CreateDirectory(Profile);
            var psi = new ProcessStartInfo(exe,
                "--user-data-dir=\"" + Profile + "\" --no-first-run --no-default-browser-check --window-size=520,780 --app=" + url)
            { UseShellExecute = false };
            login = Process.Start(psi);
            login.EnableRaisingEvents = true;
            login.Exited += exited;
        }

        static int FreePort()
        {
            var l = new TcpListener(IPAddress.Loopback, 0);
            l.Start();
            int port = ((IPEndPoint)l.LocalEndpoint).Port;
            l.Stop();
            return port;
        }

        /// <summary>Відкриває кожен сервіс у невидимому браузері й виконує його JS. Результат — JSON-текст.</summary>
        public static Dictionary<string, string> Run(IList<Prov> provs)
        {
            var res = new Dictionary<string, string>();
            var exe = Exe() ?? throw new Exception(L.T("no_browser"));
            Directory.CreateDirectory(Profile);
            int port = FreePort();
            var psi = new ProcessStartInfo(exe,
                "--headless=new --disable-gpu --no-first-run --no-default-browser-check --mute-audio --disable-extensions " +
                "--disable-background-networking --disable-component-update --disable-sync " +
                "--user-data-dir=\"" + Profile + "\" --remote-debugging-port=" + port +
                " --user-agent=\"" + Ua(exe) + "\" about:blank")
            { UseShellExecute = false, CreateNoWindow = true };
            var proc = Process.Start(psi);
            try
            {
                // процес браузера — з низьким пріоритетом, щоб не заважати роботі
                try { proc.PriorityClass = ProcessPriorityClass.BelowNormal; } catch { }

                string wsUrl = null;
                var sw = Stopwatch.StartNew();
                while (wsUrl == null && sw.ElapsedMilliseconds < 20000)
                {
                    try
                    {
                        var text = http.GetStringAsync("http://127.0.0.1:" + port + "/json/list").GetAwaiter().GetResult();
                        if (JsonNode.Parse(text) is JsonArray list)
                            foreach (var t in list)
                                if ((string)t?["type"] == "page") { wsUrl = (string)t["webSocketDebuggerUrl"]; break; }
                    }
                    catch { }
                    if (wsUrl == null) Thread.Sleep(250);
                }
                if (wsUrl == null) throw new Exception(L.T("err_browser"));

                using var cdp = new Cdp(wsUrl);
                foreach (var p in provs)
                {
                    try
                    {
                        cdp.Call("Page.navigate", new JsonObject { ["url"] = p.Origin }, 20000);
                        WaitReady(cdp);
                        res[p.Id] = cdp.Eval(p.Js, 30000);
                    }
                    catch (Exception e)
                    {
                        res[p.Id] = new JsonObject { ["__error"] = e.Message }.ToJsonString();
                    }
                }
            }
            finally
            {
                Kill(proc);
            }
            return res;
        }

        static void WaitReady(Cdp cdp)
        {
            var sw = Stopwatch.StartNew();
            Thread.Sleep(400);
            while (sw.ElapsedMilliseconds < 25000)
            {
                string s = null;
                try { s = cdp.Eval("document.readyState+'|'+document.title", 5000); } catch { }
                if (s != null && s.StartsWith("complete") && !s.Contains("Just a moment")) return;
                Thread.Sleep(500);
            }
        }

        static void Kill(Process p)
        {
            try { p.Kill(true); p.WaitForExit(4000); } catch { }
        }

        /// <summary>Вийти: якщо не лишилося підключених сервісів — стираємо весь профіль браузера.</summary>
        public static void WipeProfile()
        {
            try { if (Directory.Exists(Profile)) Directory.Delete(Profile, true); } catch { }
        }
    }

    /// <summary>Мінімальний клієнт Chrome DevTools Protocol через WebSocket.</summary>
    public sealed class Cdp : IDisposable
    {
        readonly ClientWebSocket ws = new ClientWebSocket();
        int id;

        public Cdp(string url)
        {
            if (!ws.ConnectAsync(new Uri(url), CancellationToken.None).Wait(10000)) throw new Exception("CDP connect timeout");
        }

        public JsonObject Call(string method, JsonObject prm, int timeoutMs)
        {
            int my = ++id;
            var msg = new JsonObject { ["id"] = my, ["method"] = method, ["params"] = prm ?? new JsonObject() }.ToJsonString();
            var bytes = Encoding.UTF8.GetBytes(msg);
            ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, CancellationToken.None).Wait(10000);
            var sw = Stopwatch.StartNew();
            while (sw.ElapsedMilliseconds < timeoutMs)
            {
                var text = Receive(timeoutMs - (int)sw.ElapsedMilliseconds);
                if (text == null) break;
                JsonObject o = null;
                try { o = JsonNode.Parse(text) as JsonObject; } catch { }
                if (o != null && o["id"] is JsonValue v && v.TryGetValue<int>(out var rid) && rid == my) return o;
            }
            throw new Exception("CDP timeout: " + method);
        }

        public string Eval(string js, int timeoutMs)
        {
            var r = Call("Runtime.evaluate", new JsonObject { ["expression"] = js, ["awaitPromise"] = true, ["returnByValue"] = true }, timeoutMs);
            var v = r?["result"]?["result"]?["value"];
            if (v == null) return null;
            return v is JsonValue jv && jv.TryGetValue<string>(out var s) ? s : v.ToJsonString();
        }

        string Receive(int timeoutMs)
        {
            if (timeoutMs <= 0) return null;
            var buf = new byte[65536];
            using var ms = new MemoryStream();
            using var cts = new CancellationTokenSource(timeoutMs);
            while (true)
            {
                WebSocketReceiveResult r;
                try { r = ws.ReceiveAsync(new ArraySegment<byte>(buf), cts.Token).GetAwaiter().GetResult(); }
                catch { return null; }
                ms.Write(buf, 0, r.Count);
                if (r.EndOfMessage) break;
            }
            return Encoding.UTF8.GetString(ms.ToArray());
        }

        public void Dispose()
        {
            try { ws.Abort(); ws.Dispose(); } catch { }
        }
    }

    // ===================== час і текст =====================

    public static class Fmt
    {
        public static string Clock(DateTime t) => t.ToString("HH:mm");

        public static string ResetAt(DateTime t)
        {
            var days = (t.Date - DateTime.Now.Date).Days;
            if (days == 0) return string.Format(L.T("at"), Clock(t));
            if (days == 1) return string.Format(L.T("tomorrow_at"), Clock(t));
            if (days < 7) return t.ToString("ddd HH:mm", L.Culture);
            return t.ToString("d MMM HH:mm", L.Culture);
        }

        public static string ResetShort(DateTime t)
        {
            var days = (t.Date - DateTime.Now.Date).Days;
            if (days == 0) return Clock(t);
            if (days < 7) return t.ToString("ddd HH:mm", L.Culture);
            return t.ToString("d MMM", L.Culture);
        }

        public static string Until(DateTime t)
        {
            var m = (long)Math.Ceiling((t - DateTime.Now).TotalMinutes);
            if (m < 0) m = 0;
            long d = m / 1440, h = (m % 1440) / 60, mm = m % 60;
            if (d > 0) return string.Format(L.T("dur_dh"), d, h);
            if (h > 0) return string.Format(L.T("dur_hm"), h, mm);
            return string.Format(L.T("dur_m"), mm);
        }

        /// <summary>Компактно для значка: «2:16» або «3д».</summary>
        public static string UntilTiny(DateTime t)
        {
            var m = (long)Math.Ceiling((t - DateTime.Now).TotalMinutes);
            if (m < 0) m = 0;
            if (m >= 1440) return string.Format(L.T("tiny_d"), m / 1440);
            return (m / 60) + ":" + (m % 60).ToString("00");
        }

        public static string Minutes(int min)
        {
            if (min < 60) return string.Format(L.T("dur_m"), min);
            if (min % 60 == 0) return string.Format(L.T("dur_h"), min / 60);
            return string.Format(L.T("dur_hm"), min / 60, min % 60);
        }

        public static string Word(bool used) => L.T(used ? "used" : "left");
    }
}
