using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Net.WebSockets;
using System.Numerics;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>The launcher's Friends page. The backend (/social/ws, cosmetics-server/social.py) keeps one link per account and a new one
/// replaces the old, so the launcher holds the link only while no game runs. While one runs, the game holds it and the page reads the
/// game's <c>.lads-social/friends.json</c> and hands Invite / Join / Accept to it in <c>.lads-social/command.json</c>
/// (TheLadsCore LauncherLink).</summary>
public sealed class FriendsService : IDisposable
{
    public const string Endpoint = "wss://bot.arashyt.ca/social/ws";
    public const string Folder = ".lads-social", SnapshotFile = "friends.json", CommandFile = "command.json";
    /// <summary>A game the page started: it asks that host to join, or accepts that invite (its id and host), once its link is up.</summary>
    public const string JoinProperty = "lads.social.join", InviteProperty = "lads.social.invite", InviteFromProperty = "lads.social.invite.from";

    /// <summary>What a friend is doing. State: menu | singleplayer | hosting | server | offline. World only while hosting.</summary>
    public sealed record Friend(string Uuid, string Name, string? Nick, string State, string? Server, World? World)
    {
        public string Shown => string.IsNullOrEmpty(Nick) ? Name : Nick;
        public bool Online => State != "offline";
        /// <summary>A world friends may ask to join (scope "friends") with a free slot.</summary>
        public bool Joinable => State == "hosting" && World is { Scope: "friends" } w && (w.Slots == 0 || w.Used < w.Slots);
    }
    public sealed record World(string Name, string Scope, int Slots, int Used);
    /// <summary>An invite: to a world (World) or a server (Server).</summary>
    public sealed record Invite(string InviteId, string From, string? World, string? Server);
    /// <summary>A DM. From: the sender's UUID (ours for a sent one). Img: the backend's picture id, or "file:&lt;path&gt;" for our own
    /// copy of a sent one; Gif: a Tenor/Giphy GIF link.</summary>
    public sealed record Message(string From, string Text, long Ts, string? Img, string? Gif);
    public sealed record PartyMember(string Uuid, string Name);
    public sealed record Party(string Leader, IReadOnlyList<PartyMember> Members);
    public sealed record PartyInvite(string From, string Name);
    /// <summary>The party leader is going to this server (the backend's "party_warp").</summary>
    public sealed record PartyWarp(string From, string Server, string? Name);
    /// <summary>What the page shows. FromGame: the running game's list (Invite works through it). Chats: DMs per friend the source
    /// knows (the page keeps the history).</summary>
    public sealed record Snapshot(bool Connected, bool FromGame, bool CanInvite, IReadOnlyList<Friend> Friends, IReadOnlyList<Invite> Invites, string Status)
    {
        public string? Self { get; init; }
        public IReadOnlyDictionary<string, IReadOnlyList<Message>> Chats { get; init; } = new Dictionary<string, IReadOnlyList<Message>>();
        public Party? Party { get; init; }
        public IReadOnlyList<PartyInvite> PartyInvites { get; init; } = Array.Empty<PartyInvite>();
        public PartyWarp? Warp { get; init; }
        /// <summary>The backend's last refusal (a DM too long, too fast, a full party), in words.</summary>
        public string? Error { get; init; }
    }

    /// <summary>A DM's text limit and a picture's (social.py DM_MAX_CHARS, DM_IMG_MAX_BYTES with the game's margin) and part size.</summary>
    public const int MaxDm = 256, MaxPicture = 950_000, PicturePart = 45_000, PartyMax = 8;

    public static readonly Snapshot Empty = new(false, false, false, Array.Empty<Friend>(), Array.Empty<Invite>(), "Not connected");

    private readonly HttpClient _http;
    private readonly Action<string> _log;
    private readonly object _lock = new();
    private readonly Dictionary<string, Friend> _friends = new();
    private readonly Dictionary<string, Invite> _invites = new();
    private readonly Dictionary<string, List<Message>> _chats = new();
    private readonly Dictionary<string, PartyInvite> _partyInvites = new();
    private readonly SemaphoreSlim _sending = new(1, 1);
    private Party? _party;
    private PartyWarp? _warp;
    private string? _self, _error;
    private CancellationTokenSource? _run;
    private ClientWebSocket? _ws;
    private string _status = "Not connected";
    private bool _connected;
    private volatile CancellationTokenSource? _ended;

