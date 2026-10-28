using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Animation;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Platform.Storage;
using Avalonia.Threading;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;

namespace TheLadsLauncher;

public partial class MainWindow
{
    private readonly DispatcherTimer _noticeTimer = new() { Interval = TimeSpan.FromSeconds(6) };
    private Border? _actionNotice;
    private bool _paletteOpen;
    private string? _productivityPreview;

    private void InitializeProductivity()
    {
        Classes.Set("motion", !settings.ReducedMotion);
        ProfileVersionFilter.ItemsSource = ProfileTools.FilterVersions;
        ProfileVersionFilter.SelectedIndex = 0;
        ProfileSearch.TextChanged += (_, _) => LoadProfilesUI();
        ProfileVersionFilter.SelectionChanged += (_, _) => LoadProfilesUI();
        ReducedMotionCheck.IsChecked = settings.ReducedMotion;
        ReducedMotionCheck.IsCheckedChanged += (_, _) =>
        {
            settings.ReducedMotion = ReducedMotionCheck.IsChecked == true;
            settings.Save();
            Classes.Set("motion", !settings.ReducedMotion);
            foreach (var page in ProductivityPages()) { page.Transitions = null; page.Opacity = 1; }
            if (_actionNotice != null) { _actionNotice.Transitions = null; _actionNotice.Opacity = 1; }
            if (settings.ReducedMotion) { _particleTimer?.Stop(); ParticleCanvas.IsVisible = false; }
            else
            {
                ParticleCanvas.IsVisible = true;
                if (settings.ShowParticles)
                {
                    if (!_meshControlAdded && _meshControl != null) { ParticleCanvas.Children.Add(_meshControl); _meshControlAdded = true; }
                    _particleTimer?.Start();
                }
            }
        };
        AddHandler(KeyDownEvent, (_, e) =>
        {
            if (e.Key == Key.K && e.KeyModifiers.HasFlag(KeyModifiers.Control)) { e.Handled = true; _ = OpenCommandPaletteAsync(); }
        }, Avalonia.Interactivity.RoutingStrategies.Tunnel);
        _noticeTimer.Tick += (_, _) => { _noticeTimer.Stop(); if (_actionNotice != null) _actionNotice.IsVisible = false; };
        Closed += (_, _) => _noticeTimer.Stop();
    }

    private Control[] ProductivityPages() => new Control[] { HomePage, WorldsPage, ServersPage, ModpacksPage, ProfilesPage, AccountsPage, SkinsPage, SettingsPage, ModsPage, FilesPage, GalleryPage, LogsPage };

    private void AnimateNavigation()
    {
        var page = ProductivityPages().FirstOrDefault(p => p.IsVisible);
        if (page == null) return;
        page.Transitions = null;
        if (settings.ReducedMotion) { page.Opacity = 1; return; }
        page.Opacity = .25;
        page.Transitions = new Transitions { new DoubleTransition { Property = OpacityProperty, Duration = TimeSpan.FromMilliseconds(160) } };
        Dispatcher.UIThread.Post(() => page.Opacity = 1, DispatcherPriority.Render);
    }

    private void ShowActionNotice(string text, bool error = false)
    {
        _noticeTimer.Stop();
        if (_actionNotice != null) RootGrid.Children.Remove(_actionNotice);
        var row = new DockPanel { LastChildFill = true };
        var close = new Button { Content = "×", Margin = new Thickness(12, 0, 0, 0), Padding = new Thickness(8, 4) };
        DockPanel.SetDock(close, Dock.Right);
        row.Children.Add(close);
        row.Children.Add(new TextBlock { Text = text, TextWrapping = TextWrapping.Wrap, VerticalAlignment = VerticalAlignment.Center });
        _actionNotice = new Border { Child = row, Background = Brush.Parse(error ? "#4A2528" : "#252B30"), BorderBrush = Brush.Parse(error ? "#D45B63" : "#555D66"), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(8), Padding = new Thickness(16), Margin = new Thickness(24), MaxWidth = 440, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Bottom };
        close.Click += (_, _) => { _noticeTimer.Stop(); if (_actionNotice != null) _actionNotice.IsVisible = false; };
        RootGrid.Children.Add(_actionNotice);
        if (!settings.ReducedMotion)
        {
            _actionNotice.Opacity = 0;
            _actionNotice.Transitions = new Transitions { new DoubleTransition { Property = OpacityProperty, Duration = TimeSpan.FromMilliseconds(160) } };
            var notice = _actionNotice;
            Dispatcher.UIThread.Post(() => notice.Opacity = 1, DispatcherPriority.Render);
        }
        if (!error) _noticeTimer.Start();
    }

