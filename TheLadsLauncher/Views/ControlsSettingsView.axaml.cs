using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Media;
using Avalonia.Threading;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

/// <summary>
/// Settings → Controls: field of view, sensitivity, video options and every vanilla key bind of one profile's options.txt.
/// Changes save on their own (debounced); only changed keys are rewritten, every other line stays as it was. Nothing is
/// written while that profile's game runs: Minecraft rewrites options.txt when it closes.
/// </summary>
public partial class ControlsSettingsView : UserControl
{
    private readonly DispatcherTimer _saveTimer = new() { Interval = TimeSpan.FromMilliseconds(500) };
    // options.txt key -> value as this version stores it, not yet written.
    private readonly Dictionary<string, string> _pending = new(StringComparer.Ordinal);
    private readonly Dictionary<string, string> _keyValues = new(StringComparer.Ordinal);
    private readonly Dictionary<string, Button> _keyButtons = new(StringComparer.Ordinal);
    private readonly List<Action> _sliderLabels = new();
    private IReadOnlyList<GameKeyBinding> _keys = Array.Empty<GameKeyBinding>();
    private LauncherProfile? _profile;
    private GameKeyBinding? _listening;
    private bool _legacy, _loading;

    public ControlsSettingsView()
    {
        InitializeComponent();
        _saveTimer.Tick += async (_, _) => { _saveTimer.Stop(); await SaveAsync(); };
        HookSlider(FovSlider, FovValue, "fov", v => GameControls.FovStored(v), v => v == 70 ? "70 (Normal)" : v + "");
        HookSlider(SensitivitySlider, SensitivityValue, "mouseSensitivity", v => GameControls.SensitivityStored(v), v => v + "%");
        HookSlider(RenderDistanceSlider, RenderDistanceValue, "renderDistance", v => v + "", v => v + " chunks");
        HookSlider(SimulationDistanceSlider, SimulationDistanceValue, "simulationDistance", v => v + "", v => v + " chunks");
        HookSlider(MaxFpsSlider, MaxFpsValue, "maxFps", v => v + "", v => v >= 260 ? "Unlimited" : v + " fps");
        GuiScaleBox.SelectionChanged += (_, _) => { if (GuiScaleBox.SelectedIndex >= 0) Change("guiScale", GuiScaleBox.SelectedIndex + ""); };
        VsyncCheck.IsCheckedChanged += (_, _) => Change("enableVsync", VsyncCheck.IsChecked == true ? "true" : "false");
        AddHandler(KeyDownEvent, OnKeyDown, RoutingStrategies.Tunnel);
        AddHandler(PointerPressedEvent, OnPointerPressed, RoutingStrategies.Tunnel);
    }

    /// <summary>The profile shown, for the QA preview.</summary>
    public LauncherProfile? Profile => _profile;

    /// <summary>Writes pending changes now (the QA preview, leaving the tab, switching profile).</summary>
    public async Task FlushAsync()
    {
        if (!_saveTimer.IsEnabled) return;
        _saveTimer.Stop();
        await SaveAsync();
    }

