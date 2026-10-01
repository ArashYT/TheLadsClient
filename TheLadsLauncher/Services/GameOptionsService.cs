using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace TheLadsLauncher.Services;

public static class GameOptionsService
{
    private static readonly Dictionary<string, int> ModernToLwjgl2 = new(StringComparer.OrdinalIgnoreCase)
    {
        ["key.keyboard.escape"] = 1,
        ["key.keyboard.1"] = 2,
        ["key.keyboard.2"] = 3,
        ["key.keyboard.3"] = 4,
        ["key.keyboard.4"] = 5,
        ["key.keyboard.5"] = 6,
        ["key.keyboard.6"] = 7,
        ["key.keyboard.7"] = 8,
        ["key.keyboard.8"] = 9,
        ["key.keyboard.9"] = 10,
        ["key.keyboard.0"] = 11,
        ["key.keyboard.minus"] = 12,
        ["key.keyboard.equal"] = 13,
        ["key.keyboard.backspace"] = 14,
        ["key.keyboard.tab"] = 15,
        ["key.keyboard.q"] = 16,
        ["key.keyboard.w"] = 17,
        ["key.keyboard.e"] = 18,
        ["key.keyboard.r"] = 19,
        ["key.keyboard.t"] = 20,
        ["key.keyboard.y"] = 21,
        ["key.keyboard.u"] = 22,
        ["key.keyboard.i"] = 23,
        ["key.keyboard.o"] = 24,
        ["key.keyboard.p"] = 25,
        ["key.keyboard.left.bracket"] = 26,
        ["key.keyboard.right.bracket"] = 27,
        ["key.keyboard.enter"] = 28,
        ["key.keyboard.left.control"] = 29,
        ["key.keyboard.a"] = 30,
        ["key.keyboard.s"] = 31,
        ["key.keyboard.d"] = 32,
        ["key.keyboard.f"] = 33,
        ["key.keyboard.g"] = 34,
        ["key.keyboard.h"] = 35,
        ["key.keyboard.j"] = 36,
        ["key.keyboard.k"] = 37,
        ["key.keyboard.l"] = 38,
        ["key.keyboard.semicolon"] = 39,
        ["key.keyboard.apostrophe"] = 40,
        ["key.keyboard.grave.accent"] = 41,
        ["key.keyboard.left.shift"] = 42,
        ["key.keyboard.backslash"] = 43,
        ["key.keyboard.z"] = 44,
        ["key.keyboard.x"] = 45,
        ["key.keyboard.c"] = 46,
        ["key.keyboard.v"] = 47,
        ["key.keyboard.b"] = 48,
        ["key.keyboard.n"] = 49,
        ["key.keyboard.m"] = 50,
        ["key.keyboard.comma"] = 51,
        ["key.keyboard.period"] = 52,
        ["key.keyboard.slash"] = 53,
        ["key.keyboard.right.shift"] = 54,
        ["key.keyboard.keypad.multiply"] = 55,
        ["key.keyboard.left.alt"] = 56,
        ["key.keyboard.space"] = 57,
        ["key.keyboard.caps.lock"] = 58,
        ["key.keyboard.f1"] = 59,
        ["key.keyboard.f2"] = 60,
        ["key.keyboard.f3"] = 61,
        ["key.keyboard.f4"] = 62,
        ["key.keyboard.f5"] = 63,
        ["key.keyboard.f6"] = 64,
        ["key.keyboard.f7"] = 65,
        ["key.keyboard.f8"] = 66,
        ["key.keyboard.f9"] = 67,
        ["key.keyboard.f10"] = 68,
        ["key.keyboard.num.lock"] = 69,
        ["key.keyboard.scroll.lock"] = 70,
        ["key.keyboard.keypad.7"] = 71,
        ["key.keyboard.keypad.8"] = 72,
        ["key.keyboard.keypad.9"] = 73,
        ["key.keyboard.keypad.subtract"] = 74,
        ["key.keyboard.keypad.4"] = 75,
        ["key.keyboard.keypad.5"] = 76,
        ["key.keyboard.keypad.6"] = 77,
        ["key.keyboard.keypad.add"] = 78,
        ["key.keyboard.keypad.1"] = 79,
        ["key.keyboard.keypad.2"] = 80,
        ["key.keyboard.keypad.3"] = 81,
        ["key.keyboard.keypad.0"] = 82,
        ["key.keyboard.keypad.decimal"] = 83,
        ["key.keyboard.f11"] = 87,
        ["key.keyboard.f12"] = 88,
        ["key.keyboard.f13"] = 100,
        ["key.keyboard.f14"] = 101,
        ["key.keyboard.f15"] = 102,
        ["key.keyboard.f16"] = 103,
        ["key.keyboard.f17"] = 104,
        ["key.keyboard.f18"] = 105,
        ["key.keyboard.f19"] = 106,
        ["key.keyboard.keypad.enter"] = 156,
        ["key.keyboard.right.control"] = 157,
        ["key.keyboard.keypad.divide"] = 181,
        ["key.keyboard.print.screen"] = 183,
        ["key.keyboard.right.alt"] = 184,
        ["key.keyboard.pause"] = 197,
        ["key.keyboard.home"] = 199,
        ["key.keyboard.up"] = 200,
        ["key.keyboard.page.up"] = 201,
        ["key.keyboard.left"] = 203,
        ["key.keyboard.right"] = 205,
        ["key.keyboard.end"] = 207,
        ["key.keyboard.down"] = 208,
        ["key.keyboard.page.down"] = 209,
        ["key.keyboard.insert"] = 210,
        ["key.keyboard.delete"] = 211,
        // Mouse buttons: in LWJGL2 mouse buttons are negative offsets: Button 0 = -100, Button 1 = -99, Button 2 = -98, Button 3 = -97, Button 4 = -96
        ["key.mouse.left"] = -100,
        ["key.mouse.right"] = -99,
        ["key.mouse.middle"] = -98,
        ["key.mouse.4"] = -97,
        ["key.mouse.5"] = -96
    };

