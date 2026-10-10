using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

/// <summary>
/// Settings → Controls edits one set of controls (key binds, field of view, sensitivity, distances, frame rate, GUI scale, VSync)
/// shared by every Lads Client profile that is not isolated. The launcher's shared options.txt holds it in modern key names (the
/// copy <see cref="GameOptionsService.SyncToInstance"/> feeds every launch from); a change also goes straight into each profile's
/// options.txt in that version's format: 1.8.9 stores LWJGL2 key codes, has no simulation distance and its GUI scale stops at
/// Large (3). A profile whose game is running gets the change when the game closes (<see cref="PendingFile"/>): Minecraft rewrites
/// options.txt on exit, and the exit sync would otherwise copy the old values back into the shared copy.
/// Modpack instances are not profiles and are never touched.
/// </summary>
public static class SharedControls
{
    /// <summary>In a profile's game folder: controls changed while its game ran (modern key names), applied when it closes or at
    /// its next launch, whichever the launcher sees first.</summary>
    public const string PendingFile = "lads-controls-pending.txt";

    /// <summary>The options.txt settings the tab edits besides the key binds (key_*).</summary>
    public static readonly IReadOnlyList<string> SettingKeys =
        new[] { "fov", "mouseSensitivity", "renderDistance", "simulationDistance", "maxFps", "guiScale", "enableVsync" };

    public static bool IsControl(string key) =>
        key.StartsWith("key_", StringComparison.Ordinal) || SettingKeys.Contains(key, StringComparer.Ordinal);

    /// <summary>Key binds 1.8.9 saves under another options.txt name than the newer versions: Lads' screenshot viewer, and the
    /// Essential keys its 1.8.9 build names by their English text.</summary>
    private static readonly Dictionary<string, string> Legacy18Names = new(StringComparer.Ordinal)
    {
        ["key_key.lads_screenshots.open_screenshots_screen"] = "key_key.theladscore.screenshots",
        ["key_keybind.name.CHAT_PEEK"] = "key_Chat Peek",
        ["key_keybind.name.COSMETIC_STUDIO"] = "key_Open Cosmetic Studio",
        ["key_keybind.name.COSMETICS_VISIBILITY_TOGGLE"] = "key_Cosmetic Visibility Toggle",
        ["key_keybind.name.ESSENTIAL_FRIENDS"] = "key_Open Friends Gui",
        ["key_keybind.name.INVITE_FRIENDS"] = "key_Invite Friends"
    };

    private static readonly Dictionary<string, string> ModernNames = Legacy18Names.ToDictionary(n => n.Value, n => n.Key, StringComparer.Ordinal);

    /// <summary>An options.txt key as 1.8.9 (<paramref name="legacy"/>) or the newer versions name it.</summary>
    public static string OptionKey(string key, bool legacy) =>
        (legacy ? Legacy18Names : ModernNames).TryGetValue(key, out var name) ? name : key;

    /// <summary>The controls of one parsed options.txt: keys in the newer versions' names, values as stored. A 1.8.9 file's line
    /// under a newer name of a key 1.8.9 names otherwise is a stale copy (launchers before 1.9.0 added it) and skipped.</summary>
    public static IEnumerable<(string Key, string Stored)> Controls(IReadOnlyDictionary<string, string> options, bool legacy)
    {
        foreach (var (stored, value) in options)
        {
            string key = legacy ? OptionKey(stored, false) : stored;
            if (!IsControl(key) || legacy && OptionKey(stored, true) is var own && own != stored && options.ContainsKey(own)) continue;
            yield return (key, value);
        }
    }

    /// <summary>A modern-format value as <paramref name="minecraftVersion"/> stores it, or null when that version has no such
    /// setting or no key code for the key (it keeps its own).</summary>
    public static string? ToVersion(string key, string value, string minecraftVersion)
    {
        bool legacy = GameOptionsService.IsLegacy18(minecraftVersion);
        if (key.StartsWith("key_", StringComparison.Ordinal)) return GameOptionsService.TranslateKeybindToTarget(key, value, legacy);
        if (!legacy) return value;
        return key switch
        {
            "simulationDistance" => null,
            // 1.8.9: 0 Auto, 1 Small, 2 Normal, 3 Large; anything larger reads as Large there.
            "guiScale" => int.TryParse(value, NumberStyles.Integer, CultureInfo.InvariantCulture, out int scale)
                ? Math.Clamp(scale, 0, 3).ToString(CultureInfo.InvariantCulture) : value,
            _ => value
        };
    }

