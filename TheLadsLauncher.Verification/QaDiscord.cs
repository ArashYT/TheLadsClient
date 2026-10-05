using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

/// <summary>
/// QA games never show on the owner's Discord: before every launch, Essential's "set activity status on Discord" and the Lads
/// DiscordRPC module are switched off in the sandbox game folder (essential/config.toml, thelads_config.json), as the QA
/// options are forced in options.txt. Nothing outside the given folder is read or written.
/// </summary>
static class QaDiscord
{
    const string Section = "[quality_of_life.discord_integration]", Key = "set_activity_status_on_discord";

    public static void ForceOff(string gameDirectory)
    {
        string essential = Path.Combine(gameDirectory, "essential", "config.toml");
        Directory.CreateDirectory(Path.GetDirectoryName(essential)!);
        File.WriteAllText(essential, EssentialOff(File.Exists(essential) ? File.ReadAllText(essential) : ""));
        string lads = Path.Combine(gameDirectory, "thelads_config.json");
        var config = File.Exists(lads) ? JsonNode.Parse(File.ReadAllText(lads)) as JsonObject ?? new JsonObject() : new JsonObject();
        File.WriteAllText(lads, LadsOff(config).ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
    }

    /// <summary>Essential's TOML with the activity status off: the key set, added under its table, or the table added.</summary>
    public static string EssentialOff(string toml)
    {
        string nl = toml.Contains("\r\n") ? "\r\n" : "\n";
        var key = new Regex(@"^(\s*)" + Key + @"\s*=.*$", RegexOptions.Multiline);
        if (key.IsMatch(toml)) return key.Replace(toml, m => m.Groups[1].Value + Key + " = false" + (m.Value.EndsWith('\r') ? "\r" : ""), 1);
        int table = toml.IndexOf(Section, StringComparison.Ordinal);
        if (table >= 0)
        {
            int end = toml.IndexOf('\n', table);
            return end < 0 ? toml + nl + "\t\t" + Key + " = false" + nl : toml.Insert(end + 1, "\t\t" + Key + " = false" + nl);
        }
        return toml + (toml.Length == 0 || toml.EndsWith('\n') ? "" : nl) + Section + nl + Key + " = false" + nl;
    }

    /// <summary>The Lads config with DiscordRPC off. Its pre-1.7.1 "Share activity" option goes, or Core would start it from the defaults (on).</summary>
    public static JsonObject LadsOff(JsonObject config)
    {
        if (config["modules"] is not JsonObject modules) config["modules"] = modules = new JsonObject();
        if (modules["DiscordRPC"] is not JsonObject discord) modules["DiscordRPC"] = discord = new JsonObject();
        discord["enabled"] = false;
        (discord["options"] as JsonObject)?.Remove("Share activity");
        return config;
    }
}