    private static readonly Dictionary<int, string> Lwjgl2ToModern =
        ModernToLwjgl2.GroupBy(kvp => kvp.Value).ToDictionary(g => g.Key, g => g.First().Key);

    private static readonly HashSet<string> SharedSettingsKeys = new(StringComparer.OrdinalIgnoreCase)
    {
        "fov", "gamma", "mouseSensitivity", "invertYMouse", "viewBobbing", "guiScale",
        "chatVisibility", "chatColors", "chatLinks", "chatLinksPrompt", "chatOpacity",
        "chatScale", "chatWidth", "chatHeightFocused", "chatHeightUnfocused",
        "difficulty", "hideServerAddress", "advancedItemTooltips", "pauseOnLostFocus",
        "soundCategory_master", "soundCategory_music", "soundCategory_record",
        "soundCategory_weather", "soundCategory_block", "soundCategory_hostile",
        "soundCategory_neutral", "soundCategory_player", "soundCategory_ambient",
        "soundCategory_voice"
    };

    public static bool IsLegacy18(string mcVersion) =>
        mcVersion == "1.8.9" || mcVersion.StartsWith("1.8", StringComparison.Ordinal);

    public static Dictionary<string, string> ParseOptions(string text)
    {
        var map = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        if (string.IsNullOrWhiteSpace(text)) return map;
        using var reader = new StringReader(text);
        string? line;
        while ((line = reader.ReadLine()) != null)
        {
            int colon = line.IndexOf(':');
            if (colon <= 0) continue;
            string key = line.Substring(0, colon).Trim();
            string val = line.Substring(colon + 1).Trim();
            map[key] = val;
        }
        return map;
    }

    public static string SerializeOptions(Dictionary<string, string> map)
    {
        var sb = new System.Text.StringBuilder();
        foreach (var kvp in map)
        {
            sb.Append(kvp.Key).Append(':').Append(kvp.Value).Append('\n');
        }
        return sb.ToString();
    }

    public static string TranslateKeybindToTarget(string keyName, string value, bool targetIs18)
    {
        if (targetIs18)
        {
            // Modern to LWJGL2 int
            if (int.TryParse(value, out int existingInt)) return existingInt.ToString();
            if (ModernToLwjgl2.TryGetValue(value, out int lwjglCode)) return lwjglCode.ToString();
            return value;
        }
        else
        {
            // LWJGL2 int to Modern string
            if (int.TryParse(value, out int lwjglCode) && Lwjgl2ToModern.TryGetValue(lwjglCode, out string? modernKey))
            {
                return modernKey;
            }
            return value;
        }
    }

    public static void SyncToInstance(string sharedFile, string instanceFile, string mcVersion)
    {
        if (!File.Exists(sharedFile))
        {
            if (File.Exists(instanceFile))
            {
                SyncFromInstance(instanceFile, sharedFile, mcVersion);
            }
            return;
        }

        var sharedText = File.ReadAllText(sharedFile);
        var sharedMap = ParseOptions(sharedText);
        if (sharedMap.Count == 0 && !string.IsNullOrWhiteSpace(sharedText))
        {
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(instanceFile))!);
            File.WriteAllText(instanceFile, sharedText);
            return;
        }

        bool targetIs18 = IsLegacy18(mcVersion);
        var instanceMap = File.Exists(instanceFile) ? ParseOptions(File.ReadAllText(instanceFile)) : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

        // Merge shared settings and controls without overwriting version-exclusive settings
        foreach (var kvp in sharedMap)
        {
            if (kvp.Key.StartsWith("key_", StringComparison.OrdinalIgnoreCase))
            {
                instanceMap[kvp.Key] = TranslateKeybindToTarget(kvp.Key, kvp.Value, targetIs18);
            }
            else if (SharedSettingsKeys.Contains(kvp.Key) || !instanceMap.ContainsKey(kvp.Key))
            {
                instanceMap[kvp.Key] = kvp.Value;
            }
        }

        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(instanceFile))!);
        File.WriteAllText(instanceFile, SerializeOptions(instanceMap));
    }

    public static void SyncFromInstance(string instanceFile, string sharedFile, string mcVersion)
    {
        if (!File.Exists(instanceFile)) return;
        var instanceText = File.ReadAllText(instanceFile);
        var instanceMap = ParseOptions(instanceText);
        if (instanceMap.Count == 0 && !string.IsNullOrWhiteSpace(instanceText))
        {
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(sharedFile))!);
            File.WriteAllText(sharedFile, instanceText);
            return;
        }

        bool sourceIs18 = IsLegacy18(mcVersion);
        var sharedMap = File.Exists(sharedFile) ? ParseOptions(File.ReadAllText(sharedFile)) : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

        foreach (var kvp in instanceMap)
        {
            if (kvp.Key.StartsWith("key_", StringComparison.OrdinalIgnoreCase))
            {
                sharedMap[kvp.Key] = TranslateKeybindToTarget(kvp.Key, kvp.Value, false); // Store modern representation in shared
            }
            else
            {
                sharedMap[kvp.Key] = kvp.Value;
            }
        }

        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(sharedFile))!);
        File.WriteAllText(sharedFile, SerializeOptions(sharedMap));
    }
}
