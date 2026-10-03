using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.Http;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using CmlLib.Core;
using CmlLib.Core.Auth;
using CmlLib.Core.ModLoaders.FabricMC;
using CmlLib.Core.ModLoaders.QuiltMC;
using CmlLib.Core.ProcessBuilder;

namespace TheLadsLauncher.Services;

/// <summary>
/// Installs modpacks as instances and starts them: vanilla plus the pack's loader, never LadsCore or the Lads pack. Libraries,
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
            await Mrpack.InstallAsync(mrpackFile, instance.Directory, http, status, token);
            Apply(instance, index, source);
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
        var index = await Mrpack.InstallAsync(mrpackFile, instance.Directory, http, status, token);
        Apply(instance, index, source);
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

    /// <summary>Installs what the instance needs and starts it with <paramref name="session"/> and the Java its version needs.</summary>
    public static async Task<Process> LaunchAsync(string dataDirectory, ModpackInstance instance, MSession session, LauncherSettings settings,
        IJavaService java, HttpClient http, Action<string>? status, CancellationToken token)
    {
        var process = await PrepareAsync(dataDirectory, instance, session, settings, java, http, status, token);
        token.ThrowIfCancellationRequested();
        process.Start();
        instance.LastPlayedUtc = DateTime.UtcNow;
        Modpacks.Save(instance);
        return process;
    }

    /// <summary>Everything <see cref="LaunchAsync"/> does short of starting the game: Java, loader, game files; the process to start.</summary>
    public static async Task<Process> PrepareAsync(string dataDirectory, ModpackInstance instance, MSession session, LauncherSettings settings,
        IJavaService java, HttpClient http, Action<string>? status, CancellationToken token)
    {
        var path = GamePath(dataDirectory, instance);
        Directory.CreateDirectory(instance.GameDirectory);
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
        var process = await launcher.InstallAndBuildProcessAsync(versionId, new MLaunchOption
        {
            Session = session,
            JavaPath = javaPath,
            MaximumRamMb = ram,
            MinimumRamMb = Math.Clamp(settings.MinRamMb, 256, ram),
            FullScreen = settings.FullscreenOnLaunch
        }, token);
        process.StartInfo.UseShellExecute = false;
        process.StartInfo.CreateNoWindow = true;
        return process;
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
        if (!File.Exists(jar))
        {
            byte[]? bytes = null;
            foreach (var url in urls)
            {
                using var response = await http.GetAsync(url, token);
                if (response.StatusCode == HttpStatusCode.NotFound) continue;
                response.EnsureSuccessStatusCode();
                bytes = await response.Content.ReadAsByteArrayAsync(token);
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
