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

/// <summary>Pack defaults for informational mod introductions. Never touches Essential account/consent state.</summary>
public static class ModWelcomeSettings
{
    public static async Task<IReadOnlyList<string>> PrepareAsync(string gameDirectory, CancellationToken cancellationToken = default)
    {
        var warnings = new List<string>();
        string config = Path.Combine(gameDirectory, "config");
        await UpdateAsync(Path.Combine(config, "fancymenu", "options.txt"), ConfigureFancyMenu);
        await UpdateAsync(Path.Combine(config, "Modpack Core Essentials", "custom_window.json"), DisableModpackWelcome);
        await UpdateAsync(Path.Combine(config, "forge.cfg"), DisableForgeVersionCheck);
        return warnings;

        async Task UpdateAsync(string path, Func<string, string> rewrite)
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
                warnings.Add($"Could not configure mod welcome screen in '{path}': {e.Message}");
            }
        }
    }

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
        var root = string.IsNullOrWhiteSpace(text) ? new JsonObject() :
            JsonNode.Parse(text, documentOptions: new JsonDocumentOptions { AllowTrailingCommas = true, CommentHandling = JsonCommentHandling.Skip }) as JsonObject
            ?? throw new JsonException("Expected a JSON object; existing file was preserved.");
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
