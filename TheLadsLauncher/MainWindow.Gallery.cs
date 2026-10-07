using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Layout;
using Avalonia.LogicalTree;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Platform.Storage;
using Avalonia.Threading;
using TheLadsLauncher.Services;
using TheLadsLauncher.Views;

namespace TheLadsLauncher;

/// <summary>
/// Gallery tab: screenshots from every Lads profile and other launchers' instances in a responsive grid (the column count
/// follows the width, tiles stretch so no strip is left on the right), with search, a source filter, hover actions and a
/// zoom/pan viewer with previous/next navigation.
/// </summary>
public partial class MainWindow
{
    // ═══════════════════════════════════════
    //  GALLERY
    // ═══════════════════════════════════════

    // Screenshots are inventoried in place across Lads and other launcher instances.
    private const int GalleryPageSize = 60;
    private const double GalleryMinTile = 250; // the narrowest a tile gets before a column is dropped
    private const double GalleryTileGap = 6;   // each tile's margin (half the gap between tiles)
    private List<(string Path, DateTime Time)> _galleryFiles = new();
    private Dictionary<string, ScreenshotEntry> _gallerySources = new(StringComparer.OrdinalIgnoreCase);
    private CancellationTokenSource? _galleryScanCancellation;
    private string _galleryScanSummary = "";
    private int _galleryShown;
    private int _galleryGeneration;
    private Task _galleryThumbnails = Task.CompletedTask;
    private string? _galleryFavoritesError;
    private DispatcherTimer? _gallerySearchDebounce;
    private bool _galleryUpdatingSources;
    private bool _galleryPreviewDone;

    private void NavGallery_Click(object? sender, RoutedEventArgs e) { _ = LoadGalleryAsync(); NavigateTo("Gallery"); }
    // Reorders the last scan; a scan still running applies the new order when it finishes.
    private void GallerySort_Changed(object? sender, Avalonia.Controls.SelectionChangedEventArgs e) { if (GalleryPage?.IsVisible == true && _galleryScanCancellation == null) ShowGallerySorted(); }
    private void GallerySource_Changed(object? sender, Avalonia.Controls.SelectionChangedEventArgs e) { if (!_galleryUpdatingSources && GalleryPage?.IsVisible == true && _galleryScanCancellation == null) ShowGallerySorted(); }
    private void ImgurId_Changed(object? sender, RoutedEventArgs e) { settings.ImgurClientId = ImgurIdBox.Text ?? ""; settings.Save(); }
    private void GalleryOpenFolder_Click(object? sender, RoutedEventArgs e)
    {
        var error = OpenFolderCreatingIt(SharedContentService.Instance.ScreenshotsDirectory);
        if (error != null) GalleryStatusText.Text = error;
    }
    private void GalleryLoadMore_Click(object? sender, RoutedEventArgs e) => ShowMoreScreenshots();

    // Filtering re-reads thumbnails from disk, so it waits until typing pauses.
    private void GallerySearch_TextChanged(object? sender, TextChangedEventArgs e)
    {
        _gallerySearchDebounce ??= new DispatcherTimer(TimeSpan.FromMilliseconds(250), DispatcherPriority.Background, (_, _) =>
        {
            _gallerySearchDebounce!.Stop();
            if (_galleryScanCancellation == null) ShowGallerySorted();
        });
        _gallerySearchDebounce.Stop();
        _gallerySearchDebounce.Start();
    }

    /// <summary>Columns from the width; tiles share it exactly (no empty strip on the right at any window size).</summary>
    private void GalleryScroller_SizeChanged(object? sender, SizeChangedEventArgs e) => LayoutGalleryGrid(e.NewSize.Width);

    private void LayoutGalleryGrid(double viewport)
    {
        if (viewport <= 0) return;
        double width = viewport + 2 * GalleryTileGap; // the list's negative margin lets the outer tiles touch the page edges
        int columns = Math.Max(1, (int)Math.Floor(width / (GalleryMinTile + 2 * GalleryTileGap)));
        GalleryList.ItemWidth = Math.Max(1, Math.Floor(width / columns));
    }

