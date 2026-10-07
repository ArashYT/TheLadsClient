using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public sealed record WorldSource(string Name, string Category, string GameDirectory, string Version = "");
/// <summary>GameMode is "" when level.dat could not be read; Icon is the world's icon.png when the game has written one.</summary>
public sealed record WorldEntry(string Name, string Folder, string Category, string Source, string Version, DateTime LastPlayed, string? Warning,
    string GameMode = "", bool? Cheats = null, string? Icon = null);
public sealed record WorldCatalog(IReadOnlyList<WorldEntry> Worlds, IReadOnlyList<string> Warnings);

/// <summary>Read-only world inventory. Resolves shared junctions so a save appears once; never opens or migrates worlds.</summary>
public sealed class WorldCatalogService
{
    /// <summary>Written into each game folder: the world folders LadsCore's in-game picker offers.</summary>
    public const string GameSourcesFile = "lads-world-sources.json";
    private readonly string _sourcesFile;
    public WorldCatalogService(string launcherRoot) => _sourcesFile = Path.Combine(launcherRoot, "world-sources.json");

    public IReadOnlyList<WorldSource> LoadCustomSources() => File.Exists(_sourcesFile)
        ? JsonSerializer.Deserialize<List<WorldSource>>(File.ReadAllText(_sourcesFile)) ?? new()
        : new List<WorldSource>();

    public void AddCustomSource(string gameDirectory)
    {
        string full = Path.GetFullPath(gameDirectory);
        if (!Directory.Exists(Path.Combine(full, "saves")))
            throw new IOException("Choose an instance folder containing a saves folder.");
        var sources = LoadCustomSources().ToList();
        if (sources.Any(s => SafeFileOps.PathsEqual(s.GameDirectory, full))) return;
        sources.Add(new WorldSource(Path.GetFileName(full), "Custom Instances", full));
        Directory.CreateDirectory(Path.GetDirectoryName(_sourcesFile)!);
        string temp = _sourcesFile + ".tmp";
        File.WriteAllText(temp, JsonSerializer.Serialize(sources, new JsonSerializerOptions { WriteIndented = true }));
        File.Move(temp, _sourcesFile, true);
    }

    public static IEnumerable<WorldSource> ProfileSources(IEnumerable<LauncherProfile> profiles, IPathService paths) =>
        profiles.Select(p => new WorldSource(p.Name,
            string.IsNullOrWhiteSpace(p.CustomGameDir) && p.Id == p.MinecraftVersion ? "Specific Version" : "Custom Instances",
            paths.GetProfileDirectory(p), p.MinecraftVersion));

    public void WriteGameSources(string gameDirectory, string globalRoot, IEnumerable<WorldSource> profiles)
    {
        var sources = new[] { new WorldSource("Global .minecraft", "Global .minecraft", globalRoot) }
            .Concat(profiles).Concat(LoadCustomSources()).ToList();
        File.WriteAllText(Path.Combine(gameDirectory, GameSourcesFile),
            JsonSerializer.Serialize(sources, new JsonSerializerOptions { WriteIndented = true }));
    }

