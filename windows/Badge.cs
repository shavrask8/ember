using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;

namespace Ember
{
    /// <summary>Вміст плаваючого значка. Використовується і на робочому столі, і як попередній перегляд у налаштуваннях.</summary>
    public sealed class BadgeView : ContentControl
    {
        public static readonly double[] Scales = { 0.85, 1.0, 1.25 };

        public BadgeView()
        {
            Focusable = false;
            FontFamily = Pal.Sans;
        }

        public void Render(Settings S, Prov p, Usage u)
        {
            var pal = Pal.Get(p.Id, S.BadgeTheme);
            pal.Into(Resources);
            Resources["NumFont"] = Pal.NumFont(p.Id);
            SetResourceReference(ForegroundProperty, "Text");

            var now = DateTime.Now;
            bool used = S.ShowUsed;
            var sl = u?.Session;
            int v = sl?.Shown(used, now) ?? 0;
            string pct = sl == null ? "—" : v + "%";
            bool low = sl != null && sl.Low(now);
            string tiny = sl != null && sl.Active(now) ? Fmt.UntilTiny(sl.Reset.Value) : "";

            FrameworkElement body;
            CornerRadius rad;
            Thickness pad;
            switch (S.BadgeStyle)
            {
                case 0: body = RingStyle(v, pct, tiny, low, sl != null); rad = new CornerRadius(40); pad = new Thickness(6); break;
                case 2: body = Detail(p, u, used, now); rad = new CornerRadius(16); pad = new Thickness(14, 11, 14, 13); break;
                case 3: body = Minimal(pct, low, p); rad = new CornerRadius(15); pad = new Thickness(10, 5, 12, 5); break;
                default: body = Compact(p, v, pct, tiny, low, sl != null); rad = new CornerRadius(22); pad = new Thickness(13, 0, 15, 0); break;
            }

            var shell = new Border
            {
                Child = body,
                CornerRadius = rad,
                Padding = pad,
                BorderThickness = new Thickness(1),
                Margin = new Thickness(12, 8, 12, 16),     // місце для тіні
                Effect = new DropShadowEffect { BlurRadius = 16, ShadowDepth = 3, Direction = 270, Opacity = pal.Dark ? 0.45 : 0.18 },
                LayoutTransform = new ScaleTransform(Scales[Math.Clamp(S.BadgeSize, 0, 2)], Scales[Math.Clamp(S.BadgeSize, 0, 2)]),
            };
            shell.SetResourceReference(Border.BackgroundProperty, "Surface");
            shell.SetResourceReference(Border.BorderBrushProperty, "Border");
            Content = shell;
        }

        static TextBlock Num(string s, double size)
        {
            var t = K.T(s, size, "Text", FontWeights.SemiBold);
            t.SetResourceReference(TextBlock.FontFamilyProperty, "NumFont");
            t.VerticalAlignment = VerticalAlignment.Center;
            return t;
        }

        FrameworkElement RingStyle(int v, string pct, string tiny, bool low, bool has)
        {
            var g = new Grid { Width = 64, Height = 64 };
            var r = K.Ring(64, 5);
            r.Value = has ? v / 100.0 : 0;
            if (low) r.SetResourceReference(Ring.StrokeProperty, "Danger");
            g.Children.Add(r);
            var c = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            var n = Num(pct, 17);
            n.HorizontalAlignment = HorizontalAlignment.Center;
            c.Children.Add(n);
            if (tiny != "")
            {
                var t = K.T(tiny, 10, "Text2");
                t.HorizontalAlignment = HorizontalAlignment.Center;
                t.Margin = new Thickness(0, -1, 0, 0);
                c.Children.Add(t);
            }
            g.Children.Add(c);
            return g;
        }

        FrameworkElement Compact(Prov p, int v, string pct, string tiny, bool low, bool has)
        {
            var sp = new StackPanel { Orientation = Orientation.Horizontal, Height = 42 };
            sp.Children.Add(K.Logo(p.Id, 16).M(0, 0, 9, 0));
            sp.Children.Add(Num(pct, 18).M(0, 0, 10, 0));
            var bar = K.Bar(5);
            bar.Width = 54;
            bar.VerticalAlignment = VerticalAlignment.Center;
            bar.Value = has ? v / 100.0 : 0;
            if (low) bar.SetResourceReference(Bar.FillProperty, "Danger");
            sp.Children.Add(bar);
            if (tiny != "")
            {
                var t = K.T(tiny, 12.5, "Text2", FontWeights.SemiBold).M(10, 0, 0, 0);
                t.VerticalAlignment = VerticalAlignment.Center;
                sp.Children.Add(t);
            }
            return sp;
        }

        FrameworkElement Detail(Prov p, Usage u, bool used, DateTime now)
        {
            var col = new StackPanel { Width = 210 };
            var head = K.Cols(K.Auto, K.Star, K.Auto);
            head.Children.Add(K.Logo(p.Id, 15).M(0, 0, 8, 0));
            var title = Num(p.Title, 14.5).At(1);
            head.Children.Add(title);
            if (u != null)
            {
                var t = K.T(Fmt.Clock(u.Updated), 11, "Text2").At(2);
                t.VerticalAlignment = VerticalAlignment.Center;
                head.Children.Add(t);
            }
            col.Children.Add(head);
            col.Children.Add(Row(p.Id == "codex" ? L.T("h5") : L.T("session"), u?.Session, used, now, true).M(0, 10, 0, 0));
            col.Children.Add(Row(L.T("week"), u?.Week, used, now, false).M(0, 9, 0, 0));
            return col;
        }