    private async Task LoadGalleryAsync()
    {
        int generation = ++_galleryGeneration;
        _galleryScanCancellation?.Cancel();
        var cancellation = new CancellationTokenSource();
        _galleryScanCancellation = cancellation;
        SyncFavoritesWithGame();
        ClearGalleryCards();
        GalleryEmptyState.IsVisible = false;
        if (ImgurIdBox != null) ImgurIdBox.Text = settings.ImgurClientId;
        GalleryStatusText.Text = "Scanning launcher instances…";
        var profiles = _profileService.GetProfiles().Select(p => new ScreenshotRoot(_pathService.GetProfileDirectory(p), "Lads · " + p.Name)).ToArray();
        try
        {
            var catalog = await Task.Run(() => CreateScreenshotCatalog().Scan(profiles, cancellation.Token), cancellation.Token);
            if (generation != _galleryGeneration || _windowClosed) return;
            _gallerySources = catalog.Entries.ToDictionary(e => e.Path, StringComparer.OrdinalIgnoreCase);
            _galleryScanSummary = $"{catalog.Folders} folders" + (catalog.Warnings.Count > 0 ? $" · {catalog.Warnings.Count} scan notices" : "");
            ToolTip.SetTip(GalleryStatusText, catalog.Warnings.Count > 0 ? string.Join("\n", catalog.Warnings) : "Lads, Modrinth, CurseForge, Prism and your added folders. Originals stay in their instance.");
            if (GallerySourcesSummary != null)
                GallerySourcesSummary.Text = $"{catalog.Entries.Count} screenshots in {_galleryScanSummary}.";
            foreach (var warning in catalog.Warnings) Log("[Gallery] " + warning);
            FillGallerySourceFilter();
            ShowGallerySorted();
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or ArgumentException)
        {
            if (generation != _galleryGeneration) return;
            _gallerySources = new(StringComparer.OrdinalIgnoreCase); // a later re-sort must not bring back the previous scan
            GalleryStatusText.Text = _galleryScanSummary = "Screenshot scan failed: " + ex.Message;
            Log("[Gallery] " + ex.Message);
        }
        finally
        {
            if (ReferenceEquals(_galleryScanCancellation, cancellation)) _galleryScanCancellation = null;
            cancellation.Dispose();
        }
    }

    /// <summary>"Lads", "Modrinth", "CurseForge", "Prism" or an added folder's label: the launcher a screenshot came from.</summary>
    private static string GallerySourceGroup(string source)
    {
        string head = source.Split(" · ", 2)[0].Trim();
        return head.StartsWith("Lads", StringComparison.OrdinalIgnoreCase) ? "Lads" : head;
    }

    // The source filter lists the launchers of the last scan (with counts); the chosen one survives a rescan.
    private void FillGallerySourceFilter()
    {
        string? chosen = (GallerySourceBox.SelectedItem as ComboBoxItem)?.Tag as string;
        var groups = _gallerySources.Values.GroupBy(e => GallerySourceGroup(e.Source), StringComparer.OrdinalIgnoreCase)
            .OrderByDescending(g => g.Key == "Lads").ThenBy(g => g.Key, StringComparer.OrdinalIgnoreCase).ToList();
        _galleryUpdatingSources = true;
        try
        {
            var items = new List<ComboBoxItem> { new() { Content = "All sources", Tag = "" } };
            items.AddRange(groups.Select(g => new ComboBoxItem { Content = $"{g.Key} ({g.Count()})", Tag = g.Key }.Untranslated()));
            GallerySourceBox.ItemsSource = items;
            GallerySourceBox.SelectedItem = items.FirstOrDefault(i => (string)i.Tag! == chosen) ?? items[0];
        }
        finally { _galleryUpdatingSources = false; }
    }

    /// <summary>Shows the last scan in the chosen order. Sorting, filtering and favorites never rescan the disk.</summary>
    private void ShowGallerySorted()
    {
        ++_galleryGeneration; // stops thumbnail loading for the cards being replaced
        ClearGalleryCards();
        int sort = GallerySortBox?.SelectedIndex ?? 0;
        var favorites = new HashSet<string>(settings.GalleryFavorites, StringComparer.OrdinalIgnoreCase);
        IEnumerable<ScreenshotEntry> files = _gallerySources.Values;
        string search = GallerySearchBox?.Text?.Trim() ?? "";
        string source = (GallerySourceBox?.SelectedItem as ComboBoxItem)?.Tag as string ?? "";
        if (search.Length > 0)
            files = files.Where(f => Path.GetFileName(f.Path).Contains(search, StringComparison.OrdinalIgnoreCase)
                || f.Source.Contains(search, StringComparison.OrdinalIgnoreCase));
        if (source.Length > 0)
            files = files.Where(f => string.Equals(GallerySourceGroup(f.Source), source, StringComparison.OrdinalIgnoreCase));
        files = sort switch
        {
            1 => files.OrderBy(f => f.Time),
            2 => files.OrderBy(f => Path.GetFileName(f.Path), StringComparer.OrdinalIgnoreCase),
            3 => files.OrderByDescending(f => favorites.Contains(GalleryFavoriteKey(f.Path))).ThenByDescending(f => f.Time),
            _ => files.OrderByDescending(f => f.Time)
        };
        _galleryFiles = files.Select(f => (f.Path, f.Time)).ToList();
        _galleryShown = 0;
        if (_galleryFiles.Count == 0)
        {
            GalleryStatusText.Text = _galleryFavoritesError ?? _galleryScanSummary;
            bool filtered = search.Length > 0 || source.Length > 0;
            GalleryEmptyTitle.Text = filtered ? "No screenshots match" : "No screenshots yet";
            GalleryEmptyHint.Text = filtered ? "Clear the search or choose All sources."
                : "Press F2 in game to take one. Using a portable launcher or a custom instance location? Add its folder under Sources.";
            GalleryEmptyState.IsVisible = true;
            return;
        }
        GalleryEmptyState.IsVisible = false;
        ShowMoreScreenshots();
    }

