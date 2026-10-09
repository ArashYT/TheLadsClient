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
        if (patch.ConfigPatches != null)
        {
            foreach (var cfgPatch in patch.ConfigPatches)
            {
                ApplyConfigPatch(gameDir, cfgPatch);
            }
        }

        // 3. Mod Sync (Add/Remove)
        if (patch.Mods != null)
        {
            await ApplyModSyncAsync(gameDir, patch.Mods, http, status, token);
        }

        RecordAppliedPatch(instanceDirectory, patch.Version);
    }

    public static void ApplyKeybinds(string gameDir, PatchKeybinds? keybinds)
    {
        if (keybinds == null) return;
        var optionsFile = Path.Combine(gameDir, "options.txt");
        var map = File.Exists(optionsFile)
            ? GameOptionsService.ParseOptions(File.ReadAllText(optionsFile))
            : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

        bool changed = false;

        // Apply defaults ONLY for keys that are not already present
        if (keybinds.Defaults != null)
        {
            foreach (var (k, v) in keybinds.Defaults)
            {
                if (!map.ContainsKey(k))
                {
                    map[k] = v;
                    changed = true;
                }
            }
        }

        // Apply forced overrides
        if (keybinds.Force != null)
        {
            foreach (var (k, v) in keybinds.Force)
            {
                if (!map.TryGetValue(k, out var cur) || cur != v)
                {
                    map[k] = v;
                    changed = true;
                }
            }
        }

        if (changed)
        {
            File.WriteAllText(optionsFile, GameOptionsService.SerializeOptions(map));
        }
    }

    public static void ApplyConfigPatch(string gameDir, PatchConfig patch)
    {
        if (string.IsNullOrWhiteSpace(patch.File)) return;
        var filePath = Path.Combine(gameDir, patch.File);
        var dir = Path.GetDirectoryName(filePath);
        if (dir != null) Directory.CreateDirectory(dir);

        var fmt = (patch.Format ?? "json").ToLowerInvariant();

        if (fmt == "json" && patch.Set != null)
        {
            JsonObject root;
            if (File.Exists(filePath))
            {
                try
                {
                    var node = JsonNode.Parse(File.ReadAllText(filePath));
                    root = node as JsonObject ?? new JsonObject();
                }
                catch
                {
                    root = new JsonObject();
                }
            }
            else
            {
                root = new JsonObject();
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
                bool matched = false;
                var pattern = $@"^(\s*{Regex.Escape(k)}\s*[=:]\s*).*$";
                for (int i = 0; i < lines.Count; i++)
                {
                    if (Regex.IsMatch(lines[i], pattern))
                    {
                        var prefix = Regex.Match(lines[i], $@"^(\s*{Regex.Escape(k)}\s*[=:]\s*)").Groups[1].Value;
                        lines[i] = prefix + v;
                        matched = true;
                        break;
                    }
                }
                if (!matched)
                {
                    lines.Add($"{k}={v}");
                }
            }

            File.WriteAllLines(filePath, lines);
        }
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
        !string.IsNullOrWhiteSpace(name) && name == Path.GetFileName(name) && name is not ("." or "..") && name.IndexOfAny(Path.GetInvalidFileNameChars()) < 0;

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
                status?.Invoke($"Downloading new mod {mod.FileName}...");
                var bytes = await http.GetByteArrayAsync(url, token);
                if (!string.IsNullOrWhiteSpace(mod.Sha1))
                {
                    var hash = Convert.ToHexString(SHA1.HashData(bytes)).ToLowerInvariant();
                    if (!hash.Equals(mod.Sha1, StringComparison.OrdinalIgnoreCase))
                    {
                        throw new InvalidDataException($"Hash mismatch downloading {mod.FileName}: expected {mod.Sha1}, got {hash}");
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