    /// <summary>A stored value in modern format (1.8.9 key codes to key names), or null for a key code without a name.</summary>
    public static string? FromVersion(string key, string stored) =>
        key.StartsWith("key_", StringComparison.Ordinal) ? GameOptionsService.TranslateKeybindToTarget(key, stored, false) : stored;

    /// <summary>The controls in an options.txt (1.8.9's: <paramref name="legacy"/>), in modern format.</summary>
    public static Dictionary<string, string> Read(string? text, bool legacy = false)
    {
        var values = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var (key, stored) in Controls(GameOptionsService.ParseOptions(text ?? ""), legacy))
            if (FromVersion(key, stored) is { } value) values[key] = value;
        return values;
    }

    /// <summary>
    /// <paramref name="values"/> (modern format) into one options.txt text for <paramref name="minecraftVersion"/>: only those lines
    /// change, every other line stays. A key bind the version does not have is not added, and a value it cannot store is skipped.
    /// </summary>
    public static string Apply(string? text, IReadOnlyDictionary<string, string> values, string minecraftVersion)
    {
        var options = GameOptionsFile.Parse(text);
        var known = GameControls.VanillaKeys(minecraftVersion).Select(k => "key_" + k.Id).ToHashSet(StringComparer.Ordinal);
        bool legacy = GameOptionsService.IsLegacy18(minecraftVersion);
        foreach (var (key, value) in values)
        {
            string name = OptionKey(key, legacy);
            if (key.StartsWith("key_", StringComparison.Ordinal) && !known.Contains(key) && options.Get(name) == null) continue;
            if (ToVersion(key, value, minecraftVersion) is { } stored && options.Get(name) != stored) options.Set(name, stored);
        }
        return options.ToString();
    }

    /// <summary>Profiles the shared controls apply to: every launcher profile that is not isolated.</summary>
    public static IReadOnlyList<LauncherProfile> Targets(IEnumerable<LauncherProfile> profiles) => profiles.Where(p => !p.IsIsolated).ToList();

    /// <summary>
    /// What the tab shows: the shared copy's controls; anything it lacks from the profile played last (the newest options.txt),
    /// then the others. 1.8.9 key codes are read as key names.
    /// </summary>
    public static Dictionary<string, string> Load(IPathService paths, IEnumerable<LauncherProfile> targets, string? activeProfileId = null)
    {
        targets = targets.ToList();
        try { MergeOnce(paths, targets); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        var values = File.Exists(paths.SharedOptionsFile) ? Read(ReadText(paths.SharedOptionsFile)) : new(StringComparer.Ordinal);
        var files = targets.Select(p => (Profile: p, File: Path.Combine(paths.GetProfileDirectory(p), "options.txt")))
            .Where(t => File.Exists(t.File))
            .OrderByDescending(t => File.GetLastWriteTimeUtc(t.File))
            .ThenByDescending(t => t.Profile.Id == activeProfileId);
        foreach (var (profile, file) in files)
        {
            try
            {
                foreach (var (key, value) in Read(ReadText(file), GameOptionsService.IsLegacy18(profile.MinecraftVersion))) values.TryAdd(key, value);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        }
        return values;
    }

    /// <summary>Next to the shared options.txt: the shared copy as it was before <see cref="MergeOnce"/> first ran (kept).</summary>
    public const string BackupFile = "options.before-controls-sync.txt";

    /// <summary>Next to the shared options.txt: <see cref="MergeOnce"/> is done.</summary>
    public const string MergedFile = "lads-controls-merged.txt";

    /// <summary>
    /// Once (1.9.0): the controls of the shared copy and of every target profile's options.txt merged, each control taking its value
    /// from the newest of those files that has it (key binds only some versions have are kept), into the shared copy and every one
    /// of those options.txt in its version's format (<see cref="Save"/>), except a running game's: its exit sends only what the
    /// player changes in it, and its next launch takes the rest. Until 1.9.0 the versions could differ: 1.8.9 followed Lunar
    /// Client's controls and never gave its own back, and a profile kept its own controls at its first launch. The shared copy is
    /// backed up first (<see cref="BackupFile"/>). Returns the errors; with any, the merge runs again next time.
    /// </summary>
    public static IReadOnlyList<string> MergeOnce(IPathService paths, IEnumerable<LauncherProfile> targets)
    {
        var folder = Path.GetDirectoryName(paths.SharedOptionsFile)!;
        var marker = Path.Combine(folder, MergedFile);
        if (File.Exists(marker)) return Array.Empty<string>();
        Directory.CreateDirectory(folder);
        var backup = Path.Combine(folder, BackupFile);
        if (!File.Exists(backup)) WriteText(backup, File.Exists(paths.SharedOptionsFile) ? ReadText(paths.SharedOptionsFile) : "");

        // A profile whose game last ran on default options (it could not load them) has none of the player's.
        var profiles = targets.Where(p => File.Exists(Path.Combine(paths.GetProfileDirectory(p), "options.txt"))
            && !GameOptionsService.OptionsFailedToLoad(paths.GetProfileDirectory(p))).ToList();
        var sources = profiles.Select(p => (File: Path.Combine(paths.GetProfileDirectory(p), "options.txt"), Legacy: GameOptionsService.IsLegacy18(p.MinecraftVersion)))
            .Append((File: paths.SharedOptionsFile, Legacy: false)).Where(s => File.Exists(s.File)).OrderBy(s => File.GetLastWriteTimeUtc(s.File));
        var merged = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var (file, legacy) in sources)
            foreach (var (key, value) in Read(ReadText(file), legacy)) merged[key] = value;
        var errors = Save(paths, profiles.Where(p => !RunningGameMarker.IsRunning(paths.GetProfileDirectory(p))), merged).Errors;
        if (errors.Count == 0)
            WriteText(marker, $"Controls of the shared copy and {profiles.Count} profiles merged {DateTime.UtcNow:O}: "
                + string.Join(", ", profiles.Select(p => $"{p.Name} ({p.MinecraftVersion})")) + "\n");
        return errors;
    }

    public sealed record SaveResult(IReadOnlyList<LauncherProfile> Applied, IReadOnlyList<LauncherProfile> Deferred, IReadOnlyList<string> Errors);

    /// <summary>
    /// <paramref name="changes"/> (modern format) into the shared copy and every target profile's options.txt, translated for its
    /// version. A profile whose game runs gets them in <see cref="PendingFile"/> instead. One profile's error does not stop the rest.
    /// </summary>
    public static SaveResult Save(IPathService paths, IEnumerable<LauncherProfile> targets, IReadOnlyDictionary<string, string> changes)
    {
        var applied = new List<LauncherProfile>();
        var deferred = new List<LauncherProfile>();
        var errors = new List<string>();
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(paths.SharedOptionsFile)!);
            string? before = File.Exists(paths.SharedOptionsFile) ? ReadText(paths.SharedOptionsFile) : null;
            var shared = GameOptionsFile.Parse(before);
            foreach (var (key, value) in changes) if (shared.Get(key) != value) shared.Set(key, value);
            if (shared.ToString() != (before ?? "")) WriteText(paths.SharedOptionsFile, shared.ToString());
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { errors.Add("Shared copy: " + e.Message); }

        foreach (var profile in targets)
        {
            var dir = paths.GetProfileDirectory(profile);
            try
            {
                if (RunningGameMarker.IsRunning(dir))
                {
                    var pending = Path.Combine(dir, PendingFile);
                    var waiting = GameOptionsFile.Parse(File.Exists(pending) ? ReadText(pending) : "");
                    foreach (var (key, value) in changes) waiting.Set(key, value);
                    WriteText(pending, waiting.ToString());
                    deferred.Add(profile);
                    continue;
                }
                var file = Path.Combine(dir, "options.txt");
                if (SafeFileOps.IsLink(file)) throw new IOException($"'{file}' is a link; the launcher only edits a regular options.txt.");
                Directory.CreateDirectory(dir);
                string? before = File.Exists(file) ? ReadText(file) : null;
                string after = Apply(before, changes, profile.MinecraftVersion);
                if (after != before) WriteText(file, after);
                applied.Add(profile);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { errors.Add($"{profile.Name}: {e.Message}"); }
        }
        return new SaveResult(applied, deferred, errors);
    }

    /// <summary>After a game closes and before a launch: controls changed while it ran, into its options.txt. No-op without any.</summary>
    public static void ApplyPending(string gameDirectory, string minecraftVersion)
    {
        var pending = Path.Combine(gameDirectory, PendingFile);
        if (!File.Exists(pending)) return;
        var values = Read(ReadText(pending));
        var file = Path.Combine(gameDirectory, "options.txt");
        if (!SafeFileOps.IsLink(file))
        {
            string? before = File.Exists(file) ? ReadText(file) : null;
            string after = Apply(before, values, minecraftVersion);
            if (after != before) WriteText(file, after);
        }
        File.Delete(pending);
    }

    // Latin-1 maps every byte to one char: lines the launcher does not change are written back byte for byte.
    private static string ReadText(string path) => File.ReadAllText(path, Encoding.Latin1);

    private static void WriteText(string path, string text) =>
        LockFiles.WriteAtomicallyAsync(path, Encoding.Latin1.GetBytes(text)).GetAwaiter().GetResult();
}
