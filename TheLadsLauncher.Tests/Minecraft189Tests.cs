using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>Minecraft 1.8.9 (Forge, Java 8) in the launcher: version policy, Java 8, Forge output and mods, the default
/// profile, and its shared worlds, packs and settings (from Lunar Client's 1.8 profile when Lunar is installed).</summary>
public class Minecraft189Tests
{
    // ------------------------------------------------------------------ version policy and Java

    [Fact]
    public void ForgeVersionAndExactlyJavaEight()
    {
        var profile = new LauncherProfile { MinecraftVersion = "1.8.9", FabricVersion = null };
        Assert.Equal("1.8.9-forge1.8.9-11.15.1.2318-1.8.9", GameVersionPolicy.ResolveVersionId(profile));
        Assert.True(GameVersionPolicy.UsesForge("1.8.9"));
        Assert.False(GameVersionPolicy.UsesForge("1.21.1"));
        profile.FabricVersion = "0.19.5";
        Assert.Throws<ArgumentException>(() => GameVersionPolicy.ResolveVersionId(profile));

        Assert.Equal(8, GameVersionPolicy.GetRequiredJavaMajor("1.8.9"));
        Assert.Equal(8, GameVersionPolicy.GetRequiredJavaMajor("1.8.9", 21)); // a stale profile value never raises it
        Assert.Equal(8, GameVersionPolicy.GetMaximumJavaMajor("1.8.9"));
        Assert.Null(GameVersionPolicy.GetMaximumJavaMajor("26.3"));
        // "actual < required" alone would accept Java 21 and 25, which crash Forge 1.8.9's LaunchWrapper.
        Assert.True(GameVersionPolicy.AcceptsJava("1.8.9", 8, 8));
        foreach (int? wrong in new int?[] { 7, 9, 17, 21, 25, null })
            Assert.False(GameVersionPolicy.AcceptsJava("1.8.9", 8, wrong));
        Assert.True(GameVersionPolicy.AcceptsJava("1.21.1", 21, 25)); // newer versions keep "or newer"
        Assert.False(GameVersionPolicy.AcceptsJava("26.3", 25, 21));
        Assert.Equal("Java 8 exactly", GameVersionPolicy.DescribeJava("1.8.9", 8));
        Assert.Equal("Java 21 or newer", GameVersionPolicy.DescribeJava("1.21.1", 21));
    }

    [Theory]
    [InlineData("1.8.0_504", 8)]
    [InlineData("1.8.0_51", 8)]
    [InlineData("21.0.12", 21)]
    public void JavaVersionOfAReleaseFile(string release, int major)
    {
        using var dir = new TestDirectory();
        var java = Path.Combine(dir.Path, "jre", "bin", "java.exe");
        Write(java, "");
        Write(Path.Combine(dir.Path, "jre", "release"), $"JAVA_VERSION=\"{release}\"\nOS_ARCH=\"amd64\"\n");
        Assert.Equal(major, new JavaService(new PathService(Path.Combine(dir.Path, "launcher"))).GetJavaMajorVersion(java));
    }

    [Fact]
    public void ThirtyTwoBitJavaIsNotUsed()
    {
        using var dir = new TestDirectory();
        string Exe(ushort machine)
        {
            var bytes = new byte[0x100];
            "MZ"u8.CopyTo(bytes);
            BitConverter.GetBytes(0x80).CopyTo(bytes, 0x3C);
            "PE\0\0"u8.CopyTo(bytes.AsSpan(0x80));
            BitConverter.GetBytes(machine).CopyTo(bytes, 0x84);
            var path = Path.Combine(dir.Path, $"{machine:x}.exe");
            File.WriteAllBytes(path, bytes);
            return path;
        }
        Assert.False(JavaService.Is64Bit(Exe(0x014C)));
        Assert.True(JavaService.Is64Bit(Exe(0x8664)));
        Assert.True(JavaService.Is64Bit(Path.Combine(dir.Path, "missing.exe"))); // unreadable: the version check decides
    }

    [Fact]
    public async Task JavaEightDownloadIsTheJreAndMustMatchItsChecksum()
    {
        using var dir = new TestDirectory();
        var paths = new PathService(Path.Combine(dir.Path, "launcher"));
        byte[] zip;
        using (var bytes = new MemoryStream())
        {
            using (var archive = new ZipArchive(bytes, ZipArchiveMode.Create, true))
            {
                archive.CreateEntry("jdk8u504-b01-jre/bin/java.exe");
                using var release = new StreamWriter(archive.CreateEntry("jdk8u504-b01-jre/release").Open());
                release.Write("JAVA_VERSION=\"1.8.0_504\"\n");
            }
            zip = bytes.ToArray();
        }
        var requests = new List<string>();
        Task<string> Download(string publishedSha256)
        {
            var api = JsonSerializer.Serialize(new[] { new { binary = new { package = new { link = "https://example.test/jre8.zip", checksum = publishedSha256 } } } });
            var http = new HttpClient(new Scripted(request =>
            {
                requests.Add(request.RequestUri!.AbsoluteUri);
                return request.RequestUri.Host == "api.adoptium.net" ? new StringContent(api) : new ByteArrayContent(zip);
            }));
            return new JavaService(paths, http).DownloadAndInstallAdoptiumJavaAsync(8);
        }

        await Assert.ThrowsAsync<InvalidDataException>(() => Download(new string('0', 64)));
        Assert.False(Directory.Exists(paths.GetJavaRuntimeDirectory(8)));
        var java = await Download(Convert.ToHexString(SHA256.HashData(zip)).ToLowerInvariant());
        Assert.Equal(8, new JavaService(paths).GetJavaMajorVersion(java));
        Assert.All(requests.Where(r => r.Contains("api.adoptium.net")), r => Assert.Contains("/latest/8/hotspot?os=windows&architecture=x64&image_type=jre", r));
    }

    [Fact]
    public void ForgeXmlOutputBecomesPlainLogLines()
    {
        Assert.Null(GameSession.ReadableOutput("  <log4j:Event logger=\"FML\" timestamp=\"1790783206071\" level=\"INFO\" thread=\"main\">"));
        Assert.Equal("Forge Mod Loader version 11.15.1.2318 for Minecraft 1.8.9 loading",
            GameSession.ReadableOutput("    <log4j:Message><![CDATA[Forge Mod Loader version 11.15.1.2318 for Minecraft 1.8.9 loading]]></log4j:Message>"));
        Assert.Null(GameSession.ReadableOutput("  </log4j:Event>"));
        Assert.Equal("---- Minecraft Crash Report ----", GameSession.ReadableOutput("    <log4j:Message><![CDATA[---- Minecraft Crash Report ----"));
        Assert.Equal("\tat Foo.bar(Foo.java:1)", GameSession.ReadableOutput("\tat Foo.bar(Foo.java:1)")); // inside a message: as is
        Assert.Equal("[12:00:01] [Render thread/INFO]: Setting user: Lad", GameSession.ReadableOutput("[12:00:01] [Render thread/INFO]: Setting user: Lad"));
        Assert.Null(GameSession.ReadableOutput(null));
        Assert.Null(GameSession.ReadableOutput(""));
    }

    // ------------------------------------------------------------------ profiles