    /// <summary>The link's list changed (any thread).</summary>
    public event Action? Changed;

    public FriendsService(HttpClient http, Action<string> log)
    {
        _http = http;
        _log = log;
    }

    /// <summary>A link is held or being retried (false once it ended for good: no account, or our game took it over).</summary>
    public bool Running => _run is { } run && _ended != run;

    public Snapshot Current
    {
        get
        {
            lock (_lock)
                return new Snapshot(_connected, false, false, Sorted(_friends.Values), _invites.Values.ToList(), _status)
                {
                    Self = _self, Party = _party, Warp = _warp, Error = _error, PartyInvites = _partyInvites.Values.ToList(),
                    Chats = _chats.ToDictionary(c => c.Key, c => (IReadOnlyList<Message>)c.Value.ToList()),
                };
        }
    }

    /// <summary>Holds the link (reconnecting with backoff) until <see cref="Stop"/>. <paramref name="session"/> gives the account's
    /// name, UUID and Minecraft token, or null for an account that cannot sign in (offline).</summary>
    public void Start(Func<CancellationToken, Task<(string Name, string Uuid, string Token)?>> session)
    {
        if (Running) return;
        _run?.Cancel();
        var run = _run = new CancellationTokenSource();
        _ = Task.Run(async () =>
        {
            try { await LoopAsync(session, run.Token); }
            finally { _ended = run; }
        });
    }

    public void Stop()
    {
        var run = _run;
        _run = null;
        if (run == null) return;
        run.Cancel();
        try { _ws?.Abort(); } catch (Exception) { }
        lock (_lock)
        {
            _connected = false;
            _friends.Clear();
            _invites.Clear();
            ClearLink();
            _status = "Not connected";
        }
        Changed?.Invoke();
    }

    public void Dispose() => Stop();

    private async Task LoopAsync(Func<CancellationToken, Task<(string Name, string Uuid, string Token)?>> session, CancellationToken token)
    {
        int failures = 0;
        while (!token.IsCancellationRequested)
        {
            var started = DateTime.UtcNow;
            try
            {
                var who = await session(token);
                if (who == null)
                {
                    SetStatus("Friends need a Microsoft account");
                    return;
                }
                SetStatus("Connecting…");
                if (await ServeAsync(who.Value, token))
                {
                    // Our game signed in with this account and took the link over: reconnecting would throw it off again
                    lock (_lock) _connected = false;
                    SetStatus("Your game took over the friends link");
                    _log("[Friends] The game took over the friends link.");
                    return;
                }
            }
            catch (Exception e) when (!token.IsCancellationRequested)
            {
                _log($"[Friends] Link ended: {e.Message}");
            }
            if (token.IsCancellationRequested) return;
            lock (_lock) _connected = false;
            failures = DateTime.UtcNow - started > TimeSpan.FromSeconds(60) ? 1 : failures + 1;
            var wait = TimeSpan.FromSeconds(Math.Min(300, 5 << Math.Min(failures - 1, 6)));
            SetStatus($"Offline, trying again in {(int)wait.TotalSeconds} s");
            try { await Task.Delay(wait, token); } catch (OperationCanceledException) { return; }
        }
    }

