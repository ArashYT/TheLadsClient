using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Automation;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Platform.Storage;
using Avalonia.Threading;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

/// <summary>The Modpacks tab: My Modpacks (cards), Browse (Modrinth) and one instance's page. Instances start from here only.</summary>
public partial class ModpacksView : UserControl
{
    private static readonly HttpClient Http = ModrinthModpacks.CreateClient();
    private static readonly string[] LoaderNames = { "Fabric", "Quilt", "Forge", "NeoForge", "Vanilla" }; // order of Modpacks.Loaders
    private static readonly string[] GameVersions = { "26.3", "26.2", "1.21.11", "1.21.1", "1.21", "1.20.6", "1.20.4", "1.20.1", "1.19.2", "1.18.2", "1.16.5", "1.12.2", "1.8.9" };
    private static readonly (string Label, string Index)[] BrowseSorts =
        { ("Most downloads", "downloads"), ("Relevance", "relevance"), ("Most follows", "follows"), ("Newest", "newest"), ("Recently updated", "updated") };
    private static readonly Dictionary<string, Bitmap?> RemoteIcons = new();

    private readonly Dictionary<string, Process> _running = new();
    private readonly Dictionary<string, CancellationTokenSource> _launching = new();
    private readonly HashSet<string> _busy = new(); // instance ids with an update running
    private List<ModpackInstance> _instances = new();
    private List<ModpackVersion> _versions = new();
    private ModpackInstance? _selected;
    private CancellationTokenSource? _search;
    private int _browseOffset;
    private bool _browsed, _importing;
    private readonly bool _ready;

    private static string DataDirectory => PathService.Instance.BaseDirectory;
    private MainWindow? Host => TopLevel.GetTopLevel(this) as MainWindow;

    public ModpacksView()
    {
        InitializeComponent();
        MineSort.ItemsSource = new[] { "Recently played", "Recently created", "Name", "Minecraft version" };
        MineLoader.ItemsSource = new[] { "All loaders" }.Concat(LoaderNames).ToArray();
        BrowseVersion.ItemsSource = new[] { "All versions" }.Concat(GameVersions).ToArray();
        BrowseLoader.ItemsSource = new[] { "All loaders" }.Concat(LoaderNames.Take(4)).ToArray();
        BrowseSort.ItemsSource = BrowseSorts.Select(s => s.Label).ToArray();
        CreateLoader.ItemsSource = LoaderNames;
        MineSort.SelectedIndex = MineLoader.SelectedIndex = BrowseVersion.SelectedIndex = BrowseLoader.SelectedIndex = BrowseSort.SelectedIndex = CreateLoader.SelectedIndex = 0;
        _ready = true;
    }

