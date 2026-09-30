using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;

namespace TheLadsLauncher.Services;

public sealed record ScreenshotRoot(
    [property: JsonPropertyName("path")] string Path,
    [property: JsonPropertyName("label")] string Label)
{
    [JsonIgnore] public bool IncludeDirectImages { get; init; }
    [JsonIgnore] public bool LadsProfile { get; init; }
}
public sealed record ScreenshotEntry(string Path, DateTime Time, string Source, bool IsExternal);
public sealed record ScreenshotCatalog(IReadOnlyList<ScreenshotEntry> Entries, IReadOnlyList<string> Warnings, int Folders);

/// <summary>Read-only inventory across launcher instances. Never copies or changes another launcher's content.</summary>
public sealed class ScreenshotCatalogService
{
    private readonly string _sharedRoot;
    private readonly string _home;
    private readonly string _roaming;
    private readonly string _local;
    private readonly string _documents;
    private readonly bool _discoverLaunchers;
    private string SourcesFile => System.IO.Path.Combine(_sharedRoot, "config", "lads-screenshot-sources.json");
    private static readonly HashSet<string> Skip = new(StringComparer.OrdinalIgnoreCase)
        { "mods", "assets", "libraries", "runtime", "runtimes", "logs", "cache", "caches", "saves", "resourcepacks", "shaderpacks", ".git", "natives" };
    private sealed record Sources([property: JsonPropertyName("version")] int Version,
        [property: JsonPropertyName("roots")] List<ScreenshotRoot> Roots);
    /// <summary>Folders scanned per root, so one huge launcher cannot hide the roots after it.</summary>
    public int FolderLimit { get; init; } = 20000;

    // Explicit environment paths make the scanner fully sandboxable, including config discovery.
    public ScreenshotCatalogService(string sharedRoot, string? home = null, string? roaming = null,
        string? local = null, string? documents = null, bool discoverLaunchers = true)
    {
        _sharedRoot = sharedRoot;
        _home = home ?? Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        _roaming = roaming ?? Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        _local = local ?? Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        _documents = documents ?? Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
        _discoverLaunchers = discoverLaunchers;
    }

    public IReadOnlyList<ScreenshotRoot> LoadCustomRoots()
    {
        if (!File.Exists(SourcesFile)) return Array.Empty<ScreenshotRoot>();
        // An unreadable file like any other: scans and launches skip it with a notice, edits refuse and keep it.
        if (new FileInfo(SourcesFile).Length > 1024 * 1024) throw new IOException("Screenshot sources file is too large.");
        var source = JsonSerializer.Deserialize<Sources>(File.ReadAllText(SourcesFile));
        return source?.Roots?.Where(r => r != null && !string.IsNullOrWhiteSpace(r.Path)).Select(r => r with { IncludeDirectImages = true }).ToList() ?? new();
    }

    public void AddRoot(string path)
    {
        string full = SafeFileOps.GetFinalPath(path);
        if (!Directory.Exists(full)) throw new DirectoryNotFoundException("Choose an existing screenshots or instance folder.");
        var roots = LoadCustomRoots().ToList();
        if (roots.Any(r => SafeFileOps.PathsEqual(SafeFileOps.GetFinalPath(r.Path), full))) return;
        roots.Add(new(full, System.IO.Path.GetFileName(full)));
        SaveRoots(roots);
    }

    /// <summary>Stops listing a folder added with <see cref="AddRoot"/>; its files are not touched.</summary>
    public void RemoveRoot(string path)
    {
        var roots = LoadCustomRoots().ToList();
        if (roots.RemoveAll(r => SafeFileOps.PathsEqual(r.Path, path)) > 0) SaveRoots(roots);
    }

