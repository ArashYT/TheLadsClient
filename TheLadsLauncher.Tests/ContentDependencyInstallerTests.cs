using System.Net;
using System.Security.Cryptography;
using System.Text;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class ContentDependencyInstallerTests : IDisposable
{
    private readonly string _mods = Path.Combine(Path.GetTempPath(), "LadsClientTests", Guid.NewGuid().ToString("N"), "mods");
    private readonly List<string> _requests = new();
    private readonly Dictionary<string, Func<HttpRequestMessage, HttpResponseMessage>> _routes = new();
    private readonly List<string> _status = new();

    public ContentDependencyInstallerTests() => Directory.CreateDirectory(_mods);
    public void Dispose() { try { Directory.Delete(Path.GetDirectoryName(_mods)!, true); } catch (IOException) { } }

    private sealed class Handler(ContentDependencyInstallerTests test) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            var key = request.RequestUri!.GetLeftPart(UriPartial.Path);
            test._requests.Add(key);
            return Task.FromResult(test._routes.TryGetValue(key, out var route) ? route(request) : new HttpResponseMessage(HttpStatusCode.NotFound));
        }
    }

    private ContentCatalog Catalog(string? key = null) => new(new HttpClient(new Handler(this)), null, null, key);
    private static HttpResponseMessage Json(string body) => new(HttpStatusCode.OK) { Content = new StringContent(body, Encoding.UTF8, "application/json") };
    private static string Sha1(byte[] b) => Convert.ToHexString(SHA1.HashData(b)).ToLowerInvariant();
    private static string Sha512(byte[] b) => Convert.ToHexString(SHA512.HashData(b)).ToLowerInvariant();

    // A Modrinth release of a project whose file is `name` with `bytes`, listing the `required` projects and one optional one.
    private static string Version(string project, string name, byte[] bytes, params string[] required)
    {
        var deps = required.Select(r => "{\"project_id\":\"" + r + "\",\"version_id\":null,\"dependency_type\":\"required\"}")
            .Append("{\"project_id\":\"optional-" + project + "\",\"dependency_type\":\"optional\"}");
        return "{\"id\":\"v-" + project + "\",\"project_id\":\"" + project + "\",\"version_number\":\"1\",\"game_versions\":[\"26.3\"],"
            + "\"files\":[{\"primary\":true,\"filename\":\"" + name + "\",\"url\":\"https://cdn.modrinth.com/" + name + "\",\"size\":" + bytes.Length
            + ",\"hashes\":{\"sha1\":\"" + Sha1(bytes) + "\",\"sha512\":\"" + Sha512(bytes) + "\"}}],\"dependencies\":[" + string.Join(",", deps) + "]}";
    }

    private void Project(string project, string name, byte[] bytes, params string[] required)
    {
        _routes[$"https://api.modrinth.com/v2/project/{project}/version"] = _ => Json("[" + Version(project, name, bytes, required) + "]");
        _routes[$"https://cdn.modrinth.com/{name}"] = _ => new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(bytes) };
    }

    private static ContentFile Root(string project, params string[] required) =>
        new(project, "v-" + project, "1", project + ".jar", "https://cdn.modrinth.com/" + project + ".jar", null, 1)
        { Dependencies = required.Select(r => new ContentDependency("Modrinth", r, null)).ToList() };

    [Fact]
    public async Task RequiredDependenciesAreInstalledRecursivelyOnceEachAndNotWhenAlreadyThere()
    {
        byte[] a = { 1, 1 }, c = { 3, 3 }, b = { 2, 2 };
        Project("A", "a.jar", a, "C", "B");   // A needs C and B; B is already installed
        Project("C", "c.jar", c);
        Project("B", "b.jar", b);
        File.WriteAllBytes(Path.Combine(_mods, "b-old-name.jar"), b);
        _routes["https://api.modrinth.com/v2/version_files"] = _ => Json("{\"" + Sha1(b) + "\":" + Version("B", "b.jar", b) + "}");

        // The mod just installed (project R) needs A and C: C is reached twice (also through A) but installed once.
        var installed = await ContentDependencyInstaller.EnsureAsync(Catalog(), _mods, "26.3", "fabric", _status.Add, default, "Modrinth",
            new[] { Root("R", "A", "C") });

        Assert.Equal(new[] { "a.jar", "c.jar" }, installed.OrderBy(n => n));
        Assert.Equal(new[] { "a.jar", "b-old-name.jar", "c.jar" }, Directory.EnumerateFiles(_mods).Select(Path.GetFileName).OrderBy(n => n));
        Assert.Single(_requests, r => r.EndsWith("/project/C/version"));
        Assert.DoesNotContain(_requests, r => r.EndsWith("/project/B/version"));          // already present: never looked up
        Assert.DoesNotContain(_requests, r => r.Contains("optional-"));                     // optional ones are not installed
    }

    [Fact]
    public async Task AWholeFolderGetsTheDependenciesOfEveryJarTheSitesKnow()
    {
        byte[] root = { 7, 7, 7 }, dep = { 8, 8 };
        File.WriteAllBytes(Path.Combine(_mods, "root.jar"), root);
        File.WriteAllBytes(Path.Combine(_mods, "unknown.jar"), new byte[] { 5 });         // a jar no site knows costs nothing
        _routes["https://api.modrinth.com/v2/version_files"] = _ => Json("{\"" + Sha1(root) + "\":" + Version("R", "root.jar", root, "D") + "}");
        Project("D", "dep.jar", dep);

        var installed = await ContentDependencyInstaller.EnsureAsync(Catalog(), _mods, "26.3", "fabric", _status.Add, default);

        Assert.Equal(new[] { "dep.jar" }, installed);
        Assert.Equal(dep, File.ReadAllBytes(Path.Combine(_mods, "dep.jar")));
        // Run again: everything is there, nothing is downloaded a second time.
        _requests.Clear();
        Assert.Empty(await ContentDependencyInstaller.EnsureAsync(Catalog(), _mods, "26.3", "fabric", _status.Add, default));
        Assert.DoesNotContain(_requests, r => r.EndsWith("/dep.jar"));
    }

    [Fact]
    public async Task ADependencyWithoutACompatibleFileIsReportedAndTheRestStillInstalls()
    {
        byte[] ok = { 4, 4 };
        _routes["https://api.modrinth.com/v2/project/GONE/version"] = _ => Json("[]");
        Project("OK", "ok.jar", ok);

        var installed = await ContentDependencyInstaller.EnsureAsync(Catalog(), _mods, "26.3", "fabric", _status.Add, default, "Modrinth",
            new[] { Root("R", "GONE", "OK") });

        Assert.Equal(new[] { "ok.jar" }, installed);
        Assert.Contains(_status, s => s.Contains("GONE"));
    }

    [Fact]
    public async Task ADependencyWhoseModIdIsAlreadyInstalledUnderAnotherVersionIsNotInstalledTwice()
    {
        // The dependency's jar is a real Fabric mod "lib" in version 2; the folder already holds "lib" in version 1 from a site-less jar.
        byte[] Fabric(string version)
        {
            using var stream = new MemoryStream();
            using (var zip = new System.IO.Compression.ZipArchive(stream, System.IO.Compression.ZipArchiveMode.Create, true))
            using (var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open()))
                writer.Write($$"""{"schemaVersion":1,"id":"lib","version":"{{version}}"}""");
            return stream.ToArray();
        }
        File.WriteAllBytes(Path.Combine(_mods, "lib-1.jar"), Fabric("1.0"));
        byte[] two = Fabric("2.0");
        Project("LIB", "lib-2.jar", two);

        var installed = await ContentDependencyInstaller.EnsureAsync(Catalog(), _mods, "26.3", "fabric", _status.Add, default, "Modrinth",
            new[] { Root("R", "LIB") });

        Assert.Empty(installed);
        Assert.Equal(new[] { "lib-1.jar" }, Directory.EnumerateFiles(_mods).Select(Path.GetFileName));
    }

    [Fact]
    public async Task OnlyCurseForgeRelationTypeThreeCountsAsRequired()
    {
        _routes["https://api.curseforge.com/v1/mods/10/files"] = _ => Json("""
            {"data":[{"id":100,"modId":10,"displayName":"x","fileName":"x.jar","downloadUrl":"https://edge.forgecdn.net/x.jar","fileLength":1,
              "hashes":[{"algo":1,"value":"abc"}],"gameVersions":["26.3"],
              "dependencies":[{"modId":20,"relationType":3},{"modId":21,"relationType":2},{"modId":22,"relationType":1}]}]}
            """);
        var file = await Catalog("key").LatestFileAsync(new ModSearchItem { Provider = "CurseForge", Id = "10" }, ContentKind.Mod, "26.3", "fabric");

        var dependency = Assert.Single(file!.Dependencies);
        Assert.Equal(new ContentDependency("CurseForge", "20", null), dependency);
    }

    [Fact]
    public async Task ModrinthVersionIsReadWithItsRequiredDependenciesOnly()
    {
        byte[] x = { 1 };
        _routes["https://api.modrinth.com/v2/project/X/version"] = _ => Json("[" + Version("X", "x.jar", x, "Y") + "]");
        var file = await Catalog().LatestFileAsync(new ModSearchItem { Provider = "Modrinth", Id = "X" }, ContentKind.Mod, "26.3", "fabric");

        Assert.Equal(new[] { new ContentDependency("Modrinth", "Y", null) }, file!.Dependencies);
    }
}
