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
        var installed = await ModDependencyInstaller.EnsureAsync(http, _mods, "1.20.1", "fabric", null, default);
        Assert.Single(installed);
        Assert.True(File.Exists(Path.Combine(_mods, installed[0])));
        Assert.DoesNotContain("cloth-config", ModDependencyInstaller.MissingIds(_mods));
    }
}
