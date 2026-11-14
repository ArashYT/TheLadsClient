using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Shapes;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Threading;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

/// <summary>The Friends page: what <see cref="FriendsService"/> knows, with Join / Invite / Accept, the party and a chat with the
/// picked friend. What a button does (launch the game, hand it to the running one, send on the launcher's link) is the window's.</summary>
public partial class FriendsView : UserControl
{
    private static readonly IBrush OnlineDot = Brush.Parse("#4CC38A"), PlayingDot = Brush.Parse("#5B8DEF"), IdleDot = Brush.Parse("#4A4B55"),
        Muted = Brush.Parse("#8F919C"), Mine = Brush.Parse("#3A2226"), Theirs = Brush.Parse("#24252B"), Selected = Brush.Parse("#241C1D"),
        SelectedBorder = Brush.Parse("#5A2A2E"), Badge = Brush.Parse("#E5484D"), Gold = Brush.Parse("#E8B04B");

    public event Action<FriendsService.Friend>? JoinClicked, InviteClicked, PartyInviteClicked, PictureClicked, ChatOpened;
    public event Action<FriendsService.Invite>? AcceptClicked, DeclineClicked;
    public event Action<FriendsService.Friend, string>? SendClicked;
    public event Action<FriendsService.PartyInvite, bool>? PartyAnswered;
    public event Action<FriendsService.PartyWarp, bool>? WarpAnswered;
    public event Action<FriendsService.PartyMember>? PartyKickClicked, PartyPromoteClicked;
    public event Action? PartyLeaveClicked;

    /// <summary>A player's head (by UUID), set by the window.</summary>
    public Func<string, Task<Bitmap?>>? Heads { get; set; }
    /// <summary>A message's picture or GIF, set by the window.</summary>
    public Func<FriendsService.Message, Task<FriendsMedia.Media?>>? MediaFor { get; set; }

    public string? SelectedUuid { get; private set; }
    private FriendsService.Snapshot _snap = FriendsService.Empty;
    private FriendsChat? _chat;
    private bool _gameRunning;
    private (string?, int, int) _chatShown;
    /// <summary>The chat sits at its newest message: pictures that load later keep it there.</summary>
    private bool _atBottom = true;

    public FriendsView()
    {
        InitializeComponent();
        SendButton.Click += (_, _) => Send();
        ChatInput.KeyDown += (_, e) =>
        {
            if (e.Key != Key.Enter || e.KeyModifiers.HasFlag(KeyModifiers.Shift)) return;
            e.Handled = true;
            Send();
        };
        PictureButton.Click += (_, _) => { if (Picked() is { } f) PictureClicked?.Invoke(f); };
        PartyLeave.Click += (_, _) => PartyLeaveClicked?.Invoke();
        GifSearchButton.Click += (_, _) => SearchTenor();
        GifSearch.KeyDown += (_, e) => { if (e.Key == Key.Enter) { e.Handled = true; SearchTenor(); } };
        // New messages or a picture that loaded grow the list: stay at the newest one unless the player scrolled up
        ChatList.SizeChanged += (_, e) =>
        {
            if (_atBottom && e.HeightChanged) Dispatcher.UIThread.Post(() => ChatScroll.ScrollToEnd(), DispatcherPriority.Background);
        };
        ChatScroll.ScrollChanged += (_, e) =>
        {
            if (e.ExtentDelta.Y == 0 && e.ViewportDelta.Y == 0)
                _atBottom = ChatScroll.Offset.Y >= ChatScroll.Extent.Height - ChatScroll.Viewport.Height - 24;
        };
    }

    private void SearchTenor()
    {
        string q = (GifSearch.Text ?? "").Trim();
        Open(q.Length == 0 ? "https://tenor.com/" : $"https://tenor.com/search/{Uri.EscapeDataString(q.Replace(' ', '-'))}-gifs");
    }

    private FriendsService.Friend? Picked() => _snap.Friends.FirstOrDefault(f => f.Uuid == SelectedUuid);

    private void Send()
    {
        string text = (ChatInput.Text ?? "").Trim();
        if (text.Length == 0 || Picked() is not { } f || !_snap.Connected) return;
        ChatInput.Text = "";
        _atBottom = true;
        SendClicked?.Invoke(f, text);
    }

    /// <summary>Opens the chat with this friend (the window's notifications).</summary>
    public void Select(string uuid)
    {
        SelectedUuid = uuid;
        _chatShown = default;
        Show(_snap, _gameRunning, _chat);
        if (Picked() is { } f) ChatOpened?.Invoke(f);
    }

