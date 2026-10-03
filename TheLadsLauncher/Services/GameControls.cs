using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text.RegularExpressions;
using Avalonia.Input;

namespace TheLadsLauncher.Services;

/// <summary>A vanilla keybind: its options.txt id ("key.forward", stored as "key_key.forward") and default in modern key names.</summary>
public sealed record GameKeyBinding(string Id, string Label, string Default);

/// <summary>
/// The Settings → Controls values of one options.txt. Modern versions name keys ("key.keyboard.w", "key.mouse.left");
/// 1.8.9 stores LWJGL2 key codes (17, -100) and has no simulationDistance. Both store fov as (degrees - 70) / 40 and
/// mouseSensitivity as 0..1 (shown as 0..200 %). Key names convert through <see cref="GameOptionsService.TranslateKeybindToTarget"/>.
/// </summary>
public static class GameControls
{
    public const string Unbound = "key.keyboard.unknown";

    public static IReadOnlyList<GameKeyBinding> VanillaKeys(string minecraftVersion)
    {
        var keys = new List<GameKeyBinding>();
        void Add(string id, string label, string key) => keys.Add(new GameKeyBinding("key." + id, label, key.StartsWith("key.") ? key : "key.keyboard." + key));
        if (GameOptionsService.IsLegacy18(minecraftVersion))
        {
            // 1.8.9's GameSettings.keyBindings order, Twitch stream keys included.
            Add("attack", "Attack/Destroy", "key.mouse.left"); Add("use", "Use Item/Place Block", "key.mouse.right");
            Add("forward", "Walk Forwards", "w"); Add("left", "Strafe Left", "a"); Add("back", "Walk Backwards", "s"); Add("right", "Strafe Right", "d");
            Add("jump", "Jump", "space"); Add("sneak", "Sneak", "left.shift"); Add("sprint", "Sprint", "left.control");
            Add("drop", "Drop Item", "q"); Add("inventory", "Inventory", "e"); Add("chat", "Open Chat", "t"); Add("playerlist", "List Players", "tab");
            Add("pickItem", "Pick Block", "key.mouse.middle"); Add("command", "Open Command", "slash"); Add("screenshot", "Take Screenshot", "f2");
            Add("togglePerspective", "Toggle Perspective", "f5"); Add("smoothCamera", "Toggle Cinematic Camera", "unknown");
            Add("streamStartStop", "Start/Stop Stream", "f6"); Add("streamPauseUnpause", "Pause/Unpause Stream", "f7");
            Add("streamCommercial", "Show Stream Commercials", "unknown"); Add("streamToggleMic", "Push To Talk/Mute", "unknown");
            Add("fullscreen", "Toggle Fullscreen", "f11"); Add("spectatorOutlines", "Highlight Players (Spectators)", "unknown");
            for (int i = 1; i <= 9; i++) Add("hotbar." + i, "Hotbar Slot " + i, i.ToString(CultureInfo.InvariantCulture));
            return keys;
        }
        var version = Version.TryParse(minecraftVersion, out var parsed) ? parsed : new Version(99, 0);
        bool v1219 = version >= new Version(1, 21, 9), v262 = version >= new Version(26, 2), v263 = version >= new Version(26, 3);
        // Modern Options.keyMappings order (the order Minecraft writes them).
        Add("attack", "Attack/Destroy", "key.mouse.left"); Add("use", "Use Item/Place Block", "key.mouse.right");
        Add("forward", "Walk Forwards", "w"); Add("left", "Strafe Left", "a"); Add("back", "Walk Backwards", "s"); Add("right", "Strafe Right", "d");
        Add("jump", "Jump", "space"); Add("sneak", "Sneak", "left.shift"); Add("sprint", "Sprint", "left.control");
        Add("drop", "Drop Selected Item", "q"); Add("inventory", "Open/Close Inventory", "e"); Add("chat", "Open Chat", "t");
        Add("playerlist", "List Players", "tab"); Add("pickItem", "Pick Block", "key.mouse.middle"); Add("command", "Open Command", "slash");
        if (v262) Add("friends", "Friends", "o");
        Add("socialInteractions", "Social Interactions Screen", "p");
        if (v1219) { Add("toggleGui", "Toggle GUI", "f1"); Add("toggleSpectatorShaderEffects", "Toggle Spectator Shader Effects", "f4"); }
        Add("screenshot", "Take Screenshot", "f2"); Add("togglePerspective", "Toggle Perspective", "f5");
        Add("smoothCamera", "Toggle Cinematic Camera", "unknown"); Add("fullscreen", "Toggle Fullscreen", "f11");
        Add("spectatorOutlines", "Highlight Players (Spectators)", "unknown");
        if (v1219) Add("spectatorHotbar", "Spectator Hotbar", "key.mouse.middle");
        Add("swapOffhand", "Swap Item With Offhand", "f"); Add("saveToolbarActivator", "Save Hotbar Activator", "c");
        Add("loadToolbarActivator", "Load Hotbar Activator", "x"); Add("advancements", "Advancements", "l");
        if (v1219) { Add("quickActions", "Quick Actions", "g"); Add("debug.overlay", "Debug Overlay", "f3"); Add("debug.modifier", "Debug Modifier", "f3"); }
        for (int i = 1; i <= 9; i++) Add("hotbar." + i, "Hotbar Slot " + i, i.ToString(CultureInfo.InvariantCulture));
        if (v1219)
        {
            foreach (var (id, key) in new[] { ("reloadChunk", "a"), ("showHitboxes", "b"), ("clearChat", "d"), ("crash", "c"), ("showChunkBorders", "g"),
                ("showAdvancedTooltips", "h"), ("copyRecreateCommand", "i"), ("spectate", "n"), ("switchGameMode", "f4"), ("debugOptions", "f6"),
                ("focusPause", "p"), ("dumpDynamicTextures", "s"), ("reloadResourcePacks", "t"), ("profiling", "l"), ("copyLocation", "c"),
                ("dumpVersion", "v"), ("profilingChart", "1"), ("fpsCharts", "2"), ("networkCharts", "3") })
                Add("debug." + id, "F3 + " + Humanize(id), key);
            if (v262) Add("debug.lightmapTexture", "F3 + Lightmap Texture", "4");
            if (v263) Add("debug.improvedTransparency", "F3 + Improved Transparency", "x");
        }
        return keys;
    }