    public static WorldCatalog Scan(IEnumerable<WorldSource> sources)
    {
        var worlds = new List<WorldEntry>();
        var warnings = new List<string>();
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var source in sources)
        {
            try
            {
                string saves = Path.Combine(source.GameDirectory, "saves");
                if (!Directory.Exists(saves)) continue;
                saves = SafeFileOps.GetFinalPath(saves);
                if (!seen.Add(saves)) continue;
                foreach (string folder in Directory.EnumerateDirectories(saves))
                {
                    string metadata = Path.Combine(folder, "level.dat");
                    if (!File.Exists(metadata)) continue;
                    string name = Path.GetFileName(folder), version = source.Version, mode = "";
                    DateTime played = File.GetLastWriteTimeUtc(metadata);
                    string? warning = null;
                    bool? cheats = null;
                    try
                    {
                        using var stream = new FileStream(metadata, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
                        var root = Nbt.Read(Nbt.ReadCapped(stream)).Root;
                        var data = root["Data"] as NbtCompound ?? root;
                        name = (data["LevelName"] as NbtString)?.Value ?? name;
                        string? saved = ((data["Version"] as NbtCompound)?["Name"] as NbtString)?.Value;
                        // Version{} and DataVersion arrived in the 1.9 snapshots; a readable save with neither is 1.8 or older.
                        version = saved ?? (version.Length == 0 && data["DataVersion"] == null ? LegacyVersion : version);
                        if (data["LastPlayed"] is NbtNumber last && last.RawValue > 0)
                            played = DateTimeOffset.FromUnixTimeMilliseconds(last.RawValue).UtcDateTime;
                        // 26.x moved hardcore into difficulty_settings; older saves keep it at the top of Data.
                        var hardcore = (data["difficulty_settings"] as NbtCompound)?["hardcore"] as NbtNumber ?? data["hardcore"] as NbtNumber;
                        mode = GameModeName((data["GameType"] as NbtNumber)?.RawValue ?? 0, hardcore?.RawValue is not (null or 0));
                        if (data["allowCommands"] is NbtNumber commands) cheats = commands.RawValue != 0;
                    }
                    catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException or ArgumentException)
                    { warning = "Metadata unavailable: " + e.Message; }
                    string icon = Path.Combine(folder, "icon.png");
                    worlds.Add(new(name, folder, source.Category, source.Name, version, played, warning, mode, cheats, File.Exists(icon) ? icon : null));
                }
            }
            catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException or ArgumentException)
            { warnings.Add(source.Name + ": " + e.Message); }
        }
        return new(worlds.OrderByDescending(w => w.LastPlayed).ToList(), warnings);
    }

    public const string LegacyVersion = "Pre-1.9";

    public static string GameModeName(long gameType, bool hardcore) => hardcore ? "Hardcore" : gameType switch
    {
        1 => "Creative", 2 => "Adventure", 3 => "Spectator", _ => "Survival"
    };

    /// <summary>"just now", "5 minutes ago", "yesterday", "3 weeks ago" … for the world cards; the exact date goes in a tooltip.</summary>
    public static string Relative(DateTime utc, DateTime nowUtc)
    {
        var span = nowUtc - utc;
        if (span.TotalSeconds < 60) return "just now";
        static string Unit(int n, string unit) => n == 1 ? $"1 {unit} ago" : $"{n} {unit}s ago";
        if (span.TotalMinutes < 60) return Unit((int)span.TotalMinutes, "minute");
        if (span.TotalHours < 24) return Unit((int)span.TotalHours, "hour");
        if (span.TotalDays < 2) return "yesterday";
        if (span.TotalDays < 7) return Unit((int)span.TotalDays, "day");
        if (span.TotalDays < 31) return Unit((int)(span.TotalDays / 7), "week");
        if (span.TotalDays < 365) return Unit(Math.Max(1, (int)(span.TotalDays / 30.44)), "month");
        return Unit((int)(span.TotalDays / 365.25), "year");
    }

    public static string FormatSize(long bytes) => bytes switch
    {
        < 1024 => $"{bytes} B",
        < 1024 * 1024 => $"{bytes / 1024.0:0} KB",
        < 1024L * 1024 * 1024 => $"{bytes / (1024.0 * 1024):0.#} MB",
        _ => $"{bytes / (1024.0 * 1024 * 1024):0.##} GB"
    };

    /// <summary>Bytes on disk under a world folder. Skips links so a junctioned folder is not counted twice; unreadable entries are ignored.</summary>
    public static long FolderSize(string folder)
    {
        var options = new EnumerationOptions { RecurseSubdirectories = true, IgnoreInaccessible = true, AttributesToSkip = FileAttributes.ReparsePoint };
        long total = 0;
        try { foreach (var file in new DirectoryInfo(folder).EnumerateFiles("*", options)) total += file.Length; }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        return total;
    }

    public static IEnumerable<WorldEntry> Filter(IEnumerable<WorldEntry> worlds, string query, string category, string version)
    {
        query = query.Trim();
        return worlds.Where(w => (category == "All locations" || w.Category == category)
            && (version == "All versions" || w.Version == version)
            && (query.Length == 0 || $"{w.Name} {Path.GetFileName(w.Folder)} {w.Source} {w.Version} {w.GameMode}".Contains(query, StringComparison.OrdinalIgnoreCase)));
    }
}
