using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Runtime.InteropServices;
using WF = System.Windows.Forms;

namespace Ember
{
    /// <summary>Значок у треї: кільце (або смужка на малих розмірах) і число відсотків.</summary>
    public sealed class Tray : IDisposable
    {
        readonly App app;
        readonly WF.NotifyIcon ni;
        IntPtr lastHandle = IntPtr.Zero;
        string lastKey;

        [DllImport("user32.dll")] static extern bool DestroyIcon(IntPtr h);
        [DllImport("user32.dll")] static extern int GetSystemMetrics(int i);

        public Tray(App app)
        {
            this.app = app;
            ni = new WF.NotifyIcon { Text = "Ember", Visible = true };
            ni.MouseUp += (_, e) =>
            {
                if (e.Button == WF.MouseButtons.Left) app.TogglePanel();
                else if (e.Button == WF.MouseButtons.Right) app.ShowMenu();
            };
            ni.BalloonTipClicked += (_, _) => app.TogglePanel();
            Update();
        }

        public void Balloon(string title, string text) => ni.ShowBalloonTip(6000, title, text, WF.ToolTipIcon.None);

        public void Update()
        {
            var S = app.S;
            var p = app.Pick(S.TrayProvider);
            var u = app.Get(p);
            var now = DateTime.Now;
            var l = u == null ? null : (S.TrayShows == 1 ? u.Week : u.Session);
            int? pct = l?.Shown(S.ShowUsed, now);
            bool low = l != null && l.Low(now);
            bool dark = Pal.TaskbarDark();
            int size = Math.Max(16, GetSystemMetrics(49)); // SM_CXSMICON з урахуванням масштабу
            string key = pct + "|" + low + "|" + dark + "|" + size + "|" + p.Id;
            if (key != lastKey)
            {
                lastKey = key;
                var h = Render(pct, low, p, dark, size);
                var old = ni.Icon;
                ni.Icon = Icon.FromHandle(h);
                old?.Dispose();
                if (lastHandle != IntPtr.Zero) DestroyIcon(lastHandle);
                lastHandle = h;
            }
            var tip = app.Tip(127);
            if (ni.Text != tip) ni.Text = tip;
        }

        static Color C(uint rgb) => Color.FromArgb(255, (int)(rgb >> 16) & 255, (int)(rgb >> 8) & 255, (int)rgb & 255);

        static IntPtr Render(int? pct, bool low, Prov p, bool dark, int s)
        {
            using var bmp = new Bitmap(s, s);
            using (var g = Graphics.FromImage(bmp))
            {
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.PixelOffsetMode = PixelOffsetMode.HighQuality;
                g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
                Color fg = dark ? Color.White : C(0x1F1E1D);
                Color accent = p.Id == "codex" ? fg : C(0xD97757);
                if (low) accent = C(0xE5533D);
                Color track = Color.FromArgb(dark ? 75 : 55, fg);
                float f = s / 32f;

                if (pct == null)
                {
                    // ще немає даних — просто знак сервісу
                    using var pen = new Pen(accent, Math.Max(1.4f, 2.6f * f)) { StartCap = LineCap.Round, EndCap = LineCap.Round };
                    float c = s / 2f, r = s * 0.42f;
                    for (int i = 0; i < 12; i++)
                    {
                        double a = Math.PI * 2 * i / 12 - Math.PI / 2;
                        float len = (i % 2 == 0) ? r : r * 0.68f;
                        g.DrawLine(pen, c, c, c + (float)Math.Cos(a) * len, c + (float)Math.Sin(a) * len);
                    }
                }
                else
                {
                    int v = pct.Value;
                    string txt = v.ToString();
                    if (s >= 24)
                    {
                        // кільце + число
                        float t = 3.2f * f;
                        var rr = new RectangleF(t / 2 + 0.5f, t / 2 + 0.5f, s - t - 1, s - t - 1);
                        using (var pt = new Pen(track, t)) g.DrawEllipse(pt, rr);
                        if (v > 0)
                            using (var pa = new Pen(accent, t) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                                g.DrawArc(pa, rr, -90, 360f * Math.Min(100, v) / 100f);
                        float fs = (txt.Length >= 3 ? 9.5f : txt.Length == 2 ? 12.5f : 14f) * f;
                        using var font = new Font("Segoe UI", fs, FontStyle.Bold, GraphicsUnit.Pixel);
                        Center(g, txt, font, fg, s / 2f, s / 2f);
                    }
                    else
                    {
                        // маленький значок: велике число і смужка знизу
                        float bh = Math.Max(2f, s / 8f);
                        float fs = (txt.Length >= 3 ? 0.5f : 0.72f) * s;
                        using (var font = new Font("Segoe UI", fs, FontStyle.Bold, GraphicsUnit.Pixel))
                            Center(g, txt, font, fg, s / 2f, (s - bh) / 2f);
                        using (var b = new SolidBrush(track)) g.FillRectangle(b, 1, s - bh, s - 2, bh);
                        using (var b = new SolidBrush(accent)) g.FillRectangle(b, 1, s - bh, (s - 2) * Math.Min(100, v) / 100f, bh);
                    }
                }
            }
            return bmp.GetHicon();
        }

        static void Center(Graphics g, string s, Font f, Color c, float cx, float cy)
        {
            var sf = StringFormat.GenericTypographic;
            var sz = g.MeasureString(s, f, PointF.Empty, sf);
            using var b = new SolidBrush(c);
            g.DrawString(s, f, b, cx - sz.Width / 2, cy - sz.Height / 2, sf);
        }

        public void Dispose()
        {
            ni.Visible = false;
            ni.Dispose();
            if (lastHandle != IntPtr.Zero) DestroyIcon(lastHandle);
        }
    }
}