    public async Task LoadAsync()
    {
        try { _instances = await Task.Run(() => Modpacks.List(DataDirectory)); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { Status.Text = "Could not read the modpacks folder: " + e.Message; }
        RenderCards();
    }

    // ── My Modpacks ─────────────────────────────────────────────────────────

    private void RenderCards()
    {
        if (!_ready) return;
        string query = MineSearch.Text ?? "";
        string? loader = MineLoader.SelectedIndex > 0 ? Modpacks.Loaders[MineLoader.SelectedIndex - 1] : null;
        var list = _instances.Where(i => (loader == null || i.Loader == loader) && i.Name.Contains(query, StringComparison.OrdinalIgnoreCase));
        list = MineSort.SelectedIndex switch
        {
            0 => list.OrderByDescending(i => i.LastPlayedUtc ?? DateTime.MinValue).ThenByDescending(i => i.CreatedUtc),
            1 => list.OrderByDescending(i => i.CreatedUtc),
            2 => list.OrderBy(i => i.Name, StringComparer.CurrentCultureIgnoreCase),
            _ => list.OrderByDescending(i => Version.TryParse(i.McVersion, out var v) ? v : new Version(0, 0))
        };
        Cards.Children.Clear();
        foreach (var instance in list) Cards.Children.Add(Card(instance));
        if (MineTab.IsSelected)
            Status.Text = _instances.Count == 0 ? "No modpacks yet. Find one in Browse, import a .mrpack file, or create your own."
                : Cards.Children.Count == 0 ? "No modpacks match the search and filter." : "";
    }

    private Control Card(ModpackInstance instance)
    {
        var top = new Grid { Width = 190, Height = 190 };
        top.Children.Add(new Border { CornerRadius = new CornerRadius(8, 8, 0, 0), ClipToBounds = true, Child = Art(instance, 190) });
        top.Children.Add(new Border
        {
            Background = new SolidColorBrush(Color.Parse("#D9101114")), CornerRadius = new CornerRadius(4), Padding = new Thickness(7, 3),
            Margin = new Thickness(8), HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top,
            Child = new TextBlock { Text = instance.McVersion, FontSize = 11, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White }
        });
        var icon = LoaderIcon(instance.Loader);
        icon.Margin = new Thickness(8);
        icon.HorizontalAlignment = HorizontalAlignment.Right;
        icon.VerticalAlignment = VerticalAlignment.Bottom;
        top.Children.Add(icon);
        if (_running.ContainsKey(instance.Id))
            top.Children.Add(new Border
            {
                Background = new SolidColorBrush(Color.Parse("#E02E7D32")), CornerRadius = new CornerRadius(4), Padding = new Thickness(7, 3),
                Margin = new Thickness(8), HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top,
                Child = new TextBlock { Text = "Running", FontSize = 11, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White }
            });
        var text = new StackPanel { Margin = new Thickness(12, 9, 12, 12), Spacing = 2 };
        text.Children.Add(new TextBlock { Text = instance.Name, FontSize = 14, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White, TextTrimming = TextTrimming.CharacterEllipsis });
        text.Children.Add(new TextBlock { Text = Byline(instance), FontSize = 11, Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")), TextTrimming = TextTrimming.CharacterEllipsis });
        var body = new StackPanel();
        body.Children.Add(top);
        body.Children.Add(text);
        var card = new Button { Classes = { "card" }, Width = 192, Content = body };
        AutomationProperties.SetName(card, instance.Name);
        card.Click += (_, _) => OpenManage(instance);
        return card;
    }

    private static string Byline(ModpackInstance i) =>
        i.Author is { Length: > 0 } author ? "By " + author : i.Source != null ? "From Modrinth" : i.PackVersion != null ? "Imported .mrpack" : "My creation";

    private static string LoaderName(string loader) => LoaderNames[Math.Max(0, Array.IndexOf(Modpacks.Loaders, loader))];

    /// <summary>The pack's icon, or its initial on a colour picked from the name.</summary>
    private static Control Art(ModpackInstance instance, double size)
    {
        try
        {
            if (instance.IconFile is { } file && File.Exists(file))
                return new Image { Source = new Bitmap(new MemoryStream(File.ReadAllBytes(file))), Stretch = Stretch.UniformToFill, Width = size, Height = size };
        }
        catch (Exception e) when (e is IOException or InvalidDataException or ArgumentException or NotSupportedException or UnauthorizedAccessException) { }
        int hue = Math.Abs(instance.Name.Aggregate(17, (h, c) => h * 31 + c)) % 360;
        return new Border
        {
            Width = size, Height = size,
            Background = new LinearGradientBrush
            {
                StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative), EndPoint = new RelativePoint(1, 1, RelativeUnit.Relative),
                GradientStops = { new GradientStop(HsvColor.ToRgb(hue, .55, .55), 0), new GradientStop(HsvColor.ToRgb((hue + 40) % 360, .6, .25), 1) }
            },
            Child = new TextBlock
            {
                Text = instance.Name.Length > 0 ? instance.Name[..1].ToUpperInvariant() : "?", FontSize = size * .42, FontWeight = FontWeight.Bold,
                Foreground = new SolidColorBrush(Color.Parse("#E6FFFFFF")), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center
            }
        };
    }

    private static Border LoaderIcon(string loader)
    {
        var (data, color) = loader switch
        {
            "fabric" => ("M 9,1 L 17,9 L 9,17 L 1,9 Z M 5,5 L 13,13 M 3,7 L 11,15 M 7,3 L 15,11", "#DBD0B4"),
            "quilt" => ("M 2,2 H 8 V 8 H 2 Z M 10,2 H 16 V 8 H 10 Z M 2,10 H 8 V 16 H 2 Z M 10,10 H 16 V 16 H 10 Z", "#B48CF2"),
            "forge" => ("M 1,4 H 17 L 14,8 H 11 V 11 L 14,14 H 4 L 7,11 V 8 H 4 Z", "#7AA7E0"),
            "neoforge" => ("M 1,4 H 17 L 14,8 H 11 V 11 L 14,14 H 4 L 7,11 V 8 H 4 Z M 15,1 L 16,2.5 M 2,1 L 3,2.5", "#F0913A"),
            _ => ("M 9,1 L 17,5 V 13 L 9,17 L 1,13 V 5 Z M 1,5 L 9,9 L 17,5 M 9,9 V 17", "#7BC86C")
        };
        var border = new Border
        {
            Width = 30, Height = 30, CornerRadius = new CornerRadius(15), Background = new SolidColorBrush(Color.Parse("#E0101114")),
            Child = new Avalonia.Controls.Shapes.Path { Data = Geometry.Parse(data), Stroke = new SolidColorBrush(Color.Parse(color)), StrokeThickness = 1.5, Width = 15, Height = 15, Stretch = Stretch.Uniform,
                StrokeLineCap = PenLineCap.Round, StrokeJoin = PenLineJoin.Round }
        };
        ToolTip.SetTip(border, LoaderName(loader));
        return border;
    }

    private void MineFilter_Changed(object? sender, RoutedEventArgs e) => RenderCards();

    private async void Import_Click(object? sender, RoutedEventArgs e)
    {
        if (_importing || TopLevel.GetTopLevel(this) is not { } top) return;
        var files = await top.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Import a Modrinth modpack",
            FileTypeFilter = new[] { new FilePickerFileType("Modrinth modpack (*.mrpack)") { Patterns = new[] { "*.mrpack" } } }
        });
        if (files.Count == 0) return;
        await ImportAsync(files[0].Path.LocalPath);
    }