    /// <summary>True when the backend closed the link because another one of this account (our game) replaced it.</summary>
    private async Task<bool> ServeAsync((string Name, string Uuid, string Token) who, CancellationToken token)
    {
        var endpoint = EndpointUri();
        bool local = endpoint.Scheme == "ws"; // QA's dev backend on this PC (--dev-insecure-auth), as the game's LADS_SOCIAL_URL
        using var ws = new ClientWebSocket();
        ws.Options.SetRequestHeader("User-Agent", "TheLadsLauncher");
        ws.Options.KeepAliveInterval = TimeSpan.FromSeconds(30);
        _ws = ws;
        using (var connect = CancellationTokenSource.CreateLinkedTokenSource(token))
        {
            connect.CancelAfter(TimeSpan.FromSeconds(10));
            await ws.ConnectAsync(endpoint, connect.Token);
        }
        var challenge = await ReadAsync(ws, token) ?? throw new IOException("no challenge");
        if (Str(challenge, "t") != "challenge" || Str(challenge, "nonce") is not { } nonce) throw new IOException("no challenge");
        string serverId = ServerId(nonce);
        string plain = who.Uuid.Replace("-", "");
        if (!local)
        {
            using var join = new HttpRequestMessage(HttpMethod.Post, "https://sessionserver.mojang.com/session/minecraft/join");
            join.Content = new StringContent(JsonSerializer.Serialize(new { accessToken = who.Token, selectedProfile = plain, serverId }), Encoding.UTF8, "application/json");
            using var reply = await _http.SendAsync(join, token);
            if ((int)reply.StatusCode != 204) throw new IOException($"Mojang join refused (HTTP {(int)reply.StatusCode})");
        }
        await SendAsync(ws, new JsonObject { ["t"] = "auth", ["name"] = who.Name, ["uuid"] = plain, ["serverId"] = serverId }, token);
        lock (_lock) _self = Uuid(plain);
        var hello = await ReadAsync(ws, token) ?? throw new IOException("no hello");
        if (Str(hello, "t") != "hello") throw new IOException("no hello");
        OnHello(hello);
        _log("[Friends] Connected to the friends service.");
        while (!token.IsCancellationRequested && ws.State == WebSocketState.Open)
        {
            var msg = await ReadAsync(ws, token);
            if (msg == null) break;
            Handle(msg);
        }
        return ws.CloseStatus == (WebSocketCloseStatus)4001; // social.py register(): "replaced"
    }

    /// <summary>Declines an invite the link received (an accepted one is answered by the game the page starts).</summary>
    public async Task DeclineAsync(string inviteId)
    {
        lock (_lock) _invites.Remove(inviteId);
        Changed?.Invoke();
        await SendLinkAsync(new JsonObject { ["t"] = "invite_decline", ["inviteId"] = inviteId });
    }

    /// <summary>Sends one message on the link; false when it is down.</summary>
    public async Task<bool> SendLinkAsync(JsonObject msg)
    {
        if (_ws is not { State: WebSocketState.Open } ws) return false;
        await _sending.WaitAsync();
        try
        {
            await SendAsync(ws, msg, CancellationToken.None);
            return true;
        }
        catch (Exception e) when (e is WebSocketException or IOException or ObjectDisposedException or OperationCanceledException)
        {
            return false;
        }
        finally { _sending.Release(); }
    }

    /// <summary>A DM on the link: a Tenor/Giphy link goes as a GIF (its file found first). False when the link is down.</summary>
    public async Task<bool> SendDmAsync(string to, string text)
    {
        text = text.Trim();
        if (text.Length == 0) return false;
        if (text.Length > MaxDm) text = text[..MaxDm];
        string? gif = FriendsMedia.GifLink(text) is { } link ? await FriendsMedia.ResolveGifAsync(_http, link) : null;
        var msg = new JsonObject { ["t"] = "dm", ["to"] = to, ["text"] = text };
        if (gif != null) msg["gif"] = gif;
        if (!await SendLinkAsync(msg)) return false;
        Record(to, new Message(_self ?? "", text, Now(), null, gif));
        return true;
    }

    /// <summary>A picture (a JPEG/PNG of at most <see cref="MaxPicture"/> bytes) as "dm_img" parts; <paramref name="kept"/> is our
    /// copy the chat shows.</summary>
    public async Task<bool> SendPictureAsync(string to, byte[] picture, string kept)
    {
        if (picture.Length > MaxPicture) return false;
        var parts = PictureParts(picture);
        for (int i = 0; i < parts.Count; i++)
            if (!await SendLinkAsync(new JsonObject { ["t"] = "dm_img", ["to"] = to, ["text"] = FriendsMedia.ScreenshotText, ["part"] = i, ["parts"] = parts.Count, ["data"] = parts[i] }))
                return false;
        Record(to, new Message(_self ?? "", FriendsMedia.ScreenshotText, Now(), "file:" + kept, null));
        return true;
    }

    internal static List<string> PictureParts(byte[] picture)
    {
        string b64 = Convert.ToBase64String(picture);
        var parts = new List<string>();
        for (int i = 0; i < b64.Length; i += PicturePart) parts.Add(b64.Substring(i, Math.Min(PicturePart, b64.Length - i)));
        return parts;
    }

    /// <summary>A party answer we gave: the invite is gone from the list (the backend tells the party itself).</summary>
    public void ForgetPartyInvite(string from)
    {
        lock (_lock) _partyInvites.Remove(from);
        Changed?.Invoke();
    }

