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
    /// <summary>What the page shows. FromGame: the running game's list (Invite works through it).</summary>
    public sealed record Snapshot(bool Connected, bool FromGame, bool CanInvite, IReadOnlyList<Friend> Friends, IReadOnlyList<Invite> Invites, string Status);

    public static readonly Snapshot Empty = new(false, false, false, Array.Empty<Friend>(), Array.Empty<Invite>(), "Not connected");

    private readonly HttpClient _http;
    private readonly Action<string> _log;
    private readonly object _lock = new();
    private readonly Dictionary<string, Friend> _friends = new();
    private readonly Dictionary<string, Invite> _invites = new();
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
            lock (_lock) return new Snapshot(_connected, false, false, Sorted(_friends.Values), _invites.Values.ToList(), _status);
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
        if (_ws is { State: WebSocketState.Open } ws)
            try { await SendAsync(ws, new JsonObject { ["t"] = "invite_decline", ["inviteId"] = inviteId }, CancellationToken.None); }
            catch (Exception e) when (e is WebSocketException or IOException or ObjectDisposedException) { }
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
            if (hello["friends"] is JsonArray friends)
                foreach (var f in friends.OfType<JsonObject>())
                    if (ParseFriend(f, f["presence"] as JsonObject) is { } friend) _friends[friend.Uuid] = friend;
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
                default:
                    return;
            }
        }
        Changed?.Invoke();
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
            if (!File.Exists(file) || new FileInfo(file).Length > 1_000_000) return null;
            if (JsonNode.Parse(File.ReadAllText(file)) is not JsonObject o) return null;
            var friends = (o["friends"] as JsonArray ?? new()).OfType<JsonObject>()
                .Select(f => ParseFriend(f, f["presence"] as JsonObject)).OfType<Friend>().ToList();
            var invites = (o["invites"] as JsonArray ?? new()).OfType<JsonObject>()
                .Where(i => Str(i, "inviteId") != null && Uuid(Str(i, "from")) != null)
                .Select(i => new Invite(Str(i, "inviteId")!, Uuid(Str(i, "from"))!, Str(i, "world"), Str(i, "server"))).ToList();
            bool connected = o["connected"]?.GetValue<bool>() == true;
            return new Snapshot(connected, true, connected && o["canInvite"]?.GetValue<bool>() == true, Sorted(friends), invites,
                connected ? "Connected through your game" : "Your game is connecting…");
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
