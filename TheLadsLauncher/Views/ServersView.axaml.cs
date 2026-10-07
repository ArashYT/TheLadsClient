using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Documents;
using Avalonia.Controls.Primitives;
using Avalonia.Controls.Shapes;
using Avalonia.Input;
using Avalonia.Input.Platform;
using Avalonia.Interactivity;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Threading;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

/// <summary>
/// Servers: My Servers (the chosen server list's saved servers, with icon, coloured MOTD, players and ping) and Browse (the
/// known servers, ServerNameResolver's list, with live status and Add to list). Both read and add to the list picked above them.
/// </summary>
public partial class ServersView : UserControl
{
    private static readonly Dictionary<string, IBrush> BrushCache = new(StringComparer.OrdinalIgnoreCase);
    private static IBrush Hex(string hex) => BrushCache.TryGetValue(hex, out var brush) ? brush : BrushCache[hex] = new SolidColorBrush(Color.Parse(hex));
    private static readonly IBrush TextBrush = Hex("#E9E9EA"), Muted = Hex("#8F919C"), Faint = Hex("#62646E"), MotdBase = Hex("#B5B7C0"),
        OnlineDot = Hex("#4CC38A"), OfflineDot = Hex("#E5484D"), IdleDot = Hex("#4A4B55"), Tile = Hex("#24252B"), Skeleton = Hex("#25262C");

    // Material Design icons (Apache 2.0), 24x24 viewbox.
    private static readonly Geometry CopyIcon = Geometry.Parse("M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"),
        TrashIcon = Geometry.Parse("M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM8 9h8v10H8V9zm7.5-5l-1-1h-5l-1 1H5v2h14V4z"),
        UpIcon = Geometry.Parse("M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z"),
        DownIcon = Geometry.Parse("M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z"),
        RefreshIcon = Geometry.Parse("M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"),
        PlusIcon = Geometry.Parse("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"),
        CheckIcon = Geometry.Parse("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"),
        CloseIcon = Geometry.Parse("M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z");

    private const double TileMinWidth = 400, Gap = 12;
    private static readonly string[] SortNames = { "Most popular", "Most players", "Name" };

    /// <summary>One server's card (a saved row or a Browse tile) and the controls its status fills in.</summary>
    private sealed class ServerCard(string name, string address, KnownServer? known)
    {
        public string Name { get; } = name;
        public string Address { get; } = address;
        public KnownServer? Known { get; } = known;
        public Border Root = null!, Icon = null!;
        public bool HasIcon;
        public Ellipse Dot = null!;
        public TextBlock Players = null!, Ping = null!, Motd = null!, Hint = null!;
        public Control Skeleton = null!, StatusBox = null!;
        public Border? VersionChip;
        public Button? Add, Up, Down;
        public ServerStatus? Status;
        public Task StatusTask = Task.CompletedTask;
        public int Generation;
    }

    private readonly List<ServerCard> _find = new();
    private readonly Dictionary<string, ServerCard> _savedCards = new(StringComparer.OrdinalIgnoreCase);
    private List<ServerCard> _savedOrder = new();
    private readonly HashSet<string> _saved = new(StringComparer.OrdinalIgnoreCase);
    private ServerNameResolver.AutoName _autoName = new();
    private IReadOnlyList<ServerListTarget> _targets = Array.Empty<ServerListTarget>();
    private string? _savedFile, _readError;
    private bool _browseLoaded, _hookApplied;

    public ServersView()
    {
        InitializeComponent();
        RefreshGlyph.Data = RefreshGlyph2.Data = RefreshIcon;
        AddGlyph.Data = PlusIcon;
        CloseAddButton.Content = Glyph(CloseIcon, 13);
        Sort.ItemsSource = SortNames;
        Sort.SelectedIndex = 0;
        Category.ItemsSource = new[] { "All categories" }.Concat(ServerNameResolver.Servers.GroupBy(s => s.Category)
            .OrderByDescending(g => g.Count()).ThenBy(g => g.Key).Select(g => g.Key)).ToList();
        Category.SelectedIndex = 0;
    }

