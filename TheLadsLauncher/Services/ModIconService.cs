using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>
/// Icons for the Installed Mods list: the jar's own (fabric.mod.json "icon", META-INF/mods.toml or neoforge.mods.toml
/// "logoFile", mcmod.info "logoFile"), else the Modrinth project icon found by the jar's SHA-512 (or a pack entry's project id).
/// Every icon found is cached as &lt;cache&gt;/&lt;key&gt;.img; a Modrinth miss is remembered for a week in &lt;key&gt;.none.
/// </summary>
public sealed class ModIconService
{
    public const int MaximumIconBytes = 1024 * 1024;
    private static readonly TimeSpan MissRetry = TimeSpan.FromDays(7);
    private readonly string _cache;
    private readonly HttpClient _http;
    private readonly string _api;
    // Keys already asked of Modrinth this session: the list re-renders on every search keystroke.
    private readonly HashSet<string> _asked = new(StringComparer.Ordinal);

    public ModIconService(string cacheDirectory, HttpClient http, string api = "https://api.modrinth.com/v2")
    {
        _cache = cacheDirectory;
        _http = http;
        _api = api.TrimEnd('/');
    }

    /// <summary>Calls <paramref name="found"/>(entry, cached icon file) for each entry that has an icon, jar icons first. It runs
    /// on a worker thread. Offline or on any Modrinth problem the rest keep their placeholder until the next session.</summary>
    public async Task LoadAsync(IReadOnlyList<ModInventoryEntry> entries, Action<ModInventoryEntry, string> found, CancellationToken token = default)
    {
        var remote = await Task.Run(() => LoadLocal(entries, found, token), token).ConfigureAwait(false);
        List<KeyValuePair<string, List<ModInventoryEntry>>> ask;
        lock (_asked) ask = remote.Where(r => _asked.Add(r.Key)).ToList();
        if (ask.Count == 0) return;
        try
        {
            var projects = ask.Where(a => a.Key.StartsWith("project-", StringComparison.Ordinal)).ToDictionary(a => a.Key, a => a.Key["project-".Length..]);
            foreach (var chunk in ask.Select(a => a.Key).Where(k => !projects.ContainsKey(k)).Chunk(100))
                foreach (var (hash, project) in await ProjectsByHashAsync(chunk, token).ConfigureAwait(false)) projects[hash] = project;
            var icons = new Dictionary<string, string>(StringComparer.Ordinal);
            foreach (var chunk in projects.Values.Distinct().Chunk(100))
                foreach (var (project, url) in await IconUrlsAsync(chunk, token).ConfigureAwait(false)) icons[project] = url;
            await Parallel.ForEachAsync(ask, new ParallelOptions { MaxDegreeOfParallelism = 6, CancellationToken = token }, async (item, ct) =>
            {
                var file = Path.Combine(_cache, item.Key + ".img");
                if (projects.TryGetValue(item.Key, out var project) && icons.TryGetValue(project, out var url)
                    && await DownloadAsync(url, ct).ConfigureAwait(false) is { } bytes)
                {
                    await LockFiles.WriteAtomicallyAsync(file, bytes, ct).ConfigureAwait(false);
                    foreach (var entry in item.Value) found(entry, file);
                }
                else await File.WriteAllBytesAsync(Path.Combine(_cache, item.Key + ".none"), Array.Empty<byte>(), ct).ConfigureAwait(false);
            }).ConfigureAwait(false);
        }
        catch (Exception e) when (!token.IsCancellationRequested && e is HttpRequestException or JsonException or OperationCanceledException
            or IOException or UnauthorizedAccessException or InvalidOperationException or KeyNotFoundException)
        {
            // ponytail: a failed lookup is retried next session only (_asked), never in a loop while the page re-renders.
        }
    }

    // Cached and jar icons; returns what is left for Modrinth (cache key -> entries).
    private Dictionary<string, List<ModInventoryEntry>> LoadLocal(IReadOnlyList<ModInventoryEntry> entries, Action<ModInventoryEntry, string> found, CancellationToken token)
    {
        Directory.CreateDirectory(_cache);
        var remote = new Dictionary<string, List<ModInventoryEntry>>(StringComparer.Ordinal);
        foreach (var entry in entries)
        {
            token.ThrowIfCancellationRequested();
            string? key = entry.FilePath != null ? ModInventoryService.Scan(entry.FilePath, token).Hash
                : entry.ProjectId is { } id && Regex.IsMatch(id, "^[A-Za-z0-9]{1,64}$") ? "project-" + id : null;
            if (key == null) continue;
            var file = Path.Combine(_cache, key + ".img");
            if (File.Exists(file)) { found(entry, file); continue; }
            if (entry.FilePath != null && TryReadJarIcon(entry.FilePath) is { } bytes)
            {
                LockFiles.WriteAtomicallyAsync(file, bytes, token).GetAwaiter().GetResult();
                found(entry, file);
                continue;
            }
            var miss = Path.Combine(_cache, key + ".none");
            if (File.Exists(miss) && DateTime.UtcNow - File.GetLastWriteTimeUtc(miss) < MissRetry) continue;
            if (!remote.TryGetValue(key, out var list)) remote[key] = list = new List<ModInventoryEntry>();
            list.Add(entry);
        }
        return remote;
    }

