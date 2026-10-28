using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Automation;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Input.Platform;
using Avalonia.Interactivity;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Platform;
using Avalonia.Platform.Storage;
using Avalonia.Threading;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

public sealed class ModpackModItem
{
    public string FileName { get; set; } = "";
    public string FilePath { get; set; } = "";
    public string BaseName { get; set; } = "";
    public bool Enabled { get; set; }
    public string DisplayName { get; set; } = "";
    public string Version { get; set; } = "";
    public string Author { get; set; } = "";
    public string Description { get; set; } = "";
    public string Environment { get; set; } = "Client & Server";
    public byte[]? IconBytes { get; set; }
    public string? Sha1 { get; set; }
    public uint? Fingerprint { get; set; }
    public bool HasUpdate { get; set; }
    public ContentFile? UpdateFile { get; set; }
    public string? ProjectId { get; set; }
    public string? Provider { get; set; }
    public bool IsSelected { get; set; }
}

public sealed class ModpackContentItemInfo
{
    public string Name { get; set; } = "";
    public string Path { get; set; } = "";
    public bool Enabled { get; set; }
    public string Type { get; set; } = "";
    public string? Description { get; set; }
    public byte[]? IconBytes { get; set; }
}

/// <summary>The Modpacks tab: My Modpacks (cards), Browse (Modrinth) and one instance's page. Instances start from here only.</summary>
public partial class ModpacksView : UserControl
{
    private static readonly HttpClient Http = ModrinthModpacks.CreateClient();
    private static readonly string[] LoaderNames = { "Fabric", "Quilt", "Forge", "NeoForge", "Vanilla" }; // order of Modpacks.Loaders
    private static readonly string[] GameVersions = GameVersionPolicy.ModrinthGameVersions;
    private static readonly (string Label, string Index)[] BrowseSorts =
        { ("Most downloads", "downloads"), ("Relevance", "relevance"), ("Most follows", "follows"), ("Newest", "newest"), ("Recently updated", "updated") };
    private static readonly Dictionary<string, Bitmap?> RemoteIcons = new();
    private static readonly Dictionary<string, (DateTime Stamp, ModpackContentItemInfo Info)> _contentInfoCache = new(StringComparer.OrdinalIgnoreCase);
    private static readonly Dictionary<int, WriteableBitmap> _worldPlaceholders = new();
    private static WriteableBitmap? _shaderFallback;
    private static WriteableBitmap? _rpFallback;
    private static WriteableBitmap? _dpFallback;
    private static WriteableBitmap? _modFallback;

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

    private List<ModpackModItem> _instanceMods = new();
    private string _currentSubTab = "mods";
    private CancellationTokenSource? _updateCheckCts;
    private CancellationTokenSource? _modDetailsCts;
    private ModSearchItem? _currentDetailProject;
    private List<ProjectVersionItem> _currentDetailVersions = new();
    private bool _updatingAll;

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
        var mine = _instances.Where(i => !ExperiencesService.IsExperienceInstance(i.Id)).ToList();
        var list = mine.Where(i => (loader == null || i.Loader == loader) && i.Name.Contains(query, StringComparison.OrdinalIgnoreCase));
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
            Status.Text = mine.Count == 0 ? "No modpacks yet. Find one in Browse, import a .mrpack file, or create your own."
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
            Title = "Import a modpack (.mrpack or CurseForge .zip)",
            FileTypeFilter = new[]
            {
                new FilePickerFileType("Modpack packages (*.mrpack, *.zip)") { Patterns = new[] { "*.mrpack", "*.zip" } },
                new FilePickerFileType("Modrinth modpack (*.mrpack)") { Patterns = new[] { "*.mrpack" } },
                new FilePickerFileType("CurseForge modpack (*.zip)") { Patterns = new[] { "*.zip" } }
            }
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
            if (file.EndsWith(".zip", StringComparison.OrdinalIgnoreCase) && CurseForgeModpack.IsCurseForgeModpack(file))
            {
                Report("Reading CurseForge modpack " + System.IO.Path.GetFileName(file) + "...");
                var cfInstance = await ModpackLauncher.InstallCurseForgeAsync(DataDirectory, file, Catalog(), null, null, Report, CancellationToken.None);
                await LoadAsync();
                Report($"Imported {cfInstance.Name}.");
                return;
            }
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

        var actions = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto,Auto"), Margin = new Thickness(0, 4, 0, 0) };
        string? exe = ExternalInstances.FindExecutable(instance.Launcher, instance.DataRoot);
        bool canOpen = ExternalInstances.CanLaunch(instance, exe) || (instance.Launcher == ExternalLauncher.Modrinth && exe != null);
        string openLabel = instance.Launcher == ExternalLauncher.Modrinth ? "Open App" : $"Open in {launcherName}";
        void OpenInLauncher()
        {
            if (exe == null) return;
            try
            {
                Process.Start(ExternalInstances.LaunchCommand(instance, exe));
                Status.Text = instance.Launcher == ExternalLauncher.Modrinth ? $"Opened {launcherName}." : $"Launched {instance.Name} via {launcherName}.";
            }
            catch (Exception ex) { Status.Text = $"Failed to start {instance.Name}: {ex.Message}"; }
        }

        // Play starts the game here (Lads account, Lads modpack cache); what the pipeline cannot rebuild opens its own launcher instead.
        string? problem = ExternalInstances.DirectLaunchProblem(instance);
        var direct = problem == null ? ExternalInstances.ToModpack(instance) : null;
        bool launching = direct != null && _launching.ContainsKey(direct.Id), running = direct != null && _running.ContainsKey(direct.Id);
        var playBtn = new Button
        {
            Classes = { "launch" },
            Content = launching ? "Cancel" : running ? "Running" : direct != null ? "Play" : openLabel,
            Height = 32,
            HorizontalAlignment = HorizontalAlignment.Stretch,
            HorizontalContentAlignment = HorizontalAlignment.Center,
            IsEnabled = direct != null ? !running || Host?.ModpackSettings.AllowMultiInstance == true : canOpen
        };
        ToolTip.SetShowOnDisabled(playBtn, true);
        if (direct != null)
        {
            ToolTip.SetTip(playBtn, $"Starts {instance.Name} here with your Lads account. Nothing in its {launcherName} folder is changed.");
            playBtn.Click += async (_, _) => await PlayAsync(direct, retry: false);
        }
        else
        {
            ToolTip.SetTip(playBtn, canOpen ? $"Opens {launcherName}: the Lads launcher cannot start this one itself ({problem})."
                : $"The Lads launcher cannot start this one itself ({problem}), and {launcherName} was not found on your system.");
            playBtn.Click += (_, _) =>
            {
                Status.Text = $"{instance.Name}: {problem}, so {launcherName} starts it.";
                OpenInLauncher();
            };
        }
        actions.Children.Add(playBtn);

        if (direct != null && canOpen)
        {
            var openBtn = new Button { Classes = { "icon" }, Content = "↗", Margin = new Thickness(8, 0, 0, 0), Height = 32, Width = 32 };
            ToolTip.SetTip(openBtn, openLabel);
            AutomationProperties.SetName(openBtn, openLabel);
            Grid.SetColumn(openBtn, 1);
            openBtn.Click += (_, _) => OpenInLauncher();
            actions.Children.Add(openBtn);
        }

        var folderBtn = new Button
        {
            Classes = { "icon" },
            Content = "📁",
            Margin = new Thickness(8, 0, 0, 0),
            Height = 32,
            Width = 32
        };
        ToolTip.SetTip(folderBtn, "Open instance directory in File Explorer");
        Grid.SetColumn(folderBtn, 2);
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
    internal void ShowExternal() => Tabs.SelectedItem = ExternalTab;
    internal void ShowManageOverview() => InstanceTabs.SelectedIndex = 0;
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
            {
                if (url.StartsWith("local://theladscore", StringComparison.OrdinalIgnoreCase))
                {
                    string? corePath = FindBundledCoreJar("1.20.1");
                    if (corePath != null && File.Exists(corePath))
                    {
                        var info = FabricModMetadata.ReadJar(corePath, includeIcon: true);
                        if (info?.Icon is { Length: > 0 } iconBytes)
                        {
                            bitmap = new Bitmap(new MemoryStream(iconBytes));
                        }
                    }
                }
                else
                {
                    bitmap = new Bitmap(new MemoryStream(await Http.GetByteArrayAsync(url)));
                }
                RemoteIcons[url] = bitmap;
            }
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

    // ── One instance (CurseForge style) ────────────────────────────────────

    internal void OpenManage(ModpackInstance instance)
    {
        _selected = instance;
        LibraryPanel.IsVisible = false;
        ManagePanel.IsVisible = true;
        ManageArt.Child = Art(instance, 82);
        ManageTitle.Text = instance.Name;
        ManageByline.Text = Byline(instance);
        ManageVersionText.Text = string.IsNullOrEmpty(instance.PackVersion) ? (instance.Source != null ? instance.Source.VersionId : "") : $"v{instance.PackVersion}";
        ManageVersionRow.IsVisible = !string.IsNullOrEmpty(instance.PackVersion) || instance.Source != null;
        ManageLastPlayed.Text = instance.LastPlayedUtc is { } played ? $"▶ {RelativeTime(played)}" : "▶ Never";
        var analytics = InstanceAnalyticsService.GetAnalytics(instance.Directory, instance.Id);
        ManagePlaytime.Text = $"⏱ {InstanceAnalyticsService.FormatPlayTime(analytics.TotalPlaySeconds)}";
        ManageCreated.Text = $"📅 {instance.CreatedUtc.ToLocalTime():d}";
        ManageMcVersion.Text = $"🎮 {instance.McVersion}";
        ManageLoader.Text = $"🛠 {LoaderName(instance.Loader)}{(string.IsNullOrEmpty(instance.LoaderVersion) ? "" : $" - {instance.LoaderVersion}")}";
        NameBox.Text = instance.Name;
        _updatingRam = true;
        RamBox.Value = instance.MaxRamMb;
        RamSlider.Value = Math.Clamp(instance.MaxRamMb / 1024.0, 0, 32);
        UpdateRamDisplay(instance.MaxRamMb);
        _updatingRam = false;

        bool isExperience = ExperiencesService.AllExperiences.Any(e => string.Equals(e.Id, ExperiencesService.CanonicalPackId(instance.Id), StringComparison.OrdinalIgnoreCase));
        if (isExperience)
        {
            instance.LadsCore = true;
            LadsCoreToggle.IsEnabled = false;
            LadsCoreToggle.IsChecked = true;
            LadsCoreUnsupportedReason.Text = "Required for this official Experience.";
            LadsCoreUnsupportedReason.Foreground = new SolidColorBrush(Color.Parse("#52A5E0"));
            LadsCoreUnsupportedReason.IsVisible = true;
        }
        else
        {
            if (!instance.LadsCore.HasValue)
            {
                instance.LadsCore = ModpackCoreSync.ResolveDefaultLadsCore(instance);
                if (!instance.IsExternal)
                {
                    try { Modpacks.Save(instance); } catch { }
                }
            }

            bool supported = ModpackCoreSync.IsSupported(instance, out var reason);
            if (!supported)
            {
                LadsCoreToggle.IsEnabled = false;
                LadsCoreToggle.IsChecked = false;
                LadsCoreUnsupportedReason.Text = reason ?? "Not supported for this version or loader.";
                LadsCoreUnsupportedReason.Foreground = new SolidColorBrush(Color.Parse("#E06C75"));
                LadsCoreUnsupportedReason.IsVisible = true;
            }
            else
            {
                LadsCoreToggle.IsEnabled = true;
                LadsCoreToggle.IsChecked = instance.LadsCore.Value;
                LadsCoreUnsupportedReason.IsVisible = false;
            }
        }

        ManageStatus.Text = "";
        UpdateCard.IsVisible = instance.Source != null;
        VersionBox.ItemsSource = null;

        // Default to Content tab
        InstanceTabs.SelectedIndex = 1;
        OverviewPanel.IsVisible = false;
        ContentPanel.IsVisible = true;
        LogsPanel.IsVisible = false;
        ScreenshotsPanel.IsVisible = false;

        _currentSubTab = "mods";
        UpdateSubTabSelection();
        RenderAllContent();
        RefreshButtons();
        if (instance.Source != null) _ = LoadVersionsAsync(instance);
    }

