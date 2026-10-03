using System.Net;
using System.Security.Cryptography;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

// Modrinth fixtures and curseforge-403.txt are recorded API answers (2026-10-03). The other CurseForge fixtures follow the
// documented response schema (docs.curseforge.com): there is no key to record real ones with.
public sealed class ContentCatalogTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "LadsClientTests", Guid.NewGuid().ToString("N"));
    private readonly List<HttpRequestMessage> _requests = new();
    private readonly Dictionary<HttpRequestMessage, string> _bodies = new(); // the catalog disposes its requests
    private readonly Dictionary<string, Func<HttpRequestMessage, HttpResponseMessage>> _routes = new();

    public ContentCatalogTests() => Directory.CreateDirectory(_root);
    public void Dispose() { try { Directory.Delete(_root, true); } catch (IOException) { } }

    private static string Fixture(string name) => File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "Fixtures", "Content", name));
    private static HttpResponseMessage Json(string body, HttpStatusCode status = HttpStatusCode.OK) => new(status) { Content = new StringContent(body) };
    private static HttpResponseMessage Bytes(byte[] body) => new(HttpStatusCode.OK) { Content = new ByteArrayContent(body) };
    private static string Sha1(byte[] bytes) => Convert.ToHexString(SHA1.HashData(bytes)).ToLowerInvariant();

    // Routes match on the URL without its query; every request is recorded.
    private ContentCatalog Catalog(string? curseForgeKey = null) =>
        new(new HttpClient(new Handler(this)), null, null, curseForgeKey);

    private sealed class Handler(ContentCatalogTests test) : HttpMessageHandler
    {
        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            test._requests.Add(request);
            if (request.Content != null) test._bodies[request] = await request.Content.ReadAsStringAsync(token);
            var key = request.RequestUri!.GetLeftPart(UriPartial.Path);
            return test._routes.TryGetValue(key, out var route) ? route(request) : new HttpResponseMessage(HttpStatusCode.NotFound);
        }
    }

    [Fact]
    public async Task CurseForgeWithoutAnyKeyExplainsWhyInsteadOfShowingNoResults()
    {
        if (ContentCatalog.BuiltInCurseForgeKey.Length > 0) return; // this build has a key built in
        var error = await Assert.ThrowsAsync<ContentSourceException>(() => Catalog("  ").SearchCurseForgeAsync(ContentKind.ResourcePack, "faithful", "1.21.1", "fabric"));
        Assert.Equal(ContentCatalog.MissingKeyMessage, error.Message);
        Assert.Empty(_requests);
    }

    [Fact]
    public async Task CurseForgeRefusedKeyIsReportedWithTheReason()
    {
        _routes["https://api.curseforge.com/v1/mods/search"] = _ => Json(Fixture("curseforge-403.txt"), HttpStatusCode.Forbidden);
        var error = await Assert.ThrowsAsync<ContentSourceException>(() => Catalog("bad-key").SearchCurseForgeAsync(ContentKind.ResourcePack, "", "1.21.1", "fabric"));
        Assert.Contains("refused the API key", error.Message);
        Assert.Equal("bad-key", _requests.Single().Headers.GetValues("x-api-key").Single());
    }

    [Theory]
    [InlineData(ContentKind.ResourcePack, "1.21.1", "classId=12", null)]
    [InlineData(ContentKind.Mod, "1.21.11", "classId=6", "modLoaderType=4")]
    [InlineData(ContentKind.Mod, "1.8.9", "classId=6", "modLoaderType=1")]
    [InlineData(ContentKind.Shader, "26.3", "classId=6552", null)]
    [InlineData(ContentKind.DataPack, "26.2", "classId=6945", null)]
    public async Task CurseForgeSearchSendsClassVersionLoaderAndPopularitySort(ContentKind kind, string version, string classId, string? loader)
    {
        _routes["https://api.curseforge.com/v1/mods/search"] = _ => Json(Fixture("curseforge-search-resourcepacks.json"));
        var hits = await Catalog("key").SearchCurseForgeAsync(kind, "lads pack", version, ContentCatalog.ModLoader(version));
        var query = _requests.Single().RequestUri!.Query;
        Assert.Contains("gameId=432", query);
        Assert.Contains(classId + "&", query);
        Assert.Contains("gameVersion=" + version, query);
        Assert.Contains("searchFilter=lads%20pack", query);
        Assert.Contains("sortField=2&sortOrder=desc", query);
        if (loader == null) Assert.DoesNotContain("modLoaderType", query); else Assert.Contains(loader, query);
        var hit = Assert.Single(hits);
        Assert.Equal(("900001", "Lads Test Pack", "LadsAuthor", 1234567L, "CurseForge"), (hit.Id, hit.Name, hit.Author, hit.DownloadCount, hit.Provider));
        Assert.Equal("https://media.forgecdn.net/avatars/thumbnails/7/7/256/256/logo.png", hit.IconUrl);
        Assert.Equal(new[] { "16x" }, hit.Categories);
    }

    [Fact]
    public async Task AnAnswerWithoutTheExpectedShapeIsAContentSourceError()
    {
        _routes["https://api.modrinth.com/v2/search"] = _ => Json("{\"error\":\"invalid_input\"}");
        var error = await Assert.ThrowsAsync<ContentSourceException>(() => Catalog().SearchModrinthAsync(ContentKind.Shader, "", "26.3", "fabric"));
        Assert.Contains("does not understand", error.Message);
    }

    [Theory]
    [InlineData("1.21.11", "fabric", "categories:iris")]
    [InlineData("1.8.9", "forge", "categories:optifine")]
    public async Task ModrinthShaderSearchFiltersByTheTargetsShaderLoader(string version, string loader, string facet)
    {
        _routes["https://api.modrinth.com/v2/search"] = _ => Json(Fixture("modrinth-search-shader.json"));
        var hits = await Catalog().SearchModrinthAsync(ContentKind.Shader, "complementary", version, loader);
        var query = Uri.UnescapeDataString(_requests.Single().RequestUri!.Query);
        Assert.Contains("\"project_type:shader\"", query);
        Assert.Contains($"\"{facet}\"", query);
        Assert.Contains($"\"versions:{version}\"", query);
        Assert.Equal(new[] { "Complementary Shaders - Reimagined", "Complementary Shaders - Unbound" }, hits.Select(h => h.Name));
        Assert.Equal("HVnmMxH1", hits[0].Id);
    }

    [Fact]
    public async Task DataPackSearchAndFileUseTheDatapackTypeAndLoader()
    {
        _routes["https://api.modrinth.com/v2/search"] = _ => Json(Fixture("modrinth-search-datapack.json"));
        _routes["https://api.modrinth.com/v2/project/OhduvhIc/version"] = _ => Json(Fixture("modrinth-versions-datapack.json"));
        var catalog = Catalog();
        var hits = await catalog.SearchModrinthAsync(ContentKind.DataPack, "", "1.21.1", "fabric");
        Assert.Contains("\"project_type:datapack\"", Uri.UnescapeDataString(_requests[0].RequestUri!.Query));
        Assert.DoesNotContain("categories:", Uri.UnescapeDataString(_requests[0].RequestUri!.Query));
        var file = await catalog.LatestFileAsync(hits[0], ContentKind.DataPack, "1.21.1", "fabric");
        // VeinMiner also ships mod jars; the datapack loader picks its data pack zip.
        Assert.Contains("loaders=[\"datapack\"]", Uri.UnescapeDataString(_requests[1].RequestUri!.Query));
        Assert.Equal(("Veinminer-1.2.4.zip", "e6ebe9ce76023bdb79e55d20a40336db93855d0b", "1.2.4"), (file!.FileName, file.Sha1, file.VersionName));
    }

    [Fact]
    public async Task InstallVerifiesSha1AndNeverReplacesAFile()
    {
        var bytes = "datapack"u8.ToArray();
        _routes["https://cdn.example/pack.zip"] = _ => Bytes(bytes);
        var folder = Path.Combine(_root, "datapacks");
        Directory.CreateDirectory(folder);
        File.WriteAllText(Path.Combine(folder, "pack.zip"), "mine");
        var file = new ContentFile("p", "v", "1", "pack.zip", "https://cdn.example/pack.zip", Sha1(bytes), bytes.Length);
        var path = await Catalog().InstallAsync(file, folder, new SharedContentService(_root));
        Assert.Equal("pack (2).zip", Path.GetFileName(path));
        Assert.Equal("mine", File.ReadAllText(Path.Combine(folder, "pack.zip")));
        await Assert.ThrowsAsync<ContentSourceException>(() => Catalog().InstallAsync(file with { Sha1 = Sha1("other"u8.ToArray()) }, folder, new SharedContentService(_root)));
        Assert.Equal(new[] { "pack (2).zip", "pack.zip" }, Directory.GetFileSystemEntries(folder).Select(Path.GetFileName).Order());
    }

    // ---- Update resource packs ----

    private const string OldName = "Faithful 32x - 1.20.1.zip", NewName = "Faithful 32x - 1.21.1.zip";
    private const string NewUrl = "https://cdn.modrinth.com/data/w0TnApzs/versions/ruZOIEZE/Faithful%2032x%20-%201.21.1.zip";

    private (ContentTarget Target, string Pack, string Modern, string Legacy) PackSetup(byte[] oldBytes)
    {
        var packs = Path.Combine(_root, "resourcepacks");
        Directory.CreateDirectory(packs);
        var pack = Path.Combine(packs, OldName);
        File.WriteAllBytes(pack, oldBytes);
        var modern = Path.Combine(_root, "options.txt");
        File.WriteAllText(modern, $"version:3955\nresourcePacks:[\"vanilla\",\"file/{OldName}\"]\nincompatibleResourcePacks:[\"file/{OldName}\"]\nlang:en_us\n");
        var legacy = Path.Combine(_root, "options-189.txt");
        File.WriteAllText(legacy, $"resourcePacks:[\"{OldName}\"]\r\nlang:en_US\r\n");
        var target = new ContentTarget("lads", "Lads", "1.21.1", "fabric", packs, Path.Combine(_root, "shaderpacks"), Path.Combine(_root, "saves"),
            new[] { modern, legacy, Path.Combine(_root, "missing", "options.txt") });
        return (target, pack, modern, legacy);
    }

    private void RouteModrinthUpdate(string installedSha1, string newSha1) =>
        _routes["https://api.modrinth.com/v2/version_files/update"] = _ => Json(Fixture("modrinth-version-files-update.json")
            .Replace("ad2020339564f4d5e441a68f757f5293a3160fbd", installedSha1).Replace("cf90d965473549d3d642b835071c6472d60bcf7b", newSha1));

    [Fact]
    public async Task UpdateReplacesThePackAndKeepsItEnabledUnderItsNewName()
    {
        byte[] oldBytes = "old pack"u8.ToArray(), newBytes = "new pack"u8.ToArray();
        var (target, pack, modern, legacy) = PackSetup(oldBytes);
        RouteModrinthUpdate(Sha1(oldBytes), Sha1(newBytes));
        _routes[NewUrl] = _ => Bytes(newBytes);
        var recycled = new List<string>();

        var (updated, failed, lines) = await PackContentService.UpdateResourcePacksAsync(Catalog(), target, new SharedContentService(_root),
            path => { recycled.Add(path); File.Delete(path); });

        Assert.Equal((1, 0), (updated, failed));
        Assert.Equal($"{OldName} → {NewName} (1.21.1-june-2026).", Assert.Single(lines));
        Assert.Equal(new[] { pack }, recycled);
        Assert.Equal(newBytes, File.ReadAllBytes(Path.Combine(target.ResourcePacks, NewName)));
        Assert.Equal(new[] { NewName }, Directory.GetFileSystemEntries(target.ResourcePacks).Select(Path.GetFileName));
        Assert.Equal($"version:3955\nresourcePacks:[\"vanilla\",\"file/{NewName}\"]\nincompatibleResourcePacks:[\"file/{NewName}\"]\nlang:en_us\n", File.ReadAllText(modern));
        Assert.Equal($"resourcePacks:[\"{NewName}\"]\r\nlang:en_US\r\n", File.ReadAllText(legacy));
        var body = _bodies[_requests.Single(r => r.Method == HttpMethod.Post)];
        Assert.Contains("\"loaders\":[\"minecraft\"]", body);
        Assert.Contains("\"game_versions\":[\"1.21.1\"]", body);
        Assert.Contains(Sha1(oldBytes), body);
    }

    [Fact]
    public async Task UpdateWithAWrongDownloadChangesNothing()
    {
        var oldBytes = "old pack"u8.ToArray();
        var (target, pack, modern, _) = PackSetup(oldBytes);
        var before = File.ReadAllText(modern);
        RouteModrinthUpdate(Sha1(oldBytes), Sha1("expected"u8.ToArray()));
        _routes[NewUrl] = _ => Bytes("tampered"u8.ToArray());

        var (updated, failed, lines) = await PackContentService.UpdateResourcePacksAsync(Catalog(), target, new SharedContentService(_root), File.Delete);

        Assert.Equal((0, 1), (updated, failed));
        Assert.Contains("does not match its SHA-1", Assert.Single(lines));
        Assert.Equal(new[] { OldName }, Directory.GetFileSystemEntries(target.ResourcePacks).Select(Path.GetFileName)); // staging folder removed too
        Assert.Equal(oldBytes, File.ReadAllBytes(pack));
        Assert.Equal(before, File.ReadAllText(modern));
    }

    [Fact]
    public async Task UpdateForOneVersionKeepsAPackAnotherVersionPlaysFromTheSharedFolder()
    {
        byte[] oldBytes = "faithful for 26.2 and 26.3"u8.ToArray(), newBytes = "faithful for 1.8.9"u8.ToArray();
        var (target, pack, modern, legacy) = PackSetup(oldBytes);
        target = target with { MinecraftVersion = "1.8.9", FolderVersions = new[] { "1.8.9", "26.2", "26.3" } };
        var before = (File.ReadAllText(modern), File.ReadAllText(legacy));
        string Release(string versions) => Fixture("modrinth-version-files-update.json").Replace("\"game_versions\":[\"1.21\",\"1.21.1\"]", $"\"game_versions\":[{versions}]")
            .Replace("ad2020339564f4d5e441a68f757f5293a3160fbd", Sha1(oldBytes)).Replace("cf90d965473549d3d642b835071c6472d60bcf7b", Sha1(newBytes));
        _routes["https://api.modrinth.com/v2/version_files/update"] = _ => Json(Release("\"1.8.9\""));
        _routes["https://api.modrinth.com/v2/version_files"] = _ => Json(Release("\"26.2\",\"26.3\""));
        _routes[NewUrl] = _ => Bytes(newBytes);

        var (updated, failed, lines) = await PackContentService.UpdateResourcePacksAsync(Catalog(), target, new SharedContentService(_root), File.Delete);

        Assert.Equal((0, 0), (updated, failed));
        Assert.Equal($"{OldName}: kept. It is made for Minecraft 26.2, which also plays from this folder, and the 1.8.9 release (1.21.1-june-2026) does not support 26.2.",
            Assert.Single(lines));
        Assert.Equal(new[] { OldName }, Directory.GetFileSystemEntries(target.ResourcePacks).Select(Path.GetFileName));
        Assert.Equal(oldBytes, File.ReadAllBytes(pack));
        Assert.Equal(before, (File.ReadAllText(modern), File.ReadAllText(legacy)));

        // A newer release that still supports every version the pack served is an update.
        _routes["https://api.modrinth.com/v2/version_files/update"] = _ => Json(Release("\"26.2\",\"26.3\""));
        (updated, failed, _) = await PackContentService.UpdateResourcePacksAsync(Catalog(), target with { MinecraftVersion = "26.3" }, new SharedContentService(_root), File.Delete);
        Assert.Equal((1, 0), (updated, failed));
        Assert.Equal(new[] { NewName }, Directory.GetFileSystemEntries(target.ResourcePacks).Select(Path.GetFileName));
    }

    [Fact]
    public async Task UpdateWhoseOldPackCannotBeRecycledFailsAndLeavesTheFolderAsItWas()
    {
        byte[] oldBytes = "old pack"u8.ToArray(), newBytes = "new pack"u8.ToArray();
        var (target, pack, modern, _) = PackSetup(oldBytes);
        var before = File.ReadAllText(modern);
        RouteModrinthUpdate(Sha1(oldBytes), Sha1(newBytes));
        _routes[NewUrl] = _ => Bytes(newBytes);

        // The user said No to deleting a pack the Recycle Bin cannot take.
        var (updated, failed, lines) = await PackContentService.UpdateResourcePacksAsync(Catalog(), target, new SharedContentService(_root),
            _ => throw new OperationCanceledException("not deleted"));

        Assert.Equal((0, 1), (updated, failed));
        Assert.Equal($"{OldName}: not deleted", Assert.Single(lines));
        Assert.Equal(new[] { OldName }, Directory.GetFileSystemEntries(target.ResourcePacks).Select(Path.GetFileName));
        Assert.Equal(oldBytes, File.ReadAllBytes(pack));
        Assert.Equal(before, File.ReadAllText(modern));
    }

    [Fact]
    public async Task UpToDateAndUnknownPacksAreLeftAlone()
    {
        if (ContentCatalog.BuiltInCurseForgeKey.Length > 0) return; // with a built-in key the unknown pack is also looked up on CurseForge
        var oldBytes = "old pack"u8.ToArray();
        var (target, _, _, _) = PackSetup(oldBytes);
        File.WriteAllText(Path.Combine(target.ResourcePacks, "Homemade.zip"), "mine");
        Directory.CreateDirectory(Path.Combine(target.ResourcePacks, "Folder pack"));
        RouteModrinthUpdate(Sha1(oldBytes), Sha1(oldBytes));

        var (updated, failed, lines) = await PackContentService.UpdateResourcePacksAsync(Catalog(), target, new SharedContentService(_root), File.Delete);

        Assert.Equal((0, 0), (updated, failed));
        Assert.Equal(new[] { $"{OldName} is up to date.", "Homemade.zip: not found on Modrinth for Minecraft 1.21.1." }, lines);
        Assert.DoesNotContain(_requests, r => r.Method == HttpMethod.Get);
    }

    [Fact]
    public async Task UpdateFallsBackToCurseForgeFingerprintsWhenAKeyIsSet()
    {
        byte[] oldBytes = "old cf pack 1.20"u8.ToArray(), newBytes = "new cf pack 1.21"u8.ToArray();
        var packs = Path.Combine(_root, "resourcepacks");
        Directory.CreateDirectory(packs);
        File.WriteAllBytes(Path.Combine(packs, "LadsTestPack-1.20.1.zip"), oldBytes);
        var options = Path.Combine(_root, "options.txt");
        File.WriteAllText(options, "resourcePacks:[\"vanilla\",\"file/LadsTestPack-1.20.1.zip\"]\n");
        var target = new ContentTarget("i", "Pack", "1.21.1", "fabric", packs, "", "", new[] { options });
        uint fingerprint = ContentCatalog.CurseForgeFingerprint(oldBytes);
        _routes["https://api.modrinth.com/v2/version_files/update"] = _ => Json("{}");
        _routes["https://api.curseforge.com/v1/fingerprints/432"] = _ => Json(Fixture("curseforge-fingerprints.json")
            .Replace("\"__FP_OLD__\"", fingerprint.ToString()).Replace("__SHA1_OLD__", Sha1(oldBytes)));
        _routes["https://api.curseforge.com/v1/mods/900001/files"] = _ => Json(Fixture("curseforge-files.json").Replace("__SHA1_NEW__", Sha1(newBytes)));
        _routes["https://edge.forgecdn.net/files/5000/2/LadsTestPack-1.21.1.zip"] = _ => Bytes(newBytes);

        var (updated, failed, lines) = await PackContentService.UpdateResourcePacksAsync(Catalog("key"), target, new SharedContentService(_root), File.Delete);

        Assert.Equal((1, 0), (updated, failed));
        Assert.Equal(new[] { "LadsTestPack-1.21.1.zip" }, Directory.GetFileSystemEntries(packs).Select(Path.GetFileName));
        Assert.Equal("resourcePacks:[\"vanilla\",\"file/LadsTestPack-1.21.1.zip\"]\n", File.ReadAllText(options));
        Assert.Contains($"{{\"fingerprints\":[{fingerprint}]}}", _bodies[_requests.Single(r => r.RequestUri!.AbsolutePath.EndsWith("/432"))]);
        Assert.Contains("gameVersion=1.21.1", _requests.Single(r => r.RequestUri!.AbsolutePath.EndsWith("/files")).RequestUri!.Query);
    }

    [Fact]
    public void CurseForgeFingerprintIsMurmur2WithoutWhitespace()
    {
        // Cross-checked against a separate MurmurHash2 (seed 1) implementation.
        Assert.Equal(1540447798u, ContentCatalog.CurseForgeFingerprint(Array.Empty<byte>()));
        Assert.Equal(1621425345u, ContentCatalog.CurseForgeFingerprint("abc"u8.ToArray()));
        Assert.Equal(3056953977u, ContentCatalog.CurseForgeFingerprint("The Lads Client resource pack\n"u8.ToArray()));
        Assert.Equal(3056953977u, ContentCatalog.CurseForgeFingerprint("The Lads Client\tresource pack\r\n "u8.ToArray()));
    }

    // ---- Targets ----

    [Fact]
    public void TargetsAreEveryLadsProfileThenEveryModpackInstance()
    {
        var paths = new PathService(Path.Combine(_root, "launcher"));
        var shared = new SharedContentService(Path.Combine(_root, "global"));
        var instances = Path.Combine(paths.BaseDirectory, "instances");
        Directory.CreateDirectory(Path.Combine(instances, "fresh"));             // no instance.json yet: skipped
        Directory.CreateDirectory(Path.Combine(instances, "skyblock"));
        File.WriteAllText(Path.Combine(instances, "skyblock", "instance.json"),
            "{\"id\":\"skyblock\",\"name\":\"Skyblock\",\"mcVersion\":\"1.20.1\",\"loader\":\"forge\",\"loaderVersion\":\"47.3.0\",\"createdUtc\":\"2026-10-01T00:00:00Z\"}");
        var profiles = new[] { new LauncherProfile { Id = "a", Name = "PvP", MinecraftVersion = "1.8.9" }, new LauncherProfile { Id = "b", Name = "Latest", MinecraftVersion = "26.3" } };

        var targets = PackContentService.LoadTargets(profiles, paths, shared);

        Assert.Equal(new[] { "Lads · PvP (1.8.9)", "Lads · Latest (26.3)", "Modpack · Skyblock (1.20.1)" }, targets.Select(t => t.Label));
        Assert.Equal(new[] { "forge", "fabric", "forge" }, targets.Select(t => t.Loader));
        Assert.Equal(new[] { "optifine", "iris", "optifine" }, targets.Select(t => ContentCatalog.ShaderLoader(t.Loader)));
        Assert.All(targets.Take(2), t => Assert.Equal((shared.ShaderPacksDirectory, shared.SavesDirectory, 3), (t.ShaderPacks, t.Saves, t.OptionsFiles.Count)));
        var game = Path.Combine(instances, "skyblock", "minecraft");
        Assert.Equal((Path.Combine(game, "shaderpacks"), Path.Combine(game, "saves"), Path.Combine(game, "resourcepacks")), (targets[2].ShaderPacks, targets[2].Saves, targets[2].ResourcePacks));
        Assert.Equal(new[] { false, true, true }, targets.Select(t => PackContentService.SupportsDataPacks(t.MinecraftVersion)));
    }

    [Fact]
    public void WorldsAndInstalledPacksSkipWhatIsNotOne()
    {
        var saves = Path.Combine(_root, "saves");
        Directory.CreateDirectory(Path.Combine(saves, "World", "datapacks", "folder pack"));
        File.WriteAllBytes(Path.Combine(saves, "World", "level.dat"), Array.Empty<byte>());
        Directory.CreateDirectory(Path.Combine(saves, "not a world"));
        var datapacks = Path.Combine(saves, "World", "datapacks");
        File.WriteAllText(Path.Combine(datapacks, "a.zip"), "");
        File.WriteAllText(Path.Combine(datapacks, "notes.txt"), "");
        Directory.CreateDirectory(Path.Combine(datapacks, ".lads-incoming-0123456789abcdef0123456789abcdef"));

        Assert.Equal(new[] { "World" }, PackContentService.Worlds(saves));
        Assert.Equal(new[] { "a.zip", "folder pack" }, PackContentService.Installed(datapacks).Select(Path.GetFileName));
        Assert.Empty(PackContentService.Worlds(Path.Combine(_root, "nothing")));
    }
}
