using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>What a Browse list shows: a Modrinth project type and a CurseForge class.</summary>
public enum ContentKind { Mod, ResourcePack, Shader, DataPack }

/// <summary>One downloadable file. <see cref="Sha1"/> is null only when the source gives no hash.</summary>
public sealed record ContentFile(string ProjectId, string FileId, string VersionName, string FileName, string Url, string? Sha1, long Size)
{
    /// <summary>The Minecraft versions the release supports, as the source lists them.</summary>
    public IReadOnlyList<string> GameVersions { get; init; } = Array.Empty<string>();
}

/// <summary>A search or lookup failed; the message is meant for the user.</summary>
public sealed class ContentSourceException(string message, Exception? inner = null) : Exception(message, inner);

/// <summary>
/// Modrinth and CurseForge search, file lookup and verified download for mods, resource packs, shader packs and data packs.
/// Failures throw <see cref="ContentSourceException"/> with a message for the user instead of returning an empty list.
/// </summary>
public sealed class ContentCatalog
{
    public const string UserAgent = "TheLadsLauncher/1.0.0 (contact@thelads.com)";
    public const string MissingKeyMessage = "CurseForge needs an API key, and none is set. Enter yours in Mods > Mod Manager Settings (get one at console.curseforge.com), or search Modrinth.";

    /// <summary>The key built into this launcher (MSBuild property / environment variable LADS_CURSEFORGE_API_KEY at build
    /// time, see docs/CURSEFORGE_API_KEY.md), or "".</summary>
    public static string BuiltInCurseForgeKey { get; } = typeof(ContentCatalog).Assembly.GetCustomAttributes<AssemblyMetadataAttribute>()
        .FirstOrDefault(a => a.Key == "CurseForgeApiKey")?.Value?.Trim() ?? "";

    private readonly HttpClient _http;
    private readonly string _modrinth, _curseForge, _curseForgeKey;

    /// <param name="curseForgeKey">The key from Mod Manager Settings; empty falls back to <see cref="BuiltInCurseForgeKey"/>.</param>
    public ContentCatalog(HttpClient http, string? modrinthApi, string? curseForgeApi, string? curseForgeKey)
    {
        _http = http;
        _modrinth = ApiBase(modrinthApi, "https://api.modrinth.com/v2", "/v2");
        _curseForge = ApiBase(curseForgeApi, "https://api.curseforge.com/v1", "/v1");
        _curseForgeKey = curseForgeKey?.Trim() is { Length: > 0 } own ? own : BuiltInCurseForgeKey;
    }

    public bool HasCurseForgeKey => _curseForgeKey.Length > 0;

    private static string ApiBase(string? configured, string fallback, string suffix)
    {
        var url = string.IsNullOrWhiteSpace(configured) ? fallback : configured.Trim().TrimEnd('/');
        return url.EndsWith(suffix) || url.Contains(suffix + "/") ? url : url + suffix;
    }

    /// <summary>The Modrinth loader of a game's mods: Forge on 1.8.9, Fabric on every other Lads version.</summary>
    public static string ModLoader(string minecraftVersion) => GameVersionPolicy.UsesForge(minecraftVersion) ? "forge" : "fabric";

    /// <summary>The shader loader a target runs: OptiFine with Forge (Lads 1.8.9), Iris everywhere else.</summary>
    public static string ShaderLoader(string modLoader) => modLoader == "forge" ? "optifine" : "iris";

    /// <summary>The Modrinth version loader of a kind's files: data packs and resource packs have their own.</summary>
    public static string FileLoader(ContentKind kind, string modLoader) => kind switch
    {
        ContentKind.ResourcePack => "minecraft",
        ContentKind.DataPack => "datapack",
        ContentKind.Shader => ShaderLoader(modLoader),
        _ => modLoader
    };

    private static string ModrinthType(ContentKind kind) => kind switch
    {
        ContentKind.ResourcePack => "resourcepack", ContentKind.Shader => "shader", ContentKind.DataPack => "datapack", _ => "mod"
    };

    // CurseForge class ids of game 432 (Minecraft): Mods, Resource Packs, Shaders, Data Packs.
    private static int CurseForgeClass(ContentKind kind) => kind switch
    {
        ContentKind.ResourcePack => 12, ContentKind.Shader => 6552, ContentKind.DataPack => 6945, _ => 6
    };

