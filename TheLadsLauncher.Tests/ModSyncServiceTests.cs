using System.Security.Cryptography;
using System.Text.Json;
using TheLadsLauncher.Services;
using Xunit;

public sealed class ModSyncServiceTests : IDisposable
{
    private const string Host = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private readonly string game = Path.Combine(Path.GetTempPath(), "lads-modsync-test-" + Guid.NewGuid().ToString("N"));
    private readonly List<string> log = new();
    private string Mods => Path.Combine(game, "mods");
    private string Synced => Path.Combine(game, ModSyncService.SyncFolder, Host);
    public ModSyncServiceTests() => Directory.CreateDirectory(Mods);
    public void Dispose() { try { Directory.Delete(game, true); } catch (IOException) { } }

    private static string Sha512(byte[] bytes) => Convert.ToHexString(SHA512.HashData(bytes)).ToLowerInvariant();
    private static string Sha1(byte[] bytes) => Convert.ToHexString(SHA1.HashData(bytes)).ToLowerInvariant();

    private ModInventoryEntry Jar(string id, string name, byte[] bytes, ModOwnership ownership, bool enabled = true)
    {
        string path = Path.Combine(Mods, name);
        File.WriteAllBytes(path, bytes);
        return new(id, id, null, "1.0", name, path, ownership, enabled ? ModEntryStatus.Installed : ModEntryStatus.Disabled, enabled, enabled, null,
            false, true, null, null, null, null, Array.Empty<string>(), Array.Empty<string>(), Array.Empty<string>(), false, null, null,
            Array.Empty<ModInventoryEntry>());
    }

    [Fact] public async Task ListHoldsOnlyTheEnabledJarsThePlayerAdded()
    {
        byte[] own = { 1, 2, 3, 4, 5 };
        var inventory = new ModInventory("26.3", game, false, new[]
        {
            Jar("own", "own mod.jar", own, ModOwnership.User),
            Jar("sodium", "sodium.jar", new byte[] { 9 }, ModOwnership.Pack),
            Jar("theladscore", "theladscore.jar", new byte[] { 8 }, ModOwnership.Core),
            Jar("off", "off.jar.disabled", new byte[] { 7 }, ModOwnership.User, enabled: false),
        }, new ModInventoryCounts(0, 0, 0, 0, 0, 0));

        var mods = await ModSyncService.WriteListAsync(inventory, null, log.Add);
        var mod = Assert.Single(mods);
        Assert.Equal(("own", "own mod.jar", 5L, Sha1(own), Sha512(own), "", ""), (mod.Id, mod.File, mod.Size, mod.Sha1, mod.Sha512, mod.Source, mod.Url));

        // The mod's parser (ModSyncList) reads these exact lower-case keys.
        using var json = JsonDocument.Parse(File.ReadAllText(Path.Combine(game, ".lads-mod-cache", ModSyncService.ListFile)));
        Assert.Equal(1, json.RootElement.GetProperty("v").GetInt32());
        var first = json.RootElement.GetProperty("mods")[0];
        Assert.Equal(Sha512(own), first.GetProperty("sha512").GetString());
        Assert.Equal("own mod.jar", first.GetProperty("file").GetString());
        Assert.Equal(5, first.GetProperty("size").GetInt64());
        Assert.Equal("", first.GetProperty("source").GetString());

        // A second launch reuses the hashes it stored and writes the same list.
        Assert.Equal(mods, await ModSyncService.WriteListAsync(inventory, null, log.Add));
    }

    [Fact] public void SiteCountsOnlyTheSameFileOnTheSitesOwnCdn()
    {
        var jar = new ModSyncService.Known("aa", "bb", 7, false, "", "");
        ContentFile On(string url, string sha1 = "AA", string? sha512 = null) => new("p", "f", "v", "m.jar", url, sha1, 5, sha512);
        const string modrinth = "https://cdn.modrinth.com/data/a/versions/b/m.jar";

        Assert.Equal(("modrinth", modrinth), ModSyncService.Site(jar, On(modrinth), null));
        Assert.Equal(("modrinth", modrinth), ModSyncService.Site(jar, On(modrinth, sha512: "BB"), On("https://edge.forgecdn.net/files/1/2/m.jar")));
        Assert.Equal(("curseforge", "https://edge.forgecdn.net/files/1/2/my%20mod.jar"), ModSyncService.Site(jar, null, On("https://edge.forgecdn.net/files/1/2/my mod.jar")));
        Assert.Equal(("", ""), ModSyncService.Site(jar, On(modrinth, sha1: "cc"), null));            // the version's other file
        Assert.Equal(("", ""), ModSyncService.Site(jar, On(modrinth, sha512: "cc"), null));
        Assert.Equal(("", ""), ModSyncService.Site(jar, On("https://evil.example/m.jar"), On("https://cdn.modrinth.com/m.jar")));
        Assert.Equal(("", ""), ModSyncService.Site(jar, On("http://cdn.modrinth.com/m.jar"), null));
        Assert.Equal(("", ""), ModSyncService.Site(jar, On(modrinth + "?x=1"), null));
        Assert.Equal(("", ""), ModSyncService.Site(jar, null, null));
    }

