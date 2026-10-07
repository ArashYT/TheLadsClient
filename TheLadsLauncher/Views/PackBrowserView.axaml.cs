using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Threading;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

/// <summary>
/// Shader packs or data packs (<see cref="Kind"/>) for one target: a Lads version or a modpack instance. Data packs also need a
/// world of that target. Lists what is installed (with Remove, to the Recycle Bin) and browses Modrinth or CurseForge for the
/// target's Minecraft version and loader.
/// </summary>
public partial class PackBrowserView : UserControl
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(60) };
    private readonly DispatcherTimer _searchTimer = new() { Interval = TimeSpan.FromMilliseconds(450) };
    private int _searchGeneration;
    private bool _updating;

    /// <summary>Shader or DataPack; set in XAML.</summary>
    public ContentKind Kind { get; set; } = ContentKind.Shader;

    public PackBrowserView()
    {
        InitializeComponent();
        _searchTimer.Tick += async (_, _) => { _searchTimer.Stop(); await SearchAsync(); };
    }

    private string Noun => Kind == ContentKind.Shader ? "shader packs" : "data packs";
    private ContentTarget? Target => TargetBox?.SelectedItem as ContentTarget; // null while InitializeComponent runs
    private string Source => (SourceBox.SelectedItem as ComboBoxItem)?.Content as string ?? "Modrinth";

    /// <summary>The folder the packs go to: the target's shaderpacks, or the chosen world's datapacks (null without a world).</summary>
    public string? Folder => Target is not { } target ? null
        : Kind == ContentKind.Shader ? target.ShaderPacks
        : WorldBox.SelectedItem is string world ? Path.Combine(target.Saves, world, "datapacks") : null;

    // The tab hosts this view only while it is selected: every visit re-reads targets, worlds and the installed list.
    protected override async void OnAttachedToVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnAttachedToVisualTree(e);
        await LoadAsync();
    }

    /// <summary>Re-reads the targets (keeping the chosen one, else the active profile), worlds, installed packs and results.</summary>
    public async Task LoadAsync(string? selectTargetId = null)
    {
        SearchBox.PlaceholderText = $"Search {Noun} — searches as you type";
        List<ContentTarget> targets;
        try
        {
            targets = PackContentService.LoadTargets(ProfileService.Instance.GetProfiles(), PathService.Instance, SharedContentService.Instance);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidOperationException)
        {
            Status.Text = $"Could not read the profiles and modpacks: {e.Message}";
            return;
        }
        if (Kind == ContentKind.DataPack) targets = targets.Where(t => PackContentService.SupportsDataPacks(t.MinecraftVersion)).ToList();
        string? keep = selectTargetId ?? Target?.Id ?? ProfileService.Instance.GetActiveProfile().Id;
        _updating = true;
        TargetBox.ItemsSource = targets;
        TargetBox.SelectedItem = targets.FirstOrDefault(t => t.Id == keep) ?? targets.FirstOrDefault();
        if (Kind == ContentKind.Shader)
            CategoryBox.ItemsSource = new[] { "All categories", "Realistic", "Fantasy", "Performance", "Vibrant", "Cel-shaded", "Vanilla-like" };
        else if (Kind == ContentKind.DataPack)
            CategoryBox.ItemsSource = new[] { "All categories", "Adventure", "Magic", "Technology", "Utility", "Worldgen", "Minigame" };
        else
            CategoryBox.ItemsSource = new[] { "All categories", "16x", "32x", "64x", "128x", "Faithful", "Realistic", "Medieval", "Vanilla-like" };
        CategoryBox.SelectedIndex = 0;
        _updating = false;
        await ShowTargetAsync();
    }

    private async Task ShowTargetAsync()
    {
        bool dataPacks = Kind == ContentKind.DataPack;
        WorldLabel.IsVisible = WorldBox.IsVisible = dataPacks;
        if (dataPacks && Target is { } target)
        {
            var keep = WorldBox.SelectedItem as string;
            var worlds = PackContentService.Worlds(target.Saves);
            _updating = true;
            WorldBox.ItemsSource = worlds;
            WorldBox.SelectedItem = worlds.Contains(keep ?? "") ? keep : worlds.FirstOrDefault();
            _updating = false;
        }
        RenderInstalled();
        await SearchAsync();
    }

    private void RenderInstalled()
    {
        InstalledList.Children.Clear();
        InstalledHeader.Text = $"Installed {Noun}";
        if (Target == null)
        {
            InstalledList.Children.Add(Note(Kind == ContentKind.DataPack ? "Data packs need Minecraft 1.13 or newer: add a 1.21 or 26.x profile or modpack." : "No profiles yet."));
            return;
        }
        if (Folder is not { } folder)
        {
            InstalledList.Children.Add(Note($"There are no worlds in '{Target.Saves}' yet. Create one in game, then press Refresh."));
            return;
        }
        var packs = PackContentService.Installed(folder);
        InstalledHeader.Text = $"Installed {Noun} ({packs.Count})";
        if (packs.Count == 0) InstalledList.Children.Add(Note($"None yet in '{folder}'. Install one from the list on the right."));
        foreach (var path in packs)
        {
            var text = new StackPanel { Spacing = 2 };
            var name = new TextBlock { Text = Path.GetFileName(path), Foreground = Brushes.White, FontSize = 13, FontWeight = FontWeight.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis }.Untranslated();
            ToolTip.SetTip(name, path);
            text.Children.Add(name);
            long bytes = Directory.Exists(path) ? -1 : new FileInfo(path).Length;
            text.Children.Add(new TextBlock { Text = bytes < 0 ? "Folder" : bytes >= 1 << 20 ? $"{bytes / 1048576.0:0.0} MB" : $"{Math.Max(1, bytes / 1024)} KB", Foreground = new SolidColorBrush(Color.Parse("#868994")), FontSize = 11 });
            var remove = new Button { Content = "Remove", Height = 28, Padding = new Thickness(10, 0), VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center, Classes = { "danger" } };
            ToolTip.SetTip(remove, "Move it to the Recycle Bin");
            remove.Click += async (_, _) => await RemoveAsync(path);
            InstalledList.Children.Add(Card(text, remove));
        }
    }

    private async Task RemoveAsync(string path)
    {
        try
        {
            await Task.Run(() => SafeFileOps.DeleteToRecycleBin(path));
            Status.Text = $"Moved {Path.GetFileName(path)} to the Recycle Bin.";
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidOperationException)
        {
            Status.Text = $"Could not remove {Path.GetFileName(path)}: {e.Message} (close Minecraft if it is using it).";
        }
        RenderInstalled();
    }

    private ContentCatalog Catalog()
    {
        var settings = LauncherSettings.Load();
        return new ContentCatalog(Http, settings.ModrinthApiUrl, settings.CurseForgeApiUrl, settings.CurseForgeApiKey);
    }

    /// <summary>Searches the chosen source for the target's version and loader; failures show their reason in the status line.</summary>
    public async Task SearchAsync()
    {
        int generation = ++_searchGeneration;
        ResultsList.Children.Clear();
        if (Target is not { } target) { Status.Text = ""; return; }
        Status.Text = $"Searching {Source} for {Noun} for Minecraft {target.MinecraftVersion}…";
        try
        {
            string? sort = (SortBox?.SelectedItem as ComboBoxItem)?.Content as string;
            string? cat = CategoryBox?.SelectedItem as string;
            if (cat == "All categories") cat = null;
            var results = await Catalog().SearchAsync(Source, Kind, SearchBox.Text ?? "", target.MinecraftVersion, target.Loader, cat, sort);
            if (generation != _searchGeneration) return; // a newer search owns the list
            foreach (var item in results) ResultsList.Children.Add(ResultRow(item));
            string loader = Kind == ContentKind.Shader ? $" ({(ContentCatalog.ShaderLoader(target.Loader) == "iris" ? "Iris" : "OptiFine")})" : "";
            Status.Text = results.Count == 0 ? $"No {Noun} on {Source} match for Minecraft {target.MinecraftVersion}{loader}."
                : $"{results.Count} {Noun} on {Source} for Minecraft {target.MinecraftVersion}{loader}.";
        }
        catch (ContentSourceException e)
        {
            if (generation == _searchGeneration) Status.Text = e.Message;
        }
    }

    private Control ResultRow(ModSearchItem item)
    {
        var icon = new Image { Width = 40, Height = 40 };
        var text = new StackPanel { Spacing = 2, Margin = new Thickness(12, 0, 0, 0) };
        text.Children.Add(new TextBlock { Text = item.Name, Foreground = Brushes.White, FontSize = 14, FontWeight = FontWeight.Bold, TextWrapping = TextWrapping.Wrap }.Untranslated());
        text.Children.Add(new TextBlock { Text = item.Summary, Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")), FontSize = 12, TextWrapping = TextWrapping.Wrap, MaxLines = 2, TextTrimming = TextTrimming.CharacterEllipsis });
        text.Children.Add(new TextBlock { Text = $"⬇ {Downloads(item.DownloadCount)}  ·  {item.Provider}{(item.Author.Length > 0 ? "  ·  by " + item.Author : "")}", Foreground = new SolidColorBrush(Color.Parse("#868994")), FontSize = 10 }.Untranslated());
        var body = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*") };
        body.Children.Add(new Border { Width = 40, Height = 40, CornerRadius = new CornerRadius(4), ClipToBounds = true, Background = new SolidColorBrush(Color.Parse("#25262A")), Child = icon, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Top });
        Grid.SetColumn(text, 1);
        body.Children.Add(text);
        var install = new Button { Content = "Install", Height = 30, Padding = new Thickness(12, 0), VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center, Classes = { "launch" } };
        install.Click += async (_, _) => await InstallAsync(item, install);
        if (item.IconUrl.Length > 0) _ = LoadIconAsync(icon, item.IconUrl);
        return Card(body, install);
    }

    private async Task InstallAsync(ModSearchItem item, Button button)
    {
        if (Target is not { } target || Folder is not { } folder)
        {
            Status.Text = "Choose a world first (create one in game if the list is empty).";
            return;
        }
        button.IsEnabled = false;
        button.Content = "Installing…";
        try
        {
            var catalog = Catalog();
            var file = await catalog.LatestFileAsync(item, Kind, target.MinecraftVersion, target.Loader)
                ?? throw new ContentSourceException($"{item.Name} has no {(Kind == ContentKind.Shader ? "shader pack" : "data pack")} file for Minecraft {target.MinecraftVersion}.");
            var path = await catalog.InstallAsync(file, folder, SharedContentService.Instance);
            button.Content = "Installed";
            Status.Text = $"Installed {item.Name} {file.VersionName} as '{Path.GetFileName(path)}' in '{folder}'.";
            RenderInstalled();
        }
        catch (Exception e) when (e is ContentSourceException or IOException or UnauthorizedAccessException or HttpRequestException or ArgumentException)
        {
            button.IsEnabled = true;
            button.Content = "Install";
            Status.Text = $"{item.Name}: {e.Message}";
        }
    }

    private static async Task LoadIconAsync(Image image, string url)
    {
        try
        {
            var bytes = await Http.GetByteArrayAsync(url);
            using var stream = new MemoryStream(bytes);
            image.Source = Bitmap.DecodeToWidth(stream, 80);
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or ArgumentException or InvalidOperationException or NotSupportedException)
        {
            // No icon: the row still works.
        }
    }

    private static Border Card(Control body, Control action)
    {
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        grid.Children.Add(body);
        Grid.SetColumn(action, 1);
        action.Margin = new Thickness(12, 0, 0, 0);
        grid.Children.Add(action);
        return new Border { Child = grid, Background = new SolidColorBrush(Color.Parse("#1D1E22")), BorderBrush = new SolidColorBrush(Color.Parse("#303137")), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(4), Padding = new Thickness(12, 10) };
    }

    private static TextBlock Note(string text) => new() { Text = text, Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")), FontSize = 12, TextWrapping = TextWrapping.Wrap };

    private static string Downloads(long count) => count >= 1_000_000 ? $"{count / 1_000_000.0:F1}M" : count >= 1_000 ? $"{count / 1_000.0:F1}K" : count.ToString();

    private async void Target_Changed(object? sender, SelectionChangedEventArgs e) { if (!_updating) await ShowTargetAsync(); }
    private void World_Changed(object? sender, SelectionChangedEventArgs e) { if (!_updating) RenderInstalled(); }
    private async void Source_Changed(object? sender, SelectionChangedEventArgs e) { if (!_updating && Target != null) await SearchAsync(); }
    private async void Filter_Changed(object? sender, SelectionChangedEventArgs e) { if (!_updating && Target != null) await SearchAsync(); }
    private void Search_TextChanged(object? sender, TextChangedEventArgs e) { _searchTimer.Stop(); _searchTimer.Start(); }
    private async void Refresh_Click(object? sender, RoutedEventArgs e) => await LoadAsync();

    private void OpenFolder_Click(object? sender, RoutedEventArgs e)
    {
        if (Folder is not { } folder) { Status.Text = "Choose a world first."; return; }
        try
        {
            Directory.CreateDirectory(folder);
            Process.Start(new ProcessStartInfo { FileName = folder, UseShellExecute = true });
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.ComponentModel.Win32Exception)
        {
            Status.Text = $"Could not open '{folder}': {ex.Message}";
        }
    }
}