    [Fact]
    public void DefaultProfileIsAddedAndSavedOnesAreRepairedToForgeAndJavaEight()
    {
        using var dir = new TestDirectory();
        var global = new SharedContentService(Path.Combine(dir.Path, "global"));
        var fresh = new ProfileService(new PathService(Path.Combine(dir.Path, "fresh")), global);
        var profile = Assert.Single(fresh.GetProfiles(), p => p.MinecraftVersion == "1.8.9");
        Assert.Equal(("1.8.9", "The Lads Client 1.8.9", (string?)null, 8, false), (profile.Id, profile.Name, profile.FabricVersion, profile.JavaMajorVersion, profile.IsIsolated));
        Assert.Equal(GameVersionPolicy.ForgeVersionId, GameVersionPolicy.ResolveVersionId(profile));
        Assert.Equal("26.3", fresh.GetActiveProfile().Id);

        // A 1.3.x profile list gains it; a hand-made 1.8.9 profile is repaired, and stays the active one.
        var upgraded = Path.Combine(dir.Path, "upgraded");
        Write(Path.Combine(upgraded, "profiles.json"), JsonSerializer.Serialize(new { ActiveProfileId = "26.3", Profiles = new[] {
            new LauncherProfile { Id = "26.3", Name = "Main", MinecraftVersion = "26.3", FabricVersion = "0.19.5", JavaMajorVersion = 25 } } }));
        Assert.Contains(new ProfileService(new PathService(upgraded), global).GetProfiles(), p => p.Id == "1.8.9" && p.JavaMajorVersion == 8);
        var handMade = Path.Combine(dir.Path, "hand-made");
        Write(Path.Combine(handMade, "profiles.json"), JsonSerializer.Serialize(new { ActiveProfileId = "mine", Profiles = new[] {
            new LauncherProfile { Id = "mine", Name = "Mine", MinecraftVersion = "1.8.9", FabricVersion = "0.19.5", JavaMajorVersion = 25 } } }));
        var repaired = new ProfileService(new PathService(handMade), global);
        Assert.Equal(("mine", (string?)null, 8), (repaired.GetActiveProfile().Id, repaired.GetActiveProfile().FabricVersion, repaired.GetActiveProfile().JavaMajorVersion));

        // An unusable active profile falls back to a Fabric profile, never to 1.8.9, even when 1.8.9 comes first.
        var broken = Path.Combine(dir.Path, "broken");
        Write(Path.Combine(broken, "profiles.json"), JsonSerializer.Serialize(new { ActiveProfileId = "broken", Profiles = new[] {
            new LauncherProfile { Id = "old-189", Name = "Old 1.8.9", MinecraftVersion = "1.8.9", FabricVersion = null, JavaMajorVersion = 8 },
            new LauncherProfile { Id = "broken", Name = "Broken", MinecraftVersion = "26.3", FabricVersion = "custom-loader", JavaMajorVersion = 25 } } }));
        Assert.Equal("26.2", new ProfileService(new PathService(broken), global).GetActiveProfile().Id);

        // A 1.8.9 profile an older launcher saved isolated (it was forced) shares its settings now, once: isolating it again sticks.
        var older = Path.Combine(dir.Path, "older");
        Write(Path.Combine(older, "profiles.json"), JsonSerializer.Serialize(new { ActiveProfileId = "26.3", Profiles = new[] {
            new LauncherProfile { Id = "26.3", Name = "Main", MinecraftVersion = "26.3", FabricVersion = "0.19.5", JavaMajorVersion = 25, IsIsolated = true },
            new LauncherProfile { Id = "1.8.9", Name = "The Lads Client 1.8.9", MinecraftVersion = "1.8.9", FabricVersion = null, JavaMajorVersion = 8, IsIsolated = true } } }));
        var migrated = new ProfileService(new PathService(older), global);
        Assert.Equal((false, true), (migrated.GetProfile("1.8.9")!.IsIsolated, migrated.GetProfile("26.3")!.IsIsolated));
        migrated.GetProfile("1.8.9")!.IsIsolated = true;
        migrated.SaveProfiles();
        Assert.True(new ProfileService(new PathService(older), global).GetProfile("1.8.9")!.IsIsolated);
    }

    // ------------------------------------------------------------------ shared worlds, packs and settings

    /// <summary>1.8.9 shares saves, resourcepacks and shaderpacks like every version: what its game folder held is moved into the
    /// shared folders (a name already taken there is kept under another name), and nothing shared is changed or lost.</summary>
    [Fact]
    public async Task Minecraft189IsLinkedToTheSharedFoldersAndItsOwnWorldsAndPacksMoveIn()
    {
        using var l = new Launcher();
        var shared = l.SharedTree();
        Write(Path.Combine(l.Game189, "saves", "Old 1.8.9 World", "level.dat"), "1.8.9 world");
        Write(Path.Combine(l.Game189, "saves", "Modern World", "level.dat"), "a 1.8.9 world with a taken name");
        Write(Path.Combine(l.Game189, "resourcepacks", "faithful-1.8.zip"), "pack_format 1");

        await l.Profiles.PrepareAllProfilesSharedContentAsync();
        await l.Profiles.PrepareProfileEnvironmentAsync(l.Profiles.GetProfile("1.8.9")!, null);

        Assert.All(SharedContentService.SharedFolders, folder =>
            Assert.Equal(SafeFileOps.GetFinalPath(l.G(folder)), SafeFileOps.GetFinalPath(Path.Combine(l.Game189, folder))));
        Assert.Equal("1.8.9 world", File.ReadAllText(l.G("saves", "Old 1.8.9 World", "level.dat")));
        Assert.Equal("pack_format 1", File.ReadAllText(l.G("resourcepacks", "faithful-1.8.zip")));
        Assert.Contains(Directory.EnumerateFiles(l.G("saves"), "level.dat", SearchOption.AllDirectories),
            file => File.ReadAllText(file) == "a 1.8.9 world with a taken name");
        Assert.All(shared, entry => Assert.Equal(entry.Value, Sha(l.G(entry.Key))));
        Assert.Equal(Sha(l.G("servers.dat")), Sha(Path.Combine(l.Game189, "servers.dat")));
    }

    [Fact]
    public async Task Minecraft189RefusesTheGlobalFolderAndAFabricProfilesFolder()
    {
        using var l = new Launcher();
        var shared = l.SharedTree();
        var direct = new LauncherProfile { Name = "1.8.9 in .minecraft", MinecraftVersion = "1.8.9", FabricVersion = null, CustomGameDir = l.Global };
        var refused = await Assert.ThrowsAsync<InvalidOperationException>(() => l.Profiles.PrepareProfileEnvironmentAsync(direct, null));
        Assert.Contains("Forge and Fabric mods cannot share a mods folder", refused.Message);

        var newer = l.Paths.GetProfileDirectory(l.Profiles.GetProfile("26.3")!);
        var inNewer = new LauncherProfile { Name = "1.8.9 in the 26.3 folder", MinecraftVersion = "1.8.9", FabricVersion = null, CustomGameDir = newer };
        Assert.Contains("'The Lads Client 26.3 (Primary)'", (await Assert.ThrowsAsync<InvalidOperationException>(() => l.Profiles.PrepareProfileEnvironmentAsync(inNewer, null))).Message);
        Assert.False(Directory.Exists(newer));
        Assert.Equal(shared, l.SharedTree());
    }