    public void ForgetWarp()
    {
        lock (_lock) _warp = null;
        Changed?.Invoke();
    }

    private static long Now() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    private void Record(string friend, Message m)
    {
        lock (_lock) AddMessage(friend, m);
        Changed?.Invoke();
    }

    private void AddMessage(string friend, Message m)
    {
        if (!_chats.TryGetValue(friend, out var list))
        {
            if (_chats.Count >= 500) return;
            _chats[friend] = list = new List<Message>();
        }
        list.Add(m);
        if (list.Count > 200) list.RemoveAt(0);
    }

    private void ClearLink()
    {
        _chats.Clear();
        _partyInvites.Clear();
        _party = null;
        _warp = null;
        _error = null;
    }

    /// <summary>Takes an invite out of the list (the game the page starts accepts it).</summary>
    public void Forget(string inviteId)
    {
        lock (_lock) _invites.Remove(inviteId);
        Changed?.Invoke();
    }

    private void OnHello(JsonObject hello)
    {
        lock (_lock)
        {
            _friends.Clear();
            _invites.Clear();
            ClearLink();
            if (hello["friends"] is JsonArray friends)
                foreach (var f in friends.OfType<JsonObject>())
                    if (ParseFriend(f, f["presence"] as JsonObject) is { } friend) _friends[friend.Uuid] = friend;
            if (hello["dms"] is JsonArray dms) // DMs that came while we were offline
                foreach (var d in dms.OfType<JsonObject>())
                    if (ParseMessage(d) is { } m) AddMessage(m.From, m);
            _connected = true;
            _status = "Connected";
        }
        Changed?.Invoke();
    }

    internal void Handle(JsonObject msg)
    {
        lock (_lock)
        {
            switch (Str(msg, "t"))
            {
                case "presence":
                    if (Uuid(Str(msg, "uuid")) is { } id && _friends.TryGetValue(id, out var old))
                        _friends[id] = ParsePresence(old, msg);
                    break;
                case "friend_added":
                    if (ParseFriend(msg, msg["presence"] as JsonObject) is { } added) _friends[added.Uuid] = added;
                    break;
                case "friend_removed":
                    if (Uuid(Str(msg, "uuid")) is { } gone) _friends.Remove(gone);
                    break;
                case "invite":
                    if (Uuid(Str(msg, "from")) is not { } from) break;
                    string? server = Str(msg, "server");
                    string? inviteId = Str(msg, "inviteId");
                    if (server != null) _invites["server:" + from] = new Invite("server:" + from, from, null, server);
                    else if (inviteId != null && System.Text.RegularExpressions.Regex.IsMatch(inviteId, "^[A-Za-z0-9_-]{1,64}$"))
                        _invites[inviteId] = new Invite(inviteId, from, (msg["world"] as JsonObject) is { } w ? Str(w, "name") : null, null);
                    break;
                case "dm":
                    if (ParseMessage(msg) is { } dm && _friends.ContainsKey(dm.From)) AddMessage(dm.From, dm);
                    break;
                case "party_state":
                    _party = ParseParty(msg);
                    if (_party != null) foreach (var m in _party.Members) _partyInvites.Remove(m.Uuid);
                    if (_warp != null && _party?.Leader != _warp.From) _warp = null;
                    break;
                case "party_ended":
                    _party = null;
                    _warp = null;
                    break;
                case "party_invite_in":
                    if (Uuid(Str(msg, "from")) is { } inviter && _friends.TryGetValue(inviter, out var by) && _party == null)
                        _partyInvites[inviter] = new PartyInvite(inviter, by.Shown);
                    break;
                case "party_warp":
                    if (Uuid(Str(msg, "from")) is { } leader && _party?.Leader == leader && Str(msg, "server") is { Length: > 0 and <= 255 } target)
                        _warp = new PartyWarp(leader, target, Str(msg, "name"));
                    break;
                case "error":
                    _error = Str(msg, "code") switch
                    {
                        "rate" => "Slow down a little: too many messages",
                        "limit" => Str(msg, "ref") == "party_invites" ? "Too many party invites waiting" : "Too long or too big to send",
                        "not_friend" => "You can only message friends",
                        "blocked" => "You can't message this player",
                        "full" => "The party is full",
                        "offline" => "That friend is offline",
                        "bad" when Str(msg, "ref") is "img" or "gif" => "That picture or GIF could not be sent",
                        _ => _error,
                    };
                    break;
                default:
                    return;
            }
        }
        Changed?.Invoke();
    }

