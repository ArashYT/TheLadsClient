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
    string PackArchiveSha256 = "",
    bool InviteOnly = false
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
    public static readonly ExperienceDefinition LadsSmpReborn = new(
        Id: "lads-smp-reborn-26.3",
        Title: "The Lads SMP Reborn",
        Subtitle: "Official The Lads Modpack",
        Description: "The Lads SMP is back! This time with fully custom mods, and more polished than ever!",
        McVersion: "26.3",
        Loader: "fabric",
        LoaderVersion: "0.19.5",
        ModCount: 335,
        ServerAddress: "",
        IconAsset: "avares://TheLadsLauncher/Assets/lads_smp_reborn_icon.png",
        RecommendedRamMb: 8192,
        PackArchiveUrl: "https://bot.arashyt.ca/packs/lads-smp-reborn-26.3.zip",
        PackArchiveSha256: "E92C7DB5C12BC78AA130BF7D793ED9062A82E5D9FC39FDAC7F0A73CF21A05E88",
        InviteOnly: true
    );

    public const string LadsSmpRebornDevId = "lads-smp-reborn-26.3-dev";

    public static readonly ExperienceDefinition ZeroSmp = new(
        Id: "zero-smp-1.20.1",
        Title: "Zero SMP",
        Subtitle: "Official The Lads Modpack",
        Description: "A modded SMP Owned by Vools, if you like re zero, this is your place hangout!",
        McVersion: "1.20.1",
        Loader: "forge",
        LoaderVersion: "47.4.10",
        ModCount: 137,
        ServerAddress: "nikzerosmp.my.pebble.host",
        IconAsset: "avares://TheLadsLauncher/Assets/zero_smp_icon.png",
        RecommendedRamMb: 6144,
        PackArchiveUrl: "https://bot.arashyt.ca/packs/zero-smp-1.20.1.zip",
        PackArchiveSha256: "DD70F98EF39D30B247CC2130119994EE90DB6459BEE5E09A2AB3596C353AD64D"
    );

    public const string ZeroSmpDevId = "zero-smp-1.20.1-dev";

    public static readonly IReadOnlyList<ExperienceDefinition> AllExperiences = new[] { LadsSmpReborn, ZeroSmp };

    public static bool IsDevLauncherEnvironment()
    {
        if (Environment.GetEnvironmentVariable("LADS_DEV_LAUNCHER") == "1") return true;
        if (File.Exists(Path.Combine(AppContext.BaseDirectory, ".lads-dev-launcher"))) return true;
        if (!string.Equals(System.Reflection.Assembly.GetEntryAssembly()?.GetName().Name, "TheLadsLauncher", StringComparison.OrdinalIgnoreCase))
            return false;
        for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir is { Exists: true }; dir = dir.Parent)
        {
            if (File.Exists(Path.Combine(dir.FullName, "TheLadsLauncher.csproj"))
                || File.Exists(Path.Combine(dir.FullName, "TheLadsLauncher", "TheLadsLauncher.csproj")))
                return true;
        }
        return false;
    }

    public static string DevInstanceId(ExperienceDefinition exp) => $"{exp.Id}-dev";

    public static string ActiveInstanceId(ExperienceDefinition exp, bool? isDevLauncher = null) =>
        (isDevLauncher ?? IsDevLauncherEnvironment()) ? DevInstanceId(exp) : exp.Id;

    public static string ActiveLadsSmpRebornInstanceId(bool? isDevLauncher = null) =>
        ActiveInstanceId(LadsSmpReborn, isDevLauncher);

    public static string ActiveZeroSmpInstanceId(bool? isDevLauncher = null) =>
        ActiveInstanceId(ZeroSmp, isDevLauncher);

    public static string CanonicalPackId(string experienceOrInstanceId)
    {
        if (string.Equals(experienceOrInstanceId, ZeroSmpDevId, StringComparison.OrdinalIgnoreCase))
            return ZeroSmp.Id;
        if (string.Equals(experienceOrInstanceId, LadsSmpRebornDevId, StringComparison.OrdinalIgnoreCase))
            return LadsSmpReborn.Id;
        if (experienceOrInstanceId.EndsWith("-dev", StringComparison.OrdinalIgnoreCase))
            return experienceOrInstanceId[..^4];
        return experienceOrInstanceId;
    }

    /// <summary>An official Experience's instance (its dev copy too): shown under Play > Experiences, not in My Modpacks.</summary>
    public static bool IsExperienceInstance(string instanceId) =>
        AllExperiences.Any(e => string.Equals(e.Id, CanonicalPackId(instanceId), StringComparison.OrdinalIgnoreCase));

    public sealed record ExperienceButtonState(string Text, bool IsEnabled, string? Hint = null);

    public static ExperienceButtonState GetExperienceButtonState(ExperienceDefinition exp, bool installed, bool isRunning = false, bool isBusy = false, bool allowMultiInstance = false)
    {
        if (isBusy) return new ExperienceButtonState("Please wait...", false);
        if (isRunning && !allowMultiInstance) return new ExperienceButtonState("Game Running", false);
        if (installed) return new ExperienceButtonState("Play", true);
        return new ExperienceButtonState($"Install & Play {exp.Title}", true);
    }

    public static ExperienceButtonState GetLadsSmpRebornButtonState(bool installed, bool isRunning = false, bool isBusy = false, bool allowMultiInstance = false) =>
        GetExperienceButtonState(LadsSmpReborn, installed, isRunning, isBusy, allowMultiInstance);

    public static ExperienceButtonState GetZeroSmpButtonState(bool installed, bool isRunning = false, bool isBusy = false, bool allowMultiInstance = false) =>
        GetExperienceButtonState(ZeroSmp, installed, isRunning, isBusy, allowMultiInstance);

    public static string InstanceDirectory(string baseDirectory, string experienceId) =>
        Path.Combine(Modpacks.Root(baseDirectory), experienceId);

    public static string PackRoot(string baseDirectory, string experienceId) => Path.Combine(baseDirectory, "experiences", CanonicalPackId(experienceId));
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
    /// Seeds the dev launcher's Experience instance (&lt;id&gt;-dev) from the regular instance (&lt;id&gt;)
    /// once when the dev instance does not exist yet, keeping the two instances completely separate from then on.
    /// </summary>
    public static void EnsureDevExperienceSeeded(string baseDirectory, ExperienceDefinition exp)
    {
        try
        {
            var devId = DevInstanceId(exp);
            if (IsInstalled(baseDirectory, devId) || !IsInstalled(baseDirectory, exp.Id)) return;
            var srcDir = InstanceDirectory(baseDirectory, exp.Id);
            var dstDir = InstanceDirectory(baseDirectory, devId);
            Directory.CreateDirectory(dstDir);
            CopyDirectoryContents(srcDir, dstDir,
                skipSubdirs: new[] { ".mixin.out", "immersive_paintings_cache", "particular_cache" },
                skipFilePrefixes: new[] { "hs_err_pid" },
                skipFileExtensions: new[] { ".log" },
                skipFiles: new[] { "instance.json", RunningGameMarker.FileName });

            var srcInstance = GetInstance(baseDirectory, exp.Id);
            var defaultVersion = string.Equals(exp.Id, LadsSmpReborn.Id, StringComparison.OrdinalIgnoreCase) ? "0.2.1" : "1.0.0";
            var devInstance = new ModpackInstance
            {
                Id = devId,
                Name = $"{exp.Title} (Dev)",
                McVersion = srcInstance?.McVersion ?? exp.McVersion,
                Loader = srcInstance?.Loader ?? exp.Loader,
                LoaderVersion = srcInstance?.LoaderVersion ?? exp.LoaderVersion,
                IconPath = srcInstance?.IconPath ?? "icon.png",
                CreatedUtc = DateTime.UtcNow,
                Author = srcInstance?.Author ?? "The Lads",
                PackVersion = srcInstance?.PackVersion ?? defaultVersion,
                MaxRamMb = srcInstance is { MaxRamMb: > 0 } ? srcInstance.MaxRamMb : exp.RecommendedRamMb,
                Directory = dstDir
            };
            Modpacks.Save(devInstance);
        }
        catch { }
    }

    public static void EnsureDevLadsSmpRebornSeeded(string baseDirectory) =>
        EnsureDevExperienceSeeded(baseDirectory, LadsSmpReborn);

    public static void EnsureDevZeroSmpSeeded(string baseDirectory) =>
        EnsureDevExperienceSeeded(baseDirectory, ZeroSmp);

    /// <summary>
    /// Ensures the Experience's server (when configured) is present in the specified Minecraft game directory's servers.dat,
    /// creating or updating the entry idempotently and preserving other servers.
    /// </summary>
    public static bool EnsureExperienceServer(string gameDirectory, ExperienceDefinition exp)
    {
        if (string.IsNullOrWhiteSpace(exp.ServerAddress)) return false;
        var serversPath = Path.Combine(gameDirectory, "servers.dat");
        return ServerListService.EnsureServer(serversPath, exp.Title, exp.ServerAddress);
    }

    public static bool EnsureZeroSmpServer(string gameDirectory) =>
        EnsureExperienceServer(gameDirectory, ZeroSmp);

    /// <summary>Ensures the Zero SMP server is present in the instance's servers.dat under the given base directory.</summary>
    public static bool EnsureZeroSmpServerInstance(string baseDirectory) =>
        EnsureZeroSmpServer(Path.Combine(InstanceDirectory(baseDirectory, ZeroSmp.Id), "minecraft"));

    public static readonly HttpClient DefaultHttp = CreateDefaultHttp();

    private static readonly HashSet<string> AllowedDownloadHosts = new(StringComparer.OrdinalIgnoreCase)
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

    public const string AccessEndpoint = "https://bot.arashyt.ca/experiences/";

    /// <summary>
    /// Whether an Experience may be shown for these Minecraft usernames. Invite-only Experiences ask the bot, whose list staff
    /// edit live with /smp_invite. The last answer is cached so the card stays right offline; never answered means hidden.
    /// </summary>
    public static async Task<bool> HasAccessAsync(string baseDirectory, ExperienceDefinition exp, IEnumerable<string> usernames, HttpClient? http = null, CancellationToken token = default) =>
        !exp.InviteOnly || await HasAccessAsync(baseDirectory, exp.Id, usernames, http, token);

    /// <summary>The bot's invite list <paramref name="inviteId"/> (an Experience, or "experimental" for /experimental_invite).</summary>
    public static async Task<bool> HasAccessAsync(string baseDirectory, string inviteId, IEnumerable<string> usernames, HttpClient? http = null, CancellationToken token = default)
    {
        var names = usernames.Where(n => !string.IsNullOrWhiteSpace(n)).Select(n => n.Trim().ToLowerInvariant()).Distinct().OrderBy(n => n, StringComparer.Ordinal).ToList();
        if (names.Count == 0) return false;
        var key = string.Join(",", names);
        var cache = Path.Combine(PackRoot(baseDirectory, inviteId), "access.json");
        try
        {
            var url = AccessEndpoint + Uri.EscapeDataString(inviteId) + "/access?" + string.Join("&", names.Select(n => "u=" + Uri.EscapeDataString(n)));
            using var cts = CancellationTokenSource.CreateLinkedTokenSource(token);
            cts.CancelAfter(TimeSpan.FromSeconds(10));
            using var doc = JsonDocument.Parse(await (http ?? DefaultHttp).GetStringAsync(url, cts.Token));
            var allowed = doc.RootElement.GetProperty("allowed").GetBoolean();
            Directory.CreateDirectory(Path.GetDirectoryName(cache)!);
            File.WriteAllText(cache, JsonSerializer.Serialize(new { names = key, allowed }));
            return allowed;
        }
        catch (OperationCanceledException) when (token.IsCancellationRequested) { throw; }
        catch
        {
            try
            {
                using var cached = JsonDocument.Parse(File.ReadAllText(cache));
                return cached.RootElement.GetProperty("names").GetString() == key && cached.RootElement.GetProperty("allowed").GetBoolean();
            }
            catch { return false; }
        }
    }

    private static HttpClient CreateDefaultHttp()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromMinutes(10) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("TheLadsLauncher/1.0 (https://bot.arashyt.ca)");
        return client;
    }

    /// <summary>Installs an Experience instance from a local pack zip file (copying it to the pack archive path first if needed).</summary>
    public static Task<ModpackInstance> InstallFromZipAsync(ExperienceDefinition exp, string zipPath, string baseDirectory, Action<string>? status, CancellationToken token, HttpClient? http = null, string? instanceIdOverride = null)
    {
        var archive = PackArchivePath(baseDirectory, exp.Id);
        Directory.CreateDirectory(Path.GetDirectoryName(archive)!);
        if (!SafeFileOps.PathsEqual(Path.GetFullPath(zipPath), Path.GetFullPath(archive)))
        {
            File.Copy(zipPath, archive, overwrite: true);
        }
        return InstallExperienceAsync(exp, baseDirectory, status, token, http, instanceIdOverride);
    }

    /// <summary>Installs (or reinstalls over) an Experience instance from its pack source. Saves, options and the player's own files stay.</summary>
    public static async Task<ModpackInstance> InstallExperienceAsync(ExperienceDefinition exp, string baseDirectory, Action<string>? status, CancellationToken token, HttpClient? http = null, string? instanceIdOverride = null)
    {
        var devId = DevInstanceId(exp);
        var instanceId = string.IsNullOrWhiteSpace(instanceIdOverride) ? exp.Id : instanceIdOverride;
        var instanceTitle = string.Equals(instanceId, devId, StringComparison.OrdinalIgnoreCase) ? $"{exp.Title} (Dev)" : exp.Title;
        var instDir = InstanceDirectory(baseDirectory, instanceId);
        var gameDir = Path.Combine(instDir, "minecraft");
        var modsDir = Path.Combine(gameDir, "mods");
        var rpDir = Path.Combine(gameDir, "resourcepacks");

        status?.Invoke($"Setting up {instanceTitle} instance...");
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

                EnsureExperienceServer(gameDir, exp);

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

        if (File.Exists(Path.Combine(source, "modrinth.index.json")))
        {
            await InstallManifestFilesAsync(source, gameDir, http ?? DefaultHttp, status, token);
        }

        EnsureExperienceServer(gameDir, exp);

        var pack = ReadPackInfo(source);
        var defaultVersion = string.Equals(exp.Id, LadsSmpReborn.Id, StringComparison.OrdinalIgnoreCase) ? "0.2.1" : "1.0.0";
        var instance = new ModpackInstance
        {
            Id = instanceId,
            Name = instanceTitle,
            McVersion = exp.McVersion,
            Loader = exp.Loader,
            LoaderVersion = exp.LoaderVersion,
            IconPath = "icon.png",
            CreatedUtc = DateTime.UtcNow,
            Author = "The Lads",
            PackVersion = pack.Version ?? defaultVersion,
            MaxRamMb = exp.RecommendedRamMb,
            Directory = instDir
        };
        Modpacks.Save(instance);
        // The archive already contains what its patches change; they must not run again on top of the player's choices.
        foreach (var applied in pack.AppliedPatches) ModpackPatchEngine.RecordAppliedPatch(instDir, applied);
        if (pack.IncludeLadsCore
            && Environment.GetEnvironmentVariable(ModpackCoreSync.SkipEnvironmentVariable) != "1"
            && ModpackCoreSync.CoreJars(modsDir).Count == 0
            && ModpackCoreSync.FindBundledJar(exp.McVersion) is { } bundledCore
            && File.Exists(bundledCore))
        {
            try { File.Copy(bundledCore, Path.Combine(modsDir, ModpackCoreSync.BundledName), overwrite: true); }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        }
        ModpackCoreSync.Sync(instance, status: status);

        status?.Invoke($"{instanceTitle} instance ready.");
        return instance;
    }

    public static Task<ModpackInstance> InstallLadsSmpRebornAsync(string baseDirectory, Action<string>? status, CancellationToken token, HttpClient? http = null, string? instanceIdOverride = null) =>
        InstallExperienceAsync(LadsSmpReborn, baseDirectory, status, token, http, instanceIdOverride);

    public static Task<ModpackInstance> InstallZeroSmpAsync(string baseDirectory, Action<string>? status, CancellationToken token, HttpClient? http = null, string? instanceIdOverride = null) =>
        InstallExperienceAsync(ZeroSmp, baseDirectory, status, token, http, instanceIdOverride);

    /// <summary>Keeps the Lads Core of an installed Experience at the launcher's newest bundled build (nothing happens when the
    /// instance has no Core jar). <see cref="ModpackLauncher.PrepareAsync"/> does the same before every launch.</summary>
    public static bool SyncLadsCore(string baseDirectory, string experienceId)
    {
        var packId = CanonicalPackId(experienceId);
        var exp = AllExperiences.FirstOrDefault(e => e.Id == packId);
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
        var packId = CanonicalPackId(experienceId);
        var dirs = new[] { Path.Combine(PackRoot(baseDirectory, packId), "patches"), Path.Combine(PackSourceDirectory(baseDirectory, packId), "patches"), bundledPatchesDirectory };
        var pending = ModpackPatchEngine.AdoptReflectedPatches(instDir, dirs.OfType<string>()
            .SelectMany(dir => ModpackPatchEngine.PendingPatches(dir, instDir, packId))
            .GroupBy(p => p.Version, StringComparer.OrdinalIgnoreCase).Select(g => g.First())
            .OrderBy(p => Version.TryParse(p.Version, out var v) ? v : new Version(0, 0)).ToList());
        int applied = 0;
        foreach (var patch in pending)
        {
            token.ThrowIfCancellationRequested();
            if (!await ModpackPatchEngine.ApplyPatchAsync(instDir, patch, http, status, token))
            {
                status?.Invoke($"Update {patch.Version} is incomplete, so newer updates wait for it.");
                break;
            }
            applied++;
        }
        return applied;
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
        // A launcher update pinned a newer pack than the cached one: download it. Offline (or the server not updated yet)
        // the cached pack keeps working.
        else if (File.Exists(archive) && http != null && !string.IsNullOrWhiteSpace(exp.PackArchiveSha256)
                 && Uri.TryCreate(exp.PackArchiveUrl, UriKind.Absolute, out var pinned) && pinned.Scheme == Uri.UriSchemeHttps
                 && !(await FileSha256Async(archive, token)).Equals(exp.PackArchiveSha256, StringComparison.OrdinalIgnoreCase))
        {
            try
            {
                status?.Invoke("Downloading the updated pack...");
                await DownloadArchiveAsync(http, pinned, archive, exp.PackArchiveSha256, status, token);
            }
            catch (Exception e) when (e is HttpRequestException or InvalidDataException or IOException || (e is TaskCanceledException && !token.IsCancellationRequested))
            {
                status?.Invoke("Could not download the updated pack; using the one already downloaded.");
            }
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
        if (!HasPackFiles(source)) throw new InvalidDataException($"'{source}' has no overrides/, all_mods/, or modrinth.index.json: this is not a Lads pack archive.");
        return source;
    }

    private static bool HasPackFiles(string source) =>
        Directory.Exists(Path.Combine(source, "overrides"))
        || Directory.Exists(Path.Combine(source, "all_mods"))
        || File.Exists(Path.Combine(source, "modrinth.index.json"));

    private sealed class ProgressStream : Stream
    {
        private readonly Stream _inner;
        private readonly long _total;
        private readonly Action<string>? _status;
        private long _done;
        private int _lastMb = -1;

        public ProgressStream(Stream inner, long total, Action<string>? status)
        {
            _inner = inner;
            _total = total;
            _status = status;
        }

        public override void Write(byte[] buffer, int offset, int count)
        {
            _inner.Write(buffer, offset, count);
            Report(count);
        }

        public override async ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken cancellationToken = default)
        {
            await _inner.WriteAsync(buffer, cancellationToken);
            Report(buffer.Length);
        }

        public override async Task WriteAsync(byte[] buffer, int offset, int count, CancellationToken cancellationToken)
        {
            await _inner.WriteAsync(buffer, offset, count, cancellationToken);
            Report(count);
        }

        private void Report(int bytes)
        {
            _done += bytes;
            if ((int)(_done >> 20) is var mb && mb != _lastMb)
            {
                _lastMb = mb;
                _status?.Invoke(_total > 0 ? $"Downloading the pack... {mb}/{_total >> 20} MB" : $"Downloading the pack... {mb} MB");
            }
        }

        public override bool CanRead => false;
        public override bool CanSeek => false;
        public override bool CanWrite => _inner.CanWrite;
        public override long Length => _inner.Length;
        public override long Position { get => _inner.Position; set => _inner.Position = value; }
        public override void Flush() => _inner.Flush();
        public override Task FlushAsync(CancellationToken cancellationToken) => _inner.FlushAsync(cancellationToken);
        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => _inner.SetLength(value);
    }

    private static async Task DownloadArchiveAsync(HttpClient http, Uri url, string archive, string sha256, Action<string>? status, CancellationToken token)
    {
        if (string.IsNullOrWhiteSpace(sha256))
            throw new InvalidDataException("A published SHA-256 checksum is required to download the pack archive.");

        var part = archive + ".part";
        try
        {
            using (var response = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, token))
            {
                response.EnsureSuccessStatusCode();
                long total = response.Content.Headers.ContentLength ?? (1L << 31);
                await using var input = await response.Content.ReadAsStreamAsync(token);
                await using var fileOutput = new FileStream(part, FileMode.Create, FileAccess.Write, FileShare.None, 81920, useAsync: true);
                await using var progressOutput = new ProgressStream(fileOutput, response.Content.Headers.ContentLength ?? 0, status);
                await ClientModInstaller.CopyWithStallTimeoutAsync(input, progressOutput, total > 0 ? total : (1L << 31), TimeSpan.FromSeconds(60), Path.GetFileName(archive), token);
            }
            string actual = await FileSha256Async(part, token);
            if (!actual.Equals(sha256, StringComparison.OrdinalIgnoreCase))
            {
                File.Delete(part);
                throw new InvalidDataException($"The downloaded pack does not match its published checksum (expected {sha256}, got {actual}).");
            }
            File.Move(part, archive, overwrite: true);
        }
        catch
        {
            try { if (File.Exists(part)) File.Delete(part); } catch { }
            throw;
        }
    }

    private static async Task<string> FileSha256Async(string path, CancellationToken token)
    {
        await using var stream = File.OpenRead(path);
        return Convert.ToHexString(await SHA256.HashDataAsync(stream, token));
    }

    private sealed record JarOverlayEntry(string Path, string OverlayZipPath, string OverlaySha256, string? PatchedSha1, string? PatchedSha512);

    private static async Task InstallManifestFilesAsync(string source, string gameDir, HttpClient http, Action<string>? status, CancellationToken token)
    {
        var indexPath = Path.Combine(source, "modrinth.index.json");
        if (!File.Exists(indexPath)) return;

        MrpackIndex index;
        await using (var stream = File.OpenRead(indexPath))
            index = Mrpack.ParseIndex(stream);

        var files = index.Files.Where(f => !f.ClientUnsupported).Select(f => (File: f, Target: Mrpack.SafePath(gameDir, f.Path))).ToList();
        if (files.DistinctBy(f => f.Target, StringComparer.OrdinalIgnoreCase).Count() != files.Count)
            throw new InvalidDataException("The modpack lists the same file twice.");

        var overlays = ReadJarOverlays(source);
        int done = 0;

        await Parallel.ForEachAsync(files, new ParallelOptions { MaxDegreeOfParallelism = 8, CancellationToken = token }, async (item, ct) =>
        {
            var target = !File.Exists(item.Target) && File.Exists(item.Target + ".disabled") ? item.Target + ".disabled" : item.Target;
            var relKey = item.File.Path.Replace('\\', '/');

            if (overlays.TryGetValue(relKey, out var overlay))
            {
                VerifyFileSha256(overlay.OverlayZipPath, overlay.OverlaySha256);
                bool alreadyPatched = File.Exists(target)
                    && (Mrpack.HashMatches(target, overlay.PatchedSha1, overlay.PatchedSha512)
                        || OverlayEntriesMatch(target, overlay.OverlayZipPath));

                if (!alreadyPatched)
                {
                    if (!(File.Exists(target) && Mrpack.HashMatches(target, item.File.Sha1, item.File.Sha512)))
                        await DownloadManifestFileAsync(http, item.File, target, ct);
                    ApplyJarOverlay(target, overlay.OverlayZipPath, overlay.OverlaySha256);
                }
            }
            else
            {
                if (!(File.Exists(target) && Mrpack.HashMatches(target, item.File.Sha1, item.File.Sha512)))
                    await DownloadManifestFileAsync(http, item.File, target, ct);
            }

            int current = Interlocked.Increment(ref done);
            status?.Invoke($"Downloading modpack files ({current}/{files.Count}): {Path.GetFileName(item.File.Path)}");
        });
    }

    private static async Task DownloadManifestFileAsync(HttpClient http, MrpackFile file, string target, CancellationToken token)
    {
        if (file.Sha1 == null && file.Sha512 == null)
            throw new InvalidDataException($"'{file.Path}' has no cryptographic hash in the modpack.");
        Directory.CreateDirectory(Path.GetDirectoryName(target)!);
        var temp = $"{target}.{Guid.NewGuid():N}.download";
        Exception? last = null;
        foreach (var url in file.Downloads)
        {
            if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps || !AllowedDownloadHosts.Contains(uri.Host))
            {
                last = new InvalidDataException($"'{url}' is not an allowed https download URL.");
                continue;
            }
            for (int attempt = 1; attempt <= 3; attempt++)
            {
                try
                {
                    using (var response = await http.GetAsync(uri, HttpCompletionOption.ResponseHeadersRead, token))
                    {
                        response.EnsureSuccessStatusCode();
                        await using var input = await response.Content.ReadAsStreamAsync(token);
                        await using var output = new FileStream(temp, FileMode.Create, FileAccess.Write, FileShare.None, 81920, useAsync: true);
                        await ClientModInstaller.CopyWithStallTimeoutAsync(input, output, file.Size ?? (1L << 31), TimeSpan.FromSeconds(60), file.Path, token);
                    }
                    if (!Mrpack.HashMatches(temp, file.Sha1, file.Sha512))
                        throw new InvalidDataException($"'{file.Path}' does not match the modpack's hash.");
                    File.Move(temp, target, overwrite: true);
                    return;
                }
                catch (InvalidDataException e)
                {
                    last = e;
                    break;
                }
                catch (Exception e) when (e is HttpRequestException or IOException or TimeoutException && !token.IsCancellationRequested)
                {
                    last = e;
                    if (attempt < 3) await Task.Delay(250 * attempt, token);
                }
                finally
                {
                    if (File.Exists(temp))
                    {
                        try { File.Delete(temp); } catch { }
                    }
                }
            }
        }
        throw new InvalidDataException($"Could not download '{file.Path}': {last?.Message ?? "the modpack gives no download for it."}", last);
    }

    private static Dictionary<string, JarOverlayEntry> ReadJarOverlays(string source)
    {
        var map = new Dictionary<string, JarOverlayEntry>(StringComparer.OrdinalIgnoreCase);
        var manifestPath = Path.Combine(source, "jar-overlays", "manifest.json");
        if (!File.Exists(manifestPath)) return map;

        using var doc = JsonDocument.Parse(File.ReadAllText(manifestPath));
        if (!doc.RootElement.TryGetProperty("overlays", out var list) || list.ValueKind != JsonValueKind.Array)
            return map;

        foreach (var item in list.EnumerateArray())
        {
            var relPath = item.TryGetProperty("path", out var p) && p.ValueKind == JsonValueKind.String ? p.GetString() : null;
            var overlayRel = item.TryGetProperty("overlayZip", out var oz) && oz.ValueKind == JsonValueKind.String ? oz.GetString() : null;
            var overlaySha256 = item.TryGetProperty("overlaySha256", out var os) && os.ValueKind == JsonValueKind.String ? os.GetString() : null;
            if (string.IsNullOrWhiteSpace(relPath) || string.IsNullOrWhiteSpace(overlayRel) || string.IsNullOrWhiteSpace(overlaySha256))
                throw new InvalidDataException("Invalid entry in jar-overlays/manifest.json.");

            var overlayFullPath = Mrpack.SafePath(source, overlayRel);
            var patchedSha1 = item.TryGetProperty("patchedSha1", out var s1) && s1.ValueKind == JsonValueKind.String ? s1.GetString() : null;
            var patchedSha512 = item.TryGetProperty("patchedSha512", out var s512) && s512.ValueKind == JsonValueKind.String ? s512.GetString() : null;
            map[relPath.Replace('\\', '/')] = new JarOverlayEntry(relPath.Replace('\\', '/'), overlayFullPath, overlaySha256, patchedSha1, patchedSha512);
        }
        return map;
    }

    private static void VerifyFileSha256(string filePath, string expectedSha256)
    {
        if (string.IsNullOrWhiteSpace(expectedSha256) || !File.Exists(filePath))
            throw new InvalidDataException($"Overlay archive '{filePath}' is missing or has no checksum.");
        using var stream = File.OpenRead(filePath);
        var actual = Convert.ToHexString(SHA256.HashData(stream));
        if (!actual.Equals(expectedSha256, StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException($"Overlay archive '{Path.GetFileName(filePath)}' failed SHA-256 verification.");
    }

    private static bool OverlayEntriesMatch(string targetJar, string overlayZipPath)
    {
        try
        {
            using var jarZip = ZipFile.OpenRead(targetJar);
            using var overlayZip = ZipFile.OpenRead(overlayZipPath);
            foreach (var entry in overlayZip.Entries)
            {
                if (entry.FullName.EndsWith('/')) continue;
                var jarEntry = jarZip.GetEntry(entry.FullName);
                if (jarEntry == null || jarEntry.Length != entry.Length) return false;
                using var s1 = entry.Open();
                using var s2 = jarEntry.Open();
                using var ms1 = new MemoryStream();
                using var ms2 = new MemoryStream();
                s1.CopyTo(ms1);
                s2.CopyTo(ms2);
                if (!ms1.GetBuffer().AsSpan(0, (int)ms1.Length).SequenceEqual(ms2.GetBuffer().AsSpan(0, (int)ms2.Length)))
                    return false;
            }
            return true;
        }
        catch
        {
            return false;
        }
    }

    private static void ApplyJarOverlay(string targetJar, string overlayZipPath, string expectedOverlaySha256)
    {
        VerifyFileSha256(overlayZipPath, expectedOverlaySha256);
        var staged = $"{targetJar}.{Guid.NewGuid():N}.overlay.tmp";
        try
        {
            File.Copy(targetJar, staged, overwrite: true);
            using (var jarZip = ZipFile.Open(staged, ZipArchiveMode.Update))
            using (var overlayZip = ZipFile.OpenRead(overlayZipPath))
            {
                foreach (var entry in overlayZip.Entries)
                {
                    var name = entry.FullName.Replace('\\', '/');
                    if (name.EndsWith('/')) continue;
                    if (name.StartsWith('/') || name.Contains(".."))
                        throw new InvalidDataException($"Unsafe entry '{name}' in jar overlay.");
                    jarZip.GetEntry(entry.FullName)?.Delete();
                    jarZip.GetEntry(name)?.Delete();
                    var dst = jarZip.CreateEntry(name, CompressionLevel.Optimal);
                    using var srcStream = entry.Open();
                    using var dstStream = dst.Open();
                    srcStream.CopyTo(dstStream);
                }
            }
            File.Move(staged, targetJar, overwrite: true);
        }
        finally
        {
            if (File.Exists(staged))
            {
                try { File.Delete(staged); } catch { }
            }
        }
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

    private sealed record PackInfo(string? Version, IReadOnlyList<string> AppliedPatches, bool IncludeLadsCore = false);

    private static PackInfo ReadPackInfo(string source)
    {
        try
        {
            using var json = JsonDocument.Parse(File.ReadAllText(Path.Combine(source, "pack.json")));
            var root = json.RootElement;
            var version = root.TryGetProperty("packVersion", out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;
            var applied = root.TryGetProperty("appliedPatches", out var a) && a.ValueKind == JsonValueKind.Array
                ? a.EnumerateArray().Where(e => e.ValueKind == JsonValueKind.String).Select(e => e.GetString()!).ToList() : new List<string>();
            bool includeLadsCore = root.TryGetProperty("includeLadsCore", out var lc) && lc.ValueKind == JsonValueKind.True;
            return new PackInfo(version, applied, includeLadsCore);
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
