using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <param name="PackArchiveUrl">Where fresh installs on other PCs download the pack archive (https; empty until it is hosted).
/// The archive is what tools/modpack-manager/export-pack.ps1 writes. <paramref name="PackArchiveSha256"/> is checked when set.</param>
public sealed record ExperienceDefinition(
    string Id,
    string Title,
    string Subtitle,
    string Description,
    string McVersion,
    string Loader,
    string LoaderVersion,
    int ModCount,
    string ServerAddress,
    string IconAsset,
    int RecommendedRamMb,
    string PackArchiveUrl = "",
    string PackArchiveSha256 = ""
);

/// <summary>
/// The official modpacks ("Experiences"). The pack source lives in the Lads data folder (THELADS_DIR aware), never in a fixed
/// user path:
/// <code>
/// &lt;data&gt;/experiences/&lt;id&gt;/pack.zip   the pack archive (downloaded from PackArchiveUrl, or dropped in by hand)
/// &lt;data&gt;/experiences/&lt;id&gt;/source/    the archive extracted: overrides/ (config, mods/, ...), all_mods/ (optional), pack.json, patches/
/// &lt;data&gt;/experiences/&lt;id&gt;/patches/   update-*.json patches to apply before the next launch (optional)
/// </code>
/// A ready-made source folder works without an archive.
/// </summary>
public static class ExperiencesService
{
    public static readonly ExperienceDefinition ZeroSmp = new(
        Id: "zero-smp-1.20.1",
        Title: "Zero SMP",
        Subtitle: "Official The Lads Modpack",
        Description: "An expansive adventure featuring Alex's Caves, Create machinery, immersive sound physics, dynamic animations, and proximity voice chat. Pre-configured with the official Zero SMP server.",
        McVersion: "1.20.1",
        Loader: "forge",
        LoaderVersion: "47.4.10",
        ModCount: 131,
        ServerAddress: "nikzerosmp.my.pebble.host",
        IconAsset: "avares://TheLadsLauncher/Assets/zero_smp_icon.png",
        RecommendedRamMb: 6144,
        PackArchiveUrl: "https://bot.arashyt.ca/packs/zero-smp-1.20.1.zip"
    );

    public static readonly IReadOnlyList<ExperienceDefinition> AllExperiences = new[] { ZeroSmp };

    public sealed record ExperienceButtonState(string Text, bool IsEnabled, string? Hint = null);

    public static ExperienceButtonState GetZeroSmpButtonState(bool installed, bool isRunning = false, bool isBusy = false)
    {
        if (isRunning) return new ExperienceButtonState("Game Running", false);
        if (isBusy) return new ExperienceButtonState("Please wait...", false);
        if (installed) return new ExperienceButtonState("Play Zero SMP", true);
        return new ExperienceButtonState("Install & Play Zero SMP", true);
    }

    public static string InstanceDirectory(string baseDirectory, string experienceId) =>
        Path.Combine(Modpacks.Root(baseDirectory), experienceId);

    public static string PackRoot(string baseDirectory, string experienceId) => Path.Combine(baseDirectory, "experiences", experienceId);
    public static string PackArchivePath(string baseDirectory, string experienceId) => Path.Combine(PackRoot(baseDirectory, experienceId), "pack.zip");
    public static string PackSourceDirectory(string baseDirectory, string experienceId) => Path.Combine(PackRoot(baseDirectory, experienceId), "source");

    public static bool IsInstalled(string baseDirectory, string experienceId)
    {
        var dir = InstanceDirectory(baseDirectory, experienceId);
        var json = Path.Combine(dir, "instance.json");
        var modsDir = Path.Combine(dir, "minecraft", "mods");
        return File.Exists(json) && Directory.Exists(modsDir) && Directory.EnumerateFiles(modsDir, "*.jar").Any();
    }

    public static ModpackInstance? GetInstance(string baseDirectory, string experienceId) =>
        Modpacks.List(baseDirectory).FirstOrDefault(i => i.Id == experienceId);

    /// <summary>
    /// Ensures the Zero SMP server is present in the specified Minecraft game directory's servers.dat,
    /// creating or updating the entry idempotently and preserving other servers.
    /// </summary>
    public static bool EnsureZeroSmpServer(string gameDirectory)
    {
        var serversPath = Path.Combine(gameDirectory, "servers.dat");
        return ServerListService.EnsureServer(serversPath, ZeroSmp.Title, ZeroSmp.ServerAddress);
    }