        static FrameworkElement Row(string name, Limit l, bool used, DateTime now, bool withTime)
        {
            var col = new StackPanel();
            var g = K.Cols(K.Star, K.Auto);
            g.Children.Add(K.T(name, 11.5, "Text2"));
            string right = l == null ? "—" : l.Shown(used, now) + "%";
            if (withTime && l != null && l.Active(now)) right += "  ·  " + Fmt.UntilTiny(l.Reset.Value);
            g.Children.Add(K.T(right, 12, "Text", FontWeights.SemiBold).At(1));
            col.Children.Add(g);
            var bar = K.Bar(5).M(0, 5, 0, 0);
            bar.Value = l == null ? 0 : l.Shown(used, now) / 100.0;
            if (l != null && l.Low(now)) bar.SetResourceReference(Bar.FillProperty, "Danger");
            col.Children.Add(bar);
            return col;
        }

        FrameworkElement Minimal(string pct, bool low, Prov p)
        {
            var sp = new StackPanel { Orientation = Orientation.Horizontal };
            var dot = new System.Windows.Shapes.Ellipse { Width = 8, Height = 8, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 7, 0) };
            dot.SetResourceReference(System.Windows.Shapes.Shape.FillProperty, low ? "Danger" : "Accent");
            sp.Children.Add(dot);
            var t = K.T(pct, 13, "Text", FontWeights.SemiBold);
            t.VerticalAlignment = VerticalAlignment.Center;
            sp.Children.Add(t);
            return sp;
        }
    }

    /// <summary>Плаваюче вікно значка: прозоре, поверх усіх, не забирає фокус.</summary>
    public sealed class BadgeWindow : Window
    {
        readonly App app;
        readonly BadgeView view = new BadgeView();
        bool placed;

        public BadgeWindow(App app)
        {
            this.app = app;
            WindowStyle = WindowStyle.None;
            AllowsTransparency = true;
            Background = Brushes.Transparent;
            Topmost = true;
            ShowInTaskbar = false;
            ShowActivated = false;
            ResizeMode = ResizeMode.NoResize;
            SizeToContent = SizeToContent.WidthAndHeight;
            UseLayoutRounding = true;
            Title = "Ember";
            Content = view;
            ToolTip = new ToolTip();
            ToolTipService.SetInitialShowDelay(this, 700);

            SourceInitialized += (_, _) => Dwm.ToolWindow(new WindowInteropHelper(this).Handle, true);
            MouseLeftButtonDown += OnDown;
            MouseRightButtonUp += (_, e) => { e.Handled = true; app.ShowMenu(); };
            MouseEnter += (_, _) => Fade(1);
            MouseLeave += (_, _) => Fade(BaseOpacity);
            ToolTipOpening += (_, _) => ((ToolTip)ToolTip).Content = app.Tip(500);
        }

        double BaseOpacity => Math.Clamp(app.S.BadgeOpacity, 30, 100) / 100.0;

        void Fade(double to)
        {
            BeginAnimation(OpacityProperty, new DoubleAnimation(to, TimeSpan.FromMilliseconds(150)));
        }

        public void Render()
        {
            var S = app.S;
            if (!S.BadgeVisible) { if (IsVisible) Hide(); return; }
            var p = app.Pick(S.BadgeProvider);
            view.Render(S, p, app.Get(p));
            if (!IsMouseOver) { BeginAnimation(OpacityProperty, null); Opacity = BaseOpacity; }
            if (!placed) Place();
            if (!IsVisible) Show();
        }

        void Place()
        {
            placed = true;
            var S = app.S;
            view.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
            var sz = view.DesiredSize;
            double vl = SystemParameters.VirtualScreenLeft, vt = SystemParameters.VirtualScreenTop;
            double vw = SystemParameters.VirtualScreenWidth, vh = SystemParameters.VirtualScreenHeight;
            bool ok = S.BadgeX >= vl - 20 && S.BadgeY >= vt - 20 && S.BadgeX + 40 < vl + vw && S.BadgeY + 30 < vt + vh && S.BadgeX != -1;
            if (ok) { Left = S.BadgeX; Top = S.BadgeY; }
            else
            {
                var wa = SystemParameters.WorkArea;
                Left = wa.Right - sz.Width - 8;
                Top = wa.Top + 8;
            }
        }

        public void ResetPosition()
        {
            app.S.BadgeX = -1;
            app.S.BadgeY = -1;
            app.S.Save();
            placed = false;
            Render();
        }

        void OnDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ClickCount == 2) { app.TogglePanel(); return; }
            if (app.S.BadgeLocked) { app.TogglePanel(); return; }
            double x = Left, y = Top;
            try { DragMove(); } catch { }
            if (Math.Abs(Left - x) > 1 || Math.Abs(Top - y) > 1)
            {
                app.S.BadgeX = (int)Math.Round(Left);
                app.S.BadgeY = (int)Math.Round(Top);
                app.S.Save();
            }
        }
    }
}