    // CurseForge ModLoaderType: only mods are filtered by loader.
    private static int? CurseForgeLoader(ContentKind kind, string modLoader) => kind != ContentKind.Mod ? null : modLoader switch
    {
        "forge" => 1, "fabric" => 4, "quilt" => 5, "neoforge" => 6, _ => null
    };

    /// <param name="modLoader">fabric, forge, quilt or neoforge: filters mods and picks the shader loader.</param>
    public async Task<List<ModSearchItem>> SearchModrinthAsync(ContentKind kind, string query, string minecraftVersion, string modLoader,
        CancellationToken cancellationToken = default)
    {
        var facets = new List<string[]> { new[] { "project_type:" + ModrinthType(kind) } };
        if (kind is ContentKind.Mod or ContentKind.Shader) facets.Add(new[] { "categories:" + FileLoader(kind, modLoader) });
        if (minecraftVersion.Length > 0) facets.Add(new[] { "versions:" + minecraftVersion });
        // An empty query lists the most downloaded projects, so the page is never blank.
        string index = string.IsNullOrWhiteSpace(query) ? "downloads" : "relevance";
        string url = $"{_modrinth}/search?query={Uri.EscapeDataString(query)}&facets={Uri.EscapeDataString(JsonSerializer.Serialize(facets))}&index={index}&limit=30";
        return await SendAsync("Modrinth", new HttpRequestMessage(HttpMethod.Get, url), root => root.GetProperty("hits").EnumerateArray().Select(hit => new ModSearchItem
        {
            Id = Text(hit, "project_id"),
            Name = Text(hit, "title"),
            Summary = Text(hit, "description"),
            IconUrl = Text(hit, "icon_url"),
            Author = Text(hit, "author"),
            DownloadCount = hit.TryGetProperty("downloads", out var d) && d.ValueKind == JsonValueKind.Number ? d.GetInt64() : 0,
            Provider = "Modrinth",
            ProjectSlug = Text(hit, "slug"),
            Categories = Strings(hit, "categories"),
            Version = Text(hit, "latest_version"),
        }).ToList(), cancellationToken);
    }

    public async Task<List<ModSearchItem>> SearchCurseForgeAsync(ContentKind kind, string query, string minecraftVersion, string modLoader,
        CancellationToken cancellationToken = default)
    {
        // sortField 2 = popularity; without sortOrder=desc CurseForge lists the least popular first.
        string url = $"{_curseForge}/mods/search?gameId=432&classId={CurseForgeClass(kind)}&searchFilter={Uri.EscapeDataString(query)}&sortField=2&sortOrder=desc&pageSize=30"
            + (minecraftVersion.Length > 0 ? "&gameVersion=" + Uri.EscapeDataString(minecraftVersion) : "")
            + (CurseForgeLoader(kind, modLoader) is int loader ? $"&modLoaderType={loader}" : "");
        return await SendAsync("CurseForge", CurseForgeRequest(HttpMethod.Get, url), root => root.GetProperty("data").EnumerateArray().Select(mod => new ModSearchItem
        {
            Id = Number(mod, "id"),
            Name = Text(mod, "name"),
            Summary = Text(mod, "summary"),
            IconUrl = mod.TryGetProperty("logo", out var logo) && logo.ValueKind == JsonValueKind.Object ? Text(logo, "thumbnailUrl") : "",
            Author = mod.TryGetProperty("authors", out var authors) && authors.ValueKind == JsonValueKind.Array && authors.GetArrayLength() > 0
                ? Text(authors[0], "name") : "",
            DownloadCount = mod.TryGetProperty("downloadCount", out var d) && d.ValueKind == JsonValueKind.Number ? (long)d.GetDouble() : 0,
            Provider = "CurseForge",
            ProjectSlug = Text(mod, "slug"),
            Categories = mod.TryGetProperty("categories", out var cats) && cats.ValueKind == JsonValueKind.Array
                ? cats.EnumerateArray().Select(c => Text(c, "name")).Where(n => n.Length > 0).ToList() : new List<string>(),
        }).ToList(), cancellationToken);
    }

    public Task<List<ModSearchItem>> SearchAsync(string provider, ContentKind kind, string query, string minecraftVersion, string modLoader,
        CancellationToken cancellationToken = default) => provider == "CurseForge"
        ? SearchCurseForgeAsync(kind, query, minecraftVersion, modLoader, cancellationToken)
        : SearchModrinthAsync(kind, query, minecraftVersion, modLoader, cancellationToken);

