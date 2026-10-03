using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Threading;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

/// <summary>Known servers (ServerNameResolver's list) with live status, and the chosen server list's saved servers.</summary>
public partial class ServersView : UserControl
{
    private static readonly IBrush Card = new SolidColorBrush(Color.Parse("#202126")), Muted = new SolidColorBrush(Color.Parse("#A0A1AA")),
        Online = new SolidColorBrush(Color.Parse("#6FD58A"));

    /// <summary>One known server's row and the controls its status fills in.</summary>
    private sealed record FindRow(KnownServer Server, Border Row, Border Icon, TextBlock Motd, TextBlock Players, Button Add);

    private readonly List<FindRow> _find = new();
    private readonly HashSet<string> _saved = new(StringComparer.OrdinalIgnoreCase);
    private ServerNameResolver.AutoName _autoName = new();
    private IReadOnlyList<ServerListTarget> _targets = Array.Empty<ServerListTarget>();

    public ServersView() => InitializeComponent();

    private ServerListTarget? Selected => Target.SelectedIndex >= 0 && Target.SelectedIndex < _targets.Count ? _targets[Target.SelectedIndex] : null;

    public async Task LoadAsync()
    {
        if (_find.Count == 0)
            foreach (var server in ServerNameResolver.Servers) _find.Add(CreateRow(server));
        var label = Selected?.Label;
        _targets = ServerListService.Targets(SharedContentService.Instance, PathService.Instance.BaseDirectory);
        Target.ItemsSource = _targets.Select(t => t.Label).ToList();
        Target.SelectedIndex = Math.Max(0, _targets.ToList().FindIndex(t => t.Label == label));
        ReadSaved();
        Filter();
        await Task.WhenAll(_find.Select(ShowStatusAsync));
    }

