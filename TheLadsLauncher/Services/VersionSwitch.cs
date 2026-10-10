using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

/// <summary>
/// The in-game "Switch Versions" hand-off, shared by every way the launcher starts a game (a profile, Zero SMP, a modpack): before
/// the game starts the launcher writes <see cref="VersionsFile"/> into the game folder (Core reads the folder from THELADS_DIR);
/// the game writes <see cref="RequestFile"/> and quits; when it has exited the launcher takes the request and starts that version.
/// </summary>
public static class VersionSwitch
{
    public const string VersionsFile = "lads_versions.json", RequestFile = "lads_next_version.json";

    /// <summary>Called right before a game starts: a request left over from an earlier run must not switch this one at its exit,
    /// and the in-game screen needs the versions it may offer. <paramref name="current"/> names what runs ("26.3", "Zero SMP").</summary>
    public static void Prepare(string gameDirectory, string current)
    {
        Directory.CreateDirectory(gameDirectory);
        File.Delete(Path.Combine(gameDirectory, RequestFile));
        var json = new JsonObject
        {
            ["current"] = current,
            ["versions"] = new JsonArray(ProfileTools.PlayableVersions.Select(v => (JsonNode)JsonValue.Create(v)!).ToArray())
        }.ToJsonString(new JsonSerializerOptions { WriteIndented = true });
        string target = Path.Combine(gameDirectory, VersionsFile), temporary = target + ".tmp";
        File.WriteAllText(temporary, json);
        File.Move(temporary, target, overwrite: true);
    }

    /// <summary>The game's request, read and deleted: the version it asks for, or null when there is none or it is refused
    /// (<paramref name="log"/> says why). Only the versions the Play screen offers can be switched to.</summary>
    public static string? TakeRequest(string gameDirectory, Action<string> log)
    {
        string file = Path.Combine(gameDirectory, RequestFile);
        if (!File.Exists(file)) return null;
        string version;
        try
        {
            using var doc = JsonDocument.Parse(File.ReadAllText(file));
            version = doc.RootElement.GetProperty("version").GetString()?.Trim() ?? "";
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or JsonException or KeyNotFoundException or InvalidOperationException)
        {
            log($"[Launcher] Could not read the game's version switch request: {ex.Message}");
            TryDelete(file);
            return null;
        }
        TryDelete(file);
        log($"[Launcher] Version switch requested to: {version}");
        string? refusal = GameVersionPolicy.IsComingNever(version) ? GameVersionPolicy.ComingNeverMessage
            : GameVersionPolicy.IsPaused(version) ? GameVersionPolicy.PausedMessage
            : ProfileTools.PlayableVersions.Contains(version) ? null
            : $"Minecraft {version} is not offered on the Play screen";
        if (refusal == null) return version;
        log($"[Launcher] Version switch refused: {refusal}.");
        return null;
    }

    /// <summary>After a game exited from <paramref name="gameDirectory"/>: when it asked for another version, that version's profile
    /// (the first launchable one saved, else the standard profile, made again with Core's loader and Java when it was deleted) becomes
    /// the active one. Returns it; null when the game asked for nothing (or something refused).</summary>
    public static LauncherProfile? Apply(string gameDirectory, IProfileService profiles, Action<string> log)
    {
        if (TakeRequest(gameDirectory, log) is not { } version) return null;
        var profile = profiles.GetProfiles().FirstOrDefault(p => p.MinecraftVersion == version && Launchable(p))
            ?? profiles.CreateProfile($"The Lads Client {version}", version, GameVersionPolicy.GetRequiredJavaMajor(version), false,
                GameVersionPolicy.UsesForge(version) ? null : "0.19.5");
        profiles.SetActiveProfile(profile.Id);
        return profile;
    }

    private static bool Launchable(LauncherProfile profile)
    {
        try
        {
            GameVersionPolicy.ResolveVersionId(profile);
            return !GameVersionPolicy.RequiresFabric(profile.MinecraftVersion) || !string.IsNullOrWhiteSpace(profile.FabricVersion);
        }
        catch (ArgumentException) { return false; }
    }

    private static void TryDelete(string file)
    {
        try { File.Delete(file); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { } // read once already; a leftover is cleared by the next Prepare
    }
}