    private Button ProfileActions(LauncherProfile profile)
    {
        var button = new Button { Content = "More ▾", Classes = { "action" }, Height = 34, FontSize = 12 };
        var favorite = new MenuItem { Header = profile.IsFavorite ? "★ Remove favorite" : "☆ Favorite profile" };
        favorite.Click += (_, _) => { profile.IsFavorite = !profile.IsFavorite; _profileService.SaveProfiles(); PopulateLaunchProfileSelector(); LoadProfilesUI(); };
        var duplicate = new MenuItem { Header = "Duplicate settings…" };
        duplicate.Click += async (_, _) => await ProfileActionAsync(async () =>
        {
            var preset = await Task.Run(() => ProfileTools.Capture(profile, _pathService.GetProfileDirectory(profile)));
            var copy = await Task.Run(() => ProfileTools.Import(preset with { Name = profile.Name[..Math.Min(95, profile.Name.Length)] + " copy" }, _pathService.ProfilesDirectory));
            _profileService.SaveProfile(copy);
            PopulateLaunchProfileSelector(); LoadProfilesUI();
            ShowActionNotice($"Created {copy.Name} with separate game and Lads settings. Worlds stay shared.");
        });
        var export = new MenuItem { Header = "Export settings preset…" };
        export.Click += async (_, _) => await ProfileActionAsync(async () =>
        {
            var file = await StorageProvider.SaveFilePickerAsync(new FilePickerSaveOptions { Title = "Export profile settings", SuggestedFileName = "lads-profile.json", DefaultExtension = "json", FileTypeChoices = new[] { new FilePickerFileType("Lads settings preset") { Patterns = new[] { "*.json" } } } });
            if (file?.TryGetLocalPath() is not string path) return;
            string text = await Task.Run(() => ProfileTools.Serialize(ProfileTools.Capture(profile, _pathService.GetProfileDirectory(profile))));
            await File.WriteAllTextAsync(path, text);
            ShowActionNotice("Settings preset exported. Includes game options, Lads modules and mod toggles; excludes worlds and accounts.");
        });
        var customize = new MenuItem { Header = "Customize icon…" };
        customize.Click += async (_, _) => await OpenProfileIconEditorAsync(profile);
        var check = new MenuItem { Header = "Check profile…" };
        check.Click += async (_, _) => await InspectProfileAsync(profile);
        var menu = new ContextMenu { ItemsSource = new[] { favorite, customize, duplicate, export, check } };
        button.ContextMenu = menu;
        button.Click += (_, _) => menu.Open(button);
        return button;
    }

    private async Task ProfileActionAsync(Func<Task> action)
    {
        if (_launching) { ShowActionNotice("Wait for the current launch to finish.", true); return; }
        try { await action(); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or System.Text.Json.JsonException or ArgumentException or InvalidOperationException or InvalidDataException)
        { ShowActionNotice(e.Message, true); }
    }

