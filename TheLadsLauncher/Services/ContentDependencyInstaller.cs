using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>Installs the required dependencies the sites list for the mods of a modpack (Modrinth "required", CurseForge relation 3),
/// recursively, each project once, skipping what the mods folder already has. Used for a mod that was just installed or updated
/// (<paramref name="added"/>) and for a whole folder (imports, launches, ModSync packs): every jar the sites know by hash is a root.
/// A site that cannot be asked, or a dependency without a download, only costs that dependency (status says so).</summary>
public static class ContentDependencyInstaller
{
    public const int MaxInstalls = 64;

    /// <param name="provider">"Modrinth" or "CurseForge": whose files <paramref name="added"/> are. Null with no <paramref name="added"/>: every jar in the folder is a root.</param>
    public static async Task<IReadOnlyList<string>> EnsureAsync(ContentCatalog catalog, string modsDirectory, string mcVersion, string loader,
        Action<string>? status, CancellationToken token, string? provider = null, IReadOnlyCollection<ContentFile>? added = null)
    {
        var installed = new List<string>();
        loader = (loader ?? "").Trim().ToLowerInvariant();
        if (loader is not ("fabric" or "quilt" or "forge" or "neoforge")) return installed;
        Directory.CreateDirectory(modsDirectory);

        var disk = await ScanAsync(catalog, modsDirectory, status, token);
        var present = new HashSet<string>(disk.Select(d => Key(d.Provider, d.File.ProjectId)), StringComparer.OrdinalIgnoreCase);
        var roots = disk.AsEnumerable();
        if (added != null)
        {
            var addedRoots = added.Select(f => (Provider: provider ?? "Modrinth", File: f)).ToList();
            foreach (var root in addedRoots) present.Add(Key(root.Provider, root.File.ProjectId));
            roots = addedRoots;
        }
        var queue = new Queue<ContentDependency>(roots.SelectMany(r => r.File.Dependencies));
        var visited = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        while (queue.Count > 0 && installed.Count < MaxInstalls)
        {
            token.ThrowIfCancellationRequested();
            var dep = queue.Dequeue();
            try
            {
                string key = Key(dep.Provider, dep.ProjectId);
                if (dep.ProjectId.Length > 0 && (present.Contains(key) || !visited.Add(key))) continue;
                ContentFile? file = dep.ProjectId.Length == 0 ? null
                    : await catalog.LatestFileAsync(new ModSearchItem { Provider = dep.Provider, Id = dep.ProjectId }, ContentKind.Mod, mcVersion, loader, token);
                if (file == null && dep.Provider == "Modrinth" && dep.VersionId != null) file = await catalog.ModrinthVersionAsync(dep.VersionId, token);
                if (file == null) { status?.Invoke($"No download of the required mod '{dep.ProjectId}' for Minecraft {mcVersion} was found."); continue; }
                if (dep.ProjectId.Length == 0 && (present.Contains(Key(dep.Provider, file.ProjectId)) || !visited.Add(Key(dep.Provider, file.ProjectId)))) continue;
                present.Add(Key(dep.Provider, file.ProjectId));
                foreach (var next in file.Dependencies) queue.Enqueue(next);
                if (HasFile(modsDirectory, file)) continue;
                status?.Invoke($"Installing {file.FileName} (needed by a mod)...");
                string path = await catalog.InstallAsync(file, modsDirectory, SharedContentService.Instance, null, token);
                if (ProvidesSameModAsAnotherJar(path, modsDirectory))
                {
                    File.Delete(path); // another version of it is already installed: two jars of one mod id stop the game
                    continue;
                }
                installed.Add(Path.GetFileName(path));
                status?.Invoke($"Installed {Path.GetFileName(path)}.");
            }
            catch (Exception e) when (!token.IsCancellationRequested && e is ContentSourceException or IOException or UnauthorizedAccessException)
            {
                status?.Invoke($"Could not install the required mod '{dep.ProjectId}' ({e.Message}).");
            }
        }
        return installed;
    }

    private static string Key(string provider, string projectId) => provider + ":" + projectId;

    private static bool HasFile(string modsDirectory, ContentFile file) =>
        SafeFileOps.IsPlainFileName(file.FileName)
        && (File.Exists(Path.Combine(modsDirectory, file.FileName)) || File.Exists(Path.Combine(modsDirectory, file.FileName + ".disabled")));

    /// <summary>The enabled jars of the folder the sites know exactly (by SHA-1 on Modrinth, by fingerprint on CurseForge).</summary>
    private static async Task<List<(string Provider, ContentFile File)>> ScanAsync(ContentCatalog catalog, string modsDirectory, Action<string>? status, CancellationToken token)
    {
        var result = new List<(string, ContentFile)>();
        var jars = Directory.EnumerateFiles(modsDirectory, "*.jar").ToList();
        if (jars.Count == 0) return result;
        try
        {
            var sha1s = jars.Select(ContentCatalog.Sha1).Distinct().ToList();
            var modrinth = await catalog.ModrinthFilesAsync(sha1s, token);
            result.AddRange(modrinth.Values.Select(f => ("Modrinth", f)));
            if (catalog.HasCurseForgeKey)
            {
                var rest = jars.Where(j => !modrinth.ContainsKey(ContentCatalog.Sha1(j))).Select(j => ContentCatalog.CurseForgeFingerprint(File.ReadAllBytes(j))).Distinct().ToList();
                result.AddRange((await catalog.CurseForgeMatchesAsync(rest, token)).Values.Select(f => ("CurseForge", f)));
            }
        }
        catch (Exception e) when (!token.IsCancellationRequested && e is ContentSourceException or IOException or UnauthorizedAccessException)
        {
            status?.Invoke($"The sites could not be asked about the installed mods ({e.Message}); their dependencies are not checked.");
        }
        return result;
    }

    private static bool ProvidesSameModAsAnotherJar(string newJar, string modsDirectory)
    {
        try
        {
            if (FabricModMetadata.ReadJar(newJar)?.Id is not { Length: > 0 } id) return false;
            return Directory.EnumerateFiles(modsDirectory, "*.jar").Where(j => !string.Equals(j, newJar, StringComparison.OrdinalIgnoreCase))
                .Any(j => { try { return FabricModMetadata.ReadJar(j)?.Id == id; } catch (Exception e) when (e is InvalidDataException or IOException) { return false; } });
        }
        catch (Exception e) when (e is InvalidDataException or IOException) { return false; }
    }
}
