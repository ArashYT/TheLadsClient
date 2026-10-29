using System.IO;
using System.Threading.Tasks;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class GameRepairServiceTests
{
    [Fact]
    public void CleanLauncherCacheTempFiles_RemovesCacheTempFiles_AndNeverTouchesSaves()
    {
        using var dir = new TestDirectory();
        var cacheDir = Path.Combine(dir.Path, ".lads-mod-cache");
        var nativesDir = Path.Combine(dir.Path, "natives");
        var savesDir = Path.Combine(dir.Path, "saves", "New World");

        Directory.CreateDirectory(cacheDir);
        Directory.CreateDirectory(nativesDir);
        Directory.CreateDirectory(savesDir);

        string cacheTmp = Path.Combine(cacheDir, "download.tmp");
        string nativesPart = Path.Combine(nativesDir, "library.part");
        string saveTmp = Path.Combine(savesDir, "level.dat.tmp");

        File.WriteAllText(cacheTmp, "temp cache");
        File.WriteAllText(nativesPart, "temp native");
        File.WriteAllText(saveTmp, "precious save game temp data");

        int cleaned = GameRepairService.CleanLauncherCacheTempFiles(dir.Path);

        // Cache temp files must be removed
        Assert.Equal(2, cleaned);
        Assert.False(File.Exists(cacheTmp));
        Assert.False(File.Exists(nativesPart));

        // SAVES FOLDER MUST NEVER BE TOUCHED
        Assert.True(File.Exists(saveTmp));
        Assert.Equal("precious save game temp data", File.ReadAllText(saveTmp));
    }

    [Fact]
    public async Task RepairProfileAsync_WhenVersionInstallThrows_ReturnsFailureWithoutMarker()
    {
        using var dir = new TestDirectory();
        var profile = new LauncherProfile
        {
            Name = "RepairTest",
            MinecraftVersion = "1.20.1",
            FabricVersion = "0.16.9",
            CustomGameDir = dir.Path
        };

        string versionId = GameVersionPolicy.ResolveVersionId(profile);

        // Stub step 3: LadsCore mod exists so step 3 does not fail
        var modsDir = Path.Combine(dir.Path, "mods");
        Directory.CreateDirectory(modsDir);
        File.WriteAllBytes(Path.Combine(modsDir, "theladscore.jar"), new byte[] { 0x50, 0x4B, 0x03, 0x04 });

        // Stub step 4: Fabric version manifest exists and has valid json, so step 4 does not fail
        var versionDir = Path.Combine(dir.Path, "versions", versionId);
        Directory.CreateDirectory(versionDir);
        File.WriteAllText(Path.Combine(versionDir, versionId + ".json"), "{\"id\":\"" + versionId + "\",\"inheritsFrom\":\"nonexistent-version-xyz\"}");

        var cacheDir = Path.Combine(dir.Path, ".lads-mod-cache");
        Directory.CreateDirectory(cacheDir);
        string cacheTmp = Path.Combine(cacheDir, "stale.tmp");
        File.WriteAllText(cacheTmp, "garbage");

        var report = await GameRepairService.RepairProfileAsync(dir.Path, profile);

        // Steps 3 & 4 succeeded, but step 5 (integrity check / launcher install) failed.
        // Repair must report failure and must NOT write .lads-verified marker.
        Assert.False(report.Success);
        Assert.False(File.Exists(cacheTmp));

        string marker = Path.Combine(versionDir, ".lads-verified");
        Assert.False(File.Exists(marker));
        Assert.Contains(report.Messages, m => m.Contains("Integrity check failed"));
    }
}
