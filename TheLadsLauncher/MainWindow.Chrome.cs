using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.LogicalTree;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Threading;
using Avalonia.VisualTree;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using TheLadsLauncher.Views;

namespace TheLadsLauncher;

// Launcher chrome (1.6.0): animated maximize, the Halloween theme's accents, auto-saving settings, Installed Mods icons and
// the --preview-chrome QA capture.
public partial class MainWindow
{
    // ═══════════════════════════════════════
    //  ANIMATED MAXIMIZE
    // ═══════════════════════════════════════

    // Maximize is an animated move to the screen's working area (with the 16:9 lock the largest 16:9 rectangle in it): the OS
    // maximize of this undecorated window jumps without animation and ignores the lock.
    private PixelRect? _restoreBounds;
    private DispatcherTimer? _boundsAnimation;

    private double ScreenScaling() => Screens.ScreenFromWindow(this)?.Scaling ?? RenderScaling;

    private PixelRect PixelBounds()
    {
        double scaling = ScreenScaling();
        return new PixelRect(Position, new PixelSize((int)Math.Round(Width * scaling), (int)Math.Round(Height * scaling)));
    }

    private PixelRect? MaximizedTarget() =>
        Screens.ScreenFromWindow(this) is { } screen ? WindowGeometry.Maximized(screen.WorkingArea, _lockAspect, ASPECT_RATIO) : null;

    private bool IsAnimatedMaximized() => _restoreBounds != null && WindowState == WindowState.Normal && MaximizedTarget() is { } max
        && PixelBounds() is var now && Math.Abs(now.Width - max.Width) <= 2 && Math.Abs(now.Height - max.Height) <= 2;

    private void ToggleAnimatedMaximize()
    {
        if (WindowState != WindowState.Normal) { WindowState = WindowState.Normal; return; } // maximized by Windows itself (Win+Up, snap)
        if (IsAnimatedMaximized())
        {
            var restore = _restoreBounds!.Value;
            _restoreBounds = null;
            AnimateBounds(restore);
        }
        else
        {
            _restoreBounds = PixelBounds();
            AnimateToMaximized();
        }
        ShowMaximizeState();
    }

    /// <summary>Reset Size: the window is no longer maximized, and no animation may still move it.</summary>
    private void ForgetAnimatedMaximize()
    {
        _boundsAnimation?.Stop();
        _restoreBounds = null;
        ShowMaximizeState();
    }

    private void ShowMaximizeState()
    {
        MaximizeBtn.Content = _restoreBounds != null ? "❐" : "□";
        ToolTip.SetTip(MaximizeBtn, _restoreBounds != null ? "Restore" : "Maximize");
    }

    private void AnimateToMaximized()
    {
        if (MaximizedTarget() is { } target) AnimateBounds(target);
    }

    private void AnimateBounds(PixelRect to)
    {
        _boundsAnimation?.Stop();
        var from = PixelBounds();
        double scaling = ScreenScaling();
        void Apply(PixelRect r)
        {
            // The aspect handler (OnPropertyChanged) must not answer these steps with resizes of its own: that was the flicker.
            _adjustingAspect = true;
            try { Position = r.Position; Width = r.Width / scaling; Height = r.Height / scaling; }
            finally { _adjustingAspect = false; }
        }
        if (settings.ReducedMotion) { Apply(to); return; }
        var clock = Stopwatch.StartNew();
        var timer = new DispatcherTimer(DispatcherPriority.Render) { Interval = TimeSpan.FromMilliseconds(15) };
        timer.Tick += (_, _) =>
        {
            double t = Math.Min(1, clock.Elapsed.TotalMilliseconds / 200);
            Apply(WindowGeometry.Lerp(from, to, 1 - Math.Pow(1 - t, 3))); // ease-out cubic
            if (t >= 1) timer.Stop();
        };
        _boundsAnimation = timer;
        timer.Start();
    }

    // ═══════════════════════════════════════
    //  HALLOWEEN THEME ACCENTS
    // ═══════════════════════════════════════

