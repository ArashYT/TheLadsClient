using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
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

    public LaunchService(
        IPathService pathService,
        IProfileService profileService,
        IJavaService javaService,
        IAuthService authService)
    {
        _pathService = pathService;
        _profileService = profileService;
        _javaService = javaService;
        _authService = authService;
    }

    public async Task<Process?> LaunchAsync(
        LauncherProfile profile,
        string username,
        LauncherSettings settings,
        IProgress<double>? progress = null,
        Action<string>? statusCallback = null,
        CancellationToken cancellationToken = default)
    {
        statusCallback?.Invoke("Preparing profile environment...");
        await _profileService.PrepareProfileEnvironmentAsync(profile);

        var gameDir = _pathService.GetProfileDirectory(profile);
        Directory.CreateDirectory(gameDir);

        statusCallback?.Invoke("Verifying Java runtime...");
        string javaPath;
        if (!string.IsNullOrWhiteSpace(profile.CustomJavaPath) && File.Exists(profile.CustomJavaPath))
        {
            javaPath = profile.CustomJavaPath;
        }
        else if (!string.IsNullOrWhiteSpace(settings.JavaPath) && File.Exists(settings.JavaPath) && !settings.AutoDetectJava)
        {
            javaPath = settings.JavaPath;
        }
        else
        {
            javaPath = await _javaService.EnsureJavaAsync(
                profile.JavaMajorVersion,
                progress,
                statusCallback,
                cancellationToken);
        }

        statusCallback?.Invoke("Authenticating player session...");
        var session = await _authService.ResolveSessionAsync(username);

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
        string versionId = ResolveVersionId(gameDir, profile, settings);

        // Ensure Fabric JSON exists if requested
        var vdir = Path.Combine(gameDir, "versions", versionId);
        var vjson = Path.Combine(vdir, versionId + ".json");
        if (!File.Exists(vjson))
        {
            var match = System.Text.RegularExpressions.Regex.Match(versionId, @"fabric-loader-(?<loader>[\d\.]+)-(?<mc>[\w\.\-]+)");
            if (match.Success)
            {
                string loaderVer = match.Groups["loader"].Value;
                string mcVer = match.Groups["mc"].Value;
                try
                {
                    statusCallback?.Invoke($"Installing Fabric {loaderVer} for MC {mcVer}...");
                    var fabricInstaller = new CmlLib.Core.ModLoaders.FabricMC.FabricInstaller(new HttpClient());
                    await fabricInstaller.Install(mcVer, loaderVer, path);
                }
                catch { }
            }
        }

        statusCallback?.Invoke($"Building launch arguments for {versionId}...");
        var launchOption = new MLaunchOption
        {
            Path = path,
            StartVersion = await launcher.GetVersionAsync(versionId),
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
            process = await launcher.BuildProcessAsync(versionId, launchOption);
        }
        else
        {
            process = await launcher.InstallAndBuildProcessAsync(versionId, launchOption);
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

        process.Start();
        statusCallback?.Invoke($"Minecraft started with PID {process.Id}");

        return process;
    }

    private static string ResolveVersionId(string gameDir, LauncherProfile profile, LauncherSettings settings)
    {
        // 1. If profile has specific fabric version
        if (!string.IsNullOrEmpty(profile.FabricVersion))
        {
            string wantedFabric = profile.FabricVersion.StartsWith("fabric-loader-")
                ? profile.FabricVersion
                : $"fabric-loader-{profile.FabricVersion}-{profile.MinecraftVersion}";

            var vdir = Path.Combine(gameDir, "versions");
            if (File.Exists(Path.Combine(vdir, wantedFabric, wantedFabric + ".json")))
                return wantedFabric;

            if (Directory.Exists(vdir))
            {
                var candidates = Directory.GetDirectories(vdir)
                    .Select(d => Path.GetFileName(d) ?? "")
                    .Where(n => !string.IsNullOrEmpty(n) && File.Exists(Path.Combine(vdir, n, n + ".json")))
                    .ToList();

                var best = candidates.FirstOrDefault(n => n.Contains(profile.MinecraftVersion, StringComparison.OrdinalIgnoreCase) && n.StartsWith("fabric-loader-", StringComparison.OrdinalIgnoreCase))
                           ?? candidates.FirstOrDefault(n => n.StartsWith("fabric-loader-", StringComparison.OrdinalIgnoreCase));
                if (best != null) return best;
            }

            return wantedFabric;
        }

        // 2. Otherwise use Minecraft version
        return profile.MinecraftVersion;
    }
}
