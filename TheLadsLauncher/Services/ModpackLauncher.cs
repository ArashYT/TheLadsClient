using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.Http;
using System.Linq;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using CmlLib.Core;
using CmlLib.Core.Auth;
using CmlLib.Core.ModLoaders.FabricMC;
using CmlLib.Core.ModLoaders.QuiltMC;
using CmlLib.Core.ProcessBuilder;

namespace TheLadsLauncher.Services;

/// <summary>
/// Installs modpacks as instances and starts them: vanilla plus the pack's loader, never the Lads pack (a Lads Core a pack already
/// contains is kept at the launcher's newest build, see ModpackCoreSync). Libraries,
/// assets and versions are shared by every instance in &lt;launcher data&gt;/modpack-cache; each game folder is its own.
/// </summary>
public static class ModpackLauncher
{
    public static string CacheRoot(string dataDirectory) => Path.Combine(dataDirectory, "modpack-cache");

    public static MinecraftPath GamePath(string dataDirectory, ModpackInstance instance)
    {
        var cache = CacheRoot(dataDirectory);
        return new MinecraftPath(instance.GameDirectory)
        {
            Library = Path.Combine(cache, "libraries"),
            Versions = Path.Combine(cache, "versions"),
            Assets = Path.Combine(cache, "assets"),
            Runtime = Path.Combine(cache, "runtime")
        };
    }

    /// <summary>A new instance from a .mrpack (an import, or a Modrinth download with its <paramref name="source"/>), named
    /// <paramref name="name"/> (the Modrinth project's title) or else as the pack names itself.</summary>
    public static async Task<ModpackInstance> InstallAsync(string dataDirectory, string mrpackFile, ModpackSource? source, string? name, string? author,
        string? iconUrl, HttpClient http, Action<string>? status, CancellationToken token)
    {
        var index = Mrpack.ReadIndex(mrpackFile);
        var instance = Modpacks.NewInstance(dataDirectory, string.IsNullOrWhiteSpace(name) ? index.Name : name);
        try
        {
            await Task.Run(() => Mrpack.InstallAsync(mrpackFile, instance.Directory, http, status, token), token); // off the UI thread: it hashes and copies every file
            Apply(instance, index, source);
            ModpackCoreSync.Sync(instance, status: status); // a Core inside the pack is brought to this launcher's build
            instance.Author = author;
            if (iconUrl != null && Uri.TryCreate(iconUrl, UriKind.Absolute, out var icon) && icon.Scheme == Uri.UriSchemeHttps)
            {
                try
                {
                    await File.WriteAllBytesAsync(Path.Combine(instance.Directory, "icon.png"), await http.GetByteArrayAsync(icon, token), token);
                    instance.IconPath = "icon.png";
                }
                catch (Exception e) when (e is HttpRequestException or IOException or UnauthorizedAccessException or TaskCanceledException && !token.IsCancellationRequested)
                {
                    // The card shows its placeholder: a stalled icon (HttpClient's timeout is a TaskCanceledException) is no reason to throw the finished install away.
                }
            }
            Modpacks.Save(instance);
            return instance;
        }
        catch
        {
            SafeFileOps.DeleteTree(instance.Directory); // our own new folder: nothing of the user's is in it yet
            throw;
        }
    }

    /// <summary>A new instance from a CurseForge modpack .zip archive.</summary>
    public static async Task<ModpackInstance> InstallCurseForgeAsync(string dataDirectory, string zipFile, ContentCatalog? catalog,
        string? name, string? author, Action<string>? status, CancellationToken token)
    {
        var manifest = CurseForgeModpack.ReadManifest(zipFile);
        var instance = Modpacks.NewInstance(dataDirectory, string.IsNullOrWhiteSpace(name) ? manifest.Name : name);
        try
        {
            await Task.Run(() => CurseForgeModpack.InstallAsync(zipFile, instance.GameDirectory, catalog, status ?? (_ => { }), token), token);
            instance.McVersion = manifest.MinecraftVersion;
            instance.Loader = manifest.ModLoader;
            instance.LoaderVersion = manifest.ModLoaderVersion;
            instance.PackVersion = manifest.Version;
            instance.Author = author ?? manifest.Author;
            ModpackCoreSync.Sync(instance, status: status);
            Modpacks.Save(instance);
            return instance;
        }
        catch
        {
            SafeFileOps.DeleteTree(instance.Directory);
            throw;
        }
    }

    // What the launcher itself keeps in an instance folder beside the game folder; anything else there is from a pack.
    private static readonly string[] InstanceOwnFiles = { "minecraft", "instance.json", "instance.json.tmp", "icon.png", Mrpack.RecordFile,
        ".lads-applied-patches.json", "instance-analytics.json" };

