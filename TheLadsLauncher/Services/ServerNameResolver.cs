using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace TheLadsLauncher.Services;

public sealed record KnownServer(string Name, string Address, IReadOnlyList<string> Domains, string Category, string Description);

/// <summary>
/// Friendly names for server addresses, from LadsCore's thelads/known_servers.json (embedded; LadsCore's ServerNames reads the
/// same file): a known server matches its domain and every subdomain; any other domain is named after its registrable label,
/// title-cased (play.funnyservername.com is "Funnyservername"). Bare IPs and single labels get no name.
/// </summary>
public static class ServerNameResolver
{
    /// <summary>The name Minecraft gives a new server entry.</summary>
    public const string DefaultName = "Minecraft Server";
    private static readonly Regex Host = new(@"\A[a-z0-9_-]+(\.[a-z0-9_-]+)*\z");
    private static readonly Data Known = Load();

    /// <summary>Every known server, in the file's order (most popular first).</summary>
    public static IReadOnlyList<KnownServer> Servers => Known.Servers;

    private sealed record Data(List<KnownServer> Servers, List<string> Suffixes, List<string> Prefixes)
    {
        public Dictionary<string, string> Names { get; } = Servers.SelectMany(s => s.Domains.Select(d => (d, s.Name))).ToDictionary(p => p.d, p => p.Name);
    }

    private static Data Load()
    {
        using var stream = typeof(ServerNameResolver).Assembly.GetManifestResourceStream("known_servers.json")
            ?? throw new InvalidOperationException("known_servers.json is not embedded in the launcher.");
        return JsonSerializer.Deserialize<Data>(stream, new JsonSerializerOptions { PropertyNameCaseInsensitive = true })!;
    }

    /// <summary>The friendly name for a server address (port and case ignored), or null for a bare IP, a single label or no address.</summary>
    public static string? Resolve(string? address)
    {
        if (address == null) return null;
        var host = address.Trim().ToLowerInvariant();
        var colon = host.IndexOf(':');
        if (colon >= 0)
        {
            if (host.IndexOf(':', colon + 1) >= 0) return null; // IPv6
            host = host[..colon];
        }
        host = host.TrimEnd('.');
        if (!Host.IsMatch(host)) return null;
        var labels = host.Split('.');
        // Top-level domains are never numeric: "192.168" (an IP being typed) and IPv4 addresses get no name.
        if (labels.Length < 2 || labels[^1].All(char.IsAsciiDigit)) return null;
        for (var i = 0; i < labels.Length; i++)
            if (Known.Names.TryGetValue(string.Join('.', labels[i..]), out var known)) return known;
        var end = labels.Length - 1;
        for (var i = 1; i < labels.Length; i++)
        {
            if (!Known.Suffixes.Contains(string.Join('.', labels[i..]))) continue;
            end = i;
            break;
        }
        var start = 0;
        while (start < end && Known.Prefixes.Contains(labels[start])) start++;
        return start < end ? TitleCase(labels[end - 1]) : null;
    }

    private static string? TitleCase(string label)
    {
        var words = label.Split(['-', '_'], StringSplitOptions.RemoveEmptyEntries).Select(w => char.ToUpperInvariant(w[0]) + w[1..]).ToList();
        return words.Count == 0 ? null : string.Join(' ', words);
    }

    /// <summary>
    /// One Add/Edit Server form's name field. While the name is empty, the default or the name this filled in, each address change
    /// fills in the address's friendly name (or puts the earlier name back when the address has none). A typed name is never replaced.
    /// </summary>
    public sealed class AutoName
    {
        private readonly string _defaultName;
        private string? _address, _filled, _before;

        public AutoName(string defaultName = DefaultName) => _defaultName = defaultName;

        /// <summary>The text the name field should show now, or null to leave it as it is. Call it whenever either field changes.</summary>
        public string? Update(string name, string address)
        {
            if (address == _address) return null;
            _address = address;
            var ours = name == _filled;
            if (!ours && !string.IsNullOrWhiteSpace(name) && name != _defaultName && name != DefaultName)
            {
                _filled = null;
                return null;
            }
            if (!ours) _before = name;
            _filled = Resolve(address);
            var next = _filled ?? _before!;
            return next == name ? null : next;
        }
    }
}