    private void Request(string host, params (string File, long Size, string Sha512)[] mods) =>
        File.WriteAllText(Path.Combine(game, ModSyncService.RelaunchFile), JsonSerializer.Serialize(new
        {
            host,
            mods = mods.Select(m => new { file = m.File, size = m.Size, sha512 = m.Sha512 })
        }));

    [Fact] public void RelaunchLoadsOnlyJarsThatPassTheHashCheck()
    {
        byte[] good = { 1, 2, 3 }, stale = { 4 };
        Directory.CreateDirectory(Synced);
        File.WriteAllBytes(Path.Combine(Synced, "a.jar"), good);
        File.WriteAllBytes(Path.Combine(Synced, "stale.jar"), stale);
        File.WriteAllBytes(Path.Combine(Mods, "mine.jar"), stale);

        Assert.Null(ModSyncService.TakeRelaunch(game, log.Add)); // no request

        Request(Host, ("a.jar", 3, Sha512(good)));
        var relaunch = ModSyncService.TakeRelaunch(game, log.Add);
        Assert.Equal(new ModSyncService.Relaunch(Host, Synced), relaunch);
        Assert.False(File.Exists(Path.Combine(game, ModSyncService.RelaunchFile)));
        Assert.Equal(new[] { "a.jar" }, Directory.GetFiles(Synced).Select(Path.GetFileName));
        Assert.True(File.Exists(Path.Combine(Mods, "mine.jar")));
        Assert.Equal(new[] { "-Dfabric.addMods=" + Synced, "-Dlads.modsync.host=" + Host }, ModSyncService.JvmArguments(relaunch!));
    }

    [Fact] public void RelaunchIsRefusedForAnythingUnchecked()
    {
        byte[] good = { 1, 2, 3 };
        Directory.CreateDirectory(Synced);
        File.WriteAllBytes(Path.Combine(Synced, "a.jar"), good);
        File.WriteAllBytes(Path.Combine(Mods, "mine.jar"), good);

        Request(Host, ("a.jar", 3, Sha512(new byte[] { 1, 2, 4 })));                 // not the listed bytes
        Assert.Null(ModSyncService.TakeRelaunch(game, log.Add));
        Assert.Contains(log, line => line.Contains("hash check failed"));
        Request(Host, ("a.jar", 4, Sha512(good)));                                   // wrong size
        Assert.Null(ModSyncService.TakeRelaunch(game, log.Add));
        Request(Host, ("a.jar", 3, Sha512(good)), ("missing.jar", 3, Sha512(good)));
        Assert.Null(ModSyncService.TakeRelaunch(game, log.Add));
        Request(Host, (Path.Combine("..", "..", "mods", "mine.jar"), 3, Sha512(good))); // a path out of the folder
        Assert.Null(ModSyncService.TakeRelaunch(game, log.Add));
        Request(Path.Combine("..", "mods"), ("mine.jar", 3, Sha512(good)));            // the host is not a UUID
        Assert.Null(ModSyncService.TakeRelaunch(game, log.Add));
        Request(Host);                                                                // nothing to load
        Assert.Null(ModSyncService.TakeRelaunch(game, log.Add));
        File.WriteAllText(Path.Combine(game, ModSyncService.RelaunchFile), "not json");
        Assert.Null(ModSyncService.TakeRelaunch(game, log.Add));

        Assert.False(File.Exists(Path.Combine(game, ModSyncService.RelaunchFile)));  // every request is taken once
        Assert.True(File.Exists(Path.Combine(Mods, "mine.jar")));
        Assert.True(File.Exists(Path.Combine(Synced, "a.jar")));
    }
}