    private void SaveRoots(List<ScreenshotRoot> roots)
    {
        Directory.CreateDirectory(System.IO.Path.GetDirectoryName(SourcesFile)!);
        string staging = SourcesFile + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            File.WriteAllText(staging, JsonSerializer.Serialize(new Sources(1, roots), new JsonSerializerOptions { WriteIndented = true }));
            File.Move(staging, SourcesFile, true);
        }
        finally { if (File.Exists(staging)) File.Delete(staging); }
    }

    public IReadOnlyList<ScreenshotRoot> DiscoverRoots(ICollection<string>? warnings = null, CancellationToken token = default)
    {
        token.ThrowIfCancellationRequested();
        var roots = new List<ScreenshotRoot> { new(System.IO.Path.Combine(_sharedRoot, "screenshots"), "Lads / Shared") };
        // Custom roots come first, so an explicit image folder is not swallowed by an automatic parent scan.
        try { roots.AddRange(LoadCustomRoots()); }
        catch (Exception e) when (IsReadFailure(e)) { warnings?.Add("Custom folders: " + e.Message); }
        if (_discoverLaunchers)
        {
            foreach (string data in new[] { _roaming, _local })
            foreach (string name in new[] { "com.modrinth.theseus", "ModrinthApp", "Modrinth App" })
            {
                string app = System.IO.Path.Combine(data, name);
                roots.Add(new(System.IO.Path.Combine(app, "profiles"), "Modrinth"));
                ReadJsonPaths(System.IO.Path.Combine(app, "settings.json"), new[] { "custom_dir", "customDir" }, "Modrinth", roots, warnings, token);
                // Current Modrinth App stores settings in SQLite; Windows ships its read-only SQLite API.
                foreach (string database in new[] { "app.db", "theseus.db" })
                    if (ModrinthScreenshotSettings.ReadCustomDirectory(System.IO.Path.Combine(app, database), warnings) is { Length: > 0 } custom)
                        roots.Add(new(custom, "Modrinth"));
            }
            foreach (string app in new[] { System.IO.Path.Combine(_roaming, "PrismLauncher"), System.IO.Path.Combine(_local, "PrismLauncher") })
            {
                roots.Add(new(System.IO.Path.Combine(app, "instances"), "Prism"));
                ReadPrismConfig(app, roots, warnings, token);
            }
            roots.Add(new(System.IO.Path.Combine(_home, "curseforge", "minecraft", "Instances"), "CurseForge"));
            roots.Add(new(System.IO.Path.Combine(_documents, "CurseForge", "Minecraft", "Instances"), "CurseForge"));
            roots.Add(new(System.IO.Path.Combine(_documents, "Curse", "Minecraft", "Instances"), "CurseForge"));
            ReadCurseForgeStorage(System.IO.Path.Combine(_roaming, "CurseForge", "storage.json"), roots, warnings);
            foreach (string data in new[] { _roaming, _local }) // older guesses, kept for existing setups
                foreach (string name in new[] { "CurseForge", "CurseForge App", "Overwolf/CurseForge" })
                    foreach (string config in new[] { "settings.json", "Settings.json", "minecraft.settings.json" })
                        ReadJsonPaths(System.IO.Path.Combine(data, name, config), new[] { "minecraftFolder", "minecraftModdingFolder", "minecraftGamePath", "installPath", "instancesPath" }, "CurseForge", roots, warnings, token);
        }
        return roots;
    }

    public ScreenshotCatalog Scan(IEnumerable<ScreenshotRoot>? ladsProfiles = null, CancellationToken token = default)
    {
        var warnings = new List<string>();
        var roots = DiscoverRoots(warnings, token).Concat((ladsProfiles ?? Array.Empty<ScreenshotRoot>()).Select(r => r with { LadsProfile = true }));
        var entries = new List<ScreenshotEntry>();
        var visited = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        string shared = SafeFileOps.GetFinalPath(System.IO.Path.Combine(_sharedRoot, "screenshots"));
        int folders = 0;
        foreach (var root in roots)
        {
            token.ThrowIfCancellationRequested();
            var pending = new Queue<(string Path, int Depth)>();
            pending.Enqueue((root.Path, 0));
            int directories = 0;
            while (pending.TryDequeue(out var item))
            {
                token.ThrowIfCancellationRequested();
                if (++directories > FolderLimit) { warnings.Add(root.Label + ": folder scan limit reached. Add a more specific folder to include its remaining instances."); break; }
                try
                {
                    if (!Directory.Exists(item.Path)) continue;
                    string final = SafeFileOps.GetFinalPath(item.Path);
                    int remainingDepth = 5 - item.Depth;
                    if (visited.TryGetValue(final, out int scannedDepth) && scannedDepth >= remainingDepth) continue;
                    visited[final] = remainingDepth; // a narrower custom root can extend a previous depth-limited scan
                    bool screenshots = System.IO.Path.GetFileName(final).Equals("screenshots", StringComparison.OrdinalIgnoreCase)
                        || System.IO.Path.GetFileName(item.Path).Equals("screenshots", StringComparison.OrdinalIgnoreCase);
                    if (screenshots || (item.Depth == 0 && root.IncludeDirectImages))
                    {
                        bool counted = screenshots;
                        if (counted) folders++;
                        foreach (string file in Directory.EnumerateFiles(final))
                        {
                            token.ThrowIfCancellationRequested();
                            string extension = System.IO.Path.GetExtension(file);
                            if (!extension.Equals(".png", StringComparison.OrdinalIgnoreCase) && !extension.Equals(".jpg", StringComparison.OrdinalIgnoreCase) && !extension.Equals(".jpeg", StringComparison.OrdinalIgnoreCase)) continue;
                            string image = SafeFileOps.GetFinalPath(file);
                            if (!seen.Add(image)) continue;
                            // 1.21.x profiles copy their screenshots into the shared folder (SyncScreenshotsToGlobal): list that copy only.
                            if (root.LadsProfile && IsSharedCopy(image, shared)) continue;
                            if (!counted) { folders++; counted = true; }
                            string instance = System.IO.Path.GetFileName(System.IO.Path.GetDirectoryName(final)) ?? "Instance";
                            if (instance is ".minecraft" or "minecraft") instance = System.IO.Path.GetFileName(System.IO.Path.GetDirectoryName(System.IO.Path.GetDirectoryName(final))) ?? "Instance";
                            entries.Add(new(image, File.GetLastWriteTime(image), root.Label == "Lads / Shared" ? root.Label : root.Label + " · " + instance, !SafeFileOps.IsSameOrInside(image, shared)));
                        }
                        if (screenshots) continue;
                    }
                    if (item.Depth >= 5) continue;
                    foreach (string child in Directory.EnumerateDirectories(final))
                    {
                        token.ThrowIfCancellationRequested();
                        if (!Skip.Contains(System.IO.Path.GetFileName(child))) pending.Enqueue((child, item.Depth + 1));
                    }
                }
                catch (Exception e) when (IsReadFailure(e)) { warnings.Add(root.Label + ": " + e.Message); }
            }
        }
        return new(entries.OrderByDescending(e => e.Time).ToList(), warnings, folders);
    }

    private static bool IsSharedCopy(string image, string sharedScreenshots)
    {
        var copy = new FileInfo(System.IO.Path.Combine(sharedScreenshots, System.IO.Path.GetFileName(image)));
        return copy.Exists && copy.Length == new FileInfo(image).Length;
    }

    /// <summary>
    /// Lads profiles' own copies of a shared screenshot (same name and size in their screenshots folder). Deleting only the shared
    /// file would let the next game exit copy it back. Looks only in the given Lads profile folders, never in other launchers'.
    /// </summary>
    public static IReadOnlyList<string> ProfileCopies(string sharedImage, string sharedScreenshots, IEnumerable<string> profileDirectories)
    {
        string image = SafeFileOps.GetFinalPath(sharedImage), shared = SafeFileOps.GetFinalPath(sharedScreenshots);
        if (!SafeFileOps.PathsEqual(System.IO.Path.GetDirectoryName(image)!, shared)) return Array.Empty<string>();
        return profileDirectories.Select(d => System.IO.Path.Combine(d, "screenshots", System.IO.Path.GetFileName(image))).Where(File.Exists)
            .Select(SafeFileOps.GetFinalPath).Distinct(StringComparer.OrdinalIgnoreCase)
            .Where(copy => !SafeFileOps.PathsEqual(copy, image) && IsSharedCopy(copy, shared)).ToList();
    }

    public void WriteGameSources(string gameDirectory, IEnumerable<ScreenshotRoot> profiles, CancellationToken token = default)
    {
        var roots = DiscoverRoots(token: token).Concat(profiles).ToList();
        token.ThrowIfCancellationRequested();
        string target = System.IO.Path.Combine(gameDirectory, "lads-screenshot-discovered.json");
        string staging = target + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            File.WriteAllText(staging, JsonSerializer.Serialize(new Sources(1, roots), new JsonSerializerOptions { WriteIndented = true }));
            File.Move(staging, target, true);
        }
        finally { if (File.Exists(staging)) File.Delete(staging); }
    }

    private static void ReadPrismConfig(string app, List<ScreenshotRoot> roots, ICollection<string>? warnings, CancellationToken token)
    {
        string file = System.IO.Path.Combine(app, "prismlauncher.cfg");
        try
        {
            if (!File.Exists(file) || new FileInfo(file).Length > 1024 * 1024) return;
            foreach (string line in File.ReadLines(file))
            {
                token.ThrowIfCancellationRequested();
                int equals = line.IndexOf('=');
                if (equals < 0 || !line[..equals].Trim().Equals("InstanceDir", StringComparison.OrdinalIgnoreCase)) continue;
                string path = line[(equals + 1)..].Trim().Trim('"').Replace("\\\\", "\\");
                if (path.Length > 0) roots.Add(new(System.IO.Path.GetFullPath(path, app), "Prism"));
            }
        }
        catch (Exception e) when (IsReadFailure(e)) { warnings?.Add("Prism settings: " + e.Message); }
    }

    // The CurseForge app keeps a custom folder in storage.json: "minecraft-settings" holds a JSON document as a string, whose
    // "minecraftRoot" (null when unchanged) contains the Instances folder.
    private static void ReadCurseForgeStorage(string file, List<ScreenshotRoot> roots, ICollection<string>? warnings)
    {
        try
        {
            if (!File.Exists(file)) return;
            if (new FileInfo(file).Length > 4 * 1024 * 1024) throw new IOException("storage.json is too large to read. Add its instance folder manually.");
            using var storage = JsonDocument.Parse(File.ReadAllText(file));
            if (storage.RootElement.ValueKind != JsonValueKind.Object || !storage.RootElement.TryGetProperty("minecraft-settings", out var text)
                || text.ValueKind != JsonValueKind.String) return;
            using var settings = JsonDocument.Parse(text.GetString()!);
            if (settings.RootElement.ValueKind == JsonValueKind.Object && settings.RootElement.TryGetProperty("minecraftRoot", out var root)
                && root.ValueKind == JsonValueKind.String && root.GetString() is { Length: > 0 } path && System.IO.Path.IsPathFullyQualified(path))
                roots.Add(new(System.IO.Path.Combine(path, "Instances"), "CurseForge"));
        }
        catch (Exception e) when (IsReadFailure(e)) { warnings?.Add("CurseForge settings: " + e.Message); }
    }

    private static void ReadJsonPaths(string file, string[] keys, string label, List<ScreenshotRoot> roots, ICollection<string>? warnings, CancellationToken token)
    {
        try
        {
            if (!File.Exists(file) || new FileInfo(file).Length > 1024 * 1024) return;
            using var doc = JsonDocument.Parse(File.ReadAllText(file));
            void Visit(JsonElement node, int depth)
            {
                token.ThrowIfCancellationRequested();
                if (depth > 12) return;
                if (node.ValueKind == JsonValueKind.Object)
                    foreach (var property in node.EnumerateObject())
                    {
                        if (keys.Contains(property.Name, StringComparer.OrdinalIgnoreCase) && property.Value.ValueKind == JsonValueKind.String && property.Value.GetString() is { Length: > 0 } path && System.IO.Path.IsPathFullyQualified(path))
                            roots.Add(new(path, label));
                        else Visit(property.Value, depth + 1);
                    }
                else if (node.ValueKind == JsonValueKind.Array) foreach (var child in node.EnumerateArray()) Visit(child, depth + 1);
            }
            Visit(doc.RootElement, 0);
        }
        catch (Exception e) when (IsReadFailure(e)) { warnings?.Add(label + " settings: " + e.Message); }
    }

    private static bool IsReadFailure(Exception e) => e is IOException or UnauthorizedAccessException or ArgumentException or JsonException or NotSupportedException;
}
