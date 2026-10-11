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
                catch (HttpRequestException) { } // the card shows its placeholder
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
    /// <paramref name="joinServer"/> ("host" or "host:port"): the game joins it as soon as it has loaded.</summary>
    public static async Task<Process> LaunchAsync(string dataDirectory, ModpackInstance instance, MSession session, LauncherSettings settings,
        IJavaService java, HttpClient http, Action<string>? status, CancellationToken token, string? joinServer = null)
    {
        SyncLadsConfigToInstance(dataDirectory, instance.GameDirectory);
        var process = await PrepareAsync(dataDirectory, instance, session, settings, java, http, status, token, joinServer);
        token.ThrowIfCancellationRequested();
        process.EnableRaisingEvents = true;
        process.Exited += (_, _) => SyncLadsConfigFromInstance(instance.GameDirectory, dataDirectory);
        process.Start();
        LaunchToken.Send(process);
        instance.LastPlayedUtc = DateTime.UtcNow;
        Modpacks.Save(instance);
        return process;
    }

    private static void SyncLadsConfigToInstance(string dataDirectory, string gameDirectory)
    {
        try
        {
            var sharedConfig = Path.Combine(dataDirectory, "shared", "thelads_config.json");
            var instanceConfig = Path.Combine(gameDirectory, "thelads_config.json");
            if (File.Exists(sharedConfig))
            {
                if (!File.Exists(instanceConfig) || File.GetLastWriteTimeUtc(sharedConfig) > File.GetLastWriteTimeUtc(instanceConfig))
                {
                    Directory.CreateDirectory(gameDirectory);
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
            if (File.Exists(instanceConfig))
            {
                if (!File.Exists(sharedConfig) || File.GetLastWriteTimeUtc(instanceConfig) >= File.GetLastWriteTimeUtc(sharedConfig))
                {
                    Directory.CreateDirectory(Path.GetDirectoryName(sharedConfig)!);
                    File.Copy(instanceConfig, sharedConfig, true);
                    File.Copy(instanceConfig, rootConfig, true);
                }
            }
        }
        catch { }
    }

    /// <summary>Everything <see cref="LaunchAsync"/> does short of starting the game: Java, loader, game files; the process to start.</summary>
    public static async Task<Process> PrepareAsync(string dataDirectory, ModpackInstance instance, MSession session, LauncherSettings settings,
        IJavaService java, HttpClient http, Action<string>? status, CancellationToken token, string? joinServer = null)
    {
        var path = GamePath(dataDirectory, instance);
        Directory.CreateDirectory(instance.GameDirectory);
        ModpackCoreSync.Sync(instance, status: status); // before every launch: a modpack always runs the newest bundled Lads Core
        var launcher = new MinecraftLauncher(path);
        launcher.FileProgressChanged += (_, e) =>
        {
            if (e.TotalTasks > 0) status?.Invoke($"Downloading game files {e.ProgressedTasks}/{e.TotalTasks}...");
        };

        status?.Invoke($"Resolving Minecraft {instance.McVersion}...");
        var vanilla = await launcher.GetVersionAsync(instance.McVersion, token);
        int? manifestJava = int.TryParse(vanilla.JavaVersion?.MajorVersion, out var major) ? major : null;
        int requiredJava = GameVersionPolicy.GetRequiredJavaMajor(instance.McVersion, manifestJava);
        // As the Lads launch: the Java set in Settings when it is exactly the version's, otherwise one picked for the version.
        string javaPath = !settings.AutoDetectJava && File.Exists(settings.JavaPath) && java.GetJavaMajorVersion(settings.JavaPath) == requiredJava
            ? settings.JavaPath
            : await java.EnsureJavaAsync(requiredJava, null, status, token);

        var versionId = await InstallLoaderAsync(launcher, dataDirectory, instance, javaPath, http, status, token);
        await launcher.GetAllVersionsAsync(token); // pick up the loader's manifest

        status?.Invoke("Checking game files...");
        int ram = instance.MaxRamMb > 0 ? instance.MaxRamMb : Math.Max(1024, settings.MaxRamMb);
        var options = new MLaunchOption
        {
            Session = session,
            JavaPath = javaPath,
            MaximumRamMb = ram,
            MinimumRamMb = Math.Clamp(settings.MinRamMb, 256, ram),
            FullScreen = settings.FullscreenOnLaunch
        };
        if (!string.IsNullOrWhiteSpace(joinServer)) JoinServer(options, joinServer);
        var process = await launcher.InstallAndBuildProcessAsync(versionId, options, token);
        process.StartInfo.UseShellExecute = false;
        process.StartInfo.CreateNoWindow = true;
        process.StartInfo.Environment["THELADS_GLOBAL_DIR"] = dataDirectory;
        process.StartInfo.Environment["THELADS_DIR"] = instance.GameDirectory;
        LaunchToken.Hide(process, instance.GameDirectory, session.AccessToken); // the caller sends it after Start
        try { VersionSwitch.Prepare(instance.GameDirectory, instance.Name); } // the in-game Switch Versions screen offers the Lads Client versions
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { status?.Invoke($"Could not write {VersionSwitch.VersionsFile}: {ex.Message}"); }
        try { AccountSwitch.Prepare(instance.GameDirectory); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { status?.Invoke($"Could not prepare {AccountSwitch.RequestFile}: {ex.Message}"); }
        return process;
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
                return await new FabricInstaller(http).Install(mc, loader, launcher.MinecraftPath);
            case "quilt":
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
        if (File.Exists(jar) && PinnedInstallerSha256.TryGetValue(Path.GetFileName(jar), out var pinned) && Sha256(await File.ReadAllBytesAsync(jar, token)) != pinned)
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
