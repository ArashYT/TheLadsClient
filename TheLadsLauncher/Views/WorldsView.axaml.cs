using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Path = Avalonia.Controls.Shapes.Path;
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

public partial class WorldsView : UserControl
{
    private const double MinCardWidth = 440, Gap = 12, Thumb = 80;
    private static readonly string[] Sorts = { "Sort: Last played", "Sort: Name", "Sort: Size" };
    // Shared across visits: icons keyed by path + write time (a re-saved world gets its new icon), sizes until Refresh.
    private static readonly ConcurrentDictionary<string, (DateTime Stamp, Bitmap? Image)> Icons = new(StringComparer.OrdinalIgnoreCase);
    private static readonly ConcurrentDictionary<string, long> Sizes = new(StringComparer.OrdinalIgnoreCase);
    private static readonly Dictionary<int, WriteableBitmap> Placeholders = new();

    private WorldCatalog _catalog = new(Array.Empty<WorldEntry>(), Array.Empty<string>());
    private readonly WorldCatalogService _service = new(PathService.Instance.BaseDirectory);
    private readonly Dictionary<string, TextBlock> _sizeLabels = new(StringComparer.OrdinalIgnoreCase);
    private readonly List<Control> _cards = new();
    private int _columns;
    private CancellationTokenSource? _sizing;
    private bool _loading, _loaded;

    public WorldsView()
    {
        InitializeComponent();
        Location.ItemsSource = new[] { "All locations", "Global .minecraft", "Specific Version", "Custom Instances" };
        Location.SelectedIndex = 0;
        Version.ItemsSource = new[] { "All versions" };
        Version.SelectedIndex = 0;
        Sort.ItemsSource = Sorts;
        Sort.SelectedIndex = 0;
        // Sandbox QA only (--preview-page): pre-fill the search to capture the "no matches" state.
        if (Environment.GetEnvironmentVariable("LADS_PREVIEW_WORLDS_SEARCH") is { Length: > 0 } query) Search.Text = query;
        Toolbar.SizeChanged += (_, e) => AddLabel.IsVisible = e.NewSize.Width >= 820; // icon-only "+" when the window is narrow
    }

    public async Task LoadAsync()
    {
        if (_loading) return;
        _loading = true;
        if (!_loaded) Render();
        try
        {
            var sources = new List<WorldSource> { new("Global .minecraft", "Global .minecraft", SharedContentService.Instance.Root) };
            sources.AddRange(WorldCatalogService.ProfileSources(ProfileService.Instance.GetProfiles(), PathService.Instance));
            sources.AddRange(_service.LoadCustomSources());
            _catalog = await Task.Run(() => WorldCatalogService.Scan(sources));
            string selected = Version.SelectedItem as string ?? "All versions";
            var versions = new[] { "All versions" }.Concat(_catalog.Worlds.Select(w => w.Version).Where(v => v.Length > 0).Distinct()
                .OrderByDescending(v => v, VersionOrder.Instance)).ToList();
            Version.ItemsSource = versions;
            Version.SelectedItem = versions.Contains(selected) ? selected : "All versions";
            ShowStatus(_catalog.Warnings.Count > 0 ? string.Join("\n", _catalog.Warnings) : null);
        }
        catch (Exception e) { ShowStatus("Unable to read worlds: " + e.Message); }
        finally { _loading = false; _loaded = true; }
        Render();
    }

    private void ShowStatus(string? text) { Status.Text = text ?? ""; Status.IsVisible = !string.IsNullOrEmpty(text); }

    private List<WorldEntry> Visible()
    {
        var filtered = WorldCatalogService.Filter(_catalog.Worlds, Search.Text ?? "", Location.SelectedItem as string ?? "All locations",
            Version.SelectedItem as string ?? "All versions");
        return (Sort.SelectedIndex switch
        {
            1 => filtered.OrderBy(w => w.Name, StringComparer.CurrentCultureIgnoreCase).ThenByDescending(w => w.LastPlayed),
            2 => filtered.OrderByDescending(w => Sizes.TryGetValue(w.Folder, out long s) ? s : -1).ThenByDescending(w => w.LastPlayed),
            _ => filtered.OrderByDescending(w => w.LastPlayed)
        }).ToList();
    }

