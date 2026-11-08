using System;
using System.Collections.Generic;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Shell;
using WF = System.Windows.Forms;

namespace Ember
{
    /// <summary>Панель біля трею: картка для кожного сервісу.</summary>
    public sealed class PanelWindow : Window
    {
        readonly App app;
        readonly Dictionary<string, ProviderCard> cards = new Dictionary<string, ProviderCard>();
        StackPanel cardsBox;
        TextBlock subtitle, status;
        Button refreshBtn;
        RotateTransform spin;
        Grid root;
        public DateTime HiddenAt = DateTime.MinValue;

        public PanelWindow(App app)
        {
            this.app = app;
            WindowStyle = WindowStyle.SingleBorderWindow;   // рамка лишається для тіні DWM, але не малюється
            ResizeMode = ResizeMode.NoResize;
            ShowInTaskbar = false;
            Topmost = true;
            Width = 380;
            UseLayoutRounding = true;
            Title = "Ember";
            WindowChrome.SetWindowChrome(this, new WindowChrome
            {
                CaptionHeight = 0,
                GlassFrameThickness = new Thickness(0),
                ResizeBorderThickness = new Thickness(0),
                CornerRadius = new CornerRadius(0),
                UseAeroCaptionButtons = false,
            });
            SetResourceReference(BackgroundProperty, "Bg");
            SetResourceReference(ForegroundProperty, "Text");
            FontFamily = Pal.Sans;
            Build();

            SourceInitialized += (_, _) => { Dwm.ToolWindow(new WindowInteropHelper(this).Handle, false); ApplyChrome(); };
            new WindowInteropHelper(this).EnsureHandle();
            Deactivated += (_, _) => HideNow();
            PreviewKeyDown += (_, e) => { if (e.Key == Key.Escape) HideNow(); };
        }

        public void ApplyChrome()
        {
            var h = new WindowInteropHelper(this).Handle;
            if (h == IntPtr.Zero) return;
            Dwm.Round(h);
            Dwm.Dark(h, Pal.AppsDark());
            if (TryFindResource("Border") is SolidColorBrush b) Dwm.BorderColor(h, b.Color);
        }

        void Build()
        {
            root = new Grid { RenderTransform = new TranslateTransform() };
            var stack = new StackPanel { Margin = new Thickness(14, 12, 14, 12) };

            // шапка
            var head = K.Cols(K.Auto, K.Star, K.Auto).M(4, 0, 0, 12);
            head.Children.Add(K.Logo("claude", 22).M(0, 0, 10, 0));
            var titles = new StackPanel { VerticalAlignment = VerticalAlignment.Center }.At(1);
            var name = K.T("Ember", 19, "Text", FontWeights.SemiBold);
            name.FontFamily = Pal.Serif;
            titles.Children.Add(name);
            subtitle = K.T("", 12, "Text2");
            titles.Children.Add(subtitle);
            head.Children.Add(titles);
            var tools = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center }.At(2);
            spin = new RotateTransform();
            var icon = K.Icon("", 15, "Text2");
            icon.RenderTransformOrigin = new Point(0.5, 0.5);
            icon.RenderTransform = spin;
            refreshBtn = new Button { Content = icon, Style = K.S("IconBtn") };
            refreshBtn.Click += (_, _) => app.Refresh();
            tools.Children.Add(refreshBtn);
            tools.Children.Add(K.IconBtn("", null, () => { HideNow(); app.OpenSettings(); }));
            head.Children.Add(tools);
            stack.Children.Add(head);

            cardsBox = new StackPanel();
            stack.Children.Add(cardsBox);

            status = K.T("", 12, "Text2").M(4, 10, 0, 0);
            stack.Children.Add(status);

            root.Children.Add(stack);
            Content = root;
        }

        public void Rebuild()
        {
            cards.Clear();
            cardsBox.Children.Clear();
            if (IsVisible) Update(false);
        }

