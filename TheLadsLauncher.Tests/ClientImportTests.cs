using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using System.Text.Json.Nodes;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>The other-client importer over synthetic files shaped like real Lunar, Badlion, Feather, Prism and vanilla folders.</summary>
public sealed class ClientImportTests : IDisposable
{
    private const string Modern = "version:5023\nfov:0.5\nmouseSensitivity:0.25\nguiScale:2\nenableVsync:false\nsoundCategory_master:0.2\nlastServer:mc.example.net\nao:true\n"
        + "key_key.jump:key.keyboard.space\nkey_key.attack:key.mouse.left\nkey_Zoom Key:key.keyboard.c\nmodelPart_cape:true\n";
    private const string Legacy189 = "fov:0.25\nguiScale:3\nao:2\nfancyGraphics:true\nkey_key.jump:57\nkey_key.attack:-100\nkey_key.drop:0\nkey_Zoom Key:46\n";

    private readonly string _root = Path.Combine(Path.GetTempPath(), "lads-import-test-" + Guid.NewGuid().ToString("N"));
    private readonly ImportEnvironment _env;
    private readonly ImportTargets _targets;
    private readonly string _modernProfile, _legacyProfile;

    public ClientImportTests()
    {
        _env = new ImportEnvironment(Path.Combine(_root, "home"), Path.Combine(_root, "appdata"));
        Directory.CreateDirectory(_env.Minecraft);
        var paths = new PathService(Path.Combine(_root, "lads"));
        var modern = new LauncherProfile { Id = "modern", Name = "26.3", MinecraftVersion = "26.3" };
        var legacy = new LauncherProfile { Id = "legacy", Name = "1.8.9", MinecraftVersion = "1.8.9" };
        _modernProfile = paths.GetProfileDirectory(modern);
        _legacyProfile = paths.GetProfileDirectory(legacy);
        Directory.CreateDirectory(_modernProfile);
        Directory.CreateDirectory(_legacyProfile);
        var shared = Path.Combine(_root, "shared-root");
        Directory.CreateDirectory(shared);
        _targets = new ImportTargets(paths, new[] { modern, legacy }, shared, Path.Combine(paths.BaseDirectory, "settings.json"));
    }

    public void Dispose()
    {
        try { Directory.Delete(_root, true); } catch (IOException) { }
    }