    private static string Humanize(string camel) =>
        CultureInfo.InvariantCulture.TextInfo.ToTitleCase(Regex.Replace(camel, "(?<=[a-z])(?=[A-Z])", " "));

    /// <summary>A modern key name as this version stores it (1.8.9: its LWJGL2 code), or null when 1.8.9 has no code for it.</summary>
    public static string? ToStored(string modernKey, bool legacy)
    {
        if (!legacy) return modernKey;
        var code = GameOptionsService.TranslateKeybindToTarget("", modernKey, true);
        return int.TryParse(code, out _) ? code : null;
    }

    public static bool IsUnbound(string stored) => stored is Unbound or "0" or "";

    /// <summary>"W", "Left Shift", "Keypad 1", "Middle Button", "Not bound"; 1.8.9 codes are named like their modern keys.</summary>
    public static string DisplayName(string stored)
    {
        if (IsUnbound(stored)) return "Not bound";
        var name = int.TryParse(stored, out var code) ? GameOptionsService.TranslateKeybindToTarget("", stored, false) : stored;
        if (name == stored && int.TryParse(stored, out _)) return "Key code " + code;
        if (name.StartsWith("key.mouse.", StringComparison.Ordinal))
            return name[10..] switch { "left" => "Left Button", "right" => "Right Button", "middle" => "Middle Button", var n => "Mouse " + n };
        if (!name.StartsWith("key.keyboard.", StringComparison.Ordinal)) return name;
        var key = name[13..];
        return key.Length == 1 ? key.ToUpperInvariant()
            : CultureInfo.InvariantCulture.TextInfo.ToTitleCase(key.Replace('.', ' ')).Replace("Grave Accent", "`");
    }

    /// <summary>Ids of bound keys that share a key with another key of the same kind (F3 debug combinations apart from the rest).
    /// Vanilla's own shared defaults (F3 overlay and modifier, pick block and spectator hotbar) are not conflicts.</summary>
    public static IReadOnlySet<string> Conflicts(IEnumerable<(GameKeyBinding Key, string Stored)> bindings, bool legacy) =>
        bindings.Where(b => !IsUnbound(b.Stored))
            .GroupBy(b => (b.Key.Id.StartsWith("key.debug.", StringComparison.Ordinal) && b.Key.Id is not ("key.debug.overlay" or "key.debug.modifier"), b.Stored))
            .Where(g => g.Count() > 1 && g.Any(b => b.Stored != ToStored(b.Key.Default, legacy)))
            .SelectMany(g => g.Select(b => b.Key.Id)).ToHashSet(StringComparer.Ordinal);