    [Fact]
    public async Task InGameWorldPickersList189WorldsAndNewerOnes()
    {
        using var l = new Launcher();
        var modern = l.Profiles.GetProfile("26.3")!;
        await l.Profiles.PrepareProfileEnvironmentAsync(modern, null);
        await l.Profiles.PrepareProfileEnvironmentAsync(l.Profiles.GetProfile("1.8.9")!, null);
        List<WorldSource> Sources(string game) => JsonSerializer.Deserialize<List<WorldSource>>(File.ReadAllText(Path.Combine(game, WorldCatalogService.GameSourcesFile)))!;
        Assert.Contains(Sources(l.Paths.GetProfileDirectory(modern)), s => SafeFileOps.PathsEqual(s.GameDirectory, l.Game189) && s.Version == "1.8.9");
        Assert.Contains(Sources(l.Game189), s => s.Version == "26.3");
    }

    /// <summary>Without Lunar, 1.8.9 follows the launcher's shared options.txt with keybinds in 1.8 key codes, and after the game
    /// only the shared settings and the keybinds (in modern names) go back: never 1.8's own formats.</summary>
    [Fact]
    public async Task Minecraft189UsesTheSharedOptionsAndOnlySharedKeysAndKeybindsGoBack()
    {
        using var l = new Launcher();
        var profile = l.Profiles.GetProfile("1.8.9")!;
        var options = Path.Combine(l.Game189, "options.txt");
        File.WriteAllText(l.Paths.SharedOptionsFile, "version:4671\nlang:en_us\nfov:0.25\nresourcePacks:[\"vanilla\",\"file/modern.zip\"]\n"
            + "key_key.attack:key.mouse.left\nkey_key.jump:key.keyboard.space\n");

        await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
        var instance = GameOptionsService.ParseOptions(File.ReadAllText(options));
        Assert.Equal(("-100", "57", "0.25"), (instance["key_key.attack"], instance["key_key.jump"], instance["fov"]));

        // As 1.8.9 saves it on exit.
        File.WriteAllText(options, "version:0\nlang:en_US\nresourcePacks:[\"faithful-1.8.zip\"]\nfancyGraphics:true\nfov:0.5\nguiScale:3\n"
            + "key_key.attack:-99\nkey_key.drop:0\n");
        await l.Profiles.SyncProfileToSharedAsync(profile);
        var shared = GameOptionsService.ParseOptions(File.ReadAllText(l.Paths.SharedOptionsFile));
        Assert.Equal(("4671", "en_us", "[\"vanilla\",\"file/modern.zip\"]", false), (shared["version"], shared["lang"], shared["resourcePacks"], shared.ContainsKey("fancyGraphics")));
        Assert.Equal(("0.5", "3", "key.mouse.right", "key.keyboard.unknown"), (shared["fov"], shared["guiScale"], shared["key_key.attack"], shared["key_key.drop"]));
    }

    /// <summary>With Lunar installed, 1.8.9 takes its settings from Lunar's 1.8 profile (modern key names, translated) and
    /// Lunar's optionsof.txt once; Lunar's files are only read, and nothing goes back to the launcher's shared copy.</summary>
    [Fact]
    public async Task Minecraft189UsesLunarsSettingsWhenInstalledAndNeverWritesThere()
    {
        using var l = new Launcher();
        var profile = l.Profiles.GetProfile("1.8.9")!;
        var options = Path.Combine(l.Game189, "options.txt");
        var optiFine = Path.Combine(l.Game189, "optionsof.txt");
        var lunar18 = Path.Combine(l.Lunar, "profiles", "1.8");
        Write(Path.Combine(lunar18, "options.txt"), "version:3700\nlang:en_ca\nfov:0.75\nkey_key.attack:key.mouse.left\nkey_key.sprint:key.keyboard.left.control\n");
        Write(Path.Combine(lunar18, "optionsof.txt"), "ofFastRender:true\n");
        Write(Path.Combine(lunar18, "resourcepacks", "lunar-pack.zip"), "a 1.8 pack");
        Dictionary<string, string> LunarTree() => Directory.EnumerateFiles(l.Lunar, "*", SearchOption.AllDirectories).ToDictionary(file => file, Sha);
        var lunarBefore = LunarTree();

        await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
        var instance = GameOptionsService.ParseOptions(File.ReadAllText(options));
        Assert.Equal(("-100", "29", "0.75"), (instance["key_key.attack"], instance["key_key.sprint"], instance["fov"]));
        Assert.Equal("ofFastRender:true\n", File.ReadAllText(optiFine));

        // The game's own changes: OptiFine's are kept (copied once), Lunar's shared settings come back at the next launch.
        File.WriteAllText(optiFine, "ofFastRender:false\n");
        File.WriteAllText(options, "fov:1.0\nkey_key.attack:-99\n");
        await l.Profiles.SyncProfileToSharedAsync(profile);
        await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
        Assert.Equal("ofFastRender:false\n", File.ReadAllText(optiFine));
        Assert.Equal(("0.75", "-100"), (GameOptionsService.ParseOptions(File.ReadAllText(options))["fov"], GameOptionsService.ParseOptions(File.ReadAllText(options))["key_key.attack"]));
        Assert.Equal(lunarBefore, LunarTree());
        Assert.Equal(Launcher.SharedOptions, File.ReadAllText(l.Paths.SharedOptionsFile));
    }

    // ------------------------------------------------------------------ Forge mods

    [Fact]
    public async Task ForgeModsAreReadFromMcmodInfoAndNothingCrashes()
    {
        using var box = new ModSandbox();
        File.WriteAllBytes(box.Mod("examplemod-1.0.jar"), ForgeJar("[{\"modid\": \"examplemod\", \"name\": \"Example Mod\", \"version\": \"1.0\", \"authorList\": [\"Ann\"], \"description\": \"line one\nline two\",},]"));
        File.WriteAllBytes(box.Mod("second.jar"), ForgeJar("{\"modListVersion\": 2, \"modList\": [{\"modid\": \"second\", \"name\": \"Second\"}]}"));
        File.WriteAllBytes(box.Mod("OptiFine_1.8.9_HD_U_M5.jar"), ModSandbox.PlainJar());
        File.WriteAllBytes(box.Mod("OldTweak.jar.disabled"), ModSandbox.PlainJar());
        File.WriteAllBytes(box.Mod("fabricmod.jar"), ModSandbox.Jar("fabricmod"));
        File.WriteAllBytes(box.Mod("broken.jar"), new byte[] { 1, 2, 3 });

        var inventory = await new ModInventoryService().BuildAsync(box.Bundle, box.Game, "1.8.9");
        await new ModInventoryService().WriteSnapshotAsync(inventory);
        ModInventoryEntry Row(ModInventory list, string id) => list.Entries.Single(e => e.Id == id);
        var example = Row(inventory, "examplemod");
        Assert.Equal(("Example Mod", "1.0", ModEntryStatus.Installed), (example.DisplayName, example.Version, example.Status));
        Assert.Equal(new[] { "Ann" }, example.Authors);
        Assert.Equal(ModEntryStatus.Installed, Row(inventory, "second").Status);
        // OptiFine and coremods have no mcmod.info, and Forge still loads them.
        Assert.Equal(ModEntryStatus.Installed, Row(inventory, "OptiFine_1.8.9_HD_U_M5").Status);
        Assert.Equal((ModEntryStatus.Disabled, false), (Row(inventory, "OldTweak").Status, Row(inventory, "OldTweak").RequestedEnabled));
        Assert.Equal("Not a loadable Forge mod: it is a Fabric mod. Delete or replace it.", Row(inventory, "fabricmod.jar").Note);
        Assert.Equal(ModEntryStatus.Invalid, Row(inventory, "broken.jar").Status);
        Assert.Equal(("Forge", "11.15.1.2318"), (Row(inventory, "forge").DisplayName, Row(inventory, "forge").Version));
        Assert.Equal("8", Row(inventory, "java").Version);
        Assert.DoesNotContain(inventory.Entries, e => e.Id == "fabricloader");

        // In a Fabric profile a Forge jar reads exactly as before.
        var fabric = await new ModInventoryService().BuildAsync(box.Bundle, box.Game, "26.3");
        Assert.Equal("Not a loadable Fabric mod: it has no fabric.mod.json. Delete or replace it.", Row(fabric, "examplemod-1.0.jar").Note);
        Assert.Equal(ModEntryStatus.Installed, Row(fabric, "fabricmod").Status);

        // Switching off works for Forge jars with and without mcmod.info.
        var state = new ModStateService(_ => false);
        var off = await state.ApplyAsync(box.Game, inventory, state.Plan(inventory, new[] { "examplemod", "OptiFine_1.8.9_HD_U_M5" }, false));
        Assert.True(off.Success, off.Message);
        Assert.True(File.Exists(box.Mod("examplemod-1.0.jar.disabled")));
        Assert.True(File.Exists(box.Mod("OptiFine_1.8.9_HD_U_M5.jar.disabled")));
    }