    private void ClearGalleryCards()
    {
        foreach (var old in GalleryList.GetLogicalDescendants().OfType<Image>()) (old.Source as Bitmap)?.Dispose();
        GalleryList.Children.Clear();
        GalleryLoadMoreBtn.IsVisible = false;
    }

    private ScreenshotCatalogService CreateScreenshotCatalog() => new(SharedContentService.Instance.Root,
        discoverLaunchers: !Environment.GetCommandLineArgs().Any(a => a.StartsWith("--preview-", StringComparison.Ordinal)));

    private string GalleryFavoriteKey(string path) => _gallerySources.TryGetValue(path, out var entry) && entry.IsExternal ? path : Path.GetFileName(path);

    private void GalleryRescan_Click(object? sender, RoutedEventArgs e) => _ = LoadGalleryAsync();

    private async void GalleryAddFolder_Click(object? sender, RoutedEventArgs e)
    {
        try
        {
            GallerySourcesBtn.Flyout?.Hide();
            var chosen = await StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions { Title = "Choose a screenshots, instance or launcher folder", AllowMultiple = false });
            if (chosen.FirstOrDefault()?.TryGetLocalPath() is not { } folder) return;
            CreateScreenshotCatalog().AddRoot(folder);
            await LoadGalleryAsync();
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Text.Json.JsonException or ArgumentException)
        { await ShowLadsDialogAsync("Could not add screenshot folder", ex.Message); }
    }

    // The Sources flyout lists the folders added with "Add folder"; removing one stops listing it. Its files stay where they are.
    private void GallerySources_Opening(object? sender, EventArgs e)
    {
        GalleryCustomFoldersList.Children.Clear();
        try
        {
            var roots = CreateScreenshotCatalog().LoadCustomRoots();
            if (roots.Count == 0)
                GalleryCustomFoldersList.Children.Add(new TextBlock { Text = "No added folders.", Classes = { "muted" } });
            foreach (var root in roots)
            {
                var row = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };
                row.Children.Add(new Border { Margin = new Thickness(0, 0, 8, 0), VerticalAlignment = VerticalAlignment.Center, Child = LadsIcons.Glyph(LadsIcons.Folder, 14, Brush.Parse("#E0B45A")) });
                var path = new TextBlock { Text = root.Path, FontSize = 12, Foreground = Brush.Parse("#D2D3D8"), VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.PathSegmentEllipsis }.Untranslated();
                ToolTip.SetTip(path, root.Path);
                Grid.SetColumn(path, 1);
                row.Children.Add(path);
                var remove = new Button { Classes = { "icon", "danger" }, Width = 28, Height = 28, Margin = new Thickness(8, 0, 0, 0), Content = LadsIcons.Glyph(LadsIcons.Close, 12, Brush.Parse("#E08A8A")) };
                ToolTip.SetTip(remove, "Stop listing this folder. Its files are not touched.");
                remove.Click += async (_, _) =>
                {
                    try
                    {
                        CreateScreenshotCatalog().RemoveRoot(root.Path);
                        GallerySources_Opening(null, EventArgs.Empty);
                        await LoadGalleryAsync();
                    }
                    catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Text.Json.JsonException or ArgumentException)
                    { await ShowLadsDialogAsync("Could not remove screenshot folder", ex.Message); }
                };
                Grid.SetColumn(remove, 2);
                row.Children.Add(remove);
                GalleryCustomFoldersList.Children.Add(row);
            }
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Text.Json.JsonException)
        {
            GalleryCustomFoldersList.Children.Add(new TextBlock { Text = "Could not read the added folders: " + ex.Message, Classes = { "muted" }, TextWrapping = TextWrapping.Wrap });
        }
    }

    /// <summary>Adds the next page of cards; their thumbnails are decoded off the UI thread.</summary>
    private void ShowMoreScreenshots()
    {
        var page = _galleryFiles.Skip(_galleryShown).Take(GalleryPageSize).ToList();
        _galleryShown += page.Count;
        var cards = new List<(string Path, Image Image, TextBlock Meta)>();
        foreach (var (path, time) in page)
        {
            GalleryList.Children.Add(BuildGalleryCard(path, time, out var image, out var meta));
            cards.Add((path, image, meta));
        }
        int left = _galleryFiles.Count - _galleryShown;
        GalleryLoadMoreBtn.IsVisible = left > 0;
        GalleryLoadMoreBtn.Content = $"Load more ({left} left)";
        GalleryStatusText.Text = $"{_galleryShown} of {_galleryFiles.Count} · {_galleryScanSummary}"
            + (_galleryFavoritesError != null ? $" · {_galleryFavoritesError}" : "");
        _galleryThumbnails = LoadGalleryThumbnailsAsync(cards, _galleryGeneration);
        ApplyGalleryPreviewHooks();
    }

    private async Task LoadGalleryThumbnailsAsync(List<(string Path, Image Image, TextBlock Meta)> cards, int generation)
    {
        foreach (var card in cards)
        {
            var (bitmap, meta, error) = await Task.Run(() => ReadGalleryThumbnail(card.Path));
            if (generation != _galleryGeneration)
            {
                bitmap?.Dispose();
                return;
            }
            card.Image.Source = bitmap;
            card.Meta.Text = error ?? meta;
            card.Meta.IsVisible = card.Meta.Text.Length > 0;
            if (card.Meta.Text.Length > 0 && card.Meta.Parent?.Parent is Control tile) ToolTip.SetTip(tile, card.Meta.Text);
        }
    }

    private static (Bitmap? Bitmap, string Meta, string? Error) ReadGalleryThumbnail(string path)
    {
        Bitmap? bitmap = null;
        string? error = null;
        try
        {
            using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            bitmap = Bitmap.DecodeToWidth(stream, 480); // sharp at the widest tile of a maximized 1080p window
        }
        catch (Exception ex) // the image decoder throws plain exceptions for damaged files
        {
            error = $"Preview unavailable: {ex.Message}";
        }
        return (bitmap, ReadScreenshotMeta(path), error);
    }

    /// <summary>
    /// A tile: the 16:9 thumbnail with its actions on a hover overlay (copy image, Imgur link, show in folder, favorite,
    /// delete) and a compact caption (file name, source · date). The whole tile opens the viewer.
    /// </summary>
    private Border BuildGalleryCard(string path, DateTime time, out Image image, out TextBlock meta)
    {
        string name = Path.GetFileName(path);
        string favoriteKey = GalleryFavoriteKey(path);
        bool fav = settings.GalleryFavorites.Contains(favoriteKey);
        _gallerySources.TryGetValue(path, out var source);

        image = new Image { Stretch = Stretch.UniformToFill, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        RenderOptions.SetBitmapInterpolationMode(image, BitmapInterpolationMode.HighQuality);
        var thumb = new Panel { Background = Brush.Parse("#202127"), ClipToBounds = true };
        thumb.Children.Add(image);

        // Always visible: a small star on favorites.
        var favBadge = new Border
        {
            Width = 24, Height = 24, CornerRadius = new CornerRadius(12), Background = Brush.Parse("#B0141518"), Margin = new Thickness(8),
            HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top, IsVisible = fav, IsHitTestVisible = false,
            Child = LadsIcons.Glyph(LadsIcons.Star, 14, Brush.Parse("#F2C94C"))
        };

        // Hover overlay: gradients keep the white glyphs and the metadata readable on bright screenshots.
        var overlay = new Panel { Classes = { "tileOverlay" } };
        overlay.Children.Add(new Border
        {
            Height = 52, VerticalAlignment = VerticalAlignment.Top, IsHitTestVisible = false,
            Background = new LinearGradientBrush
            {
                StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative), EndPoint = new RelativePoint(0, 1, RelativeUnit.Relative),
                GradientStops = { new GradientStop(Color.Parse("#A0000000"), 0), new GradientStop(Color.Parse("#00000000"), 1) }
            }
        });
        meta = new TextBlock
        {
            Foreground = Brush.Parse("#E6E7EB"), FontSize = 11, TextTrimming = TextTrimming.CharacterEllipsis, IsVisible = false,
            Margin = new Thickness(10, 0, 10, 8), VerticalAlignment = VerticalAlignment.Bottom
        }.Untranslated();
        var metaShade = new Panel { VerticalAlignment = VerticalAlignment.Bottom, IsHitTestVisible = false };
        metaShade.Children.Add(new Border
        {
            Height = 44,
            Background = new LinearGradientBrush
            {
                StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative), EndPoint = new RelativePoint(0, 1, RelativeUnit.Relative),
                GradientStops = { new GradientStop(Color.Parse("#00000000"), 0), new GradientStop(Color.Parse("#B0000000"), 1) }
            }
        });
        metaShade.Children.Add(meta);
        var metaLabel = meta;
        metaShade.Bind(IsVisibleProperty, metaLabel.GetObservable(IsVisibleProperty));
        overlay.Children.Add(metaShade);

        var favButton = TileAction(fav ? LadsIcons.Star : LadsIcons.StarOutline, fav ? "Remove from favorites" : "Add to favorites", fav ? "#F2C94C" : null);
        favButton.HorizontalAlignment = HorizontalAlignment.Left;
        favButton.VerticalAlignment = VerticalAlignment.Top;
        favButton.Margin = new Thickness(8);
        favButton.Click += (_, _) =>
        {
            bool now = ToggleGalleryFav(favoriteKey);
            favButton.Content = LadsIcons.Glyph(now ? LadsIcons.Star : LadsIcons.StarOutline, 15, Brush.Parse(now ? "#F2C94C" : "#FFFFFF"));
            ToolTip.SetTip(favButton, now ? "Remove from favorites" : "Add to favorites");
            favBadge.IsVisible = now;
            if (GallerySortBox?.SelectedIndex == 3) ShowGallerySorted();
        };
        overlay.Children.Add(favButton);

        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, Margin = new Thickness(8), HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top };
        var copy = TileAction(LadsIcons.Copy, "Copy image");
        copy.Click += (_, _) => CopyImageToClipboard(path);
        actions.Children.Add(copy);
        var upload = TileAction(LadsIcons.Link, "Upload to Imgur and copy the link");
        upload.Click += (_, _) => UploadImgur(path);
        actions.Children.Add(upload);
        var folder = TileAction(LadsIcons.FolderOpen, "Show in folder");
        folder.Click += (_, _) => OpenFolderSelect(path);
        actions.Children.Add(folder);
        if (source?.IsExternal != true)
        {
            var delete = TileAction(LadsIcons.Delete, "Move to the Recycle Bin", "#FF9A9A");
            delete.Classes.Add("tileDanger");
            delete.Click += (_, _) => _ = DeleteScreenshotAsync(path);
            actions.Children.Add(delete);
        }
        overlay.Children.Add(actions);

        var media = new Panel();
        media.Children.Add(thumb);
        media.Children.Add(favBadge);
        media.Children.Add(overlay);

        var caption = new StackPanel { Spacing = 2, Margin = new Thickness(12, 9, 12, 11) };
        caption.Children.Add(new TextBlock { Text = name, Foreground = Brush.Parse("#E9E9EA"), FontSize = 12.5, FontWeight = FontWeight.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis }.Untranslated());
        var sub = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        var sourceText = new TextBlock { Text = source?.Source ?? "", Foreground = Brush.Parse("#8F919C"), FontSize = 11, TextTrimming = TextTrimming.CharacterEllipsis }.Untranslated();
        ToolTip.SetTip(sourceText, source?.Source);
        sub.Children.Add(sourceText);
        var dateText = new TextBlock { Text = time.ToString("g"), Foreground = Brush.Parse("#8F919C"), FontSize = 11, Margin = new Thickness(10, 0, 0, 0) };
        Grid.SetColumn(dateText, 1);
        sub.Children.Add(dateText);
        caption.Children.Add(sub);

        var stack = new StackPanel();
        stack.Children.Add(new AspectBox { Child = media });
        stack.Children.Add(caption);
        var card = new Border { Classes = { "galleryTile" }, Child = stack, Tag = path };
        card.PointerPressed += (_, e) =>
        {
            if (e.GetCurrentPoint(card).Properties.IsLeftButtonPressed) ShowGalleryViewer(path);
        };
        return card;
    }

    private static Button TileAction(string icon, string tip, string? color = null)
    {
        var b = new Button { Classes = { "tileAction" }, Content = LadsIcons.Glyph(icon, 15, Brush.Parse(color ?? "#FFFFFF")) };
        ToolTip.SetTip(b, tip);
        return b;
    }

    private static string ReadScreenshotMeta(string pngPath)
    {
        string metaPath = pngPath + ".json";
        try
        {
            if (!File.Exists(metaPath)) return "";
            using var doc = JsonDocument.Parse(File.ReadAllText(metaPath));
            var root = doc.RootElement;
            var parts = new List<string>();
            if (root.TryGetProperty("server", out var sv) && !string.IsNullOrEmpty(sv.GetString()))
                parts.Add(sv.GetString()!);
            else if (root.TryGetProperty("world", out var wd) && !string.IsNullOrEmpty(wd.GetString()))
                parts.Add(wd.GetString()!);
            if (root.TryGetProperty("x", out var x) && root.TryGetProperty("z", out var z))
                parts.Add($"{x.GetInt32()}, {z.GetInt32()}");
            if (root.TryGetProperty("biome", out var bi) && !string.IsNullOrEmpty(bi.GetString()))
                parts.Add(bi.GetString()?.Replace("minecraft:", "") ?? "");
            if (root.TryGetProperty("seed", out var sd))
                parts.Add("seed " + sd.GetInt64());
            return string.Join("  ·  ", parts);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or JsonException or InvalidOperationException or FormatException)
        {
            return $"Details unreadable ({Path.GetFileName(metaPath)}: {ex.Message})";
        }
    }

    // Favorites are global (settings), like the shared screenshots folder they name. The active profile's
    // <game>\config\gallery_favorites.json only mirrors them: nothing else writes that file, and reading it back replaced the
    // global list with another profile's older copy on every profile switch (favorites were lost).
    private string GalleryFavoritesMirror => Path.Combine(settings.InstancePath, "config", "gallery_favorites.json");

    private void SyncFavoritesWithGame()
    {
        _galleryFavoritesError = null;
        if (settings.GalleryFavorites.Count > 0 || File.Exists(GalleryFavoritesMirror)) WriteGalleryFavoritesMirror();
    }

    private void SaveGalleryFavorites()
    {
        settings.Save();
        WriteGalleryFavoritesMirror();
    }

    private void WriteGalleryFavoritesMirror()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(GalleryFavoritesMirror)!);
            File.WriteAllText(GalleryFavoritesMirror, JsonSerializer.Serialize(settings.GalleryFavorites));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            _galleryFavoritesError = $"Favorites are saved, but the copy '{GalleryFavoritesMirror}' could not be written: {ex.Message}";
            GalleryStatusText.Text = _galleryFavoritesError;
            Log($"[Gallery] {_galleryFavoritesError}");
        }
    }

    /// <summary>Returns whether the screenshot is a favorite now.</summary>
    private bool ToggleGalleryFav(string name)
    {
        bool favorite = !settings.GalleryFavorites.Remove(name);
        if (favorite) settings.GalleryFavorites.Add(name);
        SaveGalleryFavorites();
        return favorite;
    }

    private async Task DeleteScreenshotAsync(string path)
    {
        if (_gallerySources.TryGetValue(path, out var source) && source.IsExternal) return;
        string name = Path.GetFileName(path);
        if (!await ShowLadsDialogAsync("Delete screenshot",
                $"Move '{name}' to the Recycle Bin? Screenshots are shared by every version.", "Move to Recycle Bin", "Cancel", danger: true))
            return;
        var profiles = _profileService.GetProfiles().Select(p => _pathService.GetProfileDirectory(p)).ToList();
        try
        {
            var deleted = await Task.Run(() =>
            {
                // Lads profiles' copies too (found while the shared file still exists), or the next game exit copies it back.
                var files = ScreenshotCatalogService.ProfileCopies(path, SharedContentService.Instance.ScreenshotsDirectory, profiles).Append(path).ToList();
                foreach (string file in files)
                {
                    SafeFileOps.DeleteToRecycleBin(file);
                    string sidecar = file + ".json";
                    if (File.Exists(sidecar)) SafeFileOps.DeleteToRecycleBin(sidecar);
                }
                return files;
            });
            Log($"[Gallery] Moved to the Recycle Bin: {string.Join(", ", deleted)}");
            if (settings.GalleryFavorites.Remove(name)) SaveGalleryFavorites();
        }
        catch (Exception ex)
        {
            Log($"[Gallery] Delete failed for '{path}': {ex.Message}");
            await ShowLadsDialogAsync("Could not delete the screenshot", $"'{name}': {ex.Message}");
        }
        if (GalleryViewerOverlay.IsVisible) CloseGalleryViewer();
        await LoadGalleryAsync();
    }

    private string _viewerPath = "";
    // Gallery viewer zoom/pan state
    private double _viewerZoom = 1.0;
    private double _viewerPanX = 0, _viewerPanY = 0;
    private bool _viewerDragging = false;
    private Avalonia.Point _viewerLastPointer;
    private bool _viewerChromeReady;

    private void ShowGalleryViewer(string path)
    {
        if (!_viewerChromeReady)
        {
            _viewerChromeReady = true;
            GalleryViewerPrevBtn.Content = LadsIcons.Glyph(LadsIcons.ChevronLeft, 30, Brush.Parse("#FFFFFF"));
            GalleryViewerNextBtn.Content = LadsIcons.Glyph(LadsIcons.ChevronRight, 30, Brush.Parse("#FFFFFF"));
            GalleryViewerCopyBtn.Content = LadsIcons.Glyph(LadsIcons.Copy, 16, Brush.Parse("#D6D6D9"));
            GalleryViewerFolderBtn.Content = LadsIcons.Glyph(LadsIcons.FolderOpen, 16, Brush.Parse("#D6D6D9"));
        }
        _viewerPath = path;
        int index = _galleryFiles.FindIndex(f => string.Equals(f.Path, path, StringComparison.OrdinalIgnoreCase));
        GalleryViewerTitle.Text = Path.GetFileName(path);
        GalleryViewerCounter.Text = index >= 0 ? $"{index + 1} / {_galleryFiles.Count}" : "";
        GalleryViewerPrevBtn.IsVisible = index > 0;
        GalleryViewerNextBtn.IsVisible = index >= 0 && index < _galleryFiles.Count - 1;
        string meta = ReadScreenshotMeta(path);
        string source = _gallerySources.TryGetValue(path, out var entry) ? entry.Source + "  ·  " : "";
        GalleryViewerMeta.Text = source + File.GetLastWriteTime(path).ToString("f") + (meta.Length > 0 ? "  ·  " + meta : "");
        var old = GalleryViewerImage.Source as Bitmap;
        try
        {
            using var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            GalleryViewerImage.Source = new Bitmap(fs);
        }
        catch { GalleryViewerImage.Source = null; }
        old?.Dispose();
        ResetViewerZoom();
        GalleryViewerOverlay.IsVisible = true;
    }

    /// <summary>Previous/next screenshot in the current order and filter (also the ones not loaded into the grid yet).</summary>
    private void StepGalleryViewer(int delta)
    {
        int index = _galleryFiles.FindIndex(f => string.Equals(f.Path, _viewerPath, StringComparison.OrdinalIgnoreCase));
        int next = index + delta;
        if (index < 0 || next < 0 || next >= _galleryFiles.Count) return;
        ShowGalleryViewer(_galleryFiles[next].Path);
    }

    private void GalleryViewerPrev_Click(object? sender, RoutedEventArgs e) => StepGalleryViewer(-1);
    private void GalleryViewerNext_Click(object? sender, RoutedEventArgs e) => StepGalleryViewer(1);
    private void GalleryViewerCopy_Click(object? sender, RoutedEventArgs e) { if (_viewerPath.Length > 0) CopyImageToClipboard(_viewerPath); }
    private void GalleryViewerFolder_Click(object? sender, RoutedEventArgs e) { if (_viewerPath.Length > 0) OpenFolderSelect(_viewerPath); }

    // ─── Gallery viewer zoom + pan ───────────────
    private void ResetViewerZoom()
    {
        _viewerZoom = 1.0;
        _viewerPanX = 0; _viewerPanY = 0;
        _viewerDragging = false;
        ApplyViewerTransform();
    }

    private void ApplyViewerTransform()
    {
        if (GalleryViewerImage == null) return;
        GalleryViewerImage.RenderTransformOrigin = Avalonia.RelativePoint.Center;
        var g = new TransformGroup();
        g.Children.Add(new ScaleTransform(_viewerZoom, _viewerZoom));
        g.Children.Add(new TranslateTransform(_viewerPanX, _viewerPanY));
        GalleryViewerImage.RenderTransform = g;
        GalleryViewerImage.Cursor = new Avalonia.Input.Cursor(
            _viewerZoom > 1.0 ? Avalonia.Input.StandardCursorType.SizeAll : Avalonia.Input.StandardCursorType.Arrow);
    }

    private void GalleryViewer_Wheel(object? sender, Avalonia.Input.PointerWheelEventArgs e)
    {
        double factor = e.Delta.Y > 0 ? 1.15 : 1.0 / 1.15;
        double newZoom = Math.Clamp(_viewerZoom * factor, 1.0, 8.0);
        if (Math.Abs(newZoom - _viewerZoom) < 0.0001) return;
        _viewerZoom = newZoom;
        if (_viewerZoom <= 1.0) { _viewerPanX = 0; _viewerPanY = 0; } // snap back to centered
        ApplyViewerTransform();
        e.Handled = true;
    }

    private void GalleryViewer_PointerPressed(object? sender, Avalonia.Input.PointerPressedEventArgs e)
    {
        if (_viewerZoom <= 1.0) return; // only pan when zoomed in
        _viewerDragging = true;
        _viewerLastPointer = e.GetPosition(GalleryViewerOverlay);
        e.Handled = true;
    }

    private void GalleryViewer_PointerMoved(object? sender, Avalonia.Input.PointerEventArgs e)
    {
        if (!_viewerDragging) return;
        var p = e.GetPosition(GalleryViewerOverlay);
        _viewerPanX += p.X - _viewerLastPointer.X;
        _viewerPanY += p.Y - _viewerLastPointer.Y;
        _viewerLastPointer = p;
        ApplyViewerTransform();
    }

    private void GalleryViewer_PointerReleased(object? sender, Avalonia.Input.PointerReleasedEventArgs e)
    {
        _viewerDragging = false;
    }

    private void GalleryViewerClose_Click(object? sender, RoutedEventArgs e) => CloseGalleryViewer();

    private void CloseGalleryViewer()
    {
        GalleryViewerOverlay.IsVisible = false;
        (GalleryViewerImage.Source as Bitmap)?.Dispose();
        GalleryViewerImage.Source = null;
        _viewerPath = "";
        ResetViewerZoom();
    }

    private void GalleryViewerFullscreen_Click(object? sender, RoutedEventArgs e)
    {
        this.WindowState = this.WindowState == WindowState.FullScreen
            ? WindowState.Normal
            : WindowState.FullScreen;
    }

    protected override void OnKeyDown(KeyEventArgs e)
    {
        if (GalleryViewerOverlay != null && GalleryViewerOverlay.IsVisible && e.Key is Key.Escape or Key.Left or Key.Right)
        {
            if (!this.IsActive || this.WindowState == WindowState.Minimized)
            {
                base.OnKeyDown(e);
                return;
            }
            if (e.Key == Key.Left) StepGalleryViewer(-1);
            else if (e.Key == Key.Right) StepGalleryViewer(1);
            else CloseGalleryViewer();
            e.Handled = true;
            return;
        }
        base.OnKeyDown(e);
    }

    private void OpenFolderSelect(string path)
    {
        try { Process.Start(new ProcessStartInfo { FileName = "explorer.exe", Arguments = "/select,\"" + path + "\"", UseShellExecute = true }); }
        catch (Exception ex) { Log($"[Gallery] show in folder failed: {ex.Message}"); }
    }

    private void CopyTextToClipboard(string text)
    {
        try
        {
            var p = new Process();
            p.StartInfo.FileName = "clip.exe";
            p.StartInfo.UseShellExecute = false;
            p.StartInfo.RedirectStandardInput = true;
            p.StartInfo.CreateNoWindow = true;
            p.Start();
            p.StandardInput.Write(text);
            p.StandardInput.Close();
            p.WaitForExit(1000);
        }
        catch (Exception ex) { Log($"[Gallery] copy text failed: {ex.Message}"); }
    }

    private void CopyImageToClipboard(string path)
    {
        try
        {
            string safe = path.Replace("'", "''");
            var p = new Process();
            p.StartInfo.FileName = "powershell";
            p.StartInfo.Arguments = "-NoProfile -STA -Command \"Add-Type -AssemblyName System.Windows.Forms,System.Drawing; [System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile('" + safe + "'))\"";
            p.StartInfo.UseShellExecute = false;
            p.StartInfo.CreateNoWindow = true;
            p.Start();
            p.WaitForExit(4000);
            StatusText.Text = "Copied image to clipboard.";
        }
        catch (Exception ex) { Log($"[Gallery] copy image failed: {ex.Message}"); }
    }

    private async void UploadImgur(string path)
    {
        if (string.IsNullOrWhiteSpace(settings.ImgurClientId))
        {
            StatusText.Text = "Enter an Imgur Client ID under Gallery → Sources first.";
            if (GalleryPage.IsVisible && !GalleryViewerOverlay.IsVisible) GallerySourcesBtn.Flyout?.ShowAt(GallerySourcesBtn);
            return;
        }
        try
        {
            StatusText.Text = "Uploading to Imgur...";
            byte[] bytes = File.ReadAllBytes(path);
            var req = new HttpRequestMessage(HttpMethod.Post, "https://api.imgur.com/3/image");
            req.Headers.Add("Authorization", "Client-ID " + settings.ImgurClientId);
            var form = new MultipartFormDataContent();
            form.Add(new ByteArrayContent(bytes), "image", Path.GetFileName(path));
            req.Content = form;
            var resp = await _httpClient.SendAsync(req);
            string body = await resp.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(body);
            if (doc.RootElement.TryGetProperty("data", out var data) && data.TryGetProperty("link", out var linkEl))
            {
                string link = linkEl.GetString() ?? "";
                CopyTextToClipboard(link);
                StatusText.Text = "Imgur link copied: " + link;
                Log($"[Gallery] Uploaded: {link}");
            }
            else
            {
                StatusText.Text = "Imgur upload failed (check your Client ID).";
            }
        }
        catch (Exception ex)
        {
            StatusText.Text = "Imgur upload failed: " + ex.Message;
        }
    }

    /// <summary>
    /// Sandbox QA only (--preview-page Gallery): LADS_PREVIEW_GALLERY=hover shows the first tile's hover actions,
    /// =viewer opens the viewer on the second screenshot, =sources opens the Sources flyout.
    /// </summary>
    private void ApplyGalleryPreviewHooks()
    {
        if (_galleryPreviewDone || !Environment.GetCommandLineArgs().Contains("--preview-page")
            || Environment.GetEnvironmentVariable("LADS_PREVIEW_GALLERY") is not { Length: > 0 } mode || GalleryList.Children.Count == 0) return;
        _galleryPreviewDone = true;
        Dispatcher.UIThread.Post(() =>
        {
            if (mode == "hover" && GalleryList.Children[0] is Border tile) tile.Classes.Add("preview");
            else if (mode == "viewer" && _galleryFiles.Count > 1) ShowGalleryViewer(_galleryFiles[1].Path);
            else if (mode == "sources") GallerySourcesBtn.Flyout?.ShowAt(GallerySourcesBtn);
        }, DispatcherPriority.Background);
    }
}
