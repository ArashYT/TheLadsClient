using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>
/// The parts of starting a game every launch path shares (MainWindow, LaunchService, the verification harness),
/// so they cannot drift apart: child environment, running marker and post-exit server-list reconcile.
/// </summary>
public static class GameSession
{
    private static readonly Regex Log4jMarkup = new(@"</?log4j:[^>]*>|<!\[CDATA\[|\]\]>", RegexOptions.Compiled);

    /// <summary>A game output line as the launcher log shows it, or null when nothing is left. Forge 1.8.9 prints log4j XML
    /// events on stdout (client-1.7.xml's console appender): only their text is kept. Its logs\latest.log is plain text, and
    /// that is what crash detection reads.</summary>
    public static string? ReadableOutput(string? line)
    {
        if (string.IsNullOrEmpty(line) || !line.Contains("log4j:", StringComparison.Ordinal)) return string.IsNullOrEmpty(line) ? null : line;
        var text = Log4jMarkup.Replace(line, "").Trim();
        return text.Length == 0 ? null : text;
    }

    /// <summary>The game log line every supported version prints once its startup resources are loaded.</summary>
    public static bool StartupFinished(string? line) => line != null && line.Contains("Sound engine started", StringComparison.Ordinal);

    /// <summary>
    /// Startup runs at Realtime priority (Windows grants High unless the launcher is elevated), back to Normal once
    /// <see cref="StartupFinished"/> appears on stdout or after 3 minutes: Realtime during play could starve input and audio.
    /// </summary>
    private static void BoostStartup(Process process)
    {
        try { process.PriorityClass = ProcessPriorityClass.RealTime; }
        catch (Exception e) when (e is InvalidOperationException or System.ComponentModel.Win32Exception or NotSupportedException) { return; }
        var restored = 0;
        void Restore()
        {
            if (System.Threading.Interlocked.Exchange(ref restored, 1) != 0) return;
            try { if (!process.HasExited) process.PriorityClass = ProcessPriorityClass.Normal; }
            catch (Exception e) when (e is InvalidOperationException or System.ComponentModel.Win32Exception) { }
        }
        DataReceivedEventHandler? watch = null;
        watch = (_, e) => { if (StartupFinished(e.Data)) { process.OutputDataReceived -= watch; Restore(); } };
        process.OutputDataReceived += watch;
        _ = Task.Delay(TimeSpan.FromMinutes(3)).ContinueWith(_ => Restore(), TaskScheduler.Default);
    }

    // Games the user stopped while they were starting. Weak: a finished Process is not kept alive.
    private static readonly System.Runtime.CompilerServices.ConditionalWeakTable<Process, object> Cancelled = new();

    /// <summary>The user cancelled while the game was starting: its whole process tree is killed, and its exit is not a crash
    /// (<see cref="WasCancelled"/>).</summary>
    public static void Cancel(Process process)
    {
        Cancelled.AddOrUpdate(process, true);
        try { process.Kill(entireProcessTree: true); }
        catch (Exception e) when (e is InvalidOperationException or AggregateException or System.ComponentModel.Win32Exception) { }
    }

    public static bool WasCancelled(Process process) => Cancelled.TryGetValue(process, out _);

    /// <summary>Call before Start: Core reads its profile folder from THELADS_DIR, the shared root from LADS_GLOBAL_MINECRAFT_DIR
    /// and (1.8.9) Lunar Client's folder, for its 1.8 resource packs, from LADS_LUNAR_DIR. Also copies in the pack's missing
    /// default files (<see cref="PackDefaults"/>).</summary>
    public static void Configure(ProcessStartInfo startInfo, string gameDirectory, string sharedRoot)
    {
        startInfo.Environment["THELADS_DIR"] = gameDirectory;
        startInfo.Environment[SharedContentService.RootEnvironmentVariable] = sharedRoot;
        startInfo.Environment[GameOptionsService.LunarEnvironmentVariable] = GameOptionsService.LunarRoot();
        PackDefaults.Apply(gameDirectory);
    }

    /// <summary>
    /// Call right after Start: writes the running marker and, when the game exits, deletes it and folds server-list edits
    /// made without LadsCore back into the shared list. Every message and error goes to <paramref name="report"/>.
    /// </summary>
    /// <param name="afterExit">The caller's own post-exit work. It runs once, after the marker is gone and the server list is
    /// reconciled, so a relaunch from it sees a finished session.</param>
    public static void Attach(Process process, string gameDirectory, IReadOnlyList<string> loadedMods, Action<string> report,
        SharedContentService? sharedContent = null, Func<Task>? afterExit = null)
    {
        var shared = sharedContent ?? SharedContentService.Instance;
        var pid = process.Id;
        BoostStartup(process);
        try
        {
            RunningGameMarker.Write(gameDirectory, pid, process.StartTime.ToUniversalTime(), loadedMods);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // The game is already running; failing the launch now would only hide it.
            report($"Could not record that the game is running in '{gameDirectory}' ({ex.Message}). Mod changes and shared-content checks may not see it until it closes.");
        }
        process.EnableRaisingEvents = true;
        process.Exited += async (_, _) =>
        {
            try
            {
                RunningGameMarker.Delete(gameDirectory, pid);
                var result = await shared.ReconcileFallbackServersAsync(gameDirectory);
                foreach (var message in result.Messages.Concat(result.Warnings)) report(message);
            }
            catch (Exception ex)
            {
                report($"After the game closed, the shared server list could not be updated for '{gameDirectory}': {ex.Message}");
            }
            if (afterExit == null) return;
            try
            {
                await afterExit();
            }
            catch (Exception ex)
            {
                report($"After the game closed, the launcher could not finish its checks for '{gameDirectory}': {ex.Message}");
            }
        };
    }
}