    /// <summary>Ensures the Zero SMP server is present in the instance's servers.dat under the given base directory.</summary>
    public static bool EnsureZeroSmpServerInstance(string baseDirectory) =>
        EnsureZeroSmpServer(Path.Combine(InstanceDirectory(baseDirectory, ZeroSmp.Id), "minecraft"));

    /// <summary>Installs (or reinstalls over) the Zero SMP instance from the pack source. Saves, options and the player's own files stay.</summary>
    public static async Task<ModpackInstance> InstallZeroSmpAsync(string baseDirectory, Action<string>? status, CancellationToken token, HttpClient? http = null)
    {
        var exp = ZeroSmp;
        var instDir = InstanceDirectory(baseDirectory, exp.Id);
        var gameDir = Path.Combine(instDir, "minecraft");
        var modsDir = Path.Combine(gameDir, "mods");
        var rpDir = Path.Combine(gameDir, "resourcepacks");

        status?.Invoke("Setting up Zero SMP instance...");
        var source = await EnsurePackSourceAsync(baseDirectory, exp, http, status, token);

        Directory.CreateDirectory(modsDir);
        Directory.CreateDirectory(rpDir);
        status?.Invoke("Installing modpack files and configurations...");
        await Task.Run(() =>
        {
            var overrides = Path.Combine(source, "overrides");
            if (Directory.Exists(overrides))
            {
                var iconSrc = Path.Combine(overrides, "icon.png");
                if (File.Exists(iconSrc))
                {
                    try { File.Copy(iconSrc, Path.Combine(instDir, "icon.png"), overwrite: true); } catch (IOException) { }
                }

                CopyDirectoryContents(overrides, gameDir, skipSubdirs: new[] { "mods", ".mixin.out", "immersive_paintings_cache", "particular_cache" },
                    skipFilePrefixes: new[] { "hs_err_pid" }, skipFileExtensions: new[] { ".log" }, skipFiles: new[] { "servers.dat" });

                var overridesServers = Path.Combine(overrides, "servers.dat");
                var destServers = Path.Combine(gameDir, "servers.dat");
                if (!File.Exists(destServers) && File.Exists(overridesServers))
                {
                    try { File.Copy(overridesServers, destServers, overwrite: false); } catch { }
                }

                EnsureZeroSmpServer(gameDir);

                var localMods = Path.Combine(overrides, "mods");
                if (Directory.Exists(localMods))
                {
                    foreach (var jar in Directory.EnumerateFiles(localMods, "*.jar"))
                        File.Copy(jar, Path.Combine(modsDir, Path.GetFileName(jar)), overwrite: true);
                }
            }

            var allMods = Path.Combine(source, "all_mods");
            if (Directory.Exists(allMods))
            {
                foreach (var jar in Directory.EnumerateFiles(allMods, "*.jar"))
                    File.Copy(jar, Path.Combine(modsDir, Path.GetFileName(jar)), overwrite: true);
                foreach (var zip in Directory.EnumerateFiles(allMods, "*.zip"))
                    File.Copy(zip, Path.Combine(rpDir, Path.GetFileName(zip)), overwrite: true);
            }
        }, token);

        EnsureZeroSmpServer(gameDir);

        var pack = ReadPackInfo(source);
        var instance = new ModpackInstance
        {
            Id = exp.Id,
            Name = exp.Title,
            McVersion = exp.McVersion,
            Loader = exp.Loader,
            LoaderVersion = exp.LoaderVersion,
            IconPath = "icon.png",
            CreatedUtc = DateTime.UtcNow,
            Author = "The Lads",
            PackVersion = pack.Version ?? "1.0.0",
            MaxRamMb = exp.RecommendedRamMb,
            Directory = instDir
        };
        Modpacks.Save(instance);
        // The archive already contains what its patches change; they must not run again on top of the player's choices.
        foreach (var applied in pack.AppliedPatches) ModpackPatchEngine.RecordAppliedPatch(instDir, applied);
        ModpackCoreSync.Sync(instance, status: status);

        status?.Invoke("Zero SMP instance ready.");
        return instance;
    }

    /// <summary>Keeps the Lads Core of an installed Experience at the launcher's newest bundled build (nothing happens when the
    /// instance has no Core jar). <see cref="ModpackLauncher.PrepareAsync"/> does the same before every launch.</summary>
    public static bool SyncLadsCore(string baseDirectory, string experienceId)
    {
        var exp = AllExperiences.FirstOrDefault(e => e.Id == experienceId);
        return exp != null && ModpackCoreSync.Sync(Path.Combine(InstanceDirectory(baseDirectory, experienceId), "minecraft"), exp.McVersion);
    }

