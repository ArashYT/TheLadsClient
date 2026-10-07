using Microsoft.Data.Sqlite;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class ExternalInstancesTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "lads-external-" + Guid.NewGuid().ToString("N"));

    public ExternalInstancesTests() => Directory.CreateDirectory(_root);
    public void Dispose() { SqliteConnection.ClearAllPools(); try { Directory.Delete(_root, true); } catch (IOException) { } }

    private const string CurseForgeForge = """
        {"baseModLoader":{"forgeVersion":"47.4.0","name":"forge-47.4.0","type":1,"minecraftVersion":"1.20.1"},
         "lastPlayed":"2026-10-05T00:40:59.355Z","guid":"3ad42a41-54a5-4fee-9ec7-dfb4b761292f","gameTypeID":432,
         "installPath":"C:\\x\\Arise SMP\\","name":"Arise SMP","gameVersion":"1.20.1","profileImagePath":null,
         "installedModpack":{"name":"Arise SMP","thumbnailUrl":"https://media.forgecdn.net/a.png"}}
        """;

    [Fact]
    public void ParsesCurseForgeInstance()
    {
        var i = ExternalInstances.ParseCurseForge(CurseForgeForge, @"C:\x\Arise SMP", @"C:\x")!;
        Assert.Equal(ExternalLauncher.CurseForge, i.Launcher);
        Assert.Equal("Arise SMP", i.Name);
        Assert.Equal("3ad42a41-54a5-4fee-9ec7-dfb4b761292f", i.Id);
        Assert.Equal("1.20.1", i.McVersion);
        Assert.Equal("forge", i.Loader);
        Assert.Equal("47.4.0", i.LoaderVersion);
        Assert.Equal(new DateTime(2026, 10, 5, 0, 40, 59, 355, DateTimeKind.Utc), i.LastPlayedUtc);
        Assert.Equal(DateTimeKind.Utc, i.LastPlayedUtc!.Value.Kind);
        Assert.Equal("https://media.forgecdn.net/a.png", i.IconUrl);
        Assert.Null(i.IconFile);
    }

    [Fact]
    public void CurseForgeVanillaAndNeverPlayed()
    {
        var i = ExternalInstances.ParseCurseForge("""{"name":"Plain","gameVersion":"1.21.4","guid":"7f0e7bd2-9a35-4c7e-8a1b-9a3c5a1e2f10","lastPlayed":"0001-01-01T00:00:00","baseModLoader":null}""", "d", "r")!;
        Assert.Equal("vanilla", i.Loader);
        Assert.Null(i.LastPlayedUtc);
        Assert.Null(ExternalInstances.ParseCurseForge("""{"gameVersion":"1.21.4"}""", "d", "r"));
    }

    [Theory]
    [InlineData("fabric-0.15.11-1.20.1", "1.20.1", "fabric", "0.15.11")]
    [InlineData("forge-1.20.1-47.2.0", "1.20.1", "forge", "47.2.0")]
    [InlineData("neoforge-21.1.77", "1.21.1", "neoforge", "21.1.77")]
    [InlineData("quilt-0.26.0-1.20.4", "1.20.4", "quilt", "0.26.0")]
    public void SplitsCurseForgeLoaderNames(string name, string mc, string loader, string version)
    {
        Assert.Equal((loader, version), ExternalInstances.SplitCurseForgeLoader(name, mc));
    }

    [Fact]
    public void ParsesPrismInstanceWithIconAndGameFolder()
    {
        var dir = Path.Combine(_root, "Prism", "instances", "Skyblock");
        Directory.CreateDirectory(Path.Combine(dir, "minecraft", "mods"));
        File.WriteAllText(Path.Combine(dir, "minecraft", "mods", "a.jar"), "");
        var icons = Path.Combine(_root, "Prism", "icons");
        Directory.CreateDirectory(icons);
        File.WriteAllText(Path.Combine(icons, "sky.png"), "png");
        const string cfg = "[General]\nConfigVersion=1.2\niconKey=sky\nname=Skyblock PvP\nlastLaunchTime=1791300000000\nInstanceType=OneSix\n";
        const string pack = """{"components":[{"uid":"org.lwjgl","version":"2.9.4"},{"uid":"net.minecraft","version":"1.8.9"},{"uid":"net.minecraftforge","version":"11.15.1.2318"}],"formatVersion":1}""";

        var i = ExternalInstances.ParsePrism(cfg, pack, dir, Path.Combine(_root, "Prism"), icons)!;
        Assert.Equal("Skyblock", i.Id);
        Assert.Equal("Skyblock PvP", i.Name);
        Assert.Equal("1.8.9", i.McVersion);
        Assert.Equal("forge", i.Loader);
        Assert.Equal("11.15.1.2318", i.LoaderVersion);
        Assert.Equal(DateTimeOffset.FromUnixTimeMilliseconds(1791300000000).UtcDateTime, i.LastPlayedUtc);
        Assert.Equal(Path.Combine(icons, "sky.png"), i.IconFile);
        Assert.Equal(Path.Combine(dir, "minecraft"), i.GameDirectory);
    }

    [Fact]
    public void PrismDefaultsWhenFieldsAreMissing()
    {
        var i = ExternalInstances.ParsePrism("lastLaunchTime=0\niconKey=default\n", null, Path.Combine(_root, "Folder Name"), _root, null)!;
        Assert.Equal("Folder Name", i.Name);
        Assert.Null(i.LastPlayedUtc);
        Assert.Null(i.IconFile);
        Assert.Equal(Path.Combine(_root, "Folder Name", ".minecraft"), i.GameDirectory);
    }

    [Theory]
    [InlineData("net.fabricmc.fabric-loader", "fabric")]
    [InlineData("org.quiltmc.quilt-loader", "quilt")]
    [InlineData("net.neoforged", "neoforge")]
    public void ReadsLoaderFromMmcPack(string uid, string loader)
    {
        var (mc, l, v) = ExternalInstances.ParseMmcPack($$"""{"components":[{"uid":"net.minecraft","version":"1.21.1"},{"uid":"{{uid}}","version":"9.9"}]}""");
        Assert.Equal(("1.21.1", loader, "9.9"), (mc, l, v));
        Assert.Equal(("1.20", "vanilla", (string?)null), ExternalInstances.ParseMmcPack("""{"components":[{"uid":"net.minecraft","version":"1.20"}]}"""));
    }

    [Fact]
    public void IniHandlesSectionsCommentsAndQuotes()
    {
        var ini = ExternalInstances.ParseIni("[General]\r\n; comment\r\nname=\"My \\\"pack\\\"\"\r\nInstanceDir=D:/Games/instances\r\nbroken\r\n");
        Assert.Equal("My \"pack\"", ini["name"]);
        Assert.Equal("D:/Games/instances", ini["instancedir"]);
        Assert.False(ini.ContainsKey("broken"));
    }

    [Fact]
    public void ReadsPrismDataFolderUsingItsInstanceDirSetting()
    {
        var data = Path.Combine(_root, "PrismLauncher");
        var inst = Path.Combine(_root, "elsewhere", "A");
        Directory.CreateDirectory(inst);
        Directory.CreateDirectory(data);
        File.WriteAllText(Path.Combine(data, "prismlauncher.cfg"), $"[General]\nInstanceDir={Path.Combine(_root, "elsewhere").Replace('\\', '/')}\n");
        File.WriteAllText(Path.Combine(inst, "instance.cfg"), "name=Alpha\n");
        File.WriteAllText(Path.Combine(inst, "mmc-pack.json"), """{"components":[{"uid":"net.minecraft","version":"26.2"}]}""");

        var source = ExternalInstances.Classify(data, custom: true)!;
        Assert.Equal(ExternalLauncher.Prism, source.Launcher);
        var found = Assert.Single(ExternalInstances.Read(source));
        Assert.Equal("Alpha", found.Name);
        Assert.Equal(data, found.DataRoot);
    }

    [Fact]
    public void ClassifiesCurseForgeFolders()
    {
        var cf = Path.Combine(_root, "curseforge", "minecraft");
        var one = Path.Combine(cf, "Instances", "Pack");
        Directory.CreateDirectory(one);
        File.WriteAllText(Path.Combine(one, "minecraftinstance.json"), CurseForgeForge);
        Assert.Equal(Path.Combine(cf, "Instances"), ExternalInstances.Classify(cf, true)!.Root);
        Assert.Equal(ExternalLauncher.CurseForge, ExternalInstances.Classify(Path.Combine(cf, "Instances"), true)!.Launcher);
        Assert.Null(ExternalInstances.Classify(Path.Combine(_root, "nothing-here"), true));
        var read = Assert.Single(ExternalInstances.Read(ExternalInstances.Classify(cf, true)!));
        Assert.Equal(one, read.Directory);
    }

    [Fact]
    public void ParsesOldModrinthProfileJson()
    {
        var i = ExternalInstances.ParseModrinthProfileJson("""{"path":"fo","metadata":{"name":"FO","game_version":"1.20.1","loader":"fabric","loader_version":{"id":"0.14.21"},"last_played":"2023-05-01T10:00:00Z"}}""", Path.Combine(_root, "fo"), _root)!;
        Assert.Equal(("FO", "1.20.1", "fabric", "0.14.21"), (i.Name, i.McVersion, i.Loader, i.LoaderVersion));
        Assert.Equal(new DateTime(2023, 5, 1, 10, 0, 0, DateTimeKind.Utc), i.LastPlayedUtc);
    }

    [Fact]
    public void ReadsModrinthDatabaseNewSchemaWithoutTouchingIt()
    {
        var app = Path.Combine(_root, "ModrinthApp");
        Directory.CreateDirectory(Path.Combine(app, "profiles", "cobble", "mods"));
        File.WriteAllText(Path.Combine(app, "profiles", "cobble", "mods", "a.jar"), "");
        var db = Path.Combine(app, "app.db");
        Exec(db, """
            CREATE TABLE instances (id TEXT, path TEXT, applied_content_set_id TEXT, install_stage TEXT, name TEXT, icon_path TEXT, created INTEGER, modified INTEGER, last_played INTEGER);
            CREATE TABLE instance_content_sets (id TEXT, instance_id TEXT, game_version TEXT, loader TEXT, loader_version TEXT);
            INSERT INTO instances VALUES ('i1', 'cobble', 'cs1', 'installed', 'Cobblemon', NULL, 1, 1, 1790000000);
            INSERT INTO instance_content_sets VALUES ('cs1', 'i1', '1.21.1', 'fabric', '0.16.9');
            """);
        var before = File.GetLastWriteTimeUtc(db);
        var source = ExternalInstances.Classify(app, true)!;
        Assert.Equal(ExternalLauncher.Modrinth, source.Launcher);

        var i = Assert.Single(ExternalInstances.Read(source));
        Assert.Equal(("cobble", "Cobblemon", "1.21.1", "fabric", "0.16.9"), (i.Id, i.Name, i.McVersion, i.Loader, i.LoaderVersion));
        Assert.Equal(DateTimeOffset.FromUnixTimeSeconds(1790000000).UtcDateTime, i.LastPlayedUtc);
        Assert.Equal(Path.Combine(app, "profiles", "cobble"), i.Directory);
        Assert.Equal(1, i.ModCount);
        Assert.Equal(before, File.GetLastWriteTimeUtc(db));
        Assert.False(File.Exists(db + "-shm"));
    }

    [Fact]
    public void ReadsModrinthDatabaseProfilesSchema()
    {
        var app = Path.Combine(_root, "ModrinthOld");
        Directory.CreateDirectory(app);
        Exec(Path.Combine(app, "app.db"), """
            CREATE TABLE profiles (path TEXT, install_stage TEXT, name TEXT, icon_path TEXT, game_version TEXT, mod_loader TEXT, mod_loader_version TEXT, created INTEGER, modified INTEGER, last_played INTEGER);
            INSERT INTO profiles VALUES ('Vanilla', 'installed', 'Plain', NULL, '1.21.4', 'vanilla', NULL, 1, 1, NULL);
            """);
        var i = Assert.Single(ExternalInstances.ReadModrinthDatabase(Path.Combine(app, "app.db"), app));
        Assert.Equal(("Plain", "1.21.4", "vanilla"), (i.Name, i.McVersion, i.Loader));
        Assert.Null(i.LastPlayedUtc);
    }

    [Fact]
    public void LaunchCommandsMatchEachLauncher()
    {
        var cf = new ExternalInstance { Launcher = ExternalLauncher.CurseForge, Id = "3ad42a41-54a5-4fee-9ec7-dfb4b761292f" };
        var start = ExternalInstances.LaunchCommand(cf, @"C:\CF\CurseForge.exe");
        Assert.Equal(new[] { "curseforge://launch-game?instanceId=3ad42a41-54a5-4fee-9ec7-dfb4b761292f&gameId=432" }, start.ArgumentList);

        var prismDefault = new ExternalInstance { Launcher = ExternalLauncher.Prism, Id = "26.2", DataRoot = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "PrismLauncher") };
        Assert.Equal(new[] { "--launch", "26.2" }, ExternalInstances.LaunchCommand(prismDefault, @"C:\P\prismlauncher.exe").ArgumentList);

        var prismOther = prismDefault with { DataRoot = @"D:\Games\Prism" };
        Assert.Equal(new[] { "--dir", @"D:\Games\Prism", "--launch", "26.2" }, ExternalInstances.LaunchCommand(prismOther, @"C:\P\prismlauncher.exe").ArgumentList);

        var portable = prismDefault with { DataRoot = @"C:\P" };
        Assert.Equal(new[] { "--launch", "26.2" }, ExternalInstances.LaunchCommand(portable, @"C:\P\prismlauncher.exe").ArgumentList);

        var modrinth = new ExternalInstance { Launcher = ExternalLauncher.Modrinth, Id = "cobble" };
        Assert.Empty(ExternalInstances.LaunchCommand(modrinth, @"C:\M\Modrinth App.exe").ArgumentList);
        Assert.False(ExternalInstances.CanStartInstance(ExternalLauncher.Modrinth));
        Assert.False(ExternalInstances.CanLaunch(cf with { Id = "" }, @"C:\CF\CurseForge.exe"));
        Assert.False(ExternalInstances.CanLaunch(cf, null));
    }

    [Theory]
    [InlineData("\"C:\\Users\\A\\AppData\\Local\\Programs\\CurseForge Windows\\CurseForge.exe\" \"%1\"", "C:\\Users\\A\\AppData\\Local\\Programs\\CurseForge Windows\\CurseForge.exe")]
    [InlineData("C:\\Apps\\Modrinth App.exe %1", "C:\\Apps\\Modrinth App.exe")]
    public void ReadsProtocolHandlerProgram(string command, string exe) => Assert.Equal(exe, ExternalInstances.CommandExe(command));

    [Fact]
    public void CustomFoldersRoundTrip()
    {
        Assert.Empty(ExternalInstances.LoadCustomFolders(_root));
        ExternalInstances.SaveCustomFolders(_root, new[] { @"D:\MultiMC", @"d:\multimc", @"E:\CF" });
        Assert.Equal(new[] { @"D:\MultiMC", @"E:\CF" }, ExternalInstances.LoadCustomFolders(_root));
    }

    private static void Exec(string db, string sql)
    {
        using (var c = new SqliteConnection($"Data Source={db};Pooling=False"))
        {
            c.Open();
            using var cmd = c.CreateCommand();
            cmd.CommandText = sql;
            cmd.ExecuteNonQuery();
        }
    }
}
