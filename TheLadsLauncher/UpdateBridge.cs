using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
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
    /// <summary>Which block of the mining scene the bridge loops; the relaunched launcher pins its startup screen to the same one.</summary>
    public const string MiningCycleArg = "--mining-cycle";
    public const int MiningFps = 30;
    public static readonly string Folder = Path.Combine(Path.GetTempPath(), "TheLadsClient-update");

    /// <summary>Copies the bridge out of current\ (which Update.exe replaces), starts it over <paramref name="client"/> and waits up to 2 s for its first frame.</summary>
    /// <returns>Whether the bridge is on screen; never throws.</returns>
    public static bool TryStart(string snapshotPng, PixelRect client, PixelRect bar, bool reducedMotion, (string Frames, PixelRect Rect)? mining = null, string? qaUpdateExe = null)
    {
        try
        {
            string updateExe = qaUpdateExe ?? VelopackLocator.Current.UpdateExePath ?? throw new InvalidOperationException("no Update.exe (not installed)");   // qaUpdateExe: the QA handoff preview has no install
            string root = qaUpdateExe != null ? Path.GetDirectoryName(qaUpdateExe)! : VelopackLocator.Current.RootAppDir ?? throw new InvalidOperationException("no install root");
            Directory.CreateDirectory(Folder);
            string exe = Path.Combine(Folder, "LadsUpdateBridge.exe");
            File.Copy(Path.Combine(AppContext.BaseDirectory, "LadsUpdateBridge.exe"), exe, overwrite: true);

            var start = new ProcessStartInfo(exe) { UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = Folder };
            foreach (string arg in new[] { "--image", snapshotPng, "--client", Rect(client), "--bar", Rect(bar),
                         "--update-exe", updateExe, "--relaunch", Path.Combine(root, "The Lads Client.exe"), "--grace", "15" })
                start.ArgumentList.Add(arg);
            if (reducedMotion) start.ArgumentList.Add("--reduced-motion");
            if (mining is { } m) foreach (string arg in new[] { "--mining", Rect(m.Rect), "--frames", m.Frames }) start.ArgumentList.Add(arg);
            // The bridge's taskbar button groups with the launcher's (Velopack runs it as velopack.<packId>) instead of adding a second one.
            if (GetCurrentProcessExplicitAppUserModelID(out string? appId) == 0 && !string.IsNullOrEmpty(appId))
            {
                start.ArgumentList.Add("--app-id");
                start.ArgumentList.Add(appId);
            }

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

    /// <summary>One loop of the pickaxe scene for the bridge, which has no Avalonia: int count, double cycle seconds, long epoch ticks (UTC),
    /// then count x (int length, PNG bytes). The bridge plays frame floor(phase * count) where phase comes from the same wall clock.</summary>
    public static void WriteMiningFrames(string path, IReadOnlyList<byte[]> pngs)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        using var writer = new BinaryWriter(File.Create(path));
        writer.Write(pngs.Count);
        writer.Write(Controls.MiningAnimation.CycleSeconds);
        writer.Write(Controls.MiningAnimation.Epoch.Ticks);
        foreach (byte[] png in pngs)
        {
            writer.Write(png.Length);
            writer.Write(png);
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

    /// <summary>A rect in window DIPs as screen pixels, rounded to nearest. Avalonia rounds sizes up (PixelSize.FromSize) and points
    /// down (PointToScreen), so float noise (900 px / 1.75 x 1.75 = 900.0000000001) made the snapshot and bridge 1 px too big.</summary>
    public static PixelRect ToScreenPixels(PixelPoint clientOrigin, Rect rect, double scale) =>
        new(new PixelPoint(clientOrigin.X + (int)Math.Round(rect.X * scale), clientOrigin.Y + (int)Math.Round(rect.Y * scale)),
            new PixelPoint(clientOrigin.X + (int)Math.Round(rect.Right * scale), clientOrigin.Y + (int)Math.Round(rect.Bottom * scale)));

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

    [DllImport("shell32.dll")]
    private static extern int GetCurrentProcessExplicitAppUserModelID([MarshalAs(UnmanagedType.LPWStr)] out string? appId);

    private static string Rect(PixelRect r) => string.Format(CultureInfo.InvariantCulture, "{0},{1},{2},{3}", r.X, r.Y, r.Width, r.Height);

    // Same file as the bridge's own log, so one file tells the whole handoff.
    private static void Log(string message)
    {
        try { File.AppendAllText(Path.Combine(Folder, "bridge.log"), $"{DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.fff", CultureInfo.InvariantCulture)} launcher pid {Environment.ProcessId}: {message}{Environment.NewLine}"); }
        catch (Exception) { } // logging never stops an update
    }
}

/// <summary>When the new launcher tells the bridge it is on screen: never before its first frame is out, which the bridge's fade relies on.</summary>
public sealed class LauncherShownSignal(Action signal)
{
    private bool _framePresented;
    private readonly TaskCompletionSource _shown = new(TaskCreationOptions.RunContinuationsAsynchronously);

    /// <summary>Completes once the bridge has been signalled (a first frame, or the fallback), so a window that must not outshine the bridge can wait for it.</summary>
    public Task Shown => _shown.Task;

    /// <summary>The first frame is on screen (the second animation frame after Opened).</summary>
    public void FramePresented()
    {
        _framePresented = true;
        signal();
        _shown.TrySetResult();
    }

    /// <summary>Some time after Opened: signals anyway when no frame came (a stalled renderer), so the bridge doesn't sit out its grace.</summary>
    /// <returns>Whether it had to.</returns>
    public bool Fallback()
    {
        if (_framePresented) return false;
        FramePresented();
        return true;
    }

    /// <summary>The startup screen closed. Still running after a startup install means the install failed, so this dismisses
    /// the bridge that install started; before the first frame it waits for <see cref="FramePresented"/>, which signals anyway.</summary>
    public void StartupFinished()
    {
        if (_framePresented) signal();
    }
}
