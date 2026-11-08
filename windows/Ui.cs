using System;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using Microsoft.Win32;

namespace Ember
{
    // ===================== кольори =====================

    /// <summary>Палітри в стилі сервісу: Claude — кремовий і теракота, Codex — чорно-білий.</summary>
    public sealed class Pal
    {
        public Color Bg, Surface, Border, Text, Text2, Accent, OnAccent, Track, SegBg, SegOn, Hover, Scroll, Danger, Good;
        public bool Dark;

        public static readonly FontFamily Sans = new FontFamily("Segoe UI");
        public static readonly FontFamily Serif = new FontFamily("Georgia");
        public static readonly FontFamily Icons = new FontFamily("Segoe Fluent Icons, Segoe MDL2 Assets");

        static Color H(uint rgb, byte a = 255) => Color.FromArgb(a, (byte)(rgb >> 16), (byte)(rgb >> 8), (byte)rgb);

        static bool Reg(string name)
        {
            try
            {
                using var k = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
                return k?.GetValue(name) is int v && v == 0;
            }
            catch { return false; }
        }

        public static bool AppsDark() => Reg("AppsUseLightTheme");
        public static bool TaskbarDark() => Reg("SystemUsesLightTheme");

        public static FontFamily NumFont(string prov) => prov == "codex" ? Sans : Serif;

        /// <param name="mode">0 авто, 1 світла, 2 темна, 3 акцент</param>
        public static Pal Get(string prov, int mode)
        {
            bool dark = mode == 2 || (mode == 0 && AppsDark());
            var t = new Pal { Dark = dark };
            if (prov == "codex")
            {
                if (dark)
                {
                    t.Bg = H(0x212121); t.Surface = H(0x2A2A2A); t.Border = H(0x3A3A3A); t.Text = H(0xECECEC); t.Text2 = H(0xA8A8A8);
                    t.Accent = H(0xECECEC); t.OnAccent = H(0x0D0D0D); t.Track = H(0x424242); t.SegBg = H(0x171717); t.SegOn = H(0x3A3A3A);
                }
                else
                {
                    t.Bg = H(0xF9F9F9); t.Surface = H(0xFFFFFF); t.Border = H(0xE5E5E5); t.Text = H(0x0D0D0D); t.Text2 = H(0x5D5D5D);
                    t.Accent = H(0x0D0D0D); t.OnAccent = H(0xFFFFFF); t.Track = H(0xECECEC); t.SegBg = H(0xEFEFEF); t.SegOn = H(0xFFFFFF);
                }
            }
            else
            {
                if (dark)
                {
                    t.Bg = H(0x1F1E1D); t.Surface = H(0x2A2927); t.Border = H(0x3A3936); t.Text = H(0xF5F4EE); t.Text2 = H(0xA39F96);
                    t.Accent = H(0xD97757); t.OnAccent = H(0xFFFFFF); t.Track = H(0x3F3D39); t.SegBg = H(0x191817); t.SegOn = H(0x3A3936);
                }
                else
                {
                    t.Bg = H(0xF4F3EE); t.Surface = H(0xFFFFFF); t.Border = H(0xE6E3DA); t.Text = H(0x1F1E1D); t.Text2 = H(0x6F6C64);
                    t.Accent = H(0xD97757); t.OnAccent = H(0xFFFFFF); t.Track = H(0xEAE6DC); t.SegBg = H(0xEAE7DF); t.SegOn = H(0xFFFFFF);
                }
            }
            t.Hover = dark ? H(0xFFFFFF, 0x12) : H(0x000000, 0x0A);
            t.Scroll = dark ? H(0xFFFFFF, 0x40) : H(0x000000, 0x33);
            t.Danger = dark ? H(0xE8806B) : H(0xB5463C);
            t.Good = dark ? H(0x8DBF8F) : H(0x4F7D53);
            if (mode == 3)
            {
                // акцентна: фон кольору сервісу, білий текст
                t.Surface = t.Bg = prov == "codex" ? H(0x0D0D0D) : H(0xD97757);
                t.Border = H(0xFFFFFF, 0x30);
                t.Text = Colors.White; t.Text2 = H(0xFFFFFF, 0xD0);
                t.Accent = Colors.White; t.Track = H(0xFFFFFF, 0x50); t.Danger = Colors.White;
                t.Dark = true;
            }
            return t;
        }

        /// <summary>Акцент сервісу на поточному фоні (Codex — колір тексту).</summary>
        public static Color AccentFor(string prov, bool dark) => prov == "codex" ? (dark ? H(0xECECEC) : H(0x0D0D0D)) : H(0xD97757);