    /// <summary>The selected list's servers. An unreadable list is reported and left alone: nothing can be added to it.</summary>
    private void ReadSaved()
    {
        SavedRows.Children.Clear();
        _saved.Clear();
        if (Selected is not { } target) return;
        IReadOnlyList<ServerListEntry> entries;
        try { entries = ServerListService.Read(target); }
        catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException)
        {
            Status.Text = e.Message;
            SavedTitle.Text = "Saved servers (unreadable)";
            foreach (var row in _find) row.Add.IsEnabled = false;
            return;
        }
        foreach (var entry in entries)
        {
            _saved.Add(entry.Ip.Trim());
            var remove = new Button { Content = "Remove", Classes = { "danger" }, VerticalAlignment = VerticalAlignment.Center };
            remove.Click += async (_, _) => await ChangeAsync(() => ServerListService.RemoveAsync(target, entry.Ip), $"Removed {entry.Name}.");
            var text = new StackPanel { Spacing = 2 };
            text.Children.Add(new TextBlock { Text = entry.Name.Length > 0 ? entry.Name : entry.Ip, FontWeight = FontWeight.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis });
            text.Children.Add(new TextBlock { Text = entry.Ip, Foreground = Muted, FontSize = 12, TextTrimming = TextTrimming.CharacterEllipsis });
            var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            grid.Children.Add(text);
            Grid.SetColumn(remove, 1);
            grid.Children.Add(remove);
            SavedRows.Children.Add(new Border { Child = grid, Padding = new Thickness(12, 8), CornerRadius = new CornerRadius(4), Background = Card });
        }
        SavedTitle.Text = $"Saved servers ({entries.Count})";
        if (entries.Count == 0) SavedRows.Children.Add(new TextBlock { Text = "No servers in this list yet.", Foreground = Muted });
        foreach (var row in _find)
        {
            var saved = _saved.Contains(row.Server.Address);
            row.Add.Content = saved ? "Added" : "Add";
            row.Add.IsEnabled = !saved;
        }
    }

    private FindRow CreateRow(KnownServer server)
    {
        var add = new Button { Content = "Add", Classes = { "action" }, HorizontalAlignment = HorizontalAlignment.Stretch, HorizontalContentAlignment = HorizontalAlignment.Center };
        add.Click += async (_, _) =>
        {
            if (Selected is not { } target) return;
            var name = ServerNameResolver.Resolve(server.Address) ?? server.Name;
            await ChangeAsync(() => ServerListService.AddAsync(target, name, server.Address), $"Added {name} to {target.Label}.");
        };
        var icon = new Border { Width = 48, Height = 48, CornerRadius = new CornerRadius(4), Background = new SolidColorBrush(Color.Parse("#2C2D33")), VerticalAlignment = VerticalAlignment.Top };
        var motd = new TextBlock { Text = server.Description, TextWrapping = TextWrapping.Wrap, FontSize = 12, MaxLines = 2 };
        var text = new StackPanel { Spacing = 3, Margin = new Thickness(12, 0) };
        text.Children.Add(new TextBlock { Text = server.Name, FontSize = 15, FontWeight = FontWeight.SemiBold });
        text.Children.Add(new TextBlock { Text = $"{server.Category}  ·  {server.Address}", Foreground = Muted, FontSize = 12 });
        text.Children.Add(motd);
        var players = new TextBlock { Text = "Checking…", Foreground = Muted, FontSize = 12, HorizontalAlignment = HorizontalAlignment.Right };
        var right = new StackPanel { Spacing = 6, Width = 100, VerticalAlignment = VerticalAlignment.Center };
        right.Children.Add(players);
        right.Children.Add(add);
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };
        grid.Children.Add(icon);
        Grid.SetColumn(text, 1);
        grid.Children.Add(text);
        Grid.SetColumn(right, 2);
        grid.Children.Add(right);
        var row = new Border { Child = grid, Padding = new Thickness(12), CornerRadius = new CornerRadius(4), Background = Card };
        FindRows.Children.Add(row);
        return new FindRow(server, row, icon, motd, players, add);
    }

    private static async Task ShowStatusAsync(FindRow row)
    {
        var status = await ServerStatusService.GetAsync(row.Server.Address);
        await Dispatcher.UIThread.InvokeAsync(() =>
        {
            row.Players.Text = status == null ? "Status unavailable" : status.Online ? $"{status.Players:N0} online" : "Offline";
            row.Players.Foreground = status is { Online: true } ? Online : Muted;
            if (status is { Online: true, Motd.Length: > 0 }) row.Motd.Text = status.Motd;
            if (status?.Icon is not { } png) return;
            try { row.Icon.Child = new Image { Source = new Bitmap(new MemoryStream(png)), Width = 48, Height = 48 }; }
            catch (Exception) { } // a broken favicon keeps the placeholder
        });
    }

    private async Task ChangeAsync(Func<Task<bool>> change, string done)
    {
        try { Status.Text = await change() ? done : "That server is already in this list."; }
        catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException) { Status.Text = e.Message; }
        ReadSaved();
    }

    private void Filter()
    {
        var query = (Search.Text ?? "").Trim();
        foreach (var row in _find)
            row.Row.IsVisible = query.Length == 0 || new[] { row.Server.Name, row.Server.Address, row.Server.Category, row.Server.Description }
                .Any(field => field.Contains(query, StringComparison.OrdinalIgnoreCase));
    }

    private void AddressChanged(object? sender, TextChangedEventArgs e)
    {
        if (_autoName.Update(NameBox.Text ?? "", AddressBox.Text ?? "") is { } name) NameBox.Text = name;
    }

    private async void AddTyped(object? sender, RoutedEventArgs e) => await AddTypedAsync();

    private async Task AddTypedAsync()
    {
        var address = (AddressBox.Text ?? "").Trim();
        if (Selected is not { } target) return;
        if (address.Length == 0) { Status.Text = "Type the server's address first."; return; }
        var name = string.IsNullOrWhiteSpace(NameBox.Text) ? ServerNameResolver.Resolve(address) ?? ServerNameResolver.DefaultName : NameBox.Text.Trim();
        await ChangeAsync(() => ServerListService.AddAsync(target, name, address), $"Added {name} to {target.Label}.");
        if (!_saved.Contains(address)) return;
        _autoName = new ServerNameResolver.AutoName();
        AddressBox.Text = NameBox.Text = "";
    }

    private void TargetChanged(object? sender, SelectionChangedEventArgs e)
    {
        Status.Text = Selected is { } target ? "Server list: " + target.File : "";
        ReadSaved();
    }

    private void SearchChanged(object? sender, TextChangedEventArgs e) => Filter();

    private async void Refresh(object? sender, RoutedEventArgs e)
    {
        ServerStatusService.ClearCache();
        foreach (var row in _find) row.Players.Text = "Checking…";
        await LoadAsync();
    }

    /// <summary>--preview-servers (sandboxed launcher only): the tab with live status, then a search with a typed address, then
    /// both added to the global list, then the first modpack instance's own list.</summary>
    public async Task PreviewAsync(Action<string> screenshot)
    {
        await LoadAsync();
        await Task.Delay(1500);
        screenshot("servers.png");
        Search.Text = "anarchy";
        AddressBox.Text = "play.funnyservername.com";
        await Task.Delay(500);
        screenshot("servers-search-typed.png");
        await AddTypedAsync();
        Search.Text = "";
        var hypixel = _find.First(f => f.Server.Name == "Hypixel");
        await ChangeAsync(() => ServerListService.AddAsync(_targets[0], ServerNameResolver.Resolve(hypixel.Server.Address)!, hypixel.Server.Address), "Added Hypixel.");
        await Task.Delay(500);
        screenshot("servers-added.png");
        if (_targets.Count < 2) return;
        Target.SelectedIndex = 1;
        await ChangeAsync(() => ServerListService.AddAsync(_targets[1], "CubeCraft", "play.cubecraft.net"), "Added CubeCraft to " + _targets[1].Label + ".");
        await Task.Delay(500);
        screenshot("servers-modpack-target.png");
    }
}
