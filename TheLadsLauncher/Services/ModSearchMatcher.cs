using System;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;

namespace TheLadsLauncher.Services;

/// <summary>
/// Connects browsed mods and resource packs (from Modrinth or CurseForge) to the installed mods inventory,
/// profile mod jars and shipped pack manifests.
/// </summary>
public static class ModSearchMatcher
{
    /// <summary>
    /// Normalizes a name or slug by removing non-alphanumeric characters and converting to lowercase.
    /// E.g. "Fabric API" -> "fabricapi", "Reese's Sodium Options" -> "reesessodiumoptions", "3D Skin Layers" -> "3dskinlayers".
    /// </summary>
    public static string NormalizeKey(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return "";
        return Regex.Replace(value.ToLowerInvariant(), @"[^a-z0-9]", "");
    }

    /// <summary>
    /// Checks whether a browsed item matches an entry in the active profile's mod inventory.
    /// </summary>
    public static bool MatchesInventoryEntry(ModSearchItem item, ModInventoryEntry entry)
    {
        if (item == null || entry == null) return false;
        if (entry.Status is ModEntryStatus.Unavailable or ModEntryStatus.NotDownloaded or ModEntryStatus.Invalid)
            return false;

        // 1. Direct ProjectId match (e.g. Modrinth project ID)
        if (!string.IsNullOrEmpty(entry.ProjectId) && string.Equals(entry.ProjectId, item.Id, StringComparison.OrdinalIgnoreCase))
            return true;

        // 2. Direct ModId match with ProjectSlug
        if (!string.IsNullOrEmpty(item.ProjectSlug) && string.Equals(entry.Id, item.ProjectSlug, StringComparison.OrdinalIgnoreCase))
            return true;

        // 3. ProjectUrl slug match
        if (!string.IsNullOrEmpty(item.ProjectSlug) && !string.IsNullOrEmpty(entry.ProjectUrl) &&
            (entry.ProjectUrl.EndsWith("/" + item.ProjectSlug, StringComparison.OrdinalIgnoreCase) ||
             entry.ProjectUrl.Contains("/" + item.ProjectSlug + "/", StringComparison.OrdinalIgnoreCase)))
            return true;

        string normItemSlug = NormalizeKey(item.ProjectSlug);
        string normItemName = NormalizeKey(item.Name);
        string normEntryId = NormalizeKey(entry.Id);
        string normEntryName = NormalizeKey(entry.DisplayName);
        string normUpstream = NormalizeKey(entry.UpstreamName);

        // 4. Normalized exact matches
        if (!string.IsNullOrEmpty(normItemSlug) && normEntryId == normItemSlug)
            return true;
        if (!string.IsNullOrEmpty(normItemName) && normEntryName == normItemName)
            return true;
        if (!string.IsNullOrEmpty(normItemName) && normUpstream == normItemName)
            return true;
        if (!string.IsNullOrEmpty(normItemSlug) && normEntryName == normItemSlug)
            return true;
        if (!string.IsNullOrEmpty(normItemName) && normEntryId == normItemName)
            return true;

        // 5. Provides match
        if (entry.Provides != null && entry.Provides.Any(p =>
            string.Equals(p, item.ProjectSlug, StringComparison.OrdinalIgnoreCase) ||
            NormalizeKey(p) == normItemSlug ||
            NormalizeKey(p) == normItemName))
            return true;

        // 6. File name on disk match
        if (!string.IsNullOrEmpty(entry.FileName) && MatchesJarFile(item, entry.FileName))
            return true;

        return false;
    }

    /// <summary>
    /// Checks whether a browsed item matches an entry in a shipped pack manifest (client-mods.json).
    /// </summary>
    public static bool MatchesManifestEntry(ModSearchItem item, ClientModInstaller.Entry mod)
    {
        if (item == null || mod == null) return false;

        if (string.Equals(mod.ProjectId, item.Id, StringComparison.OrdinalIgnoreCase))
            return true;
        if (!string.IsNullOrEmpty(item.ProjectSlug) &&
            (string.Equals(mod.ProjectSlug, item.ProjectSlug, StringComparison.OrdinalIgnoreCase) ||
             string.Equals(mod.ModId, item.ProjectSlug, StringComparison.OrdinalIgnoreCase)))
            return true;

        string normItemSlug = NormalizeKey(item.ProjectSlug);
        string normItemName = NormalizeKey(item.Name);
        if (NormalizeKey(mod.Name) == normItemName ||
            NormalizeKey(mod.ProjectSlug) == normItemSlug ||
            NormalizeKey(mod.ModId) == normItemSlug ||
            NormalizeKey(mod.Name) == normItemSlug ||
            NormalizeKey(mod.ModId) == normItemName)
            return true;

        return false;
    }