        public void Update(bool fresh)
        {
            subtitle.Text = L.T("subtitle");
            refreshBtn.ToolTip = L.T("m_refresh");
            var list = app.Enabled();
            var keep = new HashSet<string>();
            int i = 0;
            foreach (var p in list)
            {
                keep.Add(p.Id);
                if (!cards.TryGetValue(p.Id, out var c))
                {
                    c = new ProviderCard(app, p);
                    cards[p.Id] = c;
                }
                if (cardsBox.Children.IndexOf(c) != i)
                {
                    cardsBox.Children.Remove(c);
                    cardsBox.Children.Insert(i, c);
                }
                c.Margin = new Thickness(0, i == 0 ? 0 : 10, 0, 0);
                c.Update(fresh);
                i++;
            }
            foreach (var id in new List<string>(cards.Keys))
                if (!keep.Contains(id)) { cardsBox.Children.Remove(cards[id]); cards.Remove(id); }

            // підвал
            var last = app.LastUpdate();
            if (app.Busy) status.Text = L.T("updating");
            else if (last > DateTime.MinValue) status.Text = string.Format(L.T("updated_at"), Fmt.Clock(last));
            else status.Text = "";
            refreshBtn.IsEnabled = !app.Busy && app.Active().Count > 0;
            Spin(app.Busy);
            FitHeight();
        }

        void Spin(bool on)
        {
            if (on)
            {
                var a = new DoubleAnimation(0, 360, TimeSpan.FromSeconds(1)) { RepeatBehavior = RepeatBehavior.Forever };
                spin.BeginAnimation(RotateTransform.AngleProperty, a);
            }
            else spin.BeginAnimation(RotateTransform.AngleProperty, null);
        }

        void FitHeight()
        {
            root.Measure(new Size(Width, double.PositiveInfinity));
            double h = Math.Ceiling(root.DesiredSize.Height);
            if (Math.Abs(Height - h) > 0.5 || double.IsNaN(Height))
            {
                double bottom = Top + (double.IsNaN(Height) ? 0 : Height);
                Height = h;
                if (IsVisible && anchoredBottom) Top = bottom - h;
            }
        }

        bool anchoredBottom = true;

        public void ShowNear()
        {
            Update(true);
            // екран, де зараз курсор (тобто де трей), і з якого боку панель задач
            var scr = WF.Screen.FromPoint(WF.Cursor.Position);
            double k = VisualTreeHelper.GetDpi(this).DpiScaleX;
            if (k <= 0) k = 1;
            var wa = scr.WorkingArea;
            var b = scr.Bounds;
            double m = 12;
            double l = wa.Left / k, t = wa.Top / k, r = wa.Right / k, bt = wa.Bottom / k;
            anchoredBottom = !(wa.Top > b.Top);
            Left = wa.Left > b.Left ? l + m : r - Width - m;
            Top = anchoredBottom ? bt - Height - m : t + m;

            Show();
            Activate();
            Animate();
        }

