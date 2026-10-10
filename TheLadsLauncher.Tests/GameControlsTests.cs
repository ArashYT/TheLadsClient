using Avalonia;
using Avalonia.Input;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>Settings → Controls: options.txt read and written in both formats (modern key names, 1.8.9 LWJGL2 codes), unknown
/// lines kept; the vanilla key lists, conflicts and key names; plus the window geometry and theme list of the launcher chrome.</summary>
public sealed class GameControlsTests
{
    [Fact]
    public void ModernOptionsKeepUnknownLinesOrderAndLineEndings()
    {
        const string text = "version:4903\r\nfov:0.0\r\nmouseSensitivity:0.5\r\nnot an option line\r\nkey_key.jump:key.keyboard.space\r\n"
            + "key_key.theladscore.zoom:key.keyboard.c\r\nfov:0.25\r\n";
        var options = GameOptionsFile.Parse(text);
        Assert.Equal(80, GameControls.FovDegrees(options.Get("fov"))); // Minecraft reads the last line of a key
        Assert.Equal(100, GameControls.SensitivityPercent(options.Get("mouseSensitivity")));

        options.Set("fov", GameControls.FovStored(90));
        options.Set("key_key.jump", "key.keyboard.w");
        options.Set("simulationDistance", "8");

        Assert.Equal("version:4903\r\nfov:0.0\r\nmouseSensitivity:0.5\r\nnot an option line\r\nkey_key.jump:key.keyboard.w\r\n"
            + "key_key.theladscore.zoom:key.keyboard.c\r\nfov:0.5\r\nsimulationDistance:8\r\n", options.ToString());
    }

    [Fact]
    public void LegacyOptionsUseLwjgl2CodesAndNormalisedFov()
    {
        // 1.8.9 GameSettings: "fov:" + (fovSetting - 70) / 40, keys as LWJGL2 codes, mouse buttons -100 + button.
        var options = GameOptionsFile.Parse("fov:-0.25\nkey_key.attack:-100\nkey_key.jump:57\nlastServer:mc.example.net\n");
        Assert.Equal(60, GameControls.FovDegrees(options.Get("fov")));
        Assert.Equal("Left Button", GameControls.DisplayName(options.Get("key_key.attack")!));
        Assert.Equal("Space", GameControls.DisplayName(options.Get("key_key.jump")!));

        options.Set("key_key.jump", GameControls.ToStored("key.keyboard.w", legacy: true)!);
        options.Set("key_key.drop", GameControls.ToStored(GameControls.Unbound, legacy: true)!);
        options.Set("fov", GameControls.FovStored(110));
        Assert.Equal("fov:1.0\nkey_key.attack:-100\nkey_key.jump:17\nlastServer:mc.example.net\nkey_key.drop:0\n", options.ToString());

        Assert.Equal("219", GameControls.ToStored("key.keyboard.left.win", legacy: true)); // LWJGL2's KEY_LMETA
        Assert.Null(GameControls.ToStored("key.keyboard.world.1", legacy: true)); // no 1.8.9 code: refused, never written as text
        Assert.Equal("key.keyboard.left.win", GameControls.ToStored("key.keyboard.left.win", legacy: false));
        Assert.Equal("Not bound", GameControls.DisplayName("0"));
        Assert.Equal("Not bound", GameControls.DisplayName(GameControls.Unbound));
    }

    [Fact]
    public void NewFileStartsEmptyAndGetsOnlyTheChangedKeys()
    {
        var options = GameOptionsFile.Parse(null);
        Assert.Null(options.Get("fov"));
        options.Set("maxFps", "260");
        Assert.Equal("maxFps:260\r\n", options.ToString());
    }

    [Theory]
    [InlineData("1.8.9", "key.streamStartStop", "key.swapOffhand")]
    [InlineData("1.21.1", "key.swapOffhand", "key.toggleGui")]
    [InlineData("1.21.11", "key.debug.overlay", "key.friends")]
    [InlineData("26.2", "key.friends", "key.debug.improvedTransparency")]
    [InlineData("26.3", "key.debug.improvedTransparency", "key.streamStartStop")]
    public void VanillaKeysFollowTheVersion(string version, string present, string absent)
    {
        var ids = GameControls.VanillaKeys(version).Select(k => k.Id).ToList();
        Assert.Contains(present, ids);
        Assert.DoesNotContain(absent, ids);
        Assert.Equal(ids.Count, ids.Distinct().Count());
        Assert.Contains("key.hotbar.9", ids);
    }

