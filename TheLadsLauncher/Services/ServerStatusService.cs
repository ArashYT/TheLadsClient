using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Text;
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
        client.DefaultRequestHeaders.UserAgent.ParseAdd(LauncherHttp.UserAgent); // mcsrvstat.us refuses requests without one
        return client;
    }

    /// <summary>Null when neither API answered.</summary>
    public static Task<ServerStatus?> GetAsync(string address) => Cache.GetOrAdd(address.Trim(), FetchAsync);

    /// <summary>As <see cref="GetAsync"/>, after dropping this address's cached status (a count that should be current).</summary>
    public static Task<ServerStatus?> RefreshAsync(string address)
    {
        Cache.TryRemove(address.Trim(), out _);
        return GetAsync(address);
    }

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

    /// <summary>
    /// The game's own status ping (Server List Ping) from here to the server, no API between and no cache. Null when it does not answer.
    /// Only players and version are read; no SRV lookup, so for addresses with a known port.
    /// </summary>
    public static async Task<ServerStatus?> SlpAsync(string host, int port = 25565, int timeoutMs = 4000)
    {
        try
        {
            using var timeout = new CancellationTokenSource(timeoutMs);
            using var tcp = new TcpClient();
            await tcp.ConnectAsync(host, port, timeout.Token);
            var stream = tcp.GetStream();
            var handshake = new List<byte> { 0 }; // packet 0: protocol -1 ("just asking"), host, port, next state 1 (status)
            WriteVarInt(handshake, -1);
            var hostBytes = Encoding.UTF8.GetBytes(host);
            WriteVarInt(handshake, hostBytes.Length);
            handshake.AddRange(hostBytes);
            handshake.Add((byte)(port >> 8));
            handshake.Add((byte)port);
            WriteVarInt(handshake, 1);
            var frame = new List<byte>();
            WriteVarInt(frame, handshake.Count);
            frame.AddRange(handshake);
            frame.AddRange(new byte[] { 1, 0 }); // status request
            await stream.WriteAsync(frame.ToArray(), timeout.Token);

            await ReadVarIntAsync(stream, timeout.Token); // packet length
            if (await ReadVarIntAsync(stream, timeout.Token) != 0) return null;
            var length = await ReadVarIntAsync(stream, timeout.Token);
            if (length is <= 0 or > 1 << 21) return null; // the protocol's own limit for a string
            var json = new byte[length];
            await stream.ReadExactlyAsync(json, timeout.Token);
            var root = JsonNode.Parse(json)!;
            return new ServerStatus(true, root["players"]?["online"]?.GetValue<int>() ?? 0, root["players"]?["max"]?.GetValue<int>() ?? 0, "", null)
            {
                Version = root["version"]?["name"]?.GetValue<string>() ?? ""
            };
        }
        catch (Exception e) when (e is SocketException or IOException or OperationCanceledException or ArgumentException
            or System.Text.Json.JsonException or InvalidOperationException or FormatException) { return null; }
    }

    private static void WriteVarInt(List<byte> to, int value)
    {
        var rest = (uint)value;
        while (rest >= 0x80) { to.Add((byte)(rest | 0x80)); rest >>= 7; }
        to.Add((byte)rest);
    }

    private static async Task<int> ReadVarIntAsync(NetworkStream stream, CancellationToken token)
    {
        int value = 0;
        var one = new byte[1];
        for (int shift = 0; shift < 35; shift += 7)
        {
            await stream.ReadExactlyAsync(one, token);
            value |= (one[0] & 0x7F) << shift;
            if ((one[0] & 0x80) == 0) return value;
        }
        throw new FormatException("VarInt too long");
    }

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

    /// <summary>Validates that the server favicon is a valid PNG with 64x64 dimensions, matching vanilla requirements.</summary>
    public static bool IsValidFaviconPng(byte[] png)
    {
        ReadOnlySpan<byte> signature = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];
        if (png.Length < 24 || !png.AsSpan(0, 8).SequenceEqual(signature) || png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R')
            return false;
        int width = (png[16] << 24) | (png[17] << 16) | (png[18] << 8) | png[19];
        int height = (png[20] << 24) | (png[21] << 16) | (png[22] << 8) | png[23];
        return width == 64 && height == 64;
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
        byte[]? iconBytes = null;
        if (comma >= 0)
        {
            try { iconBytes = Convert.FromBase64String(icon![(comma + 7)..]); }
            catch { }
        }
        return new ServerStatus(online, root["players"]?["online"]?.GetValue<int>() ?? 0, root["players"]?["max"]?.GetValue<int>() ?? 0,
            Lines(root["motd"]?["clean"], trim: true), iconBytes)
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
