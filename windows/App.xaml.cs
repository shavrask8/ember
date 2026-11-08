using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Interop;
using System.Windows.Threading;
using Microsoft.Win32;

namespace Ember
{
    /// <summary>Головний «мозок»: значок у треї, таймери, оновлення, сповіщення.</summary>
    public partial class App : Application
    {
        public Settings S;
        public readonly Dictionary<string, Usage> Data = new Dictionary<string, Usage>();
        public readonly Dictionary<string, string> Errors = new Dictionary<string, string>();
        public bool Busy;
        public DateTime NextRefresh;

        Tray tray;
        PanelWindow panel;
        BadgeWindow badge;
        SettingsWindow settings;
        DispatcherTimer refreshTimer, tickTimer;
        readonly HashSet<string> notified = new HashSet<string>();
        Mutex mutex;
        EventWaitHandle showSignal;

        protected override void OnStartup(StartupEventArgs e)
        {
            mutex = new Mutex(true, "Ember.Tray.SingleInstance", out bool created);
            if (!created)
            {
                // вже запущено — просимо першу копію відкрити налаштування
                try { EventWaitHandle.OpenExisting("Ember.Tray.Show").Set(); } catch { }
                Shutdown();
                return;
            }
            base.OnStartup(e);
            DispatcherUnhandledException += (_, a) => { Log(a.Exception); a.Handled = true; };

            S = Settings.Load();
            L.Init(S.Lang);
            if (e.Args.Contains("--en")) L.Init("en");
            ApplyTheme();
            SystemEvents.UserPreferenceChanged += (_, a) =>
            {
                if (a.Category == UserPreferenceCategory.General || a.Category == UserPreferenceCategory.Color)
                    Dispatcher.BeginInvoke(new Action(ApplyTheme));
            };

            tray = new Tray(this);
            panel = new PanelWindow(this);
            badge = new BadgeWindow(this);
            badge.Render();

            refreshTimer = new DispatcherTimer(DispatcherPriority.Background);
            refreshTimer.Tick += (_, _) => Refresh();
            ApplyInterval();
            tickTimer = new DispatcherTimer(DispatcherPriority.Background) { Interval = TimeSpan.FromSeconds(30) };
            tickTimer.Tick += (_, _) => { CheckResets(); UpdateViews(); };
            tickTimer.Start();

            showSignal = new EventWaitHandle(false, EventResetMode.AutoReset, "Ember.Tray.Show");
            ThreadPool.RegisterWaitForSingleObject(showSignal, (_, _) => Dispatcher.BeginInvoke(new Action(OpenSettings)), null, -1, false);

            bool silent = e.Args.Contains("--tray");
            if (e.Args.Contains("--demo"))
            {
                Settings.Demo = true;
                S.Logged = new List<string> { "claude", "codex" };
                S.CodexEnabled = true;
                S.ShowUsed = false;
                S.IntervalMin = 15;
                S.BadgeTheme = 0;
                S.BadgeSize = 1;
                S.BadgeOpacity = 100;
                var n = DateTime.Now;
                Data["claude"] = new Usage { Updated = n, Session = new Limit { Util = 28, Reset = n.AddMinutes(136) }, Week = new Limit { Util = 41, Reset = n.AddDays(3).AddHours(5) }, Opus = new Limit { Util = 12, Reset = n.AddDays(3) } };
                Data["codex"] = new Usage { Updated = n, Session = new Limit { Util = 91, Reset = n.AddMinutes(48) }, Week = new Limit { Util = 63, Reset = n.AddDays(5) } };
                foreach (var a in e.Args) if (int.TryParse(a, out var st)) S.BadgeStyle = st;
                UpdateViews();
                if (e.Args.Contains("--panel")) panel.ShowNear();
                return;
            }
            if (S.Logged.Count == 0)
            {
                tray.Balloon(L.T("welcome_t"), L.T("welcome_b"));
                if (!silent) OpenSettings();
            }
            else Refresh();
        }

        static void Log(Exception ex)
        {
            try
            {
                Directory.CreateDirectory(Settings.Dir);
                File.AppendAllText(Path.Combine(Settings.Dir, "error.log"), DateTime.Now + "  " + ex + "\r\n\r\n");
            }
            catch { }
        }

        // ---------- тема ----------

        public void ApplyTheme()
        {
            var pal = Pal.Get("claude", 0);
            pal.Into(Resources);
            settings?.ApplyChrome();
            panel?.ApplyChrome();
            tray?.Update();
            badge?.Render();
        }