    [Theory]
    [InlineData("1.8.9")]
    [InlineData("1.21.1")]
    [InlineData("1.21.11")]
    [InlineData("26.2")]
    [InlineData("26.3")]
    public void DefaultsAreStorableAndNeverConflict(string version)
    {
        bool legacy = version == "1.8.9";
        var keys = GameControls.VanillaKeys(version);
        var defaults = keys.Select(k => (k, GameControls.ToStored(k.Default, legacy)!)).ToList();
        Assert.All(defaults, d => Assert.NotNull(d.Item2));
        if (legacy) Assert.All(defaults, d => Assert.True(int.TryParse(d.Item2, out _), d.k.Id));
        Assert.Empty(GameControls.Conflicts(defaults, legacy)); // vanilla's own shared defaults (F3, F3+C, middle click) are not conflicts
    }

    [Fact]
    public void ConflictsAreTheKeysSharingABindingWithinTheirKind()
    {
        var keys = GameControls.VanillaKeys("26.3");
        List<(GameKeyBinding, string)> With(string id, string key) =>
            keys.Select(k => (k, k.Id == id ? key : k.Default)).ToList();

        Assert.Equal(new[] { "key.forward", "key.jump" }, GameControls.Conflicts(With("key.jump", "key.keyboard.w"), false).Order());
        Assert.Empty(GameControls.Conflicts(With("key.drop", "key.keyboard.b"), false)); // F3+B is a debug combination, not B
        Assert.Empty(GameControls.Conflicts(With("key.jump", GameControls.Unbound), false));

        var legacy = GameControls.VanillaKeys("1.8.9");
        var stored = legacy.Select(k => (k, k.Id == "key.sprint" ? "17" : GameControls.ToStored(k.Default, true)!)).ToList();
        Assert.Equal(new[] { "key.forward", "key.sprint" }, GameControls.Conflicts(stored, true).Order());
    }

    [Fact]
    public void PhysicalKeysAndMouseButtonsGetMinecraftNames()
    {
        Assert.Equal("key.keyboard.w", GameControls.KeyName(PhysicalKey.W));
        Assert.Equal("key.keyboard.1", GameControls.KeyName(PhysicalKey.Digit1));
        Assert.Equal("key.keyboard.keypad.5", GameControls.KeyName(PhysicalKey.NumPad5));
        Assert.Equal("key.keyboard.f11", GameControls.KeyName(PhysicalKey.F11));
        Assert.Equal("key.keyboard.left.shift", GameControls.KeyName(PhysicalKey.ShiftLeft));
        Assert.Equal("key.keyboard.grave.accent", GameControls.KeyName(PhysicalKey.Backquote));
        Assert.Null(GameControls.KeyName(PhysicalKey.Escape));
        Assert.Equal("key.mouse.4", GameControls.MouseName(MouseButton.XButton1));
        Assert.Equal("Left Shift", GameControls.DisplayName("key.keyboard.left.shift"));
        Assert.Equal("Keypad 5", GameControls.DisplayName("key.keyboard.keypad.5"));
        Assert.Equal("Mouse 4", GameControls.DisplayName("-97"));
        Assert.Equal("Mouse 6", GameControls.DisplayName("-95"));
        Assert.Equal("Key code 508", GameControls.DisplayName("508"));
        Assert.Equal("0.75", GameControls.SensitivityStored(150));
        Assert.Equal(32, GameControls.Int("99", 12, 2, 32));
    }

