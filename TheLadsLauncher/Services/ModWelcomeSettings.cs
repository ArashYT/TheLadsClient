using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>Pack defaults for informational mod introductions (<see cref="PrepareAsync"/>), and Essential's Terms of Service
/// and Discord status (<see cref="PrepareEssentialAsync"/>, a product decision disclosed by <see cref="EssentialNote"/>).</summary>
public static class ModWelcomeSettings
{
    /// <summary>Essential's Modrinth project: "essential" on 1.8.9 Forge, "essential-container" on Fabric.</summary>
    public const string EssentialProjectId = "k2ZPuTBm";
    public const string EssentialNote =
        "While enabled, Lads accepts Essential's Terms of Use and Privacy Policy for you (essential.gg/terms-of-use) and turns off its Discord status";
    private const string OnboardingFile = "onboarding.json";

    public static async Task<IReadOnlyList<string>> PrepareAsync(string gameDirectory, CancellationToken cancellationToken = default)
    {
        var warnings = new List<string>();
        string config = Path.Combine(gameDirectory, "config");
        const string what = "mod welcome screen";
        await UpdateAsync(Path.Combine(config, "fancymenu", "options.txt"), ConfigureFancyMenu, what, warnings, cancellationToken);
        await UpdateAsync(Path.Combine(config, "Modpack Core Essentials", "custom_window.json"), DisableModpackWelcome, what, warnings, cancellationToken);
        await UpdateAsync(Path.Combine(config, "forge.cfg"), DisableForgeVersionCheck, what, warnings, cancellationToken);
        return warnings;
    }

    /// <summary>
    /// Essential 1.5 (gg.essential.data.OnboardingData) reads its ToS answer, a plain "accepted_tos" boolean not tied to a
    /// ToS version, from the first onboarding.json that exists: &lt;.minecraft&gt;\essential, then the machine-wide
    /// gg.essential.mod folder, then &lt;gameDir&gt;\essential. The profile's own file is created; the machine-wide
    /// <paramref name="sharedOnboardingFiles"/> (<see cref="EssentialSharedOnboardingFiles"/>) are only updated when present.
    /// </summary>
    public static async Task<IReadOnlyList<string>> PrepareEssentialAsync(string gameDirectory, IEnumerable<string> sharedOnboardingFiles,
        CancellationToken cancellationToken = default)
    {
        var warnings = new List<string>();
        string essential = Path.Combine(gameDirectory, "essential");
        await UpdateAsync(Path.Combine(essential, OnboardingFile), AcceptEssentialTerms, "Essential", warnings, cancellationToken);
        foreach (string file in sharedOnboardingFiles)
            if (File.Exists(file)) await UpdateAsync(file, AcceptEssentialTerms, "Essential", warnings, cancellationToken);
        // Essential reads it at startup and only the player's in-game toggle changes it; it is turned off again every launch.
        await UpdateAsync(Path.Combine(essential, "config.toml"), DisableEssentialDiscordStatus, "Essential", warnings, cancellationToken);
        return warnings;
    }

