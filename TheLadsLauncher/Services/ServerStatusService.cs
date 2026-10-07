using System;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>A server's ping result. <see cref="Icon"/>: its 64x64 PNG favicon, or null.</summary>
public sealed record ServerStatus(bool Online, int Players, int MaxPlayers, string Motd, byte[]? Icon)
{
    /// <summary>The MOTD with its § formatting codes (both APIs' motd.raw), for <see cref="MinecraftText"/>; "" when the API gave none.</summary>
    public string MotdRaw { get; init; } = "";
    /// <summary>The version text the server advertises ("Requires MC 1.8 / 1.21"), formatting removed; "" when unknown.</summary>
    public string Version { get; init; } = "";
    /// <summary>The address the API resolved (SRV record followed), for <see cref="ServerStatusService.PingAsync"/>.</summary>
    public string? Ip { get; init; }
    public int Port { get; init; } = 25565;
}

/// <summary>
/// Live status (players, MOTD, favicon) from public status APIs, no key needed: api.mcstatus.io, then api.mcsrvstat.us when
/// that reports a server offline (some networks, Hypixel among them, refuse mcstatus.io's pings). Cached for the session.
/// </summary>
public static class ServerStatusService
{
    private static readonly HttpClient Http = CreateClient();
    private static readonly SemaphoreSlim Gate = new(6); // a page of rows, without hammering the free APIs
    private static readonly ConcurrentDictionary<string, Task<ServerStatus?>> Cache = new(StringComparer.OrdinalIgnoreCase);
    private static readonly ConcurrentDictionary<string, Task<int?>> Pings = new(StringComparer.OrdinalIgnoreCase);

    private static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("TheLadsClient/" + Program.Version); // mcsrvstat.us refuses requests without one
        return client;
    }

    /// <summary>Null when neither API answered.</summary>
    public static Task<ServerStatus?> GetAsync(string address) => Cache.GetOrAdd(address.Trim(), FetchAsync);

    public static void ClearCache()
    {
        Cache.Clear();
        Pings.Clear();
    }

    /// <summary>
    /// Round trip in ms to the server itself, or null when it is offline or unreachable. The status APIs ping from their own
    /// machines, so this times a TCP handshake from here to the address they resolved (SRV records already followed).
    /// </summary>
    public static Task<int?> PingAsync(string address) => Pings.GetOrAdd(address.Trim(), async key =>
    {
        if (await GetAsync(key) is not { Online: true } status) return null;
        var host = status.Ip ?? key.Split(':')[0];
        try
        {
            using var tcp = new TcpClient();
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(4));
            var clock = Stopwatch.StartNew();
            await tcp.ConnectAsync(host, status.Port, timeout.Token);
            return (int)Math.Max(1, clock.ElapsedMilliseconds);
        }
        catch (Exception e) when (e is SocketException or OperationCanceledException or ArgumentException) { return null; }
    });

    private static async Task<ServerStatus?> FetchAsync(string address)
    {
        await Gate.WaitAsync();
        try
        {
            var path = Uri.EscapeDataString(address);
            var status = await TryAsync("https://api.mcstatus.io/v2/status/java/" + path);
            return status is { Online: true } ? status : await TryAsync("https://api.mcsrvstat.us/3/" + path) ?? status;
        }
        finally { Gate.Release(); }
    }

    private static async Task<ServerStatus?> TryAsync(string url)
    {
        try { return Parse(await Http.GetStringAsync(url)); }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or System.Text.Json.JsonException or InvalidOperationException or FormatException) { return null; }
    }

    /// <summary>Either API's JSON: {online, players{online,max}, motd{raw,clean}, icon "data:image/png;base64,...", version,
    /// ip/ip_address, port}; mcsrvstat.us gives the MOTD as arrays of HTML-escaped lines and the version as a plain string,
    /// mcstatus.io as strings and {name_clean}.</summary>
    public static ServerStatus Parse(string json)
    {
        var root = JsonNode.Parse(json)!;
        var online = root["online"]?.GetValue<bool>() == true;
        var icon = root["icon"]?.GetValue<string>();
        var comma = icon?.IndexOf("base64,", StringComparison.Ordinal) ?? -1;
        var version = root["version"] is JsonObject v ? v["name_clean"]?.GetValue<string>() : root["version"]?.GetValue<string>();
        return new ServerStatus(online, root["players"]?["online"]?.GetValue<int>() ?? 0, root["players"]?["max"]?.GetValue<int>() ?? 0,
            Lines(root["motd"]?["clean"], trim: true), comma < 0 ? null : Convert.FromBase64String(icon![(comma + 7)..]))
        {
            MotdRaw = Lines(root["motd"]?["raw"], trim: false),
            Version = MinecraftText.Strip(WebUtility.HtmlDecode(version ?? "")).Trim(),
            Ip = (root["ip_address"] ?? root["ip"])?.GetValue<string>() is { Length: > 0 } ip ? ip : null,
            Port = root["port"]?.GetValue<int>() ?? 25565,
        };
    }

    /// <summary>A MOTD field (one string, or an array of lines), HTML entities decoded. The clean text is trimmed per line: servers
    /// pad their MOTDs with spaces to centre them in the game's list. The raw one keeps them, as the codes span lines.</summary>
    private static string Lines(JsonNode? node, bool trim)
    {
        var text = node is JsonArray lines ? string.Join("\n", lines.Select(l => l?.GetValue<string>() ?? "")) : node?.GetValue<string>() ?? "";
        text = WebUtility.HtmlDecode(text);
        return trim ? string.Join("\n", text.Split('\n').Select(l => l.Trim())).Trim() : text;
    }
}
