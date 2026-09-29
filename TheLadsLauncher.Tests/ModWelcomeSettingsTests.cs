using System.Text.Json.Nodes;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class ModWelcomeSettingsTests
{
    [Fact]
    public async Task FreshProfileDisablesBothIntroductionsAndSecondLaunchDoesNotRewrite()
    {
        using var dir = new TestDirectory();
        Assert.Empty(await ModWelcomeSettings.PrepareAsync(dir.Path));
        string fancy = Path.Combine(dir.Path, "config", "fancymenu", "options.txt");
        string mce = Path.Combine(dir.Path, "config", "Modpack Core Essentials", "custom_window.json");
        Assert.Contains("##[tutorial]\nB:show_welcome_screen = 'false';", File.ReadAllText(fancy));
        Assert.Equal("NEVER", JsonNode.Parse(File.ReadAllText(mce))!["welcomeMode"]!.GetValue<string>());
        var paths = new[] { fancy, mce };
        var bytes = paths.Select(File.ReadAllBytes).ToArray();
        var stamp = DateTime.UtcNow.AddDays(-1);
        foreach (var path in paths) File.SetLastWriteTimeUtc(path, stamp);
        Assert.Empty(await ModWelcomeSettings.PrepareAsync(dir.Path));
        for (int i = 0; i < paths.Length; i++)
        {
            Assert.Equal(bytes[i], File.ReadAllBytes(paths[i]));
            Assert.Equal(stamp, File.GetLastWriteTimeUtc(paths[i]));
        }
        Assert.False(Directory.Exists(Path.Combine(dir.Path, "essential")));
    }

    [Fact]
    public async Task ExistingPreferencesAndEssentialConsentSurviveLegacyWelcomeMigration()
    {
        using var dir = new TestDirectory();
        string fancy = Path.Combine(dir.Path, "config", "fancymenu", "options.txt");
        string mce = Path.Combine(dir.Path, "config", "Modpack Core Essentials", "custom_window.json");
        string consent = Path.Combine(dir.Path, "essential", "onboarding.json");
        const string original = "##[general]\r\nB:force_fullscreen = 'true';\r\n##[tutorial]\r\nB:show_welcome_screen = 'true';\r\n# my comment\r\n";
        Write(fancy, original);
        Write(mce, "{\"windowTitle\":\"My pack\",\"welcomeMode\":\"EVERY_LAUNCH\",\"showWelcomeOnStartup\":true,\"showWelcomeEveryTime\":true,\"custom\":{\"keep\":42}}");
        Write(consent, "{\"accepted_tos\":false}");
        Assert.Empty(await ModWelcomeSettings.PrepareAsync(dir.Path));
        Assert.Equal(original.Replace("show_welcome_screen = 'true'", "show_welcome_screen = 'false'"), File.ReadAllText(fancy));
        var json = JsonNode.Parse(File.ReadAllText(mce))!;
        Assert.Equal("NEVER", json["welcomeMode"]!.GetValue<string>());
        Assert.False(json["showWelcomeOnStartup"]!.GetValue<bool>());
        Assert.False(json["showWelcomeEveryTime"]!.GetValue<bool>());
        Assert.Equal("My pack", json["windowTitle"]!.GetValue<string>());
        Assert.Equal(42, json["custom"]!["keep"]!.GetValue<int>());
        Assert.Equal("{\"accepted_tos\":false}", File.ReadAllText(consent));
    }

    [Theory]
    [InlineData("{ broken config")]
    [InlineData("[]")]
    [InlineData("null")]
    public async Task InvalidConfigIsPreservedAndOtherModStillPrepared(string broken)
    {
        using var dir = new TestDirectory();
        string mce = Path.Combine(dir.Path, "config", "Modpack Core Essentials", "custom_window.json");
        Write(mce, broken);
        Assert.Single(await ModWelcomeSettings.PrepareAsync(dir.Path));
        Assert.Equal(broken, File.ReadAllText(mce));
        Assert.Contains("show_welcome_screen = 'false'", File.ReadAllText(Path.Combine(dir.Path, "config", "fancymenu", "options.txt")));
    }

    private static void Write(string path, string text)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllText(path, text);
    }
}
