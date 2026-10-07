using System.IO.Compression;
using System.Text.Json;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class VersionAndInstallationTests
{
    [Theory]
    [InlineData("1.21.11", 21)]
    [InlineData("26.2", 25)]
    [InlineData("26.3", 25)]
    public void ExactVersionAndJavaRequirements(string version, int java)
    {
        var profile = new LauncherProfile { MinecraftVersion = version, FabricVersion = "0.19.5" };
        Assert.Equal($"fabric-loader-0.19.5-{version}", GameVersionPolicy.ResolveVersionId(profile));
        Assert.Equal(java, GameVersionPolicy.GetRequiredJavaMajor(version));
        profile.FabricVersion = "fabric-loader-0.19.5-1.21.1";
        Assert.Throws<ArgumentException>(() => GameVersionPolicy.ResolveVersionId(profile));
    }

    [Theory]
    [InlineData("../26.2")]
    [InlineData("26.2/other")]
    [InlineData("latest.release")]
    [InlineData("")]
    public void InvalidVersionCannotBecomeAnInstallPath(string version) =>
        Assert.Throws<ArgumentException>(() => GameVersionPolicy.ResolveVersionId(new LauncherProfile { MinecraftVersion = version }));

    [Fact]
    public void MissingRequestedProfileIsAddedWithoutReplacingSavedWorldLocations()
    {
        using var dir = new TestDirectory();
        string oldGame = Path.Combine(dir.Path, "existing-worlds");
        var saved = new { ActiveProfileId = "custom", Profiles = new[] {
            new LauncherProfile { Id = "custom", Name = "My world", MinecraftVersion = "1.21.11", FabricVersion = "0.16.9", JavaMajorVersion = 21, CustomGameDir = oldGame }
        }};
        File.WriteAllText(Path.Combine(dir.Path, "profiles.json"), JsonSerializer.Serialize(saved));
        var service = new ProfileService(new PathService(dir.Path), new SharedContentService(Path.Combine(dir.Path, "global")));
        Assert.Equal("custom", service.GetActiveProfile().Id);
        Assert.Equal(oldGame, service.GetProfile("custom")!.CustomGameDir);
        Assert.Contains(service.GetProfiles(), p => p.MinecraftVersion == "1.21.11" && p.JavaMajorVersion == 21);
        Assert.Contains(service.GetProfiles(), p => p.MinecraftVersion == "26.2" && p.JavaMajorVersion == 25);
    }

    [Theory]
    [InlineData("latest.release", null, "0.19.5")]
    [InlineData("latest-release", "0.16.9", "0.19.5")]
    [InlineData("latest.release", "fabric-loader-0.16.9-latest.release", "fabric-loader-0.19.5-26.3")]
    [InlineData("latest-release", "fabric-loader-0.20.0-latest-release", "fabric-loader-0.20.0-26.3")]
    public void ReleaseAliasMigratesInPlaceToRequestedVersion(string alias, string? loader, string expectedLoader)
    {
        using var dir = new TestDirectory();
        string world = Path.Combine(dir.Path, "saved-world");
        var profile = new LauncherProfile { Id = "legacy-release", Name = "My release", MinecraftVersion = alias,
            FabricVersion = loader, JavaMajorVersion = 21, CustomGameDir = world, PackwizUrl = "https://example.com/custom/pack.toml" };
        string file = Path.Combine(dir.Path, "profiles.json");
        File.WriteAllText(file, JsonSerializer.Serialize(new { ActiveProfileId = profile.Id, Profiles = new[] { profile } }));

        var service = new ProfileService(new PathService(dir.Path), new SharedContentService(Path.Combine(dir.Path, "global")));
        var active = service.GetActiveProfile();
        Assert.Equal(profile.Id, active.Id);
        Assert.Equal(profile.Name, active.Name);
        Assert.Equal(world, active.CustomGameDir);
        Assert.Equal(profile.PackwizUrl, active.PackwizUrl);
        Assert.Equal("26.3", active.MinecraftVersion);
        Assert.Equal(25, active.JavaMajorVersion);
        Assert.Equal(expectedLoader, active.FabricVersion);
        Assert.EndsWith("-26.3", GameVersionPolicy.ResolveVersionId(active));
        string migrated = File.ReadAllText(file);
        var reloaded = new ProfileService(new PathService(dir.Path), new SharedContentService(Path.Combine(dir.Path, "global")));
        Assert.Equal(profile.Id, reloaded.GetActiveProfile().Id);
        Assert.Equal(migrated, File.ReadAllText(file));
    }

    [Theory]
    [InlineData("1.21.11", "0.16.9", "0.19.5")]
    [InlineData("26.2", "0.19.2", "0.19.5")]
    [InlineData("1.21.11", "fabric-loader-0.18.0-1.21.11", "fabric-loader-0.19.5-1.21.11")]
    [InlineData("26.2", "fabric-loader-0.19.2-26.2", "fabric-loader-0.19.5-26.2")]
    [InlineData("26.2", "0.19.5", "0.19.5")]
    [InlineData("1.21.11", "fabric-loader-0.20.0-1.21.11", "fabric-loader-0.20.0-1.21.11")]
    [InlineData("1.21.11", "0.18.0-custom", "0.18.0-custom")]
    [InlineData("26.2", "fabric-loader-0.18.0-custom-26.2", "fabric-loader-0.18.0-custom-26.2")]
    [InlineData("26.3", "0.19.5", "0.19.5")]
    [InlineData("26.3", "fabric-loader-0.19.5-26.3", "fabric-loader-0.19.5-26.3")]
    public void SupportedCoreLoaderMinimumPreservesOtherLoaders(string mc, string loader, string expected)
    {
        using var dir = new TestDirectory();
        var profile = new LauncherProfile { Id = "saved", MinecraftVersion = mc, FabricVersion = loader,
            CustomGameDir = Path.Combine(dir.Path, "worlds") };
        File.WriteAllText(Path.Combine(dir.Path, "profiles.json"),
            JsonSerializer.Serialize(new { ActiveProfileId = profile.Id, Profiles = new[] { profile } }));
        var service = new ProfileService(new PathService(dir.Path), new SharedContentService(Path.Combine(dir.Path, "global")));
        Assert.Equal(expected, service.GetProfile(profile.Id)!.FabricVersion);
        Assert.Equal(profile.Id, service.GetActiveProfile().Id);
        Assert.Equal(profile.CustomGameDir, service.GetActiveProfile().CustomGameDir);
    }

    [Theory]
    [InlineData("latest.snapshot")]
    [InlineData("latest-snapshot")]
    public void UnresolvedActiveAliasIsPreservedButValidDefaultBecomesActive(string alias)
    {
        using var dir = new TestDirectory();
        var profile = new LauncherProfile { Id = "saved-snapshot", MinecraftVersion = alias, FabricVersion = null,
            CustomGameDir = Path.Combine(dir.Path, "snapshot-worlds") };
        File.WriteAllText(Path.Combine(dir.Path, "profiles.json"),
            JsonSerializer.Serialize(new { ActiveProfileId = profile.Id, Profiles = new[] { profile } }));
        var service = new ProfileService(new PathService(dir.Path), new SharedContentService(Path.Combine(dir.Path, "global")));
        var preserved = service.GetProfile(profile.Id)!;
        Assert.Equal(alias, preserved.MinecraftVersion);
        Assert.Null(preserved.FabricVersion);
        Assert.Equal(profile.CustomGameDir, preserved.CustomGameDir);
        Assert.NotEqual(profile.Id, service.GetActiveProfile().Id);
        Assert.True(GameVersionPolicy.RequiresBundledCore(service.GetActiveProfile().MinecraftVersion));
        GameVersionPolicy.ResolveVersionId(service.GetActiveProfile());
        Assert.Equal(service.GetActiveProfile().Id, new ProfileService(new PathService(dir.Path), new SharedContentService(Path.Combine(dir.Path, "global"))).GetActiveProfile().Id);
    }

    [Fact]
    public void InvalidSupportedProfilesCannotPreventCreationOfSafeActiveDefault()
    {
        using var dir = new TestDirectory();
        var saved = new[] {
            new LauncherProfile { Id = "1.21.11", MinecraftVersion = "1.21.11", FabricVersion = "fabric-loader-0.19.5-26.2", CustomGameDir = "existing-world" },
            new LauncherProfile { Id = "26.2", MinecraftVersion = "26.2", FabricVersion = "custom-loader", CustomGameDir = "other-world" }
        };
        File.WriteAllText(Path.Combine(dir.Path, "profiles.json"),
            JsonSerializer.Serialize(new { ActiveProfileId = "1.21.11", Profiles = saved }));
        var service = new ProfileService(new PathService(dir.Path), new SharedContentService(Path.Combine(dir.Path, "global")));
        Assert.Equal(4, service.GetProfiles().Count); // the two saved ones plus the 26.3 and 1.8.9 defaults
        Assert.Equal("existing-world", service.GetProfile("1.21.11")!.CustomGameDir);
        Assert.Equal(saved[0].FabricVersion, service.GetProfile("1.21.11")!.FabricVersion);
        Assert.Equal(saved[1].FabricVersion, service.GetProfile("26.2")!.FabricVersion);
        Assert.Equal("26.3", service.GetActiveProfile().Id);
        Assert.Null(service.GetActiveProfile().CustomGameDir);
        GameVersionPolicy.ResolveVersionId(service.GetActiveProfile());
    }

    /// <summary>1.6.0 dropped Minecraft 1.21.1: a saved 1.21.1 profile is no longer offered and the launcher starts on the default
    /// version, but the profile entry stays in profiles.json and its game folder is left alone.</summary>
    [Fact]
    public void DroppedVersionProfileIsKeptButNoLongerOffered()
    {
        using var dir = new TestDirectory();
        string world = Path.Combine(dir.Path, "my-1.21.1-game");
        Directory.CreateDirectory(Path.Combine(world, "saves", "Old World"));
        File.WriteAllText(Path.Combine(world, "options.txt"), "fov:0.5\n");
        var saved = new[] {
            new LauncherProfile { Id = "1.21.1", Name = "The Lads Client 1.21.1 (Legacy)", MinecraftVersion = "1.21.1", FabricVersion = "0.19.5", JavaMajorVersion = 21, CustomGameDir = world },
            new LauncherProfile { Id = "1.8.9", Name = "The Lads Client 1.8.9", MinecraftVersion = "1.8.9", JavaMajorVersion = 8 }
        };
        string file = Path.Combine(dir.Path, "profiles.json");
        File.WriteAllText(file, JsonSerializer.Serialize(new { ActiveProfileId = "1.21.1", Profiles = saved }));

        var service = new ProfileService(new PathService(dir.Path), new SharedContentService(Path.Combine(dir.Path, "global")));
        Assert.DoesNotContain(service.GetProfiles(), p => p.MinecraftVersion == "1.21.1");
        Assert.Null(service.GetProfile("1.21.1"));
        Assert.Equal("26.3", service.GetActiveProfile().MinecraftVersion); // the default, never 1.8.9 by surprise
        service.SetActiveProfile("1.21.1");
        Assert.Equal("26.3", service.GetActiveProfile().MinecraftVersion);

        using var json = JsonDocument.Parse(File.ReadAllText(file));
        var kept = Assert.Single(json.RootElement.GetProperty("DroppedProfiles").EnumerateArray());
        Assert.Equal(("1.21.1", world), (kept.GetProperty("MinecraftVersion").GetString(), kept.GetProperty("CustomGameDir").GetString()));
        Assert.True(Directory.Exists(Path.Combine(world, "saves", "Old World")));
        Assert.Equal("fov:0.5\n", File.ReadAllText(Path.Combine(world, "options.txt")));
        string migrated = File.ReadAllText(file);
        Assert.Equal("26.3", new ProfileService(new PathService(dir.Path), new SharedContentService(Path.Combine(dir.Path, "global"))).GetActiveProfile().MinecraftVersion);
        Assert.Equal(migrated, File.ReadAllText(file)); // a second start changes nothing
    }

    [Fact]
    public async Task ReturningGameDoesNotResurrectRemovedSharedAccount()
    {
        using var dir = new TestDirectory();
        var paths = new PathService(dir.Path);
        var profiles = new ProfileService(paths, new SharedContentService(Path.Combine(dir.Path, "global")));
        var profile = profiles.GetActiveProfile();
        string game = paths.GetProfileDirectory(profile);
        Directory.CreateDirectory(game);
        File.WriteAllText(paths.AccountsFile, "[]");
        File.WriteAllText(paths.SharedAccountsFile, "[]");
        File.WriteAllText(Path.Combine(game, "lads_accounts.json"), "[{\"username\":\"Removed\"}]");
        await profiles.SyncProfileToSharedAsync(profile);
        Assert.Equal("[]", File.ReadAllText(paths.AccountsFile));
        Assert.Equal("[]", File.ReadAllText(paths.SharedAccountsFile));
    }

    [Fact]
    public async Task CorrectBundleReplacesOnlyCoreAndPreservesItsBackup()
    {
        using var dir = new TestDirectory();
        string bundle = Path.Combine(dir.Path, "bundle");
        string game = Path.Combine(dir.Path, "game");
        string source = Path.Combine(bundle, "game-mods", "26.2", "theladscore.jar");
        CreateMod(source, "theladscore", "26.2");
        string old = Path.Combine(game, "mods", "old-core.jar");
        CreateMod(old, "theladscore", "1.21.1");
        byte[] oldBytes = File.ReadAllBytes(old);
        string other = Path.Combine(game, "mods", "other.jar");
        CreateMod(other, "othermod", "26.2");
        byte[] otherBytes = File.ReadAllBytes(other);
        Assert.True(await BundledModInstaller.InstallAsync(bundle, game, "26.2"));
        Assert.False(File.Exists(old));
        Assert.Equal(otherBytes, File.ReadAllBytes(other));
        Assert.Equal(oldBytes, File.ReadAllBytes(Directory.GetFiles(Path.Combine(game, "mods-disabled"), "old-core.jar", SearchOption.AllDirectories).Single()));
        string installed = Path.Combine(game, "mods", "theladscore.jar");
        DateTime stamp = File.GetLastWriteTimeUtc(installed);
        Assert.False(await BundledModInstaller.InstallAsync(bundle, game, "26.2"));
        Assert.Equal(stamp, File.GetLastWriteTimeUtc(installed));
    }

    [Fact]
    public async Task WrongBundleFailsBeforeTouchingExistingMods()
    {
        using var dir = new TestDirectory();
        string source = Path.Combine(dir.Path, "game-mods", "1.21.11", "theladscore.jar");
        CreateMod(source, "theladscore", "1.21.1");
        string game = Path.Combine(dir.Path, "game");
        await Assert.ThrowsAsync<InvalidDataException>(() => BundledModInstaller.InstallAsync(dir.Path, game, "1.21.11"));
        Assert.False(Directory.Exists(game));
    }

    [Fact]
    public async Task MissingRequiredBundleDoesNotLaunchVanillaSilently()
    {
        using var dir = new TestDirectory();
        await Assert.ThrowsAsync<FileNotFoundException>(() => BundledModInstaller.InstallAsync(dir.Path, Path.Combine(dir.Path, "game"), "26.2"));
    }

    [Fact]
    public async Task AForeignJarNamedLikeCoreIsNeverOverwritten()
    {
        using var dir = new TestDirectory();
        CreateMod(Path.Combine(dir.Path, "game-mods", "26.2", "theladscore.jar"), "theladscore", "26.2");
        string game = Path.Combine(dir.Path, "game");
        string foreign = Path.Combine(game, "mods", "theladscore.jar");
        CreateMod(foreign, "othermod", "26.2");
        byte[] before = File.ReadAllBytes(foreign);
        await Assert.ThrowsAsync<IOException>(() => BundledModInstaller.InstallAsync(dir.Path, game, "26.2"));
        Assert.Equal(before, File.ReadAllBytes(foreign));
    }

    private static void CreateMod(string path, string id, string version)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        using var zip = ZipFile.Open(path, ZipArchiveMode.Create);
        using var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open());
        writer.Write(JsonSerializer.Serialize(new { schemaVersion = 1, id, version = "test", depends = new { minecraft = version } }));
    }

    [Fact]
    public void VersionSortingOrderIsStrictlyDescending()
    {
        var unsorted = new[] { "1.8.9", "1.21.11", "26.2", "26.3", "26.1.2", "27.0", "1.7.10", "1.20.1", "26.1" };
        var list = unsorted.ToList();
        list.Sort(GameVersionPolicy.CompareDescending);

        Assert.Equal(new[] { "27.0", "26.3", "26.2", "26.1.2", "26.1", "1.21.11", "1.20.1", "1.8.9", "1.7.10" }, list);
    }

    [Fact]
    public void ModrinthGameVersionsAreSortedDescending()
    {
        var versions = GameVersionPolicy.ModrinthGameVersions;
        Assert.NotEmpty(versions);
        Assert.Equal("26.3", versions[0]);
        Assert.Equal("1.7.10", versions[^1]);

        for (int i = 0; i < versions.Length - 1; i++)
        {
            int cmp = GameVersionPolicy.CompareDescending(versions[i], versions[i + 1]);
            Assert.True(cmp <= 0, $"{versions[i]} should rank before or equal to {versions[i + 1]} in descending order");
        }
    }
}
