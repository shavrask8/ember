using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;

namespace Ember
{
    /// <summary>Налаштування в стилі Windows 11: бічне меню і картки-рядки.</summary>
    public sealed class SettingsWindow : Window
    {
        readonly App app;
        Settings S => app.S;
        readonly StackPanel nav = new StackPanel();
        readonly StackPanel page = new StackPanel { MaxWidth = 720 };
        readonly ScrollViewer scroller;
        TextBlock subtitle;
        int current;
        BadgeView preview;
        TextBlock lastUpdated;

        static readonly int[] Steps = { 1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 120, 180, 360 };

        public SettingsWindow(App app)
        {
            this.app = app;
            Width = 880;
            Height = 660;
            MinWidth = 700;
            MinHeight = 480;
            WindowStartupLocation = WindowStartupLocation.CenterScreen;
            UseLayoutRounding = true;
            FontFamily = Pal.Sans;
            FontSize = 13;
            SetResourceReference(BackgroundProperty, "Bg");
            SetResourceReference(ForegroundProperty, "Text");
            try { Icon = BitmapFrame.Create(new Uri("pack://application:,,,/ember.ico")); } catch { }

            var grid = K.Cols(new GridLength(250), K.Star);

            // ліва колонка
            var left = new DockPanel { Margin = new Thickness(12, 10, 10, 14) };
            var brand = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(12, 8, 0, 20) };
            DockPanel.SetDock(brand, Dock.Top);
            brand.Children.Add(K.Logo("claude", 26).M(0, 0, 12, 0));
            var bt = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            var name = K.T("Ember", 21, "Text", FontWeights.SemiBold);
            name.FontFamily = Pal.Serif;
            bt.Children.Add(name);
            subtitle = K.T("", 12, "Text2");
            bt.Children.Add(subtitle);
            brand.Children.Add(bt);
            left.Children.Add(brand);
            var ver = K.T("v" + typeof(App).Assembly.GetName().Version.ToString(3), 11.5, "Text2").M(14, 0, 0, 0);
            DockPanel.SetDock(ver, Dock.Bottom);
            left.Children.Add(ver);
            left.Children.Add(nav);
            grid.Children.Add(left);

            scroller = new ScrollViewer
            {
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
                Padding = new Thickness(18, 0, 30, 30),
                Focusable = false,
                Content = page,
            }.At(1);
            grid.Children.Add(scroller);
            Content = grid;

            SourceInitialized += (_, _) => ApplyChrome();
            Reload();
        }

        public void ApplyChrome()
        {
            var h = new WindowInteropHelper(this).Handle;
            if (h == IntPtr.Zero) return;
            Dwm.Dark(h, Pal.AppsDark());
            if (TryFindResource("Bg") is SolidColorBrush bg && TryFindResource("Text") is SolidColorBrush tx) Dwm.Caption(h, bg.Color, tx.Color);
            if (TryFindResource("Border") is SolidColorBrush b) Dwm.BorderColor(h, b.Color);
            if (preview != null) RenderPreview();
        }

        // ---------- навігація ----------

        public void Reload()
        {
            Title = "Ember — " + L.T("settings");
            subtitle.Text = L.T("subtitle");
            nav.Children.Clear();
            string[] names = { L.T("s_services"), L.T("s_tray"), L.T("s_badge"), L.T("s_updates"), L.T("s_system") };
            string[] icons = { "", "", "", "", "" };
            for (int i = 0; i < names.Length; i++)
            {
                int k = i;
                var rb = new RadioButton { Content = names[i], Tag = icons[i], Style = K.S("Nav"), IsChecked = i == current };
                rb.Checked += (_, _) => ShowPage(k);
                nav.Children.Add(rb);
            }
            ShowPage(current);
        }

        void ShowPage(int i, bool animate = true)
        {
            current = i;
            page.Children.Clear();
            preview = null;
            lastUpdated = null;
            switch (i)
            {
                case 0: Services(); break;
                case 1: TrayPage(); break;
                case 2: BadgePage(); break;
                case 3: Updates(); break;
                default: SystemPage(); break;
            }
            if (!animate) return;
            scroller.ScrollToTop();
            page.BeginAnimation(OpacityProperty, new DoubleAnimation(0.2, 1, TimeSpan.FromMilliseconds(180)));
        }

        string servicesKey;

        string ServicesKey()
        {
            var k = S.CodexEnabled + "|";
            foreach (var p in Provs.All) k += S.IsLogged(p.Id) + app.Error(p) + app.Get(p)?.Updated.Ticks + "|";
            return k;
        }

        public void UpdateStatus()
        {
            if (current == 0 && ServicesKey() != servicesKey) ShowPage(0, false);
            if (preview != null) RenderPreview();
            if (lastUpdated != null) lastUpdated.Text = UpdatedText();
        }

