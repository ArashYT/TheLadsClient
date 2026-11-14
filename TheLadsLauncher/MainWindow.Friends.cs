using System;
using System.IO;
using System.Linq;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;
using Avalonia.Interactivity;
using Avalonia.Threading;
using CmlLib.Core.ProcessBuilder;
using TheLadsLauncher.Services;

namespace TheLadsLauncher;

/// <summary>The Friends page: the launcher's own social link while no game runs, the running game's list (and hands) while one does.</summary>
public partial class MainWindow
{
    private FriendsService? _friends;
    private DispatcherTimer? _friendsTimer;
    private string? _friendsAccount, _friendsGame;
    /// <summary>The -D argument the next launch carries for the Friends page (join a host, accept an invite) and when it was set.</summary>
    private (string Arg, DateTime At)? _socialLaunch;
    /// <summary>Until then the launcher's link stays up while the game starts: the backend drops a guest's invites when the guest's
    /// link closes, but not when the game's link replaces it, so an invite accepted here is still there for the game.</summary>
    private DateTime _friendsHandoverUntil;
    private FriendsService.Snapshot _friendsShown = FriendsService.Empty;

    private void StartFriends()
    {
        _friends = new FriendsService(_httpClient, Log);
        _friends.Changed += () => Dispatcher.UIThread.Post(FriendsTick);
        FriendsPage.JoinClicked += f => _ = FriendJoinAsync(f);
        FriendsPage.InviteClicked += FriendInvite;
        FriendsPage.AcceptClicked += i => _ = FriendAcceptAsync(i);
        FriendsPage.DeclineClicked += FriendDecline;
        _friendsTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3) };
        _friendsTimer.Tick += (_, _) => FriendsTick();
        _friendsTimer.Start();
        FriendsTick();
    }

    private void NavFriends_Click(object? sender, RoutedEventArgs e)
    {
        NavigateTo("Friends");
        _friendsShown = FriendsService.Empty with { Status = "" }; // shown again in full
        FriendsTick();
    }

    /// <summary>Picks the source (the game holds the link while it runs: two links on one account replace each other) and shows it.</summary>
    private void FriendsTick()
    {
        if (_friends == null) return;
        _friendsGame = _runningProcesses.Where(p => IsGameRunning(p.Key)).OrderByDescending(p => SafeStart(p.Key)).Select(p => p.Value).FirstOrDefault();
        FriendsService.Snapshot snap;
        if (_friendsGame != null || AnyGameRunning())
        {
            var fromGame = _friendsGame == null ? null : FriendsService.ReadGame(_friendsGame);
            if (DateTime.UtcNow >= _friendsHandoverUntil || fromGame is { Connected: true }) _friends.Stop();
            snap = fromGame ?? FriendsService.Empty with { FromGame = true, Status = "Your game is running; its friends list shows here once it has connected" };
        }
        else
        {
            if (_friendsAccount != _selectedAccount) _friends.Stop();
            _friendsAccount = _selectedAccount;
            if (!_friends.Running) _friends.Start(FriendsSessionAsync);
            snap = string.IsNullOrEmpty(_selectedAccount) && Environment.GetEnvironmentVariable("LADS_QA_SOCIAL_UUID") == null ? FriendsService.Empty with { Status = "Sign in to see your friends" } : _friends.Current;
        }
        FriendsBadgeText.Text = snap.Invites.Count.ToString();
        FriendsBadge.IsVisible = snap.Invites.Count > 0;
        if (Same(snap, _friendsShown)) return;
        _friendsShown = snap;
        if (FriendsPage.IsVisible) FriendsPage.Show(snap, _friendsGame != null);
    }

    private static bool Same(FriendsService.Snapshot a, FriendsService.Snapshot b) =>
        a.Connected == b.Connected && a.FromGame == b.FromGame && a.CanInvite == b.CanInvite && a.Status == b.Status
        && a.Friends.SequenceEqual(b.Friends) && a.Invites.SequenceEqual(b.Invites);

    private static DateTime SafeStart(System.Diagnostics.Process p)
    {
        try { return p.StartTime; } catch (Exception) { return DateTime.MinValue; }
    }

    /// <summary>The selected Microsoft account's name, UUID and a fresh Minecraft token; null for an offline account.</summary>
    private async Task<(string Name, string Uuid, string Token)?> FriendsSessionAsync(CancellationToken token)
    {
        // QA against a dev backend on this PC (--dev-insecure-auth): a made-up player, never sent to the real service
        if (FriendsService.EndpointUri().IsLoopback && Environment.GetEnvironmentVariable("LADS_QA_SOCIAL_UUID") is { Length: > 0 } qa)
            return ("QaLauncher", qa, "");
        string selected = await Dispatcher.UIThread.InvokeAsync(() => _selectedAccount);
        var account = loginHandler.AccountManager.GetAccounts()
            .FirstOrDefault(a => (a as CmlLib.Core.Auth.Microsoft.Sessions.JEGameAccount)?.Profile?.Username == selected);
        if (account == null) return null;
        var session = await loginHandler.AuthenticateSilently(account, token);
        return session.Username is { } name && session.UUID is { } uuid && session.AccessToken is { } access ? (name, uuid, access) : null;
    }

    private async Task FriendJoinAsync(FriendsService.Friend f)
    {
        if (_friendsGame is { } game)
        {
            if (f.Joinable) Hand(game, new JsonObject { ["t"] = "join", ["host"] = f.Uuid }, $"Asked {f.Shown} to let you in. Their game shows your request.");
            return;
        }
        if (f.Joinable)
        {
            Log($"[Friends] Starting the game to join {f.Shown}'s world.");
            await LaunchForFriendAsync($"-D{FriendsService.JoinProperty}={f.Uuid}", null);
        }
        else if (!string.IsNullOrEmpty(f.Server))
        {
            Log($"[Friends] Starting the game to join {f.Shown} on {f.Server}.");
            await LaunchForFriendAsync(null, f.Server);
        }
    }

    private void FriendInvite(FriendsService.Friend f)
    {
        if (_friendsGame is { } game) Hand(game, new JsonObject { ["t"] = "invite", ["to"] = f.Uuid }, $"Invited {f.Shown}.");
    }

    private async Task FriendAcceptAsync(FriendsService.Invite invite)
    {
        if (_friendsGame is { } game)
        {
            Hand(game, new JsonObject { ["t"] = "accept", ["inviteId"] = invite.InviteId }, "Joining…");
            return;
        }
        _friends?.Forget(invite.InviteId);
        if (invite.Server != null) await LaunchForFriendAsync(null, invite.Server);
        else await LaunchForFriendAsync($"-D{FriendsService.InviteProperty}={invite.InviteId} -D{FriendsService.InviteFromProperty}={invite.From}", null);
    }

    private void FriendDecline(FriendsService.Invite invite)
    {
        if (_friendsGame is { } game) Hand(game, new JsonObject { ["t"] = "decline", ["inviteId"] = invite.InviteId }, "Invite declined.");
        else if (invite.Server != null) _friends?.Forget(invite.InviteId);
        else _ = _friends?.DeclineAsync(invite.InviteId);
    }

    private void Hand(string game, JsonObject command, string done)
    {
        try
        {
            FriendsService.SendGame(game, command);
            StatusText.Text = done;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            StatusText.Text = "Could not reach your game: " + e.Message;
        }
    }

    /// <summary>Starts the selected profile with the Friends page's argument, or straight onto a server. The launcher's own link
    /// stays up until the game's replaces it (<see cref="_friendsHandoverUntil"/>).</summary>
    private async Task LaunchForFriendAsync(string? arg, string? server)
    {
        _friendsHandoverUntil = DateTime.UtcNow.AddMinutes(4);
        _socialLaunch = arg == null ? null : (arg, DateTime.UtcNow);
        // A relaunch target (a server, or none for a friend's world) also keeps the auto-join settings from sending the game elsewhere
        await StartLaunchAsync(false, new GraphicsRenderer.Relaunch("Friends", null, server));
    }

    /// <summary>At launch: the Friends page's argument, when it was set in the last two minutes (a cancelled launch does not carry it later).</summary>
    private void AddSocialLaunchArgument(MLaunchOption options)
    {
        if (_socialLaunch is not { } social) return;
        _socialLaunch = null;
        if (DateTime.UtcNow - social.At > TimeSpan.FromMinutes(2)) return;
        options.ExtraJvmArguments = (options.ExtraJvmArguments ?? Array.Empty<MArgument>()).Append(MArgument.FromCommandLine(social.Arg)).ToArray();
    }
}