    private static void Write(string file, string text)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(file)!);
        File.WriteAllText(file, text);
    }

    private ImportSource Client(string id) => ClientImport.Detect(_env).Single(s => s.Id == id);

    private string LunarRoot => Path.Combine(_env.Home, ".lunarclient");

    private void LunarFixture()
    {
        var game = Path.Combine(LunarRoot, "settings", "game");
        Write(Path.Combine(LunarRoot, "settings", "launcher.json"), """
            {"settings":{"allocatedMemory":6144,"afterLaunchAction":"HIDE","gameResolution":{"width":1024,"height":576,"fullscreen":false},"discordRichPresence":{"enabled":false}}}
            """);
        Write(Path.Combine(game, "accounts.json"), """{"accessToken":"MUST-NEVER-BE-READ"}""");
        Write(Path.Combine(game, "profile_manager.json"), """[{"name":"Default","active":false},{"name":"Arena PvP","active":true}]""");
        Write(Path.Combine(game, "Arena PvP", "mods.json"), """
            {"version":37,
             "FPS":{"enabled":true,"options":{"brackets":false,"backgroundColor":{"value":0},"scale":0.9},"x":4.5,"y":7.0,"position":"topLeft","seen":true,"panelIndex":2},
             "COORDINATES":{"options":{"backgroundColor":{"value":0}},"x":-4.5,"y":25.0,"position":"topRight"},
             "ARMORSTATUS":{"x":-4.5,"position":"bottomRight","panelIndex":5},
             "ZOOM":{"options":{"smoothZoom":false,"variableZoom":true}},
             "PING":{"enabled":true,"PING_HUD":{"x":4.5,"y":43.0,"position":"topLeft"}},
             "CROSSHAIR":{"enabled":true,"CROSSHAIR_NORMAL":{"options":{"color":{"value":-11141121},"crosshairDot":false},"customCrosshair":"LCCH-1-AA=="}},
             "LIGHTING":{"options":{"fullBright":true}},
             "TIER_TAGGER":{"enabled":true},
             "NOT_A_STRING_POS":{"position":5}}
            """);
        Write(Path.Combine(_env.Minecraft, "optionsLC.txt"), """
            {"fov":"90","mouseSensitivity":"0.5","guiScale":"2","lastServer":"mc.example.net","maxFPS":260,"key_key.jump":"key.keyboard.space","key_key.drop":"16","key_Freelook":"key.keyboard.right.alt"}
            """);
    }

    private void BadlionFixture()
    {
        Write(Path.Combine(_env.Minecraft, "options.txt"), Modern);
        Write(Path.Combine(_env.Minecraft, "badlion_settings.json"), """{"activeProfile":"Cloned Profile "}""");
        Write(Path.Combine(_env.AppData, "Badlion Client", "accounts.dat"), "MUST-NEVER-BE-READ");
        var zip = Path.Combine(_env.Minecraft, "BLClient-Mod-Profiles", "Cloned Profile .zip");
        Directory.CreateDirectory(Path.GetDirectoryName(zip)!);
        using var archive = ZipFile.Open(zip, ZipArchiveMode.Create);
        using var writer = new StreamWriter(archive.CreateEntry("data.json").Open());
        writer.Write("""
            {"boxes":[{"identifier":"fps","quadrant":"TOP_RIGHT","xPosition":1.0,"yPosition":0.1,"screenWidth":1920,"screenHeight":1080},{"identifier":"hystats","quadrant":"TOP_LEFT","xPosition":0,"yPosition":0}],
             "showFPS":{"enabled":true,"favorite":false,"reversed":{"value":true},"backgroundColor":{"set":true,"enabled":false,"color":{"red":0,"green":0,"blue":0,"alpha":-128}},"labelColor":{"set":false,"enabled":true,"color":{"red":-1,"green":-1,"blue":-1,"alpha":-1}}},
             "crosshair":{"enabled":true,"widthS":{"value":5.0},"outline":{"value":true},"crosshairColor":{"enabled":true,"color":{"red":0,"green":-52,"blue":-2,"alpha":-1}}},
             "autoGG":{"enabled":true},
             "skyblockAddons":{"enabled":false}}
            """);
    }

    private void FeatherFixture()
    {
        Write(Path.Combine(_env.Minecraft, "options.txt"), Modern);
        var folder = Path.Combine(_env.Minecraft, "feather", "configuration");
        Write(Path.Combine(folder, "current.profile"), "default");
        Write(Path.Combine(folder, "profiles", "default.json"), """
            {"fps":{"**ArbitraryData**":{},"hudAnchor":"top_right","hudRelativeX":"-3.0","hudRelativeY":"8.0","hudScale":"0.6","textColor":"false/255/0/0/255","backgroundColor":"7/7/7/146","enabled":"true"},
             "zoom":{"smoothZoom":"true","scrollZoom":"false","enabled":"false","keyZoom":"0"},
             "culllogs":{"enabled":"true"}}
            """);
    }

    [Fact]
    public void DetectFindsOnlyWhatIsInstalled()
    {
        Assert.Empty(ClientImport.Detect(_env));
        LunarFixture();
        FeatherFixture();
        var found = ClientImport.Detect(_env).Select(s => s.Id).ToList();
        Assert.Contains("lunar", found);
        Assert.Contains("feather", found);
        Assert.Contains("vanilla", found);
        Assert.DoesNotContain("badlion", found);
        Assert.True(Client("vanilla").Basic);
        Assert.False(Client("lunar").Basic);
    }

    [Fact]
    public void LunarMapsItsActiveProfileAndNeverGuesses()
    {
        LunarFixture();
        var plan = ClientImport.Plan(Client("lunar"), _targets);
        var mods = plan.Categories[ImportCategory.Mods];
        Assert.Null(mods.Problem);
        var result = ClientImport.Apply(plan, new[] { ImportCategory.Mods }, _targets);
        Assert.Empty(result.Errors);
        Assert.Equal(mods.Changes.Count, result.Applied[ImportCategory.Mods]);

        var config = JsonNode.Parse(File.ReadAllText(_targets.ConfigFile))!;
        Assert.True(config["lads197PresetApplied"]!.GetValue<bool>()); // else the game resets to its preset over the import
        var fps = config["modules"]!["FPS"]!;
        Assert.True(fps["enabled"]!.GetValue<bool>());
        Assert.Equal(90, fps["options"]!["Size"]!.GetValue<double>());
        Assert.Equal(0, fps["options"]!["Background"]!["color"]!.GetValue<int>());
        // Lunar omits "enabled" when it is at its default: not listed means left alone.
        Assert.Null(config["modules"]!["Coordinates"]!["enabled"]);
        Assert.False(config["modules"]!["Zoom"]!["options"]!["Smooth Zoom"]!.GetValue<bool>());
        Assert.True(config["modules"]!["Fullbright"]!["enabled"]!.GetValue<bool>());
        Assert.Equal(-11141121, config["modules"]!["Crosshair Tweaks"]!["options"]!["Color"]!["color"]!.GetValue<int>());

        var anchor = config["hud"]!["anchors"]!["Coordinates"]!;
        Assert.Equal("[2,2,-4,0]", anchor["x"]!.ToJsonString()); // -4.5 rounds to even
        Assert.Equal("[0,0,25,0]", anchor["y"]!.ToJsonString());
        Assert.Equal(anchor["at"]!.ToJsonString(), config["hud"]!["positions"]!["Coordinates"]!.ToJsonString());
        Assert.Equal("[2,2,-4,0]", config["hud"]!["anchors"]!["ArmorHUD"]!["x"]!.ToJsonString());
        Assert.Equal("[2,2,0,0]", config["hud"]!["anchors"]!["ArmorHUD"]!["y"]!.ToJsonString()); // y left out means 0
        Assert.NotNull(config["hud"]!["anchors"]!["PingHUD"]);

        Assert.Contains(mods.Skipped, s => s.StartsWith("TIER_TAGGER", StringComparison.Ordinal));
        Assert.Contains(mods.Skipped, s => s.StartsWith("FPS: brackets", StringComparison.Ordinal));
        Assert.Contains(mods.Skipped, s => s.Contains("LCCH", StringComparison.Ordinal));
    }

    [Fact]
    public void LunarOptionsConvertDegreesAndLauncherSettings()
    {
        LunarFixture();
        var plan = ClientImport.Plan(Client("lunar"), _targets);
        var options = plan.Categories[ImportCategory.Options];
        Assert.Equal("0.5", options.Options["fov"]); // 90 degrees in Lunar's own file is 0.5 in options.txt
        Assert.Equal("key.keyboard.q", options.Options["key_key.drop"]); // Lunar keeps 1.8 codes in the file too
        Assert.False(options.Options.ContainsKey("lastServer"));
        Assert.False(options.Options.ContainsKey("key_Freelook"));
        Assert.Contains(options.Skipped, s => s.Contains("Freelook", StringComparison.Ordinal));

        var launcher = plan.Categories[ImportCategory.Launcher];
        Assert.Equal(6144, launcher.Launcher["MaxRamMb"].GetValue<int>());
        Assert.False(launcher.Launcher["DiscordRichPresence"].GetValue<bool>());
        Assert.Contains(launcher.Skipped, s => s.Contains("window size", StringComparison.Ordinal));
    }

    [Fact]
    public void BadlionReadsTheActiveProfileZipAndBoxes()
    {
        BadlionFixture();
        var plan = ClientImport.Plan(Client("badlion"), _targets);
        var mods = plan.Categories[ImportCategory.Mods];
        Assert.Null(mods.Problem);
        ClientImport.Apply(plan, new[] { ImportCategory.Mods }, _targets);
        var config = JsonNode.Parse(File.ReadAllText(_targets.ConfigFile))!;
        Assert.True(config["modules"]!["HYPIXEL Mods"]!["options"]!["Auto GG"]!.GetValue<bool>());
        Assert.Equal(0, config["modules"]!["FPS"]!["options"]!["Background"]!["color"]!.GetValue<int>()); // switched off in Badlion
        Assert.Null(config["modules"]!["FPS"]!["options"]!["Text Color"]); // set:false, the client's default
        Assert.Equal(unchecked((int)0xFF00CCFE), config["modules"]!["Crosshair Tweaks"]!["options"]!["Color"]!["color"]!.GetValue<int>());
        Assert.Equal("[2,2,0,0]", config["hud"]!["anchors"]!["FPS"]!["x"]!.ToJsonString());
        Assert.Equal("[0,0,54,0]", config["hud"]!["anchors"]!["FPS"]!["y"]!.ToJsonString()); // 10% of 540 GUI pixels
        Assert.Contains(mods.Skipped, s => s.Contains("skyblockAddons", StringComparison.Ordinal));
        Assert.Contains(mods.Skipped, s => s.Contains("hystats", StringComparison.Ordinal));
        Assert.Contains(plan.Categories[ImportCategory.Launcher].Skipped, s => s.Contains("no verified", StringComparison.Ordinal));
    }

    [Fact]
    public void FeatherStringsConvert()
    {
        FeatherFixture();
        var plan = ClientImport.Plan(Client("feather"), _targets);
        ClientImport.Apply(plan, new[] { ImportCategory.Mods }, _targets);
        var config = JsonNode.Parse(File.ReadAllText(_targets.ConfigFile))!;
        var fps = config["modules"]!["FPS"]!;
        Assert.True(fps["enabled"]!.GetValue<bool>());
        Assert.Equal(60, fps["options"]!["Size"]!.GetValue<double>());
        Assert.Equal(unchecked((int)0xFFFF0000), fps["options"]!["Text Color"]!["color"]!.GetValue<int>());
        Assert.Equal(unchecked((int)0x92070707), fps["options"]!["Background"]!["color"]!.GetValue<int>());
        Assert.False(fps["useGlobalColor"]!.GetValue<bool>());
        Assert.Equal("[2,2,-3,0]", config["hud"]!["anchors"]!["FPS"]!["x"]!.ToJsonString());
        Assert.False(config["modules"]!["Zoom"]!["enabled"]!.GetValue<bool>());
        Assert.False(config["modules"]!["Zoom"]!["options"]!["Scroll to Zoom"]!.GetValue<bool>());
    }

    [Fact]
    public void LegacyOptionsConvertAndReachBothVersionsInTheirFormats()
    {
        Write(Path.Combine(_env.Minecraft, "options.txt"), Legacy189);
        Write(Path.Combine(_legacyProfile, "options.txt"), "fov:0.0\nkey_key.jump:30\n");
        Write(Path.Combine(_modernProfile, "options.txt"), "version:5023\nfov:0.0\nkey_key.jump:key.keyboard.a\nlastServer:keep.example.net\n");
        var plan = ClientImport.Plan(Client("vanilla"), _targets);
        var options = plan.Categories[ImportCategory.Options];
        Assert.Equal("key.keyboard.space", options.Options["key_key.jump"]);
        Assert.Equal("key.mouse.left", options.Options["key_key.attack"]);
        Assert.Equal("key.keyboard.unknown", options.Options["key_key.drop"]);
        Assert.Equal("true", options.Options["ao"]); // 1.8.9's 2
        Assert.False(options.Options.ContainsKey("fancyGraphics"));
        Assert.Contains(options.Skipped, s => s.Contains("left as they are", StringComparison.Ordinal));

        var result = ClientImport.Apply(plan, new[] { ImportCategory.Options }, _targets);
        Assert.Empty(result.Errors);
        var modern = GameOptionsFile.Parse(File.ReadAllText(Path.Combine(_modernProfile, "options.txt")));
        Assert.Equal("0.25", modern.Get("fov"));
        Assert.Equal("key.keyboard.space", modern.Get("key_key.jump"));
        Assert.Equal("keep.example.net", modern.Get("lastServer")); // nothing else changes
        var legacy = GameOptionsFile.Parse(File.ReadAllText(Path.Combine(_legacyProfile, "options.txt")));
        Assert.Equal("57", legacy.Get("key_key.jump"));
        Assert.Equal("-100", legacy.Get("key_key.attack"));
        Assert.Equal("2", legacy.Get("ao"));
        Assert.Equal("3", legacy.Get("guiScale"));
    }

    [Fact]
    public void ARunningGameGetsItsOptionsAtClose()
    {
        Write(Path.Combine(_env.Minecraft, "options.txt"), Modern);
        Write(Path.Combine(_modernProfile, "options.txt"), "version:5023\nfov:0.0\n");
        using var java = RunningJava.Start(_modernProfile);
        if (java == null) return; // no Java on this machine
        RunningGameMarker.Write(_modernProfile, java.Process.Id, java.Process.StartTime.ToUniversalTime(), new[] { "theladscore" });
        Assert.True(RunningGameMarker.IsRunning(_modernProfile));
        var targets = _targets with { InGame = true };
        var result = ClientImport.Apply(ClientImport.Plan(Client("vanilla"), targets), new[] { ImportCategory.Options }, targets);
        Assert.True(result.RestartNeeded);
        Assert.Equal("0.0", GameOptionsFile.Parse(File.ReadAllText(Path.Combine(_modernProfile, "options.txt"))).Get("fov"));

        File.Delete(RunningGameMarker.PathFor(_modernProfile));
        SharedControls.ApplyPending(_modernProfile, "26.3");
        var after = GameOptionsFile.Parse(File.ReadAllText(Path.Combine(_modernProfile, "options.txt")));
        Assert.Equal("0.5", after.Get("fov"));
        Assert.Equal("0.2", after.Get("soundCategory_master")); // video and audio too, not only the controls
    }

    private static byte[] Servers(params (string Name, string Ip)[] servers)
    {
        var list = ServerListFile.CreateEmpty();
        foreach (var (name, ip) in servers) list.Add(new NbtCompound { ["name"] = new NbtString(name), ["ip"] = new NbtString(ip) });
        return Nbt.Write(list.Root);
    }

    [Fact]
    public void ServersMergeWithoutDuplicatesAndTheLadsNetworkStaysFirst()
    {
        Write(Path.Combine(_env.Minecraft, "options.txt"), Modern);
        File.WriteAllBytes(Path.Combine(_env.Minecraft, "servers.dat"), Servers(("New", "new.example.net"), ("Dupe", "HAVE.example.net"), ("Bad", "not a valid/address"), ("Twice", "new.example.net")));
        File.WriteAllBytes(_targets.ServersFile, Servers(("Have", "have.example.net")));

        var plan = ClientImport.Plan(Client("vanilla"), _targets);
        var servers = plan.Categories[ImportCategory.Servers];
        Assert.Single(servers.Servers);
        Assert.Contains(servers.Skipped, s => s.Contains("unusable address", StringComparison.Ordinal));
        ClientImport.Apply(plan, new[] { ImportCategory.Servers }, _targets);

        var read = ServerListFile.Read(_targets.ServersFile).Entries.ToList();
        Assert.Equal(new[] { ServerListService.PinnedAddress, "have.example.net", "new.example.net" }, read.Select(e => e.Ip));
        Assert.Empty(ClientImport.Plan(Client("vanilla"), _targets).Categories[ImportCategory.Servers].Changes);
    }

    [Fact]
    public void PacksCopyOnceAndNeverEscapeTheFolder()
    {
        Write(Path.Combine(_env.Minecraft, "options.txt"), Modern);
        var packs = Path.Combine(_env.Minecraft, "resourcepacks");
        Write(Path.Combine(packs, "Fresh.zip"), "pk");
        Write(Path.Combine(packs, "Have.zip"), "different");
        Write(Path.Combine(packs, "Folder Pack", "pack.mcmeta"), "{}");
        Write(Path.Combine(packs, "Folder Pack", "assets", "a.png"), "x");
        Write(Path.Combine(packs, "not a pack.txt"), "no");
        Write(Path.Combine(_targets.PacksDirectory, "Have.zip"), "mine");

        var plan = ClientImport.Plan(Client("vanilla"), _targets);
        var category = plan.Categories[ImportCategory.Packs];
        Assert.Equal(2, category.Changes.Count);
        Assert.Contains(category.Skipped, s => s.StartsWith("Have.zip", StringComparison.Ordinal));
        var result = ClientImport.Apply(plan, new[] { ImportCategory.Packs }, _targets);
        Assert.Empty(result.Errors);
        Assert.True(File.Exists(Path.Combine(_targets.PacksDirectory, "Fresh.zip")));
        Assert.True(File.Exists(Path.Combine(_targets.PacksDirectory, "Folder Pack", "assets", "a.png")));
        Assert.Equal("mine", File.ReadAllText(Path.Combine(_targets.PacksDirectory, "Have.zip")));

        ClientImport.Undo(result.BackupDirectory!, _targets);
        Assert.False(File.Exists(Path.Combine(_targets.PacksDirectory, "Fresh.zip")));
        Assert.False(Directory.Exists(Path.Combine(_targets.PacksDirectory, "Folder Pack")));
        Assert.Equal("mine", File.ReadAllText(Path.Combine(_targets.PacksDirectory, "Have.zip")));
    }

    [Fact]
    public void AnOldBackupIsNoLongerOffered()
    {
        LunarFixture();
        File.WriteAllBytes(Path.Combine(_env.Minecraft, "servers.dat"), Servers(("New", "new.example.net")));
        var result = ClientImport.Apply(ClientImport.Plan(Client("lunar"), _targets), Enum.GetValues<ImportCategory>(), _targets);
        Assert.Equal(result.BackupDirectory, ClientImport.LatestBackup(_targets));
        Directory.SetCreationTimeUtc(result.BackupDirectory!, DateTime.UtcNow.AddDays(-8));
        Assert.Null(ClientImport.LatestBackup(_targets));
    }

    [Fact]
    public void ImportingTwiceChangesNothingAndUndoRestoresEverything()
    {
        LunarFixture();
        Write(_targets.Paths.SharedOptionsFile, "fov:0.0\n");
        Write(Path.Combine(_modernProfile, "options.txt"), "version:5023\nfov:0.0\n");
        File.WriteAllBytes(_targets.ServersFile, Servers(("Have", "have.example.net")));
        var serversBefore = File.ReadAllBytes(_targets.ServersFile);
        File.WriteAllBytes(Path.Combine(_env.Minecraft, "servers.dat"), Servers(("New", "new.example.net")));
        var existingConfig = """{"modules":{"Zoom":{"enabled":true,"options":{"Smooth Zoom":true}},"Unknown Mod":{"enabled":true}},"hud":{"hudScale":3},"lads197PresetApplied":true}""";
        Write(_targets.ConfigFile, existingConfig);
        new LauncherSettings { MaxRamMb = 2048, RamChosenByUser = true }.SaveTo(_targets.SettingsFile);
        var all = Enum.GetValues<ImportCategory>();

        var first = ClientImport.Apply(ClientImport.Plan(Client("lunar"), _targets), all, _targets);
        Assert.Empty(first.Errors);
        Assert.NotNull(first.BackupDirectory);
        Assert.Equal(6144, LauncherSettings.LoadFrom(_targets.SettingsFile).MaxRamMb);
        var config = JsonNode.Parse(File.ReadAllText(_targets.ConfigFile))!;
        Assert.NotNull(config["modules"]!["Unknown Mod"]); // what the import does not name stays
        Assert.Equal(3, config["hud"]!["hudScale"]!.GetValue<int>());

        var again = ClientImport.Plan(Client("lunar"), _targets);
        Assert.All(again.Categories.Values, c => Assert.True(c.Changes.Count == 0, $"{c.Category}: {string.Join(", ", c.Changes)}"));
        var second = ClientImport.Apply(again, all, _targets);
        Assert.Null(second.BackupDirectory); // nothing to do, nothing to back up
        Assert.Equal(first.BackupDirectory, ClientImport.LatestBackup(_targets));

        var undone = ClientImport.Undo(first.BackupDirectory!, _targets);
        Assert.Empty(undone.Errors);
        Assert.Equal("fov:0.0\n", File.ReadAllText(_targets.Paths.SharedOptionsFile));
        Assert.Equal("version:5023\nfov:0.0\n", File.ReadAllText(Path.Combine(_modernProfile, "options.txt")));
        Assert.False(File.Exists(Path.Combine(_legacyProfile, "options.txt"))); // the import created it
        Assert.Equal(serversBefore, File.ReadAllBytes(_targets.ServersFile));
        Assert.Equal(existingConfig, File.ReadAllText(_targets.ConfigFile));
        Assert.Equal(2048, LauncherSettings.LoadFrom(_targets.SettingsFile).MaxRamMb);
        Assert.Null(ClientImport.LatestBackup(_targets)); // undone once
    }

    [Fact]
    public void MalformedSourcesStopOnlyTheirCategoryAndWriteNothing()
    {
        LunarFixture();
        var game = Path.Combine(LunarRoot, "settings", "game");
        Write(Path.Combine(game, "Arena PvP", "mods.json"), "{ not json");
        Write(Path.Combine(LunarRoot, "settings", "launcher.json"), """["a newer format"]""");
        Write(Path.Combine(_env.Minecraft, "optionsLC.txt"), new string('[', 200000));
        File.WriteAllBytes(Path.Combine(_env.Minecraft, "servers.dat"), new byte[] { 0x0A, 0, 0, 9, 0, 7, 1, 2, 3 });

        var plan = ClientImport.Plan(Client("lunar"), _targets);
        foreach (var category in new[] { ImportCategory.Options, ImportCategory.Servers, ImportCategory.Mods, ImportCategory.Launcher })
        {
            Assert.NotNull(plan.Categories[category].Problem);
            Assert.Empty(plan.Categories[category].Changes);
        }
        var result = ClientImport.Apply(plan, Enum.GetValues<ImportCategory>(), _targets);
        Assert.Empty(result.Applied);
        Assert.Null(result.BackupDirectory);
        Assert.False(File.Exists(_targets.ConfigFile));
    }

    [Fact]
    public void OversizedAndHostileValuesAreRefused()
    {
        LunarFixture();
        var game = Path.Combine(LunarRoot, "settings", "game");
        Write(Path.Combine(game, "profile_manager.json"), """[{"name":"..\\..\\..\\secret","active":true}]""");
        var plan = ClientImport.Plan(Client("lunar"), _targets);
        Assert.Empty(plan.Categories[ImportCategory.Mods].Changes); // the traversal name falls back to Default, which has no mods.json

        File.WriteAllText(Path.Combine(_env.Minecraft, "optionsLC.txt"), new string(' ', 17 * 1024 * 1024));
        Assert.Contains("too large", ClientImport.Plan(Client("lunar"), _targets).Categories[ImportCategory.Options].Problem);

        Write(Path.Combine(_env.Minecraft, "options.txt"), "fov:9000\nmouseSensitivity:NaN\nguiScale:abc\nenableVsync:maybe\nkey_key.jump:key.keyboard.space\n");
        var options = ClientImport.Plan(Client("vanilla"), _targets).Categories[ImportCategory.Options];
        Assert.Equal(new[] { "key_key.jump" }, options.Options.Keys);
        Assert.Equal(4, options.Skipped.Count(s => s.Contains("unusable", StringComparison.Ordinal)));
    }

    [Fact]
    public void AGameRunningElsewhereStopsTheConfigFromTheLauncherButNotInGame()
    {
        LunarFixture();
        using var java = RunningJava.Start(_modernProfile);
        if (java == null) return; // no Java on this machine
        RunningGameMarker.Write(_modernProfile, java.Process.Id, java.Process.StartTime.ToUniversalTime(), new[] { "theladscore" });
        Assert.Contains("running", ClientImport.Plan(Client("lunar"), _targets).Categories[ImportCategory.Mods].Problem);
        Assert.Null(ClientImport.Plan(Client("lunar"), _targets with { InGame = true }).Categories[ImportCategory.Mods].Problem);
    }

    [Fact]
    public void LauncherSettingsWaitWhenAskedInGame()
    {
        LunarFixture();
        var targets = _targets with { InGame = true };
        var result = ClientImport.Apply(ClientImport.Plan(Client("lunar"), targets), new[] { ImportCategory.Launcher }, targets);
        Assert.True(result.LauncherPending);
        Assert.False(File.Exists(targets.SettingsFile));

        var live = new LauncherSettings { MaxRamMb = 1024 };
        Assert.True(ClientImport.ApplyPendingLauncher(targets.Paths.BaseDirectory, live));
        Assert.Equal(6144, live.MaxRamMb);
        Assert.True(live.RamChosenByUser);
        Assert.False(ClientImport.ApplyPendingLauncher(targets.Paths.BaseDirectory, live));
    }

    [Fact]
    public void PrismAndVanillaLauncherSettings()
    {
        Write(Path.Combine(_env.AppData, "PrismLauncher", "prismlauncher.cfg"), "MaxMemAlloc=8192\nMinMemAlloc=1024\nCloseAfterLaunch=false\nJvmArgs=\"-Xmx1G\"\nMinecraftWinWidth=854\n");
        Write(Path.Combine(_env.AppData, "PrismLauncher", "accounts.json"), "MUST-NEVER-BE-READ");
        Write(Path.Combine(_env.AppData, "PrismLauncher", "instances", "One", "minecraft", "options.txt"), Modern);
        var prism = ClientImport.Plan(Client("prism"), _targets).Categories[ImportCategory.Launcher];
        Assert.Equal(8192, prism.Launcher["MaxRamMb"].GetValue<int>());
        Assert.True(prism.Launcher["KeepLauncherOpen"].GetValue<bool>());
        Assert.Contains(prism.Skipped, s => s.StartsWith("JVM arguments", StringComparison.Ordinal));

        Write(Path.Combine(_env.Minecraft, "options.txt"), Modern);
        Write(Path.Combine(_env.Minecraft, "launcher_profiles.json"), """
            {"profiles":{"a":{"lastUsed":"2020-01-01T00:00:00.000Z","javaArgs":"-Xmx2G"},"b":{"lastUsed":"2026-01-01T00:00:00.000Z","javaArgs":"-XX:+UseG1GC -Xmx4G"},"c":{"lastUsed":"2027-01-01T00:00:00.000Z"}},
             "settings":{"keepLauncherOpen":true},"authenticationDatabase":{"x":{"accessToken":"MUST-NEVER-BE-READ"}}}
            """);
        var vanilla = ClientImport.Plan(Client("vanilla"), _targets).Categories[ImportCategory.Launcher];
        Assert.Equal(4096, vanilla.Launcher["MaxRamMb"].GetValue<int>());
        Assert.DoesNotContain(vanilla.Changes.Concat(vanilla.Skipped), l => l.Contains("MUST-NEVER", StringComparison.Ordinal));
    }

    [Fact]
    public void BasicFolderImportOffersOnlyTheVanillaFiles()
    {
        var folder = Path.Combine(_root, "Salwyrr");
        Write(Path.Combine(folder, "options.txt"), Modern);
        File.WriteAllBytes(Path.Combine(folder, "servers.dat"), Servers(("S", "s.example.net")));
        var source = ClientImport.FromFolder(folder);
        Assert.True(source.Basic);
        Assert.Contains("Basic import only", source.Note);
        var plan = ClientImport.Plan(source, _targets);
        Assert.NotEmpty(plan.Categories[ImportCategory.Options].Changes);
        Assert.Single(plan.Categories[ImportCategory.Servers].Servers);
        Assert.Empty(plan.Categories[ImportCategory.Mods].Changes);
        Assert.Empty(plan.Categories[ImportCategory.Launcher].Changes);
    }

    [Fact]
    public void SameTreatsTheGamesFloatsAsEqual()
    {
        Assert.True(ClientImport.Same(JsonNode.Parse("100.0"), JsonValue.Create(100)));
        Assert.True(ClientImport.Same(JsonNode.Parse("""{"useGlobal":false,"color":-1}"""), new JsonObject { ["color"] = -1, ["useGlobal"] = false }));
        Assert.False(ClientImport.Same(JsonNode.Parse("[1,2]"), JsonNode.Parse("[1,3]")));
        Assert.False(ClientImport.Same(null, JsonValue.Create(true)));
    }
}