    internal async Task ImportAsync(string file)
    {
        _importing = true;
        void Report(string message) => Dispatcher.UIThread.Post(() => Status.Text = message);
        try
        {
            Report("Reading " + System.IO.Path.GetFileName(file) + "...");
            // A pack published on Modrinth gets its source (for updates), author and icon.
            ModpackVersion? published = null;
            string? title = null, author = null, icon = null;
            try
            {
                published = await ModrinthModpacks.FindByFileAsync(Http, file, CancellationToken.None);
                if (published != null) (title, author, icon) = await ModrinthModpacks.ProjectAsync(Http, published.ProjectId, CancellationToken.None);
            }
            catch (HttpRequestException) { } // offline: imported without them
            var source = published == null ? null : new ModpackSource { ProjectId = published.ProjectId, VersionId = published.Id };
            var instance = await ModpackLauncher.InstallAsync(DataDirectory, file, source, title, author, icon, Http, Report, CancellationToken.None);
            await LoadAsync();
            Report($"Imported {instance.Name}.");
        }
        catch (Exception error) { Report("Import failed: " + error.Message); Host?.ModpackLog("Import failed: " + error); }
        finally { _importing = false; }
    }

    private void Create_Click(object? sender, RoutedEventArgs e) => ShowCreate();

    internal void ShowCreate()
    {
        CreateName.Text = "";
        CreateStatus.Text = "";
        CreatePanel.IsVisible = true;
        CreateName.Focus();
    }

    internal void HideCreate() => CreatePanel.IsVisible = false;
    private void CreateCancel_Click(object? sender, RoutedEventArgs e) => HideCreate();

