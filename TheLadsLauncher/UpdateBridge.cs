using System;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Threading;
using Avalonia;
using Avalonia.Controls;
using Velopack.Locators;

namespace TheLadsLauncher;

/// <summary>Hands the "Installing update" screen to LadsUpdateBridge.exe while Update.exe swaps the launcher, and carries the window placement into the new launcher.</summary>
public static class UpdateBridge
{
    /// <summary>Set by the bridge once its first frame is on screen.</summary>
    public const string ShownEvent = @"Local\TheLadsClient.UpdateBridgeShown";
    /// <summary>Created by the bridge; the new launcher sets it once its window is up, and the bridge fades out.</summary>
    public const string LauncherShownEvent = @"Local\TheLadsClient.LauncherShown";
    public const string RestoreWindowArg = "--restore-window";
    public static readonly string Folder = Path.Combine(Path.GetTempPath(), "TheLadsClient-update");

    /// <summary>Copies the bridge out of current\ (which Update.exe replaces), starts it over <paramref name="client"/> and waits up to 2 s for its first frame.</summary>
    /// <returns>Whether the bridge is on screen; never throws.</returns>
    public static bool TryStart(string snapshotPng, PixelRect client, PixelRect bar, bool reducedMotion)
    {
        try
        {
            string updateExe = VelopackLocator.Current.UpdateExePath ?? throw new InvalidOperationException("no Update.exe (not installed)");
            string root = VelopackLocator.Current.RootAppDir ?? throw new InvalidOperationException("no install root");
            Directory.CreateDirectory(Folder);
            string exe = Path.Combine(Folder, "LadsUpdateBridge.exe");
            File.Copy(Path.Combine(AppContext.BaseDirectory, "LadsUpdateBridge.exe"), exe, overwrite: true);

            var start = new ProcessStartInfo(exe) { UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = Folder };
            foreach (string arg in new[] { "--image", snapshotPng, "--client", Rect(client), "--bar", Rect(bar),
                         "--update-exe", updateExe, "--relaunch", Path.Combine(root, "The Lads Client.exe"), "--grace", "15" })
                start.ArgumentList.Add(arg);
            if (reducedMotion) start.ArgumentList.Add("--reduced-motion");

            // Created before the bridge starts, so its Set can never land before this wait begins.
            using var shown = new EventWaitHandle(false, EventResetMode.ManualReset, ShownEvent);
            shown.Reset();
            using (Process.Start(start)) { }
            bool onScreen = shown.WaitOne(2000);
            Log(onScreen ? "bridge shown" : "bridge not shown within 2 s");
            return onScreen;
        }
        catch (Exception e)
        {
            Log("bridge not started: " + e);
            return false;
        }
    }

    /// <summary>Tells a waiting bridge that the new launcher is on screen; does nothing when no bridge runs.</summary>
    public static void SignalLauncherShown()
    {
        try
        {
            if (!EventWaitHandle.TryOpenExisting(LauncherShownEvent, out var handle)) return;
            using (handle) handle.Set();
        }
        catch (Exception e)
        {
            Log("could not signal the bridge: " + e.Message);
        }
    }

    public static string FormatRestoreWindow(PixelPoint position, double width, double height, WindowState state) =>
        string.Format(CultureInfo.InvariantCulture, "{0},{1},{2},{3},{4}", position.X, position.Y, width, height, state);

    public static bool TryParseRestoreWindow(string[] args, out PixelPoint position, out double width, out double height, out WindowState state)
    {
        position = default;
        width = height = 0;
        state = WindowState.Normal;
        int index = Array.IndexOf(args, RestoreWindowArg);
        if (index < 0 || index + 1 >= args.Length) return false;
        string[] parts = args[index + 1].Split(',');
        if (parts.Length != 5
            || !int.TryParse(parts[0], NumberStyles.Integer, CultureInfo.InvariantCulture, out int x)
            || !int.TryParse(parts[1], NumberStyles.Integer, CultureInfo.InvariantCulture, out int y)
            || !double.TryParse(parts[2], NumberStyles.Float, CultureInfo.InvariantCulture, out width) || !(width > 0 && width < 100_000)
            || !double.TryParse(parts[3], NumberStyles.Float, CultureInfo.InvariantCulture, out height) || !(height > 0 && height < 100_000)
            || !Enum.TryParse(parts[4], out state) || !Enum.IsDefined(state))
            return false;
        position = new PixelPoint(x, y);
        return true;
    }

    private static string Rect(PixelRect r) => string.Format(CultureInfo.InvariantCulture, "{0},{1},{2},{3}", r.X, r.Y, r.Width, r.Height);

    // Same file as the bridge's own log, so one file tells the whole handoff.
    private static void Log(string message)
    {
        try { File.AppendAllText(Path.Combine(Folder, "bridge.log"), $"{DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.fff", CultureInfo.InvariantCulture)} launcher pid {Environment.ProcessId}: {message}{Environment.NewLine}"); }
        catch (Exception) { } // logging never stops an update
    }
}
