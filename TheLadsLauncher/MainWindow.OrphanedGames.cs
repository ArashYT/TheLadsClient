using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using CmlLib.Core.ProcessBuilder;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;

namespace TheLadsLauncher;

/// <summary>Games that were started by an earlier launcher (closed, killed or crashed since): a running one is watched again, a crashed one is
/// reported and relaunched like any crash. See <see cref="OrphanedGame"/>, <see cref="CrashHook"/>.</summary>
public partial class MainWindow
{
    private List<(LauncherProfile Profile, string Dir, RunningGameInfo Game)> _orphanedGames = new();

    /// <summary>The games the profiles' running markers name, read without deleting anything. Nothing in the QA and preview modes.</summary>
    private List<(LauncherProfile Profile, string Dir, RunningGameInfo Game)> SnapshotRunningMarkers()
    {
        var found = new List<(LauncherProfile, string, RunningGameInfo)>();
        if (Environment.GetCommandLineArgs().Any(a => a.StartsWith("--preview-", StringComparison.Ordinal) || a == "--test-services")) return found;
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        try
        {
            foreach (var profile in _profileService.GetProfiles())
            {
                string dir = _pathService.GetProfileDirectory(profile);
                if (!seen.Add(dir)) continue;
                try { foreach (var game in RunningGameMarker.ReadAll(dir)) found.Add((profile, dir, game)); }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
            }
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or InvalidOperationException) { }
        return found;
    }

    /// <summary>The JVM's crash hook for the game being launched, so a native crash is handled even when this launcher is gone by then.</summary>
    private static void AddCrashHookArgument(MLaunchOption options)
    {
        var existing = options.ExtraJvmArguments ?? Array.Empty<MArgument>();
        if (existing.Any(a => a.Values.Any(v => v.StartsWith("-XX:OnError", StringComparison.OrdinalIgnoreCase)))) return;
        if (CrashHook.Argument(Environment.ProcessPath) is { } hook) options.ExtraJvmArguments = existing.Append(new MArgument(hook)).ToArray();
    }

    private void HandleOrphanedGames()
    {
        var games = _orphanedGames;
        _orphanedGames = new();
        foreach (var (profile, dir, game) in games)
        {
            try { HandleOrphanedGame(profile, dir, game); }
            catch (Exception ex) { Log($"[Launcher] Could not check the game of an earlier launcher in '{dir}' (pid {game.Pid}): {ex.Message}"); }
        }
    }

    private void HandleOrphanedGame(LauncherProfile profile, string dir, RunningGameInfo game)
    {
        if (_runningProcesses.Keys.Any(p => { try { return p.Id == game.Pid; } catch (InvalidOperationException) { return false; } })) return; // this launcher started it
        DateTime since = game.StartedAt.ToLocalTime();
        Process? process = null;
        if (RunningGameMarker.IsAlive(game))
        {
            try
            {
                process = Process.GetProcessById(game.Pid);
                _ = process.StartTime; // opens the handle now: its exit code stays readable after the game is gone
            }
            catch (Exception ex) when (ex is ArgumentException or InvalidOperationException or System.ComponentModel.Win32Exception)
            {
                process?.Dispose();
                process = null; // gone meanwhile
            }
        }
        DateTime? evidence = process == null ? CrashReporter.NewestEvidence(dir, since) : null;
        switch (OrphanedGame.Classify(process != null, evidence))
        {
            case OrphanedGameState.Running:
                _runningProcesses[process!] = dir; UpdateAnimationTimers();
                DiscordPresence.GameRunning(true);
                var sessionMessages = new List<string>();
                GameSession.Attach(process!, dir, game.LoadedMods, message =>
                    {
                        lock (sessionMessages) sessionMessages.Add(message);
                        Log($"[Shared] {message}");
                    },
                    afterExit: () => OnGameExitedAsync(process!, profile, dir, sessionMessages), adopted: true);
                StatusText.Text = "Game running.";
                Log($"[Launcher] Watching a game that was already running when the launcher started (profile '{profile.Name}', pid {game.Pid}, started {since:HH:mm:ss}); its exit or crash is handled as usual.");
                break;

            case OrphanedGameState.MissedCrash:
                RunningGameMarker.Delete(dir, game.Pid); // handled once, whatever happens below
                DateTime newest = evidence!.Value, now = DateTime.Now;
                Log("[Launcher] The game crashed while the launcher was not running.");
                StatusText.Text = "Game crashed! (the launcher was not running, exit code unknown)";
                var policy = GetRelaunchPolicy(profile.Id);
                string relaunchReason = "Auto-relaunch disabled in settings.";
                bool canRelaunch = false;
                if (settings.AutoRelaunchOnCrash)
                {
                    if (!OrphanedGame.MayRelaunch(newest, now))
                        relaunchReason = $"The crash is from {newest:HH:mm:ss}, {(now - newest).TotalMinutes:0} minutes ago: an old crash never starts the game by surprise.";
                    else
                        canRelaunch = policy.ShouldRelaunch(newest - since, false, DateTimeOffset.UtcNow, out relaunchReason);
                }
                Log($"[Crash Detection] {relaunchReason}");
                HandleCrashDetection(dir, canRelaunch, relaunchReason, profile);
                if (settings.SendCrashReports && string.IsNullOrEmpty(profile.PackwizUrl) && policy.ShouldSendCrashReport(DateTimeOffset.UtcNow))
                    _ = SendCrashReportAsync(dir, null, profile.MinecraftVersion, "unknown (the launcher was not running)", since: since);
                break;

            default:
                RunningGameMarker.Delete(dir, game.Pid);
                Log($"[Launcher] A game of an earlier launcher (pid {game.Pid}, started {since:HH:mm:ss}) ended without crash evidence; its running marker was removed.");
                break;
        }
    }
}
