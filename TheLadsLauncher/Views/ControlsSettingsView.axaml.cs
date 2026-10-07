using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Media;
using Avalonia.Threading;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

/// <summary>
/// Settings → Controls: field of view, sensitivity, video options and every vanilla key bind, as ONE set shared by every Lads
/// Client profile that is not isolated (<see cref="SharedControls"/>). Values are shown and kept in modern key names whatever the
/// version; each profile's options.txt gets them in its own format. Changes save on their own (debounced); only changed keys are
/// rewritten. A profile whose game runs gets the change when it closes: Minecraft rewrites options.txt on exit.
/// </summary>
public partial class ControlsSettingsView : UserControl
{
    private readonly DispatcherTimer _saveTimer = new() { Interval = TimeSpan.FromMilliseconds(500) };
    // options.txt key -> modern-format value, not yet written.
    private readonly Dictionary<string, string> _pending = new(StringComparer.Ordinal);
    private readonly Dictionary<string, string> _keyValues = new(StringComparer.Ordinal);
    private readonly Dictionary<string, Button> _keyButtons = new(StringComparer.Ordinal);
    private readonly List<Action> _sliderLabels = new();
    private IReadOnlyList<GameKeyBinding> _keys = Array.Empty<GameKeyBinding>();
    private IReadOnlyList<LauncherProfile> _targets = Array.Empty<LauncherProfile>();
    private GameKeyBinding? _listening;
    private bool _loading;

    /// <summary>Short save state for the Settings page's indicator: "Saving…", "All changes saved" or an error (true).</summary>
    public event Action<string, bool>? SaveStateChanged;

    public ControlsSettingsView()
    {
        InitializeComponent();
        _saveTimer.Tick += async (_, _) => { _saveTimer.Stop(); await SaveAsync(); };
        HookSlider(FovSlider, FovValue, "fov", v => GameControls.FovStored(v), v => v == 70 ? "70 (Normal)" : v + "");
        HookSlider(SensitivitySlider, SensitivityValue, "mouseSensitivity", v => GameControls.SensitivityStored(v), v => v + "%");
        HookSlider(RenderDistanceSlider, RenderDistanceValue, "renderDistance", v => v + "", v => v + " chunks");
        HookSlider(SimulationDistanceSlider, SimulationDistanceValue, "simulationDistance", v => v + "", v => v + " chunks");
        HookSlider(MaxFpsSlider, MaxFpsValue, "maxFps", v => v + "", v => v >= 260 ? "Unlimited" : v + " fps");
        GuiScaleBox.ItemsSource = new[] { "Auto", "1", "2", "3", "4", "5", "6" };
        GuiScaleBox.SelectionChanged += (_, _) => { if (GuiScaleBox.SelectedIndex >= 0) Change("guiScale", GuiScaleBox.SelectedIndex + ""); };
        VsyncCheck.IsCheckedChanged += (_, _) => Change("enableVsync", VsyncCheck.IsChecked == true ? "true" : "false");
        AddHandler(KeyDownEvent, OnKeyDown, RoutingStrategies.Tunnel);
        AddHandler(PointerPressedEvent, OnPointerPressed, RoutingStrategies.Tunnel);
    }

    /// <summary>The profiles the controls apply to, for the QA preview.</summary>
    public IReadOnlyList<LauncherProfile> Targets => _targets;

    /// <summary>Writes pending changes now (the QA preview, leaving the tab).</summary>
    public async Task FlushAsync()
    {
        if (!_saveTimer.IsEnabled) return;
        _saveTimer.Stop();
        await SaveAsync();
    }

