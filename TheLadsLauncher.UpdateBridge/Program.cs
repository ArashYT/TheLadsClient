using System;
using System.Drawing;
using System.Globalization;
using System.IO;
using System.Threading;
using System.Windows.Forms;

namespace TheLadsLauncher.UpdateBridge
{
    // LadsUpdateBridge.exe --image <png> --client x,y,w,h --bar x,y,w,h --update-exe <path> --relaunch <path> --grace <seconds> [--reduced-motion]
    // Numbers are invariant culture, rects are physical screen pixels.
    static class Program
    {
        static readonly string LogPath = Path.Combine(Path.GetTempPath(), "TheLadsClient-update", "bridge.log");

        [STAThread]
        static int Main(string[] args)
        {
            try
            {
                string image = null, updateExe = null, relaunch = null;
                Rectangle client = Rectangle.Empty, bar = Rectangle.Empty;
                double grace = 15;
                bool reducedMotion = false;
                for (int i = 0; i < args.Length; i++)
                {
                    switch (args[i])
                    {
                        case "--image": image = args[++i]; break;
                        case "--client": client = ParseRect(args[++i]); break;
                        case "--bar": bar = ParseRect(args[++i]); break;
                        case "--update-exe": updateExe = Path.GetFullPath(args[++i]); break;
                        case "--relaunch": relaunch = Path.GetFullPath(args[++i]); break;
                        case "--grace": grace = double.Parse(args[++i], NumberStyles.Float, CultureInfo.InvariantCulture); break;
                        case "--reduced-motion": reducedMotion = true; break;
                        default: throw new ArgumentException("unknown argument " + args[i]);
                    }
                }
                if (image == null || updateExe == null || relaunch == null || client.Width <= 0 || client.Height <= 0)
                    throw new ArgumentException("needs --image, --client, --update-exe and --relaunch");
                Log("start pid " + System.Diagnostics.Process.GetCurrentProcess().Id + " client " + client + " bar " + bar + " grace " + grace + "s" + (reducedMotion ? " reduced-motion" : ""));

                // Created (unsignalled) before the window shows, so the new launcher can always find it.
                var launcherShown = new EventWaitHandle(false, EventResetMode.ManualReset, @"Local\TheLadsClient.LauncherShown");
                launcherShown.Reset();
                Application.Run(new BridgeForm(image, client, bar, updateExe, relaunch, grace, reducedMotion, launcherShown));
                Log("exit");
                return 0;
            }
            catch (Exception e)
            {
                Log("failed: " + e);
                return 1;
            }
        }

        static Rectangle ParseRect(string text)
        {
            string[] parts = text.Split(',');
            if (parts.Length != 4) throw new FormatException("rect needs x,y,w,h: " + text);
            int[] n = Array.ConvertAll(parts, p => (int)Math.Round(double.Parse(p, NumberStyles.Float, CultureInfo.InvariantCulture)));
            return new Rectangle(n[0], n[1], n[2], n[3]);
        }

        internal static void Log(string message)
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(LogPath));
                File.AppendAllText(LogPath, DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.fff", CultureInfo.InvariantCulture) + " " + message + Environment.NewLine);
            }
            catch
            {
                // Logging never stops the bridge.
            }
        }
    }
}