    /// <summary>
    /// Checks whether a browsed item matches a jar file on disk.
    /// </summary>
    public static bool MatchesJarFile(ModSearchItem item, string filePath)
    {
        if (item == null || string.IsNullOrWhiteSpace(filePath)) return false;
        string nameWithoutExt = Path.GetFileNameWithoutExtension(filePath).ToLowerInvariant();
        if (nameWithoutExt.EndsWith(".disabled"))
            nameWithoutExt = nameWithoutExt.Substring(0, nameWithoutExt.Length - ".disabled".Length);
        if (nameWithoutExt.Length == 0) return false;

        string normItemSlug = NormalizeKey(item.ProjectSlug);
        string normItemName = NormalizeKey(item.Name);
        string normFile = NormalizeKey(nameWithoutExt);

        if (!string.IsNullOrEmpty(normItemSlug) && normFile == normItemSlug)
            return true;
        if (!string.IsNullOrEmpty(normItemName) && normFile == normItemName)
            return true;

        string cleanSlug = (item.ProjectSlug ?? "").ToLowerInvariant().Replace('_', '-');
        string cleanName = (item.Name ?? "").ToLowerInvariant().Replace('_', '-');

        if (MatchesNamePrefix(nameWithoutExt, cleanSlug))
            return true;
        if (MatchesNamePrefix(nameWithoutExt, cleanName))
            return true;

        return false;
    }

    private static bool MatchesNamePrefix(string fileName, string prefix)
    {
        if (string.IsNullOrEmpty(prefix)) return false;
        if (fileName == prefix) return true;

        char[] seps = { '-', '_', '+', ' ' };
        foreach (char sep in seps)
        {
            string withSep = prefix + sep;
            if (fileName.StartsWith(withSep))
            {
                string remainder = fileName.Substring(withSep.Length);
                string nextToken = remainder.Split(seps)[0];
                if (IsLoaderOrVersion(nextToken))
                    return true;
            }
        }
        return false;
    }

    private static bool IsLoaderOrVersion(string token)
    {
        if (string.IsNullOrEmpty(token)) return true;
        if (token is "fabric" or "forge" or "neoforge" or "quilt" or "client") return true;
        if (char.IsDigit(token[0])) return true;
        if (token.StartsWith("v") && token.Length > 1 && char.IsDigit(token[1])) return true;
        if (token.StartsWith("mc") && token.Length > 2 && (char.IsDigit(token[2]) || token[2] == '-')) return true;
        if (token.StartsWith("(") || token.StartsWith("[")) return true;
        return false;
    }

    /// <summary>
    /// Checks whether a browsed resource pack matches any pack in the shared resourcepacks folder.
    /// </summary>
    public static bool MatchesResourcePack(ModSearchItem item, string targetFolder)
    {
        if (item == null || !Directory.Exists(targetFolder)) return false;

        string normName = NormalizeKey(item.Name);
        string normSlug = NormalizeKey(item.ProjectSlug);

        foreach (var entry in Directory.EnumerateFileSystemEntries(targetFolder))
        {
            string cleanFile = NormalizeKey(Path.GetFileNameWithoutExtension(entry));
            if (cleanFile.Length == 0) continue;

            if (cleanFile == normName || (!string.IsNullOrEmpty(normSlug) && cleanFile == normSlug))
                return true;

            if (normName.Length >= 4 && (cleanFile.StartsWith(normName) || cleanFile.Contains(normName)))
                return true;

            if (normSlug.Length >= 4 && (cleanFile.StartsWith(normSlug) || cleanFile.Contains(normSlug)))
                return true;
        }

        return false;
    }
}
