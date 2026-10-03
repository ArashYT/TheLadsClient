using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

/// <summary>
/// Where packs go for one game: a Lads version profile (worlds, resource packs and shader packs are shared by every profile)
/// or a modpack instance (&lt;launcher data&gt;\instances\&lt;id&gt;\minecraft). <see cref="Id"/> is the profile or instance id. <see cref="OptionsFiles"/> are the options.txt
/// files whose resourcePacks list names packs of <see cref="ResourcePacks"/>.
/// </summary>
public sealed record ContentTarget(string Id, string Label, string MinecraftVersion, string Loader, string ResourcePacks, string ShaderPacks, string Saves,
    IReadOnlyList<string> OptionsFiles)
{
    public override string ToString() => Label;
}

public static class PackContentService
{
    /// <summary>Data packs exist from Minecraft 1.13 on: never for Lads 1.8.9.</summary>
    public static bool SupportsDataPacks(string minecraftVersion) => !minecraftVersion.StartsWith("1.") ||
        (Version.TryParse(minecraftVersion, out var v) && (v.Major > 1 || v.Minor >= 13));

    /// <summary>Every Lads profile, then every modpack instance the Modpacks tab lists (<see cref="Modpacks.List"/>) that names its Minecraft version.</summary>
    public static List<ContentTarget> LoadTargets(IEnumerable<LauncherProfile> profiles, IPathService paths, SharedContentService shared)
    {
        var list = profiles.ToList();
        var ladsOptions = list.Select(p => Path.Combine(paths.GetProfileDirectory(p), "options.txt")).Append(paths.SharedOptionsFile)
            .Distinct(StringComparer.OrdinalIgnoreCase).ToList();
        var targets = list.Select(p => new ContentTarget(p.Id, $"Lads · {p.Name} ({p.MinecraftVersion})", p.MinecraftVersion, ContentCatalog.ModLoader(p.MinecraftVersion),
            shared.ResourcePacksDirectory, shared.ShaderPacksDirectory, shared.SavesDirectory, ladsOptions)).ToList();
        foreach (var instance in Modpacks.List(paths.BaseDirectory).Where(i => i.McVersion.Length > 0))
        {
            var game = instance.GameDirectory;
            targets.Add(new ContentTarget(instance.Id, $"Modpack · {(instance.Name.Length > 0 ? instance.Name : instance.Id)} ({instance.McVersion})", instance.McVersion,
                instance.Loader.Length > 0 ? instance.Loader : "vanilla", Path.Combine(game, "resourcepacks"), Path.Combine(game, "shaderpacks"),
                Path.Combine(game, "saves"), new[] { Path.Combine(game, "options.txt") }));
        }
        return targets;
    }

    /// <summary>The world folders (with a level.dat) of a saves folder, newest played first.</summary>
    public static List<string> Worlds(string saves) => !Directory.Exists(saves) ? new List<string>()
        : new DirectoryInfo(saves).GetDirectories().Where(d => File.Exists(Path.Combine(d.FullName, "level.dat")))
            .OrderByDescending(d => File.GetLastWriteTimeUtc(Path.Combine(d.FullName, "level.dat"))).Select(d => d.Name).ToList();

    /// <summary>The packs in a folder (zips and folders), without the launcher's staging folders.</summary>
    public static List<string> Installed(string folder) => !Directory.Exists(folder) ? new List<string>()
        : Directory.GetFileSystemEntries(folder).Where(p => !Path.GetFileName(p).StartsWith(".lads-incoming-", StringComparison.Ordinal))
            .Where(p => Directory.Exists(p) || p.EndsWith(".zip", StringComparison.OrdinalIgnoreCase))
            .OrderBy(p => Path.GetFileName(p), StringComparer.OrdinalIgnoreCase).ToList();

    private static readonly JsonSerializerOptions OptionsJson = new() { Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping };

    /// <summary>
    /// Renames a pack in options.txt's resourcePacks and incompatibleResourcePacks lists ("file/Old.zip" on 1.13+, "Old.zip" on
    /// 1.8.9) so it stays enabled. Every other line is kept as it is. Returns whether the file changed.
    /// </summary>
    public static bool RenamePackInOptions(string optionsFile, string oldName, string newName)
    {
        if (!File.Exists(optionsFile)) return false;
        var lines = File.ReadAllText(optionsFile).Split('\n');
        bool changed = false;
        for (int i = 0; i < lines.Length; i++)
        {
            var line = lines[i].TrimEnd('\r');
            int colon = line.IndexOf(':');
            if (colon < 0 || line[..colon] is not ("resourcePacks" or "incompatibleResourcePacks")) continue;
            List<string>? packs;
            try { packs = JsonSerializer.Deserialize<List<string>>(line[(colon + 1)..]); }
            catch (JsonException) { continue; }
            if (packs == null) continue;
            var renamed = packs.Select(p => p == oldName ? newName : p == "file/" + oldName ? "file/" + newName : p).ToList();
            if (renamed.SequenceEqual(packs)) continue;
            lines[i] = line[..(colon + 1)] + JsonSerializer.Serialize(renamed, OptionsJson) + (lines[i].EndsWith('\r') ? "\r" : "");
            changed = true;
        }
        if (!changed) return false;
        var temp = optionsFile + ".lads-tmp";
        File.WriteAllText(temp, string.Join('\n', lines));
        File.Move(temp, optionsFile, overwrite: true);
        return true;
    }