        // ---------- провайдери ----------

        public List<Prov> Active() => Provs.All.Where(p => S.IsEnabled(p.Id) && S.IsLogged(p.Id)).ToList();
        public List<Prov> Enabled() => Provs.All.Where(p => S.IsEnabled(p.Id)).ToList();

        public Prov Pick(string id)
        {
            var p = Provs.Get(id);
            return S.IsEnabled(p.Id) ? p : Provs.Claude;
        }

        public Usage Get(Prov p) => Data.TryGetValue(p.Id, out var u) ? u : null;
        public string Error(Prov p) => Errors.TryGetValue(p.Id, out var e) ? e : null;

        public DateTime LastUpdate() => Data.Values.Select(u => u.Updated).DefaultIfEmpty(DateTime.MinValue).Max();

        // ---------- оновлення ----------

        public void Refresh()
        {
            if (Busy || Browser.LoginRunning) return;
            var list = Active();
            NextRefresh = DateTime.Now.AddMinutes(Math.Max(1, S.IntervalMin));
            if (list.Count == 0) { UpdateViews(); return; }
            Busy = true;
            UpdateViews();
            Task.Run(() =>
            {
                Dictionary<string, string> res = null;
                string fatal = null;
                try { res = Browser.Run(list); }
                catch (Exception e) { fatal = e.Message; }
                Dispatcher.BeginInvoke(new Action(() =>
                {
                    Busy = false;
                    foreach (var p in list)
                    {
                        if (fatal != null) { Errors[p.Id] = fatal; continue; }
                        res.TryGetValue(p.Id, out var text);
                        var u = Api.Parse(p, text, out var err);
                        if (u != null) { Data[p.Id] = u; Errors.Remove(p.Id); CheckLow(p, u); }
                        else Errors[p.Id] = err;
                    }
                    UpdateViews(true);
                    Trim();
                }));
            });
        }

        /// <summary>Повертає системі пам’ять, яка більше не потрібна (після оновлення або закриття вікон).</summary>
        public static void Trim()
        {
            GC.Collect(2, GCCollectionMode.Optimized, false, true);
            try { Native.EmptyWorkingSet(System.Diagnostics.Process.GetCurrentProcess().Handle); } catch { }
        }

        public void ApplyInterval()
        {
            refreshTimer.Stop();
            refreshTimer.Interval = TimeSpan.FromMinutes(Math.Max(1, S.IntervalMin));
            NextRefresh = DateTime.Now + refreshTimer.Interval;
            refreshTimer.Start();
        }

        /// <param name="fresh">true — прийшли нові дані (тоді кільця плавно анімуються)</param>
        public void UpdateViews(bool fresh = false)
        {
            tray?.Update();
            badge?.Render();
            if (panel != null && panel.IsVisible) panel.Update(fresh);
            settings?.UpdateStatus();
        }

        public string Tip(int max)
        {
            var now = DateTime.Now;
            var parts = new List<string>();
            foreach (var p in Active())
            {
                var u = Get(p);
                if (u == null) { parts.Add(p.Title + ": —"); continue; }
                string s = p.Title;
                if (u.Session != null) s += " " + u.Session.Shown(S.ShowUsed, now) + "%";
                if (u.Week != null) s += " · " + L.T("tip_week") + " " + u.Week.Shown(S.ShowUsed, now) + "%";
                if (u.Session != null && u.Session.Active(now)) s += " · " + Fmt.ResetShort(u.Session.Reset.Value);
                parts.Add(s);
            }
            var t = parts.Count == 0 ? "Ember" : string.Join("\n", parts);
            if (Busy) t = L.T("updating") + "\n" + t;
            return t.Length > max ? t.Substring(0, max - 1) + "…" : t;
        }

        // ---------- сповіщення ----------

        void CheckResets()
        {
            var now = DateTime.Now;
            bool refresh = false;
            foreach (var p in Active())
            {
                var u = Get(p);
                if (u == null) continue;
                if (Reset(p, u.Session, "s", S.NotifySession, now)) { tray.Balloon(string.Format(L.T("n_session_t"), p.Title), L.T("n_session_b")); refresh = true; }
                if (Reset(p, u.Week, "w", S.NotifyWeek, now)) { tray.Balloon(string.Format(L.T("n_week_t"), p.Title), L.T("n_week_b")); refresh = true; }
            }
            if (refresh) Refresh();
        }

