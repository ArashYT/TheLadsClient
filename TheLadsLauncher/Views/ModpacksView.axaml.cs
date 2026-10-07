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
    private static readonly string[] GameVersions = GameVersionPolicy.ModrinthGameVersions;
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

    private ExternalScan? _externalScan;
    private bool _externalLoaded;
    private bool _updatingRam;

    private static string DataDirectory => PathService.Instance.BaseDirectory;
    private MainWindow? Host => TopLevel.GetTopLevel(this) as MainWindow;

    public ModpacksView()
    {
        InitializeComponent();
        MineSort.ItemsSource = new[] { "Recently played", "Recently created", "Name", "Minecraft version" };
        MineLoader.ItemsSource = new[] { "All loaders" }.Concat(LoaderNames).ToArray();
        ExternalLauncherFilter.ItemsSource = new[] { "All launchers", "CurseForge", "Modrinth App", "Prism Launcher", "MultiMC" };
        ExternalSort.ItemsSource = new[] { "Recently played", "Name", "Minecraft version", "Launcher" };
        BrowseVersion.ItemsSource = new[] { "All versions" }.Concat(GameVersions).ToArray();
        BrowseLoader.ItemsSource = new[] { "All loaders" }.Concat(LoaderNames.Take(4)).ToArray();
        BrowseSort.ItemsSource = BrowseSorts.Select(s => s.Label).ToArray();
        CreateLoader.ItemsSource = LoaderNames;
        CreateVersion.ItemsSource = GameVersions;
        MineSort.SelectedIndex = MineLoader.SelectedIndex = ExternalLauncherFilter.SelectedIndex = ExternalSort.SelectedIndex = BrowseVersion.SelectedIndex = BrowseLoader.SelectedIndex = BrowseSort.SelectedIndex = CreateLoader.SelectedIndex = CreateVersion.SelectedIndex = 0;
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
        text.Children.Add(new TextBlock { Text = instance.Name, FontSize = 14, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White, TextTrimming = TextTrimming.CharacterEllipsis }.Untranslated());
        text.Children.Add(new TextBlock { Text = Byline(instance), FontSize = 11, Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")), TextTrimming = TextTrimming.CharacterEllipsis }.Untranslated(instance.Author is { Length: > 0 }));
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
        string name = (CreateName.Text ?? "").Trim(), version = (CreateVersion.SelectedItem as string ?? CreateVersion.Text ?? "26.3").Trim();
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

    // ── External Instances ──────────────────────────────────────────────────

    private void ExternalFilter_Changed(object? sender, RoutedEventArgs e) => RenderExternalCards();

    private async Task LoadExternalAsync(bool force = false)
    {
        if (_externalLoaded && !force) { RenderExternalCards(); return; }
        Status.Text = "Scanning for external instances (CurseForge, Modrinth, Prism, MultiMC)...";
        try
        {
            _externalScan = await ExternalInstances.ScanAsync(DataDirectory);
            _externalLoaded = true;
            RenderExternalCards();
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            Status.Text = "Failed to scan external instances: " + e.Message;
        }
    }

    private void RescanExternal_Click(object? sender, RoutedEventArgs e) => _ = LoadExternalAsync(force: true);

    private async void AddExternalFolder_Click(object? sender, RoutedEventArgs e)
    {
        if (TopLevel.GetTopLevel(this) is not { } top) return;
        var folders = await top.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions { Title = "Choose an instances or launcher data folder" });
        if (folders.Count == 0) return;
        string picked = folders[0].Path.LocalPath;
        var existing = ExternalInstances.LoadCustomFolders(DataDirectory);
        if (!existing.Contains(picked, StringComparer.OrdinalIgnoreCase))
        {
            existing.Add(picked);
            ExternalInstances.SaveCustomFolders(DataDirectory, existing);
            await LoadExternalAsync(force: true);
        }
    }

    private void RenderExternalCards()
    {
        if (!_ready) return;
        ExternalCards.Children.Clear();
        if (_externalScan == null) return;

        string query = ExternalSearch.Text ?? "";
        int launcherIndex = ExternalLauncherFilter.SelectedIndex;
        ExternalLauncher? targetLauncher = launcherIndex switch
        {
            1 => ExternalLauncher.CurseForge,
            2 => ExternalLauncher.Modrinth,
            3 => ExternalLauncher.Prism,
            4 => ExternalLauncher.MultiMC,
            _ => null
        };

        var list = _externalScan.Instances.Where(i =>
            (targetLauncher == null || i.Launcher == targetLauncher) &&
            (string.IsNullOrWhiteSpace(query) || i.Name.Contains(query, StringComparison.OrdinalIgnoreCase) || (i.McVersion != null && i.McVersion.Contains(query, StringComparison.OrdinalIgnoreCase)))
        );

        list = ExternalSort.SelectedIndex switch
        {
            0 => list.OrderByDescending(i => i.LastPlayedUtc ?? DateTime.MinValue).ThenBy(i => i.Name, StringComparer.CurrentCultureIgnoreCase),
            1 => list.OrderBy(i => i.Name, StringComparer.CurrentCultureIgnoreCase),
            2 => list.OrderByDescending(i => Version.TryParse(i.McVersion, out var v) ? v : new Version(0, 0)),
            3 => list.OrderBy(i => i.Launcher).ThenBy(i => i.Name, StringComparer.CurrentCultureIgnoreCase),
            _ => list
        };

        var items = list.ToList();
        foreach (var inst in items)
        {
            ExternalCards.Children.Add(ExternalCard(inst));
        }

        if (ExternalTab.IsSelected)
        {
            if (_externalScan.Instances.Count == 0)
            {
                Status.Text = "No external instances found. Click '+ Add folder' to point to your CurseForge, Modrinth, Prism, or MultiMC folders.";
            }
            else if (items.Count == 0)
            {
                Status.Text = "No external instances match the search filter.";
            }
            else
            {
                Status.Text = $"{items.Count} external instance{(items.Count == 1 ? "" : "s")} found across {_externalScan.Sources.Count} launcher location{(_externalScan.Sources.Count == 1 ? "" : "s")}.";
            }
        }
    }

    private Control ExternalCard(ExternalInstance instance)
    {
        var card = new Border
        {
            Classes = { "card" },
            Width = 260,
            Margin = new Thickness(0, 0, 16, 16)
        };

        var root = new StackPanel { Spacing = 10 };

        var header = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*") };
        var iconBorder = new Border
        {
            Width = 44,
            Height = 44,
            CornerRadius = new CornerRadius(6),
            ClipToBounds = true,
            Background = new SolidColorBrush(Color.Parse("#25262B")),
            VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(0, 0, 10, 0)
        };

        if (instance.IconFile != null && File.Exists(instance.IconFile))
        {
            try
            {
                using var stream = File.OpenRead(instance.IconFile);
                iconBorder.Child = new Image { Source = new Bitmap(stream), Stretch = Stretch.UniformToFill };
            }
            catch { }
        }
        else if (instance.IconUrl != null)
        {
            _ = LoadRemoteIconAsync(iconBorder, instance.IconUrl);
        }
        else
        {
            iconBorder.Child = new TextBlock
            {
                Text = instance.Name.Length > 0 ? instance.Name[..1].ToUpperInvariant() : "E",
                FontWeight = FontWeight.Bold,
                FontSize = 20,
                Foreground = Brushes.White,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
        }
        header.Children.Add(iconBorder);

        var titleStack = new StackPanel { Spacing = 3, VerticalAlignment = VerticalAlignment.Center };
        Grid.SetColumn(titleStack, 1);

        var launcherName = ExternalInstances.LauncherName(instance.Launcher);
        var chip = new Border
        {
            Classes = { "chip" },
            HorizontalAlignment = HorizontalAlignment.Left,
            Child = new TextBlock { Text = launcherName, FontSize = 10.5, FontWeight = FontWeight.SemiBold }
        };
        titleStack.Children.Add(chip);

        var titleBlock = new TextBlock
        {
            Text = instance.Name,
            FontWeight = FontWeight.SemiBold,
            FontSize = 14,
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        ToolTip.SetTip(titleBlock, instance.Name);
        titleStack.Children.Add(titleBlock);
        header.Children.Add(titleStack);
        root.Children.Add(header);

        var detailsPanel = new StackPanel { Spacing = 2 };
        var info = new List<string>();
        if (!string.IsNullOrEmpty(instance.McVersion))
            info.Add($"MC {instance.McVersion}");
        if (!string.IsNullOrEmpty(instance.Loader))
            info.Add(LoaderName(instance.Loader));
        if (instance.ModCount > 0)
            info.Add($"{instance.ModCount} mods");

        if (info.Count > 0)
        {
            detailsPanel.Children.Add(new TextBlock
            {
                Text = string.Join("  ·  ", info),
                Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")),
                FontSize = 11.5
            });
        }

        if (instance.LastPlayedUtc is { } lp)
        {
            detailsPanel.Children.Add(new TextBlock
            {
                Text = $"Last played {RelativeTime(lp)}",
                Foreground = new SolidColorBrush(Color.Parse("#767884")),
                FontSize = 11
            });
        }
        root.Children.Add(detailsPanel);

        var actions = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), Margin = new Thickness(0, 4, 0, 0) };
        string? exe = ExternalInstances.FindExecutable(instance.Launcher, instance.DataRoot);
        bool canLaunch = ExternalInstances.CanLaunch(instance, exe);

        var playBtn = new Button
        {
            Classes = { "launch" },
            Content = instance.Launcher == ExternalLauncher.Modrinth ? "Open App" : "Play",
            Height = 32,
            HorizontalAlignment = HorizontalAlignment.Stretch,
            HorizontalContentAlignment = HorizontalAlignment.Center,
            IsEnabled = canLaunch || (instance.Launcher == ExternalLauncher.Modrinth && exe != null)
        };

        if (!playBtn.IsEnabled)
        {
            ToolTip.SetTip(playBtn, $"{launcherName} was not found on your system. Please install it or start {instance.Name} directly.");
            ToolTip.SetShowOnDisabled(playBtn, true);
        }
        else
        {
            playBtn.Click += (_, _) =>
            {
                if (exe == null) return;
                try
                {
                    var startInfo = ExternalInstances.LaunchCommand(instance, exe);
                    Process.Start(startInfo);
                    Status.Text = $"Launched {instance.Name} via {launcherName}.";
                }
                catch (Exception ex)
                {
                    Status.Text = $"Failed to start {instance.Name}: {ex.Message}";
                }
            };
        }
        actions.Children.Add(playBtn);

        var folderBtn = new Button
        {
            Classes = { "icon" },
            Content = "📁",
            Margin = new Thickness(8, 0, 0, 0),
            Height = 32,
            Width = 32
        };
        ToolTip.SetTip(folderBtn, "Open instance directory in File Explorer");
        Grid.SetColumn(folderBtn, 1);
        folderBtn.Click += (_, _) =>
        {
            string target = Directory.Exists(instance.GameDirectory) ? instance.GameDirectory : instance.Directory;
            if (Directory.Exists(target)) Process.Start(new ProcessStartInfo(target) { UseShellExecute = true });
        };
        actions.Children.Add(folderBtn);

        root.Children.Add(actions);
        card.Child = root;
        return card;
    }

    private static string RelativeTime(DateTime utc)
    {
        var span = DateTime.UtcNow - utc;
        if (span.TotalMinutes < 1) return "just now";
        if (span.TotalHours < 1) return $"{(int)span.TotalMinutes}m ago";
        if (span.TotalDays < 1) return $"{(int)span.TotalHours}h ago";
        if (span.TotalDays < 7) return $"{(int)span.TotalDays}d ago";
        return utc.ToLocalTime().ToString("MMM d, yyyy");
    }

    // ── Browse ──────────────────────────────────────────────────────────────

    private void Tabs_Changed(object? sender, SelectionChangedEventArgs e)
    {
        if (!_ready || e.Source != Tabs) return;
        bool isMine = MineTab.IsSelected;
        bool isExternal = ExternalTab.IsSelected;
        bool isBrowse = BrowseTab.IsSelected;

        MineToolbar.IsVisible = MineScroll.IsVisible = isMine;
        ExternalToolbar.IsVisible = ExternalScroll.IsVisible = isExternal;
        BrowseToolbar.IsVisible = BrowseScroll.IsVisible = isBrowse;

        Status.Text = "";
        if (isBrowse && !_browsed) _ = SearchAsync(more: false);
        else if (isMine) RenderCards();
        else if (isExternal) _ = LoadExternalAsync();
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
        } }.Untranslated();
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
        _updatingRam = true;
        RamBox.Value = instance.MaxRamMb;
        RamSlider.Value = Math.Clamp(instance.MaxRamMb / 1024.0, 0, 32);
        UpdateRamDisplay(instance.MaxRamMb);
        _updatingRam = false;
        ManageStatus.Text = "";
        UpdateCard.IsVisible = instance.Source != null;
        VersionBox.ItemsSource = null;
        RenderMods();
        RefreshButtons();
        if (instance.Source != null) _ = LoadVersionsAsync(instance);
    }

    private void RamSlider_ValueChanged(object? sender, Avalonia.Controls.Primitives.RangeBaseValueChangedEventArgs e)
    {
        if (!_ready || _updatingRam) return;
        _updatingRam = true;
        int mb = (int)Math.Round(RamSlider.Value * 1024.0);
        RamBox.Value = mb;
        UpdateRamDisplay(mb);
        _updatingRam = false;
    }

    private void RamBox_ValueChanged(object? sender, NumericUpDownValueChangedEventArgs e)
    {
        if (!_ready || _updatingRam) return;
        _updatingRam = true;
        int mb = (int)(RamBox.Value ?? 0);
        RamSlider.Value = Math.Clamp(mb / 1024.0, 0, 32);
        UpdateRamDisplay(mb);
        _updatingRam = false;
    }

    private void RamPreset_Click(object? sender, RoutedEventArgs e)
    {
        if (sender is Button b && int.TryParse(b.Tag?.ToString(), out int mb))
        {
            _updatingRam = true;
            RamBox.Value = mb;
            RamSlider.Value = Math.Clamp(mb / 1024.0, 0, 32);
            UpdateRamDisplay(mb);
            _updatingRam = false;
        }
    }

    private void UpdateRamDisplay(int mb)
    {
        if (mb <= 0) RamDisplay.Text = "Default (Settings)";
        else RamDisplay.Text = $"{(mb / 1024.0):0.#} GB ({mb} MB)";
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

    private string _addContentType = "mod";
    private string _addContentTargetDir = "";
    private CancellationTokenSource? _addContentSearchCts;

    private void InstanceTabs_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (_selected == null) return;
        RenderAllContent();
    }

    private void ContentFilter_Changed(object? sender, TextChangedEventArgs e)
    {
        if (_selected == null) return;
        RenderAllContent();
    }

    private void RenderAllContent()
    {
        if (_selected is not { } i) return;
        RenderContent("mods", "MODS", ModsHeader, ModsList, ModsSearch);
        RenderContent("resourcepacks", "RESOURCE PACKS", ResourcePacksHeader, ResourcePacksList, ResourcePacksSearch);
        RenderContent("shaderpacks", "SHADERS", ShadersHeader, ShadersList, ShadersSearch);
        RenderContent("datapacks", "DATAPACKS", DatapacksHeader, DatapacksList, DatapacksSearch);
    }

    private void RenderContent(string folder, string typeHeader, TextBlock header, StackPanel list, TextBox searchBox)
    {
        list.Children.Clear();
        if (_selected is not { } i) return;
        List<ModpackContentItem> items;
        try { items = Modpacks.Content(i, folder, typeHeader); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { header.Text = typeHeader; ManageStatus.Text = e.Message; return; }

        string filter = (searchBox.Text ?? "").Trim();
        var visible = string.IsNullOrEmpty(filter)
            ? items
            : items.Where(item => item.Name.Contains(filter, StringComparison.OrdinalIgnoreCase)).ToList();

        header.Text = $"{typeHeader} · {items.Count(m => m.Enabled)} OF {items.Count} ON";
        if (visible.Count == 0)
        {
            list.Children.Add(new TextBlock
            {
                Text = items.Count == 0
                    ? $"No {typeHeader.ToLowerInvariant()} installed. Use + Add {typeHeader.ToLowerInvariant()} to search Modrinth or + From file to install."
                    : "No items match your filter.",
                Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")),
                Margin = new Thickness(0, 10, 0, 0)
            });
            return;
        }

        foreach (var item in visible)
        {
            var rowGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            var box = new CheckBox
            {
                Content = item.Name,
                IsChecked = item.Enabled,
                IsEnabled = Idle(i),
                VerticalAlignment = VerticalAlignment.Center
            }.Untranslated();

            box.IsCheckedChanged += (_, _) =>
            {
                try { Modpacks.SetContentEnabled(item, box.IsChecked == true); }
                catch (Exception e) when (e is IOException or UnauthorizedAccessException) { ManageStatus.Text = $"Could not switch {item.Name}: {e.Message}"; }
                RenderContent(folder, typeHeader, header, list, searchBox);
            };
            rowGrid.Children.Add(box);

            var delBtn = new Button
            {
                Classes = { "danger" },
                Content = "✕",
                Width = 28,
                Height = 28,
                Padding = new Thickness(0),
                HorizontalContentAlignment = HorizontalAlignment.Center,
                VerticalContentAlignment = VerticalAlignment.Center,
            };
            ToolTip.SetTip(delBtn, "Delete this file");
            Grid.SetColumn(delBtn, 1);
            delBtn.Click += async (_, _) =>
            {
                if (Host is { } host && !await host.ConfirmModpackAsync("Delete item", $"Delete '{item.Name}' from this modpack?", "Delete")) return;
                try
                {
                    Modpacks.DeleteContent(item);
                    RenderContent(folder, typeHeader, header, list, searchBox);
                }
                catch (Exception e) when (e is IOException or UnauthorizedAccessException) { ManageStatus.Text = $"Could not delete {item.Name}: {e.Message}"; }
            };
            rowGrid.Children.Add(delBtn);

            var border = new Border
            {
                Background = new SolidColorBrush(Color.Parse("#1B1C20")),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(10, 6),
                Child = rowGrid
            };
            list.Children.Add(border);
        }
    }

    private void RenderMods() => RenderAllContent();

    private void AddMods_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected == null) return;
        OpenAddContent("mod", "Mods", Path.Combine(_selected.GameDirectory, "mods"));
    }

    private void AddResourcePacks_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected == null) return;
        OpenAddContent("resourcepack", "Resource Packs", Path.Combine(_selected.GameDirectory, "resourcepacks"));
    }

    private void AddShaders_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected == null) return;
        OpenAddContent("shader", "Shader Packs", Path.Combine(_selected.GameDirectory, "shaderpacks"));
    }

    private void AddDatapacks_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected == null) return;
        OpenAddContent("datapack", "Datapacks", Path.Combine(_selected.GameDirectory, "datapacks"));
    }

    private async void AddModsFromFile_Click(object? sender, RoutedEventArgs e) => await AddFromFileAsync("mods", "jar");
    private async void AddResourcePacksFromFile_Click(object? sender, RoutedEventArgs e) => await AddFromFileAsync("resourcepacks", "zip");
    private async void AddShadersFromFile_Click(object? sender, RoutedEventArgs e) => await AddFromFileAsync("shaderpacks", "zip");
    private async void AddDatapacksFromFile_Click(object? sender, RoutedEventArgs e) => await AddFromFileAsync("datapacks", "zip");

    private async Task AddFromFileAsync(string folder, string ext)
    {
        if (_selected is not { } i || TopLevel.GetTopLevel(this) is not { } top) return;
        var filter = new FilePickerFileType($"{ext.ToUpperInvariant()} files") { Patterns = new[] { $"*.{ext}" } };
        var files = await top.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = $"Add {folder} from files",
            AllowMultiple = true,
            FileTypeFilter = new[] { filter }
        });
        if (files == null || files.Count == 0) return;
        string targetDir = Path.Combine(i.GameDirectory, folder);
        Directory.CreateDirectory(targetDir);
        int copied = 0;
        foreach (var file in files)
        {
            try
            {
                string dest = Path.Combine(targetDir, file.Name);
                File.Copy(file.Path.LocalPath, dest, true);
                copied++;
            }
            catch (Exception ex) { ManageStatus.Text = $"Error copying {file.Name}: {ex.Message}"; }
        }
        ManageStatus.Text = $"Installed {copied} file(s) into {folder}.";
        RenderAllContent();
    }

    private ContentKind AddContentKind => _addContentType switch
    {
        "resourcepack" => ContentKind.ResourcePack,
        "shader" => ContentKind.Shader,
        "datapack" => ContentKind.DataPack,
        _ => ContentKind.Mod
    };

    private ContentCatalog Catalog()
    {
        var settings = LauncherSettings.Load();
        return new ContentCatalog(Http, settings.ModrinthApiUrl, settings.CurseForgeApiUrl, settings.CurseForgeApiKey);
    }

    private void OpenAddContent(string projectType, string title, string targetDir)
    {
        if (_selected is not { } i) return;
        _addContentType = projectType;
        _addContentTargetDir = targetDir;
        AddContentTitle.Text = $"Add {title}";
        AddContentSubtitle.Text = $"Automatically searching compatible content for Minecraft {i.McVersion} ({LoaderName(i.Loader)})";
        AddContentSearchBox.Text = "";
        AddContentResults.Children.Clear();
        AddContentStatus.Text = "";

        if (AddContentKind == ContentKind.Shader)
            AddContentCategory.ItemsSource = new[] { "All categories", "Realistic", "Fantasy", "Performance", "Vibrant", "Cel-shaded", "Vanilla-like" };
        else if (AddContentKind == ContentKind.DataPack)
            AddContentCategory.ItemsSource = new[] { "All categories", "Adventure", "Magic", "Technology", "Utility", "Worldgen", "Minigame" };
        else if (AddContentKind == ContentKind.ResourcePack)
            AddContentCategory.ItemsSource = new[] { "All categories", "16x", "32x", "64x", "128x", "Faithful", "Realistic", "Medieval", "Vanilla-like" };
        else
            AddContentCategory.ItemsSource = new[] { "All categories", "Adventure", "Cursed", "Decoration", "Economy", "Equipment", "Food", "Game Mechanics", "Magic", "Management", "Minigame", "Mobs", "Optimization", "Social", "Storage", "Technology", "Transportation", "Utility", "Worldgen" };
        AddContentCategory.SelectedIndex = 0;

        AddContentPanel.IsVisible = true;
        _ = SearchAddContentAsync("");
    }

    private void AddContentFilter_Changed(object? sender, SelectionChangedEventArgs e)
    {
        if (!_ready || AddContentPanel == null || !AddContentPanel.IsVisible || _selected == null) return;
        _ = SearchAddContentAsync(AddContentSearchBox?.Text ?? "");
    }

    private void AddContentBack_Click(object? sender, RoutedEventArgs e)
    {
        AddContentPanel.IsVisible = false;
        RenderAllContent();
    }

    private void AddContentSearch_Click(object? sender, RoutedEventArgs e) => _ = SearchAddContentAsync(AddContentSearchBox.Text ?? "");
    private void AddContentSearch_KeyDown(object? sender, KeyEventArgs e) { if (e.Key == Key.Enter) _ = SearchAddContentAsync(AddContentSearchBox.Text ?? ""); }

    private async Task SearchAddContentAsync(string query)
    {
        if (_selected is not { } i) return;
        _addContentSearchCts?.Cancel();
        var cts = _addContentSearchCts = new CancellationTokenSource();
        string provider = (AddContentProvider?.SelectedItem as ComboBoxItem)?.Content as string ?? "Modrinth";
        AddContentStatus.Text = $"Searching {provider}...";
        AddContentResults.Children.Clear();
        try
        {
            string? sort = (AddContentSort?.SelectedItem as ComboBoxItem)?.Content as string;
            string? cat = AddContentCategory?.SelectedItem as string;
            if (cat == "All categories") cat = null;

            var hits = await Catalog().SearchAsync(provider, AddContentKind, query, i.McVersion, i.Loader, cat, sort, cts.Token);
            if (cts.IsCancellationRequested) return;
            if (hits.Count == 0)
            {
                AddContentStatus.Text = $"No compatible results found on {provider} for this Minecraft version and loader.";
                return;
            }
            AddContentStatus.Text = $"Found {hits.Count} results on {provider}:";
            foreach (var hit in hits)
            {
                AddContentResults.Children.Add(CreateAddContentResultRow(hit));
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            AddContentStatus.Text = "Search failed: " + ex.Message;
        }
    }

    private Control CreateAddContentResultRow(ModSearchItem hit)
    {
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };
        var icon = new Border { Width = 56, Height = 56, CornerRadius = new CornerRadius(6), ClipToBounds = true, Background = new SolidColorBrush(Color.Parse("#2A2B31")), VerticalAlignment = VerticalAlignment.Top };
        _ = LoadRemoteIconAsync(icon, hit.IconUrl);
        grid.Children.Add(icon);

        var text = new StackPanel { Spacing = 2, Margin = new Thickness(12, 0, 12, 0) };
        var title = new TextBlock { TextWrapping = TextWrapping.Wrap, Inlines = new Avalonia.Controls.Documents.InlineCollection
        {
            new Avalonia.Controls.Documents.Run(hit.Name) { FontSize = 14, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White },
            new Avalonia.Controls.Documents.Run("  by " + hit.Author) { FontSize = 11, Foreground = new SolidColorBrush(Color.Parse("#A0A1AA")) }
        } }.Untranslated();
        text.Children.Add(title);
        text.Children.Add(new TextBlock { Text = hit.Summary, TextWrapping = TextWrapping.Wrap, MaxLines = 2, TextTrimming = TextTrimming.CharacterEllipsis, Foreground = new SolidColorBrush(Color.Parse("#C9CAD1")), FontSize = 11 });
        text.Children.Add(new TextBlock { Text = $"{Downloads(hit.DownloadCount)} downloads  ·  {hit.Provider}", FontSize = 10, Foreground = new SolidColorBrush(Color.Parse("#8F919B")) });
        Grid.SetColumn(text, 1);
        grid.Children.Add(text);

        var installBtn = new Button { Classes = { "launch" }, Content = "Install", Width = 96, Height = 32, HorizontalContentAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        Grid.SetColumn(installBtn, 2);
        installBtn.Click += async (_, _) =>
        {
            if (_selected is not { } instance) return;
            installBtn.IsEnabled = false;
            installBtn.Content = "Installing...";
            try
            {
                var file = await Catalog().LatestFileAsync(hit, AddContentKind, instance.McVersion, instance.Loader);
                if (file == null) throw new InvalidOperationException($"No release found for Minecraft {instance.McVersion} and loader {instance.Loader}.");
                Directory.CreateDirectory(_addContentTargetDir);
                string savedFile = await Catalog().InstallAsync(file, _addContentTargetDir, SharedContentService.Instance);
                installBtn.Content = "✓ Installed";
                ManageStatus.Text = $"Installed {Path.GetFileName(savedFile)}!";
            }
            catch (Exception ex)
            {
                installBtn.IsEnabled = true;
                installBtn.Content = "Retry";
                AddContentStatus.Text = $"Installation failed: {ex.Message}";
            }
        };
        grid.Children.Add(installBtn);

        return new Border { Background = new SolidColorBrush(Color.Parse("#1B1C20")), CornerRadius = new CornerRadius(8), Padding = new Thickness(12), Child = grid };
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