    /// <summary>The newest file of a project for the game version, or null when it has none.</summary>
    public async Task<ContentFile?> LatestFileAsync(ModSearchItem project, ContentKind kind, string minecraftVersion, string modLoader,
        CancellationToken cancellationToken = default)
    {
        if (project.Provider == "CurseForge")
        {
            string url = $"{_curseForge}/mods/{Uri.EscapeDataString(project.Id)}/files?gameVersion={Uri.EscapeDataString(minecraftVersion)}&pageSize=1"
                + (CurseForgeLoader(kind, modLoader) is int loader ? $"&modLoaderType={loader}" : "");
            return await SendAsync("CurseForge", CurseForgeRequest(HttpMethod.Get, url),
                root => root.GetProperty("data") is var data && data.GetArrayLength() > 0 ? CurseForgeFile(data[0]) : null, cancellationToken);
        }
        string loaders = Uri.EscapeDataString(JsonSerializer.Serialize(new[] { FileLoader(kind, modLoader) }));
        string versions = Uri.EscapeDataString(JsonSerializer.Serialize(new[] { minecraftVersion }));
        return await SendAsync("Modrinth", new HttpRequestMessage(HttpMethod.Get,
            $"{_modrinth}/project/{Uri.EscapeDataString(project.Id)}/version?loaders={loaders}&game_versions={versions}"),
            root => root.GetArrayLength() > 0 ? ModrinthFile(root[0]) : null, cancellationToken);
    }

    /// <summary>Modrinth's newest version, per installed file SHA-1, for the game version and loader. Files Modrinth does not
    /// know, or that have no release for the version, are missing from the result.</summary>
    public async Task<Dictionary<string, ContentFile>> ModrinthUpdatesAsync(IReadOnlyCollection<string> sha1s, string minecraftVersion, string loader,
        CancellationToken cancellationToken = default)
    {
        if (sha1s.Count == 0) return new Dictionary<string, ContentFile>();
        return await ModrinthByHashAsync("version_files/update",
            new { hashes = sha1s, algorithm = "sha1", loaders = new[] { loader }, game_versions = new[] { minecraftVersion } }, cancellationToken);
    }

    /// <summary>Modrinth's release of each installed file, per SHA-1. Files Modrinth does not know are missing from the result.</summary>
    public async Task<Dictionary<string, ContentFile>> ModrinthFilesAsync(IReadOnlyCollection<string> sha1s, CancellationToken cancellationToken = default) =>
        sha1s.Count == 0 ? new Dictionary<string, ContentFile>()
            : await ModrinthByHashAsync("version_files", new { hashes = sha1s, algorithm = "sha1" }, cancellationToken);