        // ---------- будівельні блоки ----------

        void Header(string title, string desc)
        {
            var t = K.T(title, 28, "Text", FontWeights.SemiBold).M(2, 22, 0, 4);
            page.Children.Add(t);
            if (desc != null) page.Children.Add(K.Wrap(desc, 13).M(2, 0, 0, 14));
        }

        void Group(string title) => page.Children.Add(K.T(title, 14, "Text", FontWeights.SemiBold).M(2, 18, 0, 8));

        Border Row(string glyph, string title, string desc, UIElement ctrl, UIElement leading = null)
        {
            var g = K.Cols(K.Auto, K.Star, K.Auto);
            UIElement icon = leading ?? (glyph != null ? K.Icon(glyph, 17, "Text") : null);
            if (icon is FrameworkElement fe) { fe.Margin = new Thickness(2, 0, 16, 0); g.Children.Add(fe); }
            var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center }.At(1);
            text.Children.Add(K.T(title, 14));
            if (desc != null)
            {
                var d = K.Wrap(desc, 12).M(0, 2, 0, 0);
                if (desc == "") d.Visibility = Visibility.Collapsed;
                text.Children.Add(d);
            }
            g.Children.Add(text);
            if (ctrl is FrameworkElement c)
            {
                c.Margin = new Thickness(18, 0, 0, 0);
                c.VerticalAlignment = VerticalAlignment.Center;
                Grid.SetColumn(c, 2);
                g.Children.Add(c);
            }
            var b = new Border
            {
                Child = g,
                Padding = new Thickness(16, 13, 16, 13),
                CornerRadius = new CornerRadius(9),
                BorderThickness = new Thickness(1),
                Margin = new Thickness(0, 0, 0, 4),
                MinHeight = 64,
            };
            b.SetResourceReference(Border.BackgroundProperty, "Surface");
            b.SetResourceReference(Border.BorderBrushProperty, "Border");
            page.Children.Add(b);
            return b;
        }

        static FrameworkElement Seg(string[] labels, int sel, Action<int> on, bool enabled = true)
        {
            var box = new Border { CornerRadius = new CornerRadius(9), Padding = new Thickness(2), IsEnabled = enabled };
            box.SetResourceReference(Border.BackgroundProperty, "SegBg");
            var sp = new StackPanel { Orientation = Orientation.Horizontal };
            for (int i = 0; i < labels.Length; i++)
            {
                int k = i;
                var rb = new RadioButton { Content = labels[i], IsChecked = i == sel, Style = K.S("Seg") };
                rb.Checked += (_, _) => on(k);
                sp.Children.Add(rb);
            }
            box.Child = sp;
            return box;
        }

        static CheckBox Toggle(bool on, Action<bool> change)
        {
            var c = new CheckBox { IsChecked = on, Style = K.S("Toggle") };
            c.Checked += (_, _) => change(true);
            c.Unchecked += (_, _) => change(false);
            return c;
        }

        void Save() => S.Save();

        // ---------- сторінки ----------

        void Services()
        {
            Header(L.T("s_services"), L.T("s_services_d"));
            servicesKey = ServicesKey();
            foreach (var p in Provs.All)
            {
                bool logged = S.IsLogged(p.Id);
                var err = app.Error(p);
                var u = app.Get(p);

                var logo = new Border { Width = 40, Height = 40, CornerRadius = new CornerRadius(10) };
                logo.SetResourceReference(Border.BackgroundProperty, "SegBg");
                var lg = K.Logo(p.Id, 20);
                if (p.Id == "codex") lg.SetResourceReference(Logo.BrushProperty, "Text");
                lg.HorizontalAlignment = HorizontalAlignment.Center;
                logo.Child = lg;

                var buttons = new StackPanel { Orientation = Orientation.Horizontal };
                if (logged)
                {
                    if (err != null) buttons.Children.Add(K.Btn(L.T("s_sign_in_again"), () => app.Login(p)).M(0, 0, 8, 0));
                    buttons.Children.Add(K.Btn(L.T("s_sign_out"), () => { app.Logout(p); ShowPage(0); }));
                }
                else buttons.Children.Add(K.Btn(L.T("s_sign_in"), () => app.Login(p), true));

                string desc = p.Id == "codex" ? L.T("s_codex_d") : L.T("s_claude_d");
                var row = Row(null, p.Title, null, buttons, logo);

                // рядок статусу під назвою
                var text = (StackPanel)((Grid)row.Child).Children[1];
                var st = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 3, 0, 0) };
                var dot = new System.Windows.Shapes.Ellipse { Width = 7, Height = 7, Margin = new Thickness(0, 0, 7, 0), VerticalAlignment = VerticalAlignment.Center };
                dot.SetResourceReference(System.Windows.Shapes.Shape.FillProperty, err != null ? "Danger" : logged ? "Good" : "Text2");
                st.Children.Add(dot);
                string status = err ?? (logged ? L.T("s_connected") + (u != null ? " · " + string.Format(L.T("updated_at"), Fmt.Clock(u.Updated)).ToLower(L.Culture) : "") : desc);
                st.Children.Add(K.T(status, 12, err != null ? "Danger" : "Text2"));
                text.Children.Add(st);
            }
            Row("", L.T("s_track_codex"), L.T("s_track_codex_d"),
                Toggle(S.CodexEnabled, on => { S.CodexEnabled = on; Save(); app.UpdateViews(); }));

            var hint = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(4, 12, 0, 0) };
            hint.Children.Add(K.Icon("", 14).M(0, 1, 10, 0));
            var ht = K.Wrap(L.T("s_login_hint"), 12);
            ht.MaxWidth = 560;
            hint.Children.Add(ht);
            page.Children.Add(hint);
        }

        void TrayPage()
        {
            Header(L.T("s_tray"), L.T("s_tray_d"));
            Row("", L.T("s_show"), L.T("s_show_d"),
                Seg(new[] { L.T("s_left"), L.T("s_used") }, S.ShowUsed ? 1 : 0, i => { S.ShowUsed = i == 1; Save(); app.UpdateViews(); }));
            Row("", L.T("s_tray_shows"), L.T("s_tray_shows_d"),
                Seg(new[] { L.T("session").Split('·')[0].Trim(), L.T("week") }, S.TrayShows, i => { S.TrayShows = i; Save(); app.UpdateViews(); }));
            Row("", L.T("s_tray_service"), L.T("s_tray_service_d"),
                Seg(new[] { "Claude", "Codex" }, S.TrayProvider == "codex" ? 1 : 0, i => { S.TrayProvider = i == 1 ? "codex" : "claude"; Save(); app.UpdateViews(); }, S.CodexEnabled));
        }

        void BadgePage()
        {
            Header(L.T("s_badge"), L.T("s_badge_d"));

            // живий перегляд на «шпалерах»
            var stage = new Border { Height = 170, CornerRadius = new CornerRadius(12), Margin = new Thickness(0, 0, 0, 6), ClipToBounds = true };
            stage.Background = new LinearGradientBrush(Color.FromRgb(0x7A, 0x93, 0xC9), Color.FromRgb(0xD9, 0xA6, 0x9A), 25);
            var sg = new Grid();
            preview = new BadgeView { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, IsHitTestVisible = false };
            sg.Children.Add(preview);
            var cap = new TextBlock { Text = L.T("s_preview"), FontSize = 11.5, Foreground = new SolidColorBrush(Color.FromArgb(0xE0, 255, 255, 255)), Margin = new Thickness(14, 0, 0, 10), VerticalAlignment = VerticalAlignment.Bottom };
            sg.Children.Add(cap);
            stage.Child = sg;
            page.Children.Add(stage);
            RenderPreview();

            void Changed() { Save(); app.ApplyBadge(); RenderPreview(); }

            Row("", L.T("s_badge_show"), L.T("s_badge_show_d"), Toggle(S.BadgeVisible, on => { S.BadgeVisible = on; Changed(); }));
            Group(L.T("s_look"));
            Row("", L.T("s_style"), null,
                Seg(new[] { L.T("st_ring"), L.T("st_compact"), L.T("st_detail"), L.T("st_minimal") }, S.BadgeStyle, i => { S.BadgeStyle = i; Changed(); }));
            Row("", L.T("s_size"), null,
                Seg(new[] { L.T("sz_s"), L.T("sz_m"), L.T("sz_l") }, S.BadgeSize, i => { S.BadgeSize = i; Changed(); }));
            Row("", L.T("s_theme"), null,
                Seg(new[] { L.T("th_auto"), L.T("th_light"), L.T("th_dark"), L.T("th_accent") }, S.BadgeTheme, i => { S.BadgeTheme = i; Changed(); }));
            Row("", L.T("s_service"), L.T("s_service_d"),
                Seg(new[] { "Claude", "Codex" }, S.BadgeProvider == "codex" ? 1 : 0, i => { S.BadgeProvider = i == 1 ? "codex" : "claude"; Changed(); }, S.CodexEnabled));

            var op = new StackPanel { Orientation = Orientation.Horizontal };
            var val = K.T(S.BadgeOpacity + "%", 13, "Text2");
            val.Width = 44;
            val.TextAlignment = TextAlignment.Right;
            val.VerticalAlignment = VerticalAlignment.Center;
            var sl = new Slider { Minimum = 30, Maximum = 100, TickFrequency = 5, Value = S.BadgeOpacity, Width = 180, Style = K.S("Sld") };
            sl.ValueChanged += (_, e) => { S.BadgeOpacity = (int)e.NewValue; val.Text = S.BadgeOpacity + "%"; Changed(); };
            op.Children.Add(sl);
            op.Children.Add(val);
            Group(L.T("s_behavior"));
            Row("", L.T("s_opacity"), L.T("s_opacity_d"), op);
            Row("", L.T("s_lock"), L.T("s_lock_d"), Toggle(S.BadgeLocked, on => { S.BadgeLocked = on; Save(); }));
            Row("", L.T("s_reset_pos"), L.T("s_reset_pos_d"), K.Btn(L.T("s_reset"), () => app.ResetBadgePosition()));
        }

        void RenderPreview()
        {
            if (preview == null) return;
            var p = app.Pick(S.BadgeProvider);
            var u = app.Get(p) ?? Sample();
            preview.Render(S, p, u);
        }

        /// <summary>Приклад даних для перегляду, поки реальних ще немає.</summary>
        static Usage Sample() => new Usage
        {
            Updated = DateTime.Now,
            Session = new Limit { Util = 28, Reset = DateTime.Now.AddMinutes(136) },
            Week = new Limit { Util = 41, Reset = DateTime.Now.AddDays(3) },
        };

        string UpdatedText()
        {
            var t = app.LastUpdate();
            if (app.Busy) return L.T("updating");
            return t > DateTime.MinValue ? string.Format(L.T("updated_at"), Fmt.Clock(t)) : "";
        }

        void Updates()
        {
            Header(L.T("s_updates"), L.T("s_updates_d"));
            int idx = Array.IndexOf(Steps, S.IntervalMin);
            if (idx < 0) { idx = 5; for (int i = 0; i < Steps.Length; i++) if (Steps[i] <= S.IntervalMin) idx = i; }
            var box = new StackPanel { Orientation = Orientation.Horizontal };
            var val = K.T(Fmt.Minutes(Steps[idx]), 13, "Text", FontWeights.SemiBold);
            val.Width = 90;
            val.TextAlignment = TextAlignment.Right;
            val.VerticalAlignment = VerticalAlignment.Center;
            var sl = new Slider { Minimum = 0, Maximum = Steps.Length - 1, TickFrequency = 1, Value = idx, Width = 200, Style = K.S("Sld") };
            sl.ValueChanged += (_, e) =>
            {
                S.IntervalMin = Steps[(int)Math.Round(e.NewValue)];
                val.Text = Fmt.Minutes(S.IntervalMin);
                Save();
                app.ApplyInterval();
            };
            box.Children.Add(sl);
            box.Children.Add(val);
            Row("", L.T("s_every"), L.T("s_every_d"), box);

            var rb = Row("", L.T("s_refresh_now"), UpdatedText(), K.Btn(L.T("s_refresh"), () => { app.Refresh(); }));
            lastUpdated = (TextBlock)((StackPanel)((Grid)rb.Child).Children[1]).Children[1];
            lastUpdated.Visibility = Visibility.Visible;

            Group(L.T("s_notify"));
            Row("", L.T("s_n_session"), L.T("s_n_session_d"), Toggle(S.NotifySession, on => { S.NotifySession = on; Save(); }));
            Row("", L.T("s_n_week"), L.T("s_n_week_d"), Toggle(S.NotifyWeek, on => { S.NotifyWeek = on; Save(); }));
            Row("", L.T("s_n_low"), L.T("s_n_low_d"), Toggle(S.NotifyLow, on => { S.NotifyLow = on; Save(); }));
        }

        void SystemPage()
        {
            Header(L.T("s_system"), L.T("s_system_d"));
            Row("", L.T("s_autostart"), L.T("s_autostart_d"), Toggle(S.Autostart, on => { S.Autostart = on; Settings.SetAutostart(on); Save(); }));
            int li = S.Lang == "uk" ? 1 : S.Lang == "en" ? 2 : 0;
            Row("", L.T("s_lang"), L.T("s_lang_d"), Seg(new[] { L.T("lang_auto"), "Українська", "English" }, li, i =>
            {
                S.Lang = i == 1 ? "uk" : i == 2 ? "en" : "";
                Save();
                app.ReloadLanguage();
                Dispatcher.BeginInvoke(new Action(Reload));
            }));

            Group(L.T("s_about"));
            var logo = new Border { Width = 40, Height = 40, CornerRadius = new CornerRadius(10) };
            logo.SetResourceReference(Border.BackgroundProperty, "Accent");
            var lg = K.Logo("claude", 22, "OnAccent");
            lg.HorizontalAlignment = HorizontalAlignment.Center;
            logo.Child = lg;
            Row(null, "Ember " + typeof(App).Assembly.GetName().Version.ToString(3), L.T("s_footer"), null, logo);
            Row("", L.T("s_quit"), L.T("s_quit_d"), K.Btn(L.T("s_quit"), () => app.Quit()));
        }
    }
}
