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
}
