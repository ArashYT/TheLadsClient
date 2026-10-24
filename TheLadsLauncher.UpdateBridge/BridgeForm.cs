using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows.Forms;

namespace TheLadsLauncher.UpdateBridge
{
    // Borderless window showing the launcher's snapshot 1:1 at the launcher's client rect, with the startup bar's shine.
    sealed class BridgeForm : Form
    {
        const double SweepMs = 1600, HoldMs = 120, FadeMs = 150, PollMs = 250, HardCapMs = 10 * 60 * 1000;

        readonly Bitmap _image;
        readonly Rectangle _bar;   // in window pixels
        readonly string _updateExe, _relaunch;
        readonly double _graceMs;
        readonly bool _reducedMotion;
        readonly EventWaitHandle _launcherShown;
        readonly Stopwatch _clock = Stopwatch.StartNew();
        readonly System.Windows.Forms.Timer _timer = new System.Windows.Forms.Timer { Interval = 15 };
        EventWaitHandle _bridgeShown;   // kept open so a late waiter still sees it
        double _lastUpdateSeenMs, _lastPollMs = double.MinValue, _fadeStartMs = -1;
        float _scale = 1;

        public BridgeForm(string image, Rectangle client, Rectangle bar, string updateExe, string relaunch, double graceSeconds, bool reducedMotion, EventWaitHandle launcherShown)
        {
            _image = new Bitmap(new MemoryStream(File.ReadAllBytes(image)));   // no file lock; GDI+ keeps the stream for the bitmap's lifetime
            _bar = new Rectangle(bar.X - client.X, bar.Y - client.Y, bar.Width, bar.Height);
            _updateExe = updateExe;
            _relaunch = relaunch;
            _graceMs = graceSeconds * 1000;
            _reducedMotion = reducedMotion;
            _launcherShown = launcherShown;

            Text = "The Lads Client";
            Icon = new Icon(typeof(BridgeForm).Assembly.GetManifestResourceStream("icon.ico"));
            FormBorderStyle = FormBorderStyle.None;
            ShowInTaskbar = true;
            StartPosition = FormStartPosition.Manual;
            AutoScaleMode = AutoScaleMode.None;
            BackColor = Color.FromArgb(0x17, 0x18, 0x1B);
            Bounds = client;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint, true);

            _timer.Tick += (s, e) => Tick();
            _timer.Start();
        }

