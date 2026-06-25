using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Input;
using Avalonia.Media.Imaging;
using System;
using System.Collections.ObjectModel;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows.Input;
using System.Linq;

namespace TheLadsLauncher.Views;

public partial class GalleryModsView : UserControl
{
    private LauncherSettings _settings;
    private HttpClient _httpClient = new HttpClient();
    
    public ObservableCollection<ScreenshotItem> Screenshots { get; set; } = new();
    public ObservableCollection<GalleryItem> LocalItems { get; set; } = new();
    public ObservableCollection<GalleryItem> DiscoverItems { get; set; } = new();

    public GalleryModsView()
    {
        InitializeComponent();
        _settings = LauncherSettings.Load();
        
        ScreenshotsList.ItemsSource = Screenshots;
        LocalItemsList.ItemsSource = LocalItems;
        DiscoverItemsList.ItemsSource = DiscoverItems;

        LoadScreenshots();
        LoadLocalContent();
    }

    // --- Screenshots ---
    private void RefreshScreenshots_Click(object? sender, RoutedEventArgs e)
    {
        LoadScreenshots();
    }

    private void LoadScreenshots()
    {
        foreach (var item in Screenshots)
        {
            item.Image?.Dispose();
        }
        Screenshots.Clear();
        string dir = Path.Combine(_settings.InstancePath, "screenshots");
        if (!Directory.Exists(dir)) return;

        foreach (var file in Directory.GetFiles(dir, "*.png"))
        {
            try 
            {
                var item = new ScreenshotItem
                {
                    FilePath = file,
                    FileName = Path.GetFileName(file),
                    IsFavorite = _settings.GalleryFavorites.Contains(Path.GetFileName(file))
                };
                using var stream = File.OpenRead(file);
                item.Image = Bitmap.DecodeToWidth(stream, 200);

                item.ToggleFavoriteCommand = new RelayCommand(_ => ToggleFavorite(item));
                item.DeleteCommand = new RelayCommand(_ => DeleteScreenshot(item));

                Screenshots.Add(item);
            }
            catch { }
        }
    }

    private void ToggleFavorite(ScreenshotItem item)
    {
        if (item.IsFavorite)
        {
            if (!_settings.GalleryFavorites.Contains(item.FileName))
                _settings.GalleryFavorites.Add(item.FileName);
        }
        else
        {
            _settings.GalleryFavorites.Remove(item.FileName);
        }
        _settings.Save();
    }

    private void DeleteScreenshot(ScreenshotItem item)
    {
        if (File.Exists(item.FilePath))
        {
            try
            {
                File.Delete(item.FilePath);
                Screenshots.Remove(item);
                _settings.GalleryFavorites.Remove(item.FileName);
                _settings.Save();
            }
            catch (IOException)
            {
            }
        }
    }

    // --- Local Library ---
    private void RefreshLocal_Click(object? sender, RoutedEventArgs e)
    {
        LoadLocalContent();
    }