    /// <summary>The pack patches shipped with the launcher (tools/modpack-manager/patches, copied next to the exe): every launcher
    /// update brings the newest ones to installed packs, as it brings the newest Lads Core.</summary>
    public static string BundledPatchesDirectory => Path.Combine(AppContext.BaseDirectory, "modpack-patches");

    /// <summary>Applies every pack patch (update-*.json in &lt;pack root&gt;/patches, the extracted archive or
    /// <paramref name="bundledPatchesDirectory"/>) the instance has not had yet, oldest first, before the game starts. An instance
    /// without a patch record first adopts the patches it already reflects (<see cref="ModpackPatchEngine.AdoptReflectedPatches"/>).
    /// A failing patch stops the run and is retried at the next launch. Returns how many patches were applied.</summary>
    public static async Task<int> ApplyPendingPatchesAsync(string baseDirectory, string experienceId, HttpClient? http, Action<string>? status, CancellationToken token,
        string? bundledPatchesDirectory = null)
    {
        var instDir = InstanceDirectory(baseDirectory, experienceId);
        var dirs = new[] { Path.Combine(PackRoot(baseDirectory, experienceId), "patches"), Path.Combine(PackSourceDirectory(baseDirectory, experienceId), "patches"), bundledPatchesDirectory };
        var pending = ModpackPatchEngine.AdoptReflectedPatches(instDir, dirs.OfType<string>()
            .SelectMany(dir => ModpackPatchEngine.PendingPatches(dir, instDir, experienceId))
            .GroupBy(p => p.Version, StringComparer.OrdinalIgnoreCase).Select(g => g.First())
            .OrderBy(p => Version.TryParse(p.Version, out var v) ? v : new Version(0, 0)).ToList());
        foreach (var patch in pending)
        {
            token.ThrowIfCancellationRequested();
            await ModpackPatchEngine.ApplyPatchAsync(instDir, patch, http, status, token);
        }
        return pending.Count;
    }

    /// <summary>The folder the install copies from: an extracted source, else the archive extracted, else the archive downloaded and
    /// extracted. Throws with the place to put the archive when none of these exists.</summary>
    public static async Task<string> EnsurePackSourceAsync(string baseDirectory, ExperienceDefinition exp, HttpClient? http, Action<string>? status, CancellationToken token)
    {
        var root = PackRoot(baseDirectory, exp.Id);
        var source = PackSourceDirectory(baseDirectory, exp.Id);
        var archive = PackArchivePath(baseDirectory, exp.Id);
        Directory.CreateDirectory(root);

        if (!File.Exists(archive) && !HasPackFiles(source))
        {
            if (http == null || !Uri.TryCreate(exp.PackArchiveUrl, UriKind.Absolute, out var url) || url.Scheme != Uri.UriSchemeHttps)
                throw new FileNotFoundException(
                    $"The {exp.Title} pack is not available yet. Put its pack archive at '{archive}' (tools/modpack-manager/export-pack.ps1 writes it) and try again.", archive);
            await DownloadArchiveAsync(http, url, archive, exp.PackArchiveSha256, status, token);
        }

        // An archive newer than what was extracted from it replaces the extracted copy.
        if (File.Exists(archive))
        {
            var stamp = Path.Combine(source, ".extracted-from");
            var current = $"{new FileInfo(archive).Length}:{File.GetLastWriteTimeUtc(archive).Ticks}";
            if (!HasPackFiles(source) || !File.Exists(stamp) || File.ReadAllText(stamp) != current)
            {
                status?.Invoke("Unpacking the pack archive...");
                await Task.Run(() => ExtractArchive(archive, source, current), token);
            }
        }
        if (!HasPackFiles(source)) throw new InvalidDataException($"'{source}' has no overrides/ or all_mods/ folder: this is not a Lads pack archive.");
        return source;
    }

    private static bool HasPackFiles(string source) => Directory.Exists(Path.Combine(source, "overrides")) || Directory.Exists(Path.Combine(source, "all_mods"));

