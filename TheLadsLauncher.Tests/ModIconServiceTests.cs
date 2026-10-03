using System.IO.Compression;
using System.Net;
using System.Text;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>Installed Mods icons: read from the jar (Fabric, mods.toml, mcmod.info), else the Modrinth project icon by the jar's
/// SHA-512, cached on disk; a jar Modrinth does not know is not asked again.</summary>
public sealed class ModIconServiceTests : IDisposable
{
    private readonly string root = Path.Combine(Path.GetTempPath(), "lads-icons-" + Guid.NewGuid().ToString("N"));
    private static readonly byte[] Png = { 0x89, 0x50, 0x4E, 0x47, 1, 2, 3 };

    public ModIconServiceTests() => Directory.CreateDirectory(root);
    public void Dispose() => Directory.Delete(root, true);

    private string Jar(string name, params (string Entry, byte[] Bytes)[] entries)
    {
        var path = Path.Combine(root, name);
        using var zip = ZipFile.Open(path, ZipArchiveMode.Create);
        foreach (var (entry, bytes) in entries)
            using (var stream = zip.CreateEntry(entry).Open()) stream.Write(bytes);
        return path;
    }

    private static byte[] Utf8(string text) => Encoding.UTF8.GetBytes(text);

    private static ModInventoryEntry Entry(string? file, string? project = null) => new(Path.GetFileName(file) ?? "x", "X", null, null,
        Path.GetFileName(file), file, ModOwnership.User, ModEntryStatus.Installed, true, true, null, false, true, null, project, null, null,
        Array.Empty<string>(), Array.Empty<string>(), Array.Empty<string>(), false, null, null, Array.Empty<ModInventoryEntry>());

    [Fact]
    public void JarIconsComeFromEachLoadersMetadata()
    {
        Assert.Equal(Png, ModIconService.ReadJarIcon(Jar("fabric.jar", ("fabric.mod.json", Utf8("""{"id":"a","icon":"assets/a/icon.png"}""")), ("assets/a/icon.png", Png))));
        Assert.Equal(Png, ModIconService.ReadJarIcon(Jar("sizes.jar", ("fabric.mod.json", Utf8("""{"id":"a","icon":{"16":"small.png","128":"big.png"}}""")),
            ("small.png", new byte[] { 1 }), ("big.png", Png))));
        Assert.Equal(Png, ModIconService.ReadJarIcon(Jar("forge.jar", ("META-INF/mods.toml", Utf8("modLoader=\"javafml\"\n[[mods]]\nmodId=\"b\"\nlogoFile=\"logo.png\"\n")), ("logo.png", Png))));
        Assert.Equal(Png, ModIconService.ReadJarIcon(Jar("legacy.jar", ("mcmod.info", Utf8("""[{"modid":"c","logoFile":"/assets/c/logo.png",}]""")), ("assets/c/logo.png", Png))));
        Assert.Null(ModIconService.ReadJarIcon(Jar("none.jar", ("fabric.mod.json", Utf8("""{"id":"d"}""")))));
        Assert.Null(ModIconService.ReadJarIcon(Jar("missing.jar", ("fabric.mod.json", Utf8("""{"id":"e","icon":"gone.png"}""")))));
    }

    [Fact]
    public async Task ModrinthIconsAreFoundByHashCachedAndMissesRemembered()
    {
        var known = Jar("known.jar", ("fabric.mod.json", Utf8("""{"id":"known"}""")));
        var unknown = Jar("unknown.jar", ("fabric.mod.json", Utf8("""{"id":"unknown"}""")));
        var withIcon = Jar("own.jar", ("fabric.mod.json", Utf8("""{"id":"own","icon":"i.png"}""")), ("i.png", Png));
        string knownHash = Convert.ToHexString(System.Security.Cryptography.SHA512.HashData(File.ReadAllBytes(known))).ToLowerInvariant();
        var handler = new Modrinth(knownHash);
        var cache = Path.Combine(root, "cache");

        var found = new Dictionary<string, string>();
        await new ModIconService(cache, new HttpClient(handler)).LoadAsync(new[] { Entry(known), Entry(unknown), Entry(withIcon) },
            (entry, file) => { lock (found) found[entry.FileName!] = file; });

        Assert.Equal(new[] { "known.jar", "own.jar" }, found.Keys.Order());
        Assert.Equal(new byte[] { 7, 7, 7 }, File.ReadAllBytes(found["known.jar"]));
        Assert.Equal(Png, File.ReadAllBytes(found["own.jar"]));
        Assert.Equal(3, handler.Requests.Count); // version_files, projects, the icon
        Assert.Contains(handler.Requests, r => r.StartsWith("POST https://api.modrinth.com/v2/version_files"));

        // Next session: icons from the cache, the unknown jar not asked again.
        found.Clear();
        handler.Requests.Clear();
        await new ModIconService(cache, new HttpClient(handler)).LoadAsync(new[] { Entry(known), Entry(unknown) }, (entry, file) => found[entry.FileName!] = file);
        Assert.Equal(new[] { "known.jar" }, found.Keys);
        Assert.Empty(handler.Requests);
    }

    [Fact]
    public async Task OfflineLeavesPlaceholdersAndRemembersNothing()
    {
        var jar = Jar("a.jar", ("fabric.mod.json", Utf8("""{"id":"a"}""")));
        var cache = Path.Combine(root, "cache");
        var found = 0;
        await new ModIconService(cache, new HttpClient(new Modrinth(null))).LoadAsync(new[] { Entry(jar) }, (_, _) => found++);
        Assert.Equal(0, found);
        Assert.Empty(Directory.GetFiles(cache)); // no ".none": a later session asks again
    }

    private sealed class Modrinth(string? knownHash) : HttpMessageHandler
    {
        public List<string> Requests { get; } = new();

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            var url = request.RequestUri!.AbsoluteUri;
            lock (Requests) Requests.Add(request.Method + " " + url);
            if (knownHash == null) throw new HttpRequestException("offline");
            string? json = url == "https://api.modrinth.com/v2/version_files"
                ? (await request.Content!.ReadAsStringAsync(token)).Contains(knownHash) ? "{\"" + knownHash + "\":{\"project_id\":\"AABBCC\"}}" : "{}"
                : url.StartsWith("https://api.modrinth.com/v2/projects?ids=", StringComparison.Ordinal)
                    ? """[{"id":"AABBCC","icon_url":"https://cdn.modrinth.com/data/AABBCC/icon.png"}]""" : null;
            if (json != null) return new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(json) };
            return url == "https://cdn.modrinth.com/data/AABBCC/icon.png"
                ? new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(new byte[] { 7, 7, 7 }) }
                : new HttpResponseMessage(HttpStatusCode.NotFound);
        }
    }
}
