using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text.Json;

namespace TheLadsLauncher.Services;

/// <summary>
/// Minecraft 1.8.9: Ionium (the fast renderer, on by default) cannot show OptiFine resource-pack features yet (CIT, CTM, custom
/// sky and colors, emissive, natural textures, better grass). A launch whose enabled resource packs use them renders through
/// OptiFine instead, as a profile with an OptiFine shader pack does. Packs are only listed and read in place, never extracted:
/// a zip's central directory (cached by path, size and time) or a folder's optifine/mcpatcher file names.
/// </summary>
public static class OptiFinePacks
{
    /// <summary>LauncherSettings.OptiFinePackRenderer values.</summary>
    public const string Auto = "Auto", AlwaysIonium = "Ionium", AlwaysOptiFine = "OptiFine";

    /// <summary>Features Lads Core shows under Ionium: Auto keeps Ionium for packs that use only these. Grows as the ports land.</summary>
    private static readonly string[] IoniumShows = Array.Empty<string>();
    private static readonly string[] Order = { "CIT", "CTM", "custom sky", "custom colors", "emissive", "natural textures", "better grass" };
    private static readonly string[] Roots = { "assets/minecraft/optifine/", "assets/minecraft/mcpatcher/" };
    private const int MaxEntries = 500_000;
    private static readonly ConcurrentDictionary<(string Path, long Size, DateTime Modified), string[]> ZipCache = new();

    /// <summary>The launch notice when this launch should render through OptiFine (Ionium off), else null.</summary>
    public static string? Notice(string game, string minecraftVersion, string? setting)
    {
        if (!GameOptionsService.IsLegacy18(minecraftVersion) || setting == AlwaysIonium) return null;
        var used = EnabledPacks(game).SelectMany(Features).Distinct()
            .Where(f => setting == AlwaysOptiFine || !IoniumShows.Contains(f)).OrderBy(f => Array.IndexOf(Order, f)).ToList();
        return used.Count == 0 ? null
            : $"Using OptiFine: your resource pack uses OptiFine features ({string.Join("/", used)}) that Ionium can't show yet";
    }

    /// <summary>options.txt's resourcePacks (1.8.9: bare names), found in resourcepacks (also its pack folders) or Lunar's 1.8 packs.</summary>
    public static List<string> EnabledPacks(string game)
    {
        var found = new List<string>();
        try
        {
            var options = Path.Combine(game, "options.txt");
            if (!File.Exists(options)
                || !GameOptionsService.ParseOptions(File.ReadAllText(options)).TryGetValue("resourcePacks", out var list)) return found;
            var roots = new[] { Path.Combine(game, "resourcepacks"), Path.Combine(GameOptionsService.LunarRoot(), "profiles", "1.8", "resourcepacks") };
            foreach (var raw in JsonSerializer.Deserialize<List<string>>(list) ?? new())
            {
                var name = raw.StartsWith("file/", StringComparison.Ordinal) ? raw[5..] : raw;
                if (name.Length == 0 || Path.GetFileName(name) != name || name is "." or "..") continue;
                if (roots.Select(r => Find(r, name, 0)).FirstOrDefault(p => p != null) is { } pack) found.Add(pack);
            }
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { }
        return found;
    }

    private static string? Find(string root, string name, int depth)
    {
        var direct = Path.Combine(root, name);
        if (File.Exists(direct) || Directory.Exists(direct)) return direct;
        if (depth >= 4 || !Directory.Exists(root)) return null;
        // Lads Core also lists packs inside plain folders (no pack.mcmeta) of resourcepacks.
        try
        {
            foreach (var folder in Directory.EnumerateDirectories(root))
                if (!File.Exists(Path.Combine(folder, "pack.mcmeta")) && Find(folder, name, depth + 1) is { } nested) return nested;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        return null;
    }

    /// <summary>The OptiFine features one pack (zip or folder) uses; an unreadable pack uses none.</summary>
    public static string[] Features(string pack)
    {
        try
        {
            if (Directory.Exists(pack)) return Scan(pack); // a folder's root time misses edits deeper in: listed every time
            var file = new FileInfo(pack);
            return ZipCache.GetOrAdd((file.FullName, file.Length, file.LastWriteTimeUtc), _ => Scan(pack));
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException) { return Array.Empty<string>(); }
    }

    private static string[] Scan(string pack) =>
        Entries(pack).Take(MaxEntries).Select(Feature).OfType<string>().Distinct().ToArray();

    private static IEnumerable<string> Entries(string pack)
    {
        if (Directory.Exists(pack))
        {
            var options = new EnumerationOptions { RecurseSubdirectories = true, IgnoreInaccessible = true, AttributesToSkip = FileAttributes.ReparsePoint };
            foreach (var root in Roots)
            {
                var folder = Path.Combine(pack, root);
                if (!Directory.Exists(folder)) continue;
                foreach (var file in Directory.EnumerateFiles(folder, "*", options))
                    yield return root + Path.GetRelativePath(folder, file).Replace('\\', '/');
            }
            yield break;
        }
        using var zip = ZipFile.OpenRead(pack);
        foreach (var entry in zip.Entries) yield return entry.FullName;
    }

    /// <summary>The OptiFine feature a pack file belongs to, or null.</summary>
    public static string? Feature(string entry)
    {
        var path = entry.Replace('\\', '/').ToLowerInvariant();
        var root = Roots.FirstOrDefault(r => path.StartsWith(r, StringComparison.Ordinal));
        if (root == null) return null;
        var rest = path[root.Length..];
        bool rules = rest.EndsWith(".properties", StringComparison.Ordinal);
        return rest switch
        {
            "emissive.properties" => "emissive",
            "natural.properties" => "natural textures",
            "bettergrass.properties" => "better grass",
            "color.properties" => "custom colors",
            _ when rules && rest.StartsWith("cit/", StringComparison.Ordinal) => "CIT",
            _ when rules && rest.StartsWith("ctm/", StringComparison.Ordinal) => "CTM",
            _ when rules && rest.StartsWith("sky/", StringComparison.Ordinal) => "custom sky",
            _ when rest.StartsWith("colormap/", StringComparison.Ordinal) && !rest.EndsWith('/') => "custom colors",
            _ => null
        };
    }
}
