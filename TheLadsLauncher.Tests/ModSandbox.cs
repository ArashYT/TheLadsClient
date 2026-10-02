using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text.Json;
using TheLadsLauncher.Services;

namespace TheLadsLauncher.Tests;

/// <summary>A temporary bundle + profile game directory with fixture jars, manifests and a scripted HTTP client.</summary>
internal sealed class ModSandbox : IDisposable
{
    public string Root { get; } = Path.Combine(Path.GetTempPath(), "LadsClientTests", Guid.NewGuid().ToString("N"));
    public string Bundle => Path.Combine(Root, "bundle");
    public string Game => Path.Combine(Root, "game");
    public string Mods => Path.Combine(Game, "mods");
    public string Cache => Path.Combine(Game, ".lads-mod-cache");
    public string Receipt => Path.Combine(Cache, "installed.json");
    public string State => Path.Combine(Game, ModPreferences.FileName);
    public List<string> Requests { get; } = new();
    public List<string> Messages { get; } = new();
    /// <summary>Answers api.modrinth.com/v2/version_file lookups; null means offline (503).</summary>
    public Func<string, string?>? VersionFile { get; set; }
    private readonly Dictionary<string, byte[]> downloads = new();
    public void MockDownload(string url, byte[] bytes) => downloads[url] = bytes;

    public ModSandbox() => Directory.CreateDirectory(Mods);

    public string Mod(string fileName) => Path.Combine(Mods, fileName);

    public static byte[] Jar(string id, string version = "1.0.0", Dictionary<string, string>? depends = null, string[]? provides = null,
        (string Name, byte[] Bytes)[]? nested = null, string environment = "*", Dictionary<string, string>? breaks = null,
        bool library = false, string? name = null)
    {
        using var bytes = new MemoryStream();
        using (var zip = new ZipArchive(bytes, ZipArchiveMode.Create, true))
        {
            using (var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open()))
                writer.Write(JsonSerializer.Serialize(new Dictionary<string, object?>
                {
                    ["schemaVersion"] = 1, ["id"] = id, ["version"] = version, ["name"] = name ?? id, ["environment"] = environment,
                    ["authors"] = new object[] { "Ann", new { name = "Bob" } }, ["license"] = "MIT",
                    ["depends"] = depends ?? new(), ["breaks"] = breaks ?? new(), ["provides"] = provides ?? Array.Empty<string>(),
                    ["jars"] = (nested ?? Array.Empty<(string, byte[])>()).Select(j => new { file = j.Item1 }),
                    ["custom"] = library ? new { modmenu = new { badges = new[] { "library" } } } : null
                }));
            foreach (var (entryName, content) in nested ?? Array.Empty<(string, byte[])>())
            {
                using var entry = zip.CreateEntry(entryName).Open();
                entry.Write(content);
            }
        }
        return bytes.ToArray();
    }

    /// <summary>A zip without fabric.mod.json (an ordinary library jar).</summary>
    public static byte[] PlainJar()
    {
        using var bytes = new MemoryStream();
        using (var zip = new ZipArchive(bytes, ZipArchiveMode.Create, true))
        using (var writer = new StreamWriter(zip.CreateEntry("lib/Thing.class").Open()))
            writer.Write("class");
        return bytes.ToArray();
    }

    public static string Sha(byte[] bytes) => Convert.ToHexString(SHA512.HashData(bytes)).ToLowerInvariant();

    public ClientModInstaller.Entry Pin(string id, byte[] bytes, string? fileName = null, string? projectId = null, string version = "1.0.0")
    {
        var name = fileName ?? id + "-" + version + ".jar";
        var url = $"https://cdn.modrinth.com/data/{id}/versions/{Sha(bytes)[..8]}/{Uri.EscapeDataString(name)}";
        downloads[url] = bytes;
        return new(projectId ?? "P" + id, id, "Mod " + id, id, "V" + id, version, name, url, Sha(bytes), bytes.Length, "MIT", null,
            "https://modrinth.com/mod/" + id);
    }

    public void WriteManifest(string version, IEnumerable<ClientModInstaller.Entry> mods, List<ClientModInstaller.RetiredEntry>? retired = null,
        Dictionary<string, List<string>>? published = null)
    {
        var directory = Path.Combine(Bundle, "game-mods", version);
        Directory.CreateDirectory(directory);
        File.WriteAllText(Path.Combine(directory, "client-mods.json"),
            JsonSerializer.Serialize(new ClientModInstaller.Manifest(version, mods.ToList(), false, retired, published)));
    }

    public void WriteCore(string version, byte[] bytes)
    {
        var directory = Path.Combine(Bundle, "game-mods", version);
        Directory.CreateDirectory(directory);
        File.WriteAllBytes(Path.Combine(directory, "theladscore.jar"), bytes);
    }

    public static byte[] Core(string version, (string Name, byte[] Bytes)[]? nested = null) =>
        Jar("theladscore", "1.2.0", new() { ["minecraft"] = version }, nested: nested, name: "The Lads Core");

    public void WriteReceipt(params (string Id, byte[] Bytes)[] owned)
    {
        Directory.CreateDirectory(Cache);
        File.WriteAllText(Receipt, JsonSerializer.Serialize(owned.ToDictionary(o => o.Id, o => Sha(o.Bytes))));
    }

    public Dictionary<string, string> ReadReceipt() => JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(Receipt))!;

    public void Choose(params (string Id, bool Enabled)[] choices) =>
        File.WriteAllText(State, JsonSerializer.Serialize(new { schema = 1, mods = choices.ToDictionary(c => c.Id, c => new { enabled = c.Enabled }) }));

    public HttpClient Client() => new(new Handler(this));

    public async Task Install(string version = "26.2", Action<string>? status = null)
    {
        using var client = Client();
        await ClientModInstaller.InstallAsync(Bundle, Game, version, message => { Messages.Add(message); status?.Invoke(message); },
            httpClient: client);
    }

    public int Downloads => Requests.Count(r => r.StartsWith("https://cdn.modrinth.com/", StringComparison.Ordinal));

    private sealed class Handler(ModSandbox sandbox) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            token.ThrowIfCancellationRequested();
            var url = request.RequestUri!.AbsoluteUri;
            lock (sandbox.Requests) sandbox.Requests.Add(url);
            if (sandbox.downloads.TryGetValue(url, out var bytes))
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(bytes) });
            var json = url.StartsWith("https://api.modrinth.com/v2/version_file/", StringComparison.Ordinal) ? sandbox.VersionFile?.Invoke(url) : null;
            return Task.FromResult(json == null ? new HttpResponseMessage(HttpStatusCode.ServiceUnavailable)
                : new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(json) });
        }
    }

    public void Dispose()
    {
        if (Directory.Exists(Root)) Directory.Delete(Root, true);
    }
}