    /// <summary>
    /// CurseForge imports before 1.12.0 put the pack's mods and overrides in the instance folder (&lt;instance&gt;/mods, /config, ...)
    /// instead of in its game folder, so the game started without them. Moves them in, merging with what the game folder has: anything
    /// that already exists there stays where it is, with its twin outside, so nothing is replaced or deleted. Only an instance with an
    /// &lt;instance&gt;/mods folder is touched (nothing else makes one). The Core setting that import saved as "unsupported" (it looked
    /// at an empty game folder) is decided again once everything is in; a move that fails is tried again the next time.
    /// </summary>
    public static void RepairCurseForgeLayout(ModpackInstance instance)
    {
        var misplacedMods = Path.Combine(instance.Directory, "mods");
        if (instance.IsExternal || !Directory.Exists(misplacedMods)) return;
        try
        {
            Directory.CreateDirectory(instance.GameDirectory);
            foreach (var entry in Directory.GetFileSystemEntries(instance.Directory)
                .OrderBy(e => Path.GetFileName(e).Equals("mods", StringComparison.OrdinalIgnoreCase) ? 1 : 0))
                if (!InstanceOwnFiles.Contains(Path.GetFileName(entry), StringComparer.OrdinalIgnoreCase))
                    MoveInto(entry, Path.Combine(instance.GameDirectory, Path.GetFileName(entry)));
            if (instance.LadsCore == false && !Directory.Exists(misplacedMods))
            {
                instance.LadsCore = null;
                Modpacks.Save(instance);
            }
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException) { }
    }

    // Moves source to target; when both are folders their entries are merged. A file that is already at target stays, and so does source.
    private static void MoveInto(string source, string target)
    {
        bool folder = Directory.Exists(source) && !SafeFileOps.IsLink(source); // a link is moved as the link it is, never entered
        if (folder && Directory.Exists(target))
        {
            foreach (var entry in Directory.GetFileSystemEntries(source)) MoveInto(entry, Path.Combine(target, Path.GetFileName(entry)));
            try { Directory.Delete(source); } catch (IOException) { } // only an empty folder goes
        }
        else if (!File.Exists(target) && !Directory.Exists(target))
        {
            if (Directory.Exists(source)) Directory.Move(source, target);
            else File.Move(source, target);
        }
    }

    /// <summary>Updates an instance to another version of its pack; saves, options and the user's own files stay.</summary>
    public static async Task UpdateAsync(ModpackInstance instance, string mrpackFile, ModpackSource source, HttpClient http,
        Action<string>? status, CancellationToken token)
    {
        var index = await Task.Run(() => Mrpack.InstallAsync(mrpackFile, instance.Directory, http, status, token), token);
        Apply(instance, index, source);
        ModpackCoreSync.Sync(instance, status: status);
        Modpacks.Save(instance);
    }

    private static void Apply(ModpackInstance instance, MrpackIndex index, ModpackSource? source)
    {
        instance.McVersion = index.MinecraftVersion;
        instance.Loader = index.Loader;
        instance.LoaderVersion = index.LoaderVersion;
        instance.PackVersion = index.VersionId;
        instance.Source = source;
    }

    /// <summary>Installs what the instance needs and starts it with <paramref name="session"/> and the Java its version needs.
    /// <paramref name="joinServer"/> ("host" or "host:port"): the game joins it as soon as it has loaded; <paramref name="joinWorld"/>
    /// (a saves folder name): it opens that world.</summary>
    public static async Task<Process> LaunchAsync(string dataDirectory, ModpackInstance instance, MSession session, LauncherSettings settings,
        IJavaService java, HttpClient http, Action<string>? status, CancellationToken token, string? joinServer = null, string? joinWorld = null)
    {
        var process = await PrepareAsync(dataDirectory, instance, session, settings, java, http, status, token, joinServer, joinWorld);
        token.ThrowIfCancellationRequested();
        process.EnableRaisingEvents = true;
        if (!instance.IsExternal) process.Exited += (_, _) => SyncLadsConfigFromInstance(instance.GameDirectory, dataDirectory);
        process.Start();
        LaunchToken.Send(process);
        instance.LastPlayedUtc = DateTime.UtcNow;
        if (!instance.IsExternal) Modpacks.Save(instance);
        return process;
    }

    private static void SyncLadsConfigToInstance(string dataDirectory, string gameDirectory)
    {
        try
        {
            Directory.CreateDirectory(gameDirectory);
            SharedContentService.SyncLadsSettingsAcrossAllInstances(dataDirectory, gameDirectory, backgroundFanOut: true);
            var sharedConfig = Path.Combine(dataDirectory, "shared", "thelads_config.json");
            var instanceConfig = Path.Combine(gameDirectory, "thelads_config.json");
            if (File.Exists(sharedConfig))
            {
                if (!File.Exists(instanceConfig) || File.GetLastWriteTimeUtc(sharedConfig) > File.GetLastWriteTimeUtc(instanceConfig))
                {
                    File.Copy(sharedConfig, instanceConfig, true);
                }
            }
            else if (File.Exists(instanceConfig))
            {
                Directory.CreateDirectory(Path.GetDirectoryName(sharedConfig)!);
                File.Copy(instanceConfig, sharedConfig, true);
            }
        }
        catch { }
    }

