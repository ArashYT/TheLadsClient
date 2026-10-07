using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;

namespace TheLadsLauncher.Services;

public static class GameOptionsService
{
    private static readonly Dictionary<string, int> ModernToLwjgl2 = new(StringComparer.OrdinalIgnoreCase)
    {
        ["key.keyboard.unknown"] = 0, // unbound
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
        ["key.keyboard.f19"] = 113,
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
        // Mouse buttons: 1.8.9 stores button b as b - 100 (left -100). The side buttons and any further ones: see TranslateKeybindToTarget.
        ["key.mouse.left"] = -100,
        ["key.mouse.right"] = -99,
        ["key.mouse.middle"] = -98
    };

    /// <summary>Next to an options.txt the launcher syncs: the shared (or Lunar) values it took at the last launch. A launch takes
    /// only what changed there since, so it never undoes a key bind or setting the player changed in that game meanwhile.</summary>
    public const string TakenOptionsFile = "lads-options-taken.txt";

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
        "soundCategory_voice",
        // Settings → Controls edits these for every version at once (SharedControls); a change made in one game follows too.
        "renderDistance", "simulationDistance", "maxFps", "enableVsync"
    };

    public static bool IsLegacy18(string mcVersion) =>
        mcVersion == "1.8.9" || mcVersion.StartsWith("1.8", StringComparison.Ordinal);

    /// <summary>Lunar Client's folder: LADS_LUNAR_DIR, else %USERPROFILE%\.lunarclient. Only ever read, never written, moved or deleted.</summary>
    public const string LunarEnvironmentVariable = "LADS_LUNAR_DIR";

    public static string LunarRoot() => Environment.GetEnvironmentVariable(LunarEnvironmentVariable) is { Length: > 0 } root ? root
        : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".lunarclient");

    /// <summary>Lunar's 1.8 profile options.txt (modern key names), or null when Lunar is not installed.</summary>
    public static string? LunarOptions18(string? lunarRoot = null) =>
        Path.Combine(lunarRoot ?? LunarRoot(), "profiles", "1.8", "options.txt") is var file && File.Exists(file) ? file : null;

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

    /// <summary>A key bind as the target version stores it: 1.8.9 an LWJGL2 code (mouse button b is b - 100), the others a key
    /// name ("key.mouse.4" is button 3). Null when the target has no code or name for it.</summary>
    public static string? TranslateKeybindToTarget(string keyName, string value, bool targetIs18)
    {
        if (targetIs18)
        {
            if (int.TryParse(value, out _)) return value;
            if (ModernToLwjgl2.TryGetValue(value, out int lwjglCode)) return lwjglCode.ToString();
            return value.StartsWith("key.mouse.", StringComparison.Ordinal) && int.TryParse(value[10..], out int number) && number is >= 4 and <= 100
                ? (number - 101).ToString() : null;
        }
        if (!int.TryParse(value, out int code)) return value;
        if (Lwjgl2ToModern.TryGetValue(code, out string? modernKey)) return modernKey;
        return code is >= -97 and < 0 ? "key.mouse." + (code + 101) : null;
    }

    /// <summary>Before a launch: the shared (or Lunar) settings and key binds into the game's options.txt. Only the ones changed there
    /// since the last launch (<see cref="TakenOptionsFile"/>) replace the game's own; any setting the game lacks is filled in.
    /// A key bind the target has no code or name for keeps the game's own.</summary>
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

        var sharedMap = ParseOptions(File.ReadAllText(sharedFile));
        bool targetIs18 = IsLegacy18(mcVersion);
        var instanceMap = File.Exists(instanceFile) ? ParseOptions(File.ReadAllText(instanceFile)) : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        var takenFile = Path.Combine(Path.GetDirectoryName(Path.GetFullPath(instanceFile))!, TakenOptionsFile);
        // None yet (the first launch since 1.7.2): the game's own values stay.
        var taken = File.Exists(takenFile) ? ParseOptions(File.ReadAllText(takenFile)) : null;

        bool updated = false;
        foreach (var kvp in sharedMap)
        {
            bool keybind = kvp.Key.StartsWith("key_", StringComparison.OrdinalIgnoreCase);
            bool changed = taken != null && (keybind || SharedSettingsKeys.Contains(kvp.Key))
                && !(taken.TryGetValue(kvp.Key, out var was) && was == kvp.Value);
            if (!changed && instanceMap.ContainsKey(kvp.Key)) continue;
            // Key binds in the target's format; 1.8.9 has no simulationDistance and its GUI scale stops at Large.
            if (SharedControls.ToVersion(kvp.Key, kvp.Value, mcVersion) is not { } value
                || instanceMap.TryGetValue(kvp.Key, out var had) && had == value) continue;
            instanceMap[kvp.Key] = value;
            updated = true;
        }

        if (updated) WriteAtomically(instanceFile, SerializeOptions(instanceMap));
        WriteAtomically(takenFile, SerializeOptions(sharedMap));
    }

    /// <summary>After a game: its settings and key binds (in key names) into the shared copy. 1.8.9's own formats never go there,
    /// and a 1.8.9 key code without a key name leaves the shared bind as it was.</summary>
    public static void SyncFromInstance(string instanceFile, string sharedFile, string mcVersion)
    {
        if (!File.Exists(instanceFile)) return;
        var instanceMap = ParseOptions(File.ReadAllText(instanceFile));
        bool sourceIs18 = IsLegacy18(mcVersion);
        var sharedMap = File.Exists(sharedFile) ? ParseOptions(File.ReadAllText(sharedFile)) : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

        foreach (var kvp in instanceMap)
        {
            if (kvp.Key.StartsWith("key_", StringComparison.OrdinalIgnoreCase))
            {
                if (TranslateKeybindToTarget(kvp.Key, kvp.Value, false) is { } modern) sharedMap[kvp.Key] = modern;
            }
            else if (!sourceIs18 || SharedSettingsKeys.Contains(kvp.Key))
            {
                // 1.8's own formats (lang:en_US, its resourcePacks list, fancyGraphics...) never reach the shared copy.
                sharedMap[kvp.Key] = kvp.Value;
            }
        }

        WriteAtomically(sharedFile, SerializeOptions(sharedMap));
    }

    // A crash or a closed launcher mid-write leaves the old file whole, never a cut-off one. An unchanged file is not rewritten.
    private static void WriteAtomically(string file, string text)
    {
        if (!File.Exists(file) || File.ReadAllText(file) != text)
            LockFiles.WriteAtomicallyAsync(file, Encoding.UTF8.GetBytes(text)).GetAwaiter().GetResult();
    }
}
