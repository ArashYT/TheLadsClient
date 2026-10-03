using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Platform.Storage;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

/// <summary>
/// The Skins tab: a local library of skins and combos, applied to the main account only when the user clicks Apply.
/// Microsoft accounts go through Minecraft Services; local accounts keep the old behaviour (the launcher's skin.png).
/// </summary>
public partial class SkinsView : UserControl
{
    private static readonly IBrush Muted = Brush.Parse("#A0A1AA");
    private readonly Dictionary<string, Bitmap> _textures = new();
    private SkinLibrary? _library;
    private HttpClient _http = null!;
    private string _account = "";
    private Func<CancellationToken, Task<string>>? _microsoftToken;
    private MinecraftProfile? _profile;
    private bool _busy, _rendering;

    public SkinsView()
    {
        InitializeComponent();
        Editor.SkinSaved += async (_, png) => await AddAsync(() => Task.FromResult((png, "Edited " + DateTime.Now.ToString("yyyy-MM-dd HH.mm"), Model())));
        Editor.LogMessage += (_, text) => Status.Text = text;
    }

    private SkinLibrary Library => _library ??= new SkinLibrary(PathService.Instance.BaseDirectory);
    private SavedSkin? Selected => (SkinList.SelectedItem as ListBoxItem)?.Tag as SavedSkin;
    private string Model() => SlimModel.IsChecked == true ? "slim" : "classic";

    /// <summary>Opens the tab for the main account. microsoftToken is null for a local account or no account.</summary>
    public async Task ShowAsync(string account, Func<CancellationToken, Task<string>>? microsoftToken, HttpClient http)
    {
        bool changed = account != _account || (microsoftToken == null) != (_microsoftToken == null);
        _account = account;
        _microsoftToken = microsoftToken;
        _http = http;
        AccountText.Text = account.Length == 0 ? "No account yet: add one in Accounts to apply skins. You can still build your library."
            : microsoftToken != null ? $"Applying to {account} (Microsoft account). Change the main account in Accounts."
            : $"Applying to {account} (local account): Apply saves the launcher's skin.png, which the game shows when you play offline.";
        RenderLibrary(Selected?.Id);
        if (changed || (_profile == null && microsoftToken != null)) await LoadAccountAsync();
    }

    private async Task LoadAccountAsync()
    {
        _profile = null;
        RenderCapes();
        if (_microsoftToken == null)
        {
            string local = PathService.Instance.SkinFile;
            try { CurrentPreview.Child = _account.Length > 0 && File.Exists(local) ? FrontView(Decode(await File.ReadAllBytesAsync(local)), false, 2) : null; }
            catch (Exception) { CurrentPreview.Child = null; } // an unreadable skin.png only loses the preview
            return;
        }
        await RunAsync("Reading your Minecraft profile…", async () =>
        {
            SetProfile(await ReadProfileAsync(await TokenAsync()));
            return "";
        });
    }

    // ── Library ──────────────────────────────────────────────────────────────

    private void RenderLibrary(string? selectId)
    {
        _rendering = true;
        SkinList.Items.Clear();
        foreach (var skin in Library.Skins)
        {
            var text = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
            text.Children.Add(new TextBlock { Text = skin.Name, FontWeight = FontWeight.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis, MaxWidth = 170 });
            text.Children.Add(new TextBlock { Text = skin.Model == "slim" ? "Slim" : "Classic", FontSize = 11, Foreground = Muted });
            var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
            row.Children.Add(Preview(skin, skin.Model, 1.5));
            row.Children.Add(text);
            SkinList.Items.Add(new ListBoxItem { Content = row, Tag = skin });
        }
        if (Library.Skins.Count == 0)
            SkinList.Items.Add(new ListBoxItem { IsEnabled = false, Content = new TextBlock { Text = "No skins yet. Import a PNG file, a player's username or an https link.", TextWrapping = TextWrapping.Wrap, Foreground = Muted } });
        var items = SkinList.Items.OfType<ListBoxItem>().Where(i => i.Tag is SavedSkin).ToList();
        SkinList.SelectedItem = items.FirstOrDefault(i => ((SavedSkin)i.Tag!).Id == selectId) ?? items.FirstOrDefault();
        _rendering = false;
        ShowSelected();
        RenderCombos();
    }