    private void LocalSearchBox_KeyUp(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter) LoadLocalContent();
    }

    private void LocalFilter_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (LocalSearchBox == null) return;
        LoadLocalContent();
    }

    private void LoadLocalContent()
    {
        foreach (var item in LocalItems)
        {
            item.Icon?.Dispose();
        }
        LocalItems.Clear();
        string query = LocalSearchBox?.Text ?? "";
        string typeFilter = ((ComboBoxItem)LocalTypeFilter.SelectedItem!).Content!.ToString()!;
        
        string targetDir = Path.Combine(_settings.InstancePath, typeFilter == "Mods" ? "mods" : "resourcepacks");
        if (!Directory.Exists(targetDir)) return;

        foreach (var file in Directory.GetFileSystemEntries(targetDir))
        {
            string fileName = Path.GetFileName(file);
            if (!string.IsNullOrEmpty(query) && !fileName.Contains(query, StringComparison.OrdinalIgnoreCase))
                continue;

            bool isEnabled = !fileName.EndsWith(".disabled");
            string displayName = isEnabled ? fileName : fileName.Substring(0, fileName.Length - 9);

            var item = new GalleryItem
            {
                Title = displayName,
                Author = "Local File",
                Description = file,
                ActionButtonText = "Update",
                IsInstalled = true,
                IsEnabled = isEnabled,
                ProjectId = file 
            };

            item.ToggleStateCommand = new RelayCommand(_ => ToggleModState(item));
            item.DeleteCommand = new RelayCommand(_ => DeleteMod(item, LocalItems));

            LocalItems.Add(item);
        }
    }

    private void ToggleModState(GalleryItem item)
    {
        string path = item.Description; 
        try
        {
            if (File.Exists(path) || Directory.Exists(path))
            {
                string newPath = item.IsEnabled ? (path.EndsWith(".disabled") ? path.Substring(0, path.Length - 9) : path) : path + ".disabled";
                if (Directory.Exists(path))
                    Directory.Move(path, newPath);
                else
                    File.Move(path, newPath);
                item.Description = newPath;
            }
        }
        catch { }
    }

    private void DeleteMod(GalleryItem item, ObservableCollection<GalleryItem> collection)
    {
        string path = item.Description;
        try
        {
            if (File.Exists(path))
            {
                File.Delete(path);
                collection.Remove(item);
            }
            else if (Directory.Exists(path))
            {
                Directory.Delete(path, true);
                collection.Remove(item);
            }
        }
        catch (IOException)
        {
        }
    }

    // --- Discover ---
    private void DiscoverSearchBtn_Click(object? sender, RoutedEventArgs e)
    {
        _ = PerformDiscoverSearch();
    }

    private void DiscoverSearchBox_KeyUp(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter) _ = PerformDiscoverSearch();
    }

    private void DiscoverFilter_SelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (DiscoverSearchBox == null) return;
        _ = PerformDiscoverSearch();
    }

    private async Task PerformDiscoverSearch()
    {
        StatusText.IsVisible = true;
        StatusText.Text = "Searching...";
        foreach (var item in DiscoverItems)
        {
            item.Icon?.Dispose();
        }
        DiscoverItems.Clear();

        string query = DiscoverSearchBox.Text ?? "";
        string type = ((ComboBoxItem)DiscoverTypeFilter.SelectedItem!).Content!.ToString()!;
        string source = ((ComboBoxItem)DiscoverSourceFilter.SelectedItem!).Content!.ToString()!;

        try
        {
            string targetVersion = string.IsNullOrEmpty(_settings.SelectedMinecraftVersionOverride) 
                ? "1.21.2" : _settings.SelectedMinecraftVersionOverride;
            string projectType = type == "Mods" ? "mod" : "resourcepack";

            if (source == "Modrinth")
            {
                await SearchModrinth(query, targetVersion, projectType);
            }
            else if (source == "CurseForge")
            {
                if (string.IsNullOrEmpty(_settings.CurseForgeApiKey))
                {
                    StatusText.Text = "CurseForge API Key is missing in settings.";
                    return;
                }
                await SearchCurseForge(query, targetVersion, projectType);
            }
        }
        catch (Exception ex)
        {
            StatusText.Text = $"Error: {ex.Message}";
        }
        finally
        {
            if (StatusText.Text == "Searching...") StatusText.IsVisible = false;
        }
    }

    private async Task SearchModrinth(string query, string version, string type)
    {
        string facets = $"[[\"versions:{version}\"],[\"project_type:{type}\"]]";
        string url = $"{_settings.ModrinthApiUrl}/search?query={Uri.EscapeDataString(query)}&facets={Uri.EscapeDataString(facets)}";
        var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Add("User-Agent", "TheLadsLauncher/1.0");
        var response = await _httpClient.SendAsync(request);
        response.EnsureSuccessStatusCode();

        using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        var hits = doc.RootElement.GetProperty("hits");
        foreach (var hit in hits.EnumerateArray())
        {
            var item = new GalleryItem
            {
                Title = hit.GetProperty("title").GetString() ?? "",
                Author = hit.GetProperty("author").GetString() ?? "",
                Description = hit.GetProperty("description").GetString() ?? "",
                IconUrl = hit.TryGetProperty("icon_url", out var iconElem) ? iconElem.GetString() : null,
                ActionButtonText = "Download",
                IsInstalled = false,
                ProjectId = hit.GetProperty("project_id").GetString()
            };
            item.ActionCommand = new RelayCommand(_ => { _ = DownloadItemAsync(item, type, "Modrinth"); });
            DiscoverItems.Add(item);
            _ = LoadIconAsync(item);
        }
    }

    private async Task SearchCurseForge(string query, string version, string type)
    {
        int classId = type == "mod" ? 6 : 12; // 6 = Mods, 12 = ResourcePacks
        string url = $"{_settings.CurseForgeApiUrl}/mods/search?gameId=432&classId={classId}&searchFilter={Uri.EscapeDataString(query)}&gameVersion={Uri.EscapeDataString(version)}";
        
        var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Add("x-api-key", _settings.CurseForgeApiKey);
        var response = await _httpClient.SendAsync(request);
        response.EnsureSuccessStatusCode();

        using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        var data = doc.RootElement.GetProperty("data");
        foreach (var mod in data.EnumerateArray())
        {
            string? logoUrl = null;
            if (mod.TryGetProperty("logo", out var logoElem) && logoElem.ValueKind != JsonValueKind.Null)
            {
                logoUrl = logoElem.GetProperty("url").GetString();
            }

            var authors = mod.GetProperty("authors");
            string authorName = authors.GetArrayLength() > 0 ? authors[0].GetProperty("name").GetString() ?? "" : "";

            var item = new GalleryItem
            {
                Title = mod.GetProperty("name").GetString() ?? "",
                Author = authorName,
                Description = mod.GetProperty("summary").GetString() ?? "",
                IconUrl = logoUrl,
                ActionButtonText = "Download",
                IsInstalled = false,
                ProjectId = mod.GetProperty("id").GetInt32().ToString()
            };
            item.ActionCommand = new RelayCommand(_ => { _ = DownloadItemAsync(item, type, "CurseForge"); });
            DiscoverItems.Add(item);
            _ = LoadIconAsync(item);
        }
    }

    private async Task LoadIconAsync(GalleryItem item)
    {
        if (string.IsNullOrEmpty(item.IconUrl)) return;
        try
        {
            var bytes = await _httpClient.GetByteArrayAsync(item.IconUrl);
            using var stream = new MemoryStream(bytes);
            item.Icon = Bitmap.DecodeToWidth(stream, 64);
        }
        catch { }
    }

    private async Task DownloadItemAsync(GalleryItem item, string projectType, string source)
    {
        item.ActionButtonText = "Downloading...";
        string targetVersion = string.IsNullOrEmpty(_settings.SelectedMinecraftVersionOverride) 
            ? "1.21.2" : _settings.SelectedMinecraftVersionOverride;
        string targetDir = Path.Combine(_settings.InstancePath, projectType == "mod" ? "mods" : "resourcepacks");
        Directory.CreateDirectory(targetDir);

        try
        {
            string downloadUrl = "";
            string fileName = "";

            if (source == "Modrinth")
            {
                string url = $"{_settings.ModrinthApiUrl}/project/{item.ProjectId}/version?game_versions=[%22{targetVersion}%22]";
                var req = new HttpRequestMessage(HttpMethod.Get, url);
                req.Headers.Add("User-Agent", "TheLadsLauncher/1.0");
                var res = await _httpClient.SendAsync(req);
                res.EnsureSuccessStatusCode();
                using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync());
                var versions = doc.RootElement;
                if (versions.ValueKind == JsonValueKind.Array && versions.GetArrayLength() > 0)
                {
                    var files = versions[0].GetProperty("files");
                    if (files.GetArrayLength() > 0)
                    {
                        var primaryFile = files[0];
                        foreach(var f in files.EnumerateArray())
                        {
                            if (f.TryGetProperty("primary", out var prim) && prim.GetBoolean())
                            {
                                primaryFile = f;
                                break;
                            }
                        }
                        downloadUrl = primaryFile.GetProperty("url").GetString() ?? "";
                        fileName = primaryFile.GetProperty("filename").GetString() ?? "";
                    }
                }
            }
            else if (source == "CurseForge")
            {
                string url = $"{_settings.CurseForgeApiUrl}/mods/{item.ProjectId}/files?gameVersion={Uri.EscapeDataString(targetVersion)}";
                var req = new HttpRequestMessage(HttpMethod.Get, url);
                req.Headers.Add("x-api-key", _settings.CurseForgeApiKey);
                var res = await _httpClient.SendAsync(req);
                res.EnsureSuccessStatusCode();
                using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync());
                var data = doc.RootElement.GetProperty("data");
                if (data.ValueKind == JsonValueKind.Array && data.GetArrayLength() > 0)
                {
                    var firstFile = data[0];
                    downloadUrl = firstFile.GetProperty("downloadUrl").GetString() ?? "";
                    fileName = firstFile.GetProperty("fileName").GetString() ?? "";
                    
                    if (string.IsNullOrEmpty(downloadUrl))
                    {
                        int fileId = firstFile.GetProperty("id").GetInt32();
                        string fileStr = fileId.ToString();
                        if (fileStr.Length >= 4)
                        {
                            string p1 = fileStr.Substring(0, 4);
                            string p2 = fileStr.Substring(4);
                            downloadUrl = $"https://edge.forgecdn.net/files/{p1}/{p2}/{fileName}";
                        }
                    }
                }
            }

            if (!string.IsNullOrEmpty(downloadUrl) && !string.IsNullOrEmpty(fileName))
            {
                var bytes = await _httpClient.GetByteArrayAsync(downloadUrl);
                string safeFileName = Path.GetFileName(fileName);
                string savePath = Path.Combine(targetDir, safeFileName);
                await File.WriteAllBytesAsync(savePath, bytes);
                item.ActionButtonText = "Downloaded";
                item.IsInstalled = true;
            }
            else
            {
                item.ActionButtonText = "Failed";
            }
        }
        catch
        {
            item.ActionButtonText = "Failed";
        }
    }
}

