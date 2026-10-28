using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public sealed class ModpackPatch
{
    public string PackId { get; set; } = "";
    public string Version { get; set; } = "";
    public string? Description { get; set; }
    public PatchMods? Mods { get; set; }
    public PatchKeybinds? Keybinds { get; set; }
    public List<PatchConfig>? ConfigPatches { get; set; }
}

public sealed class PatchMods
{
    public List<PatchModFile>? Add { get; set; }
    public List<string>? Remove { get; set; }
}

public sealed class PatchModFile
{
    public string FileName { get; set; } = "";
    public string? Url { get; set; }
    public string? Sha1 { get; set; }
    public string? Sha256 { get; set; }
    public string? Sha512 { get; set; }
}

public sealed class PatchKeybinds
{
    /// <summary>
    /// Defaults applied ONLY if the keybind is NOT already present in player's options.txt.
    /// Perfect for assigning developer-selected default keys to new mods without wiping existing player keys.
    /// </summary>
    public Dictionary<string, string>? Defaults { get; set; }

    /// <summary>
    /// Forcefully updates the keybind even if already customized by the player (e.g. resolving critical conflicts).
    /// </summary>
    public Dictionary<string, string>? Force { get; set; }
}

public sealed class PatchConfig
{
    public string File { get; set; } = ""; // e.g. "config/modernfix.json"
    public string Format { get; set; } = "json"; // "json", "toml", "cfg", "properties"
    public Dictionary<string, JsonElement>? Set { get; set; }
    public Dictionary<string, string>? KeyValues { get; set; }
}

/// <summary>
/// Executes surgical delta updates on a modpack instance without wiping player settings.
/// </summary>
public static class ModpackPatchEngine
{
    private const string AppliedPatchesFile = ".lads-applied-patches.json";

    private static readonly HashSet<string> AllowedModDownloadHosts = new(StringComparer.OrdinalIgnoreCase)
    {
        "cdn.modrinth.com",
        "edge.forgecdn.net",
        "mediafilez.forgecdn.net",
        "github.com",
        "objects.githubusercontent.com",
        "raw.githubusercontent.com",
        "bot.arashyt.ca",
        "cdn.example.invalid"
    };

    public static List<string> GetAppliedPatches(string instanceDirectory)
    {
        var file = Path.Combine(instanceDirectory, AppliedPatchesFile);
        if (!File.Exists(file)) return new List<string>();
        try
        {
            return JsonSerializer.Deserialize<List<string>>(File.ReadAllText(file)) ?? new List<string>();
        }
        catch
        {
            return new List<string>();
        }
    }

    public static void RecordAppliedPatch(string instanceDirectory, string version)
    {
        var file = Path.Combine(instanceDirectory, AppliedPatchesFile);
        var list = GetAppliedPatches(instanceDirectory);
        if (!list.Contains(version, StringComparer.OrdinalIgnoreCase))
        {
            list.Add(version);
            File.WriteAllText(file, JsonSerializer.Serialize(list, new JsonSerializerOptions { WriteIndented = true }));
        }
    }

    public static async Task ApplyPatchAsync(string instanceDirectory, ModpackPatch patch, HttpClient? http, Action<string>? status, CancellationToken token = default)
    {
        var gameDir = Path.Combine(instanceDirectory, "minecraft");
        Directory.CreateDirectory(gameDir);

        status?.Invoke($"Applying update patch {patch.Version} ({patch.PackId})...");

        // 1. Surgical Keybinds Update
        ApplyKeybinds(gameDir, patch.Keybinds);

        // 2. Surgical Config Patches
        bool complete = true;
        if (patch.ConfigPatches != null)
        {
            foreach (var cfgPatch in patch.ConfigPatches)
            {
                if (ApplyConfigPatch(gameDir, cfgPatch)) continue;
                complete = false;
                status?.Invoke($"Could not patch {cfgPatch.File}: it is not valid JSON, so it was left as it is. The update is tried again at the next launch.");
            }
        }

        // 3. Mod Sync (Add/Remove)
        if (patch.Mods != null)
        {
            await ApplyModSyncAsync(gameDir, patch.Mods, http, status, token);
        }

        // A patch with a skipped config is not recorded: every step is repeatable, so the next launch runs it again.
        if (complete) RecordAppliedPatch(instanceDirectory, patch.Version);
    }

