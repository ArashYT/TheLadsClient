using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public static class ProfileTools
{
    // Deliberate settings-only format: no arbitrary paths, recursive directories, jars, saves or account files.
    public static readonly string[] SettingsFiles = { "options.txt", "thelads_config.json", "lads-mod-state.json" };
    private const int MaximumBytes = 2 * 1024 * 1024;
    public sealed record Preset(int Format, string Name, string MinecraftVersion, string? FabricVersion,
        Dictionary<string, string> Settings);
    public sealed record Check(string Name, string Detail, bool Passed);

    public static IEnumerable<LauncherProfile> Filter(IEnumerable<LauncherProfile> profiles, string? query, string? version) =>
        profiles.Where(p => (string.IsNullOrWhiteSpace(version) || version == "All versions" || p.MinecraftVersion == version)
            && (string.IsNullOrWhiteSpace(query) || (p.Name + " " + p.MinecraftVersion).Contains(query.Trim(), StringComparison.OrdinalIgnoreCase)))
            .OrderByDescending(p => p.IsFavorite).ThenBy(p => p.Name, StringComparer.OrdinalIgnoreCase);

    /// <summary>The versions the Play screen offers; profiles for other versions are listed greyed out.</summary>
    public static readonly string[] PlayableVersions = { "26.3", "26.2", "1.8.9" };

    /// <summary>Newest Minecraft version first (1.8.9 last); "Latest Release" ahead of the profile it matches.</summary>
    public static IEnumerable<LauncherProfile> NewestFirst(IEnumerable<LauncherProfile> profiles) =>
        profiles.OrderByDescending(p => Version.TryParse(p.MinecraftVersion, out var v) ? v : new Version(0, 0))
            .ThenByDescending(p => p.Name == ProfileService.LatestReleaseName)
            .ThenBy(p => p.Name, StringComparer.OrdinalIgnoreCase);

    public static Preset Capture(LauncherProfile profile, string gameDirectory)
    {
        if (RunningGameMarker.IsRunning(gameDirectory)) throw new IOException("Close this profile's game before copying its settings.");
        var settings = new Dictionary<string, string>();
        foreach (string name in SettingsFiles)
        {
            string path = Path.Combine(gameDirectory, name);
            if (!File.Exists(path)) continue;
            if (SafeFileOps.IsLink(path)) throw new IOException($"'{name}' is linked; it was not copied.");
            if (new FileInfo(path).Length > MaximumBytes) throw new IOException($"'{name}' is too large for a settings preset.");
            settings[name] = File.ReadAllText(path);
        }
        return Validate(new(1, profile.Name, profile.MinecraftVersion, profile.FabricVersion, settings));
    }

    public static string Serialize(Preset preset)
    {
        string text = JsonSerializer.Serialize(Validate(preset), new JsonSerializerOptions { WriteIndented = true });
        if (System.Text.Encoding.UTF8.GetByteCount(text) > MaximumBytes) throw new InvalidDataException("Settings preset exceeds 2 MB.");
        return text;
    }

    public static Preset Read(string path)
    {
        if (new FileInfo(path).Length > MaximumBytes) throw new InvalidDataException("Settings preset exceeds 2 MB.");
        return Validate(JsonSerializer.Deserialize<Preset>(File.ReadAllText(path)) ?? throw new InvalidDataException("Empty preset."));
    }

    public static Preset Validate(Preset preset)
    {
        if (preset.Format != 1 || string.IsNullOrWhiteSpace(preset.Name) || preset.Name.Length > 100 || preset.Settings == null
            || preset.Settings.Count > SettingsFiles.Length || preset.Settings.Any(p => !SettingsFiles.Contains(p.Key) || p.Value == null)
            || preset.Settings.Sum(p => (long)System.Text.Encoding.UTF8.GetByteCount(p.Value)) > MaximumBytes)
            throw new InvalidDataException("Invalid or unsupported settings preset.");
        var profile = NewProfile(preset);
        GameVersionPolicy.ResolveVersionId(profile);
        return preset;
    }

    public static LauncherProfile NewProfile(Preset preset) => new()
    {
        Name = preset.Name, MinecraftVersion = preset.MinecraftVersion, FabricVersion = preset.FabricVersion,
        JavaMajorVersion = GameVersionPolicy.GetRequiredJavaMajor(preset.MinecraftVersion), IsIsolated = true
    };

    public static LauncherProfile Import(Preset preset, string profilesDirectory)
    {
        Validate(preset);
        var profile = NewProfile(preset);
        string directory = Path.Combine(profilesDirectory, profile.Id);
        Directory.CreateDirectory(directory);
        foreach (var file in preset.Settings) File.WriteAllText(Path.Combine(directory, file.Key), file.Value);
        return profile;
    }

    public static IReadOnlyList<Check> Inspect(LauncherProfile profile, IPathService paths, int maximumRamMb, string javaPath)
    {
        var checks = new List<Check>();
        try { checks.Add(new("Game version", GameVersionPolicy.ResolveVersionId(profile), true)); }
        catch (ArgumentException e) { checks.Add(new("Game version", e.Message, false)); }
        int required = GameVersionPolicy.GetRequiredJavaMajor(profile.MinecraftVersion);
        string rule = GameVersionPolicy.DescribeJava(profile.MinecraftVersion, required);
        if (!File.Exists(javaPath)) checks.Add(new("Java", $"{rule} is required. No runtime found at {javaPath}; use Settings → Download Java.", false));
        else
        {
            try { int? major = new JavaService(paths).GetJavaMajorVersion(javaPath); checks.Add(new("Java", $"Java {major?.ToString() ?? "unknown"}; requires {rule}", GameVersionPolicy.AcceptsJava(profile.MinecraftVersion, required, major))); }
            catch (Exception e) when (e is IOException or InvalidOperationException or System.ComponentModel.Win32Exception)
            { checks.Add(new("Java", e.Message, false)); }
        }
        checks.Add(new("Memory", $"{maximumRamMb:N0} MB maximum heap (recommended at least 2,048 MB)", maximumRamMb >= 2048));
        string game = paths.GetProfileDirectory(profile);
        try
        {
            var drive = new DriveInfo(Path.GetPathRoot(Path.GetFullPath(game))!);
            checks.Add(new("Disk space", $"{drive.AvailableFreeSpace / (1024d * 1024 * 1024):F1} GB free on {drive.Name}", drive.AvailableFreeSpace >= 2L * 1024 * 1024 * 1024));
            bool running = RunningGameMarker.IsRunning(game);
            checks.Add(new("Game state", running ? "This profile is running." : "This profile is ready to launch.", !running));
        }
        catch (IOException e) { checks.Add(new("Game folder", e.Message, false)); }
        checks.Add(new("Settings location", GameVersionPolicy.KeepsOwnWorlds(profile.MinecraftVersion)
            ? $"Separate settings, worlds and packs (Minecraft {profile.MinecraftVersion} never uses the shared ones): {game}"
            : profile.IsIsolated ? $"Separate settings: {game}" : "Shared options.txt; worlds and packs retain global sharing.", true));
        return checks;
    }
}
