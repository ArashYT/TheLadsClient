using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>Assets another game folder already holds are linked into the one being installed, hash-checked, never downloaded twice.</summary>
public class SharedAssetsTests
{
    private static string Sha1(byte[] bytes) => Convert.ToHexString(SHA1.HashData(bytes)).ToLowerInvariant();

    private static string Put(string assets, string hash, byte[] bytes)
    {
        string file = Path.Combine(assets, "objects", hash[..2], hash);
        Directory.CreateDirectory(Path.GetDirectoryName(file)!);
        File.WriteAllBytes(file, bytes);
        return file;
    }

    private static string Index(string assets, params (string Name, string Hash, long Size)[] objects)
    {
        string file = Path.Combine(assets, "indexes", "30.json");
        Directory.CreateDirectory(Path.GetDirectoryName(file)!);
        File.WriteAllText(file, JsonSerializer.Serialize(new { objects = objects.ToDictionary(o => o.Name, o => new { hash = o.Hash, size = o.Size }) }));
        return file;
    }

    [Fact]
    public void SeedTakesOnlyFilesWithTheRightSizeAndHash()
    {
        using var dir = new TestDirectory();
        string target = Path.Combine(dir.Path, "profile", "assets"), profile = Path.Combine(dir.Path, "other", "assets"), official = Path.Combine(dir.Path, "mc", "assets");
        byte[] click = Encoding.UTF8.GetBytes("click.ogg"), lang = Encoding.UTF8.GetBytes("en_us.json"), cave = Encoding.UTF8.GetBytes("cave1.ogg"),
            own = Encoding.UTF8.GetBytes("already here"), gone = Encoding.UTF8.GetBytes("nowhere"), bad = Encoding.UTF8.GetBytes("damaged!!");
        string source = Put(profile, Sha1(click), click);
        Put(official, Sha1(lang), lang);
        Put(profile, Sha1(cave), Encoding.UTF8.GetBytes("cave1.ogX")); // right size, wrong content
        Put(official, Sha1(bad), Encoding.UTF8.GetBytes("short"));     // wrong size
        string kept = Put(target, Sha1(own), own);
        string index = Index(target, ("click", Sha1(click), click.Length), ("lang", Sha1(lang), lang.Length), ("cave", Sha1(cave), cave.Length),
            ("bad", Sha1(bad), bad.Length), ("own", Sha1(own), own.Length), ("gone", Sha1(gone), gone.Length));

        var result = SharedAssets.Seed(target, index, [profile, official], CancellationToken.None);

        Assert.Equal(new SharedAssets.Result(2, 0, 3), result); // one drive: hard links, no copies
        Assert.Equal(click, File.ReadAllBytes(Path.Combine(target, "objects", Sha1(click)[..2], Sha1(click))));
        Assert.Equal(lang, File.ReadAllBytes(Path.Combine(target, "objects", Sha1(lang)[..2], Sha1(lang))));
        Assert.False(File.Exists(Path.Combine(target, "objects", Sha1(cave)[..2], Sha1(cave))));
        Assert.False(File.Exists(Path.Combine(target, "objects", Sha1(bad)[..2], Sha1(bad))));
        Assert.Equal(own, File.ReadAllBytes(kept));
        Assert.Equal(click, File.ReadAllBytes(source)); // the other folder is only read

        File.Delete(source); // a deleted profile takes only its own name of the file
        Assert.Equal(click, File.ReadAllBytes(Path.Combine(target, "objects", Sha1(click)[..2], Sha1(click))));
        Assert.Equal(new SharedAssets.Result(0, 0, 3), SharedAssets.Seed(target, index, [profile, official], CancellationToken.None));
    }

    [Fact]
    public void SeedIgnoresAnIndexThatNamesNoRealHash()
    {
        using var dir = new TestDirectory();
        string target = Path.Combine(dir.Path, "assets"), store = Path.Combine(dir.Path, "store");
        Directory.CreateDirectory(Path.Combine(store, "objects"));
        File.WriteAllText(Path.Combine(dir.Path, "secret.txt"), "x");
        string index = Index(target, ("escape", @"..\..\secret.txt", 1), ("short", "abc", 1));

        Assert.Equal(new SharedAssets.Result(0, 0, 0), SharedAssets.Seed(target, index, [store], CancellationToken.None));
        Assert.False(Directory.Exists(Path.Combine(target, "objects")));
    }

    [Fact]
    public void TheAssetIndexIsReadFromTheInstalledVersionFiles()
    {
        using var dir = new TestDirectory();
        void Version(string id, string json)
        {
            Directory.CreateDirectory(Path.Combine(dir.Path, id));
            File.WriteAllText(Path.Combine(dir.Path, id, id + ".json"), json);
        }
        Version("26.3", """{"id":"26.3","assetIndex":{"id":"34","sha1":"f3df28b441565ca4e5deb9cda856d90df62e1433","url":"https://piston-meta.mojang.com/34.json"}}""");
        Version("fabric-loader-0.19.5-26.3", """{"id":"fabric-loader-0.19.5-26.3","inheritsFrom":"26.3"}""");
        Version("orphan", """{"id":"orphan","inheritsFrom":"1.0"}""");
        Version("loop", """{"id":"loop","inheritsFrom":"loop"}""");
        Version("broken", "{");

        var expected = new SharedAssets.Index("34", "f3df28b441565ca4e5deb9cda856d90df62e1433", "https://piston-meta.mojang.com/34.json");
        Assert.Equal(expected, SharedAssets.LocalIndex(dir.Path, "26.3"));
        Assert.Equal(expected, SharedAssets.LocalIndex(dir.Path, "fabric-loader-0.19.5-26.3"));
        foreach (var id in new[] { "orphan", "loop", "broken", "missing", "", @"...3" })
            Assert.Null(SharedAssets.LocalIndex(dir.Path, id));
    }

    [Fact]
    public void StoresAreTheFoldersThatHoldObjects()
    {
        using var dir = new TestDirectory();
        string data = Path.Combine(dir.Path, "data"), official = Path.Combine(dir.Path, ".minecraft");
        Directory.CreateDirectory(Path.Combine(ModpackLauncher.CacheRoot(data), "assets", "objects"));
        Directory.CreateDirectory(Path.Combine(data, "profiles", "26.3", "assets", "objects"));
        Directory.CreateDirectory(Path.Combine(data, "profiles", "empty"));
        Directory.CreateDirectory(Path.Combine(official, "assets", "objects"));

        Assert.Equal([Path.Combine(ModpackLauncher.CacheRoot(data), "assets"), Path.Combine(data, "profiles", "26.3", "assets"), Path.Combine(official, "assets")],
            SharedAssets.Stores(data, official));
        Assert.Empty(SharedAssets.Stores(Path.Combine(dir.Path, "nothing"), Path.Combine(dir.Path, "none")));
    }
}