    private static void SyncLadsConfigFromInstance(string gameDirectory, string dataDirectory)
    {
        try
        {
            var sharedConfig = Path.Combine(dataDirectory, "shared", "thelads_config.json");
            var rootConfig = Path.Combine(dataDirectory, "thelads_config.json");
            var instanceConfig = Path.Combine(gameDirectory, "thelads_config.json");
            if (File.Exists(instanceConfig) && !SharedContentService.IsDatedInTheFuture(File.GetLastWriteTimeUtc(instanceConfig)))
            {
                if (!File.Exists(sharedConfig) || File.GetLastWriteTimeUtc(instanceConfig) >= File.GetLastWriteTimeUtc(sharedConfig))
                {
                    Directory.CreateDirectory(Path.GetDirectoryName(sharedConfig)!);
                    File.Copy(instanceConfig, sharedConfig, true);
                    File.Copy(instanceConfig, rootConfig, true);
                }
            }
            SharedContentService.SyncLadsSettingsAcrossAllInstances(dataDirectory, gameDirectory);
        }
        catch { }
    }

    /// <summary>Everything <see cref="LaunchAsync"/> does short of starting the game: Java, loader, game files; the process to start.</summary>
    public static async Task<Process> PrepareAsync(string dataDirectory, ModpackInstance instance, MSession session, LauncherSettings settings,
        IJavaService java, HttpClient http, Action<string>? status, CancellationToken token, string? joinServer = null, string? joinWorld = null)
    {
        var path = GamePath(dataDirectory, instance);
        // Another launcher's instance (CurseForge, Prism, Modrinth): only the game writes to its folder, so it stays as that launcher
        // left it. None of the Lads steps below that write there run (config sync, servers.dat, Lads Core, renderer, fml.toml, switches).
        bool own = !instance.IsExternal;
        if (own)
        {
            Directory.CreateDirectory(instance.GameDirectory);
            SyncLadsConfigToInstance(dataDirectory, instance.GameDirectory);
            var exp = ExperiencesService.AllExperiences.FirstOrDefault(e => e.Id == ExperiencesService.CanonicalPackId(instance.Id));
            if (exp != null && !string.IsNullOrWhiteSpace(exp.ServerAddress))
            {
                ServerListService.EnsureServer(Path.Combine(instance.GameDirectory, "servers.dat"), exp.Title, exp.ServerAddress);
            }
            ModpackCoreSync.Sync(instance, status: status); // before every launch: a modpack always runs the newest bundled Lads Core
            // As profiles: Vulkan unless the launcher setting or the in-game shader toggle says OpenGL; Iris and its add-ons sit out on Vulkan.
            if (GraphicsRenderer.SupportsVulkan(instance.McVersion))
                await GraphicsRenderer.PrepareAsync(instance.GameDirectory, instance.McVersion,
                    GraphicsRenderer.Preference(instance.GameDirectory, settings.GraphicsRenderer), status, token, renameJars: true);
        }
        var launcher = new MinecraftLauncher(path);
        launcher.FileProgressChanged += (_, e) =>
        {
            if (e.TotalTasks > 0) status?.Invoke($"Downloading game files {e.ProgressedTasks}/{e.TotalTasks}...");
        };

        status?.Invoke($"Resolving Minecraft {instance.McVersion}...");
        int? manifestJava = TryReadLocalJavaMajor(launcher.MinecraftPath, instance.McVersion);
        if (manifestJava == null && !File.Exists(Path.Combine(launcher.MinecraftPath.Versions, instance.McVersion, instance.McVersion + ".json")))
        {
            var vanilla = await launcher.GetVersionAsync(instance.McVersion, token);
            manifestJava = int.TryParse(vanilla.JavaVersion?.MajorVersion, out var major) ? major : null;
        }
        int requiredJava = GameVersionPolicy.GetRequiredJavaMajor(instance.McVersion, manifestJava);
        // As the Lads launch: an external instance's own Java when it is exactly the version's (ExternalInstances.ToModpack keeps only
        // plain installs), the Java set in Settings when it is, otherwise one picked for the version.
        string javaPath = instance.JavaPath is { } pinned && java.GetJavaMajorVersion(pinned) == requiredJava ? pinned
            : !settings.AutoDetectJava && File.Exists(settings.JavaPath) && java.GetJavaMajorVersion(settings.JavaPath) == requiredJava
            ? settings.JavaPath
            : await java.EnsureJavaAsync(requiredJava, null, status, token);

        var versionId = await InstallLoaderAsync(launcher, dataDirectory, instance, javaPath, http, status, token);

        status?.Invoke("Checking game files...");
        int ram = instance.MaxRamMb > 0 ? instance.MaxRamMb : Math.Max(1024, settings.MaxRamMb);
        var options = new MLaunchOption
        {
            Session = session,
            JavaPath = javaPath,
            MaximumRamMb = ram,
            MinimumRamMb = Math.Clamp(instance.MinRamMb > 0 ? instance.MinRamMb : settings.MinRamMb, 256, ram),
            FullScreen = settings.FullscreenOnLaunch
        };
        if (instance.JvmArguments.Count > 0) options.ExtraJvmArguments = new[] { new MArgument(instance.JvmArguments.ToList()) };
        if (!string.IsNullOrWhiteSpace(joinServer)) JoinServer(options, joinServer);
        if (!string.IsNullOrWhiteSpace(joinWorld)) JoinWorld(options, joinWorld);
        LimitBackgroundThreads(instance, options, Environment.ProcessorCount);
        // Lads Core leaves the client-only extras out of a modpack (the title screen's "The Lads Network" button).
        options.ExtraJvmArguments = (options.ExtraJvmArguments ?? Array.Empty<MArgument>()).Append(MArgument.FromCommandLine("-Dlads.modpack=true"));
        LaunchService.ConfigureCachedNativesDirectory(launcher.MinecraftPath, versionId, options);
        // Agent check and the security flags always; the Lads GC tuning only when the instance brings no JVM arguments of its own (two
        // collectors on one command line stop the JVM), and no clean-up of stray files in a folder that is not ours.
        LaunchService.EnsureSecurityJvmArguments(options, instance.JvmArguments.Count > 0 ? null : requiredJava, own ? instance.GameDirectory : null, instance.McVersion);
        // NEEDS CLAUDE: Wire EnsureLoadingScreenColors (with hex validation) into modpack launch path.

        string verifiedMarker = Path.Combine(launcher.MinecraftPath.Versions, versionId, ".lads-verified");
        Process process;
        if (settings.QuickLaunch && File.Exists(verifiedMarker))
        {
            try
            {
                process = await launcher.BuildProcessAsync(versionId, options, token);
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                status?.Invoke($"Quick launch fallback to full verification: {ex.Message}");
                process = await FullInstallAndBuildAsync(launcher, versionId, options, verifiedMarker, token);
            }
        }
        else
        {
            process = await FullInstallAndBuildAsync(launcher, versionId, options, verifiedMarker, token);
        }

        LaunchService.MarkCachedNativesReady(launcher.MinecraftPath, versionId);
        process.StartInfo.UseShellExecute = false;
        process.StartInfo.CreateNoWindow = true;
        process.StartInfo.Environment["THELADS_GLOBAL_DIR"] = dataDirectory;
        process.StartInfo.Environment["THELADS_DIR"] = instance.GameDirectory;
        GameSession.RemoveJavaOptionVariables(process.StartInfo);
        LaunchToken.Hide(process, instance.GameDirectory, session.AccessToken); // the caller sends it after Start
        if (!own) return process;
        if (instance.Loader == "forge" && instance.McVersion == "1.20.1")
            UseEarlyWindow(process, dataDirectory, instance.GameDirectory,
                Environment.GetEnvironmentVariable(SkipEarlyWindowVariable) == "1" ? null : ModpackCoreSync.FindBundledJar("1.20.1", fileName: EarlyWindowJar), status);
        try { VersionSwitch.Prepare(instance.GameDirectory, instance.Name); } // the in-game Switch Versions screen offers the Lads Client versions
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { status?.Invoke($"Could not write {VersionSwitch.VersionsFile}: {ex.Message}"); }
        try { AccountSwitch.Prepare(instance.GameDirectory); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { status?.Invoke($"Could not prepare {AccountSwitch.RequestFile}: {ex.Message}"); }
        return process;
    }

    private static async Task<Process> FullInstallAndBuildAsync(MinecraftLauncher launcher, string versionId, MLaunchOption options, string verifiedMarker, CancellationToken token)
    {
        await launcher.GetAllVersionsAsync(token); // pick up the loader's manifest
        try
        {
            var process = await launcher.InstallAndBuildProcessAsync(versionId, options, token);
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(verifiedMarker)!);
                await File.WriteAllTextAsync(verifiedMarker, DateTimeOffset.UtcNow.ToString("O"), token);
            }
            catch (IOException) { }
            return process;
        }
        catch (Exception ex) when ((ex is IOException or UnauthorizedAccessException)
            && File.Exists(Path.Combine(launcher.MinecraftPath.Versions, versionId, versionId + ".json")))
        {
            // Another running copy of the instance may hold native DLLs or libraries open in modpack-cache; build the process directly.
            return await launcher.BuildProcessAsync(versionId, options, token);
        }
    }

    private static int? TryReadLocalJavaMajor(MinecraftPath minecraftPath, string mcVersion)
    {
        var jsonPath = Path.Combine(minecraftPath.Versions, mcVersion, mcVersion + ".json");
        if (!File.Exists(jsonPath)) return null;
        try
        {
            using var doc = JsonDocument.Parse(File.ReadAllText(jsonPath));
            if (doc.RootElement.TryGetProperty("javaVersion", out var jv)
                && jv.TryGetProperty("majorVersion", out var mv))
            {
                if (mv.ValueKind == JsonValueKind.Number && mv.TryGetInt32(out int major))
                    return major;
                if (mv.ValueKind == JsonValueKind.String && int.TryParse(mv.GetString(), out int parsed))
                    return parsed;
            }
        }
        catch { }
        return null;
    }

    /// <summary>The Lads early loading window for Forge 1.20.1 packs (TheLadsCore/forge_earlywindow), bundled in game-mods/1.20.1.</summary>
    public const string EarlyWindowJar = "lads-earlywindow.jar";

    /// <summary>Set to 1 to start Forge 1.20.1 packs with Forge's own early window, exactly as when this launcher has no
    /// <see cref="EarlyWindowJar"/>.</summary>
    public const string SkipEarlyWindowVariable = "LADS_SKIP_EARLY_WINDOW";

    /// <summary>
    /// Forge loads its early window provider from the boot module layer, before mods/ is scanned, so the Lads one goes on the
    /// game's -cp and config/fml.toml names it. Without a jar (<paramref name="bundledJar"/> null) Forge keeps its own window: a
    /// "ladsearlywindow" an earlier launch wrote is set back, since Forge shows no early window at all for a provider it cannot find.
    /// </summary>
    public static void UseEarlyWindow(Process process, string dataDirectory, string gameDirectory, string? bundledJar, Action<string>? status)
    {
        string? jar = null;
        try { if (bundledJar != null) jar = StageEarlyWindowJar(dataDirectory, bundledJar); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { status?.Invoke($"Could not prepare the Lads loading screen ({e.Message}); Forge's is used."); }
        if (jar != null && !AppendClassPath(process.StartInfo, jar)) jar = null;
        var fml = Path.Combine(gameDirectory, "config", "fml.toml");
        try
        {
            string? toml = File.Exists(fml) ? File.ReadAllText(fml) : null;
            string? edited = WithEarlyWindow(toml, jar != null);
            if (edited == null || edited == toml) return;
            Directory.CreateDirectory(Path.GetDirectoryName(fml)!);
            File.WriteAllText(fml, edited);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { status?.Invoke($"Could not write {fml}: {e.Message}"); }
    }

    /// <summary>The copy the game runs, in the modpack cache under its hash: a running game never holds a file of the launcher's
    /// install folder open (an update replaces that folder), and a newer launcher's jar never overwrites one a running game uses.</summary>
    private static string StageEarlyWindowJar(string dataDirectory, string bundledJar)
    {
        string sha = ModpackCoreSync.GetSha256(bundledJar);
        var staged = Path.Combine(CacheRoot(dataDirectory), "lads-earlywindow", sha[..16], EarlyWindowJar);
        var bundledInfo = new FileInfo(bundledJar);
        var stagedInfo = new FileInfo(staged);
        if (stagedInfo.Exists && stagedInfo.Length == bundledInfo.Length) return staged;
        Directory.CreateDirectory(Path.GetDirectoryName(staged)!);
        File.Copy(bundledJar, staged + ".tmp", overwrite: true);
        File.Move(staged + ".tmp", staged, overwrite: true);
        return staged;
    }

    /// <summary>fml.toml (null: no file yet) with the Lads early window on (<paramref name="lads"/>), or with Forge's own provider
    /// back in place of a Lads one. Keys missing from the file go first (top level; Forge fills in the rest on its first start).</summary>
    public static string? WithEarlyWindow(string? toml, bool lads)
    {
        if (!lads) return toml == null ? null : Regex.Replace(toml, @"(?m)^([ \t]*earlyWindowProvider[ \t]*=[ \t]*)""ladsearlywindow""", "${1}\"fmlearlywindow\"");
        toml ??= "";
        foreach (var (key, value) in new[] { ("earlyWindowControl", "true"), ("earlyWindowProvider", "\"ladsearlywindow\"") })
        {
            var line = new Regex($@"(?m)^([ \t]*{key}[ \t]*=[ \t]*)[^\r\n]*");
            toml = line.IsMatch(toml) ? line.Replace(toml, "${1}" + value, 1) : $"{key} = {value}\n" + toml;
        }
        return toml;
    }

    /// <summary>Adds <paramref name="jar"/> to the game's -cp; false when the command line has none. CmlLib builds one Arguments
    /// string (-cp "a;b;c" or -cp a;b;c); an ArgumentList is handled too.</summary>
    public static bool AppendClassPath(ProcessStartInfo start, string jar)
    {
        var list = start.ArgumentList;
        int cp = list.IndexOf("-cp");
        if (cp >= 0 && cp + 1 < list.Count)
        {
            list[cp + 1] += Path.PathSeparator + jar;
            return true;
        }
        if (list.Count > 0) return false;
        var match = Regex.Match(start.Arguments, @"(?<=(^|\s)-cp\s+)(""[^""]*""|\S+)");
        if (!match.Success) return false;
        start.Arguments = start.Arguments[..match.Index] + "\"" + match.Value.Trim('"') + Path.PathSeparator + jar + "\"" + start.Arguments[(match.Index + match.Length)..];
        return true;
    }

    /// <summary>Forge 1.20.1 packs (Zero SMP) cap the game's background worker pool at half the logical CPUs (at least 2): on the laptop that
    /// lifted the 1% low from 22 to 45 fps (Distant Horizons off) and from 10 to 16 (on). Other packs, and a <c>-Dmax.bg.threads</c>
    /// already among the options' JVM arguments, are left alone.</summary>
    public static void LimitBackgroundThreads(ModpackInstance instance, MLaunchOption options, int processorCount)
    {
        var existing = options.ExtraJvmArguments ?? Array.Empty<MArgument>();
        if (instance.Loader != "forge" || instance.McVersion != "1.20.1"
            || existing.Any(a => a.Values.Any(v => v.StartsWith("-Dmax.bg.threads", StringComparison.Ordinal)))) return;
        options.ExtraJvmArguments = existing.Append(MArgument.FromCommandLine($"-Dmax.bg.threads={Math.Max(2, processorCount / 2)}"));
    }

    /// <summary>The server the built game joins at start (its --quickPlayMultiplayer value), or null when it does not.</summary>
    public static string? QuickPlayServer(Process process)
    {
        var arguments = process.StartInfo.ArgumentList.Count > 0 ? process.StartInfo.ArgumentList.ToList() : Regex.Split(process.StartInfo.Arguments, @"\s+").ToList();
        int index = arguments.IndexOf("--quickPlayMultiplayer");
        return index >= 0 && index + 1 < arguments.Count ? arguments[index + 1] : null;
    }

    /// <summary>Makes the game join <paramref name="address"/> ("host" or "host:port") as soon as it has loaded.</summary>
    public static void JoinServer(MLaunchOption options, string address)
    {
        address = address.Trim();
        int colon = address.LastIndexOf(':');
        if (colon > 0 && int.TryParse(address.AsSpan(colon + 1), out int port))
        {
            options.ServerIp = address[..colon];
            options.ServerPort = port;
        }
        else options.ServerIp = address;
    }

    /// <summary>Makes the game open the singleplayer world saves/<paramref name="world"/> as soon as it has loaded
    /// (--quickPlaySingleplayer).</summary>
    public static void JoinWorld(MLaunchOption options, string world) => options.QuickPlaySingleplayer = world;

    /// <summary>The loader's version id, installed into the shared cache if it is not there yet.</summary>
    private static async Task<string> InstallLoaderAsync(MinecraftLauncher launcher, string dataDirectory, ModpackInstance instance, string javaPath,
        HttpClient http, Action<string>? status, CancellationToken token)
    {
        string mc = instance.McVersion, loader = instance.LoaderVersion;
        if (instance.Loader != "vanilla") status?.Invoke($"Installing {instance.Loader} {loader} for Minecraft {mc}...");
        switch (instance.Loader)
        {
            case "vanilla":
                return mc;
            case "fabric":
                var fabricVersionId = $"fabric-loader-{loader}-{mc}";
                if (ValidateVersionJsonLibraries(launcher.MinecraftPath, fabricVersionId))
                    return fabricVersionId;
                return await new FabricInstaller(http).Install(mc, loader, launcher.MinecraftPath);
            case "quilt":
                var quiltVersionId = $"quilt-loader-{loader}-{mc}";
                if (ValidateVersionJsonLibraries(launcher.MinecraftPath, quiltVersionId))
                    return quiltVersionId;
                return await new QuiltInstaller(http).Install(mc, loader, launcher.MinecraftPath);
            case "forge" when mc == GameVersionPolicy.ForgeMinecraftVersion && loader == GameVersionPolicy.ForgeBuild:
                await LaunchService.InstallForgeAsync(launcher, CacheRoot(dataDirectory), http, status, token); // the Lads 1.8.9 build, pinned
                return GameVersionPolicy.ForgeVersionId;
            case "forge":
                return await InstallWithOfficialInstallerAsync(launcher, dataDirectory, javaPath, http, token,
                    $"https://maven.minecraftforge.net/net/minecraftforge/forge/{mc}-{loader}/forge-{mc}-{loader}-installer.jar",
                    // Builds up to 1.9 carry the game version twice in their maven coordinates.
                    $"https://maven.minecraftforge.net/net/minecraftforge/forge/{mc}-{loader}-{mc}/forge-{mc}-{loader}-{mc}-installer.jar");
            case "neoforge":
                return await InstallWithOfficialInstallerAsync(launcher, dataDirectory, javaPath, http, token, mc == "1.20.1"
                    ? $"https://maven.neoforged.net/releases/net/neoforged/forge/1.20.1-{loader}/forge-1.20.1-{loader}-installer.jar"
                    : $"https://maven.neoforged.net/releases/net/neoforged/neoforge/{loader}/neoforge-{loader}-installer.jar");
            default:
                throw new InvalidOperationException($"Unknown loader '{instance.Loader}'.");
        }
    }

    /// <summary>
    /// Verifies that the local version manifest JSON exists and every library jar it references is present in the library cache.
    /// </summary>
    public static bool ValidateVersionJsonLibraries(MinecraftPath minecraftPath, string versionId)
    {
        if (minecraftPath == null || string.IsNullOrWhiteSpace(versionId)) return false;
        var manifestPath = Path.Combine(minecraftPath.Versions, versionId, versionId + ".json");
        if (!File.Exists(manifestPath)) return false;
        try
        {
            using var doc = JsonDocument.Parse(File.ReadAllText(manifestPath));
            if (!doc.RootElement.TryGetProperty("libraries", out var libraries) || libraries.ValueKind != JsonValueKind.Array)
                return false;
            foreach (var lib in libraries.EnumerateArray())
            {
                if (lib.TryGetProperty("downloads", out var downloads)
                    && downloads.TryGetProperty("artifact", out var artifact)
                    && artifact.TryGetProperty("path", out var pathProp)
                    && pathProp.GetString() is { Length: > 0 } relPath)
                {
                    var fullLibPath = Path.Combine(minecraftPath.Library, relPath.Replace('/', Path.DirectorySeparatorChar));
                    if (!File.Exists(fullLibPath)) return false;
                    continue;
                }
                if (lib.TryGetProperty("name", out var nameProp) && nameProp.GetString() is { Length: > 0 } mavenCoord)
                {
                    var parts = mavenCoord.Split(':');
                    if (parts.Length < 3) return false;
                    string group = parts[0].Replace('.', Path.DirectorySeparatorChar);
                    string artifactId = parts[1];
                    string version = parts[2];
                    string fileName = parts.Length >= 4 && !string.IsNullOrEmpty(parts[3])
                        ? $"{artifactId}-{version}-{parts[3]}.jar"
                        : $"{artifactId}-{version}.jar";
                    var fullLibPath = Path.Combine(minecraftPath.Library, group, artifactId, version, fileName);
                    if (!File.Exists(fullLibPath)) return false;
                }
            }
            return true;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>Installer jars run on players' PCs, so each is checked before it is kept: builds Lads ships (Zero SMP) against a
    /// SHA-256 pinned here, any other build against the SHA-1 its maven publishes beside it.</summary>
    public static readonly Dictionary<string, string> PinnedInstallerSha256 = new(StringComparer.OrdinalIgnoreCase)
    {
        ["forge-1.20.1-47.4.10-installer.jar"] = "1912760b4cb6b803d8a826de603c9076b1da71ec2765e9a1f8c1ca78f65278e3",
    };

    private static string Sha256(byte[] bytes) => Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(bytes)).ToLowerInvariant();

    public static async Task VerifyInstallerAsync(HttpClient http, string url, byte[] bytes, CancellationToken token)
    {
        string name = Path.GetFileName(new Uri(url).AbsolutePath);
        if (PinnedInstallerSha256.TryGetValue(name, out var pinned))
        {
            if (Sha256(bytes) != pinned) throw new InvalidDataException($"{name} does not match its pinned SHA-256, so it was not run.");
            return;
        }
        string published = (await http.GetStringAsync(url + ".sha1", token)).Trim().Split(' ')[0].ToLowerInvariant();
        if (Convert.ToHexString(System.Security.Cryptography.SHA1.HashData(bytes)).ToLowerInvariant() != published)
            throw new InvalidDataException($"{name} does not match the checksum its maven publishes, so it was not run.");
    }

    /// <summary>
    /// Forge and NeoForge from their official installer jar (kept in the cache). Installers with processors (Forge 1.13+,
    /// NeoForge) run headless with --installClient into the cache; older Forge installers carry the version manifest and the
    /// Forge library, which are written directly, as for the Lads 1.8.9 build.
    /// </summary>
    private static async Task<string> InstallWithOfficialInstallerAsync(MinecraftLauncher launcher, string dataDirectory, string javaPath,
        HttpClient http, CancellationToken token, params string[] urls)
    {
        var cache = CacheRoot(dataDirectory);
        var installers = Path.Combine(cache, "installers");
        Directory.CreateDirectory(installers);
        var jar = Path.Combine(installers, Path.GetFileName(new Uri(urls[0]).AbsolutePath));
        if (File.Exists(jar) && PinnedInstallerSha256.TryGetValue(Path.GetFileName(jar), out var pinned) && ModpackCoreSync.GetSha256(jar) != pinned)
            File.Delete(jar); // a cached copy that is not the pinned build is fetched again
        if (!File.Exists(jar))
        {
            byte[]? bytes = null;
            foreach (var url in urls)
            {
                using var response = await http.GetAsync(url, token);
                if (response.StatusCode == HttpStatusCode.NotFound) continue;
                response.EnsureSuccessStatusCode();
                bytes = await response.Content.ReadAsByteArrayAsync(token);
                await VerifyInstallerAsync(http, url, bytes, token);
                break;
            }
            if (bytes == null) throw new InvalidOperationException($"The loader installer was not found at {urls[0]}. This loader version may not exist.");
            await File.WriteAllBytesAsync(jar + ".tmp", bytes, token);
            File.Move(jar + ".tmp", jar, overwrite: true);
        }

        string versionId;
        using (var zip = ZipFile.OpenRead(jar))
        using (var profile = JsonDocument.Parse(zip.GetEntry("install_profile.json")?.Open() ?? throw new InvalidDataException("Not a Forge installer.")))
        {
            if (profile.RootElement.TryGetProperty("versionInfo", out var versionInfo))
            {
                // Legacy installer: manifest and universal jar, as LaunchService writes the 1.8.9 build.
                var install = profile.RootElement.GetProperty("install");
                versionId = install.GetProperty("target").GetString()!;
                var manifest = Path.Combine(launcher.MinecraftPath.Versions, versionId, versionId + ".json");
                if (File.Exists(manifest)) return versionId;
                var coordinates = install.GetProperty("path").GetString()!.Split(':'); // group:artifact:version
                var library = Path.Combine(launcher.MinecraftPath.Library, Path.Combine(coordinates[0].Split('.')), coordinates[1], coordinates[2],
                    $"{coordinates[1]}-{coordinates[2]}.jar");
                Directory.CreateDirectory(Path.GetDirectoryName(library)!);
                zip.GetEntry(install.GetProperty("filePath").GetString()!)!.ExtractToFile(library, overwrite: true);
                Directory.CreateDirectory(Path.GetDirectoryName(manifest)!);
                await File.WriteAllTextAsync(manifest, versionInfo.GetRawText().Replace("http://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/"), token);
                return versionId;
            }
            using var version = JsonDocument.Parse(zip.GetEntry("version.json")!.Open());
            versionId = version.RootElement.GetProperty("id").GetString()!;
        }
        if (File.Exists(Path.Combine(launcher.MinecraftPath.Versions, versionId, versionId + ".json"))) return versionId;

        // The installer refuses a folder without the official launcher's profile list.
        var profiles = Path.Combine(cache, "launcher_profiles.json");
        if (!File.Exists(profiles)) await File.WriteAllTextAsync(profiles, "{\"profiles\":{}}", token);
        var log = jar + ".log";
        var start = new ProcessStartInfo(javaPath) { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
        foreach (var argument in new[] { "-Djava.awt.headless=true", "-jar", jar, "--installClient", cache }) start.ArgumentList.Add(argument);
        using var process = Process.Start(start)!;
        var output = process.StandardOutput.ReadToEndAsync(token);
        var errors = process.StandardError.ReadToEndAsync(token);
        try
        {
            using var limit = CancellationTokenSource.CreateLinkedTokenSource(token);
            limit.CancelAfter(TimeSpan.FromMinutes(15));
            await process.WaitForExitAsync(limit.Token);
        }
        catch (OperationCanceledException)
        {
            process.Kill(entireProcessTree: true);
            throw;
        }
        await File.WriteAllTextAsync(log, await output + await errors, CancellationToken.None);
        if (process.ExitCode != 0 || !File.Exists(Path.Combine(launcher.MinecraftPath.Versions, versionId, versionId + ".json")))
            throw new InvalidOperationException($"The {versionId} installer failed (exit code {process.ExitCode}). Its output is in '{log}'.");
        return versionId;
    }
}
