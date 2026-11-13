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
        Assert.Equal("-Dlads.modsync.host=" + Host, ModSyncService.JvmArgument(relaunch!.Host));
    }

    [Fact] public void RelaunchBuildsAModpackWithTheProfilesModsSettingsAndTheHostsJars()
    {
        byte[] good = { 1, 2, 3 };
        Directory.CreateDirectory(Synced);
        File.WriteAllBytes(Path.Combine(Synced, "host.jar"), good);
        File.WriteAllBytes(Path.Combine(Mods, "mine.jar"), new byte[] { 9 });
        File.WriteAllBytes(Path.Combine(Mods, "off.jar.disabled"), new byte[] { 8 });
        File.WriteAllText(Path.Combine(game, "options.txt"), "fov:0.5");
        Directory.CreateDirectory(Path.Combine(game, "config", "sodium"));
        File.WriteAllText(Path.Combine(game, "config", "sodium", "options.json"), "{}");
        File.WriteAllText(Path.Combine(game, ModSyncService.RelaunchFile), JsonSerializer.Serialize(new
        {
            host = Host, world = "Survival\n", owner = "HostPlayer",
            mods = new[] { new { file = "host.jar", size = 3, sha512 = Sha512(good) } }
        }));
        var relaunch = ModSyncService.TakeRelaunch(game, log.Add)!;
        Assert.Equal("Survival - HostPlayer", ModSyncService.PackName(relaunch));

        string data = Path.Combine(game, "data");
        var pack = ModSyncService.BuildInstance(data, relaunch, game, "26.3", "0.19.5", log.Add);
        Assert.Equal(("Survival - HostPlayer", "fabric", "0.19.5", true, Host), (pack.Name, pack.Loader, pack.LoaderVersion, pack.LadsCore == true, pack.ModSyncHost));
        Assert.Equal(new[] { "host.jar", "mine.jar" }, Modpacks.Mods(pack).Select(m => m.FileName));
        Assert.Equal("fov:0.5", File.ReadAllText(Path.Combine(pack.GameDirectory, "options.txt")));
        Assert.True(File.Exists(Path.Combine(pack.GameDirectory, "config", "sodium", "options.json")));

        // The same world again: the same modpack, its own settings kept, its mods made again.
        File.WriteAllText(Path.Combine(pack.GameDirectory, "options.txt"), "fov:1.0");
        File.Delete(Path.Combine(Mods, "mine.jar"));
        var again = ModSyncService.BuildInstance(data, relaunch, game, "26.3", "0.19.5", log.Add);
        Assert.Equal(pack.Id, again.Id);
        Assert.Single(Modpacks.List(data));
        Assert.Equal("fov:1.0", File.ReadAllText(Path.Combine(again.GameDirectory, "options.txt")));
        Assert.Equal(new[] { "host.jar" }, Modpacks.Mods(again).Select(m => m.FileName));
    }

    private ModSyncService.Relaunch HostFolder(string world, string owner, params (string Name, byte[] Bytes)[] jars)
    {
        if (Directory.Exists(Synced)) Directory.Delete(Synced, true);
        Directory.CreateDirectory(Synced);
        foreach (var (name, bytes) in jars) File.WriteAllBytes(Path.Combine(Synced, name), bytes);
        return new ModSyncService.Relaunch(Host, Synced, world, owner);
    }

    [Fact] public void SameWorldReusesTheModpackAndUpdatesItsModsInPlace()
    {
        string data = Path.Combine(game, "data");
        File.WriteAllBytes(Path.Combine(Mods, "mine.jar"), new byte[] { 9 });
        var first = ModSyncService.BuildInstance(data, HostFolder("Survival", "Alice", ("a.jar", new byte[] { 1 }), ("b.jar", new byte[] { 2 }), ("c.jar", new byte[] { 3 })),
            game, "26.3", "0.19.5", log.Add);
        string packMods = Path.Combine(first.GameDirectory, "mods");
        File.WriteAllBytes(Path.Combine(packMods, "added-by-player.jar"), new byte[] { 42 });       // the player's own addition to the pack
        File.Move(Path.Combine(packMods, "b.jar"), Path.Combine(packMods, "b.jar.disabled"));      // switched off by the player
        File.WriteAllText(Path.Combine(first.GameDirectory, "options.txt"), "mine");
        var stamp = File.GetLastWriteTimeUtc(Path.Combine(packMods, "a.jar"));

        // The host dropped c, changed b, added d, and the owner renamed (same host, same world).
        var again = ModSyncService.BuildInstance(data, HostFolder("Survival", "Alice2", ("a.jar", new byte[] { 1 }), ("b.jar", new byte[] { 22, 22 }), ("d.jar", new byte[] { 4 })),
            game, "26.3", "0.19.5", log.Add);

        Assert.Equal(first.Id, again.Id);
        Assert.Single(Modpacks.List(data));
        Assert.Equal(new[] { "a.jar", "added-by-player.jar", "b.jar", "d.jar", "mine.jar" }, Modpacks.Mods(again).Select(m => m.FileName));
        Assert.False(File.Exists(Path.Combine(packMods, "c.jar")));
        Assert.True(File.Exists(Path.Combine(packMods, "b.jar.disabled")));            // still off, with the host's new build
        Assert.False(File.Exists(Path.Combine(packMods, "b.jar")));
        Assert.Equal(new byte[] { 22, 22 }, File.ReadAllBytes(Path.Combine(packMods, "b.jar.disabled")));
        Assert.Equal(stamp, File.GetLastWriteTimeUtc(Path.Combine(packMods, "a.jar")));  // unchanged files are not rewritten
        Assert.Equal("mine", File.ReadAllText(Path.Combine(again.GameDirectory, "options.txt")));
        Assert.Contains(log, l => l.Contains("updated in place: 1 added, 1 updated, 1 removed"));
    }

    [Fact] public void AModTheHostDroppedLeavesThePackEvenWhenThePackAsksItself()
    {
        string data = Path.Combine(game, "data");
        var first = ModSyncService.BuildInstance(data, HostFolder("Survival", "Alice", ("a.jar", new byte[] { 1 }), ("c.jar", new byte[] { 3 })), game, "26.3", "0.19.5", log.Add);
        File.WriteAllBytes(Path.Combine(first.GameDirectory, "mods", "mine.jar"), new byte[] { 9 });
        // The pack's own game found c gone from the host: the restart names the host's whole list and no new jar.
        var drop = HostFolder("Survival", "Alice") with { HostFiles = new[] { "a.jar" } };
        var again = ModSyncService.BuildInstance(data, drop, first.GameDirectory, "26.3", "0.19.5", log.Add);
        Assert.Equal(new[] { "a.jar", "mine.jar" }, Modpacks.Mods(again).Select(m => m.FileName));
    }

    [Fact] public void APackFromBeforeTheOriginRecordStillDropsWhatTheHostDropped()
    {
        string data = Path.Combine(game, "data");
        var first = ModSyncService.BuildInstance(data, HostFolder("Survival", "Alice", ("a.jar", new byte[] { 1 }), ("c.jar", new byte[] { 3 })), game, "26.3", "0.19.5", log.Add);
        File.Delete(Path.Combine(first.GameDirectory, ModSyncService.SyncFolder, HostFolder("Survival", "Alice").Host + ".jars.json"));
        var drop = HostFolder("Survival", "Alice") with { HostFiles = new[] { "a.jar" } };
        var again = ModSyncService.BuildInstance(data, drop, first.GameDirectory, "26.3", "0.19.5", log.Add);
        Assert.Equal(new[] { "a.jar" }, Modpacks.Mods(again).Select(m => m.FileName));
    }

    [Fact] public void ADifferentWorldOfTheSameHostMakesANewModpack()
    {
        string data = Path.Combine(game, "data");
        var one = ModSyncService.BuildInstance(data, HostFolder("Survival", "Alice", ("a.jar", new byte[] { 1 })), game, "26.3", "0.19.5", log.Add);
        var two = ModSyncService.BuildInstance(data, HostFolder("Creative", "Alice", ("a.jar", new byte[] { 1 })), game, "26.3", "0.19.5", log.Add);
        var oneAgain = ModSyncService.BuildInstance(data, HostFolder("survival", "Alice", ("a.jar", new byte[] { 1 })), game, "26.3", "0.19.5", log.Add);
        Assert.NotEqual(one.Id, two.Id);
        Assert.Equal(one.Id, oneAgain.Id);
        Assert.Equal(2, Modpacks.List(data).Count);
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

    private ModpackInstance Pack(string loader = "fabric", bool? core = true, string id = "my-pack") =>
        new() { Id = id, Name = id, McVersion = "1.20.1", Loader = loader, LadsCore = core, Directory = Path.Combine(game, "inst-" + id) };

    [Fact] public void OnlyOwnFabricModpacksWithTheCoreAreSynced()
    {
        Assert.True(ModSyncService.Applies(Pack()));
        Assert.False(ModSyncService.Applies(Pack("forge")));
        Assert.False(ModSyncService.Applies(Pack("neoforge")));
        Assert.False(ModSyncService.Applies(Pack(core: false)));
        Assert.False(ModSyncService.Applies(Pack(core: null)));
        Assert.False(ModSyncService.Applies(new ModpackInstance { Id = "x", Loader = "fabric", LadsCore = true, ExternalGameDirectory = game }));
        Assert.False(ModSyncService.Applies(Pack(id: ExperiencesService.AllExperiences.First().Id)));
    }

    [Fact] public async Task ModpackLaunchWritesTheListAndDropsAStaleRestartRequest()
    {
        var pack = Pack();
        string mods = Path.Combine(pack.GameDirectory, "mods");
        Directory.CreateDirectory(mods);
        using (var zip = System.IO.Compression.ZipFile.Open(Path.Combine(mods, "own.jar"), System.IO.Compression.ZipArchiveMode.Create))
        using (var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open()))
            writer.Write("{\"schemaVersion\":1,\"id\":\"ownmod\",\"version\":\"1.0\",\"name\":\"Own\"}");
        File.WriteAllText(Path.Combine(pack.GameDirectory, ModSyncService.RelaunchFile), "{}");

        await ModSyncService.PrepareLaunchAsync(pack, new ModInventoryService(), game, null, log.Add);

        Assert.False(File.Exists(Path.Combine(pack.GameDirectory, ModSyncService.RelaunchFile)));
        using var json = JsonDocument.Parse(File.ReadAllText(Path.Combine(pack.GameDirectory, ".lads-mod-cache", ModSyncService.ListFile)));
        Assert.Equal("own.jar", Assert.Single(json.RootElement.GetProperty("mods").EnumerateArray()).GetProperty("file").GetString());

        // Forge is skipped: no list, but a stale request is still dropped.
        var forge = Pack("forge", id: "forge-pack");
        Directory.CreateDirectory(forge.GameDirectory);
        File.WriteAllText(Path.Combine(forge.GameDirectory, ModSyncService.RelaunchFile), "{}");
        await ModSyncService.PrepareLaunchAsync(forge, new ModInventoryService(), game, null, log.Add);
        Assert.False(File.Exists(Path.Combine(forge.GameDirectory, ModSyncService.RelaunchFile)));
        Assert.False(File.Exists(Path.Combine(forge.GameDirectory, ".lads-mod-cache", ModSyncService.ListFile)));
    }
    [Fact] public void RequiredLoaderIsTheHighestVersionTheHostsJarsAskFor()
    {
        string dir = Path.Combine(Path.GetTempPath(), "lads-reqloader-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(dir);
        try
        {
            void Jar(string name, string json)
            {
                using var zip = System.IO.Compression.ZipFile.Open(Path.Combine(dir, name), System.IO.Compression.ZipArchiveMode.Create);
                using var w = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open());
                w.Write(json);
            }
            Assert.Null(ModSyncService.RequiredLoader(dir));
            Jar("a.jar", "{\"depends\":{\"fabricloader\":\">=0.19.2\"}}");
            Jar("b.jar", "{\"depends\":{\"fabricloader\":[\">=0.20.0\"]}}");
            Jar("c.jar", "{\"depends\":{\"fabricloader\":\"*\"}}");
            Assert.Equal("0.20.0", ModSyncService.RequiredLoader(dir));
        }
        finally { Directory.Delete(dir, true); }
    }
}
