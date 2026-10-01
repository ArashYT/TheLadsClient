using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using System.Net.Http;
using CmlLib.Core;
using CmlLib.Core.ProcessBuilder;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public class LaunchService : ILaunchService
{
    private static LaunchService? _instance;
    public static LaunchService Instance => _instance ??= new LaunchService(
        PathService.Instance,
        ProfileService.Instance,
        JavaService.Instance,
        AuthService.Instance);

    private readonly IPathService _pathService;
    private readonly IProfileService _profileService;
    private readonly IJavaService _javaService;
    private readonly IAuthService _authService;
    private readonly SharedContentService _sharedContent;
    private readonly string _bundleRoot;
    private readonly OptiFineInstaller.Pin _optiFine;

    public LaunchService(
        IPathService pathService,
        IProfileService profileService,
        IJavaService javaService,
        IAuthService authService,
        string? bundleRoot = null,
        SharedContentService? sharedContent = null,
        OptiFineInstaller.Pin? optiFine = null)
    {
        _pathService = pathService;
        _profileService = profileService;
        _javaService = javaService;
        _authService = authService;
        _sharedContent = sharedContent ?? SharedContentService.Instance;
        _bundleRoot = bundleRoot ?? AppContext.BaseDirectory;
        _optiFine = optiFine ?? OptiFineInstaller.M5;
    }

    public async Task<Process?> LaunchAsync(
        LauncherProfile profile,
        string username,
        LauncherSettings settings,
        IProgress<double>? progress = null,
        Action<string>? statusCallback = null,
        CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        var versionId = GameVersionPolicy.ResolveVersionId(profile);
        var usesFabric = !string.IsNullOrWhiteSpace(profile.FabricVersion);
        var usesForge = GameVersionPolicy.UsesForge(profile.MinecraftVersion); // ResolveVersionId refuses Fabric on these
        if (GameVersionPolicy.RequiresFabric(profile.MinecraftVersion) && !usesFabric)
            throw new InvalidOperationException($"The Lads Core for Minecraft {profile.MinecraftVersion} requires Fabric. Select a Fabric loader in this profile.");

        Action<string> report = message =>
        {
            if (statusCallback != null) statusCallback(message);
            else Trace.TraceWarning(message);
        };
        statusCallback?.Invoke("Preparing profile environment...");
        var prepared = await _profileService.PrepareProfileEnvironmentAsync(profile,
            statusCallback == null ? null : new Progress<string>(statusCallback), cancellationToken);
        foreach (var warning in prepared.Warnings) report(warning);
        cancellationToken.ThrowIfCancellationRequested();

        var gameDir = _pathService.GetProfileDirectory(profile);
        Directory.CreateDirectory(gameDir);
        await GraphicsRenderer.PrepareAsync(gameDir, profile.MinecraftVersion, settings.GraphicsRenderer, report, cancellationToken);

        IReadOnlyList<string> loadedMods = Array.Empty<string>();
        if (usesFabric || usesForge)
        {
            statusCallback?.Invoke($"Installing bundled core for Minecraft {profile.MinecraftVersion}...");
            await BundledModInstaller.InstallAsync(_bundleRoot, gameDir, profile.MinecraftVersion, cancellationToken);
            await ClientModInstaller.InstallAsync(_bundleRoot, gameDir, profile.MinecraftVersion, statusCallback, cancellationToken);
            // Downloaded from optifine.net and verified; any failure only means the game starts without it.
            if (usesForge && await OptiFineInstaller.InstallAsync(_pathService.BaseDirectory, gameDir, _optiFine, statusCallback,
                    cancellationToken) is { } optiFineWarning)
                report(optiFineWarning);
            // As in MainWindow.LaunchGame: the in-game Mods view's snapshot, and the ids the running marker records as loaded.
            var inventoryService = new ModInventoryService();
            var inventory = await inventoryService.BuildAsync(_bundleRoot, gameDir, profile.MinecraftVersion, cancellationToken);
            await inventoryService.WriteSnapshotAsync(inventory, cancellationToken);
            loadedMods = ModInventoryView.EnabledJarIds(inventory);
        }

        statusCallback?.Invoke("Initializing Minecraft launcher...");
        var path = new MinecraftPath(gameDir);
        var launcher = new MinecraftLauncher(path);

        launcher.FileProgressChanged += (sender, args) =>
        {
            if (args.TotalTasks > 0)
            {
                var pct = (double)args.ProgressedTasks / args.TotalTasks * 100.0;
                progress?.Report(pct);
                statusCallback?.Invoke($"Downloading assets: {args.Name ?? "files"} ({pct:F0}%)...");
            }
        };

        statusCallback?.Invoke("Resolving game version...");

        // Install only the selected loader/version; never substitute an installed candidate.
        var vdir = Path.Combine(gameDir, "versions", versionId);
        var vjson = Path.Combine(vdir, versionId + ".json");
        if (usesFabric)
        {
            if (!File.Exists(vjson))
            {
                var loaderVer = versionId.Substring("fabric-loader-".Length,
                    versionId.Length - "fabric-loader-".Length - profile.MinecraftVersion.Length - 1);
                using var fabricHttp = new HttpClient(new LaunchCancellationHandler(cancellationToken));
                try
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    statusCallback?.Invoke($"Installing Fabric {loaderVer} for MC {profile.MinecraftVersion}...");
                    var fabricInstaller = new CmlLib.Core.ModLoaders.FabricMC.FabricInstaller(fabricHttp);
                    var installedId = await fabricInstaller.Install(profile.MinecraftVersion, loaderVer, path);
                    cancellationToken.ThrowIfCancellationRequested();
                    if (installedId != versionId)
                        throw new InvalidOperationException($"Fabric installer returned '{installedId}' instead of '{versionId}'.");
                }
                catch (Exception) when (cancellationToken.IsCancellationRequested)
                {
                    throw new OperationCanceledException(cancellationToken);
                }
                catch (Exception ex)
                {
                    throw new InvalidOperationException($"Could not install Fabric {loaderVer} for Minecraft {profile.MinecraftVersion}. Retry after checking connectivity and the selected loader version.", ex);
                }
            }
            await CheckManifestAsync(vjson, versionId, profile.MinecraftVersion, "Fabric", cancellationToken);
        }
        else if (usesForge)
        {
            using var forgeHttp = new HttpClient(new LaunchCancellationHandler(cancellationToken));
            await InstallForgeAsync(launcher, gameDir, forgeHttp, statusCallback, cancellationToken);
        }

        var selectedVersion = await launcher.GetVersionAsync(versionId, cancellationToken);
        if (selectedVersion.Id != versionId)
            throw new InvalidDataException($"Resolved version '{selectedVersion.Id}' does not match selected version '{versionId}'.");
        var baseVersion = usesFabric || usesForge
            ? await launcher.GetVersionAsync(profile.MinecraftVersion, cancellationToken)
            : selectedVersion;
        int? manifestJava = int.TryParse(baseVersion.JavaVersion?.MajorVersion, out var major) ? major : null;
        // The profile's own value counts like the manifest's, then the policy applies its minimum and (1.8.9) maximum.
        var declaredJava = Math.Max(profile.JavaMajorVersion, manifestJava ?? 0);
        var requiredJava = GameVersionPolicy.GetRequiredJavaMajor(profile.MinecraftVersion, declaredJava > 0 ? declaredJava : null);
        var javaRule = GameVersionPolicy.DescribeJava(profile.MinecraftVersion, requiredJava);

        statusCallback?.Invoke($"Verifying Java {requiredJava} runtime...");
        string javaPath;
        if (!string.IsNullOrWhiteSpace(profile.CustomJavaPath))
            javaPath = profile.CustomJavaPath;
        else if (!string.IsNullOrWhiteSpace(settings.JavaPath) && !settings.AutoDetectJava)
            javaPath = settings.JavaPath;
        else
            javaPath = await _javaService.EnsureJavaAsync(requiredJava, progress, statusCallback, cancellationToken);

        cancellationToken.ThrowIfCancellationRequested();
        if (!File.Exists(javaPath))
            throw new FileNotFoundException($"Selected Java runtime '{javaPath}' does not exist. Minecraft {profile.MinecraftVersion} requires {javaRule}.", javaPath);
        var actualJava = _javaService.GetJavaMajorVersion(javaPath);
        if (!GameVersionPolicy.AcceptsJava(profile.MinecraftVersion, requiredJava, actualJava))
            throw new InvalidOperationException($"Selected Java runtime '{javaPath}' reports {actualJava?.ToString() ?? "an unknown version"}. Minecraft {profile.MinecraftVersion} requires {javaRule}.");

        statusCallback?.Invoke("Authenticating player session...");
        var session = await _authService.ResolveSessionAsync(username).WaitAsync(cancellationToken);
        cancellationToken.ThrowIfCancellationRequested();

        statusCallback?.Invoke($"Building launch arguments for {versionId}...");
        var launchOption = new MLaunchOption
        {
            Path = path,
            StartVersion = selectedVersion,
            Session = session,
            JavaPath = javaPath,
            MaximumRamMb = settings.MaxRamMb > 0 ? settings.MaxRamMb : 4096,
            MinimumRamMb = settings.MinRamMb > 0 ? settings.MinRamMb : 512,
            FullScreen = settings.FullscreenOnLaunch
        };

        if (!string.IsNullOrEmpty(settings.QuickLaunchServerIp))
        {
            launchOption.ServerIp = settings.QuickLaunchServerIp;
        }

        statusCallback?.Invoke("Starting game process...");
        Process process;
        if (settings.QuickLaunch)
        {
            process = await launcher.BuildProcessAsync(versionId, launchOption, cancellationToken);
        }
        else
        {
            process = await launcher.InstallAndBuildProcessAsync(versionId, launchOption, cancellationToken);
        }

        GameSession.Configure(process.StartInfo, gameDir, _sharedContent.Root);
        process.EnableRaisingEvents = true;
        process.Exited += async (s, e) =>
        {
            try
            {
                // GameSession.Attach reconciles the server list on exit; only the settings sync is left here.
                var result = await _profileService.SyncProfileToSharedAsync(profile, reconcileServerList: false);
                foreach (var message in result.Messages.Concat(result.Warnings)) report(message);
            }
            catch (Exception ex)
            {
                report($"Post-game shared sync failed for '{profile.Name}': {ex.Message}");
            }
        };

        try
        {
            cancellationToken.ThrowIfCancellationRequested();
            process.Start();
        }
        catch
        {
            process.Dispose();
            throw;
        }
        GameSession.Attach(process, gameDir, loadedMods, report, _sharedContent);
        statusCallback?.Invoke($"Minecraft started with PID {process.Id}");

        return process;
    }

    /// <summary>
    /// Installs Forge <see cref="GameVersionPolicy.ForgeBuild"/> for Minecraft 1.8.9 (and vanilla 1.8.9 with its libraries) unless
    /// its version manifest is there, then checks that manifest. MainWindow's launch uses it too. Done here rather than with
    /// CmlLib.Core.Installer.Forge, whose Install always opens Forge's adfoc.us page in the browser: the pinned official installer's
    /// install_profile.json versionInfo is the manifest, its universal jar the Forge library. The libraries point at
    /// maven.minecraftforge.net, not the retired files.minecraftforge.net/maven (byte for byte what CmlLib wrote).
    /// </summary>
    public static async Task InstallForgeAsync(MinecraftLauncher launcher, string gameDir, HttpClient http,
        Action<string>? statusCallback, CancellationToken cancellationToken)
    {
        const string versionId = GameVersionPolicy.ForgeVersionId;
        var manifest = Path.Combine(gameDir, "versions", versionId, versionId + ".json");
        if (!File.Exists(manifest))
        {
            statusCallback?.Invoke($"Installing Forge {GameVersionPolicy.ForgeBuild} for Minecraft {GameVersionPolicy.ForgeMinecraftVersion}...");
            try
            {
                await InstallForgeFilesAsync(gameDir, http, cancellationToken);
                await launcher.GetAllVersionsAsync(cancellationToken); // pick up the new manifest
                await launcher.InstallAsync(versionId, cancellationToken);
            }
            catch (Exception) when (cancellationToken.IsCancellationRequested)
            {
                throw new OperationCanceledException(cancellationToken);
            }
            catch (Exception ex)
            {
                throw new InvalidOperationException($"Could not install Forge {GameVersionPolicy.ForgeBuild} for Minecraft {GameVersionPolicy.ForgeMinecraftVersion}. Retry after checking your connection.", ex);
            }
        }
        await CheckManifestAsync(manifest, versionId, GameVersionPolicy.ForgeMinecraftVersion, "Forge", cancellationToken);
    }

    private const string ForgeInstallerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9/forge-1.8.9-11.15.1.2318-1.8.9-installer.jar";
    private const string ForgeInstallerSha256 = "f9fdf4945ca02d73ec6cc46300942f4e199e4add068877d517157b3677563656";

    private static async Task InstallForgeFilesAsync(string gameDir, HttpClient http, CancellationToken cancellationToken)
    {
        const string versionId = GameVersionPolicy.ForgeVersionId;
        var bytes = await http.GetByteArrayAsync(ForgeInstallerUrl, cancellationToken);
        if (Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(bytes)).ToLowerInvariant() != ForgeInstallerSha256)
            throw new InvalidDataException("The downloaded Forge installer does not match the pinned SHA-256.");
        using var zip = new System.IO.Compression.ZipArchive(new MemoryStream(bytes));
        using var profile = JsonDocument.Parse(zip.GetEntry("install_profile.json")!.Open());
        var install = profile.RootElement.GetProperty("install");
        var manifest = profile.RootElement.GetProperty("versionInfo").GetRawText().Replace("http://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/");
        var library = Path.Combine(gameDir, "libraries", "net", "minecraftforge", "forge", "1.8.9-11.15.1.2318-1.8.9", "forge-1.8.9-11.15.1.2318-1.8.9.jar");
        if (install.GetProperty("target").GetString() != versionId || install.GetProperty("path").GetString() != "net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
            throw new InvalidDataException("The Forge installer describes a different version.");
        Directory.CreateDirectory(Path.GetDirectoryName(library)!);
        System.IO.Compression.ZipFileExtensions.ExtractToFile(zip.GetEntry(install.GetProperty("filePath").GetString()!)!, library, overwrite: true);
        // The manifest last: InstallForgeAsync takes an existing manifest as an installed Forge.
        var versionDir = Path.Combine(gameDir, "versions", versionId);
        Directory.CreateDirectory(versionDir);
        await File.WriteAllTextAsync(Path.Combine(versionDir, versionId + ".json"), manifest, cancellationToken);
    }

    /// <summary>A loader's version manifest must be exactly the selected version, on top of the selected Minecraft version.</summary>
    private static async Task CheckManifestAsync(string manifest, string versionId, string minecraftVersion, string loader,
        CancellationToken cancellationToken)
    {
        using var json = JsonDocument.Parse(await File.ReadAllTextAsync(manifest, cancellationToken));
        var root = json.RootElement;
        if (!root.TryGetProperty("id", out var id) || id.GetString() != versionId
            || !root.TryGetProperty("inheritsFrom", out var parent) || parent.GetString() != minecraftVersion)
            throw new InvalidDataException($"{loader} manifest '{manifest}' does not match the selected version '{versionId}'. Repair this version before launching.");
    }

    private sealed class LaunchCancellationHandler : DelegatingHandler
    {
        private readonly CancellationToken _launchCancellation;

        public LaunchCancellationHandler(CancellationToken cancellationToken) : base(new HttpClientHandler()) =>
            _launchCancellation = cancellationToken;

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            using var linked = CancellationTokenSource.CreateLinkedTokenSource(_launchCancellation, cancellationToken);
            linked.Token.ThrowIfCancellationRequested();
            return await base.SendAsync(request, linked.Token);
        }
    }

}