        void Animate()
        {
            var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
            var tt = (TranslateTransform)root.RenderTransform;
            tt.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(anchoredBottom ? 18 : -18, 0, TimeSpan.FromMilliseconds(220)) { EasingFunction = ease });
            root.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(160)));
        }

        public void HideNow()
        {
            if (!IsVisible) return;
            HiddenAt = DateTime.Now;
            Spin(false);
            Hide();
        }
    }

    /// <summary>Картка сервісу: велике кільце сесії, час скидання, тиждень і окремі моделі.</summary>
    public sealed class ProviderCard : Border
    {
        readonly App app;
        readonly Prov p;
        readonly TextBlock err, outText, wait, pct, word, sLabel, sReset, sIn, wLabel, wPct, wReset;
        readonly Button signIn;
        readonly Ring ring;
        readonly Bar wBar;
        readonly StackPanel outBox, extra;
        readonly Grid dataBox;

        public ProviderCard(App app, Prov p)
        {
            this.app = app;
            this.p = p;
            CornerRadius = new CornerRadius(12);
            BorderThickness = new Thickness(1);
            Padding = new Thickness(16, 14, 16, 16);
            SetResourceReference(BackgroundProperty, "Surface");
            SetResourceReference(BorderBrushProperty, "Border");
            Resources["NumFont"] = Pal.NumFont(p.Id);
            if (p.Id == "codex") Pal.Set(Resources, "Accent", Pal.AccentFor("codex", Pal.AppsDark()));

            var col = new StackPanel();

            // заголовок
            var head = K.Cols(K.Auto, K.Auto, K.Star);
            head.Children.Add(K.Logo(p.Id, 18).M(0, 0, 9, 0));
            var title = K.T(p.Title, 16, "Text", FontWeights.SemiBold).At(1);
            title.SetResourceReference(TextBlock.FontFamilyProperty, "NumFont");
            title.VerticalAlignment = VerticalAlignment.Center;
            head.Children.Add(title);
            err = K.T("", 11.5, "Danger").At(2).M(12, 0, 0, 0);
            err.HorizontalAlignment = HorizontalAlignment.Right;
            err.VerticalAlignment = VerticalAlignment.Center;
            head.Children.Add(err);
            col.Children.Add(head);

            // не підключено
            outBox = new StackPanel { Margin = new Thickness(0, 10, 0, 0) };
            outText = K.Wrap("", 13);
            outBox.Children.Add(outText);
            signIn = K.Btn("", () => { (Window.GetWindow(this) as PanelWindow)?.HideNow(); app.Login(p); }, true);
            signIn.HorizontalAlignment = HorizontalAlignment.Left;
            signIn.Margin = new Thickness(0, 12, 0, 0);
            outBox.Children.Add(signIn);
            col.Children.Add(outBox);

            wait = K.T("", 13, "Text2").M(0, 12, 0, 0);
            col.Children.Add(wait);

            // дані
            dataBox = K.Cols(K.Auto, K.Star).M(0, 14, 0, 0);
            var ringBox = new Grid { Width = 100, Height = 100 };
            ring = K.Ring(100, 9);
            ringBox.Children.Add(ring);
            var center = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            pct = K.T("", 25, "Text", FontWeights.SemiBold);
            pct.SetResourceReference(TextBlock.FontFamilyProperty, "NumFont");
            pct.HorizontalAlignment = HorizontalAlignment.Center;
            center.Children.Add(pct);
            word = K.T("", 11, "Text2");
            word.HorizontalAlignment = HorizontalAlignment.Center;
            center.Children.Add(word);
            ringBox.Children.Add(center);
            dataBox.Children.Add(ringBox);

            var right = new StackPanel { Margin = new Thickness(18, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center }.At(1);
            sLabel = K.T("", 12, "Text2");
            right.Children.Add(sLabel);
            sReset = K.T("", 14.5, "Text", FontWeights.SemiBold).M(0, 2, 0, 0);
            right.Children.Add(sReset);
            sIn = K.T("", 12, "Text2").M(0, 1, 0, 0);
            right.Children.Add(sIn);
            var wHead = K.Cols(K.Star, K.Auto).M(0, 14, 0, 0);
            wLabel = K.T("", 12, "Text2");
            wHead.Children.Add(wLabel);
            wPct = K.T("", 12.5, "Text", FontWeights.SemiBold).At(1);
            wHead.Children.Add(wPct);
            right.Children.Add(wHead);
            wBar = K.Bar(6).M(0, 6, 0, 0);
            right.Children.Add(wBar);
            wReset = K.T("", 11.5, "Text2").M(0, 6, 0, 0);
            right.Children.Add(wReset);
            dataBox.Children.Add(right);
            col.Children.Add(dataBox);

            extra = new StackPanel();
            col.Children.Add(extra);

            Child = col;
        }

        static void Show(UIElement e, bool on) => e.Visibility = on ? Visibility.Visible : Visibility.Collapsed;

        public void Update(bool fresh)
        {
            var S = app.S;
            var now = DateTime.Now;
            bool used = S.ShowUsed;
            var error = app.Error(p);
            err.Text = error ?? "";
            err.ToolTip = error;

            bool logged = S.IsLogged(p.Id);
            var u = app.Get(p);
            Show(outBox, !logged);
            Show(wait, logged && u == null);
            Show(dataBox, logged && u != null);
            Show(extra, logged && u != null);
            if (!logged)
            {
                outText.Text = L.T("not_connected");
                signIn.Content = L.T("s_sign_in");
                return;
            }
            if (u == null)
            {
                wait.Text = app.Busy ? L.T("updating") : L.T("no_data");
                return;
            }

            var sl = u.Session;
            int sp = sl?.Shown(used, now) ?? 0;
            if (fresh) ring.Animate(sp / 100.0); else { ring.BeginAnimation(Ring.ValueProperty, null); ring.Value = sp / 100.0; }
            ring.SetResourceReference(Ring.StrokeProperty, sl != null && sl.Low(now) ? "Danger" : "Accent");
            pct.Text = sl == null ? "—" : sp + "%";
            word.Text = Fmt.Word(used);

            sLabel.Text = p.Id == "codex" ? L.T("h5") : L.T("session");
            if (sl != null && sl.Active(now))
            {
                sReset.Text = string.Format(L.T("reset_in"), Fmt.ResetAt(sl.Reset.Value));
                sIn.Text = string.Format(L.T("in"), Fmt.Until(sl.Reset.Value));
                sIn.Visibility = Visibility.Visible;
            }
            else
            {
                sReset.Text = L.T(sl == null ? "not_started" : "full");
                sIn.Visibility = Visibility.Collapsed;
            }

            var wl = u.Week;
            wLabel.Text = L.T("week");
            wPct.Text = wl == null ? "—" : wl.Shown(used, now) + "%";
            double wv = wl == null ? 0 : wl.Shown(used, now) / 100.0;
            if (fresh) wBar.Animate(wv); else { wBar.BeginAnimation(Bar.ValueProperty, null); wBar.Value = wv; }
            wBar.SetResourceReference(Bar.FillProperty, wl != null && wl.Low(now) ? "Danger" : "Accent");
            wReset.Text = wl?.Reset != null ? string.Format(L.T("reset_short"), Fmt.ResetShort(wl.Reset.Value)) : "";
            wReset.Visibility = wl?.Reset != null ? Visibility.Visible : Visibility.Collapsed;

            // окремі тижневі ліміти моделей (Claude)
            extra.Children.Clear();
            Model(L.T("w_opus"), u.Opus, used, now);
            Model(L.T("w_sonnet"), u.Sonnet, used, now);
        }

        void Model(string name, Limit l, bool used, DateTime now)
        {
            if (l == null) return;
            if (extra.Children.Count == 0)
            {
                var line = new Border { Height = 1, Margin = new Thickness(0, 14, 0, 4) };
                line.SetResourceReference(Border.BackgroundProperty, "Border");
                extra.Children.Add(line);
            }
            int v = l.Shown(used, now);
            var row = K.Cols(new GridLength(110), K.Star, new GridLength(44)).M(0, 8, 0, 0);
            row.Children.Add(K.T(name, 12, "Text2"));
            var bar = K.Bar(5).At(1);
            bar.VerticalAlignment = VerticalAlignment.Center;
            bar.Value = v / 100.0;
            bar.SetResourceReference(Bar.FillProperty, l.Low(now) ? "Danger" : "Accent");
            row.Children.Add(bar);
            var t = K.T(v + "%", 12, "Text", FontWeights.SemiBold).At(2);
            t.HorizontalAlignment = HorizontalAlignment.Right;
            row.Children.Add(t);
            extra.Children.Add(row);
        }
    }
}
