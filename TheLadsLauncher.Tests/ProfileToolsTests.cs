using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ProfileToolsTests
{
    [Fact]
    public void FavoriteAndImportedSettingsSurviveProfileReload()
    {
        using var dir = new TestDirectory();
        var paths = new PathService(dir.Path);
        var shared = new SharedContentService(Path.Combine(dir.Path, "global"));
        var service = new ProfileService(paths, shared);
        var copy = ProfileTools.Import(new(1, "Favorite copy", "26.3", "0.19.5", new() { ["options.txt"] = "guiScale:3\n" }), paths.ProfilesDirectory);
        copy.IsFavorite = true; service.SaveProfile(copy);
        var loaded = new ProfileService(paths, shared).GetProfile(copy.Id)!;
        Assert.True(loaded.IsFavorite); Assert.True(loaded.IsIsolated);
        Assert.Equal("guiScale:3\n", File.ReadAllText(Path.Combine(paths.GetProfileDirectory(loaded), "options.txt")));
    }
    [Fact]
    public void FavoritesAndCombinedFiltersAreDeterministic()
    {
        var profiles = new[] { new LauncherProfile { Name = "Creative", MinecraftVersion = "26.2" }, new LauncherProfile { Name = "Survival", MinecraftVersion = "26.3", IsFavorite = true } };
        Assert.Equal("Survival", ProfileTools.Filter(profiles, null, null).First().Name);
        Assert.Equal("Creative", Assert.Single(ProfileTools.Filter(profiles, "CREATIVE", "26.2")).Name);
        Assert.Empty(ProfileTools.Filter(profiles, "Creative", "26.3"));
    }

    [Fact]
    public void PresetRoundTripCreatesIndependentSettingsWithoutAccountsOrLocalPaths()
    {
        using var dir = new TestDirectory();
        string source = Path.Combine(dir.Path, "source"); Directory.CreateDirectory(source);
        File.WriteAllText(Path.Combine(source, "options.txt"), "guiScale:2\n");
        File.WriteAllText(Path.Combine(source, "thelads_config.json"), "{\"test\":true}");
        File.WriteAllText(Path.Combine(source, "accounts.json"), "private-token");
        var original = new LauncherProfile { Name = "My profile", CustomGameDir = source, CustomJavaPath = "private-path", PackwizUrl = "https://private.invalid", IsFavorite = true };
        var preset = ProfileTools.Capture(original, source);
        string json = ProfileTools.Serialize(preset);
        Assert.DoesNotContain("private", json);
        string file = Path.Combine(dir.Path, "preset.json"); File.WriteAllText(file, json);
        var imported = ProfileTools.Import(ProfileTools.Read(file), Path.Combine(dir.Path, "profiles"));
        Assert.NotEqual(original.Id, imported.Id);
        Assert.True(imported.IsIsolated);
        Assert.Null(imported.CustomGameDir); Assert.Null(imported.CustomJavaPath); Assert.Null(imported.PackwizUrl);
        Assert.Equal("guiScale:2\n", File.ReadAllText(Path.Combine(dir.Path, "profiles", imported.Id, "options.txt")));
        Assert.Equal("private-token", File.ReadAllText(Path.Combine(source, "accounts.json")));
    }

    [Theory]
    [InlineData("../settings.json")]
    [InlineData("C:\\accounts.json")]
    [InlineData("config/../options.txt")]
    [InlineData("accounts.json")]
    public void ArbitraryPathsAreRejectedBeforeCreatingAProfile(string name)
    {
        using var dir = new TestDirectory();
        var preset = new ProfileTools.Preset(1, "Bad", "26.3", "0.19.5", new() { [name] = "data" });
        string target = Path.Combine(dir.Path, "profiles");
        Assert.Throws<InvalidDataException>(() => ProfileTools.Import(preset, target));
        Assert.False(Directory.Exists(target));
    }

    [Fact]
    public void OversizedAndUnknownFormatsAreRejected()
    {
        Assert.Throws<InvalidDataException>(() => ProfileTools.Validate(new(2, "No", "26.3", null, new())));
        Assert.Throws<InvalidDataException>(() => ProfileTools.Validate(new(1, "No", "26.3", null, new() { ["options.txt"] = new string('x', 2 * 1024 * 1024 + 1) })));
    }

    [Fact]
    public void PlayMenuListsNewestVersionFirstAndOffersOnly263_262_189()
    {
        var profiles = new[] { "1.8.9", "1.21.1", "26.2", "1.21.11", "26.3" }.Select(v => new LauncherProfile { Name = "P " + v, MinecraftVersion = v })
            .Append(new LauncherProfile { Name = ProfileService.LatestReleaseName, MinecraftVersion = "26.3" });
        Assert.Equal(new[] { "Latest Release", "P 26.3", "P 26.2", "P 1.21.11", "P 1.21.1", "P 1.8.9" }, ProfileTools.NewestFirst(profiles).Select(p => p.Name));
        Assert.Equal(new[] { "26.3", "26.2", "1.8.9" }, ProfileTools.PlayableVersions);
    }

    [Fact]
    public void LatestReleaseProfileFollowsTheNewestVersion()
    {
        using var dir = new TestDirectory();
        var root = Path.Combine(dir.Path, "launcher");
        Directory.CreateDirectory(root);
        File.WriteAllText(Path.Combine(root, "profiles.json"), System.Text.Json.JsonSerializer.Serialize(new { ActiveProfileId = "latest", Profiles = new[] {
            new LauncherProfile { Id = "latest", Name = ProfileService.LatestReleaseName, MinecraftVersion = "26.2", FabricVersion = "fabric-loader-0.19.5-26.2", JavaMajorVersion = 25 },
            new LauncherProfile { Id = "alias", Name = "Old alias", MinecraftVersion = "latest-release", FabricVersion = "0.19.5", JavaMajorVersion = 25 },
            new LauncherProfile { Id = "mine", Name = "Mine", MinecraftVersion = "26.2", FabricVersion = "0.19.5", JavaMajorVersion = 25 } } }));
        var service = new ProfileService(new PathService(root), new SharedContentService(Path.Combine(dir.Path, "global")));
        Assert.Equal("26.3", ProfileService.NewestVersion);
        Assert.Equal(("26.3", "fabric-loader-0.19.5-26.3"), (service.GetProfile("latest")!.MinecraftVersion, service.GetProfile("latest")!.FabricVersion));
        Assert.Equal("26.3", service.GetProfile("alias")!.MinecraftVersion);
        Assert.Equal("26.2", service.GetProfile("mine")!.MinecraftVersion);
    }

    [Theory]
    [InlineData(4, 2)] [InlineData(6, 3)] [InlineData(8, 4)] [InlineData(12, 5)] [InlineData(16, 6)] [InlineData(24, 8)] [InlineData(64, 8)] [InlineData(0, 4)]
    public void RecommendedRamFollowsSystemMemory(int systemGb, int expectedGb) =>
        Assert.Equal(expectedGb * 1024, LauncherSettings.RecommendedRamMb(systemGb * 1024L * 1024 * 1024));
}