    [Fact]
    public async Task ForgePackInstallsAndFabricOrUnreadableJarsNeverFailIt()
    {
        using var box = new ModSandbox();
        var forgeMod = ForgeJar("[{\"modid\": \"examplemod\", \"name\": \"Example Mod\", \"version\": \"1.0\"}]");
        box.WriteManifest("1.8.9", new[] { box.Pin("examplemod", forgeMod) });
        // Forge never loads these: a Fabric mod whose dependency is missing, and a jar that is not a zip.
        File.WriteAllBytes(box.Mod("fabricmod.jar"), ModSandbox.Jar("fabricmod", depends: new() { ["fabric-api"] = "*" }));
        File.WriteAllBytes(box.Mod("broken.jar"), new byte[] { 1, 2, 3 });
        File.WriteAllBytes(box.Mod(OptiFineInstaller.M5.FileName), OptiFineJar());
        box.Choose((OptiFineInstaller.ModId, false)); // saved while the game was running: applied at the next launch

        await box.Install("1.8.9");

        Assert.Equal(forgeMod, File.ReadAllBytes(box.Mod("examplemod-1.0.0.jar")));
        Assert.Equal(ModSandbox.Sha(forgeMod), box.ReadReceipt()["examplemod"]);
        Assert.True(File.Exists(box.Mod("fabricmod.jar")) && File.Exists(box.Mod("broken.jar")));
        Assert.True(File.Exists(box.Mod(OptiFineInstaller.M5.FileName + ".disabled")));
        var inventory = await new ModInventoryService().BuildAsync(box.Bundle, box.Game, "1.8.9");
        var example = inventory.Entries.Single(e => e.Id == "examplemod");
        Assert.Equal((ModOwnership.Pack, ModEntryStatus.Installed), (example.Ownership, example.Status));
        var optiFine = inventory.Entries.Single(e => e.Id == OptiFineInstaller.ModId);
        Assert.Equal((ModOwnership.Pack, ModEntryStatus.Disabled, false), (optiFine.Ownership, optiFine.Status, optiFine.RequestedEnabled));
    }

