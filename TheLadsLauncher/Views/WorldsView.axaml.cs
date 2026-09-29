using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Media;
using Avalonia.Platform.Storage;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

public partial class WorldsView : UserControl
{
    private WorldCatalog _catalog = new(Array.Empty<WorldEntry>(), Array.Empty<string>());
    private readonly WorldCatalogService _service = new(PathService.Instance.BaseDirectory);
    private bool _loading;
    public WorldsView()
    {
        InitializeComponent();
        Location.ItemsSource = new[] { "All locations", "Global .minecraft", "Specific Version", "Custom Instances" };
        Location.SelectedIndex = 0;
        Version.ItemsSource = new[] { "All versions" };
        Version.SelectedIndex = 0;
    }

    public async Task LoadAsync()
    {
        if (_loading) return;
        _loading = true;
        Status.Text = "Reading worlds…";
        try
        {
            var sources = new List<WorldSource> { new("Global .minecraft", "Global .minecraft", SharedContentService.Instance.Root) };
            sources.AddRange(WorldCatalogService.ProfileSources(ProfileService.Instance.GetProfiles(), PathService.Instance));
            sources.AddRange(_service.LoadCustomSources());
            _catalog = await Task.Run(() => WorldCatalogService.Scan(sources));
            string selected = Version.SelectedItem as string ?? "All versions";
            var versions = new[] { "All versions" }.Concat(_catalog.Worlds.Select(w => w.Version).Where(v => v.Length > 0).Distinct().Order()).ToList();
            Version.ItemsSource = versions;
            Version.SelectedItem = versions.Contains(selected) ? selected : "All versions";
            RenderRows();
        }
        catch (Exception e) { Status.Text = "Unable to read worlds: " + e.Message; }
        finally { _loading = false; }
    }

    private void RenderRows()
    {
        if (Rows == null || Search == null || Location == null || Version == null) return;
        var filtered = WorldCatalogService.Filter(_catalog.Worlds, Search.Text ?? "", Location.SelectedItem as string ?? "All locations",
            Version.SelectedItem as string ?? "All versions").ToList();
        Rows.Children.Clear();
        foreach (var world in filtered)
        {
            var text = new StackPanel { Spacing = 4 };
            text.Children.Add(new TextBlock { Text = world.Name, FontSize = 16, FontWeight = FontWeight.SemiBold });
            text.Children.Add(new TextBlock { Text = $"{world.Category}  ·  {world.Source}  ·  {world.Version}  ·  {world.LastPlayed.ToLocalTime():g}", Foreground = Brushes.LightGray, TextWrapping = TextWrapping.Wrap });
            text.Children.Add(new TextBlock { Text = world.Folder, FontSize = 11, Foreground = Brushes.Gray, TextWrapping = TextWrapping.Wrap });
            if (world.Warning != null) text.Children.Add(new TextBlock { Text = world.Warning, TextWrapping = TextWrapping.Wrap, Foreground = Brushes.Orange });
            var open = new Button { Content = "Open folder", HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Left };
            open.Click += (_, _) => { try { Process.Start(new ProcessStartInfo(world.Folder) { UseShellExecute = true }); } catch (Exception e) { Status.Text = e.Message; } };
            text.Children.Add(open);
            Rows.Children.Add(new Border { Child = text, Padding = new Thickness(16), CornerRadius = new CornerRadius(4), Background = new SolidColorBrush(Color.Parse("#202126")) });
        }
        Status.Text = $"{filtered.Count} of {_catalog.Worlds.Count} worlds" + (filtered.Count == 0 ? " — no worlds match these filters." : "")
            + (_catalog.Warnings.Count > 0 ? "\n" + string.Join("\n", _catalog.Warnings) : "");
    }
    private void FilterChanged(object? sender, TextChangedEventArgs e) => RenderRows();
    private void SelectionChanged(object? sender, SelectionChangedEventArgs e) => RenderRows();
    private async void Refresh(object? sender, RoutedEventArgs e) => await LoadAsync();
    private void ResetFilters(object? sender, RoutedEventArgs e) { Search.Text = ""; Location.SelectedIndex = 0; Version.SelectedIndex = 0; RenderRows(); }
    private async void AddInstance(object? sender, RoutedEventArgs e)
    {
        var top = TopLevel.GetTopLevel(this);
        if (top == null) return;
        var folders = await top.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions { Title = "Choose an instance containing a saves folder", AllowMultiple = false });
        if (folders.Count == 0) return;
        try { _service.AddCustomSource(folders[0].Path.LocalPath); await LoadAsync(); }
        catch (Exception error) { Status.Text = error.Message; }
    }
}
