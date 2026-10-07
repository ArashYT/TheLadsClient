using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public sealed record ModpackHit(string ProjectId, string Slug, string Title, string Author, string Description, string? IconUrl,
    long Downloads, IReadOnlyList<string> GameVersions, IReadOnlyList<string> Loaders);

public sealed record ModpackVersion(string Id, string ProjectId, string VersionNumber, IReadOnlyList<string> GameVersions,
    IReadOnlyList<string> Loaders, DateTime Published, string Url, string? Sha1, string? Sha512, long Size);

/// <summary>The few Modrinth API v2 calls the Modpacks tab needs (no key; Modrinth asks for a descriptive User-Agent).</summary>
public static class ModrinthModpacks
{
    private const string Api = "https://api.modrinth.com/v2";
    public static readonly string[] LoaderFilters = { "fabric", "quilt", "forge", "neoforge" };

    public static HttpClient CreateClient()
    {
        var http = new HttpClient { Timeout = TimeSpan.FromMinutes(2) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd($"TheLadsLauncher/{Program.Version} (contact@thelads.com)");
        return http;
    }

    /// <param name="sort">Modrinth index: relevance, downloads, follows, newest or updated.</param>
    public static async Task<List<ModpackHit>> SearchAsync(HttpClient http, string query, string? gameVersion, string? loader, string sort,
        int offset, CancellationToken token, string? projectId = null)
    {
        return await SearchContentAsync(http, query, "modpack", gameVersion, loader, sort, offset, token, projectId);
    }

    public static async Task<List<ModpackHit>> SearchContentAsync(HttpClient http, string query, string projectType, string? gameVersion, string? loader, string sort,
        int offset, CancellationToken token, string? projectId = null)
    {
        var facets = new List<string[]> { new[] { "project_type:" + projectType } };
        if (projectId != null) facets.Add(new[] { "project_id:" + projectId });
        if (!string.IsNullOrEmpty(gameVersion)) facets.Add(new[] { "versions:" + gameVersion });
        if (!string.IsNullOrEmpty(loader) && (projectType == "mod" || projectType == "modpack")) facets.Add(new[] { "categories:" + loader });
        var url = $"{Api}/search?query={Uri.EscapeDataString(query)}&facets={Uri.EscapeDataString(JsonSerializer.Serialize(facets))}&index={sort}&offset={offset}&limit=24";
        using var doc = JsonDocument.Parse(await http.GetStringAsync(url, token));
        return doc.RootElement.GetProperty("hits").EnumerateArray().Select(h => new ModpackHit(
            Str(h, "project_id"), Str(h, "slug"), Str(h, "title"), Str(h, "author"), Str(h, "description"),
            h.TryGetProperty("icon_url", out var icon) && icon.ValueKind == JsonValueKind.String ? icon.GetString() : null,
            h.TryGetProperty("downloads", out var d) ? d.GetInt64() : 0, Strings(h, "versions"),
            Strings(h, "categories").Where(LoaderFilters.Contains).ToList())).ToList();
    }

    public static async Task<string> DownloadLatestFileAsync(HttpClient http, string projectId, string projectType, string? gameVersion, string? loader, string targetFolder, CancellationToken token)
    {
        var query = new List<string>();
        if (!string.IsNullOrEmpty(gameVersion)) query.Add("game_versions=" + Uri.EscapeDataString("[\"" + gameVersion + "\"]"));
        if (!string.IsNullOrEmpty(loader) && (projectType == "mod" || projectType == "modpack")) query.Add("loaders=" + Uri.EscapeDataString("[\"" + loader + "\"]"));
        string qs = query.Count > 0 ? "?" + string.Join("&", query) : "";
        string url = $"{Api}/project/{Uri.EscapeDataString(projectId)}/version{qs}";
        using var doc = JsonDocument.Parse(await http.GetStringAsync(url, token));
        var versions = doc.RootElement.EnumerateArray().ToList();
        if (versions.Count == 0)
        {
            // Try without loader constraint as fallback
            if (!string.IsNullOrEmpty(loader))
            {
                string fallbackUrl = $"{Api}/project/{Uri.EscapeDataString(projectId)}/version" + (!string.IsNullOrEmpty(gameVersion) ? $"?game_versions={Uri.EscapeDataString("[\"" + gameVersion + "\"]")}" : "");
                using var fallbackDoc = JsonDocument.Parse(await http.GetStringAsync(fallbackUrl, token));
                versions = fallbackDoc.RootElement.EnumerateArray().ToList();
            }
            if (versions.Count == 0) throw new InvalidOperationException("No compatible versions found on Modrinth for this Minecraft version.");
        }
        var ver = versions[0];
        var files = ver.GetProperty("files").EnumerateArray().ToList();
        var file = files.FirstOrDefault(f => f.TryGetProperty("primary", out var p) && p.GetBoolean());
        if (file.ValueKind == JsonValueKind.Undefined && files.Count > 0) file = files[0];
        if (file.ValueKind == JsonValueKind.Undefined) throw new InvalidOperationException("No downloadable files found in this version.");
        string fileUrl = file.GetProperty("url").GetString()!;
        string fileName = file.GetProperty("filename").GetString()!;
        Directory.CreateDirectory(targetFolder);
        string dest = Path.Combine(targetFolder, fileName);

        using var response = await http.GetAsync(fileUrl, HttpCompletionOption.ResponseHeadersRead, token);
        response.EnsureSuccessStatusCode();
        await using var input = await response.Content.ReadAsStreamAsync(token);
        await using var output = new FileStream(dest, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);
        await input.CopyToAsync(output, token);
        return fileName;
    }

    /// <summary>The project's versions that ship a .mrpack, newest first.</summary>
    public static async Task<List<ModpackVersion>> VersionsAsync(HttpClient http, string projectId, CancellationToken token)
    {
        using var doc = JsonDocument.Parse(await http.GetStringAsync($"{Api}/project/{Uri.EscapeDataString(projectId)}/version", token));
        return doc.RootElement.EnumerateArray().Select(ParseVersion).OfType<ModpackVersion>().OrderByDescending(v => v.Published).ToList();
    }

    /// <summary>The Modrinth version a .mrpack file was published as (by its SHA-1), or null for a pack that is not on Modrinth.</summary>
    public static async Task<ModpackVersion?> FindByFileAsync(HttpClient http, string file, CancellationToken token)
    {
        string sha1;
        await using (var stream = File.OpenRead(file))
            sha1 = Convert.ToHexString(await System.Security.Cryptography.SHA1.HashDataAsync(stream, token)).ToLowerInvariant();
        using var response = await http.GetAsync($"{Api}/version_file/{sha1}?algorithm=sha1", token);
        if (!response.IsSuccessStatusCode) return null;
        using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync(token));
        return ParseVersion(doc.RootElement);
    }

