using System.Diagnostics;
using System.Text.Json;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>The in-game "Switch Versions" hand-off: the list the launcher writes and the request it acts on when the game has exited.</summary>
public class VersionSwitchTests
{
    private static string[] Versions(string gameDirectory) =>
        JsonDocument.Parse(File.ReadAllText(Path.Combine(gameDirectory, VersionSwitch.VersionsFile))).RootElement
            .GetProperty("versions").EnumerateArray().Select(v => v.GetString()!).ToArray();

    private static void Request(string gameDirectory, string json) =>
        File.WriteAllText(Path.Combine(gameDirectory, VersionSwitch.RequestFile), json);

    private static ProfileService Profiles(TestDirectory dir) =>
        new(new PathService(Path.Combine(dir.Path, "launcher")), new SharedContentService(Path.Combine(dir.Path, "global")));

    [Theory]
    [InlineData("1.20.1")]
    [InlineData("Zero SMP")]
    public void VersionsFileOffersTheLaunchersPlayableVersionsAndNamesWhatRuns(string running)
    {
        using var dir = new TestDirectory();
        VersionSwitch.Prepare(dir.Path, running);

        Assert.Equal(new[] { "26.3", "1.20.1", "1.8.9" }, Versions(dir.Path)); // exactly the Play screen's versions, 26.2 never
        Assert.Equal(running, JsonDocument.Parse(File.ReadAllText(Path.Combine(dir.Path, VersionSwitch.VersionsFile))).RootElement.GetProperty("current").GetString());
        Assert.Empty(Directory.GetFiles(dir.Path, "*.tmp"));
    }

    [Fact]
    public void PrepareClearsARequestLeftByAnEarlierRun()
    {
        using var dir = new TestDirectory();
        Request(dir.Path, """{"version":"1.8.9","requested":"2026-10-10T04:40:48Z"}""");

        VersionSwitch.Prepare(dir.Path, "Zero SMP"); // the next game start

        Assert.False(File.Exists(Path.Combine(dir.Path, VersionSwitch.RequestFile)));
        Assert.Null(VersionSwitch.TakeRequest(dir.Path, _ => { }));
    }

    [Fact]
    public void RequestIsReadOnceAndDeleted()
    {
        using var dir = new TestDirectory();
        Request(dir.Path, """{"version":" 1.20.1 ","requested":"2026-10-10T04:40:48Z"}""");

        Assert.Equal("1.20.1", VersionSwitch.TakeRequest(dir.Path, _ => { }));
        Assert.False(File.Exists(Path.Combine(dir.Path, VersionSwitch.RequestFile)));
        Assert.Null(VersionSwitch.TakeRequest(dir.Path, _ => { }));
    }

    [Theory]
    [InlineData("""{"version":"26.2"}""", "paused")]
    [InlineData("""{"version":"1.7.10"}""", "COMING NEVER")]
    [InlineData("""{"version":"1.21.11"}""", "not offered")]
    [InlineData("""{"version":""}""", "not offered")]
    [InlineData("""{"version":42}""", "Could not read")]
    [InlineData("""{"other":"1.8.9"}""", "Could not read")]
    [InlineData("{\"version\":", "Could not read")]
    public void RefusedRequestIsDeletedAndSaysWhy(string json, string reason)
    {
        using var dir = new TestDirectory();
        var log = new List<string>();
        Request(dir.Path, json);

        Assert.Null(VersionSwitch.TakeRequest(dir.Path, log.Add));

        Assert.False(File.Exists(Path.Combine(dir.Path, VersionSwitch.RequestFile)));
        Assert.Contains(log, line => line.Contains(reason, StringComparison.OrdinalIgnoreCase));
    }

