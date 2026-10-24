using System.IO;
using System.Threading.Tasks;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class GameRepairServiceTests
{
    [Fact]
    public async Task RepairProfileAsync_CleansTempFilesAndRestoresManifest()
    {
        using var dir = new TestDirectory();
        var profile = new LauncherProfile
        {
            Id = "test-repair",
            Name = "Test Repair Profile",
            MinecraftVersion = "1.20.1",
            CustomGameDir = dir.Path
        };

        // Create a stray .tmp file
        File.WriteAllText(Path.Combine(dir.Path, "stray.tmp"), "corrupt data");

        var report = await GameRepairService.RepairProfileAsync(dir.Path, profile);

        Assert.True(report.Success);
        Assert.False(File.Exists(Path.Combine(dir.Path, "stray.tmp")));
        Assert.True(report.FilesRepaired >= 1);

        string versionId = GameVersionPolicy.ResolveVersionId(profile);
        string manifest = Path.Combine(dir.Path, "versions", versionId, versionId + ".json");
        Assert.True(File.Exists(manifest));

        string marker = Path.Combine(dir.Path, "versions", versionId, ".lads-verified");
        Assert.True(File.Exists(marker));
    }
}
