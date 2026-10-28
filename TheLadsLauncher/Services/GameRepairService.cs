using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using CmlLib.Core;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public sealed record GameRepairReport(
    bool Success,
    int FilesChecked,
    int FilesRepaired,
    IReadOnlyList<string> RepairedFiles,
    IReadOnlyList<string> Messages);

/// <summary>
/// Game Repair tool: re-runs the launcher's install/verify path with forced SHA-1 checks
/// of libraries, assets, and version files, restores bundled core mods, and only removes
/// temporary files inside launcher-owned cache folders. Nothing under saves/ is ever touched.
/// </summary>
public static class GameRepairService
{
    private static readonly string[] LauncherCacheSubdirs = new[]
    {
        ".lads-mod-cache",
        ".lads-install-cache",
        "natives"
    };

    /// <summary>
    /// Cleans only temporary/partial download files inside launcher-owned cache directories.
    /// Explicitly protects saves/, resourcepacks/, options.txt, and user data.
    /// </summary>
    public static int CleanLauncherCacheTempFiles(string gameDirectory, List<string>? repairedList = null)
    {
        int count = 0;
        foreach (var sub in LauncherCacheSubdirs)
        {
            var dir = Path.Combine(gameDirectory, sub);
            if (!Directory.Exists(dir)) continue;

            try
            {
                var tmpFiles = Directory.GetFiles(dir, "*.tmp", SearchOption.AllDirectories)
                    .Concat(Directory.GetFiles(dir, "*.part", SearchOption.AllDirectories));

                foreach (var file in tmpFiles)
                {
                    try
                    {
                        File.Delete(file);
                        count++;
                        repairedList?.Add($"Removed cache temp file: {Path.GetFileName(file)}");
                    }
                    catch { }
                }
            }
            catch { }
        }
        return count;
    }

    /// <summary>
    /// Verifies and repairs the game directory and version installation for the given profile.
    /// Forced SHA-1 checks of libraries, assets, and version json re-download any mismatches.
    /// </summary>
    public static async Task<GameRepairReport> RepairProfileAsync(
        string baseDirectory,
        LauncherProfile profile,
        Action<string>? statusCallback = null,
        CancellationToken cancellationToken = default)
    {
        var repaired = new List<string>();
        var messages = new List<string>();
        int checkedCount = 0;

        string gameDir = string.IsNullOrWhiteSpace(profile.CustomGameDir)
            ? PathService.Instance.GetProfileDirectory(profile)
            : profile.CustomGameDir;

        Directory.CreateDirectory(gameDir);
        var modsDir = Path.Combine(gameDir, "mods");
        Directory.CreateDirectory(modsDir);

        string versionId = GameVersionPolicy.ResolveVersionId(profile);
        var versionDir = Path.Combine(gameDir, "versions", versionId);
        Directory.CreateDirectory(versionDir);

        // 1. Clean ONLY temporary files inside launcher-owned cache directories (never touch saves/)
        statusCallback?.Invoke("Cleaning launcher cache temporary files…");
        int cleaned = CleanLauncherCacheTempFiles(gameDir, repaired);
        if (cleaned > 0)
        {
            messages.Add($"Cleaned {cleaned} temporary cache files.");
        }

        // 2. Remove .lads-verified marker to force full verification on CmlLib launcher
        string marker = Path.Combine(versionDir, ".lads-verified");
        if (File.Exists(marker))
        {
            try { File.Delete(marker); } catch { }
        }

        bool hasErrors = false;

        // 3. Restore bundled LadsCore mod
        statusCallback?.Invoke("Verifying bundled Lads Core client files…");
        string coreJar = Path.Combine(modsDir, "theladscore.jar");
        checkedCount++;
        bool coreNeedsInstall = !File.Exists(coreJar) || new FileInfo(coreJar).Length == 0;

        if (coreNeedsInstall)
        {
            try
            {
                await BundledModInstaller.InstallAsync(baseDirectory, gameDir, profile.MinecraftVersion, cancellationToken);
                repaired.Add("Restored bundled LadsCore mod");
                messages.Add("LadsCore client mod restored.");
            }
            catch (Exception ex)
            {
                hasErrors = true;
                messages.Add($"Could not restore LadsCore mod: {ex.Message}");
            }
        }

        // 4. Verify version manifest and re-download via official installer if missing
        statusCallback?.Invoke($"Checking version installation for {versionId}…");
        var path = new MinecraftPath(gameDir);
        var launcher = new MinecraftLauncher(path);

        string manifestFile = Path.Combine(versionDir, versionId + ".json");
        bool manifestMissingOrCorrupt = !File.Exists(manifestFile) || new FileInfo(manifestFile).Length < 10;

        bool usesFabric = !string.IsNullOrWhiteSpace(profile.FabricVersion);
        bool usesForge = GameVersionPolicy.UsesForge(profile.MinecraftVersion);

        if (manifestMissingOrCorrupt)
        {
            try
            {
                if (usesFabric)
                {
                    statusCallback?.Invoke($"Re-downloading Fabric {profile.FabricVersion} manifest…");
                    var loaderVer = versionId.Substring("fabric-loader-".Length,
                        versionId.Length - "fabric-loader-".Length - profile.MinecraftVersion.Length - 1);
                    using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(5) };
                    var fabricInstaller = new CmlLib.Core.ModLoaders.FabricMC.FabricInstaller(http);
                    await fabricInstaller.Install(profile.MinecraftVersion, loaderVer, path);
                    repaired.Add($"Re-downloaded Fabric manifest for {versionId}");
                }
                else if (usesForge)
                {
                    using var forgeHttp = new HttpClient { Timeout = TimeSpan.FromSeconds(5) };
                    await LaunchService.InstallForgeAsync(launcher, gameDir, forgeHttp, statusCallback, cancellationToken);
                    repaired.Add($"Re-installed Forge manifest for {versionId}");
                }
                else
                {
                    await launcher.GetAllVersionsAsync(cancellationToken);
                    repaired.Add($"Re-fetched vanilla manifest for {versionId}");
                }
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                hasErrors = true;
                messages.Add($"Could not restore version manifest: {ex.Message}");
            }
        }

        // 5. Run full launcher install & verification pass (re-checks SHA-1 checksums of libraries and assets)
        statusCallback?.Invoke("Verifying file hashes and integrity…");
        bool installSucceeded = false;
        try
        {
            await launcher.GetAllVersionsAsync(cancellationToken);
            await launcher.InstallAsync(versionId, cancellationToken);
            installSucceeded = true;
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            hasErrors = true;
            messages.Add($"Integrity check failed: {ex.Message}");
        }

        // 6. Write fresh verification marker only if verification succeeded without error
        if (installSucceeded && !hasErrors)
        {
            try
            {
                await File.WriteAllTextAsync(marker, DateTime.UtcNow.ToString("O"), cancellationToken);
                checkedCount++;
            }
            catch { }
        }

        bool success = installSucceeded && !hasErrors;
        if (success)
        {
            statusCallback?.Invoke($"Repair complete. {repaired.Count} items restored.");
            messages.Add($"Verified installation and restored {repaired.Count} items.");
        }
        else
        {
            statusCallback?.Invoke($"Repair finished with problems. {repaired.Count} items restored.");
        }

        return new GameRepairReport(success, checkedCount, repaired.Count, repaired, messages);
    }
}
