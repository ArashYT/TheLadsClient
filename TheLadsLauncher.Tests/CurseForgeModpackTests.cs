using System.IO;
using System.IO.Compression;
using System.Text;
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
}