    private void OpenSettings_Click(object? sender, RoutedEventArgs e)
    {
        InstanceTabs.SelectedIndex = 0;
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

    internal void RefreshButtons()
    {
        if (_selected is not { } i) return;
        bool running = _running.ContainsKey(i.Id), launching = _launching.ContainsKey(i.Id);
        bool allowMulti = Host?.ModpackSettings.AllowMultiInstance == true;
        PlayButton.Content = launching ? "Cancel" : (running && !allowMulti) ? "Running" : "Play";
        PlayButton.IsEnabled = (!running || allowMulti) && !_busy.Contains(i.Id);
        DeleteButton.IsEnabled = UpdateButton.IsEnabled = Idle(i);
    }

    private void InstanceTabs_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (_selected == null || InstanceTabs == null) return;
        int idx = InstanceTabs.SelectedIndex;
        OverviewPanel.IsVisible = idx == 0;
        ContentPanel.IsVisible = idx == 1;
        LogsPanel.IsVisible = idx == 2;
        ScreenshotsPanel.IsVisible = idx == 3;

        if (idx == 1) RenderActiveSubTab();
        else if (idx == 2) LoadLatestLog();
        else if (idx == 3) LoadScreenshots();
    }

    private void SubTab_Click(object? sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tab)
        {
            _currentSubTab = tab;
            UpdateSubTabSelection();
            RenderActiveSubTab();
        }
    }

    private void UpdateSubTabSelection()
    {
        Button[] buttons = { SubTabModsBtn, SubTabDatapacksBtn, SubTabResourcePacksBtn, SubTabShadersBtn, SubTabWorldsBtn };
        foreach (var btn in buttons)
        {
            if ((btn.Tag as string) == _currentSubTab)
            {
                btn.Classes.Clear();
                btn.Classes.Add("launch");
            }
            else
            {
                btn.Classes.Clear();
                btn.Classes.Add("action");
            }
        }

        bool isMods = _currentSubTab == "mods";
        bool isWorlds = _currentSubTab == "worlds";
        ModsTableHeader.IsVisible = isMods;
        GenericTableHeader.IsVisible = !isMods && !isWorlds;
        WorldsTableHeader.IsVisible = isWorlds;
        UpdateAllButton.IsVisible = isMods;
        ContentUpdateStatus.IsVisible = isMods;

        ModsList.IsVisible = isMods;
        DatapacksList.IsVisible = _currentSubTab == "datapacks";
        ResourcePacksList.IsVisible = _currentSubTab == "resourcepacks";
        ShadersList.IsVisible = _currentSubTab == "shaderpacks";
        WorldsList.IsVisible = isWorlds;

        if (!isMods && !isWorlds)
        {
            GenericHeaderTitle.Text = _currentSubTab switch
            {
                "datapacks" => "Data Pack Name",
                "resourcepacks" => "Resource Pack Name",
                "shaderpacks" => "Shader Pack Name",
                _ => "Name"
            };
            GenericHeaderStatus.Text = "Active";
        }

        AddFromFileButton.IsVisible = !isWorlds;
        AddFromFileButton.Content = _currentSubTab switch
        {
            "mods" => "+ From file (.jar)",
            _ => "+ From file (.zip)"
        };
    }

    /// <summary>The Core bundled with this launcher for <paramref name="mcVersion"/>, or null (see ModpackCoreSync).</summary>
    public static string? FindBundledCoreJar(string mcVersion) => ModpackCoreSync.FindBundledJar(mcVersion);

    private void RenderActiveSubTab()
    {
        if (_selected == null) return;
        switch (_currentSubTab)
        {
            case "mods":
                RenderModsTable();
                break;
            case "datapacks":
                RenderOtherContent("datapacks", "DATAPACKS", DatapacksList);
                break;
            case "resourcepacks":
                RenderOtherContent("resourcepacks", "RESOURCE PACKS", ResourcePacksList);
                break;
            case "shaderpacks":
                RenderOtherContent("shaderpacks", "SHADERS", ShadersList);
                break;
            case "worlds":
                RenderWorlds();
                break;
        }
    }

    private void ContentFilter_Changed(object? sender, TextChangedEventArgs e)
    {
        if (_selected == null) return;
        RenderActiveSubTab();
    }

    private void RenderAllContent()
    {
        if (_selected is not { } i) return;
        _ = ScanModsAsync(i);
        UpdateHeaderCounts();
        RenderActiveSubTab();
    }

    private void UpdateHeaderCounts()
    {
        if (_selected is not { } i) return;
        int modsCount = _instanceMods.Count;
        int datapacksCount = SafeCountDirectory(Path.Combine(i.GameDirectory, "datapacks"));
        int resourcePacksCount = SafeCountDirectory(Path.Combine(i.GameDirectory, "resourcepacks"));
        int shadersCount = SafeCountDirectory(Path.Combine(i.GameDirectory, "shaderpacks"));
        int worldsCount = SafeCountDirectory(Path.Combine(i.GameDirectory, "saves"));

        SubTabModsBtn.Content = $"Mods ({modsCount})";
        SubTabDatapacksBtn.Content = $"Data Packs ({datapacksCount})";
        SubTabResourcePacksBtn.Content = $"Resource Packs ({resourcePacksCount})";
        SubTabShadersBtn.Content = $"Shaders ({shadersCount})";
        SubTabWorldsBtn.Content = $"Worlds ({worldsCount})";

        TabContent.Header = $"Content ({modsCount + datapacksCount + resourcePacksCount + shadersCount + worldsCount})";
    }

    private static int SafeCountDirectory(string path)
    {
        try
        {
            if (!Directory.Exists(path)) return 0;
            return Directory.EnumerateFileSystemEntries(path).Count();
        }
        catch { return 0; }
    }

    private async Task ScanModsAsync(ModpackInstance instance)
    {
        string modsDir = Path.Combine(instance.GameDirectory, "mods");
        Directory.CreateDirectory(modsDir);
        var files = Directory.EnumerateFiles(modsDir)
            .Where(f => f.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || f.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase))
            .OrderBy(f => Path.GetFileName(f), StringComparer.OrdinalIgnoreCase)
            .ToList();

        var items = new List<ModpackModItem>();
        await Task.Run(() =>
        {
            foreach (var file in files)
            {
                items.Add(InspectJar(file));
            }
        });

        _instanceMods = items;
        RenderModsTable();
        UpdateHeaderCounts();

        _updateCheckCts?.Cancel();
        var cts = _updateCheckCts = new CancellationTokenSource();
        _ = Task.Run(async () =>
        {
            try
            {
                var sha1Map = new Dictionary<string, ModpackModItem>(StringComparer.OrdinalIgnoreCase);
                foreach (var mod in items)
                {
                    try
                    {
                        string sha1 = ContentCatalog.Sha1(mod.FilePath);
                        mod.Sha1 = sha1;
                        sha1Map[sha1] = mod;
                    }
                    catch { }
                }

                var updates = await Catalog().ModrinthUpdatesAsync(sha1Map.Keys.ToList(), instance.McVersion, instance.Loader, cts.Token);
                int count = 0;
                foreach (var (sha1, updateFile) in updates)
                {
                    if (sha1Map.TryGetValue(sha1, out var mod))
                    {
                        if (updateFile.Sha1 != null && !updateFile.Sha1.Equals(sha1, StringComparison.OrdinalIgnoreCase))
                        {
                            mod.HasUpdate = true;
                            mod.UpdateFile = updateFile;
                            count++;
                        }
                    }
                }

                Dispatcher.UIThread.Post(() =>
                {
                    if (_selected != instance) return;
                    if (count > 0)
                    {
                        UpdateAllBadge.IsVisible = true;
                        UpdateAllBadgeText.Text = count.ToString();
                        ContentUpdateStatus.Text = $"{count} mod update{(count == 1 ? "" : "s")} available";
                    }
                    else
                    {
                        UpdateAllBadge.IsVisible = false;
                        ContentUpdateStatus.Text = "All mods are up to date";
                    }
                    RenderModsTable();
                });
            }
            catch { }
        }, cts.Token);
    }

    private static ModpackModItem InspectJar(string filePath)
    {
        string fileName = Path.GetFileName(filePath);
        bool enabled = !fileName.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase);
        string baseName = enabled ? fileName : fileName[..^".disabled".Length];
        string cleanName = Path.GetFileNameWithoutExtension(baseName);

        var item = new ModpackModItem
        {
            FileName = fileName,
            FilePath = filePath,
            BaseName = baseName,
            Enabled = enabled,
            DisplayName = cleanName,
            Author = "Unknown",
            Environment = "Client & Server"
        };

        try
        {
            var fabric = FabricModMetadata.ReadJar(filePath, includeIcon: true);
            if (fabric != null && fabric.HasMetadata)
            {
                if (!string.IsNullOrWhiteSpace(fabric.Name)) item.DisplayName = fabric.Name;
                item.Version = fabric.Version ?? "";
                if (fabric.Authors != null && fabric.Authors.Count > 0)
                    item.Author = string.Join(", ", fabric.Authors);
                item.Description = fabric.Description ?? "";
                item.Environment = fabric.Environment switch
                {
                    "client" => "Client",
                    "server" => "Server",
                    _ => "Client & Server"
                };
                item.IconBytes = fabric.Icon ?? ModIconService.ReadJarIcon(filePath);
                return item;
            }

            var forge = FabricModMetadata.ReadForgeJar(filePath);
            if (forge != null && forge.HasMetadata)
            {
                if (!string.IsNullOrWhiteSpace(forge.Name)) item.DisplayName = forge.Name;
                item.Version = forge.Version ?? "";
                if (forge.Authors != null && forge.Authors.Count > 0)
                    item.Author = string.Join(", ", forge.Authors);
                item.Description = forge.Description ?? "";
                item.IconBytes = ModIconService.ReadJarIcon(filePath);
                return item;
            }

            using (var stream = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.Read | FileShare.Delete))
            using (var zip = new ZipArchive(stream, ZipArchiveMode.Read))
            {
                var tomlEntry = zip.GetEntry("META-INF/mods.toml") ?? zip.GetEntry("META-INF/neoforge.mods.toml");
                if (tomlEntry != null)
                {
                    using var reader = new StreamReader(tomlEntry.Open(), Encoding.UTF8);
                    string toml = reader.ReadToEnd();
                    var nameMatch = Regex.Match(toml, @"displayName\s*=\s*[""']([^""']+)[""']");
                    if (nameMatch.Success) item.DisplayName = nameMatch.Groups[1].Value;
                    var authorsMatch = Regex.Match(toml, @"authors\s*=\s*[""']([^""']+)[""']");
                    if (authorsMatch.Success) item.Author = authorsMatch.Groups[1].Value;
                    var versionMatch = Regex.Match(toml, @"version\s*=\s*[""']([^""']+)[""']");
                    if (versionMatch.Success) item.Version = versionMatch.Groups[1].Value;
                    var descMatch = Regex.Match(toml, @"description\s*=\s*'''(.*?)'''", RegexOptions.Singleline);
                    if (!descMatch.Success) descMatch = Regex.Match(toml, @"description\s*=\s*[""']([^""']+)[""']");
                    if (descMatch.Success) item.Description = descMatch.Groups[1].Value.Trim();
                }
            }

            item.IconBytes = ModIconService.ReadJarIcon(filePath);
            if (item.IconBytes == null && File.Exists(filePath + ".icon.png"))
            {
                try { item.IconBytes = File.ReadAllBytes(filePath + ".icon.png"); } catch { }
            }
        }
        catch { }

        return item;
    }

    private void RenderModsTable()
    {
        ModsList.Children.Clear();
        if (_selected == null) return;

        string query = (ContentFilterSearch.Text ?? "").Trim();
        var visible = string.IsNullOrEmpty(query)
            ? _instanceMods
            : _instanceMods.Where(m => m.DisplayName.Contains(query, StringComparison.OrdinalIgnoreCase)
                                    || m.BaseName.Contains(query, StringComparison.OrdinalIgnoreCase)
                                    || m.Author.Contains(query, StringComparison.OrdinalIgnoreCase)).ToList();

        if (visible.Count == 0)
        {
            ModsList.Children.Add(new TextBlock
            {
                Text = _instanceMods.Count == 0
                    ? "No mods installed. Click '+ Add Content' to browse and install mods!"
                    : "No mods match your search.",
                Foreground = new SolidColorBrush(Color.Parse("#8F919C")),
                Margin = new Thickness(0, 14, 0, 0)
            });
            return;
        }

        foreach (var item in visible)
        {
            ModsList.Children.Add(CreateModRow(item));
        }
    }

    private Control CreateModRow(ModpackModItem item)
    {
        var rowGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("34,*,140,140,70,80,44") };

        var cb = new CheckBox { IsChecked = item.IsSelected, VerticalAlignment = VerticalAlignment.Center };
        cb.IsCheckedChanged += (_, _) => item.IsSelected = cb.IsChecked == true;
        rowGrid.Children.Add(cb);

        var nameGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*"), Cursor = new Cursor(StandardCursorType.Hand) };
        var iconBorder = new Border
        {
            Width = 36, Height = 36, CornerRadius = new CornerRadius(6), ClipToBounds = true,
            Background = new SolidColorBrush(Color.Parse("#25262B")),
            BorderBrush = new SolidColorBrush(Color.Parse("#26272E")),
            BorderThickness = new Thickness(1),
            Margin = new Thickness(0, 0, 10, 0),
            VerticalAlignment = VerticalAlignment.Center
        };
        if (item.IconBytes == null && File.Exists(item.FilePath + ".icon.png"))
        {
            try { item.IconBytes = File.ReadAllBytes(item.FilePath + ".icon.png"); } catch { }
        }
        if (item.IconBytes != null)
        {
            try { iconBorder.Child = new Image { Source = new Bitmap(new MemoryStream(item.IconBytes)), Stretch = Stretch.UniformToFill }; } catch { }
        }
        if (iconBorder.Child == null)
        {
            iconBorder.Child = new Image { Source = GetModFallbackBitmap(), Stretch = Stretch.UniformToFill };
        }
        nameGrid.Children.Add(iconBorder);

        var titleStack = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
        Grid.SetColumn(titleStack, 1);
        var titleBlock = new TextBlock { Text = item.DisplayName, FontWeight = FontWeight.SemiBold, FontSize = 13, Foreground = Brushes.White, TextTrimming = TextTrimming.CharacterEllipsis };
        var fileBlock = new TextBlock { Text = item.BaseName, FontSize = 11, Foreground = new SolidColorBrush(Color.Parse("#8E909B")), TextTrimming = TextTrimming.CharacterEllipsis };
        titleStack.Children.Add(titleBlock);
        titleStack.Children.Add(fileBlock);
        nameGrid.Children.Add(titleStack);

        nameGrid.PointerPressed += (_, _) => OpenModDetailsFromInstalled(item);
        Grid.SetColumn(nameGrid, 1);
        rowGrid.Children.Add(nameGrid);

        var actionPanel = new StackPanel { VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center };
        Grid.SetColumn(actionPanel, 2);
        if (item.HasUpdate && item.UpdateFile != null)
        {
            var updateBtn = new Button
            {
                Classes = { "cfUpdate" },
                Content = "Update",
                Height = 28,
                Padding = new Thickness(12, 0),
                VerticalContentAlignment = VerticalAlignment.Center
            };
            updateBtn.Click += async (_, _) => await UpdateModAsync(item, updateBtn);
            actionPanel.Children.Add(updateBtn);
        }
        else
        {
            actionPanel.Children.Add(new TextBlock
            {
                Text = "Recently Updated",
                FontSize = 11.5,
                Foreground = new SolidColorBrush(Color.Parse("#7B7E8C")),
                VerticalAlignment = VerticalAlignment.Center
            });
        }
        rowGrid.Children.Add(actionPanel);

        var authorBlock = new TextBlock
        {
            Text = item.Author,
            FontSize = 12,
            Foreground = new SolidColorBrush(Color.Parse("#A5A8B5")),
            VerticalAlignment = VerticalAlignment.Center,
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        Grid.SetColumn(authorBlock, 3);
        rowGrid.Children.Add(authorBlock);

        var envBlock = new TextBlock
        {
            Text = item.Environment == "Client" ? "💻 Client" : item.Environment == "Server" ? "🖧 Server" : "💻 Both",
            FontSize = 11,
            Foreground = new SolidColorBrush(Color.Parse("#8F919B")),
            VerticalAlignment = VerticalAlignment.Center,
            HorizontalAlignment = HorizontalAlignment.Center
        };
        Grid.SetColumn(envBlock, 4);
        rowGrid.Children.Add(envBlock);

        var toggle = new ToggleSwitch
        {
            Classes = { "cfSwitch" },
            IsChecked = item.Enabled,
            VerticalAlignment = VerticalAlignment.Center,
            HorizontalAlignment = HorizontalAlignment.Center,
            OnContent = "",
            OffContent = ""
        };
        toggle.IsCheckedChanged += (_, _) =>
        {
            bool nowEnabled = toggle.IsChecked == true;
            bool isCore = string.Equals(item.BaseName, "theladscore", StringComparison.OrdinalIgnoreCase)
                || item.DisplayName.IndexOf("The Lads Core", StringComparison.OrdinalIgnoreCase) >= 0
                || item.DisplayName.IndexOf("LadsCore", StringComparison.OrdinalIgnoreCase) >= 0;

            if (isCore && !nowEnabled && _selected != null && ExperiencesService.AllExperiences.Any(exp => string.Equals(exp.Id, ExperiencesService.CanonicalPackId(_selected.Id), StringComparison.OrdinalIgnoreCase)))
            {
                ManageStatus.Text = "Required for this official Experience.";
                toggle.IsChecked = true;
                return;
            }

            try
            {
                string oldPath = item.FilePath;
                string newPath = nowEnabled ? (oldPath.EndsWith(".disabled") ? oldPath[..^".disabled".Length] : oldPath)
                                            : (oldPath.EndsWith(".disabled") ? oldPath : oldPath + ".disabled");
                if (File.Exists(oldPath) && oldPath != newPath)
                {
                    File.Move(oldPath, newPath);
                    var oldIcon = oldPath + ".icon.png";
                    var newIcon = newPath + ".icon.png";
                    if (File.Exists(oldIcon))
                    {
                        try { File.Move(oldIcon, newIcon, overwrite: true); } catch { }
                    }
                    item.FilePath = newPath;
                    item.FileName = Path.GetFileName(newPath);
                    item.Enabled = nowEnabled;
                    if (isCore)
                    {
                        LadsCoreToggle.IsChecked = nowEnabled;
                        if (_selected is { } selectedInst && !selectedInst.IsExternal)
                        {
                            selectedInst.LadsCore = nowEnabled;
                            try { Modpacks.Save(selectedInst); } catch { }
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                ManageStatus.Text = $"Could not toggle {item.DisplayName}: {ex.Message}";
                toggle.IsChecked = item.Enabled;
            }
            UpdateHeaderCounts();
        };
        Grid.SetColumn(toggle, 5);
        rowGrid.Children.Add(toggle);

        var delBtn = new Button
        {
            Classes = { "icon", "danger" },
            Content = "🗑",
            Width = 30, Height = 30,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };
        delBtn.Click += async (_, _) =>
        {
            if (Host is { } host && !await host.ConfirmModpackAsync("Delete mod", $"Delete '{item.DisplayName}' from this modpack?", "Delete")) return;
            try
            {
                if (File.Exists(item.FilePath)) SafeFileOps.DeleteToRecycleBin(item.FilePath);
                _instanceMods.Remove(item);
                RenderModsTable();
                UpdateHeaderCounts();
                ManageStatus.Text = $"Removed {item.DisplayName}.";
            }
            catch (Exception ex) { ManageStatus.Text = $"Could not delete {item.DisplayName}: {ex.Message}"; }
        };
        Grid.SetColumn(delBtn, 6);
        rowGrid.Children.Add(delBtn);

        return new Border
        {
            Background = new SolidColorBrush(Color.Parse("#1A1B20")),
            BorderBrush = new SolidColorBrush(Color.Parse("#26272E")),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(6),
            Padding = new Thickness(10, 6),
            Child = rowGrid
        };
    }

    private void ModsSelectAll_Changed(object? sender, RoutedEventArgs e)
    {
        bool isChecked = ModsSelectAllBox.IsChecked == true;
        foreach (var mod in _instanceMods)
        {
            mod.IsSelected = isChecked;
        }
        RenderModsTable();
    }

    private async Task UpdateModAsync(ModpackModItem item, Button? updateBtn = null)
    {
        if (_selected is not { } instance || item.UpdateFile == null) return;
        if (updateBtn != null) { updateBtn.IsEnabled = false; updateBtn.Content = "Updating..."; }
        try
        {
            string modsDir = Path.Combine(instance.GameDirectory, "mods");
            Directory.CreateDirectory(modsDir);
            string newPath = await Catalog().InstallAsync(item.UpdateFile, modsDir, SharedContentService.Instance);
            if (File.Exists(item.FilePath) && Path.GetFullPath(item.FilePath) != Path.GetFullPath(newPath))
            {
                SafeFileOps.DeleteToRecycleBin(item.FilePath);
            }
            ManageStatus.Text = $"Updated {item.DisplayName} to {item.UpdateFile.VersionName}!";
            await ScanModsAsync(instance);
        }
        catch (Exception ex)
        {
            ManageStatus.Text = $"Update failed for {item.DisplayName}: {ex.Message}";
            if (updateBtn != null) { updateBtn.IsEnabled = true; updateBtn.Content = "Retry"; }
        }
    }

    private async void UpdateAll_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is not { } instance || _updatingAll) return;
        var toUpdate = _instanceMods.Where(m => m.HasUpdate && m.UpdateFile != null).ToList();
        if (toUpdate.Count == 0)
        {
            ManageStatus.Text = "All mods are already up to date.";
            return;
        }

        _updatingAll = true;
        UpdateAllButton.IsEnabled = false;
        ContentUpdateStatus.Text = $"Updating {toUpdate.Count} mod(s)...";
        int updated = 0;
        string modsDir = Path.Combine(instance.GameDirectory, "mods");

        try
        {
            foreach (var mod in toUpdate)
            {
                if (mod.UpdateFile == null) continue;
                ContentUpdateStatus.Text = $"Updating {mod.DisplayName}...";
                try
                {
                    string newPath = await Catalog().InstallAsync(mod.UpdateFile, modsDir, SharedContentService.Instance);
                    if (File.Exists(mod.FilePath) && Path.GetFullPath(mod.FilePath) != Path.GetFullPath(newPath))
                    {
                        SafeFileOps.DeleteToRecycleBin(mod.FilePath);
                    }
                    updated++;
                }
                catch (Exception ex)
                {
                    Host?.ModpackLog($"Failed to update {mod.DisplayName}: {ex}");
                }
            }
            ManageStatus.Text = $"Successfully updated {updated} of {toUpdate.Count} mods.";
            await ScanModsAsync(instance);
        }
        finally
        {
            _updatingAll = false;
            UpdateAllButton.IsEnabled = true;
        }
    }

    private void RenderOtherContent(string folder, string typeHeader, StackPanel list)
    {
        list.Children.Clear();
        if (_selected is not { } i) return;
        List<ModpackContentItem> rawItems;
        try { rawItems = Modpacks.Content(i, folder, typeHeader); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { ManageStatus.Text = e.Message; return; }

        string filter = (ContentFilterSearch.Text ?? "").Trim();
        var visible = string.IsNullOrEmpty(filter)
            ? rawItems
            : rawItems.Where(item => item.Name.Contains(filter, StringComparison.OrdinalIgnoreCase)).ToList();

        if (visible.Count == 0)
        {
            list.Children.Add(new TextBlock
            {
                Text = rawItems.Count == 0
                    ? $"No {typeHeader.ToLowerInvariant()} installed. Use '+ Add Content' to browse or '+ From file' to install."
                    : "No items match your filter.",
                Foreground = new SolidColorBrush(Color.Parse("#8F919C")),
                Margin = new Thickness(0, 14, 0, 0)
            });
            return;
        }

        foreach (var item in visible)
        {
            var itemInfo = InspectOtherContentItem(item, folder);

            var rowGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,100,60") };

            var nameGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*"), VerticalAlignment = VerticalAlignment.Center };

            var iconBorder = new Border
            {
                Width = 36, Height = 36, CornerRadius = new CornerRadius(6), ClipToBounds = true,
                Background = new SolidColorBrush(Color.Parse("#25262B")),
                BorderBrush = new SolidColorBrush(Color.Parse("#26272E")),
                BorderThickness = new Thickness(1),
                Margin = new Thickness(0, 0, 10, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            var iconImage = new Image { Width = 36, Height = 36, Stretch = Stretch.UniformToFill };

            if (itemInfo.IconBytes is { Length: > 0 } bytes)
            {
                try { iconImage.Source = new Bitmap(new MemoryStream(bytes)); } catch { }
            }
            if (iconImage.Source == null)
            {
                iconImage.Source = folder switch
                {
                    "shaderpacks" => GetShaderFallbackBitmap(),
                    "resourcepacks" => GetResourcePackFallbackBitmap(),
                    _ => GetDataPackFallbackBitmap()
                };

                if (File.Exists(item.Path))
                {
                    string filePath = item.Path;
                    _ = Task.Run(async () =>
                    {
                        try
                        {
                            string hash = await ComputeSha1Async(filePath);
                            string url = $"https://api.modrinth.com/v2/version_file/{hash}?algorithm=sha1";
                            using var resp = await Http.GetAsync(url);
                            if (resp.IsSuccessStatusCode)
                            {
                                string json = await resp.Content.ReadAsStringAsync();
                                using var doc = JsonDocument.Parse(json);
                                if (doc.RootElement.TryGetProperty("project_id", out var pidProp))
                                {
                                    string pid = pidProp.GetString()!;
                                    using var pResp = await Http.GetAsync($"https://api.modrinth.com/v2/project/{pid}");
                                    if (pResp.IsSuccessStatusCode)
                                    {
                                        string pJson = await pResp.Content.ReadAsStringAsync();
                                        using var pDoc = JsonDocument.Parse(pJson);
                                        if (pDoc.RootElement.TryGetProperty("icon_url", out var iconProp) && iconProp.GetString() is { Length: > 0 } iconUrl)
                                        {
                                            var imgBytes = await Http.GetByteArrayAsync(iconUrl);
                                            try { await File.WriteAllBytesAsync(filePath + ".icon.png", imgBytes); } catch { }
                                            itemInfo.IconBytes = imgBytes;
                                            Dispatcher.UIThread.Post(() =>
                                            {
                                                try { iconImage.Source = new Bitmap(new MemoryStream(imgBytes)); } catch { }
                                            });
                                        }
                                    }
                                }
                            }
                        }
                        catch { }
                    });
                }
            }
            iconBorder.Child = iconImage;

            var textStack = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
            var titleBlock = new TextBlock
            {
                Text = item.Name,
                FontWeight = FontWeight.SemiBold,
                FontSize = 13,
                Foreground = Brushes.White,
                TextTrimming = TextTrimming.CharacterEllipsis
            };
            textStack.Children.Add(titleBlock);

            string subtext = !string.IsNullOrWhiteSpace(itemInfo.Description)
                ? itemInfo.Description
                : Path.GetFileName(item.Path);
            var subBlock = new TextBlock
            {
                Text = subtext,
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.Parse("#8E909B")),
                TextTrimming = TextTrimming.CharacterEllipsis,
                MaxLines = 1
            };
            textStack.Children.Add(subBlock);

            Grid.SetColumn(textStack, 1);
            nameGrid.Children.Add(iconBorder);
            nameGrid.Children.Add(textStack);
            rowGrid.Children.Add(nameGrid);

            var toggle = new ToggleSwitch
            {
                Classes = { "cfSwitch" },
                IsChecked = item.Enabled,
                VerticalAlignment = VerticalAlignment.Center,
                HorizontalAlignment = HorizontalAlignment.Center,
                OnContent = "",
                OffContent = ""
            };
            toggle.IsCheckedChanged += (_, _) =>
            {
                try { Modpacks.SetContentEnabled(item, toggle.IsChecked == true); }
                catch (Exception e) { ManageStatus.Text = $"Could not switch {item.Name}: {e.Message}"; }
                RenderOtherContent(folder, typeHeader, list);
                UpdateHeaderCounts();
            };
            Grid.SetColumn(toggle, 1);
            rowGrid.Children.Add(toggle);

            var delBtn = new Button
            {
                Classes = { "icon", "danger" },
                Content = "🗑",
                Width = 28, Height = 28,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            delBtn.Click += async (_, _) =>
            {
                if (Host is { } host && !await host.ConfirmModpackAsync("Delete item", $"Delete '{item.Name}' from this modpack?", "Delete")) return;
                try
                {
                    Modpacks.DeleteContent(item);
                    RenderOtherContent(folder, typeHeader, list);
                    UpdateHeaderCounts();
                }
                catch (Exception e) { ManageStatus.Text = $"Could not delete {item.Name}: {e.Message}"; }
            };
            Grid.SetColumn(delBtn, 2);
            rowGrid.Children.Add(delBtn);

            var border = new Border
            {
                Background = new SolidColorBrush(Color.Parse("#1A1B20")),
                BorderBrush = new SolidColorBrush(Color.Parse("#26272E")),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(12, 6),
                Child = rowGrid
            };
            list.Children.Add(border);
        }
    }

    private static ModpackContentItemInfo InspectOtherContentItem(ModpackContentItem item, string folder)
    {
        DateTime stamp = default;
        try { stamp = File.Exists(item.Path) ? File.GetLastWriteTimeUtc(item.Path) : Directory.Exists(item.Path) ? Directory.GetLastWriteTimeUtc(item.Path) : default; }
        catch { }

        if (_contentInfoCache.TryGetValue(item.Path, out var cached) && cached.Stamp == stamp)
            return cached.Info;

        var info = new ModpackContentItemInfo
        {
            Name = item.Name,
            Path = item.Path,
            Enabled = item.Enabled,
            Type = item.Type
        };

        try
        {
            string sidecar = item.Path + ".icon.png";
            if (File.Exists(sidecar))
            {
                try { info.IconBytes = File.ReadAllBytes(sidecar); } catch { }
            }

            if (File.Exists(item.Path))
            {
                string ext = Path.GetExtension(item.Path);
                if (ext.Equals(".zip", StringComparison.OrdinalIgnoreCase) ||
                    item.Path.EndsWith(".zip.disabled", StringComparison.OrdinalIgnoreCase) ||
                    ext.Equals(".jar", StringComparison.OrdinalIgnoreCase) ||
                    item.Path.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase))
                {
                    using var stream = new FileStream(item.Path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
                    using var archive = new ZipArchive(stream, ZipArchiveMode.Read);

                    if (info.IconBytes == null)
                    {
                        var imgEntry = archive.Entries.FirstOrDefault(e =>
                            e.FullName.Equals("pack.png", StringComparison.OrdinalIgnoreCase) ||
                            e.FullName.Equals("icon.png", StringComparison.OrdinalIgnoreCase) ||
                            e.FullName.Equals("screen.png", StringComparison.OrdinalIgnoreCase) ||
                            e.FullName.EndsWith("/pack.png", StringComparison.OrdinalIgnoreCase) ||
                            e.FullName.EndsWith("/icon.png", StringComparison.OrdinalIgnoreCase) ||
                            e.FullName.EndsWith("/screen.png", StringComparison.OrdinalIgnoreCase));

                        if (imgEntry != null && imgEntry.Length is > 0 and <= 4 * 1024 * 1024)
                        {
                            using var ms = new MemoryStream();
                            using var es = imgEntry.Open();
                            es.CopyTo(ms);
                            info.IconBytes = ms.ToArray();
                        }
                    }

                    var mcmeta = archive.GetEntry("pack.mcmeta");
                    if (mcmeta != null && mcmeta.Length is > 0 and <= 64 * 1024)
                    {
                        using var es = mcmeta.Open();
                        using var reader = new StreamReader(es, Encoding.UTF8);
                        string json = reader.ReadToEnd();
                        using var doc = JsonDocument.Parse(json);
                        if (doc.RootElement.TryGetProperty("pack", out var packProp) &&
                            packProp.TryGetProperty("description", out var descProp))
                        {
                            string desc = descProp.ValueKind == JsonValueKind.String
                                ? descProp.GetString() ?? ""
                                : descProp.ToString();
                            info.Description = Regex.Replace(desc, "§[0-9a-fk-or]", "").Trim();
                        }
                    }
                }
            }
            else if (Directory.Exists(item.Path))
            {
                if (info.IconBytes == null)
                {
                    string[] candidates = { "pack.png", "icon.png", "screen.png", Path.Combine("shaders", "pack.png"), Path.Combine("shaders", "icon.png") };
                    foreach (var c in candidates)
                    {
                        string p = Path.Combine(item.Path, c);
                        if (File.Exists(p))
                        {
                            try { info.IconBytes = File.ReadAllBytes(p); } catch { }
                            break;
                        }
                    }
                }

                string metaPath = Path.Combine(item.Path, "pack.mcmeta");
                if (File.Exists(metaPath))
                {
                    string json = File.ReadAllText(metaPath);
                    using var doc = JsonDocument.Parse(json);
                    if (doc.RootElement.TryGetProperty("pack", out var packProp) &&
                        packProp.TryGetProperty("description", out var descProp))
                    {
                        string desc = descProp.ValueKind == JsonValueKind.String
                            ? descProp.GetString() ?? ""
                            : descProp.ToString();
                        info.Description = Regex.Replace(desc, "§[0-9a-fk-or]", "").Trim();
                    }
                }
            }
        }
        catch { }

        _contentInfoCache[item.Path] = (stamp, info);
        return info;
    }

    private void RenderWorlds()
    {
        WorldsList.Children.Clear();
        if (_selected is not { } i) return;
        string savesDir = Path.Combine(i.GameDirectory, "saves");
        if (!Directory.Exists(savesDir))
        {
            WorldsList.Children.Add(new TextBlock { Text = "No worlds found. Launch the modpack to create worlds.", Foreground = new SolidColorBrush(Color.Parse("#8F919C")), Margin = new Thickness(0, 14, 0, 0) });
            return;
        }

        var catalog = WorldCatalogService.Scan(new[] { new WorldSource(i.Name, "Modpack", i.GameDirectory, i.McVersion) });
        string filter = (ContentFilterSearch.Text ?? "").Trim();
        var worlds = string.IsNullOrEmpty(filter)
            ? catalog.Worlds
            : catalog.Worlds.Where(w => w.Name.Contains(filter, StringComparison.OrdinalIgnoreCase) || w.Folder.Contains(filter, StringComparison.OrdinalIgnoreCase)).ToList();

        if (worlds.Count == 0)
        {
            WorldsList.Children.Add(new TextBlock { Text = "No worlds match your filter.", Foreground = new SolidColorBrush(Color.Parse("#8F919C")), Margin = new Thickness(0, 14, 0, 0) });
            return;
        }

        foreach (var world in worlds)
        {
            var rowGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,140,80,60") };

            var nameGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*"), VerticalAlignment = VerticalAlignment.Center };

            var iconBorder = new Border
            {
                Width = 36, Height = 36, CornerRadius = new CornerRadius(6), ClipToBounds = true,
                Background = new SolidColorBrush(Color.Parse("#25262B")),
                BorderBrush = new SolidColorBrush(Color.Parse("#26272E")),
                BorderThickness = new Thickness(1),
                Margin = new Thickness(0, 0, 10, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            var iconImage = new Image { Width = 36, Height = 36, Stretch = Stretch.UniformToFill };
            RenderOptions.SetBitmapInterpolationMode(iconImage, BitmapInterpolationMode.None);

            if (world.Icon != null && File.Exists(world.Icon))
            {
                try { iconImage.Source = new Bitmap(world.Icon); } catch { }
            }
            if (iconImage.Source == null)
            {
                iconImage.Source = GetWorldGrassBlockBitmap(world.Name);
            }
            iconBorder.Child = iconImage;

            var nameStack = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
            nameStack.Children.Add(new TextBlock
            {
                Text = world.Name,
                FontWeight = FontWeight.SemiBold,
                FontSize = 13,
                Foreground = Brushes.White,
                TextTrimming = TextTrimming.CharacterEllipsis
            });

            string sub = Path.GetFileName(world.Folder);
            if (!string.IsNullOrEmpty(world.GameMode)) sub += $"  ·  {world.GameMode}";
            if (!string.IsNullOrEmpty(world.Version)) sub += $"  ·  {world.Version}";
            nameStack.Children.Add(new TextBlock
            {
                Text = sub,
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.Parse("#8E909B")),
                TextTrimming = TextTrimming.CharacterEllipsis
            });

            Grid.SetColumn(nameStack, 1);
            nameGrid.Children.Add(iconBorder);
            nameGrid.Children.Add(nameStack);
            rowGrid.Children.Add(nameGrid);

            var playedBlock = new TextBlock
            {
                Text = RelativeTime(world.LastPlayed),
                FontSize = 11.5,
                Foreground = new SolidColorBrush(Color.Parse("#8F919C")),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(playedBlock, 1);
            rowGrid.Children.Add(playedBlock);

            var openBtn = new Button
            {
                Classes = { "action" },
                Content = "Open",
                Height = 28,
                Padding = new Thickness(12, 0),
                VerticalAlignment = VerticalAlignment.Center,
                HorizontalAlignment = HorizontalAlignment.Center
            };
            string folderPath = Directory.Exists(world.Folder) ? world.Folder : Path.Combine(savesDir, world.Folder);
            openBtn.Click += (_, _) =>
            {
                if (Directory.Exists(folderPath)) Process.Start(new ProcessStartInfo(folderPath) { UseShellExecute = true });
            };
            Grid.SetColumn(openBtn, 2);
            rowGrid.Children.Add(openBtn);

            var delBtn = new Button
            {
                Classes = { "icon", "danger" },
                Content = "🗑",
                Width = 28,
                Height = 28,
                VerticalAlignment = VerticalAlignment.Center,
                HorizontalAlignment = HorizontalAlignment.Center
            };
            delBtn.Click += async (_, _) =>
            {
                if (Host is { } host && !await host.ConfirmModpackAsync("Delete world", $"Delete world '{world.Name}'? This cannot be undone.", "Delete")) return;
                try
                {
                    if (Directory.Exists(folderPath)) SafeFileOps.DeleteToRecycleBin(folderPath);
                    RenderWorlds();
                    UpdateHeaderCounts();
                }
                catch (Exception ex) { ManageStatus.Text = $"Could not delete world: {ex.Message}"; }
            };
            Grid.SetColumn(delBtn, 3);
            rowGrid.Children.Add(delBtn);

            var border = new Border
            {
                Background = new SolidColorBrush(Color.Parse("#1A1B20")),
                BorderBrush = new SolidColorBrush(Color.Parse("#26272E")),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(10, 6),
                Child = rowGrid
            };
            WorldsList.Children.Add(border);
        }
    }

    private static async Task<string> ComputeSha1Async(string filePath)
    {
        using var stream = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        using var sha1 = SHA1.Create();
        var hash = await sha1.ComputeHashAsync(stream);
        return Convert.ToHexString(hash).ToLowerInvariant();
    }

    private static WriteableBitmap GetShaderFallbackBitmap()
    {
        if (_shaderFallback != null) return _shaderFallback;
        var raw = new byte[32 * 32 * 4];
        for (int y = 0; y < 32; y++)
        for (int x = 0; x < 32; x++)
        {
            double r, g, b;
            if (y < 20)
            {
                double t = y / 20.0;
                r = 34 + t * (234 - 34);
                g = 21 + t * (88 - 21);
                b = 81 + t * (12 - 81);
                double dx = x - 16, dy = y - 8;
                double dist = Math.Sqrt(dx * dx + dy * dy);
                if (dist < 3.5) { r = 255; g = 253; b = 231; }
                else if (dist < 6.5)
                {
                    double blend = (6.5 - dist) / 3.0;
                    r = r * (1 - blend) + 254 * blend;
                    g = g * (1 - blend) + 215 * blend;
                    b = b * (1 - blend) + 100 * blend;
                }
                double rayDist = Math.Abs((x - 16) - (y - 8) * 0.7);
                if (rayDist < 2.5 && y > 8)
                {
                    double rayIntensity = (1.0 - rayDist / 2.5) * 0.35;
                    r = Math.Min(255, r + 255 * rayIntensity);
                    g = Math.Min(255, g + 220 * rayIntensity);
                    b = Math.Min(255, b + 100 * rayIntensity);
                }
            }
            else
            {
                double t = (y - 20) / 12.0;
                r = 15 + (1 - t) * 60;
                g = 23 + (1 - t) * 40;
                b = 42 + (1 - t) * 60;
                if (Math.Abs(x - 16) < 3 + (y - 20) * 0.4)
                {
                    double wave = Math.Sin((x + y * 2) * 1.5) * 0.5 + 0.5;
                    r = Math.Min(255, r + 140 * wave);
                    g = Math.Min(255, g + 80 * wave);
                    b = Math.Min(255, b + 30 * wave);
                }
            }
            int i = (y * 32 + x) * 4;
            raw[i] = (byte)Math.Clamp(b, 0, 255);
            raw[i + 1] = (byte)Math.Clamp(g, 0, 255);
            raw[i + 2] = (byte)Math.Clamp(r, 0, 255);
            raw[i + 3] = 255;
        }
        var bmp = new WriteableBitmap(new PixelSize(32, 32), new Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Opaque);
        using (var buffer = bmp.Lock())
            for (int y = 0; y < 32; y++) Marshal.Copy(raw, y * 128, buffer.Address + y * buffer.RowBytes, 128);
        return _shaderFallback = bmp;
    }

    private static WriteableBitmap GetResourcePackFallbackBitmap()
    {
        if (_rpFallback != null) return _rpFallback;
        var raw = new byte[32 * 32 * 4];
        string[] grass = { "#5DA83C", "#6DBA45", "#4F9632", "#7CC553" };
        string[] dirt = { "#8C5B37", "#7C4F2F", "#9A6841", "#6D4427" };
        string[] stone = { "#757575", "#616161", "#424242", "#9E9E9E" };
        string[] wood = { "#A27646", "#8F6438", "#B58450", "#76512B" };
        string[] cyan = { "#06B6D4", "#22D3EE", "#0891B2", "#67E8F9" };

        for (int y = 0; y < 32; y++)
        for (int x = 0; x < 32; x++)
        {
            Color color;
            if (x == 0 || y == 0 || x == 31 || y == 31 || x == 16 || y == 16)
            {
                color = Color.Parse("#1A1B20");
            }
            else if (x < 16 && y < 16)
            {
                double n = Rand(1, x, y);
                int edge = 5 + (Rand(10, x, 0) > 0.5 ? 1 : 0);
                color = Color.Parse(y < edge ? grass[(int)(n * grass.Length)] : dirt[(int)(n * dirt.Length)]);
            }
            else if (x >= 16 && y < 16)
            {
                double n = Rand(2, x, y);
                color = Color.Parse(stone[(int)(n * stone.Length)]);
            }
            else if (x < 16 && y >= 16)
            {
                double n = Rand(3, x, y);
                bool plankLine = (y % 4 == 0) || (y < 20 && x == 8) || (y >= 20 && x == 4);
                color = plankLine ? Color.Parse("#53381B") : Color.Parse(wood[(int)(n * wood.Length)]);
            }
            else
            {
                double n = Rand(4, x, y);
                bool isDiamond = (x is 20 or 21 or 25 or 26 && y is 20 or 21 or 26 or 27) || (x is 23 && y is 23 or 24);
                color = isDiamond ? Color.Parse(cyan[(int)(n * cyan.Length)]) : Color.Parse(stone[(int)(n * stone.Length)]);
            }

            int i = (y * 32 + x) * 4;
            raw[i] = color.B; raw[i + 1] = color.G; raw[i + 2] = color.R; raw[i + 3] = 255;
        }
        var bmp = new WriteableBitmap(new PixelSize(32, 32), new Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Opaque);
        using (var buffer = bmp.Lock())
            for (int y = 0; y < 32; y++) Marshal.Copy(raw, y * 128, buffer.Address + y * buffer.RowBytes, 128);
        return _rpFallback = bmp;
    }

    private static WriteableBitmap GetDataPackFallbackBitmap()
    {
        if (_dpFallback != null) return _dpFallback;
        var raw = new byte[32 * 32 * 4];
        for (int y = 0; y < 32; y++)
        for (int x = 0; x < 32; x++)
        {
            double dx = x - 16, dy = y - 16;
            double dist = Math.Sqrt(dx * dx + dy * dy);
            Color color;
            if (x is < 2 or > 29 || y is < 2 or > 29)
                color = Color.Parse("#382318");
            else if (x is 2 or 29 || y is 2 or 29)
                color = Color.Parse("#8B5A3C");
            else if (dist < 4.5)
                color = Color.Parse("#93C5FD");
            else if (dist < 8.5)
                color = (int)(dist * 2) % 2 == 0 ? Color.Parse("#3B82F6") : Color.Parse("#1D4ED8");
            else if (Math.Abs(dx) < 2 || Math.Abs(dy) < 2)
                color = Color.Parse("#EF4444");
            else
                color = Color.Parse((x + y) % 2 == 0 ? "#5E3924" : "#4A2D1B");

            int i = (y * 32 + x) * 4;
            raw[i] = color.B; raw[i + 1] = color.G; raw[i + 2] = color.R; raw[i + 3] = 255;
        }
        var bmp = new WriteableBitmap(new PixelSize(32, 32), new Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Opaque);
        using (var buffer = bmp.Lock())
            for (int y = 0; y < 32; y++) Marshal.Copy(raw, y * 128, buffer.Address + y * buffer.RowBytes, 128);
        return _dpFallback = bmp;
    }

    private static WriteableBitmap GetModFallbackBitmap()
    {
        if (_modFallback != null) return _modFallback;
        var raw = new byte[32 * 32 * 4];
        for (int y = 0; y < 32; y++)
        for (int x = 0; x < 32; x++)
        {
            double dx = x - 16, dy = y - 16;
            double dist = Math.Sqrt(dx * dx + dy * dy);
            Color color;
            if (x is < 2 or > 29 || y is < 2 or > 29)
                color = Color.Parse("#18191E");
            else if (dist < 4.0)
                color = Color.Parse("#C084FC");
            else if (dist < 7.5)
                color = Color.Parse("#818CF8");
            else if ((Math.Abs(dx) < 2.5 && dy < 0) || (Math.Abs(dy) < 2.5 && dx > 0) || (Math.Abs(dx) < 2.5 && dy > 0) || (Math.Abs(dy) < 2.5 && dx < 0))
                color = Color.Parse("#A855F7");
            else
                color = Color.Parse((x + y) % 2 == 0 ? "#25262E" : "#212228");

            int i = (y * 32 + x) * 4;
            raw[i] = color.B; raw[i + 1] = color.G; raw[i + 2] = color.R; raw[i + 3] = 255;
        }
        var bmp = new WriteableBitmap(new PixelSize(32, 32), new Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Opaque);
        using (var buffer = bmp.Lock())
            for (int y = 0; y < 32; y++) Marshal.Copy(raw, y * 128, buffer.Address + y * buffer.RowBytes, 128);
        return _modFallback = bmp;
    }

    private static WriteableBitmap GetWorldGrassBlockBitmap(string name)
    {
        int seed = 0;
        foreach (char c in name) seed = seed * 31 + c;
        seed &= 7;
        if (_worldPlaceholders.TryGetValue(seed, out var cached)) return cached;
        string[] grass = { "#6DBA45", "#5DA83C", "#4F9632", "#7CC553" }, dirt = { "#8C5B37", "#7C4F2F", "#9A6841", "#6D4427" };
        var raw = new byte[16 * 16 * 4];
        for (int y = 0; y < 16; y++)
        for (int x = 0; x < 16; x++)
        {
            double n = Rand(seed, x, y);
            int edge = 3 + (Rand(seed + 40, x, 0) > 0.5 ? 1 : 0) + (Rand(seed + 80, x, 1) > 0.8 ? 1 : 0);
            var color = Color.Parse(y < edge ? grass[(int)(n * grass.Length)] : n > 0.94 ? "#A77A52" : dirt[(int)(n * dirt.Length)]);
            int i = (y * 16 + x) * 4;
            raw[i] = color.B; raw[i + 1] = color.G; raw[i + 2] = color.R; raw[i + 3] = 255;
        }
        var bitmap = new WriteableBitmap(new PixelSize(16, 16), new Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Opaque);
        using (var buffer = bitmap.Lock())
            for (int y = 0; y < 16; y++) Marshal.Copy(raw, y * 64, buffer.Address + y * buffer.RowBytes, 64);
        return _worldPlaceholders[seed] = bitmap;
    }

    private static double Rand(int seed, int x, int y)
    {
        uint h = unchecked((uint)(seed * 374761393 + x * 668265263 + y * 1274126177));
        h = unchecked((h ^ (h >> 13)) * 1274126177);
        return ((h ^ (h >> 16)) & 0xFFFFFF) / (double)0x1000000;
    }

    private void LoadLatestLog()
    {
        if (_selected is not { } instance) return;
        string logPath = Path.Combine(instance.GameDirectory, "logs", "latest.log");
        if (File.Exists(logPath))
        {
            try
            {
                using var stream = new FileStream(logPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                using var reader = new StreamReader(stream, Encoding.UTF8);
                string text = reader.ReadToEnd();
                LogsText.Text = text;
            }
            catch (Exception ex) { LogsText.Text = $"Could not read log: {ex.Message}"; }
        }
        else
        {
            LogsText.Text = "No logs generated yet. Play the modpack to create logs.";
        }
    }

    private void LogsFilter_Changed(object? sender, TextChangedEventArgs e)
    {
        string filter = (LogsFilterBox.Text ?? "").Trim();
        if (string.IsNullOrEmpty(filter))
        {
            LoadLatestLog();
            return;
        }
        if (_selected is not { } instance) return;
        string logPath = Path.Combine(instance.GameDirectory, "logs", "latest.log");
        if (!File.Exists(logPath)) return;
        try
        {
            var lines = File.ReadAllLines(logPath).Where(l => l.Contains(filter, StringComparison.OrdinalIgnoreCase));
            LogsText.Text = string.Join(Environment.NewLine, lines);
        }
        catch { }
    }

    private void CopyLog_Click(object? sender, RoutedEventArgs e)
    {
        if (TopLevel.GetTopLevel(this) is { } top && top.Clipboard != null)
        {
            _ = top.Clipboard.SetTextAsync(LogsText.Text ?? "");
            ManageStatus.Text = "Log copied to clipboard.";
        }
    }

    private void OpenLogFile_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is not { } instance) return;
        string logPath = Path.Combine(instance.GameDirectory, "logs", "latest.log");
        if (File.Exists(logPath)) Process.Start(new ProcessStartInfo(logPath) { UseShellExecute = true });
        else Directory.CreateDirectory(Path.GetDirectoryName(logPath)!);
    }

    private void LoadScreenshots()
    {
        if (_selected is not { } instance) return;
        ScreenshotsGallery.Children.Clear();
        string ssDir = Path.Combine(instance.GameDirectory, "screenshots");
        if (!Directory.Exists(ssDir))
        {
            ScreenshotsHeader.Text = "SCREENSHOTS (0)";
            return;
        }
        var files = Directory.EnumerateFiles(ssDir)
            .Where(f => f.EndsWith(".png", StringComparison.OrdinalIgnoreCase) || f.EndsWith(".jpg", StringComparison.OrdinalIgnoreCase))
            .OrderByDescending(f => File.GetLastWriteTimeUtc(f))
            .ToList();
        ScreenshotsHeader.Text = $"SCREENSHOTS ({files.Count})";

        foreach (var file in files)
        {
            try
            {
                var card = new Border
                {
                    Width = 180, Height = 140, Margin = new Thickness(0, 0, 12, 12),
                    CornerRadius = new CornerRadius(6), ClipToBounds = true,
                    Background = new SolidColorBrush(Color.Parse("#202127")),
                    BorderBrush = new SolidColorBrush(Color.Parse("#2F3038")),
                    BorderThickness = new Thickness(1),
                    Cursor = new Cursor(StandardCursorType.Hand)
                };
                var img = new Image { Source = new Bitmap(file), Stretch = Stretch.UniformToFill };
                card.Child = img;
                card.PointerPressed += (_, _) =>
                {
                    Process.Start(new ProcessStartInfo(file) { UseShellExecute = true });
                };
                ScreenshotsGallery.Children.Add(card);
            }
            catch { }
        }
    }

    private void OpenScreenshotsFolder_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is not { } instance) return;
        string ssDir = Path.Combine(instance.GameDirectory, "screenshots");
        Directory.CreateDirectory(ssDir);
        Process.Start(new ProcessStartInfo(ssDir) { UseShellExecute = true });
    }

    private void RefreshCurrentContent_Click(object? sender, RoutedEventArgs e) => RenderAllContent();

    private void OpenCurrentFolder_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is not { } instance) return;
        string folderName = _currentSubTab switch
        {
            "datapacks" => "datapacks",
            "resourcepacks" => "resourcepacks",
            "shaderpacks" => "shaderpacks",
            "worlds" => "saves",
            _ => "mods"
        };
        string targetDir = Path.Combine(instance.GameDirectory, folderName);
        Directory.CreateDirectory(targetDir);
        Process.Start(new ProcessStartInfo(targetDir) { UseShellExecute = true });
    }

    private async void AddCurrentFromFile_Click(object? sender, RoutedEventArgs e)
    {
        string folderName = _currentSubTab switch
        {
            "datapacks" => "datapacks",
            "resourcepacks" => "resourcepacks",
            "shaderpacks" => "shaderpacks",
            "worlds" => "saves",
            _ => "mods"
        };
        string ext = _currentSubTab == "mods" ? "jar" : "zip";
        await AddFromFileAsync(folderName, ext);
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

    private string _addContentType = "mod";
    private string _addContentTargetDir = "";
    private CancellationTokenSource? _addContentSearchCts;

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
        AddContentPanel.IsVisible = true;
        AddContentArt.Child = Art(i, 44);
        AddContentTitle.Text = i.Name;
        AddContentSubtitle.Text = $"Minecraft {i.McVersion} · {LoaderName(i.Loader)}";

        AddContentLoaderPillText.Text = LoaderName(i.Loader);
        AddContentVersionPillText.Text = i.McVersion;
        AddContentLoaderPill.IsVisible = true;
        AddContentVersionPill.IsVisible = true;

        AddContentSearchBox.Text = "";
        AddContentSearchBox.PlaceholderText = $"Search for {title.ToLowerInvariant()}...";
        AddContentResults.Children.Clear();
        AddContentStatus.Text = "";

        int kindIndex = projectType switch
        {
            "datapack" => 1,
            "resourcepack" => 2,
            "shader" => 3,
            _ => 0
        };
        if (AddContentKindBox.SelectedIndex != kindIndex)
            AddContentKindBox.SelectedIndex = kindIndex;

        PopulateAddContentCategories();
        _ = SearchAddContentAsync("");
    }

    private void AddContentKind_Changed(object? sender, SelectionChangedEventArgs e)
    {
        if (!_ready || AddContentKindBox == null || _selected == null) return;
        _addContentType = AddContentKindBox.SelectedIndex switch
        {
            1 => "datapack",
            2 => "resourcepack",
            3 => "shader",
            _ => "mod"
        };
        string folderName = _addContentType switch
        {
            "datapack" => "datapacks",
            "resourcepack" => "resourcepacks",
            "shader" => "shaderpacks",
            _ => "mods"
        };
        _addContentTargetDir = Path.Combine(_selected.GameDirectory, folderName);
        PopulateAddContentCategories();
        _ = SearchAddContentAsync(AddContentSearchBox?.Text ?? "");
    }

    private void PopulateAddContentCategories()
    {
        if (AddContentCategory == null) return;
        if (AddContentKind == ContentKind.Shader)
            AddContentCategory.ItemsSource = new[] { "All categories", "Realistic", "Fantasy", "Performance", "Vibrant", "Vanilla-like" };
        else if (AddContentKind == ContentKind.DataPack)
            AddContentCategory.ItemsSource = new[] { "All categories", "Adventure", "Magic", "Technology", "Utility", "Worldgen" };
        else if (AddContentKind == ContentKind.ResourcePack)
            AddContentCategory.ItemsSource = new[] { "All categories", "16x", "32x", "64x", "128x", "Faithful", "Realistic", "Vanilla-like" };
        else
            AddContentCategory.ItemsSource = new[] { "All categories", "Adventure", "Cosmetic", "Decoration", "Equipment", "Food", "Game Mechanics", "Magic", "Mobs", "Optimization", "Storage", "Technology", "Transportation", "Utility", "Worldgen" };
        AddContentCategory.SelectedIndex = 0;
    }

    private void ClearLoaderFilter_Click(object? sender, PointerPressedEventArgs e)
    {
        AddContentLoaderPill.IsVisible = false;
        _ = SearchAddContentAsync(AddContentSearchBox?.Text ?? "");
    }

    private void ClearVersionFilter_Click(object? sender, PointerPressedEventArgs e)
    {
        AddContentVersionPill.IsVisible = false;
        _ = SearchAddContentAsync(AddContentSearchBox?.Text ?? "");
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

            string mcVer = AddContentVersionPill.IsVisible ? i.McVersion : "all";
            string loader = AddContentLoaderPill.IsVisible ? i.Loader : "all";

            var hits = await Catalog().SearchAsync(provider, AddContentKind, query, mcVer, loader, cat, sort, cts.Token);
            if (cts.IsCancellationRequested) return;

            if (AddContentKind == ContentKind.Mod)
            {
                string q = (query ?? "").Trim();
                bool matchesLadsCore = string.IsNullOrEmpty(q)
                    || "the lads core".Contains(q, StringComparison.OrdinalIgnoreCase)
                    || "ladscore".Contains(q, StringComparison.OrdinalIgnoreCase)
                    || "thelads".Contains(q, StringComparison.OrdinalIgnoreCase)
                    || "core".Contains(q, StringComparison.OrdinalIgnoreCase);

                if (matchesLadsCore)
                {
                    hits.Insert(0, new ModSearchItem
                    {
                        Id = "theladscore",
                        Provider = "The Lads",
                        Name = "The Lads Core",
                        ProjectSlug = "theladscore",
                        Author = "The Lads",
                        Summary = $"The official Lads Client core mod for Minecraft {i.McVersion}. Custom multiplayer features, Discord RPC, cosmetics, and performance optimizations.",
                        Categories = new List<string> { "Utility", "Client" },
                        DownloadCount = 250000,
                        IconUrl = "local://theladscore"
                    });
                }
            }

            if (hits.Count == 0)
            {
                AddContentResultsCount.Text = "0 Projects found";
                AddContentStatus.Text = $"No compatible results found on {provider}.";
                return;
            }
            AddContentResultsCount.Text = $"{hits.Count} Projects found";
            AddContentStatus.Text = "";
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

        var icon = new Border
        {
            Width = 60, Height = 60, CornerRadius = new CornerRadius(8), ClipToBounds = true,
            Background = new SolidColorBrush(Color.Parse("#25262B")), VerticalAlignment = VerticalAlignment.Top
        };
        _ = LoadRemoteIconAsync(icon, hit.IconUrl);
        grid.Children.Add(icon);

        var textStack = new StackPanel { Spacing = 3, Margin = new Thickness(14, 0, 14, 0), VerticalAlignment = VerticalAlignment.Center };
        Grid.SetColumn(textStack, 1);

        var titlePanel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Cursor = new Cursor(StandardCursorType.Hand) };
        titlePanel.PointerPressed += (_, _) => OpenModDetails(hit);
        var titleBlock = new TextBlock { Text = hit.Name, FontSize = 15, FontWeight = FontWeight.Bold, Foreground = Brushes.White, TextTrimming = TextTrimming.CharacterEllipsis };
        var authorBlock = new TextBlock { Text = string.IsNullOrEmpty(hit.Author) ? "" : "by " + hit.Author, FontSize = 12, Foreground = new SolidColorBrush(Color.Parse("#9CA0AB")), VerticalAlignment = VerticalAlignment.Center };
        titlePanel.Children.Add(titleBlock);
        titlePanel.Children.Add(authorBlock);
        textStack.Children.Add(titlePanel);

        if (!string.IsNullOrWhiteSpace(hit.Summary))
        {
            textStack.Children.Add(new TextBlock
            {
                Text = hit.Summary, TextWrapping = TextWrapping.Wrap, MaxLines = 2,
                TextTrimming = TextTrimming.CharacterEllipsis, Foreground = new SolidColorBrush(Color.Parse("#C9CAD1")), FontSize = 12
            });
        }

        var tagsRow = new WrapPanel { Margin = new Thickness(0, 4, 0, 0) };
        tagsRow.Children.Add(CreateChip(hit.Provider, "#2A2B33", "#B5B7C0"));
        if (_selected != null)
        {
            tagsRow.Children.Add(CreateChip(LoaderName(_selected.Loader), "#2A2B33", "#B5B7C0"));
            tagsRow.Children.Add(CreateChip($"MC {_selected.McVersion}", "#2A2B33", "#B5B7C0"));
        }
        foreach (var cat in hit.Categories.Take(3))
        {
            tagsRow.Children.Add(CreateChip(cat, "#23242A", "#9CA0AB"));
        }
        if (hit.DownloadCount > 0)
        {
            tagsRow.Children.Add(CreateChip($"⬇ {Downloads(hit.DownloadCount)}", "#23242A", "#8F919B"));
        }
        textStack.Children.Add(tagsRow);
        grid.Children.Add(textStack);

        var sideStack = new StackPanel { VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Right };
        Grid.SetColumn(sideStack, 2);

        var matchedInstalled = FindInstalledMatch(hit);
        var actionBtn = new Button { Width = 100, Height = 34, HorizontalContentAlignment = HorizontalAlignment.Center, VerticalContentAlignment = VerticalAlignment.Center };

        if (matchedInstalled != null)
        {
            if (matchedInstalled.HasUpdate)
            {
                actionBtn.Classes.Add("cfInstall");
                actionBtn.Content = "Update";
                actionBtn.Click += async (_, _) =>
                {
                    actionBtn.IsEnabled = false;
                    actionBtn.Content = "Updating...";
                    await UpdateModAsync(matchedInstalled);
                    actionBtn.Content = "✓ Installed";
                };
            }
            else
            {
                actionBtn.Classes.Add("action");
                actionBtn.IsEnabled = false;
                actionBtn.Content = "✓ Installed";
            }
        }
        else
        {
            actionBtn.Classes.Add("cfInstall");
            actionBtn.Content = "Install";
            actionBtn.Click += async (_, _) =>
            {
                if (_selected is not { } instance) return;
                actionBtn.IsEnabled = false;
                actionBtn.Content = "Installing...";
                try
                {
                    if (hit.Id == "theladscore" || hit.Provider == "The Lads")
                    {
                        string? sourceJar = FindBundledCoreJar(instance.McVersion);
                        if (sourceJar == null || !File.Exists(sourceJar))
                            throw new InvalidOperationException($"The Lads Core mod is not available for Minecraft {instance.McVersion}.");
                        Directory.CreateDirectory(_addContentTargetDir);
                        string destJar = Path.Combine(_addContentTargetDir, "theladscore.jar");
                        File.Copy(sourceJar, destJar, overwrite: true);
                        try
                        {
                            var meta = FabricModMetadata.ReadJar(sourceJar, includeIcon: true);
                            if (meta?.Icon != null)
                            {
                                await File.WriteAllBytesAsync(destJar + ".icon.png", meta.Icon);
                            }
                        }
                        catch { }
                        actionBtn.Content = "✓ Installed";
                        actionBtn.Classes.Clear();
                        actionBtn.Classes.Add("action");
                        ManageStatus.Text = "The Lads Core installed!";
                        await ScanModsAsync(instance);
                        RenderActiveSubTab();
                        return;
                    }

                    var file = await Catalog().LatestFileAsync(hit, AddContentKind, instance.McVersion, instance.Loader);
                    if (file == null) throw new InvalidOperationException($"No file found for Minecraft {instance.McVersion} ({LoaderName(instance.Loader)}).");
                    Directory.CreateDirectory(_addContentTargetDir);
                    string savedFile = await Catalog().InstallAsync(file, _addContentTargetDir, SharedContentService.Instance);
                    if (!string.IsNullOrEmpty(hit.IconUrl))
                    {
                        _ = Task.Run(async () =>
                        {
                            try
                            {
                                var imgBytes = await Http.GetByteArrayAsync(hit.IconUrl);
                                await File.WriteAllBytesAsync(savedFile + ".icon.png", imgBytes);
                            }
                            catch { }
                        });
                    }
                    actionBtn.Content = "✓ Installed";
                    actionBtn.Classes.Clear();
                    actionBtn.Classes.Add("action");
                    ManageStatus.Text = $"Installed {Path.GetFileName(savedFile)}!";
                    await ScanModsAsync(instance);
                    RenderActiveSubTab();
                }
                catch (Exception ex)
                {
                    actionBtn.IsEnabled = true;
                    actionBtn.Content = "Retry";
                    AddContentStatus.Text = $"Installation failed: {ex.Message}";
                }
            };
        }
        sideStack.Children.Add(actionBtn);
        grid.Children.Add(sideStack);

        return new Border
        {
            Background = new SolidColorBrush(Color.Parse("#1A1B20")),
            BorderBrush = new SolidColorBrush(Color.Parse("#26272E")),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(14, 10),
            Child = grid
        };
    }

    private ModpackModItem? FindInstalledMatch(ModSearchItem item)
    {
        if (AddContentKind == ContentKind.Mod)
        {
            return _instanceMods.FirstOrDefault(m =>
            {
                if (string.Equals(item.Id, "theladscore", StringComparison.OrdinalIgnoreCase))
                {
                    if (string.Equals(m.BaseName, "theladscore", StringComparison.OrdinalIgnoreCase)
                        || m.DisplayName.IndexOf("The Lads Core", StringComparison.OrdinalIgnoreCase) >= 0
                        || m.DisplayName.IndexOf("LadsCore", StringComparison.OrdinalIgnoreCase) >= 0)
                        return true;
                }
                if (!string.IsNullOrEmpty(m.ProjectId) && string.Equals(m.ProjectId, item.Id, StringComparison.OrdinalIgnoreCase))
                    return true;
                if (!string.IsNullOrEmpty(item.ProjectSlug) && string.Equals(m.BaseName, item.ProjectSlug, StringComparison.OrdinalIgnoreCase))
                    return true;
                string normItemSlug = ModSearchMatcher.NormalizeKey(item.ProjectSlug);
                string normItemName = ModSearchMatcher.NormalizeKey(item.Name);
                string normModName = ModSearchMatcher.NormalizeKey(m.DisplayName);
                string normBase = ModSearchMatcher.NormalizeKey(m.BaseName);
                return (!string.IsNullOrEmpty(normItemSlug) && normBase.Contains(normItemSlug))
                    || (!string.IsNullOrEmpty(normItemName) && normModName == normItemName)
                    || ModSearchMatcher.MatchesJarFile(item, m.BaseName);
            });
        }
        else if (_selected != null && Directory.Exists(_addContentTargetDir))
        {
            try
            {
                var files = Directory.GetFileSystemEntries(_addContentTargetDir);
                string normItemSlug = ModSearchMatcher.NormalizeKey(item.ProjectSlug);
                string normItemName = ModSearchMatcher.NormalizeKey(item.Name);
                foreach (var file in files)
                {
                    string baseName = Path.GetFileNameWithoutExtension(file);
                    string normBase = ModSearchMatcher.NormalizeKey(baseName);
                    if ((!string.IsNullOrEmpty(normItemSlug) && normBase.Contains(normItemSlug))
                        || (!string.IsNullOrEmpty(normItemName) && (normBase.Contains(normItemName) || normItemName.Contains(normBase))))
                    {
                        return new ModpackModItem { FilePath = file, FileName = Path.GetFileName(file), BaseName = baseName, DisplayName = baseName, Enabled = true };
                    }
                }
            }
            catch { }
        }
        return null;
    }

    private static Border CreateChip(string text, string bgHex, string fgHex)
    {
        return new Border
        {
            Classes = { "chip" },
            Background = new SolidColorBrush(Color.Parse(bgHex)),
            Margin = new Thickness(0, 0, 6, 6),
            Padding = new Thickness(8, 2),
            Child = new TextBlock
            {
                Text = text,
                FontSize = 10.5,
                FontWeight = FontWeight.SemiBold,
                Foreground = new SolidColorBrush(Color.Parse(fgHex))
            }
        };
    }

    private async void OpenModDetails(ModSearchItem hit)
    {
        if (_selected is not { } instance) return;
        _currentDetailProject = hit;
        _modDetailsCts?.Cancel();
        var cts = _modDetailsCts = new CancellationTokenSource();

        ModDetailsPanel.IsVisible = true;
        ModDetailsBreadcrumb.Text = $"{instance.Name} / {hit.Name}";
        ModDetailsTitle.Text = hit.Name;
        ModDetailsAuthor.Text = string.IsNullOrEmpty(hit.Author) ? "" : "by " + hit.Author;
        ModDetailsSummary.Text = hit.Summary;
        ModDetailsBody.Text = "Loading description...";

        ModDetailsIcon.Child = null;
        _ = LoadRemoteIconAsync(ModDetailsIcon, hit.IconUrl);

        ModDetailsTags.Children.Clear();
        ModDetailsTags.Children.Add(CreateChip(hit.Provider, "#2A2B33", "#B5B7C0"));
        ModDetailsTags.Children.Add(CreateChip(LoaderName(instance.Loader), "#2A2B33", "#B5B7C0"));
        ModDetailsTags.Children.Add(CreateChip($"MC {instance.McVersion}", "#2A2B33", "#B5B7C0"));
        if (hit.DownloadCount > 0) ModDetailsTags.Children.Add(CreateChip($"⬇ {Downloads(hit.DownloadCount)}", "#23242A", "#8F919B"));

        var matched = FindInstalledMatch(hit);
        if (matched != null)
        {
            if (matched.HasUpdate)
            {
                ModDetailsActionBtn.Classes.Clear();
                ModDetailsActionBtn.Classes.Add("cfInstall");
                ModDetailsActionBtn.Content = "Update";
                ModDetailsActionBtn.IsEnabled = true;
            }
            else
            {
                ModDetailsActionBtn.Classes.Clear();
                ModDetailsActionBtn.Classes.Add("action");
                ModDetailsActionBtn.Content = "✓ Installed";
                ModDetailsActionBtn.IsEnabled = false;
            }
        }
        else
        {
            ModDetailsActionBtn.Classes.Clear();
            ModDetailsActionBtn.Classes.Add("cfInstall");
            ModDetailsActionBtn.Content = "Install";
            ModDetailsActionBtn.IsEnabled = true;
        }

        ModDetailsTabs.SelectedIndex = 0;
        ModDetailsOverviewView.IsVisible = true;
        ModDetailsVersionsView.IsVisible = false;

        if (hit.Id == "theladscore" || hit.Provider == "The Lads")
        {
            Dispatcher.UIThread.Post(() =>
            {
                if (_currentDetailProject == hit)
                    ModDetailsBody.Text = string.IsNullOrWhiteSpace(hit.Summary)
                        ? "The official Lads Client core mod. Adds multiplayer integration, Discord Rich Presence, cosmetics, and client-side performance enhancements."
                        : hit.Summary;
            });
            _currentDetailVersions = new List<ProjectVersionItem>
            {
                new(
                    "1.11.1",
                    $"The Lads Core 1.11.1 (MC {instance.McVersion})",
                    "1.11.1",
                    "release",
                    DateTime.UtcNow,
                    new[] { LoaderName(instance.Loader).ToLowerInvariant() },
                    new[] { instance.McVersion },
                    null
                )
            };
            ModDetailsVersionsStatus.Text = "1 version available (Bundled)";
            RenderModVersionsTable();
            return;
        }

        _ = Task.Run(async () =>
        {
            try
            {
                string desc = await Catalog().GetProjectDescriptionAsync(hit.Provider, hit.Id, cts.Token);
                Dispatcher.UIThread.Post(() =>
                {
                    if (_currentDetailProject == hit) ModDetailsBody.Text = string.IsNullOrWhiteSpace(desc) ? hit.Summary : desc;
                });
            }
            catch
            {
                Dispatcher.UIThread.Post(() =>
                {
                    if (_currentDetailProject == hit) ModDetailsBody.Text = hit.Summary;
                });
            }
        });

        await LoadModVersionsAsync(hit);
    }

    private void OpenModDetailsFromInstalled(ModpackModItem mod)
    {
        var hit = new ModSearchItem
        {
            Id = mod.ProjectId ?? mod.BaseName,
            Name = mod.DisplayName,
            Summary = mod.Description,
            Author = mod.Author,
            Provider = mod.Provider ?? "Modrinth",
            ProjectSlug = mod.BaseName
        };
        OpenModDetails(hit);
    }

    private void ModDetailsBack_Click(object? sender, RoutedEventArgs e)
    {
        ModDetailsPanel.IsVisible = false;
        _currentDetailProject = null;
    }

    private async void ModDetailsAction_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is not { } instance || _currentDetailProject is not { } hit) return;
        ModDetailsActionBtn.IsEnabled = false;
        ModDetailsActionBtn.Content = "Installing...";
        try
        {
            string targetDir = string.IsNullOrEmpty(_addContentTargetDir) ? Path.Combine(instance.GameDirectory, "mods") : _addContentTargetDir;
            if (hit.Id == "theladscore" || hit.Provider == "The Lads")
            {
                string? sourceJar = FindBundledCoreJar(instance.McVersion);
                if (sourceJar == null || !File.Exists(sourceJar))
                    throw new InvalidOperationException($"The Lads Core mod is not available for Minecraft {instance.McVersion}.");
                Directory.CreateDirectory(targetDir);
                string destJar = Path.Combine(targetDir, "theladscore.jar");
                File.Copy(sourceJar, destJar, overwrite: true);
                try
                {
                    var meta = FabricModMetadata.ReadJar(sourceJar, includeIcon: true);
                    if (meta?.Icon != null)
                    {
                        await File.WriteAllBytesAsync(destJar + ".icon.png", meta.Icon);
                    }
                }
                catch { }
                ModDetailsActionBtn.Content = "✓ Installed";
                ModDetailsActionBtn.Classes.Clear();
                ModDetailsActionBtn.Classes.Add("action");
                ManageStatus.Text = "The Lads Core installed!";
                await ScanModsAsync(instance);
                RenderActiveSubTab();
                return;
            }
            var file = await Catalog().LatestFileAsync(hit, AddContentKind, instance.McVersion, instance.Loader);
            if (file == null) throw new InvalidOperationException($"No release found for Minecraft {instance.McVersion} and loader {instance.Loader}.");
            string savedFile = await Catalog().InstallAsync(file, targetDir, SharedContentService.Instance);
            if (!string.IsNullOrEmpty(hit.IconUrl))
            {
                _ = Task.Run(async () =>
                {
                    try
                    {
                        var imgBytes = await Http.GetByteArrayAsync(hit.IconUrl);
                        await File.WriteAllBytesAsync(savedFile + ".icon.png", imgBytes);
                    }
                    catch { }
                });
            }
            ModDetailsActionBtn.Content = "✓ Installed";
            ModDetailsActionBtn.Classes.Clear();
            ModDetailsActionBtn.Classes.Add("action");
            ManageStatus.Text = $"Installed {Path.GetFileName(savedFile)}!";
            await ScanModsAsync(instance);
            RenderActiveSubTab();
        }
        catch (Exception ex)
        {
            ModDetailsActionBtn.IsEnabled = true;
            ModDetailsActionBtn.Content = "Retry";
            ManageStatus.Text = "Installation failed: " + ex.Message;
        }
    }

    private void ModDetailsTabs_Changed(object? sender, SelectionChangedEventArgs e)
    {
        if (ModDetailsTabs == null) return;
        bool isOverview = ModDetailsTabs.SelectedIndex == 0;
        ModDetailsOverviewView.IsVisible = isOverview;
        ModDetailsVersionsView.IsVisible = !isOverview;
    }

    private void ModDetailsIncompatible_Changed(object? sender, RoutedEventArgs e)
    {
        RenderModVersionsTable();
    }

    private async Task LoadModVersionsAsync(ModSearchItem hit)
    {
        if (_selected is not { } instance) return;
        ModDetailsVersionsStatus.Text = "Loading versions...";
        ModDetailsVersionsList.Children.Clear();
        try
        {
            var versions = await Catalog().GetProjectVersionsAsync(hit.Provider, hit.Id, CancellationToken.None);
            if (_currentDetailProject != hit) return;
            _currentDetailVersions = versions;
            ModDetailsVersionsStatus.Text = $"{versions.Count} versions available";
            RenderModVersionsTable();
        }
        catch (Exception ex)
        {
            ModDetailsVersionsStatus.Text = "Failed to load versions: " + ex.Message;
        }
    }

    private void RenderModVersionsTable()
    {
        if (_selected is not { } instance) return;
        ModDetailsVersionsList.Children.Clear();
        bool showIncompatible = ModDetailsIncompatibleToggle.IsChecked == true;

        var list = _currentDetailVersions.Where(v =>
        {
            if (showIncompatible) return true;
            bool loaderMatch = v.Loaders.Count == 0 || v.Loaders.Any(l => l.Equals(instance.Loader, StringComparison.OrdinalIgnoreCase));
            bool gvMatch = v.GameVersions.Count == 0 || v.GameVersions.Contains(instance.McVersion);
            return loaderMatch && gvMatch;
        }).ToList();

        if (list.Count == 0)
        {
            ModDetailsVersionsList.Children.Add(new TextBlock
            {
                Text = showIncompatible ? "No versions found." : "No versions match this Minecraft version and loader. Toggle 'Show incompatible versions' to see all.",
                Foreground = new SolidColorBrush(Color.Parse("#8F919C")),
                Margin = new Thickness(0, 10, 0, 0)
            });
            return;
        }

        foreach (var ver in list)
        {
            var rowGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("50,*,120,100,100,110") };

            var (badgeBg, badgeText) = ver.ReleaseType switch
            {
                "beta" => ("#1F75FE", "B"),
                "alpha" => ("#E56F10", "A"),
                _ => ("#22A04A", "R")
            };
            var badgeBorder = new Border
            {
                Width = 22, Height = 22, CornerRadius = new CornerRadius(4),
                Background = new SolidColorBrush(Color.Parse(badgeBg)),
                HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Center,
                Child = new TextBlock { Text = badgeText, FontWeight = FontWeight.Bold, FontSize = 11, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }
            };
            rowGrid.Children.Add(badgeBorder);

            var nameBlock = new TextBlock
            {
                Text = string.IsNullOrEmpty(ver.Name) ? ver.VersionNumber : ver.Name,
                FontWeight = FontWeight.SemiBold, FontSize = 12.5, Foreground = Brushes.White,
                VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis
            };
            Grid.SetColumn(nameBlock, 1);
            rowGrid.Children.Add(nameBlock);

            var dateBlock = new TextBlock
            {
                Text = ver.DatePublished is { } dp ? dp.ToLocalTime().ToString("d") : "Unknown",
                FontSize = 11.5, Foreground = new SolidColorBrush(Color.Parse("#9CA0AB")),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(dateBlock, 2);
            rowGrid.Children.Add(dateBlock);

            var loaderBlock = new TextBlock
            {
                Text = ver.Loaders.Count > 0 ? string.Join(", ", ver.Loaders) : LoaderName(instance.Loader),
                FontSize = 11.5, Foreground = new SolidColorBrush(Color.Parse("#9CA0AB")),
                VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis
            };
            Grid.SetColumn(loaderBlock, 3);
            rowGrid.Children.Add(loaderBlock);

            var gvBlock = new TextBlock
            {
                Text = ver.GameVersions.Count > 0 ? ver.GameVersions[0] : instance.McVersion,
                FontSize = 11.5, Foreground = new SolidColorBrush(Color.Parse("#9CA0AB")),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(gvBlock, 4);
            rowGrid.Children.Add(gvBlock);

            bool isLadsCore = _currentDetailProject?.Id == "theladscore" || _currentDetailProject?.Provider == "The Lads";
            var isInstalled = isLadsCore
                ? _instanceMods.Any(m => string.Equals(m.BaseName, "theladscore", StringComparison.OrdinalIgnoreCase)
                                      || m.DisplayName.IndexOf("The Lads Core", StringComparison.OrdinalIgnoreCase) >= 0
                                      || m.DisplayName.IndexOf("LadsCore", StringComparison.OrdinalIgnoreCase) >= 0)
                : _instanceMods.Any(m => m.Version == ver.VersionNumber || (ver.PrimaryFile != null && m.FileName.Equals(ver.PrimaryFile.FileName, StringComparison.OrdinalIgnoreCase)));
            var actionBtn = new Button
            {
                Classes = { isInstalled ? "action" : "cfUpdate" },
                Content = isInstalled ? "✓ Installed" : "Install",
                IsEnabled = !isInstalled && (isLadsCore || ver.PrimaryFile != null),
                Height = 28, Padding = new Thickness(12, 0),
                HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center
            };
            if (!isInstalled && (isLadsCore || ver.PrimaryFile != null))
            {
                actionBtn.Click += async (_, _) =>
                {
                    actionBtn.IsEnabled = false;
                    actionBtn.Content = "Installing...";
                    try
                    {
                        string targetDir = string.IsNullOrEmpty(_addContentTargetDir) ? Path.Combine(instance.GameDirectory, "mods") : _addContentTargetDir;
                        if (isLadsCore)
                        {
                            string? sourceJar = FindBundledCoreJar(instance.McVersion);
                            if (sourceJar == null || !File.Exists(sourceJar))
                                throw new InvalidOperationException($"The Lads Core mod is not available for Minecraft {instance.McVersion}.");
                            Directory.CreateDirectory(targetDir);
                            string destJar = Path.Combine(targetDir, "theladscore.jar");
                            File.Copy(sourceJar, destJar, overwrite: true);
                            try
                            {
                                var meta = FabricModMetadata.ReadJar(sourceJar, includeIcon: true);
                                if (meta?.Icon != null)
                                {
                                    await File.WriteAllBytesAsync(destJar + ".icon.png", meta.Icon);
                                }
                            }
                            catch { }
                        }
                        else
                        {
                            await Catalog().InstallAsync(ver.PrimaryFile!, targetDir, SharedContentService.Instance);
                            string savedFile = Path.Combine(targetDir, ver.PrimaryFile!.FileName);
                            if (_currentDetailProject?.IconUrl is { Length: > 0 } iconUrl)
                            {
                                _ = Task.Run(async () =>
                                {
                                    try
                                    {
                                        var imgBytes = await Http.GetByteArrayAsync(iconUrl);
                                        await File.WriteAllBytesAsync(savedFile + ".icon.png", imgBytes);
                                    }
                                    catch { }
                                });
                            }
                        }
                        actionBtn.Content = "✓ Installed";
                        actionBtn.Classes.Clear();
                        actionBtn.Classes.Add("action");
                        ManageStatus.Text = isLadsCore ? "The Lads Core installed!" : $"Installed {ver.PrimaryFile?.FileName ?? ver.Name}!";
                        await ScanModsAsync(instance);
                        RenderActiveSubTab();
                    }
                    catch (Exception ex)
                    {
                        actionBtn.IsEnabled = true;
                        actionBtn.Content = "Retry";
                        ManageStatus.Text = $"Install failed: {ex.Message}";
                    }
                };
            }
            Grid.SetColumn(actionBtn, 5);
            rowGrid.Children.Add(actionBtn);

            var border = new Border
            {
                Background = new SolidColorBrush(Color.Parse("#1A1B20")),
                BorderBrush = new SolidColorBrush(Color.Parse("#26272E")),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(10, 6),
                Child = rowGrid
            };
            ModDetailsVersionsList.Children.Add(border);
        }
    }

    private async void Play_Click(object? sender, RoutedEventArgs e)
    {
        if (_selected is { } instance) await PlayAsync(instance, retry: false);
    }

    /// <param name="retry">The launcher's own one retry after a known mod-loading race (ModLoadingRace).</param>
    /// <param name="relaunch">The game closed itself to switch renderer (shader toggle): back to the world or server it was in.</param>
    private async Task PlayAsync(ModpackInstance instance, bool retry, GraphicsRenderer.Relaunch? relaunch = null)
    {
        if (Host is not { } host) return;
        if (_launching.TryGetValue(instance.Id, out var pending)) { pending.Cancel(); return; }
        if (_busy.Contains(instance.Id)) return;
        if (_running.ContainsKey(instance.Id) && !host.ModpackSettings.AllowMultiInstance) return;
        using var launch = new CancellationTokenSource();
        _launching[instance.Id] = launch;
        Refresh();
        // Another launcher's instance has no Manage page: its card and the tab's status line show the launch.
        void Refresh() { RefreshButtons(); RenderMods(); if (instance.IsExternal) RenderExternalCards(); }
        void Report(string message) => Dispatcher.UIThread.Post(() =>
        {
            if (_selected == instance) ManageStatus.Text = message;
            else if (instance.IsExternal) Status.Text = $"{instance.Name}: {message}";
        });
        try
        {
            Report("Signing in...");
            var session = await host.ResolveModpackSessionAsync(launch.Token, instance.IsExternal ? null : instance.GameDirectory);
            var launchedUtc = DateTime.UtcNow;
            var process = await ModpackLauncher.LaunchAsync(DataDirectory, instance, session, host.ModpackSettings, JavaService.Instance, Http, Report, launch.Token,
                relaunch?.Server, relaunch?.World);
            _running[instance.Id] = process;
            process.EnableRaisingEvents = true;
            process.Exited += (_, _) => Dispatcher.UIThread.Post(() =>
            {
                if (_running.TryGetValue(instance.Id, out var current) && current == process)
                    _running.Remove(instance.Id);
                int code = process.ExitCode;
                host.ModpackLog($"{instance.Name} closed (exit code {code}).");
                if (_selected == instance)
                    ManageStatus.Text = code == 0 ? "Game closed." : $"Minecraft closed with exit code {code}. See minecraft\\logs\\latest.log (and crash-reports) in Open folder.";
                else if (instance.IsExternal)
                    Status.Text = code == 0 ? $"{instance.Name} closed." : $"{instance.Name} closed with exit code {code}. See logs\\latest.log (and crash-reports) in its folder.";
                Refresh();
                RenderCards();
                // The in-game account / version / renderer switches are Lads files, never prepared in another launcher's instance.
                if (!instance.IsExternal)
                {
                    host.ApplyNextAccountRequest(instance.GameDirectory); // in-game account switcher
                    host.ApplyNextVersionRequest(instance.GameDirectory); // closed from the in-game Switch Versions screen
                }
                // Forge's parallel mod loading lost a known race (ModLoadingRace): start once more rather than leave the player at a crash.
                if (!retry && ModLoadingRace.FindSince(instance.GameDirectory, launchedUtc) is { } race)
                {
                    host.ModpackLog($"{instance.Name} hit a known mod-loading race: {race}. Starting it once more.");
                    _ = PlayAsync(instance, retry: true);
                }
                else if (!instance.IsExternal && !GameSession.WasCancelled(process) && GraphicsRenderer.TakeRelaunch(instance.GameDirectory, host.ModpackLog) is { } next)
                    _ = PlayAsync(instance, retry, next); // closed from the in-game shader toggle: start again on the other renderer
            });
            Report($"Minecraft {instance.McVersion} started as {session.Username}.");
            host.ModpackLog($"Started {instance.Name} ({instance.Loader} {instance.LoaderVersion}, Minecraft {instance.McVersion}) PID {process.Id}.");
        }
        catch (OperationCanceledException) when (launch.IsCancellationRequested) { Report("Launch cancelled."); }
        catch (Exception error) { Report("Launch failed: " + error.Message); host.ModpackLog($"Launch of {instance.Name} failed: {error}"); }
        finally
        {
            _launching.Remove(instance.Id);
            Refresh();
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
        bool isExperience = ExperiencesService.AllExperiences.Any(exp => string.Equals(exp.Id, ExperiencesService.CanonicalPackId(i.Id), StringComparison.OrdinalIgnoreCase));
        if (!isExperience)
        {
            i.LadsCore = LadsCoreToggle.IsEnabled ? (LadsCoreToggle.IsChecked ?? false) : false;
        }
        try
        {
            Modpacks.Save(i);
            ModpackCoreSync.Sync(i, status: msg => ManageStatus.Text = msg);
            OpenManage(i);
            ManageStatus.Text = "Saved.";
        }
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
