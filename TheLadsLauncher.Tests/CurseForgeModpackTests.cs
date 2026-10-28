using System;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class CurseForgeModpackTests
{
    private static void CreateTestZip(string zipPath, string manifestJson, (string Path, string Content)[] overrides)
    {
        using var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create);
        var entry = zip.CreateEntry("manifest.json");
        using (var writer = new StreamWriter(entry.Open(), Encoding.UTF8))
        {
            writer.Write(manifestJson);
        }

        foreach (var (path, content) in overrides)
        {
            var oEntry = zip.CreateEntry("overrides/" + path);
            using var oWriter = new StreamWriter(oEntry.Open(), Encoding.UTF8);
            oWriter.Write(content);
        }
    }

    [Fact]
    public void ParseManifest_ValidCurseForgeManifest_ParsesCorrectly()
    {
        string json = @"
{
    ""minecraft"": {
        ""version"": ""1.20.1"",
        ""modLoaders"": [
            {
                ""id"": ""fabric-0.15.11"",
                ""primary"": true
            }
        ]
    },
    ""manifestType"": ""minecraftModpack"",
    ""manifestVersion"": 1,
    ""name"": ""Epic Lads Adventure"",
    ""version"": ""2.1.0"",
    ""author"": ""The Lads"",
    ""files"": [
        {
            ""projectID"": 306612,
            ""fileID"": 481234,
            ""required"": true
        },
        {
            ""projectID"": 238222,
            ""fileID"": 510999,
            ""required"": false
        }
    ],
    ""overrides"": ""overrides""
}";

        using var stream = new MemoryStream(Encoding.UTF8.GetBytes(json));
        var manifest = CurseForgeModpack.ParseManifest(stream);

        Assert.Equal("Epic Lads Adventure", manifest.Name);
        Assert.Equal("2.1.0", manifest.Version);
        Assert.Equal("The Lads", manifest.Author);
        Assert.Equal("1.20.1", manifest.MinecraftVersion);
        Assert.Equal("fabric", manifest.ModLoader);
        Assert.Equal("0.15.11", manifest.ModLoaderVersion);
        Assert.Equal(2, manifest.Files.Count);
        Assert.Equal(306612, manifest.Files[0].ProjectId);
        Assert.Equal(481234, manifest.Files[0].FileId);
        Assert.True(manifest.Files[0].Required);
        Assert.False(manifest.Files[1].Required);
    }

    [Fact]
    public void ExtractOverrides_SafelyExtractsConfigurationFiles()
    {
        using var tempDir = new TestDirectory();
        string zipPath = Path.Combine(tempDir.Path, "testpack.zip");
        string instanceDir = Path.Combine(tempDir.Path, "instance");
        Directory.CreateDirectory(instanceDir);

        string manifest = @"{""minecraft"":{""version"":""1.20.1"",""modLoaders"":[{""id"":""fabric-0.15.11""}]},""files"":[]}";
        CreateTestZip(zipPath, manifest, new[]
        {
            ("config/lads.txt", "custom-config-value"),
            ("options.txt", "gamma:1.0")
        });

        CurseForgeModpack.ExtractOverrides(zipPath, instanceDir, "overrides");

        Assert.True(File.Exists(Path.Combine(instanceDir, "config", "lads.txt")));
        Assert.Equal("custom-config-value", File.ReadAllText(Path.Combine(instanceDir, "config", "lads.txt")));
        Assert.True(File.Exists(Path.Combine(instanceDir, "options.txt")));
        Assert.Equal("gamma:1.0", File.ReadAllText(Path.Combine(instanceDir, "options.txt")));
    }

    [Fact]
    public void ExtractOverrides_GuardsAgainstZipSlip_PathTraversal()
    {
        using var tempDir = new TestDirectory();
        string zipPath = Path.Combine(tempDir.Path, "malicious.zip");
        string instanceDir = Path.Combine(tempDir.Path, "instance");
        Directory.CreateDirectory(instanceDir);

        string manifest = @"{""minecraft"":{""version"":""1.20.1"",""modLoaders"":[{""id"":""fabric-0.15.11""}]},""files"":[]}";
        CreateTestZip(zipPath, manifest, new[]
        {
            ("../../escaped.txt", "exploit payload")
        });

        Assert.Throws<InvalidDataException>(() =>
            CurseForgeModpack.ExtractOverrides(zipPath, instanceDir, "overrides"));

        // File must not have escaped to tempDir root
        Assert.False(File.Exists(Path.Combine(tempDir.Path, "escaped.txt")));
    }

    // ------------------------------------------------------------------ the game folder

    private const string OneModManifest = @"{""minecraft"":{""version"":""1.20.1"",""modLoaders"":[{""id"":""fabric-0.15.11""}]},""name"":""Epic"",""version"":""1.0"",""files"":[{""projectID"":111,""fileID"":222,""required"":true}]}";

    /// <summary>A mod on the fake CurseForge API (file Project/File) and CDN. Blocked: its author allows no downloads outside CurseForge
    /// (no downloadUrl). Missing: the API does not know the file.</summary>
    private sealed record Mod(int Project, int File, string Name, string Content = "", bool Blocked = false, bool Missing = false);

    private sealed class CurseForgeApi(params Mod[] mods) : HttpMessageHandler
    {
        public List<string> Requests { get; } = new();

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            var url = request.RequestUri!.AbsoluteUri;
            lock (Requests) Requests.Add(url);
            foreach (var mod in mods)
            {
                var bytes = Encoding.UTF8.GetBytes(mod.Content);
                if (url == $"https://api.curseforge.com/v1/mods/{mod.Project}/files/{mod.File}")
                    return Task.FromResult(mod.Missing ? new HttpResponseMessage(HttpStatusCode.NotFound) : new HttpResponseMessage(HttpStatusCode.OK)
                    {
                        Content = new StringContent(JsonSerializer.Serialize(new
                        {
                            data = new
                            {
                                id = mod.File, modId = mod.Project, displayName = mod.Name, fileName = mod.Name,
                                downloadUrl = mod.Blocked ? null : "https://edge.forgecdn.net/" + mod.Name,
                                hashes = new[] { new { value = Convert.ToHexString(SHA1.HashData(bytes)).ToLowerInvariant(), algo = 1 } },
                                fileLength = bytes.Length, gameVersions = new[] { "1.20.1" }
                            }
                        }))
                    });
                if (url == "https://edge.forgecdn.net/" + mod.Name)
                    return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(bytes) });
            }
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotFound));
        }
    }

    [Fact]
    public async Task InstallCurseForgeAsync_PutsTheModsAndOverridesInTheGameFolder()
    {
        using var temp = new TestDirectory();
        string zip = Path.Combine(temp.Path, "pack.zip");
        CreateTestZip(zip, OneModManifest, new[] { ("config/x.txt", "x"), ("options.txt", "gamma:1.0") });
        using var http = new HttpClient(new CurseForgeApi(new Mod(111, 222, "a.jar", "mod a")));

        var instance = await ModpackLauncher.InstallCurseForgeAsync(temp.Path, zip, new ContentCatalog(http, null, null, "test-key"), null, null, null, default);

        string game = instance.GameDirectory;
        Assert.Equal("mod a", File.ReadAllText(Path.Combine(game, "mods", "a.jar")));
        Assert.Equal("x", File.ReadAllText(Path.Combine(game, "config", "x.txt")));
        Assert.Equal("gamma:1.0", File.ReadAllText(Path.Combine(game, "options.txt")));
        Assert.False(Directory.Exists(Path.Combine(instance.Directory, "mods")));
        Assert.False(Directory.Exists(Path.Combine(instance.Directory, "config")));
        Assert.Contains("a.jar", Modpacks.Mods(instance).Select(m => m.FileName));
    }

    private static ModpackInstance OlderImport(string dataDirectory)
    {
        var instance = Modpacks.NewInstance(dataDirectory, "Old Import");
        instance.McVersion = "1.20.1";
        instance.Loader = "fabric";
        instance.LadsCore = false; // what the older import saved: it looked at an empty game folder
        Modpacks.Save(instance);
        return instance;
    }

    [Fact]
    public void ListingInstances_MovesAnOlderImportIntoTheGameFolder_ReplacingAndDeletingNothing()
    {
        using var temp = new TestDirectory();
        var instance = OlderImport(temp.Path);
        string Root(string path) => Path.Combine(instance.Directory, path);
        string Game(string path) => Path.Combine(instance.GameDirectory, path);
        // What the older import wrote beside the game folder...
        Directory.CreateDirectory(Root("mods"));
        File.WriteAllText(Root("mods/a.jar"), "a");
        File.WriteAllText(Root("mods/b.jar"), "b");
        Directory.CreateDirectory(Root("config/sub"));
        File.WriteAllText(Root("config/x.txt"), "pack x");
        File.WriteAllText(Root("config/sub/y.txt"), "pack y");
        File.WriteAllText(Root("options.txt"), "pack options");
        // ...what the game folder has meanwhile...
        Directory.CreateDirectory(Game("mods"));
        File.WriteAllText(Game("mods/b.jar"), "own b");
        File.WriteAllText(Game("mods/theladscore.jar"), "core");
        Directory.CreateDirectory(Game("config"));
        File.WriteAllText(Game("config/x.txt"), "own x");
        File.WriteAllText(Game("options.txt"), "own options");
        // ...and what the launcher keeps beside it.
        File.WriteAllText(Root(".lads-applied-patches.json"), "[]");
        File.WriteAllText(Root("icon.png"), "icon");
        File.WriteAllText(Root("instance-analytics.json"), "{}");

        var listed = Modpacks.List(temp.Path).Single();

        Assert.Equal("a", File.ReadAllText(Game("mods/a.jar")));
        Assert.Equal("core", File.ReadAllText(Game("mods/theladscore.jar")));
        Assert.Equal("pack y", File.ReadAllText(Game("config/sub/y.txt")));
        Assert.False(File.Exists(Root("mods/a.jar")));
        Assert.False(Directory.Exists(Root("config/sub")));
        // A file the game folder already has stays, and so does its twin outside: nothing is replaced, nothing deleted.
        Assert.Equal("own b", File.ReadAllText(Game("mods/b.jar")));
        Assert.Equal("b", File.ReadAllText(Root("mods/b.jar")));
        Assert.Equal("own x", File.ReadAllText(Game("config/x.txt")));
        Assert.Equal("pack x", File.ReadAllText(Root("config/x.txt")));
        Assert.Equal("own options", File.ReadAllText(Game("options.txt")));
        Assert.Equal("pack options", File.ReadAllText(Root("options.txt")));
        foreach (var own in new[] { ".lads-applied-patches.json", "icon.png", "instance-analytics.json", "instance.json" })
            Assert.True(File.Exists(Root(own)), own);
        Assert.Equal(false, listed.LadsCore); // a leftover remains, so the Core setting is not decided again yet
        // Listing again changes nothing more.
        Modpacks.List(temp.Path);
        Assert.Equal("b", File.ReadAllText(Root("mods/b.jar")));
        Assert.Equal("own b", File.ReadAllText(Game("mods/b.jar")));
    }

    [Fact]
    public void ListingInstances_DecidesTheCoreAgainOnceAnOlderImportIsCompletelyInTheGameFolder()
    {
        using var temp = new TestDirectory();
        var instance = OlderImport(temp.Path);
        Directory.CreateDirectory(Path.Combine(instance.Directory, "mods"));
        File.WriteAllText(Path.Combine(instance.Directory, "mods", "a.jar"), "a");

        var listed = Modpacks.List(temp.Path).Single();

        Assert.Equal("a", File.ReadAllText(Path.Combine(instance.GameDirectory, "mods", "a.jar")));
        Assert.False(Directory.Exists(Path.Combine(instance.Directory, "mods")));
        Assert.Null(listed.LadsCore);
        Assert.Null(Modpacks.List(temp.Path).Single().LadsCore); // and saved
    }

    [Fact]
    public void ListingInstances_LeavesAnInstanceWithoutAMisplacedModsFolderAlone()
    {
        using var temp = new TestDirectory();
        var instance = OlderImport(temp.Path);
        File.WriteAllText(Path.Combine(instance.Directory, "notes.txt"), "mine"); // no <instance>/mods: not an older CurseForge import

        Modpacks.List(temp.Path);

        Assert.Equal("mine", File.ReadAllText(Path.Combine(instance.Directory, "notes.txt")));
        Assert.False(File.Exists(Path.Combine(instance.GameDirectory, "notes.txt")));
        Assert.Equal(false, Modpacks.List(temp.Path).Single().LadsCore);
    }
}