    /// <summary>Minecraft's (GLFW's) name of a physical key, or null for keys it has no name for. Escape is kept for unbinding.</summary>
    public static string? KeyName(PhysicalKey key)
    {
        var name = key.ToString();
        string? token = key switch
        {
            >= PhysicalKey.A and <= PhysicalKey.Z => name.ToLowerInvariant(),
            >= PhysicalKey.Digit0 and <= PhysicalKey.Digit9 => name[5..],
            >= PhysicalKey.F1 and <= PhysicalKey.F24 => name.ToLowerInvariant(),
            >= PhysicalKey.NumPad0 and <= PhysicalKey.NumPad9 => "keypad." + name[6..],
            PhysicalKey.NumPadAdd => "keypad.add", PhysicalKey.NumPadSubtract => "keypad.subtract", PhysicalKey.NumPadMultiply => "keypad.multiply",
            PhysicalKey.NumPadDivide => "keypad.divide", PhysicalKey.NumPadDecimal => "keypad.decimal", PhysicalKey.NumPadEnter => "keypad.enter",
            PhysicalKey.NumPadEqual => "keypad.equal",
            PhysicalKey.Backquote => "grave.accent", PhysicalKey.Backslash => "backslash", PhysicalKey.BracketLeft => "left.bracket",
            PhysicalKey.BracketRight => "right.bracket", PhysicalKey.Comma => "comma", PhysicalKey.Equal => "equal", PhysicalKey.Minus => "minus",
            PhysicalKey.Period => "period", PhysicalKey.Quote => "apostrophe", PhysicalKey.Semicolon => "semicolon", PhysicalKey.Slash => "slash",
            PhysicalKey.AltLeft => "left.alt", PhysicalKey.AltRight => "right.alt", PhysicalKey.ControlLeft => "left.control",
            PhysicalKey.ControlRight => "right.control", PhysicalKey.ShiftLeft => "left.shift", PhysicalKey.ShiftRight => "right.shift",
            PhysicalKey.MetaLeft => "left.win", PhysicalKey.MetaRight => "right.win", PhysicalKey.ContextMenu => "menu",
            PhysicalKey.Backspace => "backspace", PhysicalKey.CapsLock => "caps.lock", PhysicalKey.Enter => "enter", PhysicalKey.Space => "space",
            PhysicalKey.Tab => "tab", PhysicalKey.Delete => "delete", PhysicalKey.End => "end", PhysicalKey.Home => "home",
            PhysicalKey.Insert => "insert", PhysicalKey.PageDown => "page.down", PhysicalKey.PageUp => "page.up",
            PhysicalKey.ArrowDown => "down", PhysicalKey.ArrowLeft => "left", PhysicalKey.ArrowRight => "right", PhysicalKey.ArrowUp => "up",
            PhysicalKey.NumLock => "num.lock", PhysicalKey.PrintScreen => "print.screen", PhysicalKey.ScrollLock => "scroll.lock",
            PhysicalKey.Pause => "pause",
            _ => null
        };
        return token == null ? null : "key.keyboard." + token;
    }

    public static string? MouseName(MouseButton button) => button switch
    {
        MouseButton.Left => "key.mouse.left", MouseButton.Right => "key.mouse.right", MouseButton.Middle => "key.mouse.middle",
        MouseButton.XButton1 => "key.mouse.4", MouseButton.XButton2 => "key.mouse.5", _ => null
    };

    public static int FovDegrees(string? stored) =>
        double.TryParse(stored, NumberStyles.Float, CultureInfo.InvariantCulture, out var v) ? Math.Clamp((int)Math.Round(v * 40 + 70), 30, 110) : 70;

    public static string FovStored(int degrees) => ((degrees - 70) / 40.0).ToString("0.0###", CultureInfo.InvariantCulture);

    public static int SensitivityPercent(string? stored) =>
        double.TryParse(stored, NumberStyles.Float, CultureInfo.InvariantCulture, out var v) ? Math.Clamp((int)Math.Round(v * 200), 0, 200) : 100;

    public static string SensitivityStored(int percent) => (percent / 200.0).ToString("0.0###", CultureInfo.InvariantCulture);

    public static int Int(string? stored, int fallback, int min, int max) =>
        int.TryParse(stored, NumberStyles.Integer, CultureInfo.InvariantCulture, out var v) ? Math.Clamp(v, min, max) : fallback;
}

/// <summary>options.txt edited line by line: only the lines of changed keys are replaced (new keys appended), so unknown
/// lines, their order and the file's line endings stay as they were.</summary>
public sealed class GameOptionsFile
{
    private readonly List<string> _lines;
    private readonly string _newline;

    private GameOptionsFile(List<string> lines, string newline) { _lines = lines; _newline = newline; }

    public static GameOptionsFile Parse(string? text)
    {
        text ??= "";
        var lines = text.Split('\n').Select(l => l.TrimEnd('\r')).ToList();
        if (lines.Count > 0 && lines[^1].Length == 0) lines.RemoveAt(lines.Count - 1);
        return new GameOptionsFile(lines, text.Contains("\r\n", StringComparison.Ordinal) || text.Length == 0 ? "\r\n" : "\n");
    }

    public string? Get(string key)
    {
        int index = IndexOf(key);
        return index < 0 ? null : _lines[index][(_lines[index].IndexOf(':') + 1)..];
    }

    public void Set(string key, string value)
    {
        int index = IndexOf(key);
        if (index < 0) _lines.Add(key + ":" + value);
        else _lines[index] = key + ":" + value;
    }

    // Minecraft reads the last line of a key; keys never contain ':'.
    private int IndexOf(string key) => _lines.FindLastIndex(l => l.Length > key.Length && l[key.Length] == ':' && l.StartsWith(key, StringComparison.Ordinal));

    public override string ToString() => _lines.Count == 0 ? "" : string.Join(_newline, _lines) + _newline;
}