    private void ShowSelected()
    {
        var skin = Selected;
        _rendering = true;
        SkinName.Text = skin?.Name ?? "";
        SlimModel.IsChecked = skin?.Model == "slim";
        ClassicModel.IsChecked = skin?.Model != "slim";
        _rendering = false;
        SelectedPreview.Child = skin == null ? null : Preview(skin, skin.Model, 5);
        DeleteSkinBtn.Content = "Delete";
        SetEnabled();
    }

    private void SkinList_SelectionChanged(object? sender, SelectionChangedEventArgs e) { if (!_rendering) ShowSelected(); }

    private void SkinName_LostFocus(object? sender, RoutedEventArgs e)
    {
        if (Selected is { } skin && (SkinName.Text ?? "").Trim() != skin.Name) UpdateSelected(skin, SkinName.Text ?? "", skin.Model);
    }

    private void Model_Changed(object? sender, RoutedEventArgs e)
    {
        if (!_rendering && Selected is { } skin && Model() != skin.Model) UpdateSelected(skin, skin.Name, Model());
    }

    private void UpdateSelected(SavedSkin skin, string name, string model)
    {
        try { RenderLibrary(Library.Update(skin.Id, name, model).Id); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidOperationException) { Status.Text = "Could not save the library: " + e.Message; }
    }

    private async void ImportText_Click(object? sender, RoutedEventArgs e) => await ImportTextAsync();
    private async void ImportText_KeyDown(object? sender, KeyEventArgs e) { if (e.Key == Key.Enter) await ImportTextAsync(); }

    internal Task ImportTextAsync()
    {
        string text = (ImportText.Text ?? "").Trim();
        if (text.Length == 0) { Status.Text = "Type a player's username or an https:// link to a skin PNG."; return Task.CompletedTask; }
        return AddAsync(async () =>
        {
            if (!text.Contains("://"))
            {
                var (png, model) = await MinecraftSkinApi.DownloadPlayerSkinAsync(_http, text, CancellationToken.None);
                return (png, text, model);
            }
            byte[] file = await MinecraftSkinApi.DownloadPngAsync(_http, text, CancellationToken.None);
            return (file, Path.GetFileNameWithoutExtension(new Uri(text).AbsolutePath), "classic");
        }, () => ImportText.Text = "");
    }

