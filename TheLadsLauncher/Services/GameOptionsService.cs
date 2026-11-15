using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

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
        ["key.keyboard.keypad.equal"] = 141,
        ["key.keyboard.left.win"] = 219,
        ["key.keyboard.right.win"] = 220,
        ["key.keyboard.menu"] = 221,
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

    /// <summary>Before a launch: the shared settings and controls (<see cref="SharedControls.IsControl"/>) into the game's options.txt.
    /// Only the ones changed there since the last launch (<see cref="TakenOptionsFile"/>) replace the game's own; any setting the game
    /// lacks is filled in. A key bind the target has no code or name for keeps the game's own. <paramref name="settingsFile"/> (1.8.9:
    /// Lunar's 1.8 profile) gives every setting except the controls, which are the same for every version.</summary>
    public static void SyncToInstance(string sharedFile, string instanceFile, string mcVersion, string? settingsFile = null)
    {
        // No shared copy yet: this game's options start it.
        if (!File.Exists(sharedFile) && File.Exists(instanceFile))
            SyncFromInstance(instanceFile, sharedFile, mcVersion, controlsOnly: settingsFile != null);

        var sharedMap = File.Exists(sharedFile) ? ParseOptions(File.ReadAllText(sharedFile)) : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        if (settingsFile != null)
        {
            var settings = ParseOptions(File.ReadAllText(settingsFile));
            foreach (var key in settings.Keys.Where(SharedControls.IsControl).ToList()) settings.Remove(key);
            foreach (var (key, value) in sharedMap) if (SharedControls.IsControl(key)) settings[key] = value;
            sharedMap = settings;
        }
        bool targetIs18 = IsLegacy18(mcVersion);
        bool instanceExisted = File.Exists(instanceFile);
        var instanceMap = instanceExisted ? ParseOptions(File.ReadAllText(instanceFile)) : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        var takenFile = Path.Combine(Path.GetDirectoryName(Path.GetFullPath(instanceFile))!, TakenOptionsFile);
        // None yet (the first launch since 1.7.2, a new or no longer isolated profile): the game's own settings stay, the controls
        // become the shared ones. So too after a session that could not load its options.txt: the game saved its defaults over them.
        var taken = File.Exists(takenFile) && !OptionsFailedToLoad(Path.GetDirectoryName(takenFile)!) ? ParseOptions(File.ReadAllText(takenFile)) : null;

        bool updated = false;
        if (targetIs18 && !instanceExisted && !instanceMap.ContainsKey("useVbo"))
        {
            instanceMap["useVbo"] = "true";
            updated = true;
        }
        foreach (var kvp in sharedMap)
        {
            // Another version's data version would make Minecraft run the wrong option fixes; 1.8.9 has none (EnsureDataVersion).
            if (kvp.Key.Equals("version", StringComparison.OrdinalIgnoreCase)) continue;
            bool control = SharedControls.IsControl(kvp.Key);
            bool changed = taken == null ? control : (control || SharedSettingsKeys.Contains(kvp.Key))
                && !(taken.TryGetValue(kvp.Key, out var was) && was == kvp.Value);
            string name = SharedControls.OptionKey(kvp.Key, targetIs18);
            if (!changed && instanceMap.ContainsKey(name)) continue;
            // Key binds in the target's format; 1.8.9 has no simulationDistance and its GUI scale stops at Large.
            if (SharedControls.ToVersion(kvp.Key, kvp.Value, mcVersion) is not { } value
                || instanceMap.TryGetValue(name, out var had) && had == value) continue;
            instanceMap[name] = value;
            updated = true;
        }

        if (updated) WriteAtomically(instanceFile, SerializeOptions(instanceMap));
        WriteAtomically(takenFile, SerializeOptions(sharedMap));
    }

    /// <summary>After a game: the controls the player changed in it since its launch (key binds in key names) and its other settings
    /// into the shared copy. A control the game still has as it was given, or could not take (it kept its own), stays as the shared
    /// copy has it: another version may have changed it since, and a key bind only other versions have is never touched. 1.8.9's own
    /// formats never go there, nor a 1.8.9 key code without a key name. <paramref name="controlsOnly"/> (1.8.9 with Lunar's
    /// settings): only the controls.</summary>
    public static void SyncFromInstance(string instanceFile, string sharedFile, string mcVersion, bool controlsOnly = false)
    {
        // A game that could not read its options.txt ran, and saved it, with every option at its default: none of it is the player's.
        if (!File.Exists(instanceFile) || OptionsFailedToLoad(Path.GetDirectoryName(Path.GetFullPath(instanceFile))!)) return;
        var instanceMap = ParseOptions(File.ReadAllText(instanceFile));
        bool sourceIs18 = IsLegacy18(mcVersion);
        var sharedMap = File.Exists(sharedFile) ? ParseOptions(File.ReadAllText(sharedFile)) : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        var takenFile = Path.Combine(Path.GetDirectoryName(Path.GetFullPath(instanceFile))!, TakenOptionsFile);
        var taken = File.Exists(takenFile) ? ParseOptions(File.ReadAllText(takenFile)) : null;

        foreach (var (key, stored) in SharedControls.Controls(instanceMap, sourceIs18))
        {
            if (SharedControls.FromVersion(key, stored) is not { } value) continue;
            // Not given at the launch (a new mod's key, no launch record): only where the shared copy has none yet.
            if (taken == null || !taken.TryGetValue(key, out var given)) sharedMap.TryAdd(key, value);
            else if (SharedControls.ToVersion(key, given, mcVersion) is { } held && held != stored) sharedMap[key] = value;
        }
        if (!controlsOnly)
            foreach (var kvp in instanceMap)
            {
                // 1.8's own formats (lang:en_US, its resourcePacks list, fancyGraphics...) never reach the shared copy.
                if (!SharedControls.IsControl(kvp.Key) && (!sourceIs18 || SharedSettingsKeys.Contains(kvp.Key)))
                    sharedMap[kvp.Key] = kvp.Value;
            }

        WriteAtomically(sharedFile, SerializeOptions(sharedMap));
    }

    /// <summary>Whether the game's last session logged that it could not load its options.txt (logs\latest.log).</summary>
    public static bool OptionsFailedToLoad(string gameDirectory)
    {
        try
        {
            using var log = new FileStream(Path.Combine(gameDirectory, "logs", "latest.log"), FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            using var reader = new StreamReader(log);
            while (reader.ReadLine() is { } line)
                if (line.Contains("/ERROR]: Failed to load options", StringComparison.Ordinal)) return true;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        return false;
    }

    /// <summary>
    /// Right before a newer version starts: an options.txt without a "version:" line gets that Minecraft's data version (its client
    /// jar's version.json world_version). Without one Minecraft takes the file for a pre-1.13 one, runs its old key code fix on the
    /// key names the launcher wrote, fails to load the file and plays with every option at its default. A line the file has stays;
    /// 1.8.9 has no data version and never gets one.
    /// </summary>
    public static void EnsureDataVersion(string gameDirectory, string mcVersion, string? clientJar = null)
    {
        var file = Path.Combine(gameDirectory, "options.txt");
        if (IsLegacy18(mcVersion) || !File.Exists(file)) return;
        var bytes = File.ReadAllBytes(file);
        var text = Encoding.Latin1.GetString(bytes);
        if (ParseOptions(text).ContainsKey("version")
            || DataVersion(clientJar ?? Path.Combine(gameDirectory, "versions", mcVersion, mcVersion + ".jar")) is not { } version) return;
        // The old-file fixes this line now skips also switched the first-start accessibility screen off: keep it off, as before.
        string newline = text.Contains("\r\n", StringComparison.Ordinal) ? "\r\n" : "\n";
        var line = Encoding.ASCII.GetBytes("version:" + version + newline
            + (ParseOptions(text).ContainsKey("onboardAccessibility") ? "" : "onboardAccessibility:false" + newline));
        LockFiles.WriteAtomicallyAsync(file, line.Concat(bytes).ToArray()).GetAwaiter().GetResult();
    }

    /// <summary>A client jar's data version (version.json "world_version"), or null when it cannot be read.</summary>
    public static int? DataVersion(string clientJar)
    {
        try
        {
            using var zip = ZipFile.OpenRead(clientJar);
            if (zip.GetEntry("version.json") is not { } entry) return null;
            using var json = JsonDocument.Parse(entry.Open());
            return json.RootElement.TryGetProperty("world_version", out var version) && version.TryGetInt32(out int number) ? number : null;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException or JsonException) { return null; }
    }

    /// <summary>OptiFine's shaders.txt names a shader pack other than OFF / (internal).</summary>
    public static bool ShaderPackSelected(string gameDirectory)
    {
        var shaders = Path.Combine(gameDirectory, "optionsshaders.txt");
        if (!File.Exists(shaders)) return false;
        // optionsshaders.txt is key=value ("shaderPack=Sildurs.zip"), unlike options.txt
        var line = File.ReadLines(shaders).FirstOrDefault(l => l.StartsWith("shaderPack=", StringComparison.Ordinal));
        var pack = line?["shaderPack=".Length..].Trim();
        return !string.IsNullOrEmpty(pack) && !pack.Equals("OFF", StringComparison.OrdinalIgnoreCase)
            && !pack.Equals("(internal)", StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>Writes default OptiFine options for a 1.8.9 instance, applying defaults to existing installs once.</summary>
    public static void EnsureDefaultOptions18(string gameDirectory)
    {
        var optionsFile = Path.Combine(gameDirectory, "options.txt");
        var optiFineFile = Path.Combine(gameDirectory, "optionsof.txt");
        var optionsMap = File.Exists(optionsFile) ? ParseOptions(File.ReadAllText(optionsFile)) : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

        if (!optionsMap.ContainsKey("theladsOfDefaultsMigrated"))
        {
            // Once per install (owner, 1.9.2): VBOs, Fast Render and Fast Math on. Render Regions and Smart Animations stay
            // new-install defaults only (below), and Fast Render stays off while a shader pack is selected (it breaks shaders).
            bool newInstall = !File.Exists(optiFineFile);
            optionsMap["theladsOfDefaultsMigrated"] = "true";
            optionsMap["useVbo"] = "true";
            WriteAtomically(optionsFile, SerializeOptions(optionsMap));

            var ofMap = newInstall ? new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase) : ParseOptions(File.ReadAllText(optiFineFile));
            if (newInstall)
            {
                ofMap["ofRenderRegions"] = "true";
                ofMap["ofSmartAnimations"] = "true";
            }
            if (!ShaderPackSelected(gameDirectory)) ofMap["ofFastRender"] = "true";
            ofMap["ofFastMath"] = "true";
            WriteAtomically(optiFineFile, SerializeOptions(ofMap));
        }
        else if (!File.Exists(optiFineFile))
        {
            var ofMap = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
            {
                ["ofRenderRegions"] = "true",
                ["ofSmartAnimations"] = "true",
                ["ofFastRender"] = "true",
                ["ofFastMath"] = "true"
            };
            WriteAtomically(optiFineFile, SerializeOptions(ofMap));
        }
    }

    // ── Lads defaults (1.13.24): inherited .minecraft options, Vignette and Chunk Fade off ───────────────────────────────

    /// <summary>In a game folder: which one-time default passes already ran there. A pass that is listed never runs again, so a player who
    /// turns Vignette or Chunk Fade back on keeps that. (Minecraft rewrites options.txt and drops lines it does not know, so the marker
    /// lives in its own file.)</summary>
    public const string DefaultsMarkerFile = "lads-defaults-applied.txt";
    public const string VideoDefaultsStep = "options-vignette-chunkfade-1";
    public const string LegacyVignetteStep = "of189-vignette-1";

    /// <summary>The video defaults the launcher puts in options.txt: Vignette and Chunk Fade off (0 seconds).</summary>
    public static readonly IReadOnlyDictionary<string, string> VideoDefaults = new Dictionary<string, string>
    {
        ["vignette"] = "false",
        ["chunkSectionFadeInTime"] = "0.0"
    };

    /// <summary>Whether the game's own options.txt has Vignette and Chunk Fade: the vanilla settings of 1.21.11 and 26.x (checked in the
    /// client jars: 1.20.1 has neither, and 1.8.9 only has the Lads module option, see <see cref="LegacyVignetteStep"/>).</summary>
    public static bool HasVideoDefaults(string mcVersion) =>
        Version.TryParse(mcVersion, out var version) && version >= new Version(1, 21, 11);

    /// <summary>Right before a game starts, in every game folder (profiles, modpacks, Experiences): first a folder with no options.txt
    /// of its own takes the player's .minecraft one (<see cref="InheritMinecraftOptions"/>), then the passes of <see cref="VideoDefaultsStep"/>
    /// and <see cref="LegacyVignetteStep"/> that this folder has not had yet run once. Never throws: a file it cannot write is skipped
    /// and tried again at the next launch.</summary>
    public static void ApplyLadsDefaults(string gameDirectory, string mcVersion, string? minecraftRoot, string? clientJar = null)
    {
        try
        {
            if (!Directory.Exists(gameDirectory)) return;
            clientJar ??= Path.Combine(gameDirectory, "versions", mcVersion, mcVersion + ".jar");
            InheritMinecraftOptions(gameDirectory, mcVersion, minecraftRoot, clientJar);
            var markerFile = Path.Combine(gameDirectory, DefaultsMarkerFile);
            var done = File.Exists(markerFile) ? File.ReadAllLines(markerFile).Select(l => l.Trim()).Where(l => l.Length > 0).ToList() : new List<string>();
            bool changed = false;
            var optionsFile = Path.Combine(gameDirectory, "options.txt");
            bool existed = File.Exists(optionsFile);
            // A file made here needs the game's data version (else Minecraft takes it for a pre-1.13 one): with no jar to read it from, wait
            // for the game's own options.txt.
            if (HasVideoDefaults(mcVersion) && !done.Contains(VideoDefaultsStep) && (existed || DataVersion(clientJar) != null))
            {
                SetOptions(optionsFile, VideoDefaults);
                if (!existed) EnsureDataVersion(gameDirectory, mcVersion, clientJar);
                done.Add(VideoDefaultsStep);
                changed = true;
            }
            if (IsLegacy18(mcVersion) && !done.Contains(LegacyVignetteStep) && SetLegacyVignetteOff(gameDirectory))
            {
                done.Add(LegacyVignetteStep);
                changed = true;
            }
            if (changed) WriteAtomically(markerFile, string.Join('\n', done) + "\n");
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { }
    }

    /// <summary>The only lines the launcher itself puts in a game folder's options.txt before the game has ever run there (the data
    /// version, the first-start screen switch, the renderer choice, fullscreen on launch): a file with nothing else is not the player's own.</summary>
    private static readonly HashSet<string> LauncherOnlyKeys = new(StringComparer.OrdinalIgnoreCase)
        { "version", "onboardAccessibility", "preferredGraphicsBackend", "fullscreen" };

    /// <summary>A game folder with no options.txt of its own (none, or only what the launcher wrote, <see cref="LauncherOnlyKeys"/>) starts
    /// from the player's .minecraft one: keybinds, video, sound and the rest; the launcher's own lines stay as they are. Only the same kind of
    /// file is taken (a 1.13+ file has a "version:" line, a 1.8 one none), the resource pack lists and the last server stay behind (the Lads
    /// packs and server list are set up by the launcher), and a data version newer than this game's own is dropped so Minecraft does not
    /// take the file for one from the future. An options.txt with any other line is never touched.</summary>
    public static bool InheritMinecraftOptions(string gameDirectory, string mcVersion, string? minecraftRoot, string? clientJar = null)
    {
        if (string.IsNullOrWhiteSpace(minecraftRoot)) return false;
        var target = Path.Combine(gameDirectory, "options.txt");
        var source = Path.Combine(minecraftRoot, "options.txt");
        if (Directory.Exists(target) || !File.Exists(source) || new FileInfo(source).Length > 1 << 20) return false;
        var own = File.Exists(target) ? ParseOptions(File.ReadAllText(target)) : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        if (own.Keys.Any(key => !LauncherOnlyKeys.Contains(key))) return false;
        if (SafeFileOps.PathsEqual(SafeFileOps.GetFinalPath(gameDirectory), SafeFileOps.GetFinalPath(minecraftRoot))) return false;
        var text = Encoding.UTF8.GetString(File.ReadAllBytes(source));
        var map = ParseOptions(text);
        if (map.Count == 0 || map.ContainsKey("version") == IsLegacy18(mcVersion)) return false;
        int? targetData = IsLegacy18(mcVersion) ? null : DataVersion(clientJar ?? Path.Combine(gameDirectory, "versions", mcVersion, mcVersion + ".jar"));
        string newline = text.Contains("\r\n", StringComparison.Ordinal) ? "\r\n" : "\n";
        var kept = new List<string>();
        foreach (var line in text.Split('\n').Select(l => l.TrimEnd('\r')))
        {
            int colon = line.IndexOf(':');
            string key = colon > 0 ? line[..colon].Trim() : "";
            if (key is "resourcePacks" or "incompatibleResourcePacks" or "lastServer") continue;
            if (own.ContainsKey(key)) continue; // the launcher's own line (below) wins
            if (key == "version" && targetData is { } ownData && int.TryParse(line[(colon + 1)..].Trim(), out int theirs) && theirs > ownData) continue;
            if (line.Length > 0) kept.Add(line);
        }
        // The launcher's lines first: "version:" stays the file's first line.
        kept.InsertRange(0, own.Select(kvp => kvp.Key + ":" + kvp.Value));
        Directory.CreateDirectory(gameDirectory);
        WriteAtomically(target, string.Join(newline, kept) + newline);
        return true;
    }

    /// <summary>Sets these keys in an options.txt, in place (the other lines and the line ending stay); a missing key is appended and a
    /// missing file created.</summary>
    public static void SetOptions(string optionsFile, IReadOnlyDictionary<string, string> values)
    {
        var text = File.Exists(optionsFile) ? File.ReadAllText(optionsFile) : "";
        string newline = text.Contains("\r\n", StringComparison.Ordinal) ? "\r\n" : "\n";
        var lines = text.Split('\n').Select(l => l.TrimEnd('\r')).ToList();
        if (lines.Count > 0 && lines[^1].Length == 0) lines.RemoveAt(lines.Count - 1);
        foreach (var (key, value) in values)
        {
            int at = lines.FindIndex(l => l.StartsWith(key + ":", StringComparison.Ordinal));
            if (at >= 0) lines[at] = key + ":" + value; else lines.Add(key + ":" + value);
        }
        WriteAtomically(optionsFile, string.Join(newline, lines) + newline);
    }

    /// <summary>1.8.9's Vignette is a Lads module option (OptiFine189, "Vignette": 0 default, 1 Fast = off) in thelads_config.json. Only an
    /// existing config is changed: a partial file where none exists would send the first start down the existing-config path. False when
    /// there is none yet (the pass then runs at a later launch).</summary>
    private static bool SetLegacyVignetteOff(string gameDirectory)
    {
        var file = Path.Combine(gameDirectory, "thelads_config.json");
        if (!File.Exists(file)) return false;
        if (JsonNode.Parse(File.ReadAllText(file)) is not JsonObject root) return false;
        if (root["modules"] is not JsonObject modules) root["modules"] = modules = new JsonObject();
        if (modules["OptiFine189"] is not JsonObject module) modules["OptiFine189"] = module = new JsonObject();
        if (module["options"] is not JsonObject options) module["options"] = options = new JsonObject();
        options["Vignette"] = 1;
        WriteAtomically(file, root.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
        return true;
    }

    // A crash or a closed launcher mid-write leaves the old file whole, never a cut-off one. An unchanged file is not rewritten.
    public static void WriteAtomically(string file, string text)
    {
        if (!File.Exists(file) || File.ReadAllText(file) != text)
            LockFiles.WriteAtomicallyAsync(file, Encoding.UTF8.GetBytes(text)).GetAwaiter().GetResult();
    }
}