    protected override void OnAttachedToVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnAttachedToVisualTree(e);
        if (_pending.Count == 0) Reload();
    }

    protected override void OnDetachedFromVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnDetachedFromVisualTree(e);
        StopListening();
        _ = FlushAsync();
    }

    private static bool Legacy(LauncherProfile p) => GameOptionsService.IsLegacy18(p.MinecraftVersion);

    private static Version Order(string minecraftVersion) => Version.TryParse(minecraftVersion, out var v) ? v : new Version(99, 0);

    /// <summary>Re-reads the profiles and the shared controls (the tab opened, a profile was isolated or added).</summary>
    public void Reload()
    {
        StopListening();
        _pending.Clear();
        _saveTimer.Stop();
        var all = ProfileService.Instance.GetProfiles();
        _targets = SharedControls.Targets(all);
        var versions = _targets.Select(p => p.MinecraftVersion).Distinct().OrderByDescending(Order).ToList();
        var values = SharedControls.Load(PathService.Instance, _targets, ProfileService.Instance.GetActiveProfile().Id);

        _loading = true;
        FovSlider.Value = GameControls.FovDegrees(values.GetValueOrDefault("fov"));
        SensitivitySlider.Value = GameControls.SensitivityPercent(values.GetValueOrDefault("mouseSensitivity"));
        RenderDistanceSlider.Value = GameControls.Int(values.GetValueOrDefault("renderDistance"), 12, 2, 32);
        SimulationDistanceSlider.Value = GameControls.Int(values.GetValueOrDefault("simulationDistance"), 12, 5, 32);
        MaxFpsSlider.Value = GameControls.Int(values.GetValueOrDefault("maxFps"), 120, 10, 260);
        GuiScaleBox.SelectedIndex = GameControls.Int(values.GetValueOrDefault("guiScale"), 0, 0, 6);
        VsyncCheck.IsChecked = values.GetValueOrDefault("enableVsync") != "false";
        BuildKeyList(versions.Count > 0 ? versions : new List<string> { ProfileService.NewestVersion }, values);
        _loading = false;
        foreach (var label in _sliderLabels) label();
        RenderKeyRows();

        // The scope: every version, then who keeps their own and what follows Lunar.
        ScopeTitle.Text = _targets.Count > 0 ? "Applies to all Lads Client versions" : "Every profile keeps its own settings";
        VersionChips.Children.Clear();
        foreach (var version in versions)
            VersionChips.Children.Add(new Border { Classes = { "chip" }, Margin = new Thickness(0, 0, 6, 4), Child = new TextBlock { Text = version }.Untranslated() });
        var isolated = all.Where(p => p.IsIsolated).ToList();
        IsolatedNote.Text = isolated.Count == 0 ? "" : (_targets.Count == 0 ? "All profiles are isolated, so changes here only update the shared copy new profiles start from. Isolated: " : "Isolated profiles keep their own settings: ")
            + string.Join(", ", isolated.Select(p => $"{p.Name} ({p.MinecraftVersion})")) + ".";
        IsolatedNote.IsVisible = isolated.Count > 0;
        var notes = new List<string>();
        var running = _targets.Where(p => RunningGameMarker.IsRunning(PathService.Instance.GetProfileDirectory(p))).ToList();
        if (running.Count > 0)
            notes.Add($"Minecraft {string.Join(", ", running.Select(p => p.MinecraftVersion).Distinct())} is running: it gets your changes when it closes.");
        if (_targets.Any(Legacy) && GameOptionsService.LunarOptions18() != null)
            notes.Add("Minecraft 1.8.9 also follows Lunar Client: a key bind or setting you change in Lunar's 1.8 profile replaces this one at its next launch.");
        WarningNote.Text = string.Join("\n", notes);
        WarningNote.IsVisible = notes.Count > 0;
        Status("");
    }

    /// <summary>Every target version's vanilla keys once, newest version's order first; values in modern names.</summary>
    private void BuildKeyList(IReadOnlyList<string> versions, IReadOnlyDictionary<string, string> values)
    {
        var keys = new List<GameKeyBinding>();
        var seen = new HashSet<string>(StringComparer.Ordinal);
        foreach (var version in versions)
            foreach (var key in GameControls.VanillaKeys(version))
                if (seen.Add(key.Id)) keys.Add(key);
        _keys = keys;
        _keyValues.Clear();
        foreach (var key in _keys) _keyValues[key.Id] = values.GetValueOrDefault("key_" + key.Id) ?? key.Default;
        _keyVersions = _keys.ToDictionary(k => k.Id, k => versions.Where(v => GameControls.VanillaKeys(v).Any(x => x.Id == k.Id)).ToList(), StringComparer.Ordinal);
        _versions = versions;
    }

    private Dictionary<string, List<string>> _keyVersions = new(StringComparer.Ordinal);
    private IReadOnlyList<string> _versions = Array.Empty<string>();

    private void HookSlider(Slider slider, TextBlock label, string key, Func<int, string> stored, Func<int, string> text)
    {
        _sliderLabels.Add(() => label.Text = text((int)Math.Round(slider.Value)));
        slider.ValueChanged += (_, _) =>
        {
            int value = (int)Math.Round(slider.Value);
            label.Text = text(value);
            Change(key, stored(value));
        };
    }

    private void Change(string key, string modern)
    {
        if (_loading) return;
        _pending[key] = modern;
        _saveTimer.Stop();
        _saveTimer.Start();
        SaveStateChanged?.Invoke("Saving…", false);
    }

    private async Task SaveAsync()
    {
        if (_pending.Count == 0) return;
        var edits = new Dictionary<string, string>(_pending, StringComparer.Ordinal);
        var targets = _targets;
        var result = await Task.Run(() => SharedControls.Save(PathService.Instance, targets, edits));
        foreach (var (key, value) in edits)
            if (_pending.TryGetValue(key, out var now) && now == value) _pending.Remove(key);
        if (result.Errors.Count > 0)
        {
            Status("Not saved for " + string.Join("; ", result.Errors), true);
            SaveStateChanged?.Invoke("Some controls were not saved", true);
            return;
        }
        int versions = result.Applied.Concat(result.Deferred).Select(p => p.MinecraftVersion).Distinct().Count();
        Status($"Saved for {versions} version{(versions == 1 ? "" : "s")}"
            + (result.Deferred.Count > 0 ? $". {string.Join(", ", result.Deferred.Select(p => p.Name))} get{(result.Deferred.Count == 1 ? "s" : "")} the change when the game closes." : ".")
            + (_warning != null ? " " + _warning : ""), _warning != null);
        _warning = null;
        SaveStateChanged?.Invoke("All changes saved", false);
    }

    // Shown with the next save's result (a bind 1.8.9 cannot store).
    private string? _warning;

    private void Status(string text, bool error = false)
    {
        StatusLine.Text = text;
        StatusLine.IsVisible = text.Length > 0;
        StatusLine.Foreground = new SolidColorBrush(Color.Parse(error ? "#E27676" : "#A4BAA7"));
    }

    private void RenderKeyRows()
    {
        foreach (var grid in new[] { KeyRows, KeyRowsNewer, KeyRowsLegacy }) grid.Children.Clear();
        _keyButtons.Clear();
        bool anyLegacy = _versions.Any(GameOptionsService.IsLegacy18);
        var modern = _versions.Where(v => !GameOptionsService.IsLegacy18(v)).ToList();
        foreach (var key in _keys)
        {
            var has = _keyVersions[key.Id];
            bool common = has.Count == _versions.Count;
            bool legacyOnly = !common && has.All(GameOptionsService.IsLegacy18);
            var target = common ? KeyRows : legacyOnly ? KeyRowsLegacy : KeyRowsNewer;
            // Only some newer versions: say from which one ("26.2+").
            string? tag = !common && !legacyOnly && has.Count < modern.Count ? has.OrderBy(Order).First() + "+" : null;

            var row = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto,Auto"), Margin = new Thickness(0, 2, 28, 2), MinHeight = 34 };
            row.Children.Add(new TextBlock { Text = key.Label, Classes = { "keyLabel" }, TextTrimming = TextTrimming.CharacterEllipsis });
            if (tag != null)
            {
                var tagText = new TextBlock { Text = tag, Classes = { "keyTag" } }.Untranslated();
                Grid.SetColumn(tagText, 1);
                row.Children.Add(tagText);
            }
            var button = new Button { Classes = { "keybind", LauncherTranslator.NoTranslate } }; // key names stay as Minecraft shows them
            Grid.SetColumn(button, 2);
            button.Click += (_, _) => { StopListening(); _listening = key; button.Content = "> press a key <"; button.Classes.Add("listening"); };
            row.Children.Add(button);
            _keyButtons[key.Id] = button;
            target.Children.Add(row);
        }
        bool newer = KeyRowsNewer.Children.Count > 0, legacy = KeyRowsLegacy.Children.Count > 0;
        KeyGroupAll.IsVisible = newer || legacy;
        KeyGroupNewer.IsVisible = KeyRowsNewer.IsVisible = newer;
        KeyGroupNewer.Text = anyLegacy ? "NOT IN 1.8.9" : "NEWER VERSIONS ONLY";
        KeyGroupLegacy.IsVisible = KeyRowsLegacy.IsVisible = legacy;
        RefreshKeys();
    }

    private void RefreshKeys()
    {
        var conflicts = GameControls.Conflicts(_keys.Select(k => (k, _keyValues[k.Id])), false);
        foreach (var key in _keys)
        {
            var button = _keyButtons[key.Id];
            var value = _keyValues[key.Id];
            button.Content = GameControls.DisplayName(value);
            button.Classes.Set("conflict", conflicts.Contains(key.Id));
            ToolTip.SetTip(button, conflicts.Contains(key.Id)
                ? "Also bound to: " + string.Join(", ", _keys.Where(k => k != key && conflicts.Contains(k.Id) && _keyValues[k.Id] == value).Select(k => k.Label))
                : null);
        }
        ConflictNote.Text = conflicts.Count == 0 ? "" : $"{conflicts.Count} key binds share a key with another (red). Minecraft runs only one of them.";
        ConflictNote.IsVisible = conflicts.Count > 0;
    }

    private void StopListening()
    {
        if (_listening == null) return;
        _keyButtons[_listening.Id].Classes.Remove("listening");
        _listening = null;
        RefreshKeys();
    }

    private void Bind(string? modernKey)
    {
        if (_listening is not { } key) return;
        StopListening();
        if (modernKey == null) { Status("That key cannot be bound in Minecraft.", true); return; }
        _keyValues[key.Id] = modernKey;
        RefreshKeys();
        Change("key_" + key.Id, modernKey);
        // 1.8.9 keeps its own bind for a key it has no code for (SharedControls.ToVersion).
        if (_keyVersions[key.Id].Any(GameOptionsService.IsLegacy18) && GameControls.ToStored(modernKey, true) == null)
            _warning = $"Minecraft 1.8.9 has no key code for {GameControls.DisplayName(modernKey)}: it keeps its own {key.Label} bind.";
    }

    private void OnKeyDown(object? sender, KeyEventArgs e)
    {
        if (_listening == null) return;
        e.Handled = true;
        Bind(e.Key == Key.Escape ? GameControls.Unbound : GameControls.KeyName(e.PhysicalKey));
    }

    private void OnPointerPressed(object? sender, PointerPressedEventArgs e)
    {
        if (_listening == null) return;
        e.Handled = true;
        Bind(GameControls.MouseName(e.GetCurrentPoint(this).Properties.PointerUpdateKind switch
        {
            PointerUpdateKind.LeftButtonPressed => MouseButton.Left,
            PointerUpdateKind.RightButtonPressed => MouseButton.Right,
            PointerUpdateKind.MiddleButtonPressed => MouseButton.Middle,
            PointerUpdateKind.XButton1Pressed => MouseButton.XButton1,
            PointerUpdateKind.XButton2Pressed => MouseButton.XButton2,
            _ => MouseButton.None
        }));
    }

    private void ResetKeys_Click(object? sender, RoutedEventArgs e)
    {
        StopListening();
        foreach (var key in _keys)
        {
            if (_keyValues[key.Id] == key.Default) continue;
            _keyValues[key.Id] = key.Default;
            Change("key_" + key.Id, key.Default);
        }
        RefreshKeys();
    }
}
