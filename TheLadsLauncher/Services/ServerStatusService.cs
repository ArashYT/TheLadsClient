using System;
using System.Collections.Concurrent;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>A server's ping result. <see cref="Icon"/>: its 64x64 PNG favicon, or null.</summary>
public sealed record ServerStatus(bool Online, int Players, int MaxPlayers, string Motd, byte[]? Icon);

/// <summary>
/// Live status (players, MOTD, favicon) from public status APIs, no key needed: api.mcstatus.io, then api.mcsrvstat.us when
/// that reports a server offline (some networks, Hypixel among them, refuse mcstatus.io's pings). Cached for the session.
/// </summary>
public static class ServerStatusService
{
    private static readonly HttpClient Http = CreateClient();
    private static readonly SemaphoreSlim Gate = new(6); // a page of rows, without hammering the free APIs
    private static readonly ConcurrentDictionary<string, Task<ServerStatus?>> Cache = new(StringComparer.OrdinalIgnoreCase);

    private static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("TheLadsClient/" + Program.Version); // mcsrvstat.us refuses requests without one
        return client;
    }

    /// <summary>Null when neither API answered.</summary>
    public static Task<ServerStatus?> GetAsync(string address) => Cache.GetOrAdd(address.Trim(), FetchAsync);

    public static void ClearCache() => Cache.Clear();

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

    /// <summary>Either API's JSON: {online, players{online,max}, motd{clean}, icon "data:image/png;base64,..."}; mcsrvstat.us
    /// gives the MOTD as an array of HTML-escaped lines.</summary>
    public static ServerStatus Parse(string json)
    {
        var root = JsonNode.Parse(json)!;
        var online = root["online"]?.GetValue<bool>() == true;
        var clean = root["motd"]?["clean"];
        var motd = clean is JsonArray lines ? string.Join("\n", lines.Select(l => l?.GetValue<string>().Trim())) : clean?.GetValue<string>() ?? "";
        var icon = root["icon"]?.GetValue<string>();
        var comma = icon?.IndexOf("base64,", StringComparison.Ordinal) ?? -1;
        return new ServerStatus(online, root["players"]?["online"]?.GetValue<int>() ?? 0, root["players"]?["max"]?.GetValue<int>() ?? 0,
            WebUtility.HtmlDecode(string.Join("\n", motd.Split('\n').Select(l => l.Trim())).Trim()), comma < 0 ? null : Convert.FromBase64String(icon![(comma + 7)..]));
    }
}
