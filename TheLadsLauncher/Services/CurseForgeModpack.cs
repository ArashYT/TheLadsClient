using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public sealed record CurseForgeModpackFile(int ProjectId, int FileId, bool Required);

public sealed record CurseForgeModpackManifest(
    string Name,
    string Version,
    string? Author,
    string MinecraftVersion,
    string ModLoader,
    string ModLoaderVersion,
    string OverridesDir,
    IReadOnlyList<CurseForgeModpackFile> Files);

/// <summary>
/// Parser and installer for CurseForge modpack export archives (.zip with manifest.json and overrides/).
/// </summary>
public static class CurseForgeModpack
{
    public const string ManifestFileName = "manifest.json";

    public static bool IsCurseForgeModpack(string zipPath)
    {
        if (!File.Exists(zipPath)) return false;
        try
        {
            using var zip = ZipFile.OpenRead(zipPath);
            return zip.GetEntry(ManifestFileName) != null;
        }
        catch
        {
            return false;
        }
    }

    public static CurseForgeModpackManifest ParseManifest(Stream jsonStream)
    {
        using var doc = JsonDocument.Parse(jsonStream);
        var root = doc.RootElement;

        string name = root.TryGetProperty("name", out var n) ? n.GetString() ?? "Modpack" : "Modpack";
        string version = root.TryGetProperty("version", out var v) ? v.GetString() ?? "1.0.0" : "1.0.0";
        string? author = root.TryGetProperty("author", out var a) ? a.GetString() : null;
        string overrides = root.TryGetProperty("overrides", out var o) ? o.GetString() ?? "overrides" : "overrides";

        if (!root.TryGetProperty("minecraft", out var mc))
            throw new InvalidDataException("CurseForge modpack manifest is missing 'minecraft' configuration.");

        string mcVersion = mc.TryGetProperty("version", out var mv) ? mv.GetString() ?? "" : "";
        if (string.IsNullOrWhiteSpace(mcVersion))
            throw new InvalidDataException("CurseForge modpack manifest does not specify a Minecraft version.");

        string loader = "forge";
        string loaderVersion = "";

        if (mc.TryGetProperty("modLoaders", out var loaders) && loaders.ValueKind == JsonValueKind.Array && loaders.GetArrayLength() > 0)
        {
            var firstLoader = loaders[0];
            string loaderId = firstLoader.TryGetProperty("id", out var lid) ? lid.GetString() ?? "" : "";
            int dash = loaderId.IndexOf('-');
            if (dash > 0)
            {
                loader = loaderId[..dash].ToLowerInvariant();
                loaderVersion = loaderId[(dash + 1)..];
            }
            else
            {
                loader = loaderId.ToLowerInvariant();
            }
        }

        var files = new List<CurseForgeModpackFile>();
        if (root.TryGetProperty("files", out var fileList) && fileList.ValueKind == JsonValueKind.Array)
        {
            foreach (var f in fileList.EnumerateArray())
            {
                int pid = f.TryGetProperty("projectID", out var p) ? p.GetInt32() : 0;
                int fid = f.TryGetProperty("fileID", out var fl) ? fl.GetInt32() : 0;
                bool req = !f.TryGetProperty("required", out var r) || r.GetBoolean();
                if (pid > 0 && fid > 0)
                {
                    files.Add(new CurseForgeModpackFile(pid, fid, req));
                }
            }
        }

        return new CurseForgeModpackManifest(name, version, author, mcVersion, loader, loaderVersion, overrides, files);
    }

    public static CurseForgeModpackManifest ReadManifest(string zipPath)
    {
        using var zip = ZipFile.OpenRead(zipPath);
        var entry = zip.GetEntry(ManifestFileName)
            ?? throw new InvalidDataException($"Not a CurseForge modpack: {ManifestFileName} is missing.");
        using var stream = entry.Open();
        return ParseManifest(stream);
    }

    /// <summary>Extracts the overrides into the game folder, except Lads' own state files (<see cref="Mrpack.IsLadsStateFile"/>);
    /// returns the paths it left out.</summary>
    public static IReadOnlyList<string> ExtractOverrides(string zipPath, string gameDirectory, string overridesDir = "overrides")
    {
        using var zip = ZipFile.OpenRead(zipPath);
        string prefix = overridesDir.TrimEnd('/', '\\') + "/";
        var skipped = new List<string>();

        foreach (var entry in zip.Entries)
        {
            if (!entry.FullName.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) continue;
            string rel = entry.FullName[prefix.Length..];
            if (string.IsNullOrWhiteSpace(rel) || rel.EndsWith('/')) continue;

            string dest = Mrpack.SafePath(gameDirectory, rel);
            if (Mrpack.IsLadsStateFile(rel)) { skipped.Add(rel); continue; }
            string? dir = Path.GetDirectoryName(dest);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            entry.ExtractToFile(dest, overwrite: true);
        }
        return skipped;
    }

    public static async Task InstallAsync(string zipPath, string gameDirectory, ContentCatalog? catalog,
        Action<string> report, CancellationToken cancellationToken = default)
    {
        report("Reading CurseForge modpack manifest…");
        var manifest = ReadManifest(zipPath);

        report("Extracting modpack configuration overrides…");
        var skipped = ExtractOverrides(zipPath, gameDirectory, manifest.OverridesDir);
        if (skipped.Count > 0) report("Skipped Lads settings files in the pack: " + string.Join(", ", skipped));

        var modsDir = Path.Combine(gameDirectory, "mods");
        Directory.CreateDirectory(modsDir);

        if (catalog != null && manifest.Files.Count > 0)
        {
            int current = 0;
            foreach (var f in manifest.Files)
            {
                cancellationToken.ThrowIfCancellationRequested();
                current++;
                report($"Downloading mod {current} of {manifest.Files.Count} (Project {f.ProjectId})…");
                try
                {
                    ContentFile? fileItem = null;
                    if (f.FileId > 0)
                    {
                        fileItem = await catalog.GetCurseForgeFileAsync(f.ProjectId, f.FileId, cancellationToken);
                    }
                    if (fileItem == null)
                    {
                        fileItem = await catalog.LatestFileAsync(
                            new ModSearchItem { Id = f.ProjectId.ToString(), Provider = "CurseForge" },
                            ContentKind.Mod, manifest.MinecraftVersion, manifest.ModLoader, cancellationToken);
                    }

                    if (fileItem != null && !string.IsNullOrEmpty(fileItem.Url))
                    {
                        using var incoming = SharedContentService.Instance.CreateIncomingFolder(modsDir);
                        var downloaded = await catalog.DownloadVerifiedAsync(fileItem, incoming.Path, null, cancellationToken);
                        SafeFileOps.MoveToFreeName(downloaded, modsDir, fileItem.FileName);
                    }
                }
                catch (Exception ex)
                {
                    report($"Warning: Could not download mod {f.ProjectId}: {ex.Message}");
                }
            }
        }

        report("Modpack installation complete.");
    }
}