    private async void CreateConfirm_Click(object? sender, RoutedEventArgs e)
    {
        string name = (CreateName.Text ?? "").Trim(), version = (CreateVersion.Text ?? "").Trim();
        string loader = Modpacks.Loaders[Math.Max(0, CreateLoader.SelectedIndex)];
        if (name.Length == 0) { CreateStatus.Text = "Give the modpack a name."; return; }
        try { GameVersionPolicy.ValidateMinecraftVersion(version); }
        catch (ArgumentException error) { CreateStatus.Text = error.Message; return; }
        CreateConfirm.IsEnabled = false;
        try
        {
            CreateStatus.Text = loader == "vanilla" ? "" : $"Finding the newest {LoaderName(loader)} for Minecraft {version}...";
            var loaderVersion = await ModrinthModpacks.LatestLoaderAsync(Http, loader, version, CancellationToken.None);
            var instance = Modpacks.NewInstance(DataDirectory, name);
            instance.McVersion = version;
            instance.Loader = loader;
            instance.LoaderVersion = loaderVersion;
            Modpacks.Save(instance);
            CreatePanel.IsVisible = false;
            await LoadAsync();
            OpenManage(instance);
        }
        catch (Exception error) { CreateStatus.Text = error.Message; }
        finally { CreateConfirm.IsEnabled = true; }
    }

    // ── Browse ──────────────────────────────────────────────────────────────

    private void Tabs_Changed(object? sender, SelectionChangedEventArgs e)
    {
        if (!_ready || e.Source != Tabs) return;
        bool browse = BrowseTab.IsSelected;
        MineToolbar.IsVisible = MineScroll.IsVisible = !browse;
        BrowseToolbar.IsVisible = BrowseScroll.IsVisible = browse;
        Status.Text = "";
        if (browse && !_browsed) _ = SearchAsync(more: false);
        else if (!browse) RenderCards();
    }

    // --preview-modpacks hooks.
    internal void ShowBrowse() => Tabs.SelectedItem = BrowseTab;
    internal void PressPlay() => Play_Click(null, new RoutedEventArgs());
    internal string ManageStatusText => ManageStatus.Text ?? "";
    internal Process? RunningGame(string id) => _running.GetValueOrDefault(id);
    internal bool BrowseLoaded => Results.Children.Count > 0;

    private void BrowseFilter_Changed(object? sender, SelectionChangedEventArgs e) { if (_ready && _browsed) _ = SearchAsync(more: false); }
    private void BrowseSearch_Click(object? sender, RoutedEventArgs e) => _ = SearchAsync(more: false);
    private void BrowseSearch_KeyDown(object? sender, KeyEventArgs e) { if (e.Key == Key.Enter) _ = SearchAsync(more: false); }
    private void More_Click(object? sender, RoutedEventArgs e) => _ = SearchAsync(more: true);

    private string? BrowseGameVersion => BrowseVersion.SelectedIndex > 0 ? GameVersions[BrowseVersion.SelectedIndex - 1] : null;
    private string? BrowseLoaderId => BrowseLoader.SelectedIndex > 0 ? Modpacks.Loaders[BrowseLoader.SelectedIndex - 1] : null;

    private async Task SearchAsync(bool more)
    {
        _browsed = true;
        _search?.Cancel();
        var search = _search = new CancellationTokenSource();
        if (!more) { _browseOffset = 0; Results.Children.Clear(); MoreButton.IsVisible = false; }
        Status.Text = "Searching Modrinth...";
        try
        {
            var hits = await ModrinthModpacks.SearchAsync(Http, BrowseSearch.Text ?? "", BrowseGameVersion, BrowseLoaderId,
                BrowseSorts[Math.Max(0, BrowseSort.SelectedIndex)].Index, _browseOffset, search.Token);
            _browseOffset += hits.Count;
            foreach (var hit in hits) Results.Children.Add(ResultRow(hit));
            MoreButton.IsVisible = hits.Count == 24;
            Status.Text = Results.Children.Count == 0 ? "No modpacks match the search and filters." : "";
        }
        catch (OperationCanceledException) when (search.IsCancellationRequested) { }
        catch (Exception error) { Status.Text = "Modrinth search failed: " + error.Message; }
    }

