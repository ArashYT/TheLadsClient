using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TheLadsLauncher.Services;

/// <param name="Others">Other copies of the same profile that are still running (multiple copies allowed), newest first.</param>
public sealed record RunningGameInfo(int Pid, DateTime StartedAt, IReadOnlyList<string> LoadedMods, IReadOnlyList<RunningGameInfo>? Others = null);

/// <summary>
/// <c>&lt;gameDir&gt;\.lads-running.json</c> {pid, startedAt, loadedMods, others}: written when a launcher starts the game, deleted on
/// exit. It lets any launcher instance (also after a restart) tell that a profile's game is still running. The newest game is at
/// the top level; other running copies of the same profile stay in "others", so closing one copy keeps the marker for the rest.
/// </summary>
public static class RunningGameMarker
{
    public const string FileName = ".lads-running.json";
    private static readonly JsonSerializerOptions Json = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase, DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };
    // ponytail: in-process lock only; two launcher processes writing the same marker at the same instant can still race.
    private static readonly object Gate = new();

    public static string PathFor(string gameDirectory) => Path.Combine(gameDirectory, FileName);

    public static void Write(string gameDirectory, int pid, DateTime startedAtUtc, IReadOnlyList<string> loadedMods)
    {
        lock (Gate)
        {
            var path = PathFor(gameDirectory);
            var others = Games(File.Exists(path) ? Read(path) : null).Where(g => g.Pid != pid && IsLive(g)).ToList();
            Save(path, new RunningGameInfo(pid, startedAtUtc.ToUniversalTime(), loadedMods, others.Count == 0 ? null : others));
        }
    }

    /// <summary>A game of the marker that is really running: pid alive, a java/javaw process, started within 5 s of its entry
    /// (the newest first). A marker without a running game, or an unreadable one, is deleted.</summary>
    public static RunningGameInfo? GetRunning(string gameDirectory)
    {
        var path = PathFor(gameDirectory);
        if (!File.Exists(path)) return null;
        var live = Games(Read(path)).FirstOrDefault(IsLive);
        if (live != null) return live;
        File.Delete(path);
        return null;
    }

    public static bool IsRunning(string gameDirectory) => GetRunning(gameDirectory) != null;

    /// <summary>Removes <paramref name="pid"/>'s game from the marker (another game may have replaced it at the top). The marker
    /// is deleted when no other copy of the profile is still running.</summary>
    public static void Delete(string gameDirectory, int pid)
    {
        lock (Gate)
        {
            var path = PathFor(gameDirectory);
            if (!File.Exists(path)) return;
            var games = Games(Read(path)).ToList();
            if (games.Count > 0 && games.All(g => g.Pid != pid)) return;
            var remaining = games.Where(g => g.Pid != pid && IsLive(g)).ToList();
            if (remaining.Count == 0) File.Delete(path);
            else Save(path, remaining[0] with { Others = remaining.Count > 1 ? remaining.Skip(1).ToList() : null });
        }
    }

    private static IEnumerable<RunningGameInfo> Games(RunningGameInfo? info) =>
        info == null ? Enumerable.Empty<RunningGameInfo>() : (info.Others ?? Array.Empty<RunningGameInfo>()).Where(o => o?.LoadedMods != null)
            .Select(o => o with { Others = null }).Prepend(info with { Others = null });

    private static void Save(string path, RunningGameInfo info)
    {
        var temp = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            File.WriteAllBytes(temp, JsonSerializer.SerializeToUtf8Bytes(info, Json));
            if (File.Exists(path)) File.Replace(temp, path, destinationBackupFileName: null);
            else File.Move(temp, path);
        }
        finally
        {
            if (File.Exists(temp)) File.Delete(temp);
        }
    }

    private static RunningGameInfo? Read(string path)
    {
        try
        {
            using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            var info = JsonSerializer.Deserialize<RunningGameInfo>(stream, Json);
            return info?.LoadedMods == null ? null : info;
        }
        catch (JsonException)
        {
            // A torn or foreign file cannot identify a process, so it counts as stale.
            return null;
        }
        catch (FileNotFoundException)
        {
            return null; // deleted by the game's exit handler meanwhile
        }
    }

    private static bool IsLive(RunningGameInfo info)
    {
        try
        {
            using var process = Process.GetProcessById(info.Pid);
            if (process.HasExited) return false;
            if (!process.ProcessName.Equals("java", StringComparison.OrdinalIgnoreCase)
                && !process.ProcessName.Equals("javaw", StringComparison.OrdinalIgnoreCase)) return false;
            return Math.Abs((process.StartTime.ToUniversalTime() - info.StartedAt.ToUniversalTime()).TotalSeconds) <= 5;
        }
        catch (ArgumentException)
        {
            return false; // no process with that id
        }
        catch (InvalidOperationException)
        {
            return false; // exited while being inspected
        }
        catch (Win32Exception)
        {
            return false; // not inspectable, so not a game this user's launcher started
        }
    }
}
