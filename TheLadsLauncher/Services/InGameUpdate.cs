using System;
using System.IO;
using System.Text.Json.Nodes;

namespace TheLadsLauncher.Services;

/// <summary>
/// The hand-off behind the Update button on the game's title screen (Core: LauncherUpdate), for the profiles Play starts: while a
/// verified update waits, the launcher keeps <see cref="NoticeFile"/> in the game folder ({"version":"1.13.9","launcher":1234}, its
/// own process id); the game shows the button for it, writes <see cref="RequestFile"/> and quits; when it has exited the launcher
/// takes the request, installs the update and starts that profile again (<see cref="PlayArg"/>).
/// </summary>
public static class InGameUpdate
{
    public const string NoticeFile = "lads_update.json", RequestFile = "lads_update_request.json";
    /// <summary>Restart argument, followed by the id of the profile the updated launcher starts as soon as it is open.</summary>
    public const string PlayArg = "--play-profile";

    /// <summary>Called right before a game starts: a request left over from an earlier run must not update at this one's exit, and
    /// the notice says what is ready now (<paramref name="readyVersion"/> null: nothing).</summary>
    public static void Prepare(string gameDirectory, string? readyVersion)
    {
        Directory.CreateDirectory(gameDirectory);
        File.Delete(Path.Combine(gameDirectory, RequestFile));
        Notify(gameDirectory, readyVersion);
    }

    /// <summary>Writes the notice for <paramref name="readyVersion"/>, or deletes it when that is null.</summary>
    public static void Notify(string gameDirectory, string? readyVersion)
    {
        string target = Path.Combine(gameDirectory, NoticeFile), temporary = target + ".tmp";
        if (readyVersion == null)
        {
            File.Delete(target);
            return;
        }
        File.WriteAllText(temporary, new JsonObject { ["version"] = readyVersion, ["launcher"] = Environment.ProcessId }.ToJsonString());
        File.Move(temporary, target, overwrite: true);
    }

    /// <summary>The game's request, deleted. True: the game closed through its Update button.</summary>
    public static bool TakeRequest(string gameDirectory)
    {
        string file = Path.Combine(gameDirectory, RequestFile);
        if (!File.Exists(file)) return false;
        try { File.Delete(file); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { } // seen once already; the next Prepare clears a leftover
        return true;
    }

    /// <summary>The profile id that follows <see cref="PlayArg"/>, or null.</summary>
    public static string? PlayProfile(string[] args)
    {
        int index = Array.IndexOf(args, PlayArg);
        return index >= 0 && index + 1 < args.Length && !string.IsNullOrWhiteSpace(args[index + 1]) ? args[index + 1] : null;
    }
}