    private void ApplySeasonalChrome()
    {
        bool halloween = settings.Theme == "Halloween";
        Classes.Set("halloween", halloween); // LauncherTheme.axaml: the pumpkin launch button
        Background = Brush.Parse(halloween ? "#17121C" : "#17181B");
        TitleBar.Background = Sidebar.Background = Brush.Parse(halloween ? "#110D16" : "#121315");
        ContentTitleBar.Background = Brush.Parse(halloween ? "#17121C" : "#17181B");
        TitleBar.BorderBrush = Brush.Parse(halloween ? "#F28A2E" : "#303137");
        ContentTitleBar.BorderBrush = halloween
            ? new LinearGradientBrush
            {
                StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative), EndPoint = new RelativePoint(1, 0, RelativeUnit.Relative),
                GradientStops = { new GradientStop(Color.Parse("#F28A2E"), 0), new GradientStop(Color.Parse("#8A4FD0"), 1) }
            }
            : Brush.Parse("#303137");
        Sidebar.BorderBrush = Brush.Parse(halloween ? "#2E2238" : "#303137");
        SeasonalBadge.IsVisible = halloween;
    }

    // ═══════════════════════════════════════
    //  SETTINGS AUTO-SAVE
    // ═══════════════════════════════════════

    private readonly DispatcherTimer _settingsSaveTimer = new() { Interval = TimeSpan.FromMilliseconds(500) };
    private bool _settingsAutoSave;

    /// <summary>Every Settings control saves on change (debounced) through SaveSettingsFromUi. Hooked after LoadSettingsUI, so
    /// filling the controls at start-up saves nothing.</summary>
    private void InitializeSettingsAutoSave()
    {
        _settingsSaveTimer.Tick += (_, _) => { _settingsSaveTimer.Stop(); SaveSettingsFromUi(); };
        foreach (var box in new[] { CloseToTrayCheckbox, KeepLauncherOpenCheckbox, KeepClosedOnExitCheckbox, AutoLaunchCheckbox, AutoFixCrashesCheckbox,
                     AutoRelaunchOnCrashCheckbox, AutoRejoinServerCheckbox, MultiInstanceCheckbox, FullscreenOnLaunchCheckbox, QuickLaunchCheckbox,
                     SyncScreenshotsCheckbox, ParticleCheckbox })
            box.IsCheckedChanged += (_, _) => ScheduleSettingsSave();
        foreach (var combo in new[] { ThemeSelector, UiScaleSelector, QuickLaunchServerComboBox, GraphicsRendererSelector, JavaSelector })
            combo.SelectionChanged += (_, _) => ScheduleSettingsSave();
        foreach (var box in new[] { MicrosoftClientIdTextBox, FabricVersionBox, CurseForgeApiKeyBox, ModrinthApiUrlBox, CurseForgeApiUrlBox, MinecraftVersionOverrideBox })
            box.TextChanged += (_, _) => ScheduleSettingsSave();
        RamSlider.ValueChanged += (_, _) => ScheduleSettingsSave();
        // The Mods page's API boxes edit the same settings as Paths & services.
        Mirror(ModrinthApiUrlBox_ModTab, ModrinthApiUrlBox);
        Mirror(CurseForgeApiUrlBox_ModTab, CurseForgeApiUrlBox);
        Mirror(CfApiKeyInputBox, CurseForgeApiKeyBox);
        Closing += (_, _) => FlushSettingsSave();
        _settingsAutoSave = true;
    }

    private static void Mirror(TextBox a, TextBox b)
    {
        a.TextChanged += (_, _) => { if (b.Text != a.Text) b.Text = a.Text; };
        b.TextChanged += (_, _) => { if (a.Text != b.Text) a.Text = b.Text; };
    }

    private void ScheduleSettingsSave()
    {
        if (!_settingsAutoSave) return;
        _settingsSaveTimer.Stop();
        _settingsSaveTimer.Start();
        SettingsSaveStatus.Text = "Saving…";
        SettingsSaveStatus.Foreground = Brush.Parse("#A0A1AA");
    }

    /// <summary>ApplyProfile changes these settings: their boxes show the new values, or the next auto-save would write the old
    /// ones back.</summary>
    private void ShowProfileSettings()
    {
        bool autoSave = _settingsAutoSave;
        _settingsAutoSave = false;
        FabricVersionBox.Text = settings.FabricVersion;
        MinecraftVersionOverrideBox.Text = settings.SelectedMinecraftVersionOverride;
        InstancePathBox.Text = settings.InstancePath;
        if (JavaSelector.Items.Contains(settings.JavaPath)) JavaSelector.SelectedItem = settings.JavaPath;
        _settingsAutoSave = autoSave;
    }

    /// <summary>Saves a change still waiting for the timer (closing, exit from the tray).</summary>
    private void FlushSettingsSave()
    {
        if (!_settingsSaveTimer.IsEnabled) return;
        _settingsSaveTimer.Stop();
        SaveSettingsFromUi();
    }

    /// <summary>Shows <paramref name="error"/> under the field (Fluent's validation display), or clears it. True when valid.</summary>
    private static bool ShowFieldError(Control field, string? error)
    {
        if (error == null) DataValidationErrors.ClearErrors(field);
        else DataValidationErrors.SetErrors(field, new object[] { error });
        return error == null;
    }

    private static string? HttpUrlError(string? text, string what) =>
        Uri.TryCreate(text?.Trim(), UriKind.Absolute, out var uri) && uri.Scheme is "http" or "https" ? null : what + " must be a full http(s) address.";

    // ═══════════════════════════════════════
    //  INSTALLED MODS: ICONS, TWO COLUMNS
    // ═══════════════════════════════════════

    private ModIconService? _modIcons;
    // Icon file -> decoded image (null: not an image Avalonia can show, e.g. SVG).
    private readonly ConcurrentDictionary<string, Bitmap?> _modIconBitmaps = new(StringComparer.OrdinalIgnoreCase);
    // Row key -> icon shown, so a re-render (every search keystroke) shows it at once; rows tried this session.
    private readonly Dictionary<string, Bitmap> _modIconByRow = new(StringComparer.OrdinalIgnoreCase);
    private readonly HashSet<string> _modIconTried = new(StringComparer.OrdinalIgnoreCase);
    private bool _modIconsBusy, _modIconsAgain;
    private static readonly Lazy<Bitmap?> LadsModIcon = new(() =>
    {
        try { return new Bitmap(Avalonia.Platform.AssetLoader.Open(new Uri("avares://TheLadsLauncher/Assets/icon.ico"))); }
        catch (Exception) { return null; } // the placeholder letter stays
    });

    // ponytail: keyed by path, so a jar replaced under the same name keeps its old icon until the launcher restarts.
    private static string ModIconKey(ModInventoryEntry e) => e.FilePath ?? (e.ProjectId != null ? "project:" + e.ProjectId : "id:" + e.Id);

    private void ModsListScroller_SizeChanged(object? sender, SizeChangedEventArgs e) =>
        ModsList.ItemWidth = Math.Max(1, Math.Floor(e.NewSize.Width / 2));

    /// <summary>A 40 px icon frame: the mod's initial until its icon is known.</summary>
    private Control ModIconBox(ModInventoryEntry entry, out Image image)
    {
        image = new Image { Width = 40, Height = 40, Stretch = Stretch.UniformToFill };
        if (_modIconByRow.TryGetValue(ModIconKey(entry), out var known)) image.Source = known;
        var initial = entry.DisplayName.FirstOrDefault(char.IsLetterOrDigit);
        var placeholder = new TextBlock
        {
            Text = initial == default ? "?" : char.ToUpperInvariant(initial).ToString(), FontSize = 18, FontWeight = FontWeight.Bold,
            Foreground = Brush.Parse("#6E717C"), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center
        };
        if (entry.Ownership is ModOwnership.Core or ModOwnership.NativeModule)
        {
            placeholder.Bind(TextBlock.ForegroundProperty, this.GetResourceObservable("LadsAccent"));
            image.Source ??= LadsModIcon.Value; // Lads modules have no jar of their own: the Lads icon
        }
        var frame = new Grid();
        frame.Children.Add(placeholder);
        frame.Children.Add(image);
        return new Border
        {
            Width = 40, Height = 40, CornerRadius = new CornerRadius(6), ClipToBounds = true, Background = Brush.Parse("#25262A"),
            Margin = new Thickness(0, 2, 12, 0), VerticalAlignment = VerticalAlignment.Top, Child = frame
        };
    }

    private async Task LoadModIconsAsync()
    {
        if (_modIconsBusy) { _modIconsAgain = true; return; }
        _modIconsBusy = true;
        try
        {
            _modIcons ??= new ModIconService(Path.Combine(_pathService.BaseDirectory, "cache", "mod-icons"), _httpClient, settings.ModrinthApiUrl);
            do
            {
                _modIconsAgain = false;
                var entries = _modRows.Select(r => r.Entry).Where(e => _modIconTried.Add(ModIconKey(e))).ToList();
                if (entries.Count == 0) break;
                await _modIcons.LoadAsync(entries, (entry, file) =>
                {
                    if (_modIconBitmaps.GetOrAdd(file, DecodeModIcon) is not { } bitmap) return;
                    var key = ModIconKey(entry);
                    Dispatcher.UIThread.Post(() =>
                    {
                        _modIconByRow[key] = bitmap;
                        foreach (var row in _modRows.Where(r => ModIconKey(r.Entry).Equals(key, StringComparison.OrdinalIgnoreCase))) row.Icon.Source = bitmap;
                    });
                });
            } while (_modIconsAgain);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or InvalidDataException or OperationCanceledException)
        {
            Log($"[Mods] Could not load mod icons: {ex.Message}");
        }
        finally { _modIconsBusy = false; }
    }

    private static Bitmap? DecodeModIcon(string file)
    {
        try
        {
            using var stream = File.OpenRead(file);
            return Bitmap.DecodeToWidth(stream, 80, BitmapInterpolationMode.HighQuality);
        }
        catch (Exception) { return null; } // foreign bytes (SVG, a broken image): the placeholder stays
    }

    // ═══════════════════════════════════════
    //  --preview-chrome (QA)
    // ═══════════════════════════════════════

    // Program.Main validates the sandbox roots before this runs. Screenshots of every changed screen in the default and the
    // Halloween theme, plus checks of auto-save, validation, the animated maximize and the Controls tab's options.txt writes.
    private async Task RunChromePreviewAsync(string output)
    {
        Directory.CreateDirectory(output);
        var report = new JsonObject();
        int exitCode = 0;
        void Check(string name, bool ok, string detail)
        {
            report[name] = new JsonObject { ["ok"] = ok, ["detail"] = detail };
            if (!ok) exitCode = 1;
        }
        async Task Shot(string name, int delay = 450) { await Task.Delay(delay); SaveWindowScreenshot(Path.Combine(output, name + ".png")); }
        var settingsTabs = SettingsPage.GetLogicalDescendants().OfType<TabControl>().First();
        void SettingsTab(string header) => settingsTabs.SelectedItem = settingsTabs.Items.OfType<TabItem>().First(t => (string?)t.Header == header);
        static void ScrollTop(Control inside) => inside.FindAncestorOfType<ScrollViewer>()!.Offset = default;
        static void ScrollTo(Control target)
        {
            var scroller = target.FindAncestorOfType<ScrollViewer>()!;
            scroller.Offset = new Vector(0, target.TranslatePoint(default, (Visual)scroller.Content!)?.Y ?? 0);
        }
        JsonNode? Saved(string property) => JsonNode.Parse(File.ReadAllText(Path.Combine(_pathService.BaseDirectory, "settings.json")))?[property];
        try
        {
            Width = DEFAULT_WIDTH; Height = DEFAULT_HEIGHT;
            ReducedMotionCheck.IsChecked = false;
            foreach (var theme in new[] { "DarkRed", "Halloween" })
            {
                string tag = theme == "DarkRed" ? "default" : "halloween";
                ThemeSelector.SelectedItem = theme;
                FlushSettingsSave();
                NavigateTo("Home");
                await Shot($"home-{tag}");
                ((IPseudoClasses)CloseBtn.Classes).Set(":pointerover", true);
                await Shot($"close-hover-{tag}");
                ((IPseudoClasses)CloseBtn.Classes).Set(":pointerover", false);
                NavigateTo("Settings");
                SettingsTab("General");
                await Task.Delay(300);
                ScrollTop(CloseToTrayCheckbox); // the first pass left it scrolled to Behavior
                await Shot($"settings-general-{tag}");
                ScrollTo(CloseToTrayCheckbox.FindAncestorOfType<Border>()!);
                await Shot($"settings-behavior-{tag}");
                SettingsTab("Controls");
                await Task.Delay(300);
                ScrollTop(ControlsSettings.FindControl<Border>("KeysCard")!);
                await Shot($"settings-controls-{tag}", 700);
                ScrollTo(ControlsSettings.FindControl<Border>("KeysCard")!);
                await Shot($"settings-keybinds-{tag}");
                NavigateTo("Mods");
                await ReloadModsInventoryAsync();
                for (int i = 0; i < 40 && (_modIconsBusy || _modRows.Count == 0); i++) await Task.Delay(250);
                await Shot($"mods-installed-{tag}", 800);
            }
            Check("modIcons", _modRows.Count(r => r.Icon.Source != null) > 0,
                $"{_modRows.Count(r => r.Icon.Source != null)} of {_modRows.Count} rows show an icon; cache {Path.Combine(_pathService.BaseDirectory, "cache", "mod-icons")}");

            // The 1.8.9 (Forge) profile's list: mcmod.info icons or Modrinth by hash.
            ThemeSelector.SelectedItem = "DarkRed";
            FlushSettingsSave();
            ComboBoxItem LaunchProfile(string version) => LaunchProfileSelector.Items.OfType<ComboBoxItem>().First(i => i.Tag is LauncherProfile p && p.MinecraftVersion == version);
            LaunchProfileSelector.SelectedItem = LaunchProfile("1.8.9");
            await ReloadModsInventoryAsync();
            for (int i = 0; i < 40 && (_modIconsBusy || _modRows.Count == 0); i++) await Task.Delay(250);
            await Shot("mods-installed-189", 800);
            Check("modIcons-189", _modRows.Count(r => r.Icon.Source != null) > 0,
                $"{_modRows.Count(r => r.Icon.Source != null)} of {_modRows.Count} rows show an icon: " + string.Join(", ", _modRows.Select(r => $"{r.Entry.DisplayName}={(r.Icon.Source != null ? "icon" : "placeholder")}")));
            LaunchProfileSelector.SelectedItem = LaunchProfile("26.3");

            // Auto-save: a checkbox reaches settings.json without a Save button.
            ThemeSelector.SelectedItem = "DarkRed";
            NavigateTo("Settings");
            SettingsTab("General");
            bool keepOpen = KeepLauncherOpenCheckbox.IsChecked != true;
            KeepLauncherOpenCheckbox.IsChecked = keepOpen;
            await Task.Delay(900);
            Check("autoSave", Saved("KeepLauncherOpen")?.GetValue<bool>() == keepOpen && SettingsSaveStatus.Text == "All changes saved",
                $"KeepLauncherOpen={Saved("KeepLauncherOpen")}, status '{SettingsSaveStatus.Text}'");
            KeepLauncherOpenCheckbox.IsChecked = !keepOpen;

            // Validation: an invalid URL is shown under its field and never saved; the rest still saves.
            string modrinth = settings.ModrinthApiUrl;
            SettingsTab("Paths & services");
            ModrinthApiUrlBox.Text = "not a url";
            await Shot("settings-validation-default", 900);
            Check("validation", Saved("ModrinthApiUrl")?.GetValue<string>() == modrinth && DataValidationErrors.GetHasErrors(ModrinthApiUrlBox)
                && DataValidationErrors.GetHasErrors(ModrinthApiUrlBox_ModTab), $"saved ModrinthApiUrl={Saved("ModrinthApiUrl")}, status '{SettingsSaveStatus.Text}'");
            ModrinthApiUrlBox.Text = modrinth;
            FlushSettingsSave();

            // Animated maximize and restore, free and with the 16:9 lock.
            var before = PixelBounds();
            ToggleAnimatedMaximize();
            await Task.Delay(500);
            var maximized = PixelBounds();
            ToggleAnimatedMaximize();
            await Task.Delay(500);
            var restored = PixelBounds();
            AspectLockBtn_Click(null, new Avalonia.Interactivity.RoutedEventArgs());
            var locked = PixelBounds();
            var lockedLabel = AspectLockBtn.Content;
            ToggleAnimatedMaximize();
            await Task.Delay(500);
            var lockedMax = PixelBounds();
            await Shot("maximized-16x9", 100);
            var area = Screens.ScreenFromWindow(this)!.WorkingArea;
            ToggleAnimatedMaximize();
            await Task.Delay(500);
            var lockedRestored = PixelBounds();
            AspectLockBtn_Click(null, new Avalonia.Interactivity.RoutedEventArgs());
            bool Near(PixelRect a, PixelRect b) => Math.Abs(a.X - b.X) <= 2 && Math.Abs(a.Y - b.Y) <= 2 && Math.Abs(a.Width - b.Width) <= 2 && Math.Abs(a.Height - b.Height) <= 2;
            Check("maximize", Near(maximized, area) && Near(restored, before), $"before {before}, maximized {maximized}, working area {area}, restored {restored}");
            Check("maximize16x9", Math.Abs(lockedMax.Width / (double)lockedMax.Height - 16.0 / 9) < 0.01 && lockedMax.Width <= area.Width && lockedMax.Height <= area.Height
                && Near(lockedMax, WindowGeometry.Maximized(area, true, ASPECT_RATIO)) && Near(lockedRestored, locked),
                $"locked {locked}, maximized {lockedMax}, restored {lockedRestored}, label while locked '{lockedLabel}'");
            // Reset Size while maximized: default size, and the button offers Maximize again.
            ToggleAnimatedMaximize();
            await Task.Delay(500);
            ResetSizeBtn_Click(null, new Avalonia.Interactivity.RoutedEventArgs());
            await Task.Delay(100);
            Check("resetSize", (string?)MaximizeBtn.Content == "□" && Math.Abs(Width - DEFAULT_WIDTH) < 1 && Math.Abs(Height - DEFAULT_HEIGHT) < 1,
                $"maximize button '{MaximizeBtn.Content}', size {Width}x{Height}");

            // Controls: a modern and the 1.8.9 profile, each saved into its own options.txt (unknown lines kept).
            NavigateTo("Settings");
            SettingsTab("Controls");
            await Task.Delay(400);
            var picker = ControlsSettings.FindControl<ComboBox>("ProfilePicker")!;
            foreach (var version in new[] { "26.3", "1.8.9" })
            {
                var profile = _profileService.GetProfiles().First(p => p.MinecraftVersion == version);
                picker.SelectedItem = picker.Items.OfType<LauncherProfile>().First(p => p.Id == profile.Id);
                await Task.Delay(300);
                if (version == "1.8.9")
                {
                    // 1.8.9's own set: no simulation distance, GUI scale Auto/Small/Normal/Large.
                    ScrollTop(ControlsSettings.FindControl<Border>("KeysCard")!);
                    await Shot("settings-controls-189", 300);
                }
                ControlsSettings.FindControl<Slider>("FovSlider")!.Value = 90;
                // Jump onto W: the same key as Walk Forwards, which the list must show as a conflict.
                var rows = ControlsSettings.FindControl<UniformGrid>("KeyRows")!;
                var jump = rows.Children.OfType<Grid>().First(g => g.Children.OfType<TextBlock>().First().Text == "Jump").Children.OfType<Button>().First();
                jump.RaiseEvent(new Avalonia.Interactivity.RoutedEventArgs(Button.ClickEvent));
                ControlsSettings.RaiseEvent(new KeyEventArgs { RoutedEvent = KeyDownEvent, Key = Key.W, PhysicalKey = PhysicalKey.W });
                await ControlsSettings.FlushAsync();
                string tag = version == "1.8.9" ? "189" : "modern";
                ScrollTo(ControlsSettings.FindControl<Border>("KeysCard")!);
                await Shot($"settings-controls-conflict-{tag}", 300);
                var text = File.ReadAllText(Path.Combine(_pathService.GetProfileDirectory(profile), "options.txt"));
                string jumpKey = version == "1.8.9" ? "key_key.jump:17" : "key_key.jump:key.keyboard.w";
                var lines = text.Split('\n').Select(l => l.TrimEnd('\r')).ToList();
                Check($"controls-{tag}", lines.Contains("fov:0.5") && lines.Contains(jumpKey) && lines.Contains("lads-qa-unknown-line") && jump.Classes.Contains("conflict"),
                    $"fov:0.5 {lines.Contains("fov:0.5")}, {jumpKey} {lines.Contains(jumpKey)}, unknown line kept {lines.Contains("lads-qa-unknown-line")}, Jump shown as conflict {jump.Classes.Contains("conflict")}");
            }
            ThemeSelector.SelectedItem = "Halloween";
            FlushSettingsSave();
            await Shot("settings-controls-conflict-189-halloween", 500);
            ThemeSelector.SelectedItem = "DarkRed";
            FlushSettingsSave();

            // Nothing is written while the profile's game runs: a sleeping java process (system java) stands in for 26.2's game.
            var running = _profileService.GetProfiles().First(p => p.MinecraftVersion == "26.2");
            var runningDir = _pathService.GetProfileDirectory(running);
            var runningOptions = Path.Combine(runningDir, "options.txt");
            Directory.CreateDirectory(runningDir);
            var sleeper = Path.Combine(_pathService.BaseDirectory, "Sleep.java");
            File.WriteAllText(sleeper, "class Sleep { public static void main(String[] a) throws Exception { Thread.sleep(60000); } }");
            using var java = Process.Start(new ProcessStartInfo("java", $"\"{sleeper}\"") { UseShellExecute = false, CreateNoWindow = true })!;
            try
            {
                RunningGameMarker.Write(runningDir, java.Id, java.StartTime, Array.Empty<string>());
                string? optionsBefore = File.Exists(runningOptions) ? File.ReadAllText(runningOptions) : null;
                picker.SelectedItem = picker.Items.OfType<LauncherProfile>().First(p => p.Id == running.Id);
                await Task.Delay(300);
                bool cardsLocked = !ControlsSettings.FindControl<Border>("GameCard")!.IsEnabled && ControlsSettings.FindControl<TextBlock>("WarningNote")!.IsVisible;
                ControlsSettings.FindControl<Slider>("FovSlider")!.Value = 100;
                await ControlsSettings.FlushAsync();
                ScrollTop(ControlsSettings.FindControl<Border>("KeysCard")!);
                await Shot("settings-controls-running", 300);
                string? optionsAfter = File.Exists(runningOptions) ? File.ReadAllText(runningOptions) : null;
                var status = ControlsSettings.FindControl<TextBlock>("StatusLine")!.Text;
                Check("controls-running", cardsLocked && optionsBefore == optionsAfter && status?.StartsWith("Not saved") == true,
                    $"cards disabled with warning {cardsLocked}, options.txt unchanged {optionsBefore == optionsAfter}, status '{status}'");
            }
            finally
            {
                java.Kill();
                RunningGameMarker.Delete(runningDir, java.Id);
                File.Delete(sleeper);
            }
        }
        catch (Exception ex)
        {
            report["error"] = ex.ToString();
            exitCode = 1;
        }
        File.WriteAllText(Path.Combine(output, "chrome-report.json"), report.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
        Environment.ExitCode = exitCode;
        Close();
    }
}
