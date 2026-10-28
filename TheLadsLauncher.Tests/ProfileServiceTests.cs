using System;
using System.IO;
using System.Linq;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ProfileServiceTests
{
    [Fact]
    public void ProfileService_WritesProfilesAtomicallyAndKeepsBackup()
    {
        using var dir = new TestDirectory();
        var paths = new PathService(dir.Path);
        var shared = new SharedContentService(Path.Combine(dir.Path, "shared"));

        var service = new ProfileService(paths, shared);
        string configPath = Path.Combine(dir.Path, "profiles.json");
        string bakPath = configPath + ".bak";

        Assert.True(File.Exists(configPath));

        service.CreateProfile("CustomTest", "1.20.1", 17);

        Assert.True(File.Exists(configPath));
        Assert.True(File.Exists(bakPath));
        Assert.Empty(Directory.GetFiles(dir.Path, "*.tmp"));

        var reloaded = new ProfileService(paths, shared);
        Assert.Contains(reloaded.GetProfiles(), p => p.Name == "CustomTest");
    }

    [Fact]
    public void ProfileService_WhenMainCorrupted_LoadsFromBackup()
    {
        using var dir = new TestDirectory();
        var paths = new PathService(dir.Path);
        var shared = new SharedContentService(Path.Combine(dir.Path, "shared"));

        var service = new ProfileService(paths, shared);
        service.CreateProfile("RecoverableProfile", "1.20.1", 17);
        service.SaveProfiles();

        string configPath = Path.Combine(dir.Path, "profiles.json");
        string bakPath = configPath + ".bak";
        Assert.True(File.Exists(bakPath));

        // Corrupt main file
        File.WriteAllText(configPath, "{ broken json !@#$%");

        var recovered = new ProfileService(paths, shared);
        Assert.NotNull(recovered.StartupWarning);
        Assert.Contains("restored from backup", recovered.StartupWarning);
        Assert.Contains(recovered.GetProfiles(), p => p.Name == "RecoverableProfile");
    }

    [Fact]
    public void ProfileService_WhenBothCorrupted_StartsCleanAndPreservesCorruptFile()
    {
        using var dir = new TestDirectory();
        var paths = new PathService(dir.Path);
        var shared = new SharedContentService(Path.Combine(dir.Path, "shared"));

        string configPath = Path.Combine(dir.Path, "profiles.json");
        Directory.CreateDirectory(dir.Path);
        File.WriteAllText(configPath, "not valid json");

        var clean = new ProfileService(paths, shared);
        Assert.NotNull(clean.StartupWarning);
        Assert.Contains("corrupt", clean.StartupWarning);
        Assert.NotEmpty(clean.GetProfiles());

        var corruptFiles = Directory.GetFiles(dir.Path, "profiles.json.corrupt-*");
        Assert.Single(corruptFiles);
        Assert.Equal("not valid json", File.ReadAllText(corruptFiles[0]));
    }
}