    private static byte[]? TryReadJarIcon(string jar)
    {
        try { return ReadJarIcon(jar); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException or JsonException) { return null; }
    }

    /// <summary>The icon a jar names for itself (Fabric, Forge/NeoForge mods.toml, legacy Forge mcmod.info), or null.</summary>
    public static byte[]? ReadJarIcon(string jar)
    {
        using var stream = new FileStream(jar, FileMode.Open, FileAccess.Read, FileShare.Read | FileShare.Delete);
        using var zip = new ZipArchive(stream, ZipArchiveMode.Read);
        string? path = null;
        if (zip.GetEntry("fabric.mod.json") is { } fabric)
        {
            using var document = JsonDocument.Parse(Text(fabric), new JsonDocumentOptions { AllowTrailingCommas = true, CommentHandling = JsonCommentHandling.Skip });
            // One path, or a map of pixel size to path: the largest.
            if (document.RootElement.TryGetProperty("icon", out var icon))
                path = icon.ValueKind == JsonValueKind.String ? icon.GetString()
                    : icon.ValueKind == JsonValueKind.Object ? icon.EnumerateObject().Where(p => p.Value.ValueKind == JsonValueKind.String && int.TryParse(p.Name, out _))
                        .OrderByDescending(p => int.Parse(p.Name)).Select(p => p.Value.GetString()).FirstOrDefault() : null;
        }
        else if ((zip.GetEntry("META-INF/mods.toml") ?? zip.GetEntry("META-INF/neoforge.mods.toml")) is { } toml)
            path = Regex.Match(Text(toml), @"^\s*logoFile\s*=\s*[""']([^""']+)[""']", RegexOptions.Multiline) is { Success: true } m ? m.Groups[1].Value : null;
        else if (zip.GetEntry("mcmod.info") is { } info)
            path = Regex.Match(Text(info), @"""logoFile""\s*:\s*""([^""]+)""") is { Success: true } m ? m.Groups[1].Value : null;
        var entry = string.IsNullOrWhiteSpace(path) ? null : zip.GetEntry(path.Replace('\\', '/').TrimStart('/'));
        if (entry == null || entry.Length is 0 or > MaximumIconBytes) return null;
        using var input = entry.Open();
        using var bytes = new MemoryStream();
        input.CopyTo(bytes);
        return bytes.ToArray();
    }

    private static string Text(ZipArchiveEntry entry)
    {
        if (entry.Length > MaximumIconBytes) throw new InvalidDataException(entry.FullName + " is oversized.");
        using var reader = new StreamReader(entry.Open(), Encoding.UTF8);
        return reader.ReadToEnd();
    }

    private async Task<Dictionary<string, string>> ProjectsByHashAsync(IEnumerable<string> hashes, CancellationToken token)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, _api + "/version_files")
        {
            Content = new StringContent(JsonSerializer.Serialize(new { hashes, algorithm = "sha512" }), Encoding.UTF8, "application/json")
        };
        using var document = await SendAsync(request, token).ConfigureAwait(false);
        return document.RootElement.EnumerateObject().Where(p => p.Value.TryGetProperty("project_id", out var id) && id.ValueKind == JsonValueKind.String)
            .ToDictionary(p => p.Name, p => p.Value.GetProperty("project_id").GetString()!);
    }

    private async Task<Dictionary<string, string>> IconUrlsAsync(IEnumerable<string> projects, CancellationToken token)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, _api + "/projects?ids=" + Uri.EscapeDataString(JsonSerializer.Serialize(projects)));
        using var document = await SendAsync(request, token).ConfigureAwait(false);
        return document.RootElement.EnumerateArray()
            .Where(p => p.TryGetProperty("icon_url", out var url) && url.ValueKind == JsonValueKind.String && url.GetString()!.StartsWith("https://", StringComparison.Ordinal))
            .ToDictionary(p => p.GetProperty("id").GetString()!, p => p.GetProperty("icon_url").GetString()!);
    }

    private async Task<JsonDocument> SendAsync(HttpRequestMessage request, CancellationToken token)
    {
        request.Headers.UserAgent.ParseAdd("TheLadsClient/" + Program.Version);
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
        timeout.CancelAfter(TimeSpan.FromSeconds(10));
        using var response = await _http.SendAsync(request, timeout.Token).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        return JsonDocument.Parse(await response.Content.ReadAsStringAsync(timeout.Token).ConfigureAwait(false));
    }

    private async Task<byte[]?> DownloadAsync(string url, CancellationToken token)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.UserAgent.ParseAdd("TheLadsClient/" + Program.Version);
        using var response = await _http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, token).ConfigureAwait(false);
        if (!response.IsSuccessStatusCode || response.Content.Headers.ContentLength > MaximumIconBytes) return null;
        var bytes = await response.Content.ReadAsByteArrayAsync(token).ConfigureAwait(false);
        return bytes.Length is 0 or > MaximumIconBytes ? null : bytes;
    }
}