    /// <summary>Where Essential keeps its machine-wide onboarding.json (gg.essential.util.MagicPathsKt).</summary>
    public static string[] EssentialSharedOnboardingFiles()
    {
        string minecraft = SharedContentService.OsDefaultRoot();
        string data = OperatingSystem.IsWindows() || OperatingSystem.IsMacOS() ? Path.GetDirectoryName(minecraft)!
            : Environment.GetEnvironmentVariable("XDG_DATA_HOME") is { Length: > 0 } xdg ? xdg
            : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".local", "share");
        return new[] { Path.Combine(minecraft, "essential", OnboardingFile), Path.Combine(data, "gg.essential.mod", OnboardingFile) };
    }

    private static async Task UpdateAsync(string path, Func<string, string> rewrite, string what, List<string> warnings, CancellationToken cancellationToken)
    {
        try
        {
            string before = File.Exists(path) ? await File.ReadAllTextAsync(path, cancellationToken) : "";
            string after = rewrite(before);
            if (after != before)
                await LockFiles.WriteAtomicallyAsync(path, Encoding.UTF8.GetBytes(after), cancellationToken);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException)
        {
            warnings.Add($"Could not configure {what} in '{path}': {e.Message}");
        }
    }

    private static string AcceptEssentialTerms(string text)
    {
        var root = ParseObject(text);
        if (root["accepted_tos"]?.GetValueKind() == JsonValueKind.True) return text;
        root["accepted_tos"] = true;
        return root.ToJsonString(new JsonSerializerOptions { WriteIndented = true }) + "\n";
    }

    private static string DisableEssentialDiscordStatus(string text)
    {
        // Essential's Vigilance TOML: "Share activity status on Discord" under [quality_of_life.discord_integration].
        // Edited in place; a second table header would make the whole file unreadable to Essential.
        const string setting = "set_activity_status_on_discord = false";
        string value = @"(?m)^([\t ]*set_activity_status_on_discord[\t ]*=[\t ]*)[^\r\n]*";
        if (Regex.IsMatch(text, value)) return Regex.Replace(text, value, "${1}false");
        string newline = text.Contains("\r\n", StringComparison.Ordinal) ? "\r\n" : "\n";
        var table = Regex.Match(text, @"(?m)^[\t ]*\[[\t ]*quality_of_life\.discord_integration[\t ]*\][^\r\n]*");
        if (table.Success) return text.Insert(table.Index + table.Length, newline + "\t\t" + setting);
        string separator = text.Length == 0 || text.EndsWith('\n') ? "" : newline;
        return text + separator + "[quality_of_life.discord_integration]" + newline + "\t" + setting + newline;
    }

    private static JsonObject ParseObject(string text) => string.IsNullOrWhiteSpace(text) ? new JsonObject() :
        JsonNode.Parse(text, documentOptions: new JsonDocumentOptions { AllowTrailingCommas = true, CommentHandling = JsonCommentHandling.Skip }) as JsonObject
        ?? throw new JsonException("Expected a JSON object; existing file was preserved.");

    private static string ConfigureFancyMenu(string text)
    {
        // FancyMenu's FancyConfig format: keep all other settings and comments verbatim. Modpack mode hides FancyMenu's
        // customization overlay and its shortcuts everywhere, and with them Drippy Loading Screen's title-screen edit button.
        text = SetFancyOption(text, "tutorial", "B:show_welcome_screen", "false");
        return SetFancyOption(text, "customization", "B:modpack_mode", "true");
    }

    private static string SetFancyOption(string text, string section, string key, string value)
    {
        string setting = $"{key} = '{value}';";
        string pattern = $@"(?m)^[\t ]*{Regex.Escape(key)}[\t ]*=[^\r\n]*";
        if (Regex.IsMatch(text, pattern)) return Regex.Replace(text, pattern, setting);
        string newline = text.Contains("\r\n", StringComparison.Ordinal) ? "\r\n" : "\n";
        return text + newline + "##[" + section + "]" + newline + setting + newline;
    }

    private static string DisableModpackWelcome(string text)
    {
        var root = ParseObject(text);
        // MCE 1.2 reads NEVER; older releases use these two booleans. Set legacy fields only
        // when present, because newer MCE migrates and removes them itself.
        bool changed = root["welcomeMode"]?.ToJsonString() != "\"NEVER\"";
        root["welcomeMode"] = "NEVER";
        foreach (string key in new[] { "showWelcomeOnStartup", "showWelcomeEveryTime" })
        {
            if (!root.ContainsKey(key)) continue;
            changed |= root[key]?.ToJsonString() != "false";
            root[key] = false;
        }
        return changed ? root.ToJsonString(new JsonSerializerOptions { WriteIndented = true }) + "\n" : text;
    }

    private static string DisableForgeVersionCheck(string text)
    {
        if (string.IsNullOrWhiteSpace(text)) return text;
        var modified = Regex.Replace(text, @"(?m)^[\t ]*B:disableVersionCheck[\t ]*=.*$", "    B:disableVersionCheck=true");
        modified = Regex.Replace(modified, @"(?ms)version_checking\s*\{\s*B:Global\s*=\s*true", "version_checking {\n    B:Global=false");
        return modified;
    }
}
