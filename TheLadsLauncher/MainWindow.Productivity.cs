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
        ProfileVersionFilter.ItemsSource = new[] { "All versions", "26.3", "26.2", "1.21.11", "1.21.1", "1.8.9" };
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
        var check = new MenuItem { Header = "Check profile…" };
        check.Click += async (_, _) => await InspectProfileAsync(profile);
        var menu = new ContextMenu { ItemsSource = new[] { favorite, duplicate, export, check } };
        button.ContextMenu = menu;
        button.Click += (_, _) => menu.Open(button);
        return button;
    }

    private async Task ProfileActionAsync(Func<Task> action)
    {
        if (_launching) { ShowActionNotice("Wait for the current launch to finish.", true); return; }
        try { await action(); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or System.Text.Json.JsonException or ArgumentException or InvalidOperationException)
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
}
