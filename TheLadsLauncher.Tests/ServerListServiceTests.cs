using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ServerListServiceTests
{
    /// <summary>A list as Minecraft writes it, with fields this launcher never sets: icons, a hidden direct-connect entry, extras.</summary>
    private static byte[] Original()
    {
        var list = ServerListFile.CreateEmpty();
        list.Add(new NbtCompound
        {
            ["icon"] = new NbtString("iVBORw0KGgo="), ["ip"] = new NbtString("mc.hypixel.net"), ["name"] = new NbtString("§6Hypixel ✦"),
            ["acceptTextures"] = new NbtNumber(NbtTagType.Byte, 1), ["hidden"] = new NbtNumber(NbtTagType.Byte, 0)
        });
        list.Add(new NbtCompound { ["ip"] = new NbtString("10.0.0.5:25566"), ["name"] = new NbtString("Direct"), ["hidden"] = new NbtNumber(NbtTagType.Byte, 1) });
        list.Root["lads-extra"] = new NbtNumber(NbtTagType.Long, -42);
        return list.ToBytes();
    }

    [Fact]
    public async Task AddingThenRemovingLeavesTheFileByteForByteAsItWas()
    {
        using var dir = new TestDirectory();
        var target = new ServerListTarget("Test", Path.Combine(dir.Path, "servers.dat"), Path.Combine(dir.Path, ".lads-servers.lock"));
        var original = Original();
        await File.WriteAllBytesAsync(target.File, original);

        Assert.True(await ServerListService.AddAsync(target, "CubeCraft", " play.cubecraft.net "));
        var entries = ServerListFile.Read(target.File).Entries;
        Assert.Equal(new[] { "mc.hypixel.net", "10.0.0.5:25566", "play.cubecraft.net" }, entries.Select(e => e.Ip));
        Assert.Equal("CubeCraft", entries[2].Name);
        Assert.Equal(new[] { "§6Hypixel ✦", "CubeCraft" }, ServerListService.Read(target).Select(e => e.Name)); // the hidden one is not listed
        Assert.False(await ServerListService.AddAsync(target, "Again", "PLAY.CubeCraft.net"));

        Assert.True(await ServerListService.RemoveAsync(target, "play.cubecraft.net"));
        Assert.Equal(original, await File.ReadAllBytesAsync(target.File));
        Assert.False(await ServerListService.RemoveAsync(target, "10.0.0.5:25566")); // hidden entries are not the user's list
        Assert.Equal(original, await File.ReadAllBytesAsync(target.File));
    }

    [Fact]
    public async Task AnUnreadableListIsNeverOverwritten()
    {
        using var dir = new TestDirectory();
        var target = new ServerListTarget("Test", Path.Combine(dir.Path, "servers.dat"), null);
        var broken = Original()[..40];
        await File.WriteAllBytesAsync(target.File, broken);
        await Assert.ThrowsAsync<InvalidDataException>(() => ServerListService.AddAsync(target, "CubeCraft", "play.cubecraft.net"));
        Assert.Throws<InvalidDataException>(() => ServerListService.Read(target));
        Assert.Equal(broken, await File.ReadAllBytesAsync(target.File));
    }

    [Fact]
    public async Task AMissingListStartsEmptyAndOnlyAnOldCopyIsTheList()
    {
        using var dir = new TestDirectory();
        var target = new ServerListTarget("Test", Path.Combine(dir.Path, "minecraft", "servers.dat"), null);
        Assert.Empty(ServerListService.Read(target));
        Assert.True(await ServerListService.AddAsync(target, "Hypixel", "mc.hypixel.net"));
        Assert.Single(ServerListService.Read(target));

        // A game closed between renaming servers.dat to servers.dat_old and moving the new file in.
        File.Move(target.File, target.File + "_old");
        Assert.True(await ServerListService.AddAsync(target, "CubeCraft", "play.cubecraft.net"));
        Assert.Equal(new[] { "Hypixel", "CubeCraft" }, ServerListService.Read(target).Select(e => e.Name));
    }

    [Fact]
    public void TargetsAreTheSharedListThenEachModpackInstance()
    {
        using var dir = new TestDirectory();
        var shared = new SharedContentService(Path.Combine(dir.Path, "global"));
        var data = Path.Combine(dir.Path, "launcher");
        Directory.CreateDirectory(Path.Combine(data, "instances", "b-pack"));
        File.WriteAllText(Path.Combine(data, "instances", "b-pack", "instance.json"), """{"id":"b-pack","name":"Better MC","mcVersion":"26.3","loader":"fabric"}""");
        Directory.CreateDirectory(Path.Combine(data, "instances", "a-broken"));
        File.WriteAllText(Path.Combine(data, "instances", "a-broken", "instance.json"), "{");
        Directory.CreateDirectory(Path.Combine(data, "instances", "not-an-instance"));

        var targets = ServerListService.Targets(shared, data);
        // the same instances the Modpacks tab lists: a broken instance.json is skipped there and here
        Assert.Equal(new[] { shared.ServersFile, Path.Combine(data, "instances", "b-pack", "minecraft", "servers.dat") }, targets.Select(t => t.File));
        Assert.Equal(shared.ServersLockFile, targets[0].LockFile);
        Assert.Equal(new[] { "Modpack: Better MC 26.3" }, targets.Skip(1).Select(t => t.Label));
    }

    [Fact]
    public void StatusParsesBothApis()
    {
        var mcstatus = ServerStatusService.Parse("""
            {"online":true,"host":"play.cubecraft.net","players":{"online":1460,"max":45000},
             "motd":{"raw":"§bCubeCraft","clean":"  CubeCraft Games  \n  EggWars!  "},"icon":"data:image/png;base64,iVBORw0KGgo="}
            """);
        Assert.Equal((true, 1460, 45000, "CubeCraft Games\nEggWars!", "§bCubeCraft"), (mcstatus.Online, mcstatus.Players, mcstatus.MaxPlayers, mcstatus.Motd, mcstatus.MotdRaw));
        Assert.Equal(new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }, mcstatus.Icon);

        var mcsrvstat = ServerStatusService.Parse("""{"online":true,"players":{"online":33932,"max":200000},"motd":{"clean":["  Hypixel Network  ","  SKYBLOCK &amp; SAFARI "]}}""");
        Assert.Equal((true, 33932, "Hypixel Network\nSKYBLOCK & SAFARI", (byte[]?)null), (mcsrvstat.Online, mcsrvstat.Players, mcsrvstat.Motd, mcsrvstat.Icon));

        var offline = ServerStatusService.Parse("""{"online":false,"host":"gone.example","players":null,"motd":null,"icon":null}""");
        Assert.Equal(new ServerStatus(false, 0, 0, "", null), offline);
    }

    [Fact]
    public async Task EnsureServer_EmptyFile_CreatesServerListWithEntry()
    {
        using var dir = new TestDirectory();

        // 1. Zero-byte file on disk
        var zeroBytePath = Path.Combine(dir.Path, "zero_byte.dat");
        await File.WriteAllBytesAsync(zeroBytePath, Array.Empty<byte>());
        Assert.True(ServerListService.EnsureServer(zeroBytePath, "Zero SMP", "nikzerosmp.my.pebble.host"));
        var zeroList = ServerListFile.Read(zeroBytePath);
        Assert.Single(zeroList.Entries);
        Assert.Equal("Zero SMP", zeroList.Entries[0].Name);
        Assert.Equal("nikzerosmp.my.pebble.host", zeroList.Entries[0].Ip);

        // 2. Missing file on disk
        var missingPath = Path.Combine(dir.Path, "nested", "servers.dat");
        Assert.True(ServerListService.EnsureServer(missingPath, "Zero SMP", "nikzerosmp.my.pebble.host"));
        var missingList = ServerListFile.Read(missingPath);
        Assert.Single(missingList.Entries);
        Assert.Equal("Zero SMP", missingList.Entries[0].Name);
        Assert.Equal("nikzerosmp.my.pebble.host", missingList.Entries[0].Ip);

        // 3. Valid NBT with empty servers list
        var emptyNbtPath = Path.Combine(dir.Path, "empty_nbt.dat");
        await File.WriteAllBytesAsync(emptyNbtPath, ServerListFile.CreateEmpty().ToBytes());
        Assert.True(ServerListService.EnsureServer(emptyNbtPath, "Zero SMP", "nikzerosmp.my.pebble.host"));
        var emptyList = ServerListFile.Read(emptyNbtPath);
        Assert.Single(emptyList.Entries);
        Assert.Equal("Zero SMP", emptyList.Entries[0].Name);
        Assert.Equal("nikzerosmp.my.pebble.host", emptyList.Entries[0].Ip);
    }

    [Fact]
    public async Task EnsureServer_ExistingListPreserved_NeverWipesUserServers()
    {
        using var dir = new TestDirectory();
        var path = Path.Combine(dir.Path, "servers.dat");

        // Existing servers.dat with user servers and extras
        var initial = ServerListFile.CreateEmpty();
        initial.Add(new NbtCompound
        {
            ["name"] = new NbtString("§6Hypixel ✦"),
            ["ip"] = new NbtString("mc.hypixel.net"),
            ["icon"] = new NbtString("iVBORw0KGgo="),
            ["acceptTextures"] = new NbtNumber(NbtTagType.Byte, 1)
        });
        initial.Add(new NbtCompound
        {
            ["name"] = new NbtString("Mineplex"),
            ["ip"] = new NbtString("us.mineplex.com")
        });
        initial.Root["lads-extra"] = new NbtNumber(NbtTagType.Long, 42);
        await File.WriteAllBytesAsync(path, initial.ToBytes());

        // Ensure Zero SMP is added
        Assert.True(ServerListService.EnsureServer(path, "Zero SMP", "nikzerosmp.my.pebble.host"));

        var result = ServerListFile.Read(path);
        Assert.Equal(3, result.Entries.Count);

        // Existing servers are preserved in original order with custom tags
        Assert.Equal("§6Hypixel ✦", result.Entries[0].Name);
        Assert.Equal("mc.hypixel.net", result.Entries[0].Ip);
        Assert.Equal("iVBORw0KGgo=", ((NbtString)result.Entries[0].Raw["icon"]!).Value);
        Assert.Equal(1, ((NbtNumber)result.Entries[0].Raw["acceptTextures"]!).RawValue);

        Assert.Equal("Mineplex", result.Entries[1].Name);
        Assert.Equal("us.mineplex.com", result.Entries[1].Ip);

        // Zero SMP entry added
        Assert.Equal("Zero SMP", result.Entries[2].Name);
        Assert.Equal("nikzerosmp.my.pebble.host", result.Entries[2].Ip);

        // Extra root metadata preserved
        Assert.Equal(42, ((NbtNumber)result.Root["lads-extra"]!).RawValue);

        // Idempotent: running again makes no changes
        Assert.False(ServerListService.EnsureServer(path, "Zero SMP", "nikzerosmp.my.pebble.host"));
        var repeatResult = ServerListFile.Read(path);
        Assert.Equal(3, repeatResult.Entries.Count);
    }

    [Fact]
    public async Task EnsureServer_DuplicateUpdated_UpdatesNameOrIpWithoutDuplication()
    {
        using var dir = new TestDirectory();
        var path = Path.Combine(dir.Path, "servers.dat");

        // Case A: Matching IP with outdated/different display name
        var listA = ServerListFile.CreateEmpty();
        listA.Add(new NbtCompound
        {
            ["name"] = new NbtString("Old Zero Server"),
            ["ip"] = new NbtString("nikzerosmp.my.pebble.host"),
            ["icon"] = new NbtString("custom_icon_data")
        });
        await File.WriteAllBytesAsync(path, listA.ToBytes());

        Assert.True(ServerListService.EnsureServer(path, "Zero SMP", "nikzerosmp.my.pebble.host"));
        var resultA = ServerListFile.Read(path);
        Assert.Single(resultA.Entries);
        Assert.Equal("Zero SMP", resultA.Entries[0].Name);
        Assert.Equal("nikzerosmp.my.pebble.host", resultA.Entries[0].Ip);
        Assert.Equal("custom_icon_data", ((NbtString)resultA.Entries[0].Raw["icon"]!).Value);

        // Case B: Matching name with outdated/different IP
        var listB = ServerListFile.CreateEmpty();
        listB.Add(new NbtCompound
        {
            ["name"] = new NbtString("Zero SMP"),
            ["ip"] = new NbtString("old.server.pebble.host")
        });
        await File.WriteAllBytesAsync(path, listB.ToBytes());

        Assert.True(ServerListService.EnsureServer(path, "Zero SMP", "nikzerosmp.my.pebble.host"));
        var resultB = ServerListFile.Read(path);
        Assert.Single(resultB.Entries);
        Assert.Equal("Zero SMP", resultB.Entries[0].Name);
        Assert.Equal("nikzerosmp.my.pebble.host", resultB.Entries[0].Ip);

        // Case C: Exact match is fully idempotent
        Assert.False(ServerListService.EnsureServer(path, "Zero SMP", "nikzerosmp.my.pebble.host"));
        var resultC = ServerListFile.Read(path);
        Assert.Single(resultC.Entries);
    }
}