    [Theory]
    [InlineData("1.8.9", 8, false)]
    [InlineData("1.20.1", 17, true)]
    [InlineData("26.3", 25, true)]
    public void ApplyMakesTheChosenVersionsProfileActive(string version, int java, bool fabric)
    {
        using var dir = new TestDirectory();
        var profiles = Profiles(dir);
        var game = Path.Combine(dir.Path, "game");
        Directory.CreateDirectory(game);
        Request(game, $$"""{"version":"{{version}}"}""");

        var profile = VersionSwitch.Apply(game, profiles, _ => { });

        Assert.NotNull(profile);
        Assert.Equal(version, profile!.MinecraftVersion);
        Assert.Equal(profile.Id, profiles.GetActiveProfile().Id);
        Assert.Equal(java, profile.JavaMajorVersion);
        Assert.Equal(fabric, !string.IsNullOrEmpty(profile.FabricVersion));
    }

    [Theory]
    [InlineData("1.8.9", 8, false)]
    [InlineData("1.20.1", 17, true)]
    [InlineData("26.3", 25, true)]
    public void ADeletedStandardProfileIsMadeAgainWithCoresLoaderAndJava(string version, int java, bool fabric)
    {
        using var dir = new TestDirectory();
        var profiles = Profiles(dir);
        foreach (var profile in profiles.GetProfiles().Where(p => p.MinecraftVersion == version).ToList())
            Assert.True(profiles.DeleteProfile(profile.Id));
        var game = Path.Combine(dir.Path, "game");
        Directory.CreateDirectory(game);
        Request(game, $$"""{"version":"{{version}}"}""");

        var made = VersionSwitch.Apply(game, profiles, _ => { })!;

        Assert.Equal($"The Lads Client {version}", made.Name);
        Assert.Equal(java, made.JavaMajorVersion);
        Assert.Equal(fabric ? "0.19.5" : null, made.FabricVersion);
        GameVersionPolicy.ResolveVersionId(made); // launchable
    }

    [Fact]
    public void RefusedRequestLeavesTheActiveProfileAlone()
    {
        using var dir = new TestDirectory();
        var profiles = Profiles(dir);
        var before = profiles.GetActiveProfile().Id;
        var game = Path.Combine(dir.Path, "game");
        Directory.CreateDirectory(game);
        Request(game, """{"version":"26.2"}""");

        Assert.Null(VersionSwitch.Apply(game, profiles, _ => { }));
        Assert.Equal(before, profiles.GetActiveProfile().Id);
    }

    /// <summary>The path the launcher takes when a game exits: the process ends, GameSession runs the post-exit step, which reads the
    /// game's request. A modpack game (Zero SMP) runs from its own folder, so the request lies there, not in a profile folder.</summary>
    [Fact]
    public async Task ExitedGameWithARequestSwitchesTheProfileAndAsksForALaunch()
    {
        using var dir = new TestDirectory();
        var profiles = Profiles(dir);
        var zeroSmp = Path.Combine(dir.Path, "launcher", "modpacks", "zero-smp-1.20.1", "minecraft");
        Directory.CreateDirectory(zeroSmp);
        VersionSwitch.Prepare(zeroSmp, "Zero SMP");
        Request(zeroSmp, """{"version":"1.8.9","requested":"2026-10-10T04:40:48Z"}"""); // the game wrote it and quit
        var shared = new SharedContentService(Path.Combine(dir.Path, "global"), Path.Combine(dir.Path, "backups"));
        var relaunch = new TaskCompletionSource<string>();
        using var process = Process.Start(new ProcessStartInfo("cmd.exe", "/c exit 0") { UseShellExecute = false, CreateNoWindow = true })!;

        GameSession.Attach(process, zeroSmp, Array.Empty<string>(), _ => { }, shared, afterExit: () =>
        {
            var next = VersionSwitch.Apply(zeroSmp, profiles, _ => { });
            relaunch.TrySetResult(next == null ? "none" : $"{next.MinecraftVersion}|{profiles.GetActiveProfile().MinecraftVersion}");
            return Task.CompletedTask;
        });

        Assert.Equal("1.8.9|1.8.9", await relaunch.Task.WaitAsync(TimeSpan.FromSeconds(30)));
        Assert.False(File.Exists(Path.Combine(zeroSmp, VersionSwitch.RequestFile)));
    }
}
