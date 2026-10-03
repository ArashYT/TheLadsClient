using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class MrpackTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "lads-mrpack-" + Guid.NewGuid().ToString("N"));

    public MrpackTests() => Directory.CreateDirectory(_root);
    public void Dispose() { try { Directory.Delete(_root, true); } catch (IOException) { } }

    [Fact]
    public void ParsesIndexLoaderAndClientEnvironment()
    {
        var index = Mrpack.ParseIndex(Json(new
        {
            formatVersion = 1, game = "minecraft", versionId = "6.4.0", name = "Pack",
            dependencies = new Dictionary<string, string> { ["minecraft"] = "1.21.1", ["fabric-loader"] = "0.16.5" },
            files = new object[]
            {
                new { path = "mods/a.jar", hashes = new { sha1 = "aa", sha512 = "bb" }, downloads = new[] { "https://cdn.modrinth.com/a.jar" }, fileSize = 3 },
                new { path = "mods/server.jar", hashes = new { sha1 = "cc" }, env = new { client = "unsupported", server = "required" }, downloads = new[] { "https://x/s.jar" } }
            }
        }));
        Assert.Equal(("Pack", "6.4.0", "1.21.1", "fabric", "0.16.5"), (index.Name, index.VersionId, index.MinecraftVersion, index.Loader, index.LoaderVersion));
        Assert.Equal(2, index.Files.Count);
        Assert.False(index.Files[0].ClientUnsupported);
        Assert.True(index.Files[1].ClientUnsupported);
        Assert.Equal(("aa", "bb", 3L), (index.Files[0].Sha1, index.Files[0].Sha512, index.Files[0].Size!.Value));
    }

    [Theory]
    [InlineData("forge", "47.2.0", "forge")]
    [InlineData("neoforge", "21.1.77", "neoforge")]
    [InlineData("quilt-loader", "0.26.0", "quilt")]
    public void ReadsEachLoader(string key, string version, string loader)
    {
        var index = Mrpack.ParseIndex(Json(new { formatVersion = 1, game = "minecraft", dependencies = new Dictionary<string, string> { ["minecraft"] = "1.20.1", [key] = version } }));
        Assert.Equal((loader, version), (index.Loader, index.LoaderVersion));
    }

    [Fact]
    public void RefusesTwoLoadersAndOtherGames()
    {
        Assert.Throws<InvalidDataException>(() => Mrpack.ParseIndex(Json(new { formatVersion = 1, game = "minecraft",
            dependencies = new Dictionary<string, string> { ["minecraft"] = "1.20.1", ["forge"] = "1", ["fabric-loader"] = "2" } })));
        Assert.Throws<InvalidDataException>(() => Mrpack.ParseIndex(Json(new { formatVersion = 1, game = "terraria", dependencies = new { minecraft = "1.20.1" } })));
    }

    [Theory]
    [InlineData("../evil.jar")]
    [InlineData("mods/../../evil.jar")]
    [InlineData("mods\\..\\..\\evil.jar")]
    [InlineData("/etc/passwd")]
    [InlineData("\\Windows\\evil.dll")]
    [InlineData("C:\\Windows\\evil.dll")]
    [InlineData("C:evil.dll")]
    [InlineData("\\\\server\\share\\evil.jar")]
    [InlineData("mods/a.jar:stream")]
    [InlineData("mods/.. /.. /evil.jar")]
    [InlineData("")]
    [InlineData(".")]
    public void TraversalGuardRefusesPathsOutsideTheGameFolder(string path) =>
        Assert.Throws<InvalidDataException>(() => Mrpack.SafePath(_root, path));

    [Fact]
    public void TraversalGuardAcceptsNestedRelativePaths() =>
        Assert.Equal(Path.Combine(_root, "config", "sodium", "options.json"), Mrpack.SafePath(_root, "config/sodium/options.json"));

    [Fact]
    public void HashVerificationNeedsEveryGivenHashToMatch()
    {
        var file = Path.Combine(_root, "a.bin");
        File.WriteAllText(file, "abc");
        string sha1 = Sha1("abc"), sha512 = Sha512("abc");
        Assert.True(Mrpack.HashMatches(file, sha1, sha512));
        Assert.True(Mrpack.HashMatches(file, sha1.ToUpperInvariant(), null));
        Assert.True(Mrpack.HashMatches(file, null, sha512));
        Assert.False(Mrpack.HashMatches(file, sha1, Sha512("abd")));
        Assert.False(Mrpack.HashMatches(file, Sha1("abd"), sha512));
        Assert.False(Mrpack.HashMatches(file, null, null));
    }

    [Fact]
    public async Task InstallsVerifiedFilesAndOverridesThenUpdateKeepsUserFiles()
    {
        var served = new Dictionary<string, string> { ["https://cdn.modrinth.com/a1.jar"] = "mod a v1", ["https://cdn.modrinth.com/b.jar"] = "mod b",
            ["https://cdn.modrinth.com/a2.jar"] = "mod a v2", ["https://cdn.modrinth.com/c.jar"] = "mod c" };
        using var http = new HttpClient(new Handler(served));
        var instance = Path.Combine(_root, "instance");
        var game = Path.Combine(instance, "minecraft");

        var v1 = Pack("v1", new[] { ("mods/a.jar", "a1.jar", "mod a v1"), ("mods/b.jar", "b.jar", "mod b") },
            new() { ["overrides/config/x.txt"] = "pack x v1", ["overrides/options.txt"] = "pack options", ["client-overrides/config/x.txt"] = "client x v1" },
            serverOnly: ("mods/server.jar", "server.jar"));
        await Mrpack.InstallAsync(v1, instance, http, null, default);
        Assert.Equal("mod a v1", File.ReadAllText(Path.Combine(game, "mods", "a.jar")));
        Assert.Equal("client x v1", File.ReadAllText(Path.Combine(game, "config", "x.txt"))); // client-overrides after overrides
        Assert.False(File.Exists(Path.Combine(game, "mods", "server.jar")));

        // The user changes options, switches mod b off, adds a world and a mod of their own.
        File.WriteAllText(Path.Combine(game, "options.txt"), "my options");
        File.Move(Path.Combine(game, "mods", "b.jar"), Path.Combine(game, "mods", "b.jar.disabled"));
        Directory.CreateDirectory(Path.Combine(game, "saves", "World"));
        File.WriteAllText(Path.Combine(game, "saves", "World", "level.dat"), "world");
        File.WriteAllText(Path.Combine(game, "mods", "mine.jar"), "my mod");

        var v2 = Pack("v2", new[] { ("mods/a.jar", "a2.jar", "mod a v2"), ("mods/b.jar", "b.jar", "mod b"), ("mods/c.jar", "c.jar", "mod c") },
            new() { ["overrides/config/x.txt"] = "pack x v2", ["overrides/options.txt"] = "pack options v2" });
        await Mrpack.InstallAsync(v2, instance, http, null, default);
        Assert.Equal("mod a v2", File.ReadAllText(Path.Combine(game, "mods", "a.jar")));
        Assert.Equal("mod c", File.ReadAllText(Path.Combine(game, "mods", "c.jar")));
        Assert.False(File.Exists(Path.Combine(game, "mods", "b.jar")));
        Assert.True(File.Exists(Path.Combine(game, "mods", "b.jar.disabled")));
        Assert.Equal("pack x v2", File.ReadAllText(Path.Combine(game, "config", "x.txt")));
        Assert.Equal("my options", File.ReadAllText(Path.Combine(game, "options.txt")));
        Assert.Equal("world", File.ReadAllText(Path.Combine(game, "saves", "World", "level.dat")));
        Assert.Equal("my mod", File.ReadAllText(Path.Combine(game, "mods", "mine.jar")));

        // Removed from the pack: deleted when unchanged.
        var v3 = Pack("v3", new[] { ("mods/a.jar", "a2.jar", "mod a v2") }, new());
        await Mrpack.InstallAsync(v3, instance, http, null, default);
        Assert.False(File.Exists(Path.Combine(game, "mods", "c.jar")));
        Assert.False(File.Exists(Path.Combine(game, "mods", "b.jar.disabled")));
        Assert.False(File.Exists(Path.Combine(game, "config", "x.txt")));
        Assert.Equal("my options", File.ReadAllText(Path.Combine(game, "options.txt")));
    }

    [Fact]
    public async Task UpdateNeverOverwritesAFileThePreviousVersionDidNotInstall()
    {
        using var http = new HttpClient(new Handler(new() { ["https://cdn.modrinth.com/a.jar"] = "mod a", ["https://cdn.modrinth.com/mine.jar"] = "pack mine" }));
        var instance = Path.Combine(_root, "instance");
        string Game(string relative) => Path.Combine(instance, "minecraft", relative);
        await Mrpack.InstallAsync(Pack("v1", new[] { ("mods/a.jar", "a.jar", "mod a") }, new()), instance, http, null, default);

        // Minecraft writes options.txt, a mod makes its config on first launch, the user adds a mod of their own.
        File.WriteAllText(Game("options.txt"), "my options");
        Directory.CreateDirectory(Game("config"));
        File.WriteAllText(Game("config/mod.json"), "generated");
        File.WriteAllText(Game("mods/mine.jar"), "my mod");

        var v2 = Pack("v2", new[] { ("mods/a.jar", "a.jar", "mod a"), ("mods/mine.jar", "mine.jar", "pack mine") }, new()
        {
            ["overrides/options.txt"] = "pack options", ["overrides/config/mod.json"] = "pack config",
            ["overrides/config/new.txt"] = "pack new", ["client-overrides/config/new.txt"] = "client new"
        });
        await Mrpack.InstallAsync(v2, instance, http, null, default);
        Assert.Equal(("my options", "generated", "my mod"), (File.ReadAllText(Game("options.txt")), File.ReadAllText(Game("config/mod.json")), File.ReadAllText(Game("mods/mine.jar"))));
        Assert.Equal("client new", File.ReadAllText(Game("config/new.txt"))); // new to the pack: installed, client-overrides last

        // Still the user's when the pack drops them again.
        await Mrpack.InstallAsync(Pack("v3", Array.Empty<(string, string, string)>(), new()), instance, http, null, default);
        Assert.Equal(("my options", "generated", "my mod"), (File.ReadAllText(Game("options.txt")), File.ReadAllText(Game("config/mod.json")), File.ReadAllText(Game("mods/mine.jar"))));
        Assert.False(File.Exists(Game("config/new.txt")));
        Assert.False(File.Exists(Game("mods/a.jar")));
    }

    [Fact]
    public async Task AFailedUpdateRecordsWhatItWroteSoTheNextUpdateReplacesAndRemovesIt()
    {
        using var http = new HttpClient(new Handler(new()
        {
            ["https://cdn.modrinth.com/a1.jar"] = "mod a v1", ["https://cdn.modrinth.com/s5.jar"] = "sodium 0.5", ["https://cdn.modrinth.com/a2.jar"] = "mod a v2",
            ["https://cdn.modrinth.com/s6.jar"] = "sodium 0.6", ["https://cdn.modrinth.com/a3.jar"] = "mod a v3"
        }));
        var instance = Path.Combine(_root, "instance");
        var mods = Path.Combine(instance, "minecraft", "mods");
        await Mrpack.InstallAsync(Pack("v1", new[] { ("mods/a.jar", "a1.jar", "mod a v1"), ("mods/sodium-0.5.jar", "s5.jar", "sodium 0.5") }, new()), instance, http, null, default);

        // v2's jars arrive, then copying its settings fails (a folder stands where a file goes).
        Directory.CreateDirectory(Path.Combine(instance, "minecraft", "blocked.txt"));
        var v2 = Pack("v2", new[] { ("mods/a.jar", "a2.jar", "mod a v2"), ("mods/sodium-0.6.jar", "s6.jar", "sodium 0.6") }, new() { ["overrides/blocked.txt"] = "x" });
        await Assert.ThrowsAnyAsync<Exception>(() => Mrpack.InstallAsync(v2, instance, http, null, default));
        Assert.Equal(new[] { "a.jar", "sodium-0.5.jar", "sodium-0.6.jar" }, Directory.GetFiles(mods).Select(Path.GetFileName).Order());

        await Mrpack.InstallAsync(Pack("v3", new[] { ("mods/a.jar", "a3.jar", "mod a v3") }, new()), instance, http, null, default);
        Assert.Equal(new[] { "a.jar" }, Directory.GetFiles(mods).Select(Path.GetFileName));
        Assert.Equal("mod a v3", File.ReadAllText(Path.Combine(mods, "a.jar")));
    }

    [Fact]
    public async Task RefusesADownloadThatDoesNotMatchItsHash()
    {
        using var http = new HttpClient(new Handler(new() { ["https://cdn.modrinth.com/a.jar"] = "tampered" }));
        var pack = Pack("v1", new[] { ("mods/a.jar", "a.jar", "original") }, new());
        await Assert.ThrowsAsync<InvalidDataException>(() => Mrpack.InstallAsync(pack, Path.Combine(_root, "i"), http, null, default));
        Assert.False(File.Exists(Path.Combine(_root, "i", "minecraft", "mods", "a.jar")));
    }

    [Fact]
    public async Task RefusesTraversalInOverridesBeforeWritingAnything()
    {
        using var http = new HttpClient(new Handler(new()));
        var pack = Pack("v1", Array.Empty<(string, string, string)>(), new() { ["overrides/ok.txt"] = "ok", ["overrides/../../escape.txt"] = "bad" });
        await Assert.ThrowsAsync<InvalidDataException>(() => Mrpack.InstallAsync(pack, Path.Combine(_root, "i"), http, null, default));
        Assert.False(File.Exists(Path.Combine(_root, "i", "minecraft", "ok.txt")));
        Assert.False(File.Exists(Path.Combine(_root, "escape.txt")));
    }

    private string Pack(string version, (string Path, string Url, string Content)[] files, Dictionary<string, string> overrides,
        (string Path, string Url)? serverOnly = null)
    {
        var list = files.Select(f => (object)new { path = f.Path, hashes = new { sha1 = Sha1(f.Content), sha512 = Sha512(f.Content) },
            downloads = new[] { "https://cdn.modrinth.com/" + f.Url }, fileSize = Encoding.UTF8.GetByteCount(f.Content) }).ToList();
        if (serverOnly is { } s) list.Add(new { path = s.Path, hashes = new { sha1 = Sha1("x") }, env = new { client = "unsupported", server = "required" },
            downloads = new[] { "https://cdn.modrinth.com/" + s.Url } });
        var file = Path.Combine(_root, $"pack-{version}.mrpack");
        using var zip = ZipFile.Open(file, ZipArchiveMode.Create);
        using (var w = new StreamWriter(zip.CreateEntry("modrinth.index.json").Open()))
            w.Write(JsonSerializer.Serialize(new { formatVersion = 1, game = "minecraft", versionId = version, name = "Pack",
                dependencies = new Dictionary<string, string> { ["minecraft"] = "1.21.1", ["fabric-loader"] = "0.16.5" }, files = list }));
        foreach (var (name, content) in overrides)
            using (var w = new StreamWriter(zip.CreateEntry(name).Open())) w.Write(content);
        return file;
    }

    private static MemoryStream Json(object value) => new(JsonSerializer.SerializeToUtf8Bytes(value));
    private static string Sha1(string s) => Convert.ToHexString(SHA1.HashData(Encoding.UTF8.GetBytes(s))).ToLowerInvariant();
    private static string Sha512(string s) => Convert.ToHexString(SHA512.HashData(Encoding.UTF8.GetBytes(s))).ToLowerInvariant();

    private sealed class Handler(Dictionary<string, string> served) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(served.TryGetValue(request.RequestUri!.ToString(), out var body)
                ? new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(body) }
                : new HttpResponseMessage(HttpStatusCode.NotFound));
    }
}
