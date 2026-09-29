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
    public void ExactVersionAndJavaRequirements(string version, int java)
    {
        var profile = new LauncherProfile { MinecraftVersion = version, FabricVersion = "0.19.3" };
        Assert.Equal($"fabric-loader-0.19.3-{version}", GameVersionPolicy.ResolveVersionId(profile));
        Assert.Equal(java, GameVersionPolicy.GetRequiredJavaMajor(version));
        profile.FabricVersion = "fabric-loader-0.19.3-1.21.1";
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
            new LauncherProfile { Id = "custom", Name = "My world", MinecraftVersion = "1.21.1", FabricVersion = "0.16.9", JavaMajorVersion = 21, CustomGameDir = oldGame }
        }};
        File.WriteAllText(Path.Combine(dir.Path, "profiles.json"), JsonSerializer.Serialize(saved));
        var service = new ProfileService(new PathService(dir.Path));
        Assert.Equal("custom", service.GetActiveProfile().Id);
        Assert.Equal(oldGame, service.GetProfile("custom")!.CustomGameDir);
        Assert.Contains(service.GetProfiles(), p => p.MinecraftVersion == "1.21.11" && p.JavaMajorVersion == 21);
        Assert.Contains(service.GetProfiles(), p => p.MinecraftVersion == "26.2" && p.JavaMajorVersion == 25);
    }

    [Theory]
    [InlineData("latest.release", null, "0.19.3")]
    [InlineData("latest-release", "0.16.9", "0.19.3")]
    [InlineData("latest.release", "fabric-loader-0.16.9-latest.release", "fabric-loader-0.19.3-26.2")]
    [InlineData("latest-release", "fabric-loader-0.20.0-latest-release", "fabric-loader-0.20.0-26.2")]
    public void ReleaseAliasMigratesInPlaceToRequestedVersion(string alias, string? loader, string expectedLoader)
    {
        using var dir = new TestDirectory();
        string world = Path.Combine(dir.Path, "saved-world");
        var profile = new LauncherProfile { Id = "legacy-release", Name = "My release", MinecraftVersion = alias,
            FabricVersion = loader, JavaMajorVersion = 21, CustomGameDir = world, PackwizUrl = "https://example.com/custom/pack.toml" };
        string file = Path.Combine(dir.Path, "profiles.json");
        File.WriteAllText(file, JsonSerializer.Serialize(new { ActiveProfileId = profile.Id, Profiles = new[] { profile } }));

        var service = new ProfileService(new PathService(dir.Path));
        var active = service.GetActiveProfile();
        Assert.Equal(profile.Id, active.Id);
        Assert.Equal(profile.Name, active.Name);
        Assert.Equal(world, active.CustomGameDir);
        Assert.Equal(profile.PackwizUrl, active.PackwizUrl);
        Assert.Equal("26.2", active.MinecraftVersion);
        Assert.Equal(25, active.JavaMajorVersion);
        Assert.Equal(expectedLoader, active.FabricVersion);
        Assert.EndsWith("-26.2", GameVersionPolicy.ResolveVersionId(active));
        string migrated = File.ReadAllText(file);
        var reloaded = new ProfileService(new PathService(dir.Path));
        Assert.Equal(profile.Id, reloaded.GetActiveProfile().Id);
        Assert.Equal(migrated, File.ReadAllText(file));
    }

    [Theory]
    [InlineData("1.21.11", "0.16.9", "0.19.3")]
    [InlineData("26.2", "0.19.2", "0.19.3")]
    [InlineData("1.21.11", "fabric-loader-0.18.0-1.21.11", "fabric-loader-0.19.3-1.21.11")]
    [InlineData("26.2", "fabric-loader-0.19.2-26.2", "fabric-loader-0.19.3-26.2")]
    [InlineData("26.2", "0.19.3", "0.19.3")]
    [InlineData("1.21.11", "fabric-loader-0.20.0-1.21.11", "fabric-loader-0.20.0-1.21.11")]
    [InlineData("1.21.11", "0.18.0-custom", "0.18.0-custom")]
    [InlineData("26.2", "fabric-loader-0.18.0-custom-26.2", "fabric-loader-0.18.0-custom-26.2")]
    [InlineData("1.21.1", "0.16.9", "0.16.9")]
    public void SupportedCoreLoaderMinimumPreservesOtherLoaders(string mc, string loader, string expected)
    {
        using var dir = new TestDirectory();
        var profile = new LauncherProfile { Id = "saved", MinecraftVersion = mc, FabricVersion = loader,
            CustomGameDir = Path.Combine(dir.Path, "worlds") };
        File.WriteAllText(Path.Combine(dir.Path, "profiles.json"),
            JsonSerializer.Serialize(new { ActiveProfileId = profile.Id, Profiles = new[] { profile } }));
        var service = new ProfileService(new PathService(dir.Path));
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
        var service = new ProfileService(new PathService(dir.Path));
        var preserved = service.GetProfile(profile.Id)!;
        Assert.Equal(alias, preserved.MinecraftVersion);
        Assert.Null(preserved.FabricVersion);
        Assert.Equal(profile.CustomGameDir, preserved.CustomGameDir);
        Assert.NotEqual(profile.Id, service.GetActiveProfile().Id);
        Assert.True(GameVersionPolicy.RequiresBundledCore(service.GetActiveProfile().MinecraftVersion));
        GameVersionPolicy.ResolveVersionId(service.GetActiveProfile());
        Assert.Equal(service.GetActiveProfile().Id, new ProfileService(new PathService(dir.Path)).GetActiveProfile().Id);
    }

    [Fact]
    public void InvalidSupportedProfilesCannotPreventCreationOfSafeActiveDefault()
    {
        using var dir = new TestDirectory();
        var saved = new[] {
            new LauncherProfile { Id = "1.21.11", MinecraftVersion = "1.21.11", FabricVersion = "fabric-loader-0.19.3-26.2", CustomGameDir = "existing-world" },
            new LauncherProfile { Id = "26.2", MinecraftVersion = "26.2", FabricVersion = "custom-loader", CustomGameDir = "other-world" }
        };
        File.WriteAllText(Path.Combine(dir.Path, "profiles.json"),
            JsonSerializer.Serialize(new { ActiveProfileId = "1.21.11", Profiles = saved }));
        var service = new ProfileService(new PathService(dir.Path));
        Assert.Equal(3, service.GetProfiles().Count);
        Assert.Equal("existing-world", service.GetProfile("1.21.11")!.CustomGameDir);
        Assert.Equal(saved[0].FabricVersion, service.GetProfile("1.21.11")!.FabricVersion);
        Assert.Equal(saved[1].FabricVersion, service.GetProfile("26.2")!.FabricVersion);
        Assert.StartsWith("26.2-", service.GetActiveProfile().Id);
        Assert.Null(service.GetActiveProfile().CustomGameDir);
        GameVersionPolicy.ResolveVersionId(service.GetActiveProfile());
    }

    [Fact]
    public async Task ReturningGameDoesNotResurrectRemovedSharedAccount()
    {
        using var dir = new TestDirectory();
        var paths = new PathService(dir.Path);
        var profiles = new ProfileService(paths);
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
}