    private static async Task DownloadArchiveAsync(HttpClient http, Uri url, string archive, string sha256, Action<string>? status, CancellationToken token)
    {
        var part = archive + ".part";
        using (var response = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, token))
        {
            response.EnsureSuccessStatusCode();
            long total = response.Content.Headers.ContentLength ?? 0, done = 0;
            await using var input = await response.Content.ReadAsStreamAsync(token);
            await using var output = new FileStream(part, FileMode.Create, FileAccess.Write, FileShare.None, 81920, useAsync: true);
            var buffer = new byte[81920];
            int read, lastMb = -1;
            while ((read = await input.ReadAsync(buffer, token)) > 0)
            {
                await output.WriteAsync(buffer.AsMemory(0, read), token);
                done += read;
                if ((int)(done >> 20) is var mb && mb != lastMb)
                {
                    lastMb = mb;
                    status?.Invoke(total > 0 ? $"Downloading the pack... {mb}/{total >> 20} MB" : $"Downloading the pack... {mb} MB");
                }
            }
        }
        if (!string.IsNullOrWhiteSpace(sha256))
        {
            string actual;
            await using (var check = File.OpenRead(part)) actual = Convert.ToHexString(await SHA256.HashDataAsync(check, token));
            if (!actual.Equals(sha256, StringComparison.OrdinalIgnoreCase))
            {
                File.Delete(part);
                throw new InvalidDataException($"The downloaded pack does not match its published checksum (expected {sha256}, got {actual}).");
            }
        }
        File.Move(part, archive, overwrite: true);
    }

    /// <summary>Extracts next to the target and renames, so an interrupted run never leaves a half source behind.</summary>
    private static void ExtractArchive(string archive, string source, string stamp)
    {
        var staging = source + ".tmp";
        SafeFileOps.DeleteTree(staging);
        Directory.CreateDirectory(staging);
        using (var zip = ZipFile.OpenRead(archive))
        {
            foreach (var entry in zip.Entries)
            {
                var name = entry.FullName.Replace('\\', '/');
                if (name.EndsWith('/')) continue;
                var target = Mrpack.SafePath(staging, name); // refuses ../ and absolute entries
                Directory.CreateDirectory(Path.GetDirectoryName(target)!);
                entry.ExtractToFile(target, overwrite: true);
            }
        }
        File.WriteAllText(Path.Combine(staging, ".extracted-from"), stamp);
        SafeFileOps.DeleteTree(source);
        Directory.Move(staging, source);
    }

    private sealed record PackInfo(string? Version, IReadOnlyList<string> AppliedPatches);

    private static PackInfo ReadPackInfo(string source)
    {
        try
        {
            using var json = JsonDocument.Parse(File.ReadAllText(Path.Combine(source, "pack.json")));
            var root = json.RootElement;
            var version = root.TryGetProperty("packVersion", out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;
            var applied = root.TryGetProperty("appliedPatches", out var a) && a.ValueKind == JsonValueKind.Array
                ? a.EnumerateArray().Where(e => e.ValueKind == JsonValueKind.String).Select(e => e.GetString()!).ToList() : new List<string>();
            return new PackInfo(version, applied);
        }
        catch (Exception e) when (e is IOException or JsonException or UnauthorizedAccessException) { return new PackInfo(null, Array.Empty<string>()); }
    }

    private static void CopyDirectoryContents(string source, string target, string[] skipSubdirs, string[] skipFilePrefixes, string[] skipFileExtensions, string[]? skipFiles = null)
    {
        Directory.CreateDirectory(target);
        foreach (var dir in Directory.EnumerateDirectories(source))
        {
            var name = Path.GetFileName(dir);
            if (skipSubdirs.Contains(name, StringComparer.OrdinalIgnoreCase)) continue;
            CopyDirectoryRecursive(dir, Path.Combine(target, name));
        }
        foreach (var file in Directory.EnumerateFiles(source))
        {
            var name = Path.GetFileName(file);
            if (name.Equals("icon.png", StringComparison.OrdinalIgnoreCase)) continue;
            if (skipFiles != null && skipFiles.Contains(name, StringComparer.OrdinalIgnoreCase)) continue;
            if (skipFilePrefixes.Any(p => name.StartsWith(p, StringComparison.OrdinalIgnoreCase))) continue;
            if (skipFileExtensions.Any(e => name.EndsWith(e, StringComparison.OrdinalIgnoreCase))) continue;
            File.Copy(file, Path.Combine(target, name), overwrite: true);
        }
    }

    private static void CopyDirectoryRecursive(string source, string target)
    {
        Directory.CreateDirectory(target);
        foreach (var dir in Directory.EnumerateDirectories(source))
            CopyDirectoryRecursive(dir, Path.Combine(target, Path.GetFileName(dir)));
        foreach (var file in Directory.EnumerateFiles(source))
            File.Copy(file, Path.Combine(target, Path.GetFileName(file)), overwrite: true);
    }
}