    /// <summary>
    /// Updates the .zip packs of <paramref name="target"/>'s resource packs folder to their newest release for its game version:
    /// Modrinth by SHA-1 first, then CurseForge fingerprints when a key is set. Each new file is downloaded to a staging folder,
    /// its SHA-1 checked, then swapped in (a same-named file is replaced in one rename; otherwise the old file goes to the
    /// Recycle Bin) and options.txt keeps the pack enabled under its new name. Returns one line per pack.
    /// </summary>
    /// <param name="recycle">Removes the old file; the launcher moves it to the Recycle Bin.</param>
    public static async Task<(int Updated, int Failed, List<string> Lines)> UpdateResourcePacksAsync(ContentCatalog catalog, ContentTarget target,
        SharedContentService staging, Action<string> recycle, IProgress<string>? progress = null, CancellationToken cancellationToken = default)
    {
        var lines = new List<string>();
        var packs = Installed(target.ResourcePacks).Where(File.Exists).ToList();
        if (packs.Count == 0) return (0, 0, new List<string> { $"There are no .zip resource packs in '{target.ResourcePacks}'." });
        progress?.Report($"Checking {packs.Count} resource packs for Minecraft {target.MinecraftVersion}...");
        var hashes = new Dictionary<string, string>();
        foreach (var pack in packs) hashes[pack] = await Task.Run(() => ContentCatalog.Sha1(pack), cancellationToken);

        var updates = new Dictionary<string, ContentFile>();
        var current = new HashSet<string>();
        var found = await catalog.ModrinthUpdatesAsync(hashes.Values.Distinct().ToList(), target.MinecraftVersion, "minecraft", cancellationToken);
        foreach (var pack in packs)
            if (found.TryGetValue(hashes[pack], out var file))
            {
                if (string.Equals(file.Sha1, hashes[pack], StringComparison.OrdinalIgnoreCase)) current.Add(pack);
                else updates[pack] = file;
            }

        var unknown = packs.Where(p => !updates.ContainsKey(p) && !current.Contains(p)).ToList();
        if (unknown.Count > 0 && catalog.HasCurseForgeKey)
        {
            try
            {
                // ponytail: whole pack in memory for the fingerprint (only packs Modrinth does not know); stream it if huge packs matter.
                var fingerprints = new Dictionary<string, uint>();
                foreach (var pack in unknown) fingerprints[pack] = ContentCatalog.CurseForgeFingerprint(await File.ReadAllBytesAsync(pack, cancellationToken));
                var matches = await catalog.CurseForgeMatchesAsync(fingerprints.Values.Distinct().ToList(), cancellationToken);
                foreach (var pack in unknown)
                {
                    if (!matches.TryGetValue(fingerprints[pack], out var installed)) continue;
                    var latest = await catalog.LatestFileAsync(new ModSearchItem { Id = installed.ProjectId, Provider = "CurseForge" }, ContentKind.ResourcePack,
                        target.MinecraftVersion, "", cancellationToken);
                    if (latest == null) lines.Add($"{Path.GetFileName(pack)}: CurseForge has no release for Minecraft {target.MinecraftVersion}.");
                    else if (latest.FileId == installed.FileId) current.Add(pack);
                    else updates[pack] = latest;
                }
            }
            catch (ContentSourceException e)
            {
                lines.Add($"CurseForge was not checked: {e.Message}"); // the Modrinth updates still go ahead
            }
        }

        int updated = 0, failed = 0;
        foreach (var pack in packs)
        {
            var name = Path.GetFileName(pack);
            if (current.Contains(pack)) { lines.Add($"{name} is up to date."); continue; }
            if (!updates.TryGetValue(pack, out var file))
            {
                if (!lines.Any(l => l.StartsWith(name + ":", StringComparison.Ordinal)))
                    lines.Add($"{name}: not found on Modrinth{(catalog.HasCurseForgeKey ? " or CurseForge" : "")} for Minecraft {target.MinecraftVersion}.");
                continue;
            }
            progress?.Report($"Updating {name}...");
            try
            {
                lines.Add(await ReplacePackAsync(catalog, target, staging, recycle, pack, file, cancellationToken));
                updated++;
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException or System.Net.Http.HttpRequestException or ContentSourceException)
            {
                failed++;
                lines.Add($"{name}: {e.Message}");
            }
        }
        return (updated, failed, lines);
    }

    private static async Task<string> ReplacePackAsync(ContentCatalog catalog, ContentTarget target, SharedContentService staging, Action<string> recycle,
        string pack, ContentFile file, CancellationToken cancellationToken)
    {
        var oldName = Path.GetFileName(pack);
        using var incoming = staging.CreateIncomingFolder(target.ResourcePacks);
        var temp = await catalog.DownloadVerifiedAsync(file, incoming.Path, null, cancellationToken);
        string newName;
        if (string.Equals(file.FileName, oldName, StringComparison.OrdinalIgnoreCase))
        {
            File.Move(temp, pack, overwrite: true); // one rename on the same volume: the pack is never missing
            newName = oldName;
        }
        else
        {
            newName = Path.GetFileName(await Task.Run(() => SafeFileOps.MoveToFreeName(temp, target.ResourcePacks, file.FileName), cancellationToken));
            await Task.Run(() => recycle(pack), cancellationToken);
            foreach (var options in target.OptionsFiles) RenamePackInOptions(options, oldName, newName);
        }
        return newName == oldName ? $"{oldName}: updated to {file.VersionName}." : $"{oldName} → {newName} ({file.VersionName}).";
    }
}