    private void Render()
    {
        if (Cards == null || Search == null || Location == null || Version == null || Sort == null) return;
        var visible = Visible();
        _sizeLabels.Clear();
        _cards.Clear();
        foreach (var world in visible) _cards.Add(Card(world));
        _columns = 0;
        LayoutCards();

        int total = _catalog.Worlds.Count;
        Count.Text = visible.Count == total ? $"{total} {(total == 1 ? "world" : "worlds")}" : $"{visible.Count} of {total}";
        CountChip.IsVisible = _loaded && total > 0;
        bool empty = visible.Count == 0;
        Empty.IsVisible = empty;
        Scroller.IsVisible = !empty;
        if (empty)
        {
            (string title, string hint, string? action) = !_loaded ? ("Reading worlds…", "", null)
                : total == 0 ? ("No worlds yet", "Worlds you create in Minecraft show up here. Using another launcher? Add its instance folder to list those saves too.", "Add instance folder")
                : ("No worlds match", "Try a different search, location or version.", "Clear filters");
            EmptyTitle.Text = title;
            EmptyHint.Text = hint;
            EmptyHint.IsVisible = hint.Length > 0;
            EmptyAction.Content = action;
            EmptyAction.IsVisible = action != null;
        }
        _ = MeasureSizesAsync(visible);
    }

    // ── Card ───────────────────────────────────────────────────────────────────

    private Control Card(WorldEntry world)
    {
        var image = new Image { Stretch = Stretch.UniformToFill, Width = Thumb, Height = Thumb };
        RenderOptions.SetBitmapInterpolationMode(image, BitmapInterpolationMode.None); // 64 px icons stay pixel-crisp
        SetIcon(image, world);
        var thumb = new Border
        {
            Width = Thumb, Height = Thumb, CornerRadius = new CornerRadius(8), ClipToBounds = true, VerticalAlignment = VerticalAlignment.Top,
            Background = Brush("#121315"), Child = image
        };

        var name = new TextBlock { Text = world.Name, FontSize = 15, FontWeight = FontWeight.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis, MaxLines = 1 }.Untranslated();
        ToolTip.SetTip(name, new TextBlock { Text = world.Name + "\n" + world.Folder, TextWrapping = TextWrapping.Wrap, MaxWidth = 520 }.Untranslated());

        var played = new TextBlock { Text = "Played " + WorldCatalogService.Relative(world.LastPlayed, DateTime.UtcNow), Classes = { "muted" } };
        ToolTip.SetTip(played, world.LastPlayed.ToLocalTime().ToString("f"));
        var size = new TextBlock { Classes = { "muted" }, IsVisible = false }.Untranslated();
        if (Sizes.TryGetValue(world.Folder, out long known)) ShowSize(size, known);
        _sizeLabels[world.Folder] = size;
        var meta = new StackPanel { Orientation = Orientation.Horizontal, Children = { played } };
        if (world.Cheats == true) meta.Children.Add(new TextBlock { Text = "  ·  Cheats on", Classes = { "muted" } });
        meta.Children.Add(size);

        var chips = new WrapPanel();
        if (world.Version.Length > 0) chips.Children.Add(Chip(world.Version, null, untranslated: true));
        if (world.GameMode.Length > 0) chips.Children.Add(Chip(world.GameMode, world.GameMode switch { "Hardcore" => "accent", "Creative" => "good", _ => null }));
        chips.Children.Add(Chip(world.Source, null, untranslated: true, tip: world.Category));
        if (world.Warning != null)
        {
            var warn = Chip("Unreadable level.dat", null, tip: world.Warning);
            warn.Background = Brush("#2E2618");
            ((TextBlock)warn.Child!).Foreground = Brush("#E0B26A");
            chips.Children.Add(warn);
        }

        name.VerticalAlignment = VerticalAlignment.Center;
        name.Margin = new Thickness(0, 0, 8, 0);
        meta.Margin = new Thickness(0, 2, 0, 0);
        chips.Margin = new Thickness(0, 6, 0, 0);

        var open = IconButton("M3,6.5 L9,6.5 L11,8.5 L21,8.5 L21,19 L3,19 Z", "Open world folder");
        open.Click += (_, _) => OpenFolder(world.Folder);
        var more = IconButton("M6,10.5 A1.5,1.5 0 1 0 6,13.5 A1.5,1.5 0 1 0 6,10.5 Z M12,10.5 A1.5,1.5 0 1 0 12,13.5 A1.5,1.5 0 1 0 12,10.5 Z M18,10.5 A1.5,1.5 0 1 0 18,13.5 A1.5,1.5 0 1 0 18,10.5 Z", "More");
        var dots = (Path)more.Content!;
        dots.Classes.Add("filled");
        dots.Height = 3;
        var menu = new MenuFlyout { Placement = PlacementMode.BottomEdgeAlignedRight };
        menu.Items.Add(MenuItem("Open folder", () => OpenFolder(world.Folder)));
        menu.Items.Add(MenuItem("Copy folder path", () => Copy(world.Folder)));
        menu.Items.Add(MenuItem("Copy world name", () => Copy(world.Name)));
        more.Flyout = menu;
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, Children = { open, more } };