    private Task<Dictionary<string, ContentFile>> ModrinthByHashAsync(string path, object body, CancellationToken cancellationToken) =>
        SendAsync("Modrinth", new HttpRequestMessage(HttpMethod.Post, $"{_modrinth}/{path}")
        { Content = new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json") }, root =>
        {
            var result = new Dictionary<string, ContentFile>(StringComparer.OrdinalIgnoreCase);
            foreach (var entry in root.EnumerateObject())
                if (ModrinthFile(entry.Value) is { } file) result[entry.Name] = file;
            return result;
        }, cancellationToken);

    /// <summary>CurseForge's file of each fingerprint it knows exactly (file id and project id), keyed by fingerprint.</summary>
    public async Task<Dictionary<uint, ContentFile>> CurseForgeMatchesAsync(IReadOnlyCollection<uint> fingerprints, CancellationToken cancellationToken = default)
    {
        if (fingerprints.Count == 0) return new Dictionary<uint, ContentFile>();
        var request = CurseForgeRequest(HttpMethod.Post, $"{_curseForge}/fingerprints/432");
        request.Content = new StringContent(JsonSerializer.Serialize(new { fingerprints }), Encoding.UTF8, "application/json");
        return await SendAsync("CurseForge", request, root =>
        {
            var result = new Dictionary<uint, ContentFile>();
            foreach (var match in root.GetProperty("data").GetProperty("exactMatches").EnumerateArray())
            {
                var file = match.GetProperty("file");
                if (file.TryGetProperty("fileFingerprint", out var fp) && fp.TryGetUInt32(out var fingerprint) && CurseForgeFile(file) is { } parsed)
                    result[fingerprint] = parsed;
            }
            return result;
        }, cancellationToken);
    }

    private HttpRequestMessage CurseForgeRequest(HttpMethod method, string url)
    {
        if (!HasCurseForgeKey) throw new ContentSourceException(MissingKeyMessage);
        var request = new HttpRequestMessage(method, url);
        request.Headers.TryAddWithoutValidation("x-api-key", _curseForgeKey);
        return request;
    }

    /// <summary>Sends the request and reads the JSON answer with <paramref name="read"/>; every failure becomes a <see cref="ContentSourceException"/>.</summary>
    private async Task<T> SendAsync<T>(string source, HttpRequestMessage request, Func<JsonElement, T> read, CancellationToken cancellationToken)
    {
        using (request)
        {
            request.Headers.UserAgent.ParseAdd(UserAgent);
            request.Headers.Accept.ParseAdd("application/json");
            HttpResponseMessage response;
            try { response = await _http.SendAsync(request, cancellationToken); }
            catch (HttpRequestException e) { throw new ContentSourceException($"Could not reach {source}: {e.Message}", e); }
            catch (TaskCanceledException e) when (!cancellationToken.IsCancellationRequested) { throw new ContentSourceException($"{source} did not answer in time.", e); }
            using (response)
            {
                string text = await response.Content.ReadAsStringAsync(cancellationToken);
                if (source == "CurseForge" && response.StatusCode == HttpStatusCode.Forbidden)
                    throw new ContentSourceException("CurseForge refused the API key (403: missing or invalid). Check it in Mods > Mod Manager Settings.");
                if (!response.IsSuccessStatusCode)
                    throw new ContentSourceException($"{source} answered {(int)response.StatusCode} {response.ReasonPhrase}: {Shorten(text)}");
                try
                {
                    using var document = JsonDocument.Parse(text);
                    return read(document.RootElement);
                }
                catch (Exception e) when (e is JsonException or KeyNotFoundException or InvalidOperationException or FormatException)
                {
                    throw new ContentSourceException($"{source} sent an answer the launcher does not understand ({e.Message}): {Shorten(text)}", e);
                }
            }
        }
    }

    private static ContentFile? ModrinthFile(JsonElement version)
    {
        if (!version.TryGetProperty("files", out var files) || files.ValueKind != JsonValueKind.Array || files.GetArrayLength() == 0) return null;
        var file = files.EnumerateArray().FirstOrDefault(f => f.TryGetProperty("primary", out var p) && p.ValueKind == JsonValueKind.True);
        if (file.ValueKind == JsonValueKind.Undefined) file = files[0];
        return new ContentFile(Text(version, "project_id"), Text(version, "id"), Text(version, "version_number"), Text(file, "filename"), Text(file, "url"),
            file.TryGetProperty("hashes", out var hashes) && hashes.ValueKind == JsonValueKind.Object ? Text(hashes, "sha1") is { Length: > 0 } s ? s : null : null,
            file.TryGetProperty("size", out var size) && size.ValueKind == JsonValueKind.Number ? size.GetInt64() : 0) { GameVersions = Strings(version, "game_versions") };
    }

    // A null downloadUrl means the author does not allow downloads outside CurseForge's own app; that is respected.
    private static ContentFile? CurseForgeFile(JsonElement file)
    {
        string sha1 = file.TryGetProperty("hashes", out var hashes) && hashes.ValueKind == JsonValueKind.Array
            ? hashes.EnumerateArray().Where(h => h.TryGetProperty("algo", out var a) && a.ValueKind == JsonValueKind.Number && a.GetInt32() == 1)
                .Select(h => Text(h, "value")).FirstOrDefault() ?? "" : "";
        return new ContentFile(Number(file, "modId"), Number(file, "id"), Text(file, "displayName"), Text(file, "fileName"), Text(file, "downloadUrl"),
            sha1.Length > 0 ? sha1 : null, file.TryGetProperty("fileLength", out var length) && length.ValueKind == JsonValueKind.Number ? length.GetInt64() : 0)
        { GameVersions = Strings(file, "gameVersions") };
    }

    /// <summary>
    /// Downloads <paramref name="file"/> into a staging folder inside <paramref name="directory"/>, checks its SHA-1, then moves it
    /// in under its own name (or "name (2).ext" when taken). Nothing in the folder is replaced. Returns the final path.
    /// </summary>
    public async Task<string> InstallAsync(ContentFile file, string directory, SharedContentService staging, IProgress<double>? progress = null,
        CancellationToken cancellationToken = default)
    {
        Directory.CreateDirectory(directory);
        using var incoming = staging.CreateIncomingFolder(directory);
        var temp = await DownloadVerifiedAsync(file, incoming.Path, progress, cancellationToken);
        return await Task.Run(() => SafeFileOps.MoveToFreeName(temp, directory, file.FileName), cancellationToken);
    }

    /// <summary>Downloads into <paramref name="folder"/> (a staging folder) and checks the SHA-1; a mismatch deletes the download.</summary>
    public async Task<string> DownloadVerifiedAsync(ContentFile file, string folder, IProgress<double>? progress, CancellationToken cancellationToken)
    {
        if (!SafeFileOps.IsPlainFileName(file.FileName))
            throw new ContentSourceException($"The download has no usable file name ('{file.FileName}'). Nothing was downloaded.");
        if (file.Url.Length == 0)
            throw new ContentSourceException($"{file.FileName} has no download link: its author allows downloads only on the project's own site.");
        var temp = Path.Combine(folder, file.FileName);
        try
        {
            using (var response = await _http.GetAsync(file.Url, HttpCompletionOption.ResponseHeadersRead, cancellationToken))
            {
                response.EnsureSuccessStatusCode();
                long? total = response.Content.Headers.ContentLength;
                await using var source = await response.Content.ReadAsStreamAsync(cancellationToken);
                await using var target = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, true);
                var buffer = new byte[81920];
                long done = 0;
                int read;
                while ((read = await source.ReadAsync(buffer, cancellationToken)) > 0)
                {
                    await target.WriteAsync(buffer.AsMemory(0, read), cancellationToken);
                    done += read;
                    if (total > 0) progress?.Report(done * 100.0 / total.Value);
                }
            }
            if (file.Sha1 != null && !Sha1(temp).Equals(file.Sha1, StringComparison.OrdinalIgnoreCase))
                throw new ContentSourceException($"The download of {file.FileName} does not match its SHA-1; it was deleted and nothing was changed.");
            return temp;
        }
        catch (Exception e)
        {
            if (File.Exists(temp)) File.Delete(temp);
            if (e is HttpRequestException || (e is TaskCanceledException && !cancellationToken.IsCancellationRequested))
                throw new ContentSourceException($"Could not download {file.FileName}: {e.Message}", e);
            throw;
        }
    }