    private Control ResultRow(ModpackHit hit)
    {
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };
        var icon = new Border { Width = 72, Height = 72, CornerRadius = new CornerRadius(8), ClipToBounds = true, Background = new SolidColorBrush(Color.Parse("#2A2B31")), VerticalAlignment = VerticalAlignment.Top };
        _ = LoadRemoteIconAsync(icon, hit.IconUrl);
        grid.Children.Add(icon);

        var text = new StackPanel { Spacing = 3, Margin = new Thickness(14, 0, 14, 0) };
        var title = new TextBlock { TextWrapping = TextWrapping.Wrap, Inlines = new Avalonia.Controls.Documents.InlineCollection
        {
            new Avalonia.Controls.Documents.Run(hit.Title) { FontSize = 15, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White },
            new Avalonia.Controls.Documents.Run("  by " + hit.Author) { FontSize = 12, Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")) }
        } };
        text.Children.Add(title);
        text.Children.Add(new TextBlock { Text = hit.Description, TextWrapping = TextWrapping.Wrap, MaxLines = 2, TextTrimming = TextTrimming.CharacterEllipsis, Foreground = new SolidColorBrush(Color.Parse("#C9CAD1")), FontSize = 12 });
        string versions = hit.GameVersions.Count == 0 ? "" : hit.GameVersions.Count == 1 ? hit.GameVersions[0] : $"{hit.GameVersions[0]} – {hit.GameVersions[^1]}";
        string loaders = string.Join(", ", hit.Loaders.Select(LoaderName));
        text.Children.Add(new TextBlock { Text = $"Minecraft {versions}  ·  {loaders}  ·  {Downloads(hit.Downloads)} downloads", FontSize = 11, Foreground = new SolidColorBrush(Color.Parse("#8F919B")), TextWrapping = TextWrapping.Wrap });
        Grid.SetColumn(text, 1);
        grid.Children.Add(text);

        var install = new Button { Classes = { "launch" }, Content = "Install", Width = 110, Height = 34, HorizontalContentAlignment = HorizontalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Right };
        var progress = new ProgressBar { IsIndeterminate = true, IsVisible = false, Width = 200, Margin = new Thickness(0, 8, 0, 0) };
        var status = new TextBlock { Width = 200, FontSize = 11, Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")), TextWrapping = TextWrapping.Wrap, TextAlignment = TextAlignment.Right, Margin = new Thickness(0, 6, 0, 0) };
        if (_instances.Any(i => i.Source?.ProjectId == hit.ProjectId)) status.Text = "Already in My Modpacks.";
        install.Click += async (_, _) => await InstallAsync(hit, install, progress, status);
        var side = new StackPanel { Width = 200 };
        side.Children.Add(install);
        side.Children.Add(progress);
        side.Children.Add(status);
        Grid.SetColumn(side, 2);
        grid.Children.Add(side);
        return new Border { Background = new SolidColorBrush(Color.Parse("#1B1C20")), CornerRadius = new CornerRadius(8), Padding = new Thickness(14), Child = grid };
    }

    private static string Downloads(long n) => n >= 1_000_000 ? $"{n / 1_000_000.0:0.#}M" : n >= 1_000 ? $"{n / 1_000.0:0.#}K" : n.ToString();

    private static async Task LoadRemoteIconAsync(Border target, string? url)
    {
        if (string.IsNullOrEmpty(url)) return;
        try
        {
            if (!RemoteIcons.TryGetValue(url, out var bitmap))
                RemoteIcons[url] = bitmap = new Bitmap(new MemoryStream(await Http.GetByteArrayAsync(url)));
            if (bitmap != null) target.Child = new Image { Source = bitmap, Stretch = Stretch.UniformToFill };
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or ArgumentException or InvalidDataException or NotSupportedException) { RemoteIcons[url] = null; }
    }

    /// <summary>The newest version of the pack for the Browse filters, installed as a new instance.</summary>
    private async Task InstallAsync(ModpackHit hit, Button install, ProgressBar progress, TextBlock status)
    {
        install.IsEnabled = false;
        progress.IsVisible = true;
        void Report(string message) => Dispatcher.UIThread.Post(() => status.Text = message);
        string? gameVersion = BrowseGameVersion, loader = BrowseLoaderId;
        try
        {
            Report("Finding the newest version...");
            var versions = await ModrinthModpacks.VersionsAsync(Http, hit.ProjectId, CancellationToken.None);
            var version = versions.FirstOrDefault(v => (gameVersion == null || v.GameVersions.Contains(gameVersion)) && (loader == null || v.Loaders.Contains(loader)))
                ?? throw new InvalidOperationException("No version of this pack matches the Minecraft version and loader filters.");
            var instance = await WithDownloadedPackAsync(version, Report, file => ModpackLauncher.InstallAsync(DataDirectory, file,
                new ModpackSource { ProjectId = version.ProjectId, VersionId = version.Id }, hit.Title, hit.Author, hit.IconUrl, Http, Report, CancellationToken.None));
            await LoadAsync();
            Report($"Installed {instance.Name} {instance.PackVersion} (Minecraft {instance.McVersion}, {LoaderName(instance.Loader)}). Open it in My Modpacks.");
        }
        catch (Exception error) { Report("Install failed: " + error.Message); Host?.ModpackLog($"Install of {hit.Title} failed: {error}"); }
        finally { install.IsEnabled = true; progress.IsVisible = false; }
    }

    private static async Task<T> WithDownloadedPackAsync<T>(ModpackVersion version, Action<string> report, Func<string, Task<T>> use)
    {
        var downloads = System.IO.Path.Combine(ModpackLauncher.CacheRoot(DataDirectory), "downloads");
        Directory.CreateDirectory(downloads);
        var file = System.IO.Path.Combine(downloads, version.Id + ".mrpack");
        report($"Downloading the pack ({version.Size / 1024} KB)...");
        await ModrinthModpacks.DownloadAsync(Http, version, file, CancellationToken.None);
        try { return await use(file); }
        finally { File.Delete(file); }
    }

    // ── One instance ────────────────────────────────────────────────────────

    internal void OpenManage(ModpackInstance instance)
    {
        _selected = instance;
        LibraryPanel.IsVisible = false;
        ManagePanel.IsVisible = true;
        ManageArt.Child = Art(instance, 96);
        ManageTitle.Text = instance.Name;
        ManageInfo.Text = $"Minecraft {instance.McVersion}  ·  {LoaderName(instance.Loader)} {instance.LoaderVersion}".TrimEnd()
            + (string.IsNullOrEmpty(instance.PackVersion) ? "" : $"  ·  pack {instance.PackVersion}") + "  ·  " + Byline(instance)
            + (instance.LastPlayedUtc is { } played ? $"  ·  last played {played.ToLocalTime():g}" : "");
        NameBox.Text = instance.Name;
        RamBox.Value = instance.MaxRamMb;
        ManageStatus.Text = "";
        UpdateCard.IsVisible = instance.Source != null;
        VersionBox.ItemsSource = null;
        RenderMods();
        RefreshButtons();
        if (instance.Source != null) _ = LoadVersionsAsync(instance);
    }

    private void Back_Click(object? sender, RoutedEventArgs e)
    {
        _selected = null;
        ManagePanel.IsVisible = false;
        LibraryPanel.IsVisible = true;
        RenderCards();
    }

    private bool Idle(ModpackInstance i) => !_running.ContainsKey(i.Id) && !_launching.ContainsKey(i.Id) && !_busy.Contains(i.Id);

    private void RefreshButtons()
    {
        if (_selected is not { } i) return;
        bool running = _running.ContainsKey(i.Id), launching = _launching.ContainsKey(i.Id);
        PlayButton.Content = launching ? "Cancel" : running ? "Running" : "Play";
        PlayButton.IsEnabled = !running && !_busy.Contains(i.Id);
        DeleteButton.IsEnabled = UpdateButton.IsEnabled = Idle(i);
    }

    private void RenderMods()
    {
        ModsList.Children.Clear();
        if (_selected is not { } i) return;
        List<ModpackMod> mods;
        try { mods = Modpacks.Mods(i); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { ModsHeader.Text = "MODS"; ManageStatus.Text = e.Message; return; }
        ModsHeader.Text = $"MODS · {mods.Count(m => m.Enabled)} OF {mods.Count} ON";
        if (mods.Count == 0)
            ModsList.Children.Add(new TextBlock { Text = i.Loader == "vanilla" ? "Vanilla instances have no mods." : "No mods yet. Use Open folder and put mods in the mods folder.", Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")) });
        foreach (var mod in mods)
        {
            var box = new CheckBox { Content = mod.FileName, IsChecked = mod.Enabled, IsEnabled = Idle(i) };
            box.IsCheckedChanged += (_, _) =>
            {
                try { Modpacks.SetModEnabled(mod, box.IsChecked == true); }
                catch (Exception e) when (e is IOException or UnauthorizedAccessException) { ManageStatus.Text = $"Could not switch {mod.FileName}: {e.Message}"; }
                RenderMods();
            };
            ModsList.Children.Add(box);
        }
    }

    private async void Play_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is not { } instance || Host is not { } host) return;
        if (_launching.TryGetValue(instance.Id, out var pending)) { pending.Cancel(); return; }
        if (!Idle(instance)) return;
        using var launch = new CancellationTokenSource();
        _launching[instance.Id] = launch;
        RefreshButtons();
        RenderMods();
        void Report(string message) => Dispatcher.UIThread.Post(() => { if (_selected == instance) ManageStatus.Text = message; });
        try
        {
            Report("Signing in...");
            var session = await host.ResolveModpackSessionAsync(launch.Token);
            var process = await ModpackLauncher.LaunchAsync(DataDirectory, instance, session, host.ModpackSettings, JavaService.Instance, Http, Report, launch.Token);
            _running[instance.Id] = process;
            process.EnableRaisingEvents = true;
            process.Exited += (_, _) => Dispatcher.UIThread.Post(() =>
            {
                _running.Remove(instance.Id);
                int code = process.ExitCode;
                host.ModpackLog($"{instance.Name} closed (exit code {code}).");
                if (_selected == instance)
                    ManageStatus.Text = code == 0 ? "Game closed." : $"Minecraft closed with exit code {code}. See minecraft\\logs\\latest.log (and crash-reports) in Open folder.";
                RefreshButtons();
                RenderMods();
                RenderCards();
            });
            Report($"Minecraft {instance.McVersion} started as {session.Username}.");
            host.ModpackLog($"Started {instance.Name} ({instance.Loader} {instance.LoaderVersion}, Minecraft {instance.McVersion}) PID {process.Id}.");
        }
        catch (OperationCanceledException) when (launch.IsCancellationRequested) { Report("Launch cancelled."); }
        catch (Exception error) { Report("Launch failed: " + error.Message); host.ModpackLog($"Launch of {instance.Name} failed: {error}"); }
        finally
        {
            _launching.Remove(instance.Id);
            RefreshButtons();
            RenderMods();
        }
    }

    private void OpenFolder_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is not { } i) return;
        try
        {
            Directory.CreateDirectory(i.GameDirectory);
            Process.Start(new ProcessStartInfo(i.GameDirectory) { UseShellExecute = true });
        }
        catch (Exception error) { ManageStatus.Text = error.Message; }
    }

