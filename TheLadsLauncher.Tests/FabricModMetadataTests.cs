using System.IO.Compression;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class FabricModMetadataTests
{
    [Fact]
    public void NestedJarsAreKeptAsChildrenIncludingPlainLibraries()
    {
        var library = ModSandbox.Jar("catconfig-mc", "0.2.1", new() { ["minecraft"] = ">=26.2" }, library: true,
            nested: new[] { ("META-INF/jars/catconfig-0.3.0.jar", ModSandbox.Jar("io_github_lgatodu47_catconfig", "0.3.0")) });
        var jar = ModSandbox.Jar("theladscore", "1.2.0", new() { ["fabric-api"] = "*" }, provides: new[] { "lads" },
            breaks: new() { ["optifabric"] = "*" }, nested: new[] {
                ("META-INF/jars/catconfig-mc-26.2-0.2.1.jar", library),
                ("META-INF/jars/java-objc-bridge-1.0.0.jar", ModSandbox.PlainJar()),
                ("META-INF/jars/server.jar", ModSandbox.Jar("servermod", depends: new() { ["serverlib"] = "*" }, environment: "server"))
            });

        var info = FabricModMetadata.ReadJar(new MemoryStream(jar))!;

        Assert.Equal("theladscore", info.Id);
        Assert.Equal(new[] { "Ann", "Bob" }, info.Authors);
        Assert.Equal("MIT", info.License);
        Assert.Equal("*", info.Breaks["optifabric"]);
        Assert.Equal(new[] { "catconfig-mc", "java-objc-bridge-1.0.0.jar", "servermod" }, info.Children.Select(c => c.Id));
        var catconfig = info.Children[0];
        Assert.True(catconfig.IsLibraryBadge);
        Assert.Equal("META-INF/jars/catconfig-mc-26.2-0.2.1.jar", catconfig.NestedPath);
        Assert.Equal("io_github_lgatodu47_catconfig", Assert.Single(catconfig.Children).Id);
        var plain = info.Children[1];
        Assert.False(plain.HasMetadata);
        Assert.Null(plain.Name);
        Assert.True(info.Children[2].IsServerOnly);
        Assert.Empty(info.Children[2].Depends);
        // Client modules are what Fabric loads on the client: no plain libraries, no server-only modules.
        Assert.Equal(new[] { "theladscore", "catconfig-mc", "io_github_lgatodu47_catconfig" },
            FabricModMetadata.ClientModules(info).Select(m => m.Id));
    }

    [Fact]
    public void JarWithoutMetadataIsNotAModAndInvalidMetadataIsAnError()
    {
        Assert.Null(FabricModMetadata.ReadJar(new MemoryStream(ModSandbox.PlainJar())));
        Assert.Throws<InvalidDataException>(() => FabricModMetadata.ReadJar(new MemoryStream(ModSandbox.Jar("Not Valid"))));
        Assert.Throws<InvalidDataException>(() => FabricModMetadata.ReadJar(new MemoryStream(new byte[] { 1, 2, 3 })));
    }

    [Fact]
    public void IconIsReadOnlyWhenRequested()
    {
        using var bytes = new MemoryStream();
        using (var zip = new ZipArchive(bytes, ZipArchiveMode.Create, true))
        {
            using (var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open()))
                writer.Write("{\"schemaVersion\":1,\"id\":\"iconmod\",\"version\":\"1\",\"icon\":{\"16\":\"small.png\",\"128\":\"assets/big.png\"}}");
            using (var small = zip.CreateEntry("small.png").Open()) small.Write(new byte[] { 1 });
            using (var big = zip.CreateEntry("assets/big.png").Open()) big.Write(new byte[] { 2, 2 });
        }
        Assert.Null(FabricModMetadata.ReadJar(new MemoryStream(bytes.ToArray()))!.Icon);
        Assert.Equal(new byte[] { 2, 2 }, FabricModMetadata.ReadJar(new MemoryStream(bytes.ToArray()), includeIcon: true)!.Icon);
    }

    [Theory]
    [InlineData("*", "26.3", true)]
    [InlineData("26.2", "26.2", true)]
    [InlineData("26.2", "26.3", false)]
    [InlineData("=1.21.11", "1.21.11", true)]
    [InlineData(">=0.19.5", "0.19.10", true)]
    [InlineData(">=0.19.5", "0.19.4", false)]
    [InlineData("<2.0.0", "1.9.9", true)]
    [InlineData(">1.0", "1.0.0", false)]
    [InlineData("<=1.0", "1.0.0", true)]
    [InlineData("~26.2-", "26.2", true)]
    [InlineData("~26.2-", "26.2.1", true)]
    [InlineData("~26.2-", "26.3", false)]
    [InlineData("^1.2.3", "1.9.0", true)]
    [InlineData("^1.2.3", "2.0.0", false)]
    [InlineData("1.21.x", "1.21.11", true)]
    [InlineData("1.21.X", "1.22", false)]
    [InlineData(">=1.0 <2.0", "1.5", true)]
    [InlineData(">=1.0 <2.0", "2.0", false)]
    [InlineData("[\"1.21.1\",\"1.21.11\"]", "1.21.11", true)]
    [InlineData("[\"1.21.1\",\"1.21.11\"]", "26.3", false)]
    [InlineData(">=1.0.0", "1.0.0-beta.2", false)]
    [InlineData(">=1.0.0-beta.1", "1.0.0-beta.2", true)]
    [InlineData("22.0.3+fabric", "22.0.3+build.7", true)]
    [InlineData(">=26.2", "26w14a", true)]
    public void VersionPredicates(string predicate, string version, bool expected) =>
        Assert.Equal(expected, FabricVersionPredicate.Matches(predicate, version));
}