    public static string Sha1(string path)
    {
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read | FileShare.Delete, 1 << 16, FileOptions.SequentialScan);
        return Convert.ToHexString(SHA1.HashData(stream)).ToLowerInvariant();
    }

    /// <summary>CurseForge's file fingerprint: MurmurHash2 (seed 1) of the file without its whitespace bytes (tab, LF, CR, space).</summary>
    public static uint CurseForgeFingerprint(byte[] data)
    {
        var bytes = data.Where(b => b is not (9 or 10 or 13 or 32)).ToArray();
        const uint m = 0x5bd1e995;
        unchecked
        {
            uint h = 1u ^ (uint)bytes.Length;
            int i = 0;
            for (; i + 4 <= bytes.Length; i += 4)
            {
                uint k = BitConverter.ToUInt32(bytes, i);
                k *= m; k ^= k >> 24; k *= m;
                h *= m; h ^= k;
            }
            switch (bytes.Length - i)
            {
                case 3: h ^= (uint)bytes[i + 2] << 16; goto case 2;
                case 2: h ^= (uint)bytes[i + 1] << 8; goto case 1;
                case 1: h ^= bytes[i]; h *= m; break;
            }
            h ^= h >> 13; h *= m; h ^= h >> 15;
            return h;
        }
    }

    private static string Text(JsonElement element, string name) =>
        element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() ?? "" : "";

    private static string Number(JsonElement element, string name) =>
        element.TryGetProperty(name, out var value) ? value.ValueKind == JsonValueKind.Number ? value.GetRawText() : Text(element, name) : "";

    private static List<string> Strings(JsonElement element, string name) =>
        element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.Array
            ? value.EnumerateArray().Where(v => v.ValueKind == JsonValueKind.String).Select(v => v.GetString()!).Where(s => s.Length > 0).ToList()
            : new List<string>();

    private static string Shorten(string text) => text.Length <= 200 ? text.Trim() : text[..200].Trim() + "…";
}