        public void Into(ResourceDictionary d)
        {
            Set(d, "Bg", Bg); Set(d, "Surface", Surface); Set(d, "Border", Border); Set(d, "Text", Text); Set(d, "Text2", Text2);
            Set(d, "Accent", Accent); Set(d, "OnAccent", OnAccent); Set(d, "Track", Track); Set(d, "SegBg", SegBg); Set(d, "SegOn", SegOn);
            Set(d, "Hover", Hover); Set(d, "Scroll", Scroll); Set(d, "Danger", Danger); Set(d, "Good", Good);
        }

        public static void Set(ResourceDictionary d, string key, Color c)
        {
            var b = new SolidColorBrush(c);
            b.Freeze();
            d[key] = b;
        }
    }

    // ===================== Windows 11: кути, тінь, заголовок =====================

    public static class Dwm
    {
        [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr h, int attr, ref int v, int size);
        [DllImport("user32.dll", EntryPoint = "GetWindowLongPtrW")] static extern IntPtr GetLong(IntPtr h, int i);
        [DllImport("user32.dll", EntryPoint = "SetWindowLongPtrW")] static extern IntPtr SetLong(IntPtr h, int i, IntPtr v);
        [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);

        static void Attr(IntPtr h, int a, int v) { try { DwmSetWindowAttribute(h, a, ref v, 4); } catch { } }

        public static void Round(IntPtr h, bool small = false) => Attr(h, 33, small ? 3 : 2);
        public static void Dark(IntPtr h, bool dark) => Attr(h, 20, dark ? 1 : 0);
        static int Ref(Color c) => c.R | (c.G << 8) | (c.B << 16);
        public static void Caption(IntPtr h, Color bg, Color text) { Attr(h, 35, Ref(bg)); Attr(h, 36, Ref(text)); }
        public static void BorderColor(IntPtr h, Color c) => Attr(h, 34, Ref(c));

        /// <summary>Без Alt+Tab і (за потреби) без перехоплення фокусу.</summary>
        public static void ToolWindow(IntPtr h, bool noActivate)
        {
            long ex = GetLong(h, -20).ToInt64();
            ex |= 0x80;                                 // WS_EX_TOOLWINDOW
            if (noActivate) ex |= 0x08000000;           // WS_EX_NOACTIVATE
            SetLong(h, -20, new IntPtr(ex));
        }
    }

    // ===================== кільце, смужка, логотип =====================

    /// <summary>Кільце прогресу. Малюється напряму, без зайвих елементів.</summary>
    public sealed class Ring : FrameworkElement
    {
        public static readonly DependencyProperty ValueProperty = DependencyProperty.Register(nameof(Value), typeof(double), typeof(Ring),
            new FrameworkPropertyMetadata(0.0, FrameworkPropertyMetadataOptions.AffectsRender));
        public static readonly DependencyProperty ThicknessProperty = DependencyProperty.Register(nameof(Thickness), typeof(double), typeof(Ring),
            new FrameworkPropertyMetadata(6.0, FrameworkPropertyMetadataOptions.AffectsRender));
        public static readonly DependencyProperty StrokeProperty = DependencyProperty.Register(nameof(Stroke), typeof(Brush), typeof(Ring),
            new FrameworkPropertyMetadata(Brushes.Orange, FrameworkPropertyMetadataOptions.AffectsRender));
        public static readonly DependencyProperty TrackProperty = DependencyProperty.Register(nameof(Track), typeof(Brush), typeof(Ring),
            new FrameworkPropertyMetadata(Brushes.LightGray, FrameworkPropertyMetadataOptions.AffectsRender));

        public double Value { get => (double)GetValue(ValueProperty); set => SetValue(ValueProperty, value); }
        public double Thickness { get => (double)GetValue(ThicknessProperty); set => SetValue(ThicknessProperty, value); }
        public Brush Stroke { get => (Brush)GetValue(StrokeProperty); set => SetValue(StrokeProperty, value); }
        public Brush Track { get => (Brush)GetValue(TrackProperty); set => SetValue(TrackProperty, value); }

