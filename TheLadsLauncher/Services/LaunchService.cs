using System;
using System.Diagnostics;
using System.IO;
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
    private readonly string _bundleRoot;

    public LaunchService(
        IPathService pathService,
        IProfileService profileService,
        IJavaService javaService,
        IAuthService authService,
        string? bundleRoot = null)
    {
        _pathService = pathService;
        _profileService = profileService;
        _javaService = javaService;
        _authService = authService;
        _bundleRoot = bundleRoot ?? AppContext.BaseDirectory;
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
        if (GameVersionPolicy.RequiresBundledCore(profile.MinecraftVersion) && !usesFabric)
            throw new InvalidOperationException($"The Lads Core for Minecraft {profile.MinecraftVersion} requires Fabric. Select a Fabric loader in this profile.");

        statusCallback?.Invoke("Preparing profile environment...");
        await _profileService.PrepareProfileEnvironmentAsync(profile);
        cancellationToken.ThrowIfCancellationRequested();

        var gameDir = _pathService.GetProfileDirectory(profile);
        Directory.CreateDirectory(gameDir);

        if (usesFabric)
        {
            statusCallback?.Invoke($"Installing bundled core for Minecraft {profile.MinecraftVersion}...");
            await BundledModInstaller.InstallAsync(_bundleRoot, gameDir, profile.MinecraftVersion, cancellationToken);
            await ClientModInstaller.InstallAsync(_bundleRoot, gameDir, profile.MinecraftVersion, statusCallback, cancellationToken);
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
            using var fabricJson = JsonDocument.Parse(await File.ReadAllTextAsync(vjson, cancellationToken));
            var root = fabricJson.RootElement;
            if (!root.TryGetProperty("id", out var id) || id.GetString() != versionId
                || !root.TryGetProperty("inheritsFrom", out var parent) || parent.GetString() != profile.MinecraftVersion)
                throw new InvalidDataException($"Fabric manifest '{vjson}' does not match the selected version '{versionId}'. Repair this version before launching.");
        }

        var selectedVersion = await launcher.GetVersionAsync(versionId, cancellationToken);
        if (selectedVersion.Id != versionId)
            throw new InvalidDataException($"Resolved version '{selectedVersion.Id}' does not match selected version '{versionId}'.");
        var baseVersion = usesFabric
            ? await launcher.GetVersionAsync(profile.MinecraftVersion, cancellationToken)
            : selectedVersion;
        int? manifestJava = int.TryParse(baseVersion.JavaVersion?.MajorVersion, out var major) ? major : null;
        var requiredJava = Math.Max(profile.JavaMajorVersion,
            GameVersionPolicy.GetRequiredJavaMajor(profile.MinecraftVersion, manifestJava));

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
            throw new FileNotFoundException($"Selected Java runtime '{javaPath}' does not exist. Minecraft {profile.MinecraftVersion} requires Java {requiredJava} or newer.", javaPath);
        var actualJava = _javaService.GetJavaMajorVersion(javaPath);
        if (actualJava == null || actualJava < requiredJava)
            throw new InvalidOperationException($"Selected Java runtime '{javaPath}' reports {actualJava?.ToString() ?? "an unknown version"}. Minecraft {profile.MinecraftVersion} requires Java {requiredJava} or newer.");

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

        process.EnableRaisingEvents = true;
        process.Exited += async (s, e) =>
        {
            try
            {
                await _profileService.SyncProfileToSharedAsync(profile);
            }
            catch { }
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
        statusCallback?.Invoke($"Minecraft started with PID {process.Id}");

        return process;
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