    /// <summary>(title, author, icon URL) of a project, as Browse shows it (Modrinth's search names the author, also for
    /// projects owned by an organization). A project the search does not list has no author.</summary>
    public static async Task<(string Title, string? Author, string? IconUrl)> ProjectAsync(HttpClient http, string projectId, CancellationToken token)
    {
        if ((await SearchAsync(http, "", null, null, "relevance", 0, token, projectId)).FirstOrDefault() is { } hit) return (hit.Title, hit.Author, hit.IconUrl);
        using var project = JsonDocument.Parse(await http.GetStringAsync($"{Api}/project/{Uri.EscapeDataString(projectId)}", token));
        var icon = project.RootElement.TryGetProperty("icon_url", out var i) && i.ValueKind == JsonValueKind.String ? i.GetString() : null;
        return (Str(project.RootElement, "title"), null, icon);
    }

    /// <summary>Downloads the version's .mrpack to <paramref name="target"/>, checked against Modrinth's hashes.</summary>
    public static async Task DownloadAsync(HttpClient http, ModpackVersion version, string target, CancellationToken token)
    {
        if (!Uri.TryCreate(version.Url, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps)
            throw new InvalidDataException($"'{version.Url}' is not an https download.");
        using (var response = await http.GetAsync(uri, HttpCompletionOption.ResponseHeadersRead, token))
        {
            response.EnsureSuccessStatusCode();
            await using var input = await response.Content.ReadAsStreamAsync(token);
            await using var output = new FileStream(target, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);
            await ClientModInstaller.CopyWithStallTimeoutAsync(input, output, version.Size > 0 ? version.Size : 1L << 31, TimeSpan.FromSeconds(60), "the modpack", token);
        }
        if (!Mrpack.HashMatches(target, version.Sha1, version.Sha512))
        {
            File.Delete(target);
            throw new InvalidDataException("The downloaded modpack does not match Modrinth's hash.");
        }
    }

    /// <summary>The newest loader version for a new instance: Fabric/Quilt meta, Forge's promotions, NeoForge's maven.</summary>
    public static async Task<string> LatestLoaderAsync(HttpClient http, string loader, string gameVersion, CancellationToken token)
    {
        switch (loader)
        {
            case "vanilla": return "";
            case "fabric":
            {
                var l = await new CmlLib.Core.ModLoaders.FabricMC.FabricInstaller(http).GetFirstLoader(gameVersion);
                return l?.Version ?? throw new InvalidOperationException($"Fabric has no loader for Minecraft {gameVersion}.");
            }
            case "quilt":
            {
                var l = await new CmlLib.Core.ModLoaders.QuiltMC.QuiltInstaller(http).GetFirstLoader(gameVersion);
                return l?.Version ?? throw new InvalidOperationException($"Quilt has no loader for Minecraft {gameVersion}.");
            }
            case "forge":
            {
                using var doc = JsonDocument.Parse(await http.GetStringAsync("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json", token));
                var promos = doc.RootElement.GetProperty("promos");
                foreach (var key in new[] { gameVersion + "-recommended", gameVersion + "-latest" })
                    if (promos.TryGetProperty(key, out var v)) return v.GetString()!;
                throw new InvalidOperationException($"Forge has no build for Minecraft {gameVersion}.");
            }
            case "neoforge":
            {
                // NeoForge numbers its builds after the game: 1.21.1 -> 21.1.x, 1.21 -> 21.0.x, 26.1 -> 26.1.x.
                var parts = gameVersion.Split('.');
                var prefix = parts[0] == "1" ? $"{parts[1]}.{(parts.Length > 2 ? parts[2] : "0")}." : gameVersion + ".";
                using var doc = JsonDocument.Parse(await http.GetStringAsync("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge", token));
                var builds = doc.RootElement.GetProperty("versions").EnumerateArray().Select(v => v.GetString()!).Where(v => v.StartsWith(prefix)).ToList();
                return builds.LastOrDefault(v => !v.Contains('-')) ?? builds.LastOrDefault()
                    ?? throw new InvalidOperationException($"NeoForge has no build for Minecraft {gameVersion}.");
            }
            default: throw new InvalidOperationException($"Unknown loader '{loader}'.");
        }
    }

    private static ModpackVersion? ParseVersion(JsonElement v)
    {
        var file = v.GetProperty("files").EnumerateArray().Where(f => Str(f, "filename").EndsWith(".mrpack", StringComparison.OrdinalIgnoreCase))
            .OrderByDescending(f => f.TryGetProperty("primary", out var p) && p.ValueKind == JsonValueKind.True).FirstOrDefault();
        if (file.ValueKind != JsonValueKind.Object) return null;
        var hashes = file.GetProperty("hashes");
        return new ModpackVersion(Str(v, "id"), Str(v, "project_id"), Str(v, "version_number"), Strings(v, "game_versions"), Strings(v, "loaders"),
            DateTime.TryParse(Str(v, "date_published"), out var date) ? date.ToUniversalTime() : DateTime.MinValue, Str(file, "url"),
            hashes.TryGetProperty("sha1", out var s1) ? s1.GetString() : null, hashes.TryGetProperty("sha512", out var s5) ? s5.GetString() : null,
            file.TryGetProperty("size", out var size) ? size.GetInt64() : 0);
    }

    private static string Str(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() ?? "" : "";

    private static List<string> Strings(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.Array ? v.EnumerateArray().Select(x => x.GetString() ?? "").ToList() : new List<string>();
}