    internal static Message? ParseMessage(JsonObject d) =>
        Uuid(Str(d, "from")) is { } from && Str(d, "text") is { } text
            ? new Message(from, text, d["ts"] is JsonValue v && v.TryGetValue<long>(out var ts) ? ts : Now(), Str(d, "img"), Str(d, "gif"))
            : null;

    internal static Party? ParseParty(JsonObject p)
    {
        if (Uuid(Str(p, "leader")) is not { } leader || p["members"] is not JsonArray arr) return null;
        var members = arr.OfType<JsonObject>().Take(PartyMax)
            .Select(m => Uuid(Str(m, "id")) is { } id ? new PartyMember(id, Str(m, "name") is { Length: > 0 and <= 32 } n ? n : id[..8]) : null)
            .OfType<PartyMember>().ToList();
        return members.Count == 0 ? null : new Party(leader, members);
    }

    private static Friend? ParseFriend(JsonObject f, JsonObject? presence)
    {
        if (Uuid(Str(f, "uuid") ?? Str(f, "id")) is not { } id || Str(f, "name") is not { } name) return null;
        var friend = new Friend(id, name, Str(f, "nick"), "offline", null, null);
        return presence == null ? friend : ParsePresence(friend, presence);
    }

    private static Friend ParsePresence(Friend f, JsonObject p)
    {
        World? world = null;
        if (p["world"] is JsonObject w)
            world = new World(Str(w, "name") ?? "World", Str(w, "scope") ?? "friends", Int(w, "slots"), Int(w, "used"));
        return f with { State = Str(p, "state") ?? "menu", Server = Str(p, "server"), World = world };
    }

    /// <summary>The running game's list (<see cref="SnapshotFile"/>), or null when it has none.</summary>
    public static Snapshot? ReadGame(string gameDirectory)
    {
        try
        {
            string file = Path.Combine(gameDirectory, Folder, SnapshotFile);
            if (!File.Exists(file) || new FileInfo(file).Length > 8_000_000) return null;
            if (JsonNode.Parse(File.ReadAllText(file)) is not JsonObject o) return null;
            var friends = (o["friends"] as JsonArray ?? new()).OfType<JsonObject>()
                .Select(f => ParseFriend(f, f["presence"] as JsonObject)).OfType<Friend>().ToList();
            var invites = (o["invites"] as JsonArray ?? new()).OfType<JsonObject>()
                .Where(i => Str(i, "inviteId") != null && Uuid(Str(i, "from")) != null)
                .Select(i => new Invite(Str(i, "inviteId")!, Uuid(Str(i, "from"))!, Str(i, "world"), Str(i, "server"))).ToList();
            bool connected = o["connected"]?.GetValue<bool>() == true;
            var chats = new Dictionary<string, IReadOnlyList<Message>>();
            foreach (var f in (o["friends"] as JsonArray ?? new()).OfType<JsonObject>())
                if (Uuid(Str(f, "uuid")) is { } id && f["dms"] is JsonArray dms)
                    chats[id] = dms.OfType<JsonObject>().Select(ParseMessage).OfType<Message>().ToList();
            return new Snapshot(connected, true, connected && o["canInvite"]?.GetValue<bool>() == true, Sorted(friends), invites,
                connected ? "Connected through your game" : "Your game is connecting…")
            {
                Self = Uuid(Str(o, "self")), Chats = chats, Party = o["party"] is JsonObject party ? ParseParty(party) : null,
                PartyInvites = (o["partyInvites"] as JsonArray ?? new()).OfType<JsonObject>()
                    .Select(i => Uuid(Str(i, "from")) is { } from ? new PartyInvite(from, Str(i, "name") ?? from[..8]) : null).OfType<PartyInvite>().ToList(),
            };
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException or InvalidOperationException or FormatException)
        {
            return null;
        }
    }

