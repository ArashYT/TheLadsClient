using System;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Shapes;
using Avalonia.Layout;
using Avalonia.Media;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Views;

/// <summary>The Friends page: what <see cref="FriendsService"/> knows, with Join / Invite / Accept. What a button does (launch the game,
/// or hand it to the running one) is the window's: it handles the events.</summary>
public partial class FriendsView : UserControl
{
    private static readonly IBrush OnlineDot = Brush.Parse("#4CC38A"), PlayingDot = Brush.Parse("#5B8DEF"), IdleDot = Brush.Parse("#4A4B55"),
        Muted = Brush.Parse("#8F919C");

    public event Action<FriendsService.Friend>? JoinClicked, InviteClicked;
    public event Action<FriendsService.Invite>? AcceptClicked, DeclineClicked;

    public FriendsView() => InitializeComponent();

    public void Show(FriendsService.Snapshot snap, bool gameRunning)
    {
        StatusDot.Fill = snap.Connected ? OnlineDot : IdleDot;
        int online = 0;
        foreach (var f in snap.Friends) if (f.Online) online++;
        StatusText.Text = snap.Connected ? $"{snap.Status} · {online} of {snap.Friends.Count} online" : snap.Status;

        InvitesList.Children.Clear();
        foreach (var invite in snap.Invites) InvitesList.Children.Add(InviteRow(invite, snap));
        InvitesPanel.IsVisible = snap.Invites.Count > 0;

        FriendsList.Children.Clear();
        foreach (var friend in snap.Friends) FriendsList.Children.Add(FriendRow(friend, snap, gameRunning));
        Empty.IsVisible = snap.Friends.Count == 0;
        (EmptyTitle.Text, EmptyHint.Text) = snap.Connected
            ? ("No friends yet", "Add friends in game: open the Friends screen and type their Minecraft name. They show up here too.")
            : ("Friends are offline", snap.Status);
    }

    private Control FriendRow(FriendsService.Friend f, FriendsService.Snapshot snap, bool gameRunning)
    {
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };
        var dot = new Ellipse
        {
            Width = 10, Height = 10, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(2, 0, 12, 0),
            Fill = f.State switch { "offline" => IdleDot, "menu" => OnlineDot, _ => PlayingDot },
        };
        grid.Children.Add(dot);
        var text = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
        text.Children.Add(new TextBlock { Text = f.Shown, FontWeight = FontWeight.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis });
        text.Children.Add(new TextBlock { Text = FriendsService.Describe(f), Foreground = Muted, FontSize = 12, TextTrimming = TextTrimming.CharacterEllipsis });
        Grid.SetColumn(text, 1);
        grid.Children.Add(text);

        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        // Join: a world open to friends (the host lets us in), or a shared server (the launcher starts the game there).
        bool serverJoin = f.State == "server" && !string.IsNullOrEmpty(f.Server) && !gameRunning;
        if (f.Joinable || serverJoin)
        {
            var join = Action("Join", "play", f.Joinable ? $"Ask {f.Shown} to let you into {f.World!.Name}" : $"Start the game and join {f.Server}");
            join.Classes.Add("launch");
            join.IsEnabled = snap.Connected;
            join.Click += (_, _) => JoinClicked?.Invoke(f);
            actions.Children.Add(join);
        }
        if (f.Online)
        {
            var invite = Action("Invite", "user-add", snap.CanInvite ? $"Invite {f.Shown} to where you are playing"
                : gameRunning ? "Open a world for friends (or join a server) in game to invite" : "Start the game and open a world to invite friends");
            invite.IsEnabled = snap.CanInvite;
            invite.Click += (_, _) =>
            {
                InviteClicked?.Invoke(f);
                invite.IsEnabled = false;
                invite.Content = "Invited";
            };
            actions.Children.Add(invite);
        }
        Grid.SetColumn(actions, 2);
        grid.Children.Add(actions);

        var row = new Border { Child = grid, Padding = new Thickness(12, 10), Opacity = f.Online ? 1 : 0.6 };
        row.Classes.Add("card");
        return row;
    }

    private Control InviteRow(FriendsService.Invite invite, FriendsService.Snapshot snap)
    {
        string from = invite.From;
        foreach (var f in snap.Friends) if (f.Uuid == invite.From) from = f.Shown;
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        grid.Children.Add(new TextBlock
        {
            Text = invite.Server != null ? $"{from} invited you to {invite.Server}" : $"{from} invited you to {invite.World ?? "their world"}",
            VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis,
        });
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        var accept = Action("Accept", "check", "Join now");
        accept.Classes.Add("launch");
        accept.Click += (_, _) => AcceptClicked?.Invoke(invite);
        var decline = Action("Decline", "cross", "Say no to this invite");
        decline.Click += (_, _) => DeclineClicked?.Invoke(invite);
        actions.Children.Add(accept);
        actions.Children.Add(decline);
        Grid.SetColumn(actions, 1);
        grid.Children.Add(actions);
        var row = new Border { Child = grid, Padding = new Thickness(12, 10) };
        row.Classes.Add("card");
        return row;
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
}