    private async void ImportFile_Click(object? sender, RoutedEventArgs e)
    {
        if (TopLevel.GetTopLevel(this) is not { } top) return;
        var files = await top.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Import skin PNG",
            AllowMultiple = true,
            FileTypeFilter = new[] { new FilePickerFileType("PNG skin") { Patterns = new[] { "*.png" } } }
        });
        foreach (var file in files)
        {
            string path = file.Path.LocalPath;
            await AddAsync(async () =>
            {
                if (new FileInfo(path).Length > 1 << 20) throw new InvalidDataException("That file is too large to be a skin.");
                return (await File.ReadAllBytesAsync(path), Path.GetFileNameWithoutExtension(path), "classic");
            });
        }
    }

    private Task AddAsync(Func<Task<(byte[] Png, string Name, string Model)>> source, Action? done = null) =>
        RunAsync("Importing…", async () =>
        {
            var (png, name, model) = await source();
            var skin = Library.Add(png, name, model);
            done?.Invoke();
            RenderLibrary(skin.Id);
            return $"Added {skin.Name} to the library. Click Apply skin to use it.";
        });

    private void EditSkin_Click(object? sender, RoutedEventArgs e)
    {
        if (Selected is not { } skin) return;
        Editor.LoadSkin(Library.PathOf(skin));
        EditorExpander.IsExpanded = true;
        Avalonia.Threading.Dispatcher.UIThread.Post(() => Editor.BringIntoView(), Avalonia.Threading.DispatcherPriority.Background);
        Status.Text = $"{skin.Name} is open in the skin editor below. Save to library keeps your changes as a new skin.";
    }

    private async void DeleteSkin_Click(object? sender, RoutedEventArgs e)
    {
        if (Selected is not { } skin) return;
        // Two clicks: the library may hold the only copy of a drawn skin.
        if (DeleteSkinBtn.Content as string != "Confirm delete") { DeleteSkinBtn.Content = "Confirm delete"; return; }
        await RunAsync("Deleting…", () =>
        {
            Library.Remove(skin.Id);
            RenderLibrary(null);
            return Task.FromResult($"Deleted {skin.Name} and the combos that used it.");
        });
    }

    // ── Applying ─────────────────────────────────────────────────────────────

    private async void ApplySkin_Click(object? sender, RoutedEventArgs e) => await ApplySkinAsync();
    internal Task ApplySkinAsync() => Selected is { } skin ? ApplyAsync(skin, skin.Model, null) : Task.CompletedTask;
    internal Task ApplyComboAsync(SkinCombo combo) => Library.Find(combo.SkinId) is { } skin ? ApplyAsync(skin, combo.Model, combo) : Task.CompletedTask;

    private Task ApplyAsync(SavedSkin skin, string model, SkinCombo? combo) =>
        RunAsync($"Applying {skin.Name}…", async () =>
        {
            if (_account.Length == 0) return "Add an account in Accounts first.";
            byte[] png = await File.ReadAllBytesAsync(Library.PathOf(skin));
            if (_microsoftToken == null)
            {
                // Local accounts: unchanged behaviour, the launcher's skin.png.
                await File.WriteAllBytesAsync(PathService.Instance.SkinFile, png);
                CurrentPreview.Child = Preview(skin, model, 2);
                return $"{skin.Name} is now the local skin for {_account}." + (combo?.CapeId != null ? " Capes need a Microsoft account, so the cape was skipped." : "");
            }
            string token = await TokenAsync();
            var profile = await MinecraftSkinApi.SendAsync(_http, MinecraftSkinApi.UploadSkin(token, png, model), default);
            string done = $"{skin.Name} applied to {_account}";
            if (combo != null)
            {
                profile ??= await ReadProfileAsync(token);
                if (combo.CapeId == null)
                {
                    profile = await MinecraftSkinApi.SendAsync(_http, MinecraftSkinApi.HideCape(token), default);
                    done += " with no cape";
                }
                else if (profile.Capes.Any(c => c.Id == combo.CapeId))
                {
                    profile = await MinecraftSkinApi.SendAsync(_http, MinecraftSkinApi.ShowCape(token, combo.CapeId), default);
                    done += " with " + combo.CapeName;
                }
                else done += $". {_account} does not own the cape {combo.CapeName}, so it was skipped";
            }
            SetProfile(profile ?? await ReadProfileAsync(token));
            return done + ".";
        });

    private async void ApplyCape_Click(object? sender, RoutedEventArgs e) => await ApplyCapeAsync();

    internal Task ApplyCapeAsync()
    {
        if (CapeList.SelectedItem is not ListBoxItem item || _microsoftToken == null) return Task.CompletedTask;
        var cape = item.Tag as ProfileCape;
        return RunAsync(cape == null ? "Hiding your cape…" : $"Equipping {cape.Name}…", async () =>
        {
            string token = await TokenAsync();
            var request = cape == null ? MinecraftSkinApi.HideCape(token) : MinecraftSkinApi.ShowCape(token, cape.Id);
            SetProfile(await MinecraftSkinApi.SendAsync(_http, request, default) ?? await ReadProfileAsync(token));
            return cape == null ? $"{_account} now wears no cape." : $"{_account} now wears {cape.Name}.";
        });
    }

    private async void Refresh_Click(object? sender, RoutedEventArgs e) => await LoadAccountAsync();

    // ── Combos ───────────────────────────────────────────────────────────────

    private async void SaveCombo_Click(object? sender, RoutedEventArgs e) => await SaveComboAsync();

    internal Task SaveComboAsync()
    {
        if (Selected is not { } skin) { Status.Text = "Select a skin for the combo first."; return Task.CompletedTask; }
        var cape = (CapeList.SelectedItem as ListBoxItem)?.Tag as ProfileCape;
        string name = string.IsNullOrWhiteSpace(ComboName.Text) ? (cape == null ? skin.Name : $"{skin.Name} + {cape.Name}") : ComboName.Text;
        return RunAsync("Saving…", () =>
        {
            var combo = Library.AddCombo(name, skin, Model(), cape?.Id, cape?.Name);
            ComboName.Text = "";
            RenderCombos();
            return Task.FromResult($"Saved combo {combo.Name}.");
        });
    }

    private void RenderCombos()
    {
        ComboRows.Children.Clear();
        foreach (var combo in Library.Combos)
        {
            if (Library.Find(combo.SkinId) is not { } skin) continue;
            var row = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto,Auto") };
            row.Children.Add(Preview(skin, combo.Model, 1));
            var text = new StackPanel { Spacing = 2, Margin = new Thickness(10, 0), VerticalAlignment = VerticalAlignment.Center };
            text.Children.Add(new TextBlock { Text = combo.Name, FontWeight = FontWeight.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis });
            text.Children.Add(new TextBlock { Text = $"{skin.Name} · {(combo.Model == "slim" ? "Slim" : "Classic")} · {combo.CapeName ?? "No cape"}", FontSize = 11, Foreground = Muted, TextTrimming = TextTrimming.CharacterEllipsis });
            Grid.SetColumn(text, 1);
            row.Children.Add(text);
            var apply = new Button { Content = "Apply", Classes = { "action" }, Margin = new Thickness(0, 0, 6, 0), VerticalAlignment = VerticalAlignment.Center };
            apply.Click += async (_, _) => await ApplyComboAsync(combo);
            Grid.SetColumn(apply, 2);
            row.Children.Add(apply);
            var delete = new Button { Content = "Delete", Classes = { "danger" }, VerticalAlignment = VerticalAlignment.Center };
            delete.Click += async (_, _) => await RunAsync("Deleting…", () => { Library.RemoveCombo(combo.Id); RenderCombos(); return Task.FromResult($"Deleted combo {combo.Name}."); });
            Grid.SetColumn(delete, 3);
            row.Children.Add(delete);
            ComboRows.Children.Add(row);
        }
        if (ComboRows.Children.Count == 0) ComboRows.Children.Add(new TextBlock { Text = "No combos yet.", Foreground = Muted });
    }

    // ── Profile and capes ────────────────────────────────────────────────────

    private async Task<MinecraftProfile> ReadProfileAsync(string token) =>
        await MinecraftSkinApi.SendAsync(_http, MinecraftSkinApi.GetProfile(token), default)
        ?? throw new MinecraftSkinApiException("Minecraft Services returned a profile the launcher could not read.");

    private async Task<string> TokenAsync()
    {
        try { return await _microsoftToken!(CancellationToken.None); }
        catch (Exception e) { throw new MinecraftSkinApiException(MicrosoftAccountService.DescribeError(e)); }
    }

    private void SetProfile(MinecraftProfile profile)
    {
        _profile = profile;
        RenderCapes();
        _ = ShowCurrentAsync(profile);
    }

    private async Task ShowCurrentAsync(MinecraftProfile profile)
    {
        var skin = await TextureAsync(profile.SkinUrl);
        if (_profile == profile) CurrentPreview.Child = skin == null ? null : FrontView(skin, profile.SkinModel == "slim", 2);
    }

    private void RenderCapes()
    {
        CapeList.Items.Clear();
        CapeList.IsVisible = _profile != null;
        CapeHint.Text = _microsoftToken == null ? "Capes come with a Microsoft account; local accounts have none."
            : _profile == null ? "Your capes appear here once your profile loads."
            : _profile.Capes.Count == 0 ? "This account owns no capes." : "Pick a cape (or No cape), then Apply cape.";
        if (_profile != null)
        {
            CapeList.Items.Add(CapeItem(null));
            foreach (var cape in _profile.Capes) CapeList.Items.Add(CapeItem(cape));
            CapeList.SelectedItem = CapeList.Items.OfType<ListBoxItem>().FirstOrDefault(i => (i.Tag as ProfileCape)?.Active == true) ?? CapeList.Items[0];
        }
        SetEnabled();
    }

    private ListBoxItem CapeItem(ProfileCape? cape)
    {
        var art = new Border { Width = 30, Height = 48, Background = Brush.Parse("#121315"), CornerRadius = new CornerRadius(2), HorizontalAlignment = HorizontalAlignment.Center };
        var panel = new StackPanel { Spacing = 4, Width = 78 };
        panel.Children.Add(art);
        panel.Children.Add(new TextBlock { Text = cape == null ? "No cape" : cape.Name, FontSize = 11, TextWrapping = TextWrapping.Wrap, TextAlignment = TextAlignment.Center });
        if (cape?.Active == true) panel.Children.Add(new TextBlock { Text = "WEARING", Classes = { "caption" }, FontSize = 8, HorizontalAlignment = HorizontalAlignment.Center });
        if (cape != null) _ = FillCapeAsync(art, cape.Url);
        return new ListBoxItem { Content = panel, Tag = cape, Padding = new Thickness(6) };
    }

    private async Task FillCapeAsync(Border art, string url)
    {
        if (await TextureAsync(url) is not { } cape) return;
        int r = Math.Max(1, cape.PixelSize.Width / 64); // HD capes scale the 64x32 layout
        if (11 * r > cape.PixelSize.Width || 17 * r > cape.PixelSize.Height) return;
        art.Child = Pixelated(new CroppedBitmap(cape, new PixelRect(r, r, 10 * r, 16 * r)), 30, 48);
    }

    private async Task<Bitmap?> TextureAsync(string? url)
    {
        if (string.IsNullOrEmpty(url)) return null;
        if (_textures.TryGetValue(url, out var cached)) return cached;
        try { return _textures[url] = Decode(await _http.GetByteArrayAsync(MinecraftSkinApi.Https(url))); }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or ArgumentException or InvalidOperationException) { return null; }
    }

    // ── Shared plumbing ──────────────────────────────────────────────────────

    private async Task RunAsync(string working, Func<Task<string>> action)
    {
        if (_busy) return;
        _busy = true;
        SetEnabled();
        Status.Text = working;
        try { Status.Text = await action(); }
        catch (Exception e)
        {
            Status.Text = e switch
            {
                MinecraftSkinApiException or InvalidDataException or InvalidOperationException => e.Message,
                HttpRequestException or TaskCanceledException => "Could not reach the skin service. Check your connection and try again.",
                _ => "That did not work: " + e.Message
            };
        }
        finally
        {
            _busy = false;
            SetEnabled();
        }
    }

    private void SetEnabled()
    {
        bool skin = Selected != null && !_busy;
        ApplySkinBtn.IsEnabled = skin && _account.Length > 0;
        EditSkinBtn.IsEnabled = DeleteSkinBtn.IsEnabled = skin;
        ApplyCapeBtn.IsEnabled = !_busy && _profile != null;
    }

    private Control Preview(SavedSkin skin, string model, double scale)
    {
        try { return FrontView(Decode(File.ReadAllBytes(Library.PathOf(skin))), model == "slim", scale); }
        catch (Exception) // a PNG with a valid header can still fail to decode: show an empty frame
        {
            return new Border { Width = 16 * scale, Height = 32 * scale, Background = Brush.Parse("#121315") };
        }
    }

    private static Bitmap Decode(byte[] png) => new(new MemoryStream(png));

    /// <summary>Flat front view of a skin texture, 16x32 skin pixels: head, body, arms and legs, then their outer layer.</summary>
    internal static Control FrontView(Bitmap skin, bool slim, double scale)
    {
        var canvas = new Canvas { Width = 16 * scale, Height = 32 * scale, ClipToBounds = true };
        bool modern = skin.PixelSize.Width == 64 && skin.PixelSize.Height == 64;
        if (skin.PixelSize.Width != 64 || skin.PixelSize.Height is not (32 or 64)) return canvas;
        int arm = slim ? 3 : 4;
        void Part(int x, int y, int w, int h, int left, int top)
        {
            var image = Pixelated(new CroppedBitmap(skin, new PixelRect(x, y, w, h)), w * scale, h * scale);
            Canvas.SetLeft(image, left * scale);
            Canvas.SetTop(image, top * scale);
            canvas.Children.Add(image);
        }
        Part(8, 8, 8, 8, 4, 0);                 // head
        Part(20, 20, 8, 12, 4, 8);              // body
        Part(44, 20, arm, 12, 4 - arm, 8);      // right arm (left on screen)
        Part(4, 20, 4, 12, 4, 20);              // right leg
        // ponytail: 64x32 skins have no left limbs and Minecraft mirrors the right ones; shown unmirrored here.
        Part(modern ? 36 : 44, modern ? 52 : 20, arm, 12, 12, 8);
        Part(modern ? 20 : 4, modern ? 52 : 20, 4, 12, 8, 20);
        Part(40, 8, 8, 8, 4, 0);                // hat
        if (modern)
        {
            Part(20, 36, 8, 12, 4, 8);
            Part(44, 36, arm, 12, 4 - arm, 8);
            Part(52, 52, arm, 12, 12, 8);
            Part(4, 36, 4, 12, 4, 20);
            Part(4, 52, 4, 12, 8, 20);
        }
        return canvas;
    }

    private static Image Pixelated(IImage source, double width, double height)
    {
        var image = new Image { Source = source, Width = width, Height = height, Stretch = Stretch.Fill };
        RenderOptions.SetBitmapInterpolationMode(image, BitmapInterpolationMode.None);
        return image;
    }
}
