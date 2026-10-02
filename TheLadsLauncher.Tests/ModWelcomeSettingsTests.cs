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
        Assert.Contains("##[customization]\nB:modpack_mode = 'true';", File.ReadAllText(fancy));
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
        Assert.Equal(original.Replace("show_welcome_screen = 'true'", "show_welcome_screen = 'false'")
            + "\r\n##[customization]\r\nB:modpack_mode = 'true';\r\n", File.ReadAllText(fancy));
        var json = JsonNode.Parse(File.ReadAllText(mce))!;
        Assert.Equal("NEVER", json["welcomeMode"]!.GetValue<string>());
        Assert.False(json["showWelcomeOnStartup"]!.GetValue<bool>());
        Assert.False(json["showWelcomeEveryTime"]!.GetValue<bool>());
        Assert.Equal("My pack", json["windowTitle"]!.GetValue<string>());
        Assert.Equal(42, json["custom"]!["keep"]!.GetValue<int>());
        Assert.Equal("{\"accepted_tos\":false}", File.ReadAllText(consent));
    }

    [Fact]
    public async Task FancyMenuModpackModeIsTurnedOnInPlace()
    {
        using var dir = new TestDirectory();
        string fancy = Path.Combine(dir.Path, "config", "fancymenu", "options.txt");
        const string original = "##[customization]\nB:modpack_mode = 'false';\nB:show_customization_overlay = 'true';\n##[tutorial]\nB:show_welcome_screen = 'false';\n";
        Write(fancy, original);
        Assert.Empty(await ModWelcomeSettings.PrepareAsync(dir.Path));
        Assert.Equal(original.Replace("modpack_mode = 'false'", "modpack_mode = 'true'"), File.ReadAllText(fancy));
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

    [Fact]
    public async Task EssentialFreshProfileAcceptsTermsAndTurnsOffDiscordOnceThenLeavesFilesAlone()
    {
        using var dir = new TestDirectory();
        string shared = Path.Combine(dir.Path, "gg.essential.mod", "onboarding.json");
        Assert.Empty(await ModWelcomeSettings.PrepareEssentialAsync(dir.Path, new[] { shared }));
        string onboarding = Path.Combine(dir.Path, "essential", "onboarding.json");
        string config = Path.Combine(dir.Path, "essential", "config.toml");
        Assert.True(JsonNode.Parse(File.ReadAllText(onboarding))!["accepted_tos"]!.GetValue<bool>());
        Assert.Equal("[quality_of_life.discord_integration]\n\tset_activity_status_on_discord = false\n", File.ReadAllText(config));
        Assert.False(File.Exists(shared)); // Essential creates its machine-wide copy itself, from the profile's file
        var paths = new[] { onboarding, config };
        var bytes = paths.Select(File.ReadAllBytes).ToArray();
        var stamp = DateTime.UtcNow.AddDays(-1);
        foreach (var path in paths) File.SetLastWriteTimeUtc(path, stamp);
        Assert.Empty(await ModWelcomeSettings.PrepareEssentialAsync(dir.Path, new[] { shared }));
        for (int i = 0; i < paths.Length; i++)
        {
            Assert.Equal(bytes[i], File.ReadAllBytes(paths[i]));
            Assert.Equal(stamp, File.GetLastWriteTimeUtc(paths[i]));
        }
    }

    [Fact]
    public async Task EssentialEditsOnlyItsTwoKeysAndUpdatesTheMachineWideAnswerItReadsFirst()
    {
        using var dir = new TestDirectory();
        string onboarding = Path.Combine(dir.Path, "essential", "onboarding.json");
        string config = Path.Combine(dir.Path, "essential", "config.toml");
        string shared = Path.Combine(dir.Path, "gg.essential.mod", "onboarding.json");
        const string toml = "\r\n[general]\r\n\r\n\t[general.general]\r\n\t\tstreamer_mode = true\r\n\r\n[quality_of_life]\r\n\r\n"
            + "\t[quality_of_life.discord_integration]\r\n\t\tshow_username_and_avatar = false\r\n\t\tset_activity_status_on_discord = true\r\n"
            + "\t\tallow_ask_to_join = false\r\n\r\n[__meta]\r\n\tversion = 12\r\n";
        Write(onboarding, "{\n    \"seen_server_discovery\": true,\n    \"has_shown_wiki_toast\": true\n}");
        Write(config, toml);
        Write(shared, "{\"accepted_tos\":false,\"sent_auto_update_telemetry\":true}");
        Assert.Empty(await ModWelcomeSettings.PrepareEssentialAsync(dir.Path, new[] { shared }));
        Assert.Equal(toml.Replace("set_activity_status_on_discord = true", "set_activity_status_on_discord = false"), File.ReadAllText(config));
        var local = JsonNode.Parse(File.ReadAllText(onboarding))!;
        Assert.True(local["accepted_tos"]!.GetValue<bool>());
        Assert.True(local["seen_server_discovery"]!.GetValue<bool>());
        Assert.True(local["has_shown_wiki_toast"]!.GetValue<bool>());
        var global = JsonNode.Parse(File.ReadAllText(shared))!;
        Assert.True(global["accepted_tos"]!.GetValue<bool>());
        Assert.True(global["sent_auto_update_telemetry"]!.GetValue<bool>());

        // The table without the key: the key goes inside it, never into a second [quality_of_life.discord_integration].
        const string tableOnly = "[quality_of_life]\n\t[quality_of_life.discord_integration]\n\t\tallow_ask_to_join = true\n[cosmetics]\n";
        Write(config, tableOnly);
        Assert.Empty(await ModWelcomeSettings.PrepareEssentialAsync(dir.Path, Array.Empty<string>()));
        Assert.Equal(tableOnly.Replace("discord_integration]\n", "discord_integration]\n\t\tset_activity_status_on_discord = false\n"),
            File.ReadAllText(config));
    }

    [Fact]
    public async Task EssentialAlreadyAcceptedAndOffIsNotRewritten()
    {
        using var dir = new TestDirectory();
        string onboarding = Path.Combine(dir.Path, "essential", "onboarding.json");
        string config = Path.Combine(dir.Path, "essential", "config.toml");
        const string json = "{\n    \"accepted_tos\": true,\n    \"seen_share_server_with_friends_option\": true\n}";
        const string toml = "[quality_of_life]\n\n\t[quality_of_life.discord_integration]\n\t\tset_activity_status_on_discord = false\n";
        Write(onboarding, json);
        Write(config, toml);
        var stamp = DateTime.UtcNow.AddDays(-1);
        File.SetLastWriteTimeUtc(onboarding, stamp);
        File.SetLastWriteTimeUtc(config, stamp);
        Assert.Empty(await ModWelcomeSettings.PrepareEssentialAsync(dir.Path, Array.Empty<string>()));
        Assert.Equal(json, File.ReadAllText(onboarding));
        Assert.Equal(toml, File.ReadAllText(config));
        Assert.Equal(stamp, File.GetLastWriteTimeUtc(onboarding));
        Assert.Equal(stamp, File.GetLastWriteTimeUtc(config));
    }

    [Theory]
    [InlineData("{ \"accepted_tos\": fal")]
    [InlineData("[]")]
    public async Task MalformedEssentialOnboardingIsPreservedWithAWarningAndDiscordStillTurnedOff(string broken)
    {
        using var dir = new TestDirectory();
        string onboarding = Path.Combine(dir.Path, "essential", "onboarding.json");
        Write(onboarding, broken);
        Assert.Single(await ModWelcomeSettings.PrepareEssentialAsync(dir.Path, Array.Empty<string>()));
        Assert.Equal(broken, File.ReadAllText(onboarding));
        Assert.Contains("set_activity_status_on_discord = false", File.ReadAllText(Path.Combine(dir.Path, "essential", "config.toml")));
    }

    [Fact]
    public async Task ProfilePreparationConfiguresEssentialUnlessThePlayerTurnedItOff()
    {
        using var dir = new TestDirectory(); // deletes the shared-folder links it creates
        var paths = new PathService(Path.Combine(dir.Path, "launcher"));
        var profiles = new ProfileService(paths, new SharedContentService(Path.Combine(dir.Path, "global")));
        var profile = profiles.CreateProfile("Custom", "26.3", 25, false, "0.19.5");
        string game = paths.GetProfileDirectory(profile), essential = Path.Combine(game, "essential");
        await profiles.PrepareProfileEnvironmentAsync(profile, null);
        Assert.True(JsonNode.Parse(File.ReadAllText(Path.Combine(essential, "onboarding.json")))!["accepted_tos"]!.GetValue<bool>());
        Assert.Contains("set_activity_status_on_discord = false", File.ReadAllText(Path.Combine(essential, "config.toml")));

        SafeFileOps.DeleteTree(essential);
        await ModPreferences.UpdateAsync(game, root => ModPreferences.SetMod(root, "essential-container", false, ModWelcomeSettings.EssentialProjectId));
        await profiles.PrepareProfileEnvironmentAsync(profile, null);
        Assert.False(Directory.Exists(essential));
    }

    private static void Write(string path, string text)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllText(path, text);
    }
}