        /// <summary>Плавна зміна значення (лише коли прийшли нові дані).</summary>
        public void Animate(double to)
        {
            if (Math.Abs(to - Value) < 0.001) return;
            var a = new DoubleAnimation(to, TimeSpan.FromMilliseconds(550)) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } };
            a.Freeze();
            BeginAnimation(ValueProperty, a);
        }

        protected override void OnRender(DrawingContext dc)
        {
            double t = Thickness, w = ActualWidth, h = ActualHeight;
            double r = Math.Min(w, h) / 2 - t / 2;
            if (r <= 0) return;
            var c = new Point(w / 2, h / 2);
            dc.DrawEllipse(null, new Pen(Track, t), c, r, r);
            double v = Math.Max(0, Math.Min(1, Value));
            if (v <= 0.002) return;
            var pen = new Pen(Stroke, t) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round };
            if (v >= 0.999) { dc.DrawEllipse(null, pen, c, r, r); return; }
            double a = v * Math.PI * 2;
            var g = new StreamGeometry();
            using (var ctx = g.Open())
            {
                ctx.BeginFigure(new Point(c.X, c.Y - r), false, false);
                ctx.ArcTo(new Point(c.X + r * Math.Sin(a), c.Y - r * Math.Cos(a)), new Size(r, r), 0, v > 0.5, SweepDirection.Clockwise, true, false);
            }
            g.Freeze();
            dc.DrawGeometry(null, pen, g);
        }
    }

    /// <summary>Заокруглена смужка прогресу.</summary>
    public sealed class Bar : FrameworkElement
    {
        public static readonly DependencyProperty ValueProperty = DependencyProperty.Register(nameof(Value), typeof(double), typeof(Bar),
            new FrameworkPropertyMetadata(0.0, FrameworkPropertyMetadataOptions.AffectsRender));
        public static readonly DependencyProperty FillProperty = DependencyProperty.Register(nameof(Fill), typeof(Brush), typeof(Bar),
            new FrameworkPropertyMetadata(Brushes.Orange, FrameworkPropertyMetadataOptions.AffectsRender));
        public static readonly DependencyProperty TrackProperty = DependencyProperty.Register(nameof(Track), typeof(Brush), typeof(Bar),
            new FrameworkPropertyMetadata(Brushes.LightGray, FrameworkPropertyMetadataOptions.AffectsRender));

        public double Value { get => (double)GetValue(ValueProperty); set => SetValue(ValueProperty, value); }
        public Brush Fill { get => (Brush)GetValue(FillProperty); set => SetValue(FillProperty, value); }
        public Brush Track { get => (Brush)GetValue(TrackProperty); set => SetValue(TrackProperty, value); }

        public void Animate(double to)
        {
            if (Math.Abs(to - Value) < 0.001) return;
            var a = new DoubleAnimation(to, TimeSpan.FromMilliseconds(550)) { EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } };
            a.Freeze();
            BeginAnimation(ValueProperty, a);
        }

        protected override void OnRender(DrawingContext dc)
        {
            double w = ActualWidth, h = ActualHeight, r = h / 2;
            dc.DrawRoundedRectangle(Track, null, new Rect(0, 0, w, h), r, r);
            double v = Math.Max(0, Math.Min(1, Value));
            if (v > 0.003) dc.DrawRoundedRectangle(Fill, null, new Rect(0, 0, Math.Max(h, w * v), h), r, r);
        }
    }

    /// <summary>Знак сервісу: іскра для Claude, «>_» у рамці для Codex.</summary>
    public sealed class Logo : FrameworkElement
    {
        public static readonly DependencyProperty BrushProperty = DependencyProperty.Register(nameof(Brush), typeof(Brush), typeof(Logo),
            new FrameworkPropertyMetadata(Brushes.Orange, FrameworkPropertyMetadataOptions.AffectsRender));
        public static readonly DependencyProperty KindProperty = DependencyProperty.Register(nameof(Kind), typeof(string), typeof(Logo),
            new FrameworkPropertyMetadata("claude", FrameworkPropertyMetadataOptions.AffectsRender));

        public Brush Brush { get => (Brush)GetValue(BrushProperty); set => SetValue(BrushProperty, value); }
        public string Kind { get => (string)GetValue(KindProperty); set => SetValue(KindProperty, value); }

        protected override void OnRender(DrawingContext dc)
        {
            double s = Math.Min(ActualWidth, ActualHeight), cx = ActualWidth / 2, cy = ActualHeight / 2, r = s / 2;
            if (r <= 0) return;
            if (Kind == "codex")
            {
                var pen = new Pen(Brush, Math.Max(1.2, r * 0.17)) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round, LineJoin = PenLineJoin.Round };
                dc.DrawRoundedRectangle(null, pen, new Rect(cx - r * 0.92, cy - r * 0.74, r * 1.84, r * 1.48), r * 0.3, r * 0.3);
                var g = new StreamGeometry();
                using (var c = g.Open())
                {
                    c.BeginFigure(new Point(cx - r * 0.48, cy - r * 0.28), false, false);
                    c.LineTo(new Point(cx - r * 0.12, cy), true, true);
                    c.LineTo(new Point(cx - r * 0.48, cy + r * 0.28), true, true);
                }
                g.Freeze();
                dc.DrawGeometry(null, pen, g);
                dc.DrawLine(pen, new Point(cx + r * 0.08, cy + r * 0.3), new Point(cx + r * 0.5, cy + r * 0.3));
                return;
            }
            var p = new Pen(Brush, Math.Max(1.1, r * 0.19)) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round };
            for (int i = 0; i < 12; i++)
            {
                double a = Math.PI * 2 * i / 12 - Math.PI / 2;
                double len = (i % 2 == 0) ? r * 0.92 : r * 0.64;
                dc.DrawLine(p, new Point(cx, cy), new Point(cx + Math.Cos(a) * len, cy + Math.Sin(a) * len));
            }
        }
    }

    // ===================== фабрика елементів =====================

    public static class K
    {
        public static TextBlock T(string text, double size = 13, string fg = "Text", FontWeight? w = null)
        {
            var t = new TextBlock { Text = text, FontSize = size, FontWeight = w ?? FontWeights.Normal, TextTrimming = TextTrimming.CharacterEllipsis };
            t.SetResourceReference(TextBlock.ForegroundProperty, fg);
            return t;
        }

        public static TextBlock Wrap(string text, double size = 12.5, string fg = "Text2")
        {
            var t = T(text, size, fg);
            t.TextWrapping = TextWrapping.Wrap;
            t.TextTrimming = TextTrimming.None;
            return t;
        }

        public static TextBlock Icon(string glyph, double size = 16, string fg = "Text2")
        {
            var t = new TextBlock { Text = glyph, FontFamily = Pal.Icons, FontSize = size, VerticalAlignment = VerticalAlignment.Center };
            t.SetResourceReference(TextBlock.ForegroundProperty, fg);
            return t;
        }

        public static Style S(string key) => (Style)Application.Current.FindResource(key);

        public static Button Btn(string text, Action click, bool accent = false)
        {
            var b = new Button { Content = text, Style = S(accent ? "AccentBtn" : "Btn") };
            b.Click += (_, _) => click();
            return b;
        }

        public static Button IconBtn(string glyph, string tip, Action click)
        {
            var b = new Button { Content = glyph, Style = S("IconBtn"), ToolTip = tip };
            b.Click += (_, _) => click();
            return b;
        }

        public static Border Card(UIElement child, Thickness? pad = null)
        {
            var b = new Border { Child = child, CornerRadius = new CornerRadius(12), BorderThickness = new Thickness(1), Padding = pad ?? new Thickness(16) };
            b.SetResourceReference(Border.BackgroundProperty, "Surface");
            b.SetResourceReference(Border.BorderBrushProperty, "Border");
            return b;
        }

        public static Ring Ring(double size, double thick)
        {
            var r = new Ring { Width = size, Height = size, Thickness = thick };
            r.SetResourceReference(Ember.Ring.StrokeProperty, "Accent");
            r.SetResourceReference(Ember.Ring.TrackProperty, "Track");
            return r;
        }

        public static Bar Bar(double height)
        {
            var b = new Bar { Height = height };
            b.SetResourceReference(Ember.Bar.FillProperty, "Accent");
            b.SetResourceReference(Ember.Bar.TrackProperty, "Track");
            return b;
        }

        public static Logo Logo(string kind, double size, string brushKey = "Accent")
        {
            var l = new Logo { Kind = kind, Width = size, Height = size, VerticalAlignment = VerticalAlignment.Center };
            l.SetResourceReference(Ember.Logo.BrushProperty, brushKey);
            return l;
        }

        public static Grid Cols(params GridLength[] cols)
        {
            var g = new Grid();
            foreach (var c in cols) g.ColumnDefinitions.Add(new ColumnDefinition { Width = c });
            return g;
        }

        public static T At<T>(this T e, int col) where T : UIElement { Grid.SetColumn(e, col); return e; }
        public static T M<T>(this T e, double l, double t, double r, double b) where T : FrameworkElement { e.Margin = new Thickness(l, t, r, b); return e; }

        public static readonly GridLength Auto = GridLength.Auto;
        public static readonly GridLength Star = new GridLength(1, GridUnitType.Star);
    }
}