    private async void ImportProfilePreset_Click(object? sender, Avalonia.Interactivity.RoutedEventArgs e) => await ImportProfilePresetAsync();
    private async Task ImportProfilePresetAsync() => await ProfileActionAsync(async () =>
    {
        var files = await StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions { Title = "Import profile settings as a new profile", AllowMultiple = false, FileTypeFilter = new[] { new FilePickerFileType("Lads settings preset") { Patterns = new[] { "*.json" } } } });
        if (files.FirstOrDefault()?.TryGetLocalPath() is not string path) return;
        var profile = await Task.Run(() => ProfileTools.Import(ProfileTools.Read(path), _pathService.ProfilesDirectory));
        _profileService.SaveProfile(profile);
        PopulateLaunchProfileSelector(); LoadProfilesUI();
        ShowActionNotice($"Imported {profile.Name}. Its game settings are separate; worlds stay shared.");
    });

    private async Task InspectProfileAsync(LauncherProfile profile) => await ProfileActionAsync(async () =>
    {
        string java = profile.CustomJavaPath ?? (settings.AutoDetectJava
            ? await new JavaService(_pathService).DetectInstalledJavaAsync(profile.JavaMajorVersion) ?? _pathService.GetJavaExecutablePath(profile.JavaMajorVersion)
            : settings.JavaPath);
        var checks = await Task.Run(() => ProfileTools.Inspect(profile, _pathService, settings.MaxRamMb, java));
        var panel = new StackPanel { Spacing = 14, Margin = new Thickness(24) };
        panel.Children.Add(new TextBlock { Text = $"Check · {profile.Name}", FontSize = 22, TextWrapping = TextWrapping.Wrap });
        foreach (var check in checks) panel.Children.Add(new SelectableTextBlock { Text = $"{(check.Passed ? "✓" : "!")} {check.Name}\n{check.Detail}", TextWrapping = TextWrapping.Wrap, Foreground = Brush.Parse(check.Passed ? "#C6D9C9" : "#FFC07D") });
        panel.Children.Add(new TextBlock { Text = "Checks local prerequisites only. Network access and account sign-in are checked when you launch.", TextWrapping = TextWrapping.Wrap, Foreground = Brushes.Gray });
        var window = new Window { Title = "Profile check", Icon = Icon, Width = 600, Height = 510, MinWidth = 420, MinHeight = 320, WindowStartupLocation = WindowStartupLocation.CenterOwner, Content = new ScrollViewer { Content = panel } };
        await window.ShowDialog(this);
    });

    private async Task OpenCommandPaletteAsync()
    {
        if (_paletteOpen) return;
        _paletteOpen = true;
        try
        {
            var commands = new Dictionary<string, Func<Task>>();
            foreach (string page in new[] { "Home", "Profiles", "Worlds", "Mods", "Accounts", "Skins", "Settings", "Files", "Gallery", "Logs" })
                commands["Go to " + page] = async () =>
                {
                    if (page == "Profiles") LoadProfilesUI();
                    if (page == "Worlds") await WorldsPage.LoadAsync();
                    if (page == "Mods") ReloadModsInventory();
                    if (page == "Accounts") LoadAccounts();
                    if (page == "Skins") ShowSkins();
                    if (page == "Files") LoadFiles(settings.InstancePath);
                    if (page == "Gallery") await LoadGalleryAsync();
                    NavigateTo(page);
                };
            commands["Check active profile"] = () => InspectProfileAsync(_profileService.GetActiveProfile());
            commands["Import profile settings preset"] = ImportProfilePresetAsync;
            commands["Toggle reduced motion"] = () => { ReducedMotionCheck.IsChecked = !settings.ReducedMotion; ShowActionNotice(settings.ReducedMotion ? "Reduced motion enabled." : "Animations enabled."); return Task.CompletedTask; };
            var search = new TextBox { PlaceholderText = "Type a page or action…", Margin = new Thickness(0, 0, 0, 12) };
            var list = new ListBox { ItemsSource = commands.Keys.ToArray(), SelectedIndex = 0 };
            var grid = new Grid { RowDefinitions = new RowDefinitions("Auto,*"), Margin = new Thickness(20) };
            grid.Children.Add(search); grid.Children.Add(list); Grid.SetRow(list, 1);
            var dialog = new Window { Title = "Command palette · Ctrl+K", Icon = Icon, Width = 520, Height = 420, WindowStartupLocation = WindowStartupLocation.CenterOwner, Content = grid };
            search.TextChanged += (_, _) => { list.ItemsSource = commands.Keys.Where(k => k.Contains(search.Text ?? "", StringComparison.OrdinalIgnoreCase)).ToArray(); list.SelectedIndex = 0; };
            dialog.AddHandler(KeyDownEvent, (_, e) =>
            {
                if (e.Key == Key.Escape) { dialog.Close((string?)null); e.Handled = true; }
                if (e.Key == Key.Enter && list.SelectedItem is string item) { dialog.Close(item); e.Handled = true; }
                if (e.Key is Key.Down or Key.Up) { list.SelectedIndex = Math.Clamp(list.SelectedIndex + (e.Key == Key.Down ? 1 : -1), 0, Math.Max(0, list.ItemCount - 1)); e.Handled = true; }
            }, Avalonia.Interactivity.RoutingStrategies.Tunnel);
            list.DoubleTapped += (_, _) => { if (list.SelectedItem is string item) dialog.Close(item); };
            dialog.Opened += (_, _) => search.Focus();
            if (_productivityPreview != null) dialog.Opened += async (_, _) =>
            {
                search.Text = "reduced motion";
                await Task.Delay(250);
                SaveProductivityDialog(dialog, Path.Combine(_productivityPreview, "command-palette.png"));
                search.RaiseEvent(new KeyEventArgs { RoutedEvent = KeyDownEvent, Key = Key.Enter });
            };
            string? chosen = await dialog.ShowDialog<string?>(this);
            if (chosen != null) await commands[chosen]();
        }
        finally { _paletteOpen = false; }
    }

    private static void SaveProductivityDialog(Window window, string path)
    {
        using var bitmap = new Avalonia.Media.Imaging.RenderTargetBitmap(new PixelSize((int)window.Bounds.Width, (int)window.Bounds.Height), new Vector(96, 96));
        bitmap.Render(window); bitmap.Save(path);
    }

    // Program.Main validates sandbox roots before this opt-in UI verification can run.
    private async Task RunProductivityPreviewAsync(string output)
    {
        Directory.CreateDirectory(output);
        try
        {
            Width = 960; Height = 600;
            settings.ReducedMotion = false; ReducedMotionCheck.IsChecked = false;
            NavigateTo("Profiles"); LoadProfilesUI();
            await Task.Delay(350);
            SaveWindowScreenshot(Path.Combine(output, "profiles-minimum.png"));
            ProfileSearch.Text = "no-profile-can-match-this-130";
            await Task.Delay(100);
            if (ProfilesListContainer.Children.Count != 1 || ProfilesListContainer.Children[0] is not TextBlock) throw new InvalidOperationException("Profile search did not filter cards.");
            ProfileSearch.Text = ""; ProfileVersionFilter.SelectedItem = "26.3";
            await Task.Delay(100);
            if (ProfilesListContainer.Children.Count != ProfileTools.Filter(_profileService.GetProfiles(), "", "26.3").Count()) throw new InvalidOperationException("Version filter did not update cards.");
            ProfileVersionFilter.SelectedIndex = 0;
            var profile = _profileService.GetActiveProfile();
            var more = ProfileActions(profile);
            var items = more.ContextMenu!.Items.Cast<MenuItem>().ToArray();
            bool favorite = profile.IsFavorite;
            items[0].RaiseEvent(new Avalonia.Interactivity.RoutedEventArgs(MenuItem.ClickEvent));
            if (profile.IsFavorite == favorite) throw new InvalidOperationException("Favorite action did not update profile.");
            int before = _profileService.GetProfiles().Count;
            items[1].RaiseEvent(new Avalonia.Interactivity.RoutedEventArgs(MenuItem.ClickEvent));
            for (int i = 0; i < 100 && _profileService.GetProfiles().Count == before; i++) await Task.Delay(50);
            if (_profileService.GetProfiles().Count != before + 1) throw new InvalidOperationException("Duplicate settings action did not create a profile.");
            await Task.Delay(250);
            SaveWindowScreenshot(Path.Combine(output, "profile-action-notice.png"));
            _productivityPreview = output;
            await OpenCommandPaletteAsync();
            _productivityPreview = null;
            if (!settings.ReducedMotion || Classes.Contains("motion")) throw new InvalidOperationException("Command palette did not toggle reduced motion.");
            NavigateTo("Profiles");
            if (ProfilesPage.Transitions != null || ProfilesPage.Opacity != 1) throw new InvalidOperationException("Reduced motion still animates navigation.");
            ShowActionNotice("Example: a profile check needs attention. This notice stays until dismissed.", true);
            await Task.Delay(6500);
            if (_actionNotice?.IsVisible != true) throw new InvalidOperationException("Error notice disappeared automatically.");
            SaveWindowScreenshot(Path.Combine(output, "persistent-notice.png"));
            var checkTask = InspectProfileAsync(profile);
            for (int i = 0; i < 100 && !OwnedWindows.Any(); i++) await Task.Delay(50);
            var checks = OwnedWindows.First(); await Task.Delay(300);
            SaveProductivityDialog(checks, Path.Combine(output, "profile-check.png")); checks.Close(); await checkTask;
            await File.WriteAllTextAsync(Path.Combine(output, "result.txt"), "PASS: minimum-size profiles, search, version filter, favorite, settings duplication, keyboard command palette, reduced motion, persistent error notice, profile diagnostics.");
        }
        catch (Exception e) { await File.WriteAllTextAsync(Path.Combine(output, "result.txt"), "FAIL: " + e); }
        finally { _productivityPreview = null; Close(); }
    }

    private async Task OpenProfileIconEditorAsync(LauncherProfile profile) => await ProfileActionAsync(async () =>
    {
        var window = new Window
        {
            Title = $"Customize Icon · {profile.Name}",
            Width = 460,
            SizeToContent = SizeToContent.Height,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Background = new SolidColorBrush(Color.Parse("#17181B")),
            CanResize = false
        };

        var panel = new StackPanel { Margin = new Thickness(24), Spacing = 12 };
        panel.Children.Add(new TextBlock
        {
            Text = "Customize Profile Icon",
            Foreground = Brushes.White,
            FontSize = 18,
            FontWeight = FontWeight.Bold
        });

        Color initialBg = Color.TryParse(profile.IconColor, out var cInit) ? cInit
            : (Color.TryParse("#" + profile.IconColor, out var cInit2) ? cInit2 : Color.Parse("#E0525E"));
        var previewBorder = new Border
        {
            Width = 60, Height = 60,
            CornerRadius = new CornerRadius(30),
            Background = new SolidColorBrush(initialBg),
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            Margin = new Thickness(0, 6, 0, 6)
        };
        var previewGlyph = new TextBlock
        {
            Text = ProfileIconService.GetGlyph(profile.IconKey),
            FontSize = 26,
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center
        };
        previewBorder.Child = previewGlyph;
        panel.Children.Add(previewBorder);

        panel.Children.Add(new TextBlock { Text = "Symbol", Foreground = new SolidColorBrush(Color.Parse("#CCCCCC")), FontSize = 12 });
        var symbolBox = new ComboBox { HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Stretch, Height = 36 };
        foreach (var sym in ProfileIconService.BuiltInSymbols) symbolBox.Items.Add(sym);
        symbolBox.SelectedItem = !string.IsNullOrEmpty(profile.IconKey) ? profile.IconKey : "default";
        panel.Children.Add(symbolBox);

        panel.Children.Add(new TextBlock { Text = "Color Preset", Foreground = new SolidColorBrush(Color.Parse("#CCCCCC")), FontSize = 12 });
        var presetBox = new ComboBox { HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Stretch, Height = 36 };
        foreach (var grad in ProfileIconService.PresetGradients) presetBox.Items.Add(grad.Name);
        panel.Children.Add(presetBox);

        panel.Children.Add(new TextBlock { Text = "Primary Color (Hex)", Foreground = new SolidColorBrush(Color.Parse("#CCCCCC")), FontSize = 12 });
        var primaryBox = new TextBox { Text = profile.IconColor ?? "#E0525E", Height = 36 };
        panel.Children.Add(primaryBox);

        panel.Children.Add(new TextBlock { Text = "Secondary Color (Hex)", Foreground = new SolidColorBrush(Color.Parse("#CCCCCC")), FontSize = 12 });
        var secondaryBox = new TextBox { Text = profile.IconSecondaryColor ?? "#8B0000", Height = 36 };
        panel.Children.Add(secondaryBox);

        static bool TryNormalizeColor(string? input, out string normalized, out Color color)
        {
            color = default;
            if (ProfileIconService.TryNormalizeHexColor(input, out normalized) && Color.TryParse(normalized, out color))
                return true;
            return false;
        }

        void UpdatePreview()
        {
            string sym = symbolBox.SelectedItem?.ToString() ?? "default";
            previewGlyph.Text = ProfileIconService.GetGlyph(sym);
            if (TryNormalizeColor(primaryBox.Text, out _, out var c))
            {
                previewBorder.Background = new SolidColorBrush(c);
            }
        }

        symbolBox.SelectionChanged += (_, _) => UpdatePreview();
        primaryBox.TextChanged += (_, _) => UpdatePreview();
        secondaryBox.TextChanged += (_, _) => UpdatePreview();
        presetBox.SelectionChanged += (_, _) =>
        {
            if (presetBox.SelectedItem is string name)
            {
                var match = ProfileIconService.PresetGradients.FirstOrDefault(g => g.Name == name);
                if (match != null)
                {
                    primaryBox.Text = match.StartColor;
                    secondaryBox.Text = match.EndColor;
                    UpdatePreview();
                }
            }
        };

        var randomBtn = new Button
        {
            Content = "🎲 Randomize Icon & Colors",
            Classes = { "action" },
            Height = 34,
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Stretch,
            HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center
        };
        randomBtn.Click += (_, _) =>
        {
            var temp = new LauncherProfile();
            ProfileIconService.Randomize(temp);
            symbolBox.SelectedItem = temp.IconKey;
            primaryBox.Text = temp.IconColor;
            secondaryBox.Text = temp.IconSecondaryColor;
            UpdatePreview();
        };
        panel.Children.Add(randomBtn);

        var errorBlock = new TextBlock
        {
            Foreground = Brush.Parse("#FF8A8A"),
            FontSize = 11,
            IsVisible = false,
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 4, 0, 0)
        };
        panel.Children.Add(errorBlock);

        var btnGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,8,*"), Margin = new Thickness(0, 8, 0, 0) };
        var saveBtn = new Button
        {
            Content = "Save Icon",
            Classes = { "launch" },
            Height = 36,
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Stretch,
            HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            FontWeight = FontWeight.Bold
        };
        saveBtn.Click += (_, _) =>
        {
            if (!TryNormalizeColor(primaryBox.Text, out string primaryNorm, out _))
            {
                errorBlock.Text = "Please enter a valid hex color for Primary (e.g. #E0525E or E0525E).";
                errorBlock.IsVisible = true;
                return;
            }
            string? secondaryNorm = null;
            if (!string.IsNullOrWhiteSpace(secondaryBox.Text))
            {
                if (!TryNormalizeColor(secondaryBox.Text, out secondaryNorm, out _))
                {
                    errorBlock.Text = "Please enter a valid hex color for Secondary (e.g. #8B0000 or 8B0000).";
                    errorBlock.IsVisible = true;
                    return;
                }
            }

            string sym = symbolBox.SelectedItem?.ToString() ?? "default";
            ProfileIconService.SetSymbol(profile, sym, primaryNorm, secondaryNorm);
            _profileService.SaveProfiles();
            PopulateLaunchProfileSelector();
            LoadProfilesUI();
            window.Close();
            ShowActionNotice($"Updated profile icon for '{profile.Name}'.");
        };
        btnGrid.Children.Add(saveBtn);
        Grid.SetColumn(saveBtn, 0);

        var cancelBtn = new Button
        {
            Content = "Cancel",
            Classes = { "action" },
            Height = 36,
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Stretch,
            HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center
        };
        cancelBtn.Click += (_, _) => window.Close();
        btnGrid.Children.Add(cancelBtn);
        Grid.SetColumn(cancelBtn, 2);

        panel.Children.Add(btnGrid);
        window.Content = panel;
        await window.ShowDialog(this);
    });

}
