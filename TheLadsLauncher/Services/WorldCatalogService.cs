using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public sealed record WorldSource(string Name, string Category, string GameDirectory, string Version = "");
public sealed record WorldEntry(string Name, string Folder, string Category, string Source, string Version, DateTime LastPlayed, string? Warning);
public sealed record WorldCatalog(IReadOnlyList<WorldEntry> Worlds, IReadOnlyList<string> Warnings);

/// <summary>Read-only world inventory. Resolves shared junctions so a save appears once; never opens or migrates worlds.</summary>
public sealed class WorldCatalogService
{
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
        File.WriteAllText(Path.Combine(gameDirectory, "lads-world-sources.json"),
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
                    string name = Path.GetFileName(folder), version = source.Version;
                    DateTime played = File.GetLastWriteTimeUtc(metadata);
                    string? warning = null;
                    try
                    {
                        using var stream = new FileStream(metadata, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
                        var root = Nbt.Read(Nbt.ReadCapped(stream)).Root;
                        var data = root["Data"] as NbtCompound ?? root;
                        name = (data["LevelName"] as NbtString)?.Value ?? name;
                        version = ((data["Version"] as NbtCompound)?["Name"] as NbtString)?.Value ?? version;
                        if (data["LastPlayed"] is NbtNumber last && last.RawValue > 0)
                            played = DateTimeOffset.FromUnixTimeMilliseconds(last.RawValue).UtcDateTime;
                    }
                    catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException or ArgumentException)
                    { warning = "Metadata unavailable: " + e.Message; }
                    worlds.Add(new(name, folder, source.Category, source.Name, version, played, warning));
                }
            }
            catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException or ArgumentException)
            { warnings.Add(source.Name + ": " + e.Message); }
        }
        return new(worlds.OrderByDescending(w => w.LastPlayed).ToList(), warnings);
    }

    public static IEnumerable<WorldEntry> Filter(IEnumerable<WorldEntry> worlds, string query, string category, string version)
    {
        query = query.Trim();
        return worlds.Where(w => (category == "All locations" || w.Category == category)
            && (version == "All versions" || w.Version == version)
            && (query.Length == 0 || $"{w.Name} {Path.GetFileName(w.Folder)} {w.Source} {w.Version}".Contains(query, StringComparison.OrdinalIgnoreCase)));
    }
}