public class ScreenshotItem : System.ComponentModel.INotifyPropertyChanged
{
    public string FilePath { get; set; } = "";
    public string FileName { get; set; } = "";
    public Bitmap? Image { get; set; }
    
    private bool _isFavorite;
    public bool IsFavorite 
    { 
        get => _isFavorite; 
        set 
        {
            _isFavorite = value;
            PropertyChanged?.Invoke(this, new System.ComponentModel.PropertyChangedEventArgs(nameof(IsFavorite)));
        }
    }

    public ICommand? ToggleFavoriteCommand { get; set; }
    public ICommand? DeleteCommand { get; set; }

    public event System.ComponentModel.PropertyChangedEventHandler? PropertyChanged;
}

public class GalleryItem : System.ComponentModel.INotifyPropertyChanged
{
    public string Title { get; set; } = "";
    public string Author { get; set; } = "";
    public string Description { get; set; } = "";
    public string? IconUrl { get; set; }
    
    private Bitmap? _icon;
    public Bitmap? Icon 
    { 
        get => _icon; 
        set 
        {
            _icon = value;
            PropertyChanged?.Invoke(this, new System.ComponentModel.PropertyChangedEventArgs(nameof(Icon)));
        }
    } 
    
    private string _actionButtonText = "Download";
    public string ActionButtonText 
    {
        get => _actionButtonText;
        set
        {
            _actionButtonText = value;
            PropertyChanged?.Invoke(this, new System.ComponentModel.PropertyChangedEventArgs(nameof(ActionButtonText)));
        }
    }
    public bool IsInstalled { get; set; }
    
    private bool _isEnabled;
    public bool IsEnabled 
    {
        get => _isEnabled;
        set
        {
            _isEnabled = value;
            PropertyChanged?.Invoke(this, new System.ComponentModel.PropertyChangedEventArgs(nameof(IsEnabled)));
        }
    }
    
    public string? ProjectId { get; set; }

    public ICommand? ActionCommand { get; set; }
    public ICommand? ToggleStateCommand { get; set; }
    public ICommand? DeleteCommand { get; set; }

    public event System.ComponentModel.PropertyChangedEventHandler? PropertyChanged;
}

public class RelayCommand : ICommand
{
    private readonly Action<object?> _execute;
    public RelayCommand(Action<object?> execute) => _execute = execute;
    public event EventHandler? CanExecuteChanged;
    public bool CanExecute(object? parameter) => true;
    public void Execute(object? parameter) => _execute(parameter);
}