    private ServerListTarget? Selected => Target.SelectedIndex >= 0 && Target.SelectedIndex < _targets.Count ? _targets[Target.SelectedIndex] : null;
    private bool Browsing => Tabs.SelectedItem == BrowseTab;

    public async Task LoadAsync()
    {
        if (_find.Count == 0)
            foreach (var server in ServerNameResolver.Servers) _find.Add(CreateTile(server));
        var label = Selected?.Label;
        _targets = ServerListService.Targets(SharedContentService.Instance, PathService.Instance.BaseDirectory);
        Target.ItemsSource = _targets.Select(t => t.Label).ToList();
        Target.SelectedIndex = Math.Max(0, _targets.ToList().FindIndex(t => t.Label == label));
        ReadSaved();
        ApplyPreviewHook();
        Filter();
        await Task.WhenAll(_savedOrder.Concat(_browseLoaded ? _find : Enumerable.Empty<ServerCard>()).Select(c => c.StatusTask));
    }

    /// <summary>
    /// The selected list's servers. Cards are kept per address while the list stays the same, so adding, removing or moving one
    /// does not flash the others. An unreadable list is reported and left alone: nothing can be added to it.
    /// </summary>
    private void ReadSaved()
    {
        _saved.Clear();
        _readError = null;
        var target = Selected;
        if (target?.File != _savedFile) { _savedCards.Clear(); _savedFile = target?.File; }
        ToolTip.SetTip(Target, target?.File);
        var order = new List<ServerCard>();
        if (target != null)
        {
            try
            {
                foreach (var entry in ServerListService.Read(target))
                {
                    var address = entry.Ip.Trim();
                    if (!_saved.Add(address)) continue; // the game allows a duplicate; one card, as edits act on the first
                    var name = MinecraftText.Strip(entry.Name).Trim() is { Length: > 0 } n ? n : address;
                    if (!_savedCards.TryGetValue(address, out var card) || card.Name != name)
                        _savedCards[address] = card = CreateRow(name, address, ServerListService.Icon(entry));
                    order.Add(card);
                }
            }
            catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException)
            {
                _readError = e.Message;
                order.Clear();
            }
        }
        foreach (var gone in _savedCards.Keys.Where(k => !_saved.Contains(k)).ToList()) _savedCards.Remove(gone);
        _savedOrder = order;
        foreach (var tile in _find) ShowAddState(tile);
        RenderSaved();
    }

    // ───── cards ─────

    private ServerCard CreateRow(string name, string address, byte[]? storedIcon)
    {
        var card = new ServerCard(name, address, null);
        var copy = IconButton(CopyIcon, "Copy address");
        copy.Click += async (_, _) => await CopyAsync(card.Address);
        card.Up = IconButton(UpIcon, "Move up");
        card.Up.Click += async (_, _) => await MoveAsync(card, -1);
        card.Down = IconButton(DownIcon, "Move down");
        card.Down.Click += async (_, _) => await MoveAsync(card, 1);
        var remove = IconButton(TrashIcon, "Remove from this list");
        remove.Classes.Add("danger");
        remove.Click += async (_, _) => await RemoveAsync(card, remove);
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(18, 0, 0, 0) };
        actions.Classes.Add("rowActions");
        actions.Children.AddRange(new Control[] { copy, card.Up, card.Down, remove });

        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,2*,Auto,Auto") };
        grid.ColumnDefinitions[1].MinWidth = 140;
        grid.ColumnDefinitions[1].MaxWidth = 320; // a wide window gives the MOTD the room, not the gap after a short name
        grid.Children.Add(IconTile(card, 48));
        var info = Info(card, null);
        info.Margin = new Thickness(14, 0, 0, 0);
        Grid.SetColumn(info, 1);
        grid.Children.Add(info);
        var motd = MotdHost(card);
        motd.Margin = new Thickness(18, 0, 0, 0);
        Grid.SetColumn(motd, 2);
        grid.Children.Add(motd);
        var status = StatusBox(card);
        status.MinWidth = 110;
        status.Margin = new Thickness(18, 0, 0, 0);
        Grid.SetColumn(status, 3);
        grid.Children.Add(status);
        Grid.SetColumn(actions, 4);
        grid.Children.Add(actions);
        card.Root = new Border { Child = grid, Padding = new Thickness(12, 10) };
        card.Root.Classes.AddRange(new[] { "card", "server" });
        if (storedIcon != null) SetIcon(card, storedIcon);
        card.StatusTask = ShowStatusAsync(card);
        return card;
    }

    private ServerCard CreateTile(KnownServer server)
    {
        var card = new ServerCard(server.Name, server.Address, server);
        card.Add = new Button { Classes = { "action" }, Height = 32, Padding = new Thickness(12, 0), VerticalAlignment = VerticalAlignment.Center };
        card.Add.Click += async (_, _) =>
        {
            if (Selected is not { } target) return;
            var name = ServerNameResolver.Resolve(server.Address) ?? server.Name;
            await ChangeAsync(() => ServerListService.AddAsync(target, name, server.Address), $"Added {name} to {target.Label}.");
        };
        var top = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };
        top.Children.Add(IconTile(card, 48));
        var info = Info(card, null);
        info.Margin = new Thickness(12, 0, 8, 0);
        Grid.SetColumn(info, 1);
        top.Children.Add(info);
        var status = StatusBox(card);
        Grid.SetColumn(status, 2);
        top.Children.Add(status);
        var motd = MotdHost(card);
        motd.Margin = new Thickness(0, 12, 0, 0);
        card.VersionChip = Chip("", null);
        card.VersionChip.IsVisible = false;
        card.VersionChip.Child!.Untranslated();
        var chips = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center, ClipToBounds = true };
        chips.Children.Add(Chip(server.Category, "accent"));
        chips.Children.Add(card.VersionChip);
        var bottom = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), Margin = new Thickness(0, 12, 0, 0) };
        bottom.Children.Add(chips);
        Grid.SetColumn(card.Add, 1);
        bottom.Children.Add(card.Add);
        var stack = new StackPanel();
        stack.Children.AddRange(new Control[] { top, motd, bottom });
        card.Root = new Border { Child = stack, Padding = new Thickness(14), Margin = new Thickness(0, 0, Gap, Gap) };
        card.Root.Classes.AddRange(new[] { "card", "server" });
        SetMotdText(card, server.Description); // the curated blurb until the server's own MOTD arrives
        card.Skeleton.IsVisible = false;
        ShowAddState(card);
        return card;
    }

    /// <summary>Name and address; Browse tiles add their category chip elsewhere.</summary>
    private static StackPanel Info(ServerCard card, Control? extra)
    {
        var info = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
        info.Children.Add(new TextBlock { Text = card.Name, FontSize = 14.5, FontWeight = FontWeight.SemiBold, Foreground = TextBrush, TextTrimming = TextTrimming.CharacterEllipsis }.Untranslated());
        info.Children.Add(new TextBlock { Text = card.Address, FontSize = 12, Foreground = Muted, TextTrimming = TextTrimming.CharacterEllipsis }.Untranslated());
        if (extra != null) info.Children.Add(extra);
        return info;
    }

    /// <summary>The server's icon, or its initial on a tile until (unless) one arrives.</summary>
    private static Border IconTile(ServerCard card, double size)
    {
        card.Icon = new Border
        {
            Width = size, Height = size, CornerRadius = new CornerRadius(8), ClipToBounds = true, Background = Tile, VerticalAlignment = VerticalAlignment.Center,
            Child = new TextBlock
            {
                Text = card.Name.FirstOrDefault(char.IsLetterOrDigit) is var c and not '\0' ? char.ToUpperInvariant(c).ToString() : "?",
                FontSize = size * 0.42, FontWeight = FontWeight.Bold, Foreground = Faint, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center,
            }.Untranslated(),
        };
        return card.Icon;
    }

    /// <summary>Two lines for the MOTD: a skeleton while pinging, the coloured MOTD, or a muted hint when there is none.</summary>
    private static Panel MotdHost(ServerCard card)
    {
        card.Motd = new TextBlock
        {
            FontSize = 12.5, LineHeight = 17, TextWrapping = TextWrapping.Wrap, MaxLines = 2, TextTrimming = TextTrimming.CharacterEllipsis,
            Foreground = MotdBase, VerticalAlignment = VerticalAlignment.Center,
        }.Untranslated();
        card.Hint = new TextBlock { FontSize = 12, FontStyle = FontStyle.Italic, Foreground = Faint, VerticalAlignment = VerticalAlignment.Center, IsVisible = false, TextTrimming = TextTrimming.CharacterEllipsis };
        var skeleton = new StackPanel { Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        skeleton.Children.Add(new Border { Height = 8, Width = 230, CornerRadius = new CornerRadius(4), Background = Skeleton, HorizontalAlignment = HorizontalAlignment.Left });
        skeleton.Children.Add(new Border { Height = 8, Width = 150, CornerRadius = new CornerRadius(4), Background = Skeleton, HorizontalAlignment = HorizontalAlignment.Left });
        card.Skeleton = skeleton;
        var host = new Panel { Height = 34, ClipToBounds = true, VerticalAlignment = VerticalAlignment.Center };
        host.Children.AddRange(new Control[] { skeleton, card.Motd, card.Hint });
        return host;
    }

    private static StackPanel StatusBox(ServerCard card)
    {
        card.Dot = new Ellipse { Width = 8, Height = 8, Fill = IdleDot, VerticalAlignment = VerticalAlignment.Center };
        card.Players = new TextBlock { Text = "Pinging…", FontSize = 12.5, FontWeight = FontWeight.SemiBold, Foreground = Muted, VerticalAlignment = VerticalAlignment.Center };
        card.Ping = new TextBlock { FontSize = 11.5, Foreground = Muted, HorizontalAlignment = HorizontalAlignment.Right, IsVisible = false };
        var line = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 7, HorizontalAlignment = HorizontalAlignment.Right };
        line.Children.AddRange(new Control[] { card.Dot, card.Players });
        var box = new StackPanel { Spacing = 3, VerticalAlignment = VerticalAlignment.Center };
        box.Children.AddRange(new Control[] { line, card.Ping });
        card.StatusBox = box;
        return box;
    }

    private static Border Chip(string text, string? kind)
    {
        var chip = new Border { Child = new TextBlock { Text = text, TextTrimming = TextTrimming.CharacterEllipsis, MaxWidth = 200 } };
        chip.Classes.Add("chip");
        if (kind != null) chip.Classes.Add(kind);
        return chip;
    }

    private static PathIcon Glyph(Geometry data, double size) => new() { Data = data, Width = size, Height = size };

    private static Button IconButton(Geometry glyph, string tip)
    {
        var button = new Button { Content = Glyph(glyph, 14) };
        button.Classes.AddRange(new[] { "icon", "small" });
        ToolTip.SetTip(button, tip);
        return button;
    }

    private static StackPanel Labeled(Geometry glyph, string text)
    {
        var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
        panel.Children.Add(Glyph(glyph, 12));
        panel.Children.Add(new TextBlock { Text = text, VerticalAlignment = VerticalAlignment.Center });
        return panel;
    }

    private void ShowAddState(ServerCard tile)
    {
        var saved = _saved.Contains(tile.Address);
        var state = _readError != null ? "locked" : saved ? "added" : "add";
        if (tile.Add!.Tag as string == state) return;
        tile.Add.Tag = state;
        tile.Add.IsEnabled = state == "add";
        tile.Add.Content = saved ? Labeled(CheckIcon, "Added") : Labeled(PlusIcon, "Add to list");
    }

    // ───── status ─────

    private async Task ShowStatusAsync(ServerCard card)
    {
        var generation = ++card.Generation;
        var pending = ServerStatusService.GetAsync(card.Address);
        if (!pending.IsCompleted) ShowLoading(card);
        var status = await pending;
        if (generation != card.Generation) return;
        ShowStatus(card, status);
        UpdateSummary();
        if (status is not { Online: true }) return;
        var ping = await ServerStatusService.PingAsync(card.Address);
        if (generation != card.Generation || ping is not { } ms) return;
        card.Ping.Text = $"{ms} ms";
        card.Ping.IsVisible = true;
    }

    private static void ShowLoading(ServerCard card)
    {
        card.Dot.Fill = IdleDot;
        card.Players.Text = "Pinging…";
        card.Players.Foreground = Muted;
        card.Ping.Text = "";
        if (card.Known != null) return; // a tile keeps its description meanwhile
        card.Skeleton.IsVisible = true;
        card.Motd.IsVisible = card.Hint.IsVisible = false;
    }

    private void ShowStatus(ServerCard card, ServerStatus? status)
    {
        card.Status = status;
        card.Skeleton.IsVisible = false;
        if (status is { Online: true })
        {
            card.Dot.Fill = OnlineDot;
            card.Players.Text = status.MaxPlayers > 0 ? $"{status.Players:N0} / {status.MaxPlayers:N0}" : $"{status.Players:N0} online";
            card.Players.Foreground = TextBrush;
            ToolTip.SetTip(card.StatusBox, status.Version.Length > 0 ? status.Version : null);
            var motd = status.MotdRaw.Length > 0 ? status.MotdRaw : status.Motd;
            if (MinecraftText.Strip(motd).Trim().Length > 0) SetMotdText(card, motd);
            else if (card.Known != null) SetMotdText(card, card.Known.Description);
            else SetHint(card, "No message of the day.");
            if (card.VersionChip != null && status.Version.Length > 0)
            {
                ((TextBlock)card.VersionChip.Child!).Text = status.Version;
                card.VersionChip.IsVisible = true;
            }
            if (!card.HasIcon && status.Icon is { } png) SetIcon(card, png);
        }
        else
        {
            card.Dot.Fill = status == null ? IdleDot : OfflineDot;
            card.Players.Text = status == null ? "Unknown" : "Offline";
            card.Players.Foreground = Muted;
            card.Ping.Text = "";
            ToolTip.SetTip(card.StatusBox, status == null ? "The status services did not answer." : null);
            if (card.Known != null) SetMotdText(card, card.Known.Description);
            else SetHint(card, status == null ? "Couldn't check this server right now." : "Can't reach this server right now.");
        }
    }

    /// <summary>The MOTD with its Minecraft colours and styles (dark colours lifted to read on the card).</summary>
    private static void SetMotdText(ServerCard card, string text)
    {
        var inlines = new InlineCollection();
        var lines = MinecraftText.Lines(text);
        for (var i = 0; i < Math.Min(2, lines.Count); i++)
        {
            if (i > 0) inlines.Add(new LineBreak());
            foreach (var span in lines[i])
                inlines.Add(new Run(span.Text)
                {
                    Foreground = span.Color == null ? MotdBase : Hex(MinecraftText.Legible(span.Color)),
                    FontWeight = span.Bold ? FontWeight.Bold : FontWeight.Normal,
                    FontStyle = span.Italic ? FontStyle.Italic : FontStyle.Normal,
                    TextDecorations = span.Underline ? TextDecorations.Underline : span.Strikethrough ? TextDecorations.Strikethrough : null,
                });
        }
        card.Motd.Inlines = inlines;
        card.Motd.IsVisible = true;
        card.Hint.IsVisible = false;
    }

    private static void SetHint(ServerCard card, string text)
    {
        card.Hint.Text = text;
        card.Hint.IsVisible = true;
        card.Motd.IsVisible = false;
    }

    private static void SetIcon(ServerCard card, byte[] png)
    {
        try
        {
            var image = new Image { Source = new Bitmap(new MemoryStream(png)), Stretch = Stretch.UniformToFill };
            RenderOptions.SetBitmapInterpolationMode(image, BitmapInterpolationMode.HighQuality);
            card.Icon.Child = image;
            card.Icon.Background = Brushes.Transparent;
            card.HasIcon = true;
        }
        catch (Exception) { } // a broken icon keeps the initial
    }

    // ───── lists ─────

    private void RenderSaved()
    {
        var query = (MineSearch.Text ?? "").Trim();
        var shown = _savedOrder.Where(c => Matches(query, c.Name, c.Address, c.Status?.Motd)).ToList();
        SavedGrid.Children.Clear();
        foreach (var card in shown) SavedGrid.Children.Add(card.Root);
        for (var i = 0; i < shown.Count; i++)
        {
            // Moving within a filtered view would jump over hidden servers.
            shown[i].Up!.IsEnabled = query.Length == 0 && i > 0;
            shown[i].Down!.IsEnabled = query.Length == 0 && i < shown.Count - 1;
        }
        UpdateEmpty();
        UpdateSummary();
    }

    private void Filter()
    {
        if (_find.Count == 0) return;
        var query = (Search.Text ?? "").Trim();
        var category = Category.SelectedIndex > 0 ? Category.SelectedItem as string : null;
        var shown = _find.Where(c => (category == null || c.Known!.Category == category) && Matches(query, c.Name, c.Address, c.Known!.Category, c.Known.Description, c.Status?.Motd));
        shown = Sort.SelectedIndex switch
        {
            1 => shown.OrderByDescending(c => c.Status is { Online: true } s ? s.Players : -1),
            2 => shown.OrderBy(c => c.Name, StringComparer.OrdinalIgnoreCase),
            _ => shown, // known_servers.json's order: most popular first
        };
        FindGrid.Children.Clear();
        foreach (var card in shown) FindGrid.Children.Add(card.Root);
        UpdateEmpty();
        UpdateSummary();
    }

    private static bool Matches(string query, params string?[] fields) =>
        query.Length == 0 || fields.Any(f => f != null && f.Contains(query, StringComparison.OrdinalIgnoreCase));

    private void UpdateEmpty()
    {
        string? title = null, hint = null;
        var actions = false;
        if (Browsing)
        {
            if (_find.Count > 0 && FindGrid.Children.Count == 0) (title, hint) = ("No servers match", "Try another name, address or category.");
        }
        else if (_readError != null) (title, hint) = ("This server list can't be read", _readError);
        else if (_savedOrder.Count == 0) (title, hint, actions) = ("No servers in this list yet", "Pick some from Browse, or add one by its address.", true);
        else if (SavedGrid.Children.Count == 0) (title, hint) = ("No saved servers match", "Clear the filter to see the whole list.");
        Empty.IsVisible = title != null;
        EmptyTitle.Text = title ?? "";
        EmptyHint.Text = hint ?? "";
        EmptyActions.IsVisible = actions;
    }

    private void UpdateSummary()
    {
        if (Browsing)
        {
            var online = _find.Count(c => c.Status is { Online: true });
            Summary.Text = FindGrid.Children.Count == _find.Count ? $"{_find.Count} servers" : $"{FindGrid.Children.Count} of {_find.Count} servers";
            if (_browseLoaded && online > 0) Summary.Text += $"  ·  {online} online";
        }
        else if (_readError != null) Summary.Text = "";
        else
        {
            var online = _savedOrder.Count(c => c.Status is { Online: true });
            var checking = _savedOrder.Count(c => !c.StatusTask.IsCompleted);
            Summary.Text = $"{_savedOrder.Count} saved servers  ·  {online} online" + (checking > 0 ? $"  ·  checking {checking}…" : "");
        }
    }

    // ───── actions ─────

    private async Task ChangeAsync(Func<Task<bool>> change, string done, string unchanged = "That server is already in this list.")
    {
        try
        {
            var changed = await change();
            if (changed || unchanged.Length > 0) Status.Text = changed ? done : unchanged;
        }
        catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException or TimeoutException) { Status.Text = e.Message; }
        ReadSaved();
    }

    private async Task MoveAsync(ServerCard card, int offset)
    {
        if (Selected is not { } target) return;
        await ChangeAsync(() => ServerListService.MoveAsync(target, card.Address, offset), "", "");
    }

    /// <summary>Two clicks: the bin turns into a "Remove" button for a few seconds, so a stray click never loses a server.</summary>
    private async Task RemoveAsync(ServerCard card, Button button)
    {
        if (Selected is not { } target) return;
        if (!button.Classes.Contains("confirm"))
        {
            button.Classes.Add("confirm");
            button.Content = new TextBlock { Text = "Remove", FontSize = 12, FontWeight = FontWeight.SemiBold, VerticalAlignment = VerticalAlignment.Center };
            DispatcherTimer.RunOnce(() =>
            {
                button.Classes.Remove("confirm");
                button.Content = Glyph(TrashIcon, 14);
            }, TimeSpan.FromSeconds(3));
            return;
        }
        await ChangeAsync(() => ServerListService.RemoveAsync(target, card.Address), $"Removed {card.Name}.", "");
    }

    private async Task CopyAsync(string address)
    {
        try
        {
            if (TopLevel.GetTopLevel(this)?.Clipboard is { } clipboard)
            {
                await clipboard.SetTextAsync(address);
                Status.Text = $"Copied {address}.";
            }
        }
        catch (Exception) { Status.Text = "Couldn't copy to the clipboard."; }
    }

    private void AddressChanged(object? sender, TextChangedEventArgs e)
    {
        if (_autoName.Update(NameBox.Text ?? "", AddressBox.Text ?? "") is { } name) NameBox.Text = name;
    }

    private async void AddTyped(object? sender, RoutedEventArgs e) => await AddTypedAsync();

    private async void AddKeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape) { AddForm.IsVisible = false; e.Handled = true; }
        else if (e.Key == Key.Enter) { e.Handled = true; await AddTypedAsync(); }
    }

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
        AddForm.IsVisible = false;
    }

    private void ToggleAdd(object? sender, RoutedEventArgs e)
    {
        var open = Browsing || !AddForm.IsVisible;
        if (Browsing) Tabs.SelectedItem = MineTab;
        AddForm.IsVisible = open;
        if (open) Dispatcher.UIThread.Post(() => AddressBox.Focus());
    }

    private void CloseAdd(object? sender, RoutedEventArgs e) => AddForm.IsVisible = false;

    private void ShowBrowse(object? sender, RoutedEventArgs e) => Tabs.SelectedItem = BrowseTab;

    private void TabsChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (e.Source != Tabs || MineToolbar == null) return;
        var browse = Browsing;
        MineToolbar.IsVisible = MineScroll.IsVisible = !browse;
        BrowseToolbar.IsVisible = BrowseScroll.IsVisible = browse;
        if (browse) AddForm.IsVisible = false;
        if (browse && !_browseLoaded && _find.Count > 0)
        {
            _browseLoaded = true; // the known servers are pinged once Browse is first opened, after the saved ones
            foreach (var tile in _find) tile.StatusTask = ShowStatusAsync(tile);
        }
        UpdateEmpty();
        UpdateSummary();
    }

    private void TargetChanged(object? sender, SelectionChangedEventArgs e)
    {
        Status.Text = "";
        ReadSaved();
    }

    private void SearchChanged(object? sender, TextChangedEventArgs e) => Filter();

    private void BrowseFilterChanged(object? sender, SelectionChangedEventArgs e) => Filter();

    private void MineSearchChanged(object? sender, TextChangedEventArgs e) => RenderSaved();

    /// <summary>Browse tiles: as many columns of at least <see cref="TileMinWidth"/> as fit, so the grid always fills the width.</summary>
    private void ScrollSizeChanged(object? sender, SizeChangedEventArgs e) =>
        FindGrid.Columns = Math.Max(1, (int)((e.NewSize.Width + Gap) / (TileMinWidth + Gap)));

    private async void Refresh(object? sender, RoutedEventArgs e)
    {
        ServerStatusService.ClearCache();
        foreach (var card in _savedOrder.Concat(_browseLoaded ? _find : Enumerable.Empty<ServerCard>())) card.StatusTask = ShowStatusAsync(card);
        UpdateSummary();
        await Task.WhenAll(_savedOrder.Select(c => c.StatusTask));
        UpdateSummary();
        if (Sort.SelectedIndex == 1) Filter(); // player counts changed
    }

    /// <summary>
    /// --preview-page Servers (sandboxed launcher only): LADS_PREVIEW_SERVERS picks the state to capture: "browse" opens the
    /// Browse tab, "add" the Add server form with a typed address, "search:&lt;text&gt;" filters Browse.
    /// </summary>
    private void ApplyPreviewHook()
    {
        if (_hookApplied) return;
        _hookApplied = true;
        if (Environment.GetEnvironmentVariable("LADS_PREVIEW_SERVERS") is not { Length: > 0 } hook || Environment.GetEnvironmentVariable("THELADS_DIR") == null) return;
        foreach (var part in hook.Split(','))
        {
            if (part.Equals("browse", StringComparison.OrdinalIgnoreCase)) Tabs.SelectedItem = BrowseTab;
            else if (part.Equals("add", StringComparison.OrdinalIgnoreCase)) { AddForm.IsVisible = true; AddressBox.Text = "play.funnyservername.com"; }
            else if (part.StartsWith("search:", StringComparison.OrdinalIgnoreCase)) Search.Text = part[7..];
            else if (part.StartsWith("filter:", StringComparison.OrdinalIgnoreCase)) MineSearch.Text = part[7..];
        }
    }

    /// <summary>--preview-servers (sandboxed launcher only): the tab with live status, then a search with a typed address, then
    /// both added to the global list, then the first modpack instance's own list.</summary>
    public async Task PreviewAsync(Action<string> screenshot)
    {
        await LoadAsync();
        await Task.Delay(1500);
        screenshot("servers.png");
        Tabs.SelectedItem = BrowseTab;
        Search.Text = "anarchy";
        await Task.Delay(1500);
        screenshot("servers-search-typed.png");
        Tabs.SelectedItem = MineTab;
        AddForm.IsVisible = true;
        AddressBox.Text = "play.funnyservername.com";
        await AddTypedAsync();
        Search.Text = "";
        var hypixel = _find.First(f => f.Name == "Hypixel");
        await ChangeAsync(() => ServerListService.AddAsync(_targets[0], ServerNameResolver.Resolve(hypixel.Address)!, hypixel.Address), "Added Hypixel.");
        await Task.Delay(500);
        screenshot("servers-added.png");
        if (_targets.Count < 2) return;
        Target.SelectedIndex = 1;
        await ChangeAsync(() => ServerListService.AddAsync(_targets[1], "CubeCraft", "play.cubecraft.net"), "Added CubeCraft to " + _targets[1].Label + ".");
        await Task.Delay(500);
        screenshot("servers-modpack-target.png");
    }
}