        bool Reset(Prov p, Limit l, string key, bool on, DateTime now)
        {
            if (l == null || !l.Reset.HasValue || l.Reset.Value > now || l.Util <= 0) return false;
            var id = p.Id + key + l.Reset.Value.Ticks;
            l.Util = 0;
            return on && notified.Add(id);
        }

        void CheckLow(Prov p, Usage u)
        {
            var s = u.Session;
            var now = DateTime.Now;
            if (!S.NotifyLow || s == null || !s.Active(now)) return;
            int left = 100 - s.Used(now);
            if (left > 10) return;
            if (notified.Add("low" + p.Id + s.Reset.Value.Ticks))
                tray.Balloon(string.Format(L.T("n_low_t"), p.Title, left), string.Format(L.T("n_low_b"), Fmt.ResetAt(s.Reset.Value)));
        }

        // ---------- вхід / вихід ----------

        public void Login(Prov p)
        {
            if (Browser.LoginRunning) return;
            try
            {
                Browser.Login(p.LoginUrl, (_, _) => Dispatcher.BeginInvoke(new Action(() =>
                {
                    if (!S.Logged.Contains(p.Id)) S.Logged.Add(p.Id);
                    if (p.Id == "codex") S.CodexEnabled = true;
                    S.Save();
                    Refresh();
                    UpdateViews();
                })));
            }
            catch (Exception e)
            {
                MessageBox.Show(e.Message, "Ember", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }

        public void Logout(Prov p)
        {
            S.Logged.Remove(p.Id);
            Data.Remove(p.Id);
            Errors.Remove(p.Id);
            S.Save();
            if (S.Logged.Count == 0) Browser.WipeProfile();
            UpdateViews();
        }

        // ---------- вікна ----------

        public void TogglePanel()
        {
            if (panel.IsVisible) { panel.HideNow(); return; }
            if ((DateTime.Now - panel.HiddenAt).TotalMilliseconds < 300) return; // щойно закрилась цим самим кліком
            panel.ShowNear();
            if (DateTime.Now - LastUpdate() > TimeSpan.FromMinutes(1)) Refresh();
        }

        public void OpenSettings()
        {
            if (settings == null)
            {
                settings = new SettingsWindow(this);
                settings.Closed += (_, _) => { settings = null; Dispatcher.BeginInvoke(new Action(Trim), DispatcherPriority.ApplicationIdle); };
                settings.Show();
            }
            if (settings.WindowState == WindowState.Minimized) settings.WindowState = WindowState.Normal;
            settings.Activate();
        }

        public void ApplyBadge() => badge.Render();
        public void ResetBadgePosition() => badge.ResetPosition();

        public void ReloadLanguage()
        {
            L.Init(S.Lang);
            UpdateViews();
            panel.Rebuild();
        }

        /// <summary>Меню (правий клік по трею або значку) у стилі Windows 11.</summary>
        public void ShowMenu()
        {
            var m = new ContextMenu { Placement = PlacementMode.MousePoint };
            MenuItem Item(string text, string glyph, Action a, bool? check = null, bool enabled = true)
            {
                var it = new MenuItem { Header = text, Tag = glyph, IsEnabled = enabled };
                if (check.HasValue) it.IsChecked = check.Value;
                it.Click += (_, _) => a();
                m.Items.Add(it);
                return it;
            }
            Item(L.T("m_open"), "", TogglePanel);
            Item(L.T("m_refresh"), "", Refresh, null, !Busy && Active().Count > 0);
            m.Items.Add(new Separator());
            Item(L.T("m_badge"), "", () => { S.BadgeVisible = !S.BadgeVisible; S.Save(); ApplyBadge(); settings?.Reload(); }, S.BadgeVisible);
            Item(L.T("m_lock"), "", () => { S.BadgeLocked = !S.BadgeLocked; S.Save(); settings?.Reload(); }, S.BadgeLocked, S.BadgeVisible);
            m.Items.Add(new Separator());
            Item(L.T("m_settings"), "", OpenSettings);
            Item(L.T("m_exit"), "", Quit);
            m.IsOpen = true;
            // щоб меню закривалось кліком повз нього
            if (PresentationSource.FromVisual(m) is HwndSource src) Dwm.SetForegroundWindow(src.Handle);
        }

        public void Quit()
        {
            tray?.Dispose();
            Shutdown();
        }
    }

    static class Native
    {
        [System.Runtime.InteropServices.DllImport("psapi.dll")] public static extern bool EmptyWorkingSet(IntPtr h);
    }
}