        // Thumbnail | title row with the actions on the right; meta and chips run underneath across the full width.
        var body = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), RowDefinitions = new RowDefinitions("Auto,Auto,Auto"), Margin = new Thickness(14, 0, 0, 0) };
        body.Children.Add(name);
        Grid.SetColumn(actions, 1); body.Children.Add(actions);
        Grid.SetRow(meta, 1); Grid.SetColumnSpan(meta, 2); body.Children.Add(meta);
        Grid.SetRow(chips, 2); Grid.SetColumnSpan(chips, 2); body.Children.Add(chips);
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*") };
        grid.Children.Add(thumb);
        Grid.SetColumn(body, 1); grid.Children.Add(body);
        var card = new Border { Classes = { "card", "interactive" }, Padding = new Thickness(12), Child = grid };
        card.DoubleTapped += (_, _) => OpenFolder(world.Folder);
        return card;
    }

    private static Border Chip(string text, string? kind, bool untranslated = false, string? tip = null)
    {
        var label = new TextBlock { Text = text };
        if (untranslated) label.Untranslated();
        var chip = new Border { Classes = { "chip" }, Child = label, Margin = new Thickness(0, 2, 6, 2) };
        if (kind != null) chip.Classes.Add(kind);
        if (tip != null) ToolTip.SetTip(chip, tip);
        return chip;
    }

    private static Button IconButton(string geometry, string tip)
    {
        var button = new Button
        {
            Classes = { "icon" },
            Content = new Path { Classes = { "glyph" }, Data = Geometry.Parse(geometry) }
        };
        ToolTip.SetTip(button, tip);
        return button;
    }

    private static MenuItem MenuItem(string header, Action action)
    {
        var item = new MenuItem { Header = header };
        item.Click += (_, _) => action();
        return item;
    }

    private static IBrush Brush(string hex) => new SolidColorBrush(Color.Parse(hex));

    private static void ShowSize(TextBlock label, long bytes) { label.Text = "  ·  " + WorldCatalogService.FormatSize(bytes); label.IsVisible = true; }

    // ── Responsive grid ────────────────────────────────────────────────────────

    private void ScrollerSizeChanged(object? sender, SizeChangedEventArgs e) { if (Math.Abs(e.NewSize.Width - e.PreviousSize.Width) > 0.5) LayoutCards(); }

    /// <summary>As many ≥440 px columns as fit, so the grid always fills the width. Built as rows of star columns so each row
    /// is only as tall as its own tallest card (a UniformGrid would stretch every row to the tallest card, or to the viewport).</summary>
    private void LayoutCards()
    {
        double width = Scroller.Bounds.Width - Cards.Margin.Right;
        int columns = width <= 0 ? 2 : Math.Max(1, (int)((width + Gap) / (MinCardWidth + Gap)));
        if (columns == _columns && Cards.Children.Count > 0) return;
        _columns = columns;
        foreach (var row in Cards.Children.OfType<Grid>()) row.Children.Clear();
        Cards.Children.Clear();
        var layout = string.Join(",", Enumerable.Repeat("*", columns));
        for (int i = 0; i < _cards.Count; i += columns)
        {
            var row = new Grid { ColumnDefinitions = new ColumnDefinitions(layout), ColumnSpacing = Gap };
            for (int c = 0; c < columns && i + c < _cards.Count; c++)
            {
                Grid.SetColumn(_cards[i + c], c);
                row.Children.Add(_cards[i + c]);
            }
            Cards.Children.Add(row);
        }
    }

    // ── Icons ──────────────────────────────────────────────────────────────────

    private static async void SetIcon(Image image, WorldEntry world)
    {
        if (world.Icon == null) { image.Source = Placeholder(world.Name); return; }
        DateTime stamp;
        try { stamp = File.GetLastWriteTimeUtc(world.Icon); } catch { stamp = default; }
        if (Icons.TryGetValue(world.Icon, out var hit) && hit.Stamp == stamp) { image.Source = hit.Image ?? Placeholder(world.Name); return; }
        var bitmap = await Task.Run(() => LoadIcon(world.Icon));
        Icons[world.Icon] = (stamp, bitmap);
        image.Source = bitmap ?? Placeholder(world.Name);
    }

    /// <summary>Decoded off the UI thread; a missing, locked, huge or corrupt icon.png just falls back to the placeholder.</summary>
    private static Bitmap? LoadIcon(string path)
    {
        try
        {
            using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            if (stream.Length is 0 or > 4 * 1024 * 1024) return null;
            var bitmap = new Bitmap(stream);
            return bitmap.PixelSize.Width > 0 ? bitmap : null;
        }
        catch (Exception) { return null; }
    }

    /// <summary>A 16×16 grass-block side, varied per world name, for saves the game has not written an icon for yet.</summary>
    private static WriteableBitmap Placeholder(string name)
    {
        int seed = 0;
        foreach (char c in name) seed = seed * 31 + c;
        seed &= 7;
        if (Placeholders.TryGetValue(seed, out var cached)) return cached;
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
        return Placeholders[seed] = bitmap;
    }

    private static double Rand(int seed, int x, int y)
    {
        uint h = unchecked((uint)(seed * 374761393 + x * 668265263 + y * 1274126177));
        h = unchecked((h ^ (h >> 13)) * 1274126177);
        return ((h ^ (h >> 16)) & 0xFFFFFF) / (double)0x1000000;
    }

    // ── Sizes (lazy, one folder at a time, never blocks the page) ─────────────

    private async Task MeasureSizesAsync(List<WorldEntry> worlds)
    {
        _sizing?.Cancel();
        var cts = _sizing = new CancellationTokenSource();
        bool missing = false;
        foreach (var world in worlds)
        {
            if (cts.IsCancellationRequested) return;
            if (Sizes.ContainsKey(world.Folder)) continue;
            missing = true;
            long bytes = await Task.Run(() => WorldCatalogService.FolderSize(world.Folder));
            Sizes[world.Folder] = bytes;
            if (!cts.IsCancellationRequested && _sizeLabels.TryGetValue(world.Folder, out var label)) ShowSize(label, bytes);
        }
        // Sorting by size before every size was known: re-sort once they all are.
        if (missing && !cts.IsCancellationRequested && Sort.SelectedIndex == 2) Render();
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void OpenFolder(string folder)
    {
        try { Process.Start(new ProcessStartInfo(folder) { UseShellExecute = true }); }
        catch (Exception e) { ShowStatus(e.Message); }
    }

    private async void Copy(string text)
    {
        try { if (TopLevel.GetTopLevel(this)?.Clipboard is { } clipboard) await clipboard.SetTextAsync(text); }
        catch (Exception e) { ShowStatus("Couldn't copy: " + e.Message); }
    }

    private void FilterChanged(object? sender, TextChangedEventArgs e) => Render();
    private void SelectionChanged(object? sender, SelectionChangedEventArgs e) => Render();
    private void SortChanged(object? sender, SelectionChangedEventArgs e) => Render();
    private async void Refresh(object? sender, RoutedEventArgs e) { Sizes.Clear(); await LoadAsync(); }
    private void ResetFilters() { Search.Text = ""; Location.SelectedIndex = 0; Version.SelectedIndex = 0; Render(); }
    private void EmptyActionClick(object? sender, RoutedEventArgs e)
    {
        if (_catalog.Worlds.Count == 0) AddInstance(sender, e);
        else ResetFilters();
    }

    private async void AddInstance(object? sender, RoutedEventArgs e)
    {
        var top = TopLevel.GetTopLevel(this);
        if (top == null) return;
        var folders = await top.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions { Title = "Choose an instance containing a saves folder", AllowMultiple = false });
        if (folders.Count == 0) return;
        try { _service.AddCustomSource(folders[0].Path.LocalPath); await LoadAsync(); }
        catch (Exception error) { ShowStatus(error.Message); }
    }

    /// <summary>Newest first: compares the numeric parts ("1.21.11" &gt; "1.21.9", "26.3" &gt; "1.21"); "Pre-1.9" sorts last.</summary>
    private sealed class VersionOrder : IComparer<string>
    {
        public static readonly VersionOrder Instance = new();
        public int Compare(string? a, string? b)
        {
            if (a == b) return 0;
            if (a == WorldCatalogService.LegacyVersion) return -1;
            if (b == WorldCatalogService.LegacyVersion) return 1;
            int[] Parts(string? v) => (v ?? "").Split('.', '-', ' ').Select(p => int.TryParse(p, out int n) ? n : -1).ToArray();
            int[] x = Parts(a), y = Parts(b);
            for (int i = 0; i < Math.Max(x.Length, y.Length); i++)
            {
                int c = (i < x.Length ? x[i] : 0).CompareTo(i < y.Length ? y[i] : 0);
                if (c != 0) return c;
            }
            return string.CompareOrdinal(a, b);
        }
    }
}