    [Fact]
    public async Task ShippedManifestFor189LoadsAndTheOptiFineJarIsNeverShipped()
    {
        // The launcher's own game-mods folder (copied next to the tests); only read.
        var bundle = AppContext.BaseDirectory;
        var manifest = JsonSerializer.Deserialize<ClientModInstaller.Manifest>(File.ReadAllText(Path.Combine(bundle, "game-mods", "1.8.9", "client-mods.json")),
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true })!;
        Assert.Equal(("1.8.9", 2), (manifest.MinecraftVersion, manifest.Mods.Count));
        Assert.DoesNotContain(Directory.EnumerateFiles(Path.Combine(bundle, "game-mods"), "*", SearchOption.AllDirectories),
            file => Path.GetFileName(file).Contains("optifine", StringComparison.OrdinalIgnoreCase)
                || (file.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) && FabricModMetadata.ReadForgeJar(file)?.Id == OptiFineInstaller.ModId));

        using var box = new ModSandbox();
        box.Choose(("resourcify", false), ("essential", false));
        await ClientModInstaller.InstallAsync(bundle, box.Game, "1.8.9", httpClient: box.Client());
        Assert.Empty(box.Requests);
        var inventory = await new ModInventoryService().BuildAsync(bundle, box.Game, "1.8.9");
        var pending = Assert.Single(inventory.Entries, e => e.Status == ModEntryStatus.PendingDownload && e.Ownership != ModOwnership.Core);
        Assert.Equal((OptiFineInstaller.ModId, "OptiFine", ModOwnership.Pack, true), (pending.Id, pending.DisplayName, pending.Ownership, pending.CanToggle));
        Assert.Equal(OptiFineInstaller.M5.FileName, pending.FileName);

        // The Core that TheLadsCore's deploy staged, when it did: the Forge mod, installed the way a 1.8.9 launch installs it.
        var staged = Path.Combine(bundle, "game-mods", "1.8.9", "theladscore.jar");
        var core = inventory.Entries.Single(e => e.Id == BundledModInstaller.CoreModId);
        if (!File.Exists(staged))
        {
            Assert.Equal(ModEntryStatus.Invalid, core.Status); // a 1.8.9 launch then fails: never Forge without the Core
            return;
        }
        var info = FabricModMetadata.ReadForgeJar(staged)!;
        Assert.Equal((BundledModInstaller.CoreModId, "1.8.9", true), (info.Id, info.McVersion, info.Forge));
        Assert.Equal((ModOwnership.Core, ModEntryStatus.PendingDownload, info.Version), (core.Ownership, core.Status, core.Version));
        Assert.True(await BundledModInstaller.InstallAsync(bundle, box.Game, "1.8.9"));
        Assert.Equal(File.ReadAllBytes(staged), File.ReadAllBytes(box.Mod("theladscore.jar")));
    }

    /// <summary>Essential's Forge jar is only its loader, without mcmod.info: it is still the "essential" mod, never a nameless coremod.</summary>
    [Fact]
    public void EssentialsLoaderJarReadsAsTheEssentialMod()
    {
        using var jar = new MemoryStream();
        using (var zip = new ZipArchive(jar, ZipArchiveMode.Create, leaveOpen: true))
            zip.CreateEntry("gg/essential/loader/stage0/EssentialSetupTweaker.class");
        jar.Position = 0;
        Assert.Equal(("essential", true), (FabricModMetadata.ReadForgeJar(jar)!.Id, FabricModMetadata.ReadForgeJar(new MemoryStream(jar.ToArray()))!.Forge));
    }

    // ------------------------------------------------------------------ the Lads Core on Forge

    [Fact]
    public async Task ForgeCoreIsInstalledInto189AndOlderCopiesAreMovedAside()
    {
        using var box = new ModSandbox();
        var core = ForgeCore();
        box.WriteCore("1.8.9", core);
        var older = ForgeCore("1.3.9");
        File.WriteAllBytes(box.Mod("theladscore-1.3.9.jar"), older);
        File.WriteAllBytes(box.Mod("theladscore.jar.disabled"), ForgeCore("1.3.8"));
        File.WriteAllBytes(box.Mod("examplemod.jar"), ForgeJar("[{\"modid\": \"examplemod\", \"mcversion\": \"1.8.9\"}]"));
        File.WriteAllBytes(box.Mod(OptiFineInstaller.M5.FileName), OptiFineJar());

        Assert.True(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "1.8.9"));

        Assert.Equal(core, File.ReadAllBytes(box.Mod("theladscore.jar")));
        Assert.Equal(new[] { OptiFineInstaller.M5.FileName, "examplemod.jar", "theladscore.jar" }, Names(box.Mods));
        var backups = Directory.GetFiles(Path.Combine(box.Game, "mods-disabled"), "*", SearchOption.AllDirectories);
        Assert.Equal(new[] { "theladscore-1.3.9.jar", "theladscore.jar.disabled" }, backups.Select(Path.GetFileName).Order(StringComparer.Ordinal));
        Assert.Equal(older, File.ReadAllBytes(backups.Single(b => b.EndsWith("1.3.9.jar", StringComparison.Ordinal))));
        Assert.False(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "1.8.9")); // installed and verified: nothing to do

        var row = (await new ModInventoryService().BuildAsync(box.Bundle, box.Game, "1.8.9")).Entries.Single(e => e.Id == BundledModInstaller.CoreModId);
        Assert.Equal((ModOwnership.Core, ModEntryStatus.Installed, "The Lads Core", "1.4.0"), (row.Ownership, row.Status, row.DisplayName, row.Version));
    }

    [Fact]
    public async Task TheOtherLoadersCoreIsNeverInstalledEitherWay()
    {
        using var box = new ModSandbox();
        box.WriteCore("1.8.9", ModSandbox.Core("1.8.9"));
        box.WriteCore("26.3", ForgeCore());
        foreach (var (version, reason) in new[] { ("1.8.9", "is the Fabric Lads Core, but Minecraft 1.8.9 runs on Forge"),
                     ("26.3", "is the Forge Lads Core, but Minecraft 26.3 runs on Fabric") })
            Assert.Contains(reason, (await Assert.ThrowsAsync<InvalidDataException>(() => BundledModInstaller.InstallAsync(box.Bundle, box.Game, version))).Message);
        Assert.Empty(Directory.GetFiles(box.Mods));
        // Neither is taken for the version's Core on the Mods page.
        foreach (var version in new[] { "1.8.9", "26.3" })
            Assert.Equal(ModEntryStatus.Invalid, (await new ModInventoryService().BuildAsync(box.Bundle, box.Game, version)).Entries
                .Single(e => e.Id == BundledModInstaller.CoreModId && e.Ownership == ModOwnership.Core).Status);

        // The 1.8.9 Core names 1.8.9 in mcmod.info.
        box.WriteCore("1.8.9", ForgeCore(mcversion: "1.8.8"));
        Assert.Contains("mcversion '1.8.9'", (await Assert.ThrowsAsync<InvalidDataException>(() => BundledModInstaller.InstallAsync(box.Bundle, box.Game, "1.8.9"))).Message);

        // A Fabric Core in a 1.8.9 profile under the Core's file name is never overwritten, and the message says why.
        box.WriteCore("1.8.9", ForgeCore());
        var fabricCore = ModSandbox.Core("26.3");
        File.WriteAllBytes(box.Mod("theladscore.jar"), fabricCore);
        var kept = await Assert.ThrowsAsync<IOException>(() => BundledModInstaller.InstallAsync(box.Bundle, box.Game, "1.8.9"));
        Assert.Contains("is the Fabric Lads Core, but Minecraft 1.8.9 runs on Forge. Move it aside manually", kept.Message);
        Assert.Equal(fabricCore, File.ReadAllBytes(box.Mod("theladscore.jar")));
    }

    [Fact]
    public async Task ForgeCoreCopiesAreSwitchedOffAndBackOnLikeTheFabricCore()
    {
        using var box = new ModSandbox();
        var core = ForgeCore();
        box.WriteCore("1.8.9", core);
        Assert.True(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "1.8.9"));
        File.WriteAllBytes(box.Mod("lads-core-copy.jar"), ForgeCore("1.3.9"));

        // Switched off (a choice saved while the game ran): the next launch keeps one disabled Core and sets the copy aside.
        box.Choose((BundledModInstaller.CoreModId, false));
        Assert.True(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "1.8.9"));
        Assert.Equal(new[] { "theladscore.jar.disabled" }, Names(box.Mods));
        Assert.Equal(core, File.ReadAllBytes(box.Mod("theladscore.jar.disabled")));
        Assert.Single(Directory.GetFiles(Path.Combine(box.Game, "mods-disabled"), "lads-core-copy.jar", SearchOption.AllDirectories));
        Assert.False(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "1.8.9"));

        // Back on, then off again, from the Mods page: the jar is renamed now and the next launch agrees.
        var state = new ModStateService(_ => false);
        foreach (var on in new[] { true, false })
        {
            var inventory = await new ModInventoryService().BuildAsync(box.Bundle, box.Game, "1.8.9");
            Assert.Equal((ModOwnership.Core, on ? ModEntryStatus.Disabled : ModEntryStatus.Installed),
                (inventory.Entries.Single(e => e.Id == BundledModInstaller.CoreModId).Ownership, inventory.Entries.Single(e => e.Id == BundledModInstaller.CoreModId).Status));
            var plan = state.Plan(inventory, new[] { BundledModInstaller.CoreModId }, on);
            Assert.Equal(!on, plan.Warnings.Contains(ModStateService.CoreDisableWarning));
            Assert.True((await state.ApplyAsync(box.Game, inventory, plan)).Success);
            Assert.False(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "1.8.9"));
            Assert.Equal(new[] { on ? "theladscore.jar" : "theladscore.jar.disabled" }, Names(box.Mods));
            Assert.Equal(core, File.ReadAllBytes(box.Mod(on ? "theladscore.jar" : "theladscore.jar.disabled")));
        }
    }

    [Fact]
    public async Task The189ModsPageListsTheForgeCoreAndItsLadsModules()
    {
        using var box = new ModSandbox();
        box.WriteCore("1.8.9", ForgeCore());
        box.WriteManifest("1.8.9", Array.Empty<ClientModInstaller.Entry>());
        Task<ModInventory> Inventory() => new ModInventoryService().BuildAsync(box.Bundle, box.Game, "1.8.9");
        static ModInventoryEntry Row(ModInventory inventory, string id) => inventory.Entries.Single(e => e.Id == id);

        // Before the first launch: the bundled Core, read from its mcmod.info, and the placeholder for the module list.
        var fresh = await Inventory();
        var core = Row(fresh, BundledModInstaller.CoreModId);
        Assert.Equal((ModOwnership.Core, ModEntryStatus.PendingDownload, "The Lads Core", "1.4.0", "Installed at the next launch"),
            (core.Ownership, core.Status, core.DisplayName, core.Version, core.Note));
        Assert.Equal("Launch this profile once to list Lads modules", Row(fresh, ModInventoryService.CatalogPlaceholderId).ToggleBlockedReason);

        // After a launch, from the catalog the 1.8.9 Core writes: its modules, as on every other version.
        Assert.True(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "1.8.9"));
        File.WriteAllText(Path.Combine(box.Game, "lads-core-catalog.json"), JsonSerializer.Serialize(new
        {
            schema = 1, coreVersion = "1.4.0", minecraftVersion = "1.8.9", writtenAt = "2026-09-30T21:25:31Z",
            modules = new object[]
            {
                new { name = "FPS", description = "Frame counter", category = "HUD", support = "builtIn", label = "Built in", detail = "", externalModId = (string?)null, enabled = true, toggleable = true },
                new { name = "Zoom", description = "Zoom key", category = "General", support = "pending", label = "Coming soon", detail = "Not connected to this game version yet", externalModId = (string?)null, enabled = false, toggleable = false },
                new { name = "Performance", description = "", category = "Performance", support = "unavailable", label = "Unavailable", detail = "Built on Sodium, which The Lads Client does not include for Minecraft 1.8.9.", externalModId = (string?)null, enabled = false, toggleable = false }
            }
        }));
        var listed = await Inventory();
        Assert.DoesNotContain(listed.Entries, e => e.Id == ModInventoryService.CatalogPlaceholderId);
        Assert.Equal(ModEntryStatus.Installed, Row(listed, BundledModInstaller.CoreModId).Status);
        var fps = Row(listed, "FPS");
        Assert.Equal((ModOwnership.NativeModule, ModEntryStatus.Installed, true), (fps.Ownership, fps.Status, fps.CanToggle));
        Assert.Equal((ModEntryStatus.Unavailable, false), (Row(listed, "Zoom").Status, Row(listed, "Zoom").CanToggle));
        Assert.Contains("Sodium", Row(listed, "Performance").Note);
        Assert.Equal(new[] { "FPS", "Performance", "Zoom", BundledModInstaller.CoreModId },
            ModInventoryView.Filter(listed, ModListFilter.LadsModules, null).Select(r => r.Entry.Id).Order(StringComparer.Ordinal));

        // Switched on the Mods page as on any version: saved in the profile's thelads_config.json for the Core.
        var result = await new ModStateService(_ => false).SetNativeModuleAsync(box.Game, "FPS", false);
        Assert.True(result.Success, result.Message);
        Assert.Equal(ModEntryStatus.Disabled, Row(await Inventory(), "FPS").Status);
    }

    [Fact]
    public async Task LaunchOf189PassesTheFabricGuardAndInstallsTheForgeCore()
    {
        using var l = new Launcher();
        var bundle = Path.Combine(l.Root, "bundle");
        var core = ForgeCore();
        Directory.CreateDirectory(Path.Combine(bundle, "game-mods", "1.8.9"));
        File.WriteAllBytes(Path.Combine(bundle, "game-mods", "1.8.9", "theladscore.jar"), core);
        var service = new LaunchService(l.Paths, l.Profiles, new JavaService(l.Paths), new AuthService(l.Paths), bundle, l.Shared);

        // No client-mods.json in this bundle: the launch stops at the pack, after the Core step and before any download.
        var stopped = await Assert.ThrowsAsync<FileNotFoundException>(() => service.LaunchAsync(l.Profiles.GetProfile("1.8.9")!, "LadsQA", new LauncherSettings()));
        Assert.EndsWith("client-mods.json", stopped.FileName);
        Assert.Equal(core, File.ReadAllBytes(Path.Combine(l.Game189, "mods", "theladscore.jar")));
        // A Fabric version without a Fabric loader is still refused.
        var noLoader = new LauncherProfile { Id = "no-loader", Name = "No loader", MinecraftVersion = "26.3", FabricVersion = null, JavaMajorVersion = 25 };
        Assert.Contains("requires Fabric", (await Assert.ThrowsAsync<InvalidOperationException>(() => service.LaunchAsync(noLoader, "LadsQA", new LauncherSettings()))).Message);
    }

    // ------------------------------------------------------------------ OptiFine

    [Fact]
    public void OptiFineLinkIsReadFromTheDownloadPage()
    {
        const string file = "OptiFine_1.8.9_HD_U_M5.jar";
        // As optifine.net serves it (spike S2), and with an HTML-escaped ampersand.
        Assert.Equal("https://optifine.net/downloadx?f=OptiFine_1.8.9_HD_U_M5.jar&x=0ab5fb1afb34102740d65d8e87bc754b", OptiFineInstaller.DownloadUrl(
            "<td><a href='downloadx?f=OptiFine_1.8.9_HD_U_M5.jar&x=0ab5fb1afb34102740d65d8e87bc754b' onclick='onDownload()'>Download</a></td>", file));
        Assert.Equal("https://optifine.net/downloadx?f=OptiFine_1.8.9_HD_U_M5.jar&x=ABC123",
            OptiFineInstaller.DownloadUrl("<a href=\"downloadx?f=OptiFine_1.8.9_HD_U_M5.jar&amp;x=ABC123\">", file));
        // Another file, a link elsewhere, a token that is not one, or no link at all: nothing.
        Assert.Null(OptiFineInstaller.DownloadUrl("<a href='downloadx?f=OptiFine_1.8.9_HD_U_L5.jar&x=0ab5'>", file));
        Assert.Null(OptiFineInstaller.DownloadUrl("<a href='https://example.test/downloadx?f=OptiFine_1.8.9_HD_U_M5.jar&x=0ab5'>", file));
        Assert.Null(OptiFineInstaller.DownloadUrl("<a href='downloadx?f=OptiFine_1.8.9_HD_U_M5.jar&x=zz'>", file));
        Assert.Null(OptiFineInstaller.DownloadUrl("<html>Checking your browser</html>", file));
    }

    [Fact]
    public async Task OptiFineIsDownloadedWithAFreshTokenVerifiedCachedAndCopiedIntoMods()
    {
        using var box = new ModSandbox();
        var jar = OptiFineJar();
        var site = new OptiFineNet(jar);
        var launcher = Path.Combine(box.Root, "launcher");
        var cached = Path.Combine(launcher, "cache", "optifine", OptiFineInstaller.M5.FileName);
        var installed = box.Mod(OptiFineInstaller.M5.FileName);
        var messages = new List<string>();
        Task<string?> Install() => OptiFineInstaller.InstallAsync(launcher, box.Game, Pin(jar), messages.Add, default, new HttpClient(site));

        Assert.Null(await Install());
        Assert.Equal(jar, File.ReadAllBytes(installed));
        Assert.Equal(jar, File.ReadAllBytes(cached));
        Assert.Equal(new[] { site.Page, site.JarUrl(1) }, site.Requests);

        // Installed and verified: nothing to do. Removed from Mods: copied from the cache, still without a download.
        Assert.Null(await Install());
        File.Delete(installed);
        Assert.Null(await Install());
        Assert.Equal(jar, File.ReadAllBytes(installed));
        Assert.Equal(2, site.Requests.Count);
        Assert.Contains(messages, m => m.Contains("from the launcher cache"));

        // A damaged cache and a damaged copy in Mods: a new download with the page's new token; the damaged copy is kept aside.
        File.WriteAllBytes(cached, new byte[jar.Length]);
        File.WriteAllBytes(installed, new byte[] { 9, 9, 9 });
        Assert.Null(await Install());
        Assert.Equal(new[] { site.Page, site.JarUrl(2) }, site.Requests.Skip(2));
        Assert.Equal(jar, File.ReadAllBytes(installed));
        Assert.Equal(jar, File.ReadAllBytes(cached));
        Assert.Equal(new byte[] { 9, 9, 9 }, File.ReadAllBytes(Assert.Single(Directory.GetFiles(box.Cache, "previous-optifine-*.jar"))));
        Assert.DoesNotContain(Directory.EnumerateFiles(box.Root, "*.tmp", SearchOption.AllDirectories), _ => true);

        // The Mods page lists it as the launcher's OptiFine, and the snapshot records it as loaded.
        var inventory = await new ModInventoryService().BuildAsync(box.Bundle, box.Game, "1.8.9");
        var row = inventory.Entries.Single(e => e.Id == OptiFineInstaller.ModId);
        Assert.Equal(("OptiFine", "1.8.9_HD_U_M5", ModOwnership.Pack, ModEntryStatus.Installed), (row.DisplayName, row.Version, row.Ownership, row.Status));
        Assert.Contains("optifine.net", row.Note);
        Assert.Contains(OptiFineInstaller.ModId, ModInventoryView.EnabledJarIds(inventory));
    }

    [Theory]
    [InlineData("web page", "stale token")]
    [InlineData("does not match the pinned SHA-256", "wrong bytes")]
    [InlineData("incomplete", "short")]
    [InlineData("no link", "no link")]
    [InlineData("503", "offline")]
    public async Task OptiFineFailuresOnlyWarnAndNeverLeaveAnUnverifiedJarInMods(string reason, string failure)
    {
        using var box = new ModSandbox();
        var jar = OptiFineJar();
        var site = new OptiFineNet(jar) { Failure = failure };
        var launcher = Path.Combine(box.Root, "launcher");
        File.WriteAllBytes(box.Mod(OptiFineInstaller.M5.FileName), new byte[] { 1 }); // damaged

        var warning = await OptiFineInstaller.InstallAsync(launcher, box.Game, Pin(jar), httpClient: new HttpClient(site));

        Assert.NotNull(warning);
        Assert.Contains(reason, warning);
        Assert.Contains("Minecraft starts without it", warning);
        Assert.Empty(Directory.GetFiles(box.Mods));
        Assert.False(Directory.Exists(Path.Combine(launcher, "cache", "optifine")) && Directory.EnumerateFiles(Path.Combine(launcher, "cache", "optifine")).Any());
        Assert.Single(Directory.GetFiles(box.Cache, "previous-optifine-*.jar"));
        // Only the launch's own cancellation stops a launch.
        using var cancelled = new CancellationTokenSource();
        cancelled.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
            OptiFineInstaller.InstallAsync(launcher, box.Game, Pin(jar), null, cancelled.Token, new HttpClient(site)));
    }

    [Fact]
    public async Task AJarThatCannotBeMovedAsideIsReportedAsLeftInMods()
    {
        using var box = new ModSandbox();
        var jar = OptiFineJar();
        File.WriteAllBytes(box.Mod(OptiFineInstaller.M5.FileName), new byte[] { 1 }); // damaged
        File.WriteAllText(box.Cache, "a file where the backup folder goes");
        var warning = await OptiFineInstaller.InstallAsync(Path.Combine(box.Root, "launcher"), box.Game, Pin(jar),
            httpClient: new HttpClient(new OptiFineNet(jar)));
        Assert.Contains($"'{OptiFineInstaller.M5.FileName}' could not be checked or moved out of Mods", warning);
        Assert.DoesNotContain("starts without it", warning);
        Assert.True(File.Exists(box.Mod(OptiFineInstaller.M5.FileName)));
    }

    [Fact]
    public async Task OptiFineIsSwitchedOnTheModsPageAndYourOwnOptiFineIsKept()
    {
        using var box = new ModSandbox();
        var jar = OptiFineJar();
        var site = new OptiFineNet(jar);
        var launcher = Path.Combine(box.Root, "launcher");
        var state = new ModStateService(_ => false);
        Task<string?> Install() => OptiFineInstaller.InstallAsync(launcher, box.Game, Pin(jar), httpClient: new HttpClient(site));
        Task<ModInventory> Inventory() => new ModInventoryService().BuildAsync(box.Bundle, box.Game, "1.8.9");
        async Task Switch(bool on)
        {
            var inventory = await Inventory();
            var result = await state.ApplyAsync(box.Game, inventory, state.Plan(inventory, new[] { OptiFineInstaller.ModId }, on));
            Assert.True(result.Success, result.Message);
        }

        // Switched off before it was ever downloaded: nothing is downloaded.
        await Switch(false);
        Assert.Equal(ModEntryStatus.NotDownloaded, (await Inventory()).Entries.Single(e => e.Id == OptiFineInstaller.ModId).Status);
        Assert.Null(await Install());
        Assert.Empty(site.Requests);
        Assert.Empty(Directory.GetFiles(box.Mods));

        await Switch(true);
        Assert.Null(await Install());
        Assert.True(File.Exists(box.Mod(OptiFineInstaller.M5.FileName)));

        // Switched off once installed: the jar is renamed and stays off at the next launch.
        await Switch(false);
        Assert.Null(await Install());
        Assert.Equal(new[] { box.Mod(OptiFineInstaller.M5.FileName + ".disabled") }, Directory.GetFiles(box.Mods));
        Assert.Equal(2, site.Requests.Count);

        // Your own OptiFine (another build, another name) is used instead: the launcher's leaves Mods, so Forge never gets two.
        await Switch(true);
        var mine = box.Mod("OptiFine_1.8.9_HD_U_L5.jar");
        File.WriteAllBytes(mine, OptiFineJar("OptiFine 1.8.9_HD_U_L5"));
        Assert.Null(await Install());
        Assert.Equal(new[] { mine }, Directory.GetFiles(box.Mods));
        Assert.Equal(jar, File.ReadAllBytes(Assert.Single(Directory.GetFiles(box.Cache, "previous-optifine-*.jar"))));
        var own = (await Inventory()).Entries.Single(e => e.Id == OptiFineInstaller.ModId);
        Assert.Equal(("1.8.9_HD_U_L5", ModOwnership.User, ModEntryStatus.Installed), (own.Version, own.Ownership, own.Status));
        // Without it, the launcher's comes back from the cache.
        File.Delete(mine);
        Assert.Null(await Install());
        Assert.Equal(new[] { box.Mod(OptiFineInstaller.M5.FileName) }, Directory.GetFiles(box.Mods));
        Assert.Equal(2, site.Requests.Count);

        // Forge also loads mods/1.8.9/: your own OptiFine there is used the same way.
        var versionFolder = Directory.CreateDirectory(Path.Combine(box.Mods, "1.8.9")).FullName;
        File.WriteAllBytes(Path.Combine(versionFolder, "OptiFine_1.8.9_HD_U_L5.jar"), OptiFineJar("OptiFine 1.8.9_HD_U_L5"));
        Assert.Null(await Install());
        Assert.Empty(Directory.GetFiles(box.Mods));
        Assert.Equal(2, site.Requests.Count);

        // A Fabric profile never lists OptiFine.
        Assert.DoesNotContain((await new ModInventoryService().BuildAsync(box.Bundle, box.Game, "26.3")).Entries, e => e.Id == OptiFineInstaller.ModId);
    }

    // ------------------------------------------------------------------ release

    /// <summary>Every script that checks a published launcher for each version's Core (Publish-Release, Build-LadsClient,
    /// Install-LadsRelease) lists exactly the versions the launcher ships a pack for, 1.8.9 included.</summary>
    [Fact]
    public void ReleaseScriptsCheckEveryShippedVersionIncluding189()
    {
        var root = new DirectoryInfo(AppContext.BaseDirectory);
        while (root != null && !File.Exists(Path.Combine(root.FullName, "Publish-Release.ps1"))) root = root.Parent;
        Assert.True(root != null, "No Publish-Release.ps1 above the test folder: run the tests from the repository.");
        var shipped = Directory.GetDirectories(Path.Combine(root!.FullName, "TheLadsLauncher", "game-mods"))
            .Where(folder => File.Exists(Path.Combine(folder, "client-mods.json"))).Select(folder => Path.GetFileName(folder)).Order(StringComparer.Ordinal).ToList();
        Assert.Contains("1.8.9", shipped);
        foreach (var script in new[] { "Publish-Release.ps1", "Build-LadsClient.ps1", Path.Combine("tools", "Install-LadsRelease.ps1") })
        {
            // foreach ($gameVersion in @('1.21.1', ...)): the one list of Minecraft versions in the script.
            var lists = Regex.Matches(File.ReadAllText(Path.Combine(root.FullName, script)), @"foreach \(\$\w+ in @\(([^)]*)\)\)")
                .Select(loop => Regex.Matches(loop.Groups[1].Value, "'([^']*)'").Select(item => item.Groups[1].Value).ToList())
                .Where(items => items.Count > 0 && items.All(item => Regex.IsMatch(item, @"\A\d+(\.\d+)+\z"))).ToList();
            Assert.Equal(shipped, Assert.Single(lists).Order(StringComparer.Ordinal));
        }
    }

    // ------------------------------------------------------------------ helpers

    /// <summary>A sandboxed launcher whose shared folder holds newer-version content: a 26.3 world, packs, a server list,
    /// and the launcher's shared options.txt. Its Lunar Client folder (<see cref="Lunar"/>) is absent unless a test writes it.</summary>
    private sealed class Launcher : IDisposable
    {
        public const string SharedOptions = "version:4671\nguiScale:2\n";
        private readonly TestDirectory _dir = new();
        public string Root => _dir.Path;
        public string Global => Path.Combine(Root, "global");
        public string Lunar => Path.Combine(Root, "lunar");
        public PathService Paths { get; }
        public SharedContentService Shared { get; }
        public ProfileService Profiles { get; }
        public string Game189 => Paths.GetProfileDirectory(Profiles.GetProfile("1.8.9")!);

        public Launcher()
        {
            Paths = new PathService(Path.Combine(Root, "launcher"));
            Shared = new SharedContentService(Global, Path.Combine(Root, "launcher", "backups", "servers"));
            Profiles = new ProfileService(Paths, Shared) { LunarRoot = Lunar };
            Write(G("saves", "Modern World", "level.dat"), "a 26.3 world");
            Write(G("saves", "Modern World", "region", "r.0.0.mca"), "26.3 chunks");
            Write(G("resourcepacks", "modern.zip"), "pack_format 46");
            Write(G("shaderpacks", "shader.zip"), "a shader pack");
            var servers = ServerListFile.CreateEmpty();
            servers.Add(new NbtCompound { ["name"] = new NbtString("Lads"), ["ip"] = new NbtString("play.example.net") });
            File.WriteAllBytes(G("servers.dat"), servers.ToBytes());
            Write(Paths.SharedOptionsFile, SharedOptions);
        }

        public string G(params string[] parts) => Path.Combine(new[] { Global }.Concat(parts).ToArray());

        /// <summary>Every file in the shared saves/resourcepacks/shaderpacks, by path, with its SHA-256.</summary>
        public Dictionary<string, string> SharedTree() => SharedContentService.SharedFolders
            .SelectMany(folder => Directory.EnumerateFiles(G(folder), "*", SearchOption.AllDirectories))
            .ToDictionary(file => Path.GetRelativePath(Global, file), Sha);

        public void Dispose() => _dir.Dispose();
    }

    private sealed class Scripted(Func<HttpRequestMessage, HttpContent> respond) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = respond(request) });
    }

    /// <summary>
    /// optifine.net as spike S2 found it: every adloadx page links the jar with a new x token, the jar answers only the newest
    /// token, and any other token still gets 200 with a short web page. <see cref="Failure"/> breaks one step.
    /// </summary>
    internal sealed class OptiFineNet(byte[] jar) : HttpMessageHandler
    {
        public string Page => "https://optifine.net/adloadx?f=" + OptiFineInstaller.M5.FileName;
        public string JarUrl(int token) => $"https://optifine.net/downloadx?f={OptiFineInstaller.M5.FileName}&x={token:x8}";
        public List<string> Requests { get; } = new();
        public string? Failure { get; init; }
        private int pages;

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var url = request.RequestUri!.AbsoluteUri;
            Requests.Add(url);
            if (Failure == "offline") return Task.FromResult(new HttpResponseMessage(HttpStatusCode.ServiceUnavailable));
            if (url == Page)
                return Html(Failure == "no link" ? "<html>Checking your browser</html>"
                    : $"<a href='{JarUrl(++pages)[("https://optifine.net/".Length)..]}' onclick='onDownload()'>Download</a>");
            var bytes = Failure switch { "wrong bytes" => new byte[jar.Length], "short" => jar[..^10], _ => jar };
            if (url != JarUrl(pages) || Failure == "stale token") return Html("Error: invalid key");
            var content = new ByteArrayContent(bytes);
            content.Headers.ContentType = new("application/java-archive");
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = content });
        }

        private static Task<HttpResponseMessage> Html(string html) =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(html, Encoding.UTF8, "text/html") });
    }

    /// <summary>A stand-in for the OptiFine jar: its Forge tweaker and a changelog naming the version (never the real jar).</summary>
    internal static byte[] OptiFineJar(string changelog = "OptiFine 1.8.9_HD_U_M5")
    {
        using var bytes = new MemoryStream();
        using (var zip = new ZipArchive(bytes, ZipArchiveMode.Create, true))
        {
            using (var writer = new StreamWriter(zip.CreateEntry("META-INF/MANIFEST.MF").Open()))
                writer.Write("Manifest-Version: 1.0\r\nTweakClass: optifine.OptiFineForgeTweaker\r\nTweakOrder: -1000\r\n");
            using (var writer = new StreamWriter(zip.CreateEntry("optifine/OptiFineForgeTweaker.class").Open())) writer.Write("tweaker");
            using (var writer = new StreamWriter(zip.CreateEntry("changelog.txt").Open())) writer.Write(changelog + "\r\n - fixed particles\r\n");
        }
        return bytes.ToArray();
    }

    /// <summary>The launcher's OptiFine file name, pinned to a stand-in jar.</summary>
    internal static OptiFineInstaller.Pin Pin(byte[] jar) =>
        OptiFineInstaller.M5 with { Sha256 = Convert.ToHexString(SHA256.HashData(jar)), Size = jar.Length };

    /// <summary>A stand-in for the 1.8.9 Lads Core: mcmod.info as TheLadsCore/v1_8_9 writes it.</summary>
    private static byte[] ForgeCore(string version = "1.4.0", string mcversion = "1.8.9") => ForgeJar(
        $"[{{\"modid\": \"theladscore\", \"name\": \"The Lads Core\", \"version\": \"{version}\", \"mcversion\": \"{mcversion}\", \"authorList\": [\"The Lads\"]}}]");

    private static string[] Names(string directory) =>
        Directory.GetFiles(directory).Select(file => Path.GetFileName(file)).Order(StringComparer.Ordinal).ToArray();

    private static byte[] ForgeJar(string mcmodInfo)
    {
        using var bytes = new MemoryStream();
        using (var zip = new ZipArchive(bytes, ZipArchiveMode.Create, true))
        using (var writer = new StreamWriter(zip.CreateEntry("mcmod.info").Open(), Encoding.UTF8))
            writer.Write(mcmodInfo);
        return bytes.ToArray();
    }

    private static void Write(string path, string content)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllText(path, content);
    }

    private static string Sha(string path) => Convert.ToHexString(SHA256.HashData(File.ReadAllBytes(path)));
}