    private async void Delete_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is not { } i || !Idle(i) || Host is not { } host) return;
        if (!await host.ConfirmModpackAsync("Delete modpack", $"Delete '{i.Name}'? The whole instance, including its worlds, is moved to the Recycle Bin.", "Delete")) return;
        try
        {
            SafeFileOps.DeleteToRecycleBin(i.Directory);
            Back_Click(null, new RoutedEventArgs());
            await LoadAsync();
            Status.Text = $"Deleted {i.Name}.";
        }
        catch (Exception error) when (error is IOException or OperationCanceledException or InvalidOperationException) { ManageStatus.Text = error.Message; }
    }

    private void Save_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is not { } i) return;
        var name = (NameBox.Text ?? "").Trim();
        if (name.Length == 0) { ManageStatus.Text = "The name cannot be empty."; return; }
        i.Name = name;
        i.MaxRamMb = (int)(RamBox.Value ?? 0);
        try { Modpacks.Save(i); OpenManage(i); ManageStatus.Text = "Saved."; }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException) { ManageStatus.Text = error.Message; }
    }

    private async void Icon_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is not { } i || TopLevel.GetTopLevel(this) is not { } top) return;
        var files = await top.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions { Title = "Choose an icon", FileTypeFilter = new[] { FilePickerFileTypes.ImageAll } });
        if (files.Count == 0) return;
        try
        {
            var bytes = await File.ReadAllBytesAsync(files[0].Path.LocalPath);
            using (new Bitmap(new MemoryStream(bytes))) { } // refuse what cannot be shown
            await File.WriteAllBytesAsync(System.IO.Path.Combine(i.Directory, "icon.png"), bytes);
            i.IconPath = "icon.png";
            Modpacks.Save(i);
            OpenManage(i);
        }
        catch (Exception error) { ManageStatus.Text = "Could not use that image: " + error.Message; }
    }

    private async Task LoadVersionsAsync(ModpackInstance instance)
    {
        try
        {
            var versions = await ModrinthModpacks.VersionsAsync(Http, instance.Source!.ProjectId, CancellationToken.None);
            if (_selected != instance) return;
            _versions = versions;
            VersionBox.ItemsSource = versions.Select(v => $"{v.VersionNumber}  ·  {string.Join(", ", v.GameVersions)}  ·  {string.Join(", ", v.Loaders.Select(LoaderName))}"
                + (v.Id == instance.Source.VersionId ? "  (installed)" : "")).ToList();
            // Offered first: the newest version for this instance's Minecraft version and loader (the list has every version).
            var newest = versions.FirstOrDefault(v => v.GameVersions.Contains(instance.McVersion) && v.Loaders.Contains(instance.Loader));
            VersionBox.SelectedIndex = newest != null ? versions.IndexOf(newest) : versions.Count == 0 ? -1 : 0;
            var installed = versions.FirstOrDefault(v => v.Id == instance.Source.VersionId);
            if (newest != null && newest.Id != instance.Source.VersionId && (installed == null || newest.Published > installed.Published))
                ManageStatus.Text = $"Version {newest.VersionNumber} is available.";
        }
        catch (Exception error) when (error is HttpRequestException or TaskCanceledException or System.Text.Json.JsonException)
        {
            if (_selected == instance) ManageStatus.Text = "Could not check Modrinth for other versions: " + error.Message;
        }
    }

    private async void Update_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is not { } instance || !Idle(instance) || VersionBox.SelectedIndex < 0 || VersionBox.SelectedIndex >= _versions.Count) return;
        var version = _versions[VersionBox.SelectedIndex];
        if (!version.GameVersions.Contains(instance.McVersion) && Host is { } host
            && !await host.ConfirmModpackAsync("Change Minecraft version", $"Version {version.VersionNumber} is for Minecraft {string.Join(", ", version.GameVersions)}, "
                + $"not {instance.McVersion}. Worlds opened in a newer Minecraft version cannot be opened in {instance.McVersion} again.", "Install it")) return;
        _busy.Add(instance.Id);
        RefreshButtons();
        RenderMods();
        void Report(string message) => Dispatcher.UIThread.Post(() => { if (_selected == instance) ManageStatus.Text = message; });
        try
        {
            await WithDownloadedPackAsync(version, Report, async file =>
            {
                await ModpackLauncher.UpdateAsync(instance, file, new ModpackSource { ProjectId = version.ProjectId, VersionId = version.Id }, Http, Report, CancellationToken.None);
                return true;
            });
            if (_selected == instance) OpenManage(instance);
            Report($"Now on {instance.PackVersion} (Minecraft {instance.McVersion}). Your worlds and changed settings were kept.");
        }
        catch (Exception error) { Report("Update failed: " + error.Message); Host?.ModpackLog($"Update of {instance.Name} failed: {error}"); }
        finally
        {
            _busy.Remove(instance.Id);
            RefreshButtons();
            RenderMods();
        }
    }
}