    public void ShowError(string? error)
    {
        ChatError.Text = error ?? "";
        ChatError.IsVisible = !string.IsNullOrEmpty(error);
    }

    public void Show(FriendsService.Snapshot snap, bool gameRunning, FriendsChat? chat)
    {
        _snap = snap;
        _gameRunning = gameRunning;
        _chat = chat;
        StatusDot.Fill = snap.Connected ? OnlineDot : IdleDot;
        int online = snap.Friends.Count(f => f.Online);
        StatusText.Text = snap.Connected ? $"{snap.Status} · {online} of {snap.Friends.Count} online" : snap.Status;

        InvitesList.Children.Clear();
        if (snap.Warp is { } warp) InvitesList.Children.Add(WarpRow(warp, snap));
        foreach (var invite in snap.PartyInvites) InvitesList.Children.Add(PartyInviteRow(invite));
        foreach (var invite in snap.Invites) InvitesList.Children.Add(InviteRow(invite, snap));
        InvitesPanel.IsVisible = InvitesList.Children.Count > 0;

        ShowParty(snap);

        FriendsList.Children.Clear();
        foreach (var friend in snap.Friends) FriendsList.Children.Add(FriendRow(friend, snap, gameRunning));
        Empty.IsVisible = snap.Friends.Count == 0;
        (EmptyTitle.Text, EmptyHint.Text) = snap.Connected
            ? ("No friends yet", "Add friends in game: open the Friends screen and type their Minecraft name. They show up here too.")
            : ("Friends are offline", snap.Status);

        ShowChat();
    }

    // ---- The party ----

    private bool InParty(string uuid) => _snap.Party?.Members.Any(m => m.Uuid == uuid) == true;
    private bool Leading => _snap.Party is { } p && p.Leader == _snap.Self;

    /// <summary>Only a friend who is online and in no party with us, while we are in none or lead ours and it has room.</summary>
    private bool CanPartyInvite(FriendsService.Friend f) =>
        _snap.Connected && f.Online && !InParty(f.Uuid) && (_snap.Party == null || Leading && _snap.Party.Members.Count < FriendsService.PartyMax);

