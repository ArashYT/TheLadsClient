using System.IO.Compression;
using System.Text;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ModDependencyInstallerTests : IDisposable
{
    private readonly string _mods = Path.Combine(Path.GetTempPath(), "lads_deps_" + Guid.NewGuid().ToString("N"));

    public ModDependencyInstallerTests() => Directory.CreateDirectory(_mods);
    public void Dispose() { try { Directory.Delete(_mods, true); } catch { } }

    private void Jar(string file, string id, string depends = "", string provides = "")
    {
        using var zip = ZipFile.Open(Path.Combine(_mods, file), ZipArchiveMode.Create);
        using var w = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open(), new UTF8Encoding(false));
        w.Write("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"1.0\",\"name\":\"" + id + "\""
            + (depends != "" ? ",\"depends\":{" + depends + "}" : "") + (provides != "" ? ",\"provides\":[" + provides + "]" : "") + "}");
    }

    [Fact]
    public void MissingIds_names_only_required_ids_no_jar_provides()
    {
        Jar("a.jar", "moda", "\"minecraft\":\"*\",\"fabricloader\":\"*\",\"cloth-config\":\"*\",\"libb\":\"*\",\"fabric-resource-loader-v0\":\"*\"");
        Jar("b.jar", "modb", provides: "\"libb\"");
        Assert.Equal(new[] { "cloth-config", "fabric-resource-loader-v0" }, ModDependencyInstaller.MissingIds(_mods));
    }

    [Fact]
    public void MissingIds_counts_fabric_modules_as_provided_by_fabric_api_and_ignores_disabled_jars()
    {
        Jar("a.jar", "moda", "\"fabric-resource-loader-v0\":\"*\",\"fabric\":\"*\"");
        Jar("fabric-api.jar", "fabric-api");
        Jar("off.jar.disabled", "modc", "\"somelib\":\"*\"");
        Assert.Empty(ModDependencyInstaller.MissingIds(_mods));
    }

    [Fact]
    public async Task EnsureAsync_leaves_forge_alone_and_installs_a_missing_library_from_modrinth()
    {
        Jar("a.jar", "moda", "\"cloth-config\":\"*\"");
        using var http = new HttpClient();
        Assert.Empty(await ModDependencyInstaller.EnsureAsync(http, _mods, "1.20.1", "forge", null, default));
        var installed = await ModDependencyInstaller.EnsureAsync(http, _mods, "1.20.1", "fabric", null, default, (_, _) => Task.FromResult(true));
        Assert.Single(installed);
        Assert.True(File.Exists(Path.Combine(_mods, installed[0])));
        Assert.DoesNotContain("cloth-config", ModDependencyInstaller.MissingIds(_mods));
    }

    private sealed class Modrinth : HttpMessageHandler
    {
        public readonly List<string> Requests = new();
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            Requests.Add(request.RequestUri!.AbsoluteUri);
            string json = request.RequestUri.AbsolutePath.EndsWith("/version")
                ? "[{\"version_type\":\"release\",\"files\":[{\"primary\":true,\"filename\":\"lib.jar\",\"url\":\"https://cdn.modrinth.com/lib.jar\",\"hashes\":{}}]}]"
                : "";
            return Task.FromResult(new HttpResponseMessage { Content = new ByteArrayContent(json == "" ? new byte[] { 1 } : Encoding.UTF8.GetBytes(json)) });
        }
    }

    // A mod id is not a Modrinth name: whoever owns the slug the id maps to must not get code into the pack without the player's yes.
    [Fact]
    public async Task EnsureAsync_does_not_install_a_guessed_slug_unless_the_player_confirms()
    {
        Jar("a.jar", "moda", "\"some_lib\":\"*\"");
        var modrinth = new Modrinth();
        using var http = new HttpClient(modrinth);
        Assert.Empty(await ModDependencyInstaller.EnsureAsync(http, _mods, "1.20.1", "fabric", null, default));
        Assert.Empty(await ModDependencyInstaller.EnsureAsync(http, _mods, "1.20.1", "fabric", null, default, (_, _) => Task.FromResult(false)));
        Assert.Empty(modrinth.Requests);
        Assert.False(File.Exists(Path.Combine(_mods, "lib.jar")));

        string? asked = null;
        var installed = await ModDependencyInstaller.EnsureAsync(http, _mods, "1.20.1", "fabric", null, default, (id, slug) => { asked = id + "/" + slug; return Task.FromResult(true); });
        Assert.Equal("some_lib/some-lib", asked);
        Assert.Equal(new[] { "lib.jar" }, installed);
    }

    [Fact]
    public async Task EnsureAsync_still_installs_fabric_api_without_asking()
    {
        Jar("a.jar", "moda", "\"fabric-resource-loader-v0\":\"*\"");
        var modrinth = new Modrinth();
        using var http = new HttpClient(modrinth);
        Assert.Equal(new[] { "lib.jar" }, await ModDependencyInstaller.EnsureAsync(http, _mods, "1.20.1", "fabric", null, default));
        Assert.Contains(modrinth.Requests, r => r.Contains("/project/fabric-api/version"));
    }
}
