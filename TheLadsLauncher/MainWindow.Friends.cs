using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;
using Avalonia.Interactivity;
using Avalonia.Media.Imaging;
using Avalonia.Platform.Storage;
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
    /// <summary>The chat history of the account the page shows (null until the source has said who we are).</summary>
    private FriendsChat? _friendsChat;
    private int _friendsChatShown = -1;
    private readonly Dictionary<string, Task<Bitmap?>> _friendHeads = new();
    /// <summary>Actions for the running game, one command.json at a time (it takes one per second and deletes it).</summary>
    private readonly Queue<(string Game, JsonObject Command)> _gameCommands = new();
    private DispatcherTimer? _gameCommandPump;

    private void StartFriends()
    {
        _friends = new FriendsService(_httpClient, Log);
        _friends.Changed += () => Dispatcher.UIThread.Post(FriendsTick);
        FriendsPage.JoinClicked += f => _ = FriendJoinAsync(f);
        FriendsPage.InviteClicked += FriendInvite;
        FriendsPage.AcceptClicked += i => _ = FriendAcceptAsync(i);
        FriendsPage.DeclineClicked += FriendDecline;
        FriendsPage.SendClicked += (f, text) => _ = FriendSendAsync(f, text);
        FriendsPage.PictureClicked += f => _ = FriendPictureAsync(f);
        FriendsPage.ChatOpened += _ => FriendsTick();
        FriendsPage.PartyInviteClicked += f => Party(new JsonObject { ["t"] = "party_invite", ["to"] = f.Uuid }, $"Invited {f.Shown} to your party.");
        FriendsPage.PartyAnswered += (invite, yes) =>
        {
            Party(new JsonObject { ["t"] = yes ? "party_accept" : "party_decline", ["from"] = invite.From }, yes ? $"Joining {invite.Name}'s party…" : "Party invite declined.");
            _friends?.ForgetPartyInvite(invite.From);
        };
        FriendsPage.PartyLeaveClicked += () => Party(new JsonObject { ["t"] = "party_leave" }, "You left the party.");
        FriendsPage.PartyKickClicked += m => Party(new JsonObject { ["t"] = "party_kick", ["to"] = m.Uuid }, $"Removed {m.Name} from the party.");
        FriendsPage.PartyPromoteClicked += m => Party(new JsonObject { ["t"] = "party_promote", ["to"] = m.Uuid }, $"{m.Name} leads the party now.");
        FriendsPage.WarpAnswered += (warp, yes) =>
        {
            _friends?.ForgetWarp();
            if (yes) _ = LaunchForFriendAsync(null, warp.Server);
        };
        FriendsPage.Heads = FriendHead;
        FriendsPage.MediaFor = m => FriendsMedia.LoadAsync(_httpClient, m);
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
        KeepChats(snap);
        int waiting = snap.Invites.Count + snap.PartyInvites.Count + (snap.Warp != null ? 1 : 0) + (_friendsChat?.UnreadTotal(snap.Friends.Select(f => f.Uuid)) ?? 0);
        FriendsBadgeText.Text = waiting > 99 ? "99+" : waiting.ToString();
        FriendsBadge.IsVisible = waiting > 0;
        int chatVersion = _friendsChat?.Version ?? 0;
        if (Same(snap, _friendsShown) && chatVersion == _friendsChatShown) return;
        _friendsShown = snap;
        _friendsChatShown = chatVersion;
        if (FriendsPage.IsVisible) FriendsPage.Show(snap, _friendsGame != null, _friendsChat);
    }

    /// <summary>Keeps what the source knows of the chats (per account) and marks the open chat read.</summary>
    private void KeepChats(FriendsService.Snapshot snap)
    {
        if (snap.Self is not { } self) return;
        if (_friendsChat?.Self != self) _friendsChat = new FriendsChat(self, PathService.Instance.BaseDirectory);
        bool changed = false;
        foreach (var (friend, messages) in snap.Chats) changed |= _friendsChat.Merge(friend, messages);
        if (changed) _friendsChat.Save();
        if (FriendsPage.IsVisible && IsActive && FriendsPage.SelectedUuid is { } open) _friendsChat.MarkRead(open);
    }

    private static bool Same(FriendsService.Snapshot a, FriendsService.Snapshot b) =>
        a.Connected == b.Connected && a.FromGame == b.FromGame && a.CanInvite == b.CanInvite && a.Status == b.Status
        && a.Friends.SequenceEqual(b.Friends) && a.Invites.SequenceEqual(b.Invites) && a.Self == b.Self && a.Error == b.Error && a.Warp == b.Warp
        && a.PartyInvites.SequenceEqual(b.PartyInvites) && a.Party?.Leader == b.Party?.Leader
        && (a.Party?.Members ?? Array.Empty<FriendsService.PartyMember>()).SequenceEqual(b.Party?.Members ?? Array.Empty<FriendsService.PartyMember>());

    /// <summary>A friend's head from mc-heads.net, fetched once.</summary>
    private Task<Bitmap?> FriendHead(string uuid)
    {
        if (_friendHeads.TryGetValue(uuid, out var head)) return head;
        return _friendHeads[uuid] = Task.Run(async () =>
        {
            try
            {
                var bytes = await _httpClient.GetByteArrayAsync($"https://mc-heads.net/avatar/{uuid.Replace("-", "")}/64");
                using var stream = new MemoryStream(bytes);
                return new Bitmap(stream);
            }
            catch (Exception e) when (e is HttpRequestException or TaskCanceledException or ArgumentException or InvalidOperationException)
            {
                return (Bitmap?)null;
            }
        });
    }

    private async Task FriendSendAsync(FriendsService.Friend f, string text)
    {
        FriendsPage.ShowError(null);
        if (_friendsGame is { } game)
        {
            Hand(game, new JsonObject { ["t"] = "dm", ["to"] = f.Uuid, ["text"] = text }, $"Message sent to {f.Shown}.");
            return;
        }
        if (_friends == null || !await _friends.SendDmAsync(f.Uuid, text)) FriendsPage.ShowError("Not sent: the friends service is not connected.");
    }

    /// <summary>A picture the player picks (the screenshots folder first), made a JPEG under the backend's cap and sent.</summary>
    private async Task FriendPictureAsync(FriendsService.Friend f)
    {
        FriendsPage.ShowError(null);
        IStorageFolder? start = null;
        try { start = await StorageProvider.TryGetFolderFromPathAsync(SharedContentService.Instance.ScreenshotsDirectory); }
        catch (Exception) { }
        IReadOnlyList<IStorageFile> files;
        try
        {
            files = await StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
            {
                Title = $"Send a picture to {f.Shown}", AllowMultiple = false, SuggestedStartLocation = start,
                FileTypeFilter = new[] { new FilePickerFileType("Pictures") { Patterns = new[] { "*.png", "*.jpg", "*.jpeg", "*.webp", "*.bmp", "*.gif" } } },
            });
        }
        catch (Exception e) when (e is InvalidOperationException or IOException or UnauthorizedAccessException)
        {
            FriendsPage.ShowError("Could not open the file picker: " + e.Message);
            return;
        }
        if (files.Count == 0 || files[0].TryGetLocalPath() is not { } path) return;
        byte[]? jpeg;
        try
        {
            if (new FileInfo(path).Length > 40_000_000) { FriendsPage.ShowError("That file is too big for a picture."); return; }
            var bytes = await File.ReadAllBytesAsync(path);
            jpeg = await Task.Run(() => FriendsMedia.EncodePicture(bytes));
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            FriendsPage.ShowError("Could not read that file: " + e.Message);
            return;
        }
        if (jpeg == null) { FriendsPage.ShowError("That file is not a picture the launcher can read."); return; }
        if (_friendsGame is { } game)
        {
            try
            {
                string outbox = Path.Combine(game, FriendsService.Folder, "outbox");
                Directory.CreateDirectory(outbox);
                string name = Guid.NewGuid().ToString("N") + ".jpg";
                await File.WriteAllBytesAsync(Path.Combine(outbox, name), jpeg);
                Hand(game, new JsonObject { ["t"] = "dm_img", ["to"] = f.Uuid, ["file"] = name }, $"Picture sent to {f.Shown}.");
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                FriendsPage.ShowError("Could not hand the picture to your game: " + e.Message);
            }
            return;
        }
        StatusText.Text = $"Sending a picture to {f.Shown}…";
        if (_friends == null || !await _friends.SendPictureAsync(f.Uuid, jpeg, FriendsMedia.KeepSent(jpeg)))
            FriendsPage.ShowError("Not sent: the friends service is not connected.");
        else StatusText.Text = $"Picture sent to {f.Shown}.";
    }

    /// <summary>A party action: through the running game, or on the launcher's link.</summary>
    private void Party(JsonObject command, string done)
    {
        if (_friendsGame is { } game)
        {
            Hand(game, command, done);
            return;
        }
        if (_friends == null) return;
        _ = Task.Run(async () =>
        {
            bool sent = await _friends.SendLinkAsync(command);
            await Dispatcher.UIThread.InvokeAsync(() => StatusText.Text = sent ? done : "Not sent: the friends service is not connected.");
        });
    }

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
        _gameCommands.Enqueue((game, command));
        StatusText.Text = done;
        PumpGameCommands();
    }

    /// <summary>Writes the next queued command once the game has taken the last one (it deletes command.json).</summary>
    private void PumpGameCommands()
    {
        while (_gameCommands.TryPeek(out var next))
        {
            if (!IsGameDirRunning(next.Game)) { _gameCommands.Dequeue(); continue; }
            if (File.Exists(Path.Combine(next.Game, FriendsService.Folder, FriendsService.CommandFile))) break;
            try { FriendsService.SendGame(next.Game, next.Command); }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { StatusText.Text = "Could not reach your game: " + e.Message; }
            _gameCommands.Dequeue();
            break;
        }
        if (_gameCommands.Count == 0) { _gameCommandPump?.Stop(); return; }
        if (_gameCommandPump == null)
        {
            _gameCommandPump = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(250) };
            _gameCommandPump.Tick += (_, _) => PumpGameCommands();
        }
        _gameCommandPump.Start();
    }

    private bool IsGameDirRunning(string game) => _runningProcesses.Any(p => p.Value == game && IsGameRunning(p.Key));

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