    private void ShowParty(FriendsService.Snapshot snap)
    {
        PartyMembers.Children.Clear();
        if (snap.Party is not { } party)
        {
            PartyTitle.Text = "Party";
            PartyHint.Text = "You are in no party. Press the party button on an online friend to invite them (up to 8 players).";
            PartyLeave.IsVisible = false;
            return;
        }
        PartyTitle.Text = $"Party · {party.Members.Count}/{FriendsService.PartyMax}";
        PartyHint.Text = Leading ? "You lead the party. When you join a server in game, your party is asked to follow." : "The leader picks where the party plays.";
        PartyLeave.IsVisible = true;
        PartyLeave.IsEnabled = snap.Connected;
        foreach (var m in party.Members)
        {
            var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };
            grid.Children.Add(Head(m.Uuid, 24, new Thickness(0, 0, 10, 0)));
            var name = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
            name.Children.Add(new TextBlock { Text = m.Uuid == snap.Self ? m.Name + " (you)" : m.Name, TextTrimming = TextTrimming.CharacterEllipsis });
            if (m.Uuid == party.Leader) name.Children.Add(new TextBlock { Text = "Leader", Foreground = Gold, FontSize = 11, FontWeight = FontWeight.SemiBold, VerticalAlignment = VerticalAlignment.Center });
            Grid.SetColumn(name, 1);
            grid.Children.Add(name);
            if (Leading && m.Uuid != snap.Self)
            {
                var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };
                var promote = Icon("star", $"Make {m.Name} the leader");
                promote.Click += (_, _) => PartyPromoteClicked?.Invoke(m);
                var kick = Icon("user-remove", $"Remove {m.Name} from the party");
                kick.Click += (_, _) => PartyKickClicked?.Invoke(m);
                promote.IsEnabled = kick.IsEnabled = snap.Connected;
                actions.Children.Add(promote);
                actions.Children.Add(kick);
                Grid.SetColumn(actions, 2);
                grid.Children.Add(actions);
            }
            PartyMembers.Children.Add(grid);
        }
    }

    // ---- The friends ----

    private Control FriendRow(FriendsService.Friend f, FriendsService.Snapshot snap, bool gameRunning)
    {
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };
        var head = new Panel { Margin = new Thickness(0, 0, 10, 0), VerticalAlignment = VerticalAlignment.Center };
        head.Children.Add(Head(f.Uuid, 32, default));
        head.Children.Add(new Ellipse
        {
            Width = 11, Height = 11, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Bottom,
            Margin = new Thickness(0, 0, -3, -3), Stroke = Brush.Parse("#1B1C20"), StrokeThickness = 2,
            Fill = f.State switch { "offline" => IdleDot, "menu" => OnlineDot, _ => PlayingDot },
        });
        grid.Children.Add(head);
        var text = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
        var nameLine = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
        nameLine.Children.Add(new TextBlock { Text = f.Shown, FontWeight = FontWeight.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis });
        if (_chat?.Unread(f.Uuid) is > 0 and var unread)
            nameLine.Children.Add(new Border
            {
                Background = Badge, CornerRadius = new CornerRadius(8), MinWidth = 16, Height = 16, Padding = new Thickness(4, 0), VerticalAlignment = VerticalAlignment.Center,
                Child = new TextBlock { Text = unread > 99 ? "99+" : unread.ToString(), FontSize = 10, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center },
            });
        text.Children.Add(nameLine);
        text.Children.Add(new TextBlock { Text = FriendsService.Describe(f), Foreground = Muted, FontSize = 12, TextTrimming = TextTrimming.CharacterEllipsis });
        Grid.SetColumn(text, 1);
        grid.Children.Add(text);

        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, VerticalAlignment = VerticalAlignment.Center };
        if (JoinButton(f, snap, gameRunning, compact: true) is { } join) actions.Children.Add(join);
        if (InviteButton(f, snap, gameRunning, compact: true) is { } invite) actions.Children.Add(invite);
        if (PartyButton(f, compact: true) is { } party) actions.Children.Add(party);
        Grid.SetColumn(actions, 2);
        grid.Children.Add(actions);

        bool picked = f.Uuid == SelectedUuid;
        var row = new Border
        {
            Child = grid, Padding = new Thickness(10, 8), Opacity = f.Online ? 1 : 0.6, Cursor = new Cursor(StandardCursorType.Hand),
            Background = picked ? Selected : Brush.Parse("#1B1C20"), BorderBrush = picked ? SelectedBorder : Brush.Parse("#2A2B31"),
            BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(8),
        };
        ToolTip.SetTip(row, $"Chat with {f.Shown}");
        row.PointerPressed += (_, e) =>
        {
            if (e.GetCurrentPoint(row).Properties.IsLeftButtonPressed) Select(f.Uuid);
        };
        return row;
    }

    private Button? JoinButton(FriendsService.Friend f, FriendsService.Snapshot snap, bool gameRunning, bool compact)
    {
        // Join: a world open to friends (the host lets us in), or a shared server (the launcher starts the game there).
        bool serverJoin = f.State == "server" && !string.IsNullOrEmpty(f.Server) && !gameRunning;
        if (!f.Joinable && !serverJoin) return null;
        var join = Action("Join", "play", f.Joinable ? $"Ask {f.Shown} to let you into {f.World!.Name}" : $"Start the game and join {f.Server}");
        join.Classes.Add("launch");
        if (compact) join.Padding = new Thickness(10, 0);
        join.IsEnabled = snap.Connected;
        join.Click += (_, _) => JoinClicked?.Invoke(f);
        return join;
    }

    private Button? InviteButton(FriendsService.Friend f, FriendsService.Snapshot snap, bool gameRunning, bool compact)
    {
        if (!f.Online) return null;
        string tip = snap.CanInvite ? $"Invite {f.Shown} to where you are playing"
            : gameRunning ? "Open a world for friends (or join a server) in game to invite" : "Start the game and open a world to invite friends";
        var invite = compact ? Icon("user-add", tip) : Action("Invite", "user-add", tip);
        invite.IsEnabled = snap.CanInvite;
        invite.Click += (_, _) =>
        {
            InviteClicked?.Invoke(f);
            invite.IsEnabled = false;
            if (!compact) invite.Content = "Invited";
        };
        return invite;
    }

    private Button? PartyButton(FriendsService.Friend f, bool compact)
    {
        if (!f.Online || InParty(f.Uuid)) return null;
        string tip = CanPartyInvite(f) ? $"Invite {f.Shown} to your party" : _snap.Party != null ? "Only the party leader invites, up to 8 players" : "Connect to invite to a party";
        var party = compact ? Icon("users", tip) : Action("Party", "users", tip);
        party.IsEnabled = CanPartyInvite(f);
        party.Click += (_, _) =>
        {
            PartyInviteClicked?.Invoke(f);
            party.IsEnabled = false;
            if (!compact) party.Content = "Invited";
        };
        return party;
    }

    // ---- The chat ----

    private void ShowChat()
    {
        var f = Picked();
        ChatEmpty.IsVisible = f == null;
        ChatPane.IsVisible = f != null;
        if (f == null) return;
        ChatName.Text = f.Shown;
        ChatState.Text = FriendsService.Describe(f);
        ChatActions.Children.Clear();
        if (JoinButton(f, _snap, _gameRunning, compact: false) is { } join) ChatActions.Children.Add(join);
        if (InviteButton(f, _snap, _gameRunning, compact: false) is { } invite) ChatActions.Children.Add(invite);
        if (PartyButton(f, compact: false) is { } party) ChatActions.Children.Add(party);
        ChatInput.IsEnabled = SendButton.IsEnabled = PictureButton.IsEnabled = GifButton.IsEnabled = _snap.Connected;
        ChatInput.Watermark = _snap.Connected ? $"Message {f.Shown} (Enter sends; a Tenor or Giphy link sends a GIF)" : "Connecting…";
        if (!_snap.FromGame) ShowError(_snap.Error);

        var messages = _chat?.Get(f.Uuid) ?? Array.Empty<FriendsService.Message>();
        var key = (f.Uuid, messages.Count, _chat?.Version ?? 0);
        if (key == _chatShown) return;
        bool newFriend = _chatShown.Item1 != f.Uuid;
        _chatShown = key;
        if (newFriend) _ = SetHead(ChatHead, f.Uuid);
        ChatList.Children.Clear();
        if (messages.Count == 0)
            ChatList.Children.Add(new TextBlock { Text = $"No messages with {f.Shown} yet. Say hi!", Foreground = Muted, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 20) });
        long lastTs = 0;
        foreach (var m in messages)
        {
            if (m.Ts - lastTs > 15 * 60_000)
                ChatList.Children.Add(new TextBlock { Text = When(m.Ts), Foreground = Muted, FontSize = 11, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 8, 0, 2) });
            lastTs = m.Ts;
            ChatList.Children.Add(Bubble(m, m.From == _snap.Self || (_snap.Self == null && m.From != f.Uuid)));
        }
        if (newFriend) _atBottom = true;
        if (_atBottom) Dispatcher.UIThread.Post(() => ChatScroll.ScrollToEnd(), DispatcherPriority.Background);
    }

    private static string When(long ts)
    {
        var t = DateTimeOffset.FromUnixTimeMilliseconds(ts).LocalDateTime;
        return t.Date == DateTime.Today ? t.ToString("t") : t.Date == DateTime.Today.AddDays(-1) ? "Yesterday " + t.ToString("t") : t.ToString("g");
    }

    private Control Bubble(FriendsService.Message m, bool mine)
    {
        var content = new StackPanel { Spacing = 4 };
        bool media = m.Img != null || m.Gif != null;
        if (media)
        {
            var image = new Image { MaxWidth = 320, MaxHeight = 240, Stretch = Stretch.Uniform, Cursor = new Cursor(StandardCursorType.Hand), HorizontalAlignment = HorizontalAlignment.Left };
            ToolTip.SetTip(image, m.Gif != null ? "Open the GIF" : "Open the picture");
            image.PointerPressed += (_, _) => Open(m.Gif ?? (FriendsMedia.LocalPicture(m.Img!) ?? FriendsMedia.ImageUrl(m.Img!)));
            var placeholder = new TextBlock { Text = m.Gif != null ? "GIF…" : "Picture…", Foreground = Muted, FontSize = 12 };
            content.Children.Add(placeholder);
            content.Children.Add(image);
            _ = LoadMedia(image, placeholder, m);
        }
        if (!media || (m.Gif == null && m.Text != FriendsMedia.ScreenshotText))
            content.Children.Add(new SelectableTextBlock { Text = m.Text, TextWrapping = TextWrapping.Wrap });
        var bubble = new Border
        {
            Child = content, Background = mine ? Mine : Theirs, CornerRadius = new CornerRadius(10), Padding = new Thickness(10, 7),
            MaxWidth = 460, HorizontalAlignment = mine ? HorizontalAlignment.Right : HorizontalAlignment.Left,
        };
        ToolTip.SetTip(bubble, When(m.Ts));
        return bubble;
    }

    private async Task LoadMedia(Image image, TextBlock placeholder, FriendsService.Message m)
    {
        var media = MediaFor == null ? null : await MediaFor(m);
        if (media == null || media.Frames.Count == 0)
        {
            placeholder.Text = m.Gif != null ? "GIF not available" : "Picture no longer available";
            return;
        }
        placeholder.IsVisible = false;
        image.Source = media.Frames[0];
        if (media.Frames.Count < 2) return;
        int frame = 0;
        var timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(media.Delays[0]) };
        timer.Tick += (_, _) =>
        {
            frame = (frame + 1) % media.Frames.Count;
            image.Source = media.Frames[frame];
            timer.Interval = TimeSpan.FromMilliseconds(media.Delays[frame]);
        };
        image.DetachedFromVisualTree += (_, _) => timer.Stop();
        if (TopLevel.GetTopLevel(image) != null) timer.Start();
        image.AttachedToVisualTree += (_, _) => timer.Start();
    }

    private static void Open(string target)
    {
        try { Process.Start(new ProcessStartInfo(target) { UseShellExecute = true }); }
        catch (Exception) { }
    }

    // ---- Invites ----

    private Control InviteRow(FriendsService.Invite invite, FriendsService.Snapshot snap)
    {
        string from = snap.Friends.FirstOrDefault(f => f.Uuid == invite.From)?.Shown ?? invite.From;
        return AnswerRow(invite.From, invite.Server != null ? $"{from} invited you to {invite.Server}" : $"{from} invited you to {invite.World ?? "their world"}",
            () => AcceptClicked?.Invoke(invite), () => DeclineClicked?.Invoke(invite), "Join now");
    }

    private Control PartyInviteRow(FriendsService.PartyInvite invite) =>
        AnswerRow(invite.From, $"{invite.Name} invited you to a party", () => PartyAnswered?.Invoke(invite, true), () => PartyAnswered?.Invoke(invite, false), "Join the party");

    private Control WarpRow(FriendsService.PartyWarp warp, FriendsService.Snapshot snap)
    {
        string leader = snap.Party?.Members.FirstOrDefault(m => m.Uuid == warp.From)?.Name ?? "The leader";
        return AnswerRow(warp.From, $"{leader} is taking the party to {warp.Name ?? warp.Server}", () => WarpAnswered?.Invoke(warp, true), () => WarpAnswered?.Invoke(warp, false), $"Start the game and join {warp.Server}");
    }

    private Control AnswerRow(string who, string text, Action yes, Action no, string yesTip)
    {
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*") , RowDefinitions = new RowDefinitions("Auto,Auto") };
        grid.Children.Add(Head(who, 24, new Thickness(0, 0, 10, 0)));
        var label = new TextBlock { Text = text, VerticalAlignment = VerticalAlignment.Center, TextWrapping = TextWrapping.Wrap };
        Grid.SetColumn(label, 1);
        grid.Children.Add(label);
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Margin = new Thickness(0, 8, 0, 0) };
        var accept = Action("Accept", "check", yesTip);
        accept.Classes.Add("launch");
        accept.Click += (_, _) => yes();
        var decline = Action("Decline", "cross", "Say no");
        decline.Click += (_, _) => no();
        actions.Children.Add(accept);
        actions.Children.Add(decline);
        Grid.SetRow(actions, 1);
        Grid.SetColumn(actions, 1);
        grid.Children.Add(actions);
        var row = new Border { Child = grid, Padding = new Thickness(12, 10) };
        row.Classes.Add("card");
        return row;
    }

    // ---- Pieces ----

    private Image Head(string uuid, double size, Thickness margin)
    {
        var image = new Image { Width = size, Height = size, Margin = margin, VerticalAlignment = VerticalAlignment.Center };
        RenderOptions.SetBitmapInterpolationMode(image, Avalonia.Media.Imaging.BitmapInterpolationMode.None);
        _ = SetHead(image, uuid);
        return image;
    }

    private async Task SetHead(Image image, string uuid)
    {
        if (Heads != null && await Heads(uuid) is { } head) image.Source = head;
    }

    private static Button Action(string text, string icon, string tip)
    {
        var button = new Button { Content = text, Height = 32, Padding = new Thickness(14, 0) };
        button.Classes.Add("action");
        TheLadsLauncher.Controls.Ico.SetName(button, icon);
        ToolTip.SetTip(button, tip);
        ToolTip.SetShowOnDisabled(button, true);
        return button;
    }

    private static Button Icon(string icon, string tip)
    {
        var button = new Button { Height = 30, Width = 32, Padding = new Thickness(0) };
        button.Classes.Add("action");
        TheLadsLauncher.Controls.Ico.SetName(button, icon);
        ToolTip.SetTip(button, tip);
        ToolTip.SetShowOnDisabled(button, true);
        return button;
    }
}
