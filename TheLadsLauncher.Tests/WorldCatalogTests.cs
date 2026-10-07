using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class WorldCatalogTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "lads-worlds-" + Guid.NewGuid().ToString("N"));
    public WorldCatalogTests() => Directory.CreateDirectory(_root);
    public void Dispose()
    {
        string link = Path.Combine(_root, "linked-profile", "saves");
        if (SafeFileOps.IsLink(link)) SafeFileOps.RemoveLink(link);
        Directory.Delete(_root, true);
    }

    private string Save(string source, string folder, string name, string version)
    {
        string game = Path.Combine(_root, source);
        string directory = Path.Combine(game, "saves", folder);
        Directory.CreateDirectory(directory);
        var data = new NbtCompound();
        data["LevelName"] = new NbtString(name);
        data["Version"] = new NbtCompound { ["Name"] = new NbtString(version) };
        data["LastPlayed"] = new NbtNumber(NbtTagType.Long, 1700000000000);
        var root = new NbtCompound { ["Data"] = data };
        File.WriteAllBytes(Path.Combine(directory, "level.dat"), Nbt.Write(root));
        return game;
    }

    [Fact]
    public void ReadsNamesVersionsAndCategoriesWithoutChangingSaveBytes()
    {
        string global = Save("global", "folder", "Creative city", "26.3");
        string custom = Save("custom", "folder", "Survival island", "1.21.11");
        string metadata = Path.Combine(global, "saves", "folder", "level.dat");
        byte[] before = File.ReadAllBytes(metadata);
        var catalog = WorldCatalogService.Scan(new[] { new WorldSource("Global", "Global .minecraft", global), new WorldSource("My instance", "Custom Instances", custom) });
        Assert.Empty(catalog.Warnings);
        Assert.Equal(2, catalog.Worlds.Count);
        var result = Assert.Single(WorldCatalogService.Filter(catalog.Worlds, "CREATIVE", "Global .minecraft", "26.3"));
        Assert.Equal("Creative city", result.Name);
        Assert.Empty(WorldCatalogService.Filter(catalog.Worlds, "Creative", "Custom Instances", "All versions"));
        Assert.Equal(before, File.ReadAllBytes(metadata));
        Assert.Single(WorldCatalogService.Filter(catalog.Worlds, "my instance", "All locations", "1.21.11"));
    }

    [Fact]
    public void DuplicateRootsDoNotDuplicateWorldsAndCorruptMetadataRemainsVisible()
    {
        string game = Save("global", "broken", "Name", "26.3");
        File.WriteAllText(Path.Combine(game, "saves", "broken", "level.dat"), "broken data");
        var catalog = WorldCatalogService.Scan(new[] { new WorldSource("Global", "Global .minecraft", game), new WorldSource("Profile", "Specific Version", game) });
        var world = Assert.Single(catalog.Worlds);
        Assert.Equal("broken", world.Name);
        Assert.NotNull(world.Warning);
        Assert.Equal("Global .minecraft", world.Category);
    }

    [Fact]
    public void RegisteringCustomInstancesPersistsWithoutDuplicatingOrMovingSaves()
    {
        string custom = Save("instance", "world", "World", "26.2");
        var service = new WorldCatalogService(_root);
        service.AddCustomSource(custom);
        service.AddCustomSource(custom);
        Assert.Single(new WorldCatalogService(_root).LoadCustomSources());
        Assert.True(File.Exists(Path.Combine(custom, "saves", "world", "level.dat")));
        Assert.Throws<IOException>(() => service.AddCustomSource(Path.Combine(_root, "missing")));
    }

    [Fact]
    public void SharedJunctionAppearsOnlyUnderGlobalWhileLocalVersionRemainsSeparate()
    {
        string global = Save("global", "world", "Global world", "26.3");
        string local = Save("local", "world", "Version world", "26.2");
        string linked = Path.Combine(_root, "linked-profile");
        Directory.CreateDirectory(linked);
        SafeFileOps.CreateJunction(Path.Combine(linked, "saves"), Path.Combine(global, "saves"));
        var catalog = WorldCatalogService.Scan(new[] {
            new WorldSource("Global", "Global .minecraft", global),
            new WorldSource("Linked profile", "Specific Version", linked),
            new WorldSource("26.2", "Specific Version", local) });
        Assert.Equal(2, catalog.Worlds.Count);
        Assert.Single(WorldCatalogService.Filter(catalog.Worlds, "", "Specific Version", "26.2"));
        Assert.True(File.Exists(Path.Combine(global, "saves", "world", "level.dat")));
    }

    private string RawSave(string folder, NbtCompound data)
    {
        string game = Path.Combine(_root, "meta");
        string directory = Path.Combine(game, "saves", folder);
        Directory.CreateDirectory(directory);
        File.WriteAllBytes(Path.Combine(directory, "level.dat"), Nbt.Write(new NbtCompound { ["Data"] = data }));
        return directory;
    }

    [Fact]
    public void ReadsGameModeHardcoreCheatsIconAndLegacyVersion()
    {
        // 26.x: hardcore lives in difficulty_settings; a Version{} is present.
        string modern = RawSave("modern", new NbtCompound
        {
            ["LevelName"] = new NbtString("Modern"), ["GameType"] = new NbtNumber(NbtTagType.Int, 1), ["allowCommands"] = new NbtNumber(NbtTagType.Byte, 1),
            ["Version"] = new NbtCompound { ["Name"] = new NbtString("26.3") }, ["DataVersion"] = new NbtNumber(NbtTagType.Int, 4500),
            ["difficulty_settings"] = new NbtCompound { ["hardcore"] = new NbtNumber(NbtTagType.Byte, 0) }
        });
        File.WriteAllBytes(Path.Combine(modern, "icon.png"), new byte[] { 1 });
        RawSave("hardcore26", new NbtCompound
        {
            ["LevelName"] = new NbtString("Hard 26"), ["GameType"] = new NbtNumber(NbtTagType.Int, 0), ["DataVersion"] = new NbtNumber(NbtTagType.Int, 4500),
            ["difficulty_settings"] = new NbtCompound { ["hardcore"] = new NbtNumber(NbtTagType.Byte, 1) }
        });
        // 1.8.9: no Version{} or DataVersion; hardcore at the top of Data.
        RawSave("legacy", new NbtCompound
        {
            ["LevelName"] = new NbtString("Legacy"), ["GameType"] = new NbtNumber(NbtTagType.Int, 2), ["hardcore"] = new NbtNumber(NbtTagType.Byte, 0),
            ["allowCommands"] = new NbtNumber(NbtTagType.Byte, 0)
        });
        var worlds = WorldCatalogService.Scan(new[] { new WorldSource("Global", "Global .minecraft", Path.Combine(_root, "meta")) }).Worlds.ToDictionary(w => w.Name);
        Assert.Equal(("Creative", (bool?)true, "26.3"), (worlds["Modern"].GameMode, worlds["Modern"].Cheats, worlds["Modern"].Version));
        Assert.Equal(Path.Combine(modern, "icon.png"), worlds["Modern"].Icon);
        Assert.Equal("Hardcore", worlds["Hard 26"].GameMode);
        Assert.Null(worlds["Hard 26"].Cheats);
        Assert.Null(worlds["Hard 26"].Icon);
        Assert.Equal(("Adventure", (bool?)false, WorldCatalogService.LegacyVersion), (worlds["Legacy"].GameMode, worlds["Legacy"].Cheats, worlds["Legacy"].Version));
    }

    [Fact]
    public void RelativeTimesAndSizesReadNaturally()
    {
        var now = new DateTime(2026, 10, 7, 12, 0, 0, DateTimeKind.Utc);
        Assert.Equal("just now", WorldCatalogService.Relative(now.AddSeconds(-20), now));
        Assert.Equal("1 minute ago", WorldCatalogService.Relative(now.AddMinutes(-1.5), now));
        Assert.Equal("2 hours ago", WorldCatalogService.Relative(now.AddHours(-2), now));
        Assert.Equal("yesterday", WorldCatalogService.Relative(now.AddHours(-30), now));
        Assert.Equal("3 days ago", WorldCatalogService.Relative(now.AddDays(-3), now));
        Assert.Equal("2 weeks ago", WorldCatalogService.Relative(now.AddDays(-15), now));
        Assert.Equal("4 months ago", WorldCatalogService.Relative(now.AddDays(-125), now));
        Assert.Equal("2 years ago", WorldCatalogService.Relative(now.AddDays(-800), now));
        Assert.Equal("512 B", WorldCatalogService.FormatSize(512));
        Assert.Equal("12 KB", WorldCatalogService.FormatSize(12 * 1024));
        Assert.Equal("1.5 MB", WorldCatalogService.FormatSize(1536 * 1024));
        Assert.Equal("2 GB", WorldCatalogService.FormatSize(2L * 1024 * 1024 * 1024));
    }
}
