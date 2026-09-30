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
        if (new FileInfo(SourcesFile).Length > 1024 * 1024) throw new InvalidDataException("Screenshot sources file is too large.");
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
            roots.Add(new(System.IO.Path.Combine(_documents, "Curse", "Minecraft", "Instances"), "CurseForge"));
            foreach (string data in new[] { _roaming, _local })
                foreach (string name in new[] { "CurseForge", "CurseForge App", "Overwolf/CurseForge" })
                    foreach (string config in new[] { "settings.json", "Settings.json", "minecraft.settings.json" })
                        ReadJsonPaths(System.IO.Path.Combine(data, name, config), new[] { "minecraftFolder", "minecraftModdingFolder", "minecraftGamePath", "installPath", "instancesPath" }, "CurseForge", roots, warnings, token);
        }
        return roots;
    }

    public ScreenshotCatalog Scan(IEnumerable<ScreenshotRoot>? additional = null, CancellationToken token = default)
    {
        var warnings = new List<string>();
        var roots = DiscoverRoots(warnings, token).Concat(additional ?? Array.Empty<ScreenshotRoot>());
        var entries = new List<ScreenshotEntry>();
        var visited = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        string shared = SafeFileOps.GetFinalPath(System.IO.Path.Combine(_sharedRoot, "screenshots"));
        int folders = 0, directories = 0;
        foreach (var root in roots)
        {
            token.ThrowIfCancellationRequested();
            var pending = new Queue<(string Path, int Depth)>();
            pending.Enqueue((root.Path, 0));
            while (pending.TryDequeue(out var item))
            {
                token.ThrowIfCancellationRequested();
                if (++directories > 20000) { warnings.Add("Folder scan limit reached. Add a more specific folder to include remaining instances."); return new(entries, warnings, folders); }
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