    /// <summary>Hands an action to the running game (<see cref="CommandFile"/>; it takes one per second).</summary>
    public static void SendGame(string gameDirectory, JsonObject command)
    {
        string folder = Path.Combine(gameDirectory, Folder);
        Directory.CreateDirectory(folder);
        string tmp = Path.Combine(folder, CommandFile + ".tmp");
        File.WriteAllText(tmp, command.ToJsonString());
        File.Move(tmp, Path.Combine(folder, CommandFile), overwrite: true);
    }

    /// <summary>Online first (hosting, then playing, then in the menu), then by name.</summary>
    internal static IReadOnlyList<Friend> Sorted(IEnumerable<Friend> friends) => friends
        .OrderBy(f => f.State switch { "hosting" => 0, "server" => 1, "singleplayer" => 2, "menu" => 3, _ => 4 })
        .ThenBy(f => f.Shown, StringComparer.OrdinalIgnoreCase).ToList();

    /// <summary>What a friend is doing, in words.</summary>
    public static string Describe(Friend f) => f.State switch
    {
        "hosting" when f.World != null => $"Hosting {f.World.Name} ({f.World.Used}/{f.World.Slots})" + (f.World.Scope == "friends" ? "" : " · invite only"),
        "hosting" => "Hosting a world",
        "server" when !string.IsNullOrEmpty(f.Server) => $"Playing on {f.Server}",
        "server" => "Playing multiplayer",
        "singleplayer" => "Playing singleplayer",
        "menu" => "Online",
        _ => "Offline",
    };

    /// <summary>The backend: LADS_SOCIAL_URL when it is a ws:// address on this PC (QA, as the game), else <see cref="Endpoint"/>.</summary>
    internal static Uri EndpointUri() =>
        Uri.TryCreate(Environment.GetEnvironmentVariable("LADS_SOCIAL_URL"), UriKind.Absolute, out var uri) && uri.Scheme == "ws" && uri.IsLoopback ? uri : new Uri(Endpoint);

    /// <summary>The handshake's server id (as the game's SocialClient): Minecraft's signed hex SHA-1 of "lads-cosmetics:&lt;nonce&gt;".</summary>
    internal static string ServerId(string nonce) => HexDigest("lads-cosmetics:" + nonce);

    /// <summary>Minecraft's signed hex SHA-1 ("Notch" → 4ed1f46b…, "jeb_" → -7c9d5b00…).</summary>
    internal static string HexDigest(string text)
    {
        var hash = SHA1.HashData(Encoding.UTF8.GetBytes(text));
        var n = new BigInteger(hash, isUnsigned: false, isBigEndian: true);
        return n < 0 ? "-" + Hex(-n) : Hex(n);
    }

    private static string Hex(BigInteger n)
    {
        string s = n.ToString("x").TrimStart('0');
        return s.Length == 0 ? "0" : s;
    }

    private void SetStatus(string status)
    {
        lock (_lock) _status = status;
        Changed?.Invoke();
    }

    private static async Task SendAsync(ClientWebSocket ws, JsonObject msg, CancellationToken token) =>
        await ws.SendAsync(Encoding.UTF8.GetBytes(msg.ToJsonString()), WebSocketMessageType.Text, true, token);

    private static async Task<JsonObject?> ReadAsync(ClientWebSocket ws, CancellationToken token)
    {
        var buffer = new byte[16 * 1024];
        using var text = new MemoryStream();
        while (true)
        {
            var result = await ws.ReceiveAsync(buffer, token);
            if (result.MessageType == WebSocketMessageType.Close) return null;
            text.Write(buffer, 0, result.Count);
            if (text.Length > 4 * 1024 * 1024) throw new IOException("message too large");
            if (!result.EndOfMessage) continue;
            if (result.MessageType != WebSocketMessageType.Text) { text.SetLength(0); continue; }
            try { return JsonNode.Parse(text.ToArray()) as JsonObject ?? new JsonObject(); }
            catch (JsonException) { return new JsonObject(); }
        }
    }

    private static string? Str(JsonObject o, string key) =>
        o[key] is JsonValue v && v.TryGetValue<string>(out var s) ? s : null;

    private static int Int(JsonObject o, string key) =>
        o[key] is JsonValue v && v.TryGetValue<int>(out var i) ? i : 0;

    /// <summary>A UUID in its dashed lower-case form, from the dashed or plain form.</summary>
    internal static string? Uuid(string? text) =>
        text != null && Guid.TryParse(text, out var g) ? g.ToString("D") : null;
}