    /// <summary>1.8.9 codes and key names both ways: every mouse button (button b is b - 100), LWJGL's F19, Windows and Menu keys,
    /// and null where the other side has no equivalent, so a sync keeps the bind it has. 1.8.9's own names of Lads' and Essential's
    /// keys both ways; a 1.8.9 line under the newer name beside its own is a stale copy.</summary>
    [Fact]
    public void KeyBindsTranslateBetween189CodesAndKeyNames()
    {
        foreach (var (name, code) in new[] { ("key.mouse.left", "-100"), ("key.mouse.middle", "-98"), ("key.mouse.4", "-97"), ("key.mouse.5", "-96"),
                     ("key.mouse.8", "-93"), ("key.keyboard.f19", "113"), ("key.keyboard.unknown", "0"), ("key.keyboard.w", "17"),
                     ("key.keyboard.left.win", "219"), ("key.keyboard.right.win", "220"), ("key.keyboard.menu", "221"), ("key.keyboard.keypad.equal", "141") })
        {
            Assert.Equal(code, GameOptionsService.TranslateKeybindToTarget("", name, true));
            Assert.Equal(name, GameOptionsService.TranslateKeybindToTarget("", code, false));
        }
        Assert.Null(GameOptionsService.TranslateKeybindToTarget("", "key.keyboard.world.1", true));
        Assert.Null(GameOptionsService.TranslateKeybindToTarget("", "key.mouse.200", true));
        Assert.Null(GameOptionsService.TranslateKeybindToTarget("", "508", false)); // a character key 1.8.9 stores as character + 256
        Assert.Equal("-97", GameOptionsService.TranslateKeybindToTarget("", "-97", true));
        Assert.Equal("key.keyboard.world.1", GameOptionsService.TranslateKeybindToTarget("", "key.keyboard.world.1", false));

        Assert.Equal("key_Chat Peek", SharedControls.OptionKey("key_keybind.name.CHAT_PEEK", legacy: true));
        Assert.Equal("key_keybind.name.CHAT_PEEK", SharedControls.OptionKey("key_Chat Peek", legacy: false));
        Assert.Equal("key_key.theladscore.screenshots", SharedControls.OptionKey("key_key.lads_screenshots.open_screenshots_screen", legacy: true));
        Assert.Equal("key_key.jump", SharedControls.OptionKey("key_key.jump", legacy: true));
        Assert.Equal(new Dictionary<string, string> { ["key_keybind.name.CHAT_PEEK"] = "key.keyboard.z", ["key_key.drop"] = "key.mouse.4", ["guiScale"] = "3" },
            SharedControls.Read("key_Chat Peek:44\nkey_keybind.name.CHAT_PEEK:19\nkey_key.drop:-97\nguiScale:3\nlang:en_US\n", legacy: true));
        Assert.Equal("key_Chat Peek:37\nkey_key.drop:-96\nguiScale:3\n", SharedControls.Apply("key_Chat Peek:44\nkey_key.drop:-97\nguiScale:2\n",
            new Dictionary<string, string> { ["key_keybind.name.CHAT_PEEK"] = "key.keyboard.k", ["key_key.drop"] = "key.mouse.5", ["guiScale"] = "5" }, "1.8.9"));
    }

    [Fact]
    public void MaximizeFillsTheWorkingAreaOrItsLargestCentred16By9()
    {
        var area = new PixelRect(0, 0, 1920, 1032);
        Assert.Equal(area, WindowGeometry.Maximized(area, false, 16.0 / 9));
        Assert.Equal(new PixelRect(42, 0, 1835, 1032), WindowGeometry.Maximized(area, true, 16.0 / 9));
        Assert.Equal(new PixelRect(1920, 120, 2560, 1440), WindowGeometry.Maximized(new PixelRect(1920, 0, 2560, 1680), true, 16.0 / 9));
        Assert.Equal(new PixelRect(5, 10, 15, 20), WindowGeometry.Lerp(new PixelRect(0, 0, 10, 10), new PixelRect(10, 20, 20, 30), 0.5));
    }

    [Fact]
    public void HalloweenIsAThemeWithItsOwnColours()
    {
        Assert.Contains("Halloween", LauncherSettings.GetAvailableThemes());
        var colours = new LauncherSettings { Theme = "Halloween" }.GetThemeColors();
        Assert.NotEqual(new LauncherSettings().GetThemeColors(), colours);
        Assert.Equal("#FF8A1F", colours.Accent);
    }
}