    public static void ApplyKeybinds(string gameDir, PatchKeybinds? keybinds)
    {
        if (keybinds == null) return;
        var optionsFile = Path.Combine(gameDir, "options.txt");
        // Edited line by line (the player's other lines, their order and the line endings stay) and written atomically (a launcher
        // killed mid-write leaves the old file, never a cut-off one): the options map is lossy and File.WriteAllText is not atomic.
        var options = GameOptionsFile.Parse(File.Exists(optionsFile) ? File.ReadAllText(optionsFile) : null);

        bool changed = false;

        // Apply defaults ONLY for keys that are not already present
        if (keybinds.Defaults != null)
        {
            foreach (var (k, v) in keybinds.Defaults)
            {
                if (options.Get(k) == null)
                {
                    options.Set(k, v);
                    changed = true;
                }
            }
        }

        // Apply forced overrides
        if (keybinds.Force != null)
        {
            foreach (var (k, v) in keybinds.Force)
            {
                if (options.Get(k) != v)
                {
                    options.Set(k, v);
                    changed = true;
                }
            }
        }

        if (changed)
        {
            GameOptionsService.WriteAtomically(optionsFile, options.ToString());
        }
    }

    /// <summary>Applies one config patch. False when the file is JSON that does not parse (comments, a trailing comma, a half-written
    /// file) or is not an object: it is left exactly as it is, because writing back only the patched keys would wipe the rest. The
    /// caller reports that and does not record the patch as applied.</summary>
    public static bool ApplyConfigPatch(string gameDir, PatchConfig patch)
    {
        if (string.IsNullOrWhiteSpace(patch.File)) return true;
        string filePath;
        try
        {
            filePath = Mrpack.SafePath(gameDir, patch.File);
        }
        catch (Exception ex) when (ex is InvalidDataException or ArgumentException)
        {
            return true;
        }
        var dir = Path.GetDirectoryName(filePath);
        if (dir != null) Directory.CreateDirectory(dir);

        var fmt = (patch.Format ?? "json").ToLowerInvariant();

        if (fmt == "json" && patch.Set != null)
        {
            var root = new JsonObject();
            var text = File.Exists(filePath) ? File.ReadAllText(filePath) : "";
            if (!string.IsNullOrWhiteSpace(text))
            {
                try { root = JsonNode.Parse(text) as JsonObject ?? throw new JsonException("The file is not a JSON object."); }
                catch (JsonException) { return false; }
            }

            foreach (var (path, element) in patch.Set)
            {
                SetNestedJsonProperty(root, path, JsonNode.Parse(element.GetRawText()));
            }

            File.WriteAllText(filePath, root.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
        }
        else if (patch.KeyValues != null && (fmt == "toml" || fmt == "cfg" || fmt == "properties"))
        {
            var lines = File.Exists(filePath) ? File.ReadAllLines(filePath).ToList() : new List<string>();

            foreach (var (k, v) in patch.KeyValues)
            {
                var (index, insertAt, name, newTable) = FindKey(lines, k, fmt == "toml");
                if (index >= 0)
                    lines[index] = Regex.Match(lines[index], $@"^\s*{Regex.Escape(name)}\s*[=:]\s*").Value + v;
                else if (newTable != null)
                    lines.AddRange(new[] { newTable, $"{name} = {v}" });
                else if (name != k) // inside its table, indented like the line above
                    lines.Insert(insertAt, Regex.Match(insertAt > 0 ? lines[insertAt - 1] : "", @"^\s*").Value + $"{name} = {v}");
                else
                    lines.Add($"{k}={v}");
            }

            File.WriteAllLines(filePath, lines);
        }
        return true;
    }

    /// <summary>Where <paramref name="key"/> is in a key=value file: its line (-1 if missing) and where a missing one goes. A TOML key
    /// written as "table.path.key" is only looked for inside [table.path] (up to the next [header]) and goes at the end of that table,
    /// or into a new [table.path] at the end of the file; any other key matches the first line anywhere and goes at the end.</summary>
    private static (int Index, int InsertAt, string Name, string? NewTable) FindKey(List<string> lines, string key, bool toml)
    {
        int from = 0, to = lines.Count;
        var name = key;
        if (toml && key.LastIndexOf('.') is var dot and > 0)
        {
            var header = "[" + key[..dot] + "]";
            name = key[(dot + 1)..];
            from = lines.FindIndex(l => l.Trim() == header) + 1;
            if (from == 0) return (-1, lines.Count, name, header);
            to = lines.FindIndex(from, l => l.TrimStart().StartsWith('[')) is var next and >= 0 ? next : lines.Count;
        }
        var pattern = new Regex($@"^\s*{Regex.Escape(name)}\s*[=:]");
        for (int i = from; i < to; i++)
            if (pattern.IsMatch(lines[i])) return (i, i, name, null);
        while (to > from && string.IsNullOrWhiteSpace(lines[to - 1])) to--;
        return (-1, to, name, null);
    }

    /// <summary>Whether an instance that never recorded its patches already has <paramref name="patch"/>: its config values (keys in
    /// <paramref name="setAgainLater"/>, "file|key", are left to the later patch), every removed jar gone, at least one of its added
    /// jars there, and for a keybind-only patch its keybind defaults/forces (the pack may drop an added mod later: Crawl on Demand came in 1.0.1 and left the
    /// dev instance afterwards; "all added jars" would download it again).</summary>
    public static bool IsReflected(string gameDir, ModpackPatch patch, ISet<string> setAgainLater)
    {
        foreach (var cfg in patch.ConfigPatches ?? new List<PatchConfig>())
        {
            string filePath;
            try
            {
                filePath = Mrpack.SafePath(gameDir, cfg.File);
            }
            catch (Exception ex) when (ex is InvalidDataException or ArgumentException)
            {
                return false;
            }
            if (!File.Exists(filePath)) return false;
            if ((cfg.Format ?? "json").ToLowerInvariant() == "json" && cfg.Set != null)
            {
                JsonNode? root;
                try { root = JsonNode.Parse(File.ReadAllText(filePath)); } catch (JsonException) { return false; }
                foreach (var (path, element) in cfg.Set)
                {
                    if (setAgainLater.Contains(cfg.File + "|" + path)) continue;
                    var node = root;
                    foreach (var part in path.Split('.')) node = (node as JsonObject)?[part];
                    if (!JsonNode.DeepEquals(node, JsonNode.Parse(element.GetRawText()))) return false;
                }
            }
            else if (cfg.KeyValues != null)
            {
                var lines = File.ReadAllLines(filePath).ToList();
                foreach (var (k, v) in cfg.KeyValues)
                {
                    if (setAgainLater.Contains(cfg.File + "|" + k)) continue;
                    var (index, _, name, _) = FindKey(lines, k, (cfg.Format ?? "").ToLowerInvariant() == "toml");
                    if (index < 0 || Regex.Replace(lines[index], $@"^\s*{Regex.Escape(name)}\s*[=:]\s*", "").Trim() != v.Trim()) return false;
                }
            }
        }

        // Keybinds only decide a keybind-only patch: Minecraft drops the keys of mods that left the pack from options.txt
        // (1.0.1's Crawl on Demand keys are gone from the dev instance although its other changes are there).
        bool hasOtherChanges = patch.ConfigPatches?.Count > 0 || patch.Mods?.Add?.Count > 0 || patch.Mods?.Remove?.Count > 0;
        if (!hasOtherChanges && patch.Keybinds is { } keys && (keys.Defaults?.Count > 0 || keys.Force?.Count > 0))
        {
            var optionsFile = Path.Combine(gameDir, "options.txt");
            var map = File.Exists(optionsFile) ? GameOptionsService.ParseOptions(File.ReadAllText(optionsFile)) : new Dictionary<string, string>();
            if (keys.Defaults?.Keys.Any(k => !map.ContainsKey(k)) == true) return false;
            if (keys.Force?.Any(f => !map.TryGetValue(f.Key, out var cur) || cur != f.Value) == true) return false;
        }

        var modsDir = Path.Combine(gameDir, "mods");
        bool Has(string name) => File.Exists(Path.Combine(modsDir, name)) || File.Exists(Path.Combine(modsDir, name + ".disabled"));
        if (patch.Mods?.Remove?.Any(n => IsPlainFileName(n) && Has(n)) == true) return false;
        var added = patch.Mods?.Add?.Where(m => IsPlainFileName(m.FileName)).ToList();
        return added is not { Count: > 0 } || added.Any(m => Has(m.FileName));
    }

    /// <summary>For an instance with no <c>.lads-applied-patches.json</c> (the dev instance, or one set up before patches): records the
    /// oldest <paramref name="pending"/> patches it already reflects (<see cref="IsReflected"/>) as applied, stopping at the first it
    /// lacks, and returns the rest. With a record nothing is adopted: the record decides.</summary>
    public static List<ModpackPatch> AdoptReflectedPatches(string instanceDirectory, List<ModpackPatch> pending)
    {
        if (File.Exists(Path.Combine(instanceDirectory, AppliedPatchesFile))) return pending;
        var gameDir = Path.Combine(instanceDirectory, "minecraft");
        int adopted = 0;
        for (; adopted < pending.Count; adopted++)
        {
            var later = pending.Skip(adopted + 1).SelectMany(p => p.ConfigPatches ?? new List<PatchConfig>())
                .SelectMany(c => (c.KeyValues?.Keys ?? Enumerable.Empty<string>()).Concat(c.Set?.Keys ?? Enumerable.Empty<string>()).Select(k => c.File + "|" + k))
                .ToHashSet();
            if (!IsReflected(gameDir, pending[adopted], later)) break;
            RecordAppliedPatch(instanceDirectory, pending[adopted].Version);
        }
        return pending.Skip(adopted).ToList();
    }

    private static void SetNestedJsonProperty(JsonObject root, string path, JsonNode? value)
    {
        var parts = path.Split('.');
        JsonObject current = root;
        for (int i = 0; i < parts.Length - 1; i++)
        {
            var part = parts[i];
            if (current[part] is JsonObject next)
            {
                current = next;
            }
            else
            {
                var newObj = new JsonObject();
                current[part] = newObj;
                current = newObj;
            }
        }
        current[parts[^1]] = value;
    }

    /// <summary>Patches in <paramref name="patchesDirectory"/> (update-*.json) for this pack that the instance has not applied yet,
    /// oldest version first. Unreadable files are skipped.</summary>
    public static List<ModpackPatch> PendingPatches(string patchesDirectory, string instanceDirectory, string packId)
    {
        var pending = new List<ModpackPatch>();
        if (!Directory.Exists(patchesDirectory)) return pending;
        var applied = GetAppliedPatches(instanceDirectory);
        var options = new JsonSerializerOptions { PropertyNameCaseInsensitive = true };
        foreach (var file in Directory.EnumerateFiles(patchesDirectory, "update-*.json"))
        {
            try
            {
                var patch = JsonSerializer.Deserialize<ModpackPatch>(File.ReadAllText(file), options);
                if (patch != null && patch.PackId == packId && patch.Version.Length > 0 && !applied.Contains(patch.Version, StringComparer.OrdinalIgnoreCase))
                    pending.Add(patch);
            }
            catch (Exception e) when (e is IOException or JsonException or UnauthorizedAccessException) { }
        }
        return pending.OrderBy(p => Version.TryParse(p.Version, out var v) ? v : new Version(0, 0)).ThenBy(p => p.Version, StringComparer.Ordinal).ToList();
    }

    /// <summary>A bare file name: patches come from outside, so no path segments may reach the mods folder.</summary>
    private static bool IsPlainFileName(string name) =>
        SafeFileOps.IsPlainFileName(name) && name == Path.GetFileName(name) && name is not ("." or "..") && name.IndexOfAny(Path.GetInvalidFileNameChars()) < 0;

    private static async Task ApplyModSyncAsync(string gameDir, PatchMods mods, HttpClient? http, Action<string>? status, CancellationToken token)
    {
        var modsDir = Path.Combine(gameDir, "mods");
        Directory.CreateDirectory(modsDir);

        if (mods.Remove != null)
        {
            foreach (var name in mods.Remove)
            {
                if (!IsPlainFileName(name)) continue;
                // A mod the player switched off is renamed to <name>.disabled; retiring it removes that copy too.
                foreach (var target in new[] { Path.Combine(modsDir, name), Path.Combine(modsDir, name + ".disabled") })
                {
                    if (!File.Exists(target)) continue;
                    status?.Invoke($"Removing retired mod {name}...");
                    try { File.Delete(target); } catch (IOException) { } catch (UnauthorizedAccessException) { }
                }
            }
        }

        if (mods.Add != null && http != null)
        {
            foreach (var mod in mods.Add)
            {
                if (!IsPlainFileName(mod.FileName)) continue;
                var target = Path.Combine(modsDir, mod.FileName);
                if (File.Exists(target) || File.Exists(target + ".disabled") || !Uri.TryCreate(mod.Url, UriKind.Absolute, out var url) || url.Scheme != Uri.UriSchemeHttps) continue;
                if (!AllowedModDownloadHosts.Contains(url.Host))
                {
                    throw new InvalidDataException($"Refusing to download mod {mod.FileName} from untrusted URL: {mod.Url}");
                }
                if (string.IsNullOrWhiteSpace(mod.Sha1) && string.IsNullOrWhiteSpace(mod.Sha256) && string.IsNullOrWhiteSpace(mod.Sha512))
                {
                    throw new InvalidDataException($"Refusing to download mod {mod.FileName} without a cryptographic checksum (sha1/sha256/sha512).");
                }
                status?.Invoke($"Downloading new mod {mod.FileName}...");
                var bytes = await http.GetByteArrayAsync(url, token);
                if (!string.IsNullOrWhiteSpace(mod.Sha1))
                {
                    var hash = Convert.ToHexString(SHA1.HashData(bytes)).ToLowerInvariant();
                    if (!hash.Equals(mod.Sha1, StringComparison.OrdinalIgnoreCase))
                    {
                        throw new InvalidDataException($"SHA-1 mismatch downloading {mod.FileName}: expected {mod.Sha1}, got {hash}");
                    }
                }
                if (!string.IsNullOrWhiteSpace(mod.Sha256))
                {
                    var hash = Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
                    if (!hash.Equals(mod.Sha256, StringComparison.OrdinalIgnoreCase))
                    {
                        throw new InvalidDataException($"SHA-256 mismatch downloading {mod.FileName}: expected {mod.Sha256}, got {hash}");
                    }
                }
                if (!string.IsNullOrWhiteSpace(mod.Sha512))
                {
                    var hash = Convert.ToHexString(SHA512.HashData(bytes)).ToLowerInvariant();
                    if (!hash.Equals(mod.Sha512, StringComparison.OrdinalIgnoreCase))
                    {
                        throw new InvalidDataException($"SHA-512 mismatch downloading {mod.FileName}: expected {mod.Sha512}, got {hash}");
                    }
                }
                // Written beside the target and moved in: a cancelled download never leaves a half jar for Forge to choke on.
                var partial = target + ".part";
                await File.WriteAllBytesAsync(partial, bytes, token);
                File.Move(partial, target, overwrite: true);
            }
        }
    }
}