    protected override void OnAttachedToVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnAttachedToVisualTree(e);
        var profiles = ProfileService.Instance.GetProfiles();
        var keep = _profile?.Id ?? ProfileService.Instance.GetActiveProfile().Id;
        _loading = true;
        ProfilePicker.ItemsSource = profiles;
        ProfilePicker.SelectedItem = profiles.FirstOrDefault(p => p.Id == keep) ?? profiles.FirstOrDefault();
        _loading = false;
        if (ProfilePicker.SelectedItem is LauncherProfile profile) Load(profile);
    }

    protected override void OnDetachedFromVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnDetachedFromVisualTree(e);
        StopListening();
        _ = FlushAsync();
    }

    private async void ProfilePicker_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (_loading || ProfilePicker.SelectedItem is not LauncherProfile profile || profile.Id == _profile?.Id) return;
        await FlushAsync();
        Load(profile);
    }

    private static string OptionsPath(LauncherProfile profile) => Path.Combine(PathService.Instance.GetProfileDirectory(profile), "options.txt");

    // Latin-1 maps every byte to one char: lines the launcher does not change are written back byte for byte.
    private static string ReadText(string path) => File.ReadAllText(path, Encoding.Latin1);

    private void Load(LauncherProfile profile)
    {
        StopListening();
        _pending.Clear();
        _saveTimer.Stop();
        _profile = profile;
        Status("");
        _legacy = GameOptionsService.IsLegacy18(profile.MinecraftVersion);
        var file = OptionsPath(profile);
        var lunar = _legacy ? GameOptionsService.LunarOptions18() : null;
        // Before Minecraft's first save the launch copies the shared settings (1.8.9: Lunar's when installed) into the profile.
        var source = File.Exists(file) ? file : profile.IsIsolated ? null : lunar ?? PathService.Instance.SharedOptionsFile;
        GameOptionsFile options;
        try { options = GameOptionsFile.Parse(source != null && File.Exists(source) ? ReadText(source) : ""); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            options = GameOptionsFile.Parse("");
            Status($"Could not read '{source}': {e.Message}", true);
        }

        _loading = true;
        FovSlider.Value = GameControls.FovDegrees(options.Get("fov"));
        SensitivitySlider.Value = GameControls.SensitivityPercent(options.Get("mouseSensitivity"));
        RenderDistanceSlider.Value = GameControls.Int(options.Get("renderDistance"), 12, 2, 32);
        SimulationDistanceSlider.Value = GameControls.Int(options.Get("simulationDistance"), 12, 5, 32);
        SimulationPanel.IsVisible = !_legacy;
        MaxFpsSlider.Value = GameControls.Int(options.Get("maxFps"), 120, 10, 260);
        GuiScaleBox.ItemsSource = _legacy ? new[] { "Auto", "Small", "Normal", "Large" } : new[] { "Auto", "1", "2", "3", "4", "5", "6" };
        GuiScaleBox.SelectedIndex = GameControls.Int(options.Get("guiScale"), 0, 0, _legacy ? 3 : 6);
        VsyncCheck.IsChecked = options.Get("enableVsync") != "false";
        _keys = GameControls.VanillaKeys(profile.MinecraftVersion);
        _keyValues.Clear();
        foreach (var key in _keys)
        {
            var stored = options.Get("key_" + key.Id);
            // Shared and Lunar files name keys; 1.8.9 stores codes.
            if (stored != null && _legacy && !int.TryParse(stored, out _)) stored = GameControls.ToStored(stored, true);
            _keyValues[key.Id] = stored ?? GameControls.ToStored(key.Default, _legacy)!;
        }
        _loading = false;
        foreach (var label in _sliderLabels) label();
        BuildKeyRows();

        SourceNote.Text = (File.Exists(file) ? $"Saved in {file}." : $"Minecraft has not saved settings for this profile yet; changes go to {file}.")
            + (profile.IsIsolated ? " This profile keeps its own game settings." : " Shared with every profile that is not isolated.");
        bool running = RunningGameMarker.IsRunning(PathService.Instance.GetProfileDirectory(profile));
        WarningNote.Text = running ? $"Minecraft ({profile.Name}) is running. Close it to change these settings: the game saves its own when it closes."
            : _legacy && !profile.IsIsolated && lunar != null ? "Minecraft 1.8.9 takes key binds, field of view, sensitivity and GUI scale from Lunar Client at every launch. Make this profile isolated (Profiles) to keep the ones set here."
            : "";
        WarningNote.IsVisible = WarningNote.Text.Length > 0;
        GameCard.IsEnabled = KeysCard.IsEnabled = !running;
    }

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

    private void Change(string key, string stored)
    {
        if (_loading || _profile == null) return;
        _pending[key] = stored;
        _saveTimer.Stop();
        _saveTimer.Start();
        Status("Saving…");
    }

    private async Task SaveAsync()
    {
        if (_profile is not { } profile || _pending.Count == 0) return;
        var edits = new Dictionary<string, string>(_pending, StringComparer.Ordinal);
        var file = OptionsPath(profile);
        try
        {
            if (RunningGameMarker.IsRunning(Path.GetDirectoryName(file)!))
            {
                GameCard.IsEnabled = KeysCard.IsEnabled = false;
                Status($"Not saved: Minecraft ({profile.Name}) is running. Close it and change the setting again.", true);
                return;
            }
            if (SafeFileOps.IsLink(file)) throw new IOException($"'{file}' is a link; the launcher only edits a regular options.txt.");
            await Task.Run(async () =>
            {
                var options = GameOptionsFile.Parse(File.Exists(file) ? ReadText(file) : "");
                foreach (var (key, value) in edits) options.Set(key, value);
                await LockFiles.WriteAtomicallyAsync(file, Encoding.Latin1.GetBytes(options.ToString()));
                // A profile that is not isolated takes the shared settings at launch: they get the change too, as after a game
                // exits (ProfileService.SyncProfileToSharedAsync). Lunar's 1.8 file is only ever read.
                if (!profile.IsIsolated && !(_legacy && GameOptionsService.LunarOptions18() != null))
                    GameOptionsService.SyncFromInstance(file, PathService.Instance.SharedOptionsFile, profile.MinecraftVersion);
            });
            foreach (var (key, value) in edits)
                if (_pending.TryGetValue(key, out var now) && now == value) _pending.Remove(key);
            if (_profile == profile) Status("All changes saved to " + file);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            Status("Not saved: " + e.Message, true);
        }
    }

    private void Status(string text, bool error = false)
    {
        StatusLine.Text = text;
        StatusLine.Foreground = new SolidColorBrush(Color.Parse(error ? "#E27676" : "#A4BAA7"));
    }

    private void BuildKeyRows()
    {
        KeyRows.Children.Clear();
        _keyButtons.Clear();
        foreach (var key in _keys)
        {
            var row = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), Margin = new Thickness(0, 0, 24, 6) };
            row.Children.Add(new TextBlock { Text = key.Label, Classes = { "label" }, TextTrimming = TextTrimming.CharacterEllipsis });
            var button = new Button { Classes = { "keybind" } };
            Grid.SetColumn(button, 1);
            button.Click += (_, _) => { StopListening(); _listening = key; button.Content = "> press a key <"; button.Classes.Add("listening"); };
            row.Children.Add(button);
            _keyButtons[key.Id] = button;
            KeyRows.Children.Add(row);
        }
        RefreshKeys();
    }

    private void RefreshKeys()
    {
        var conflicts = GameControls.Conflicts(_keys.Select(k => (k, _keyValues[k.Id])), _legacy);
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
        if (GameControls.ToStored(modernKey, _legacy) is not { } stored)
        {
            Status($"Minecraft 1.8.9 has no key code for {GameControls.DisplayName(modernKey)}.", true);
            return;
        }
        _keyValues[key.Id] = stored;
        RefreshKeys();
        Change("key_" + key.Id, stored);
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
            var stored = GameControls.ToStored(key.Default, _legacy)!;
            if (_keyValues[key.Id] == stored) continue;
            _keyValues[key.Id] = stored;
            Change("key_" + key.Id, stored);
        }
        RefreshKeys();
    }
}