        [DllImport("user32.dll")] static extern uint GetDpiForWindow(IntPtr hwnd);

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            _scale = GetDpiForWindow(Handle) / 96f;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Rectangle area = Rectangle.Intersect(e.ClipRectangle, new Rectangle(Point.Empty, _image.Size));
            e.Graphics.InterpolationMode = InterpolationMode.NearestNeighbor;   // 1:1, no resampling blur
            e.Graphics.DrawImage(_image, area, area, GraphicsUnit.Pixel);
            if (!_reducedMotion) DrawShine(e.Graphics);
            if (_bridgeShown == null)
            {
                _bridgeShown = new EventWaitHandle(false, EventResetMode.ManualReset, @"Local\TheLadsClient.UpdateBridgeShown");
                BeginInvoke((Action)(() => { _bridgeShown.Set(); Program.Log("shown"); }));   // after this first frame is on screen
            }
        }

        // Same as the launcher's StartupProgressShine: 110 px (x scale) white #55 centre, sweeping the bar every 1600 ms.
        void DrawShine(Graphics g)
        {
            int border = (int)Math.Round(_scale);
            Rectangle track = Rectangle.Inflate(_bar, -border, -border);
            if (track.Width <= 0 || track.Height <= 0) return;
            float width = 110 * _scale;
            float x = track.X - width + (float)(_clock.Elapsed.TotalMilliseconds % SweepMs / SweepMs) * (_bar.Width + width);
            var shine = new RectangleF(x, track.Y, width, track.Height);
            using (var pill = new GraphicsPath())
            using (var brush = new LinearGradientBrush(shine, Color.Transparent, Color.Transparent, LinearGradientMode.Horizontal))
            {
                float d = track.Height;
                pill.AddArc(track.X, track.Y, d, d, 90, 180);
                pill.AddArc(track.Right - d, track.Y, d, d, 270, 180);
                pill.CloseFigure();
                brush.InterpolationColors = new ColorBlend
                {
                    Colors = new[] { Color.Transparent, Color.FromArgb(0x55, Color.White), Color.Transparent },
                    Positions = new[] { 0f, 0.5f, 1f },
                };
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.SetClip(shine, CombineMode.Intersect);
                g.FillPath(brush, pill);
            }
        }

        void Tick()
        {
            double now = _clock.Elapsed.TotalMilliseconds;
            if (_fadeStartMs >= 0)
            {
                if (now < _fadeStartMs) return;   // still holding: fully opaque
                double opacity = 1 - (now - _fadeStartMs) / FadeMs;
                if (opacity <= 0) Close();
                else Opacity = opacity;
                return;
            }
            if (_launcherShown.WaitOne(0))
            {
                Program.Log("launcher shown: fading out after " + HoldMs.ToString(CultureInfo.InvariantCulture) + " ms");
                _fadeStartMs = now + HoldMs;   // belt and braces: the launcher's first frame may still be on its way to the screen
                return;
            }
            if (now - _lastPollMs >= PollMs)
            {
                _lastPollMs = now;
                if (RaiseUpdateExe()) _lastUpdateSeenMs = now;
                if (now - _lastUpdateSeenMs >= _graceMs) { GiveUp("no Update.exe for " + (_graceMs / 1000).ToString(CultureInfo.InvariantCulture) + " s and no launcher signal"); return; }
                if (now >= HardCapMs) { GiveUp("10 min cap reached"); return; }
            }
            if (!_reducedMotion) Invalidate(_bar);
        }

        // Raises every process running --update-exe to High; true when one is alive.
        bool RaiseUpdateExe()
        {
            bool alive = false;
            foreach (Process p in Process.GetProcessesByName(Path.GetFileNameWithoutExtension(_updateExe)))
            {
                using (p)
                {
                    try
                    {
                        if (!string.Equals(p.MainModule.FileName, _updateExe, StringComparison.OrdinalIgnoreCase)) continue;
                        alive = true;
                        if (p.PriorityClass == ProcessPriorityClass.High) continue;
                        p.PriorityClass = ProcessPriorityClass.High;
                        Program.Log("Update.exe pid " + p.Id + " raised to High");
                    }
                    catch (Exception)
                    {
                        // Exited meanwhile or not ours to read: next poll looks again.
                    }
                }
            }
            return alive;
        }

        void GiveUp(string why)
        {
            _timer.Stop();
            Program.Log(why);
            string root = Path.GetDirectoryName(_relaunch);
            if (LauncherRunningUnder(root)) Program.Log("launcher is running, nothing to start");
            else if (!File.Exists(_relaunch)) Program.Log("skipped, missing: " + _relaunch);
            else
            {
                try
                {
                    Process.Start(new ProcessStartInfo(_relaunch) { UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = root }).Dispose();
                    Program.Log("relaunch " + _relaunch);
                }
                catch (Exception e)
                {
                    Program.Log("could not start " + _relaunch + ": " + e.Message);
                }
            }
            Close();
        }

        static bool LauncherRunningUnder(string root)
        {
            string prefix = root.TrimEnd('\\') + "\\";
            foreach (Process p in Process.GetProcessesByName("TheLadsLauncher"))
            {
                using (p)
                {
                    try
                    {
                        if (p.MainModule.FileName.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return true;
                    }
                    catch (Exception)
                    {
                        // Exited meanwhile.
                    }
                }
            }
            return false;
        }
    }
}
