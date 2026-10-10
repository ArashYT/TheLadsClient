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
        // 26.3 is broken here and 26.2 is paused, so the first valid Fabric default (1.20.1) takes over.
        Assert.Equal("1.20.1", new ProfileService(new PathService(broken), global).GetActiveProfile().Id);

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

    /// <summary>With Lunar installed, 1.8.9 takes its settings other than the controls from Lunar's 1.8 profile, and Lunar's
    /// optionsof.txt once; later launches take only what changed in Lunar, so the game's own changes stay. Its controls are the shared
    /// ones like every version's (Lunar's key binds never replace them) and go back to the shared copy; its other settings do not, and
    /// Lunar's files are only read.</summary>
    [Fact]
    public async Task Minecraft189TakesLunarsSettingsButTheSharedControls()
    {
        using var l = new Launcher();
        var profile = l.Profiles.GetProfile("1.8.9")!;
        var options = Path.Combine(l.Game189, "options.txt");
        var optiFine = Path.Combine(l.Game189, "optionsof.txt");
        var lunar18 = Path.Combine(l.Lunar, "profiles", "1.8");
        File.WriteAllText(l.Paths.SharedOptionsFile, Launcher.SharedOptions + "fov:0.25\nkey_key.attack:key.mouse.left\nkey_key.sprint:key.keyboard.r\n");
        Write(Path.Combine(lunar18, "options.txt"), "version:3700\nlang:en_ca\ngamma:0.7\nfov:0.75\nkey_key.attack:key.mouse.right\n"
            + "key_key.sprint:key.keyboard.left.control\nkey_Freelook:key.keyboard.right.alt\n");
        Write(Path.Combine(lunar18, "optionsof.txt"), "ofFastRender:true\n");
        Write(Path.Combine(lunar18, "resourcepacks", "lunar-pack.zip"), "a 1.8 pack");
        Dictionary<string, string> LunarTree() => Directory.EnumerateFiles(l.Lunar, "*", SearchOption.AllDirectories).ToDictionary(file => file, Sha);
        var lunarBefore = LunarTree();

        await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
        var instance = Options(options);
        Assert.Equal(("en_ca", "0.7", "0.25", "-100", "19"), (instance["lang"], instance["gamma"], instance["fov"], instance["key_key.attack"], instance["key_key.sprint"]));
        Assert.False(instance.ContainsKey("key_Freelook"));
        Assert.Equal("ofFastRender:true\n", File.ReadAllText(optiFine));

        // The game's own changes: its controls go to the shared copy, its other settings stay its own, also at the next launches.
        File.WriteAllText(optiFine, "ofFastRender:false\n");
        File.WriteAllText(options, "lang:en_ca\ngamma:1.0\nfov:1.0\nkey_key.attack:-99\nkey_key.sprint:-97\nkey_key.drop:-96\n");
        await l.Profiles.SyncProfileToSharedAsync(profile);
        var shared = Options(l.Paths.SharedOptionsFile);
        Assert.Equal(("1.0", "key.mouse.right", "key.mouse.4", "key.mouse.5"), (shared["fov"], shared["key_key.attack"], shared["key_key.sprint"], shared["key_key.drop"]));
        Assert.False(shared.ContainsKey("gamma") || shared.ContainsKey("lang"));
        await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
        await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
        Assert.Equal("ofFastRender:false\n", File.ReadAllText(optiFine));
        var kept = Options(options);
        Assert.Equal(("1.0", "1.0", "-99", "-97", "-96"), (kept["gamma"], kept["fov"], kept["key_key.attack"], kept["key_key.sprint"], kept["key_key.drop"]));
        Assert.Equal(lunarBefore, LunarTree());

        // A setting the player changes in Lunar comes at the next launch; a key bind or FOV changed there does not.
        Write(Path.Combine(lunar18, "options.txt"), "version:3700\nlang:en_ca\ngamma:0.2\nfov:0.5\nkey_key.attack:key.mouse.left\nkey_key.drop:key.keyboard.q\n");
        await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
        var taken = Options(options);
        Assert.Equal(("0.2", "1.0", "-99", "-96"), (taken["gamma"], taken["fov"], taken["key_key.attack"], taken["key_key.drop"]));
        Assert.Equal(shared, Options(l.Paths.SharedOptionsFile));
    }

    /// <summary>A launch never undoes the game's own key binds: not when the launcher missed the game's exit (closed, killed or
    /// crashed meanwhile), not for mouse buttons, and not for key codes without a key name. Another version's later change still
    /// comes, and nothing 26.x cannot read reaches its options.txt.</summary>
    [Fact]
    public async Task KeyBindsSurviveLaunchesWithoutTheExitSyncAndAcrossVersions()
    {
        using var l = new Launcher();
        var legacy = l.Profiles.GetProfile("1.8.9")!;
        var modern = l.Profiles.GetProfile("26.3")!;
        var options189 = Path.Combine(l.Game189, "options.txt");
        var options263 = Path.Combine(l.Paths.GetProfileDirectory(modern), "options.txt");
        Dictionary<string, string> Read(string file) => GameOptionsService.ParseOptions(File.ReadAllText(file));
        File.WriteAllText(l.Paths.SharedOptionsFile, "version:4671\nfov:0.0\nkey_key.drop:key.keyboard.q\nkey_key.pickItem:key.mouse.middle\n"
            + "key_key.jump:key.keyboard.space\nkey_key.chat:key.keyboard.t\n");
        await l.Profiles.PrepareProfileEnvironmentAsync(legacy, null);
        await l.Profiles.PrepareProfileEnvironmentAsync(modern, null);

        // 1.8.9 saves side buttons, a key and a key without a name (character + 256); then the launcher misses its exit, twice.
        File.WriteAllText(options189, "fov:0.5\nkey_key.drop:-97\nkey_key.pickItem:-96\nkey_key.jump:47\nkey_key.chat:508\n");
        await l.Profiles.PrepareProfileEnvironmentAsync(legacy, null);
        await l.Profiles.PrepareProfileEnvironmentAsync(legacy, null);
        var kept = Read(options189);
        Assert.Equal(("0.5", "-97", "-96", "47", "508"), (kept["fov"], kept["key_key.drop"], kept["key_key.pickItem"], kept["key_key.jump"], kept["key_key.chat"]));

        // The next exit is seen: names go to the shared copy, the nameless code does not; 26.3 takes them, keeping its own others.
        File.WriteAllText(options263, File.ReadAllText(options263) + "key_key.swapOffhand:key.mouse.4\n");
        await l.Profiles.SyncProfileToSharedAsync(legacy);
        var shared = Read(l.Paths.SharedOptionsFile);
        Assert.Equal(("key.mouse.4", "key.mouse.5", "key.keyboard.v", "key.keyboard.t"),
            (shared["key_key.drop"], shared["key_key.pickItem"], shared["key_key.jump"], shared["key_key.chat"]));
        await l.Profiles.PrepareProfileEnvironmentAsync(modern, null);
        var modernKeys = Read(options263);
        Assert.Equal(("key.mouse.4", "key.mouse.5", "key.keyboard.v", "key.keyboard.t", "key.mouse.4"), (modernKeys["key_key.drop"],
            modernKeys["key_key.pickItem"], modernKeys["key_key.jump"], modernKeys["key_key.chat"], modernKeys["key_key.swapOffhand"]));
        Assert.DoesNotContain(modernKeys, kvp => kvp.Key.StartsWith("key_", StringComparison.Ordinal) && int.TryParse(kvp.Value, out _));

        // A bind changed in 26.3 comes to 1.8.9 at its next launch; 1.8.9's nameless key stays.
        File.WriteAllText(options263, File.ReadAllText(options263).Replace("key_key.drop:key.mouse.4", "key_key.drop:key.keyboard.g"));
        await l.Profiles.SyncProfileToSharedAsync(modern);
        await l.Profiles.PrepareProfileEnvironmentAsync(legacy, null);
        var after = Read(options189);
        Assert.Equal(("34", "-96", "47", "508"), (after["key_key.drop"], after["key_key.pickItem"], after["key_key.jump"], after["key_key.chat"]));
    }

    // ------------------------------------------------------------------ controls shared by every version

    /// <summary>One set of controls for 26.3, 1.20.1 and 1.8.9: a bind changed in one version reaches the others at their next
    /// launch in their own format (1.8.9: LWJGL2 codes, mouse button b as b - 100, unbound 0, and its own names of Lads' and
    /// Essential's keys). A version's exit sends only what the player changed in it: its stale copy of a key bind changed meanwhile
    /// in another version, or of a key only other versions have, never resets the shared one.</summary>
    [Fact]
    public async Task ControlsFollowEveryVersionAndVersionOnlyKeysAreNeverReset()
    {
        using var l = new Launcher();
        var legacy = l.Profiles.GetProfile("1.8.9")!;
        var v1201 = l.Profiles.GetProfile("1.20.1")!;
        var v263 = l.Profiles.GetProfile("26.3")!;
        string file189 = Path.Combine(l.Game189, "options.txt"), file1201 = Path.Combine(l.Paths.GetProfileDirectory(v1201), "options.txt"),
            file263 = Path.Combine(l.Paths.GetProfileDirectory(v263), "options.txt");
        File.WriteAllText(l.Paths.SharedOptionsFile, Launcher.SharedOptions + "key_key.jump:key.keyboard.space\nkey_key.drop:key.keyboard.q\n"
            + "key_key.sprint:key.keyboard.left.control\nkey_key.theladscore.zoom:key.keyboard.c\nkey_keybind.name.CHAT_PEEK:key.keyboard.z\n"
            + "key_key.lads_screenshots.open_screenshots_screen:key.keyboard.f10\nkey_key.jei.showRecipe:key.keyboard.r\n");
        foreach (var profile in new[] { v263, v1201, legacy }) await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
        var first = Options(file189);
        Assert.Equal(("57", "44", "68"), (first["key_key.jump"], first["key_Chat Peek"], first["key_key.theladscore.screenshots"]));
        Assert.False(first.ContainsKey("key_keybind.name.CHAT_PEEK") || first.ContainsKey("key_key.lads_screenshots.open_screenshots_screen"));

        // 26.3: a key, a mouse button, unbound, a key 1.8.9 has a code for since 1.9.0, Essential's and Lads' renamed keys, a 26.3-only key.
        File.WriteAllText(file263, File.ReadAllText(file263).Replace("jump:key.keyboard.space", "jump:key.keyboard.v").Replace("drop:key.keyboard.q", "drop:key.mouse.4")
            .Replace("sprint:key.keyboard.left.control", "sprint:key.keyboard.unknown").Replace("zoom:key.keyboard.c", "zoom:key.keyboard.left.win")
            .Replace("CHAT_PEEK:key.keyboard.z", "CHAT_PEEK:key.keyboard.k").Replace("screen:key.keyboard.f10", "screen:key.keyboard.f9")
            + "key_key.debug.improvedTransparency:key.keyboard.y\n");
        await l.Profiles.SyncProfileToSharedAsync(v263);
        await l.Profiles.PrepareProfileEnvironmentAsync(legacy, null);
        await l.Profiles.PrepareProfileEnvironmentAsync(v1201, null);
        var in189 = Options(file189);
        Assert.Equal(("47", "-97", "0", "219", "37", "67"), (in189["key_key.jump"], in189["key_key.drop"], in189["key_key.sprint"],
            in189["key_key.theladscore.zoom"], in189["key_Chat Peek"], in189["key_key.theladscore.screenshots"]));
        var in1201 = Options(file1201);
        Assert.Equal(("key.keyboard.v", "key.mouse.4", "key.keyboard.unknown", "key.keyboard.k", "key.keyboard.r"), (in1201["key_key.jump"],
            in1201["key_key.drop"], in1201["key_key.sprint"], in1201["key_keybind.name.CHAT_PEEK"], in1201["key_key.jei.showRecipe"]));

        // 1.20.1 changes its own JEI key; 1.8.9, still holding the old one, exits later with one change of its own (Chat Peek to G).
        File.WriteAllText(file1201, File.ReadAllText(file1201).Replace("showRecipe:key.keyboard.r", "showRecipe:key.keyboard.u"));
        await l.Profiles.SyncProfileToSharedAsync(v1201);
        File.WriteAllText(file189, File.ReadAllText(file189).Replace("key_Chat Peek:37", "key_Chat Peek:34"));
        await l.Profiles.SyncProfileToSharedAsync(legacy);
        var shared = Options(l.Paths.SharedOptionsFile);
        Assert.Equal(("key.keyboard.u", "key.keyboard.g", "key.keyboard.y", "key.keyboard.left.win", "key.keyboard.f9"), (shared["key_key.jei.showRecipe"],
            shared["key_keybind.name.CHAT_PEEK"], shared["key_key.debug.improvedTransparency"], shared["key_key.theladscore.zoom"],
            shared["key_key.lads_screenshots.open_screenshots_screen"]));
        Assert.False(shared.ContainsKey("key_Chat Peek") || shared.ContainsKey("key_key.theladscore.screenshots"));

        await l.Profiles.PrepareProfileEnvironmentAsync(v263, null);
        var in263 = Options(file263);
        Assert.Equal(("key.keyboard.g", "key.keyboard.u", "key.keyboard.y", "key.keyboard.v"), (in263["key_keybind.name.CHAT_PEEK"],
            in263["key_key.jei.showRecipe"], in263["key_key.debug.improvedTransparency"], in263["key_key.jump"]));
    }

    /// <summary>No shared copy yet (a new install): the first game's options start it, and a bind changed in that first session goes
    /// back to it at the exit and stays at the next launch.</summary>
    [Fact]
    public async Task FirstGameStartsTheSharedCopyAndItsChangesGoBack()
    {
        using var l = new Launcher();
        File.Delete(l.Paths.SharedOptionsFile);
        Write(Path.Combine(l.Paths.SharedDirectory, SharedControls.MergedFile), "");
        var v1201 = l.Profiles.GetProfile("1.20.1")!;
        var file = Path.Combine(l.Paths.GetProfileDirectory(v1201), "options.txt");
        Write(file, "key_key.jump:key.keyboard.space\n");
        await l.Profiles.PrepareProfileEnvironmentAsync(v1201, null);
        Assert.Equal("key.keyboard.space", Options(l.Paths.SharedOptionsFile)["key_key.jump"]);

        File.WriteAllText(file, "key_key.jump:key.keyboard.v\n");
        await l.Profiles.SyncProfileToSharedAsync(v1201);
        await l.Profiles.PrepareProfileEnvironmentAsync(v1201, null);
        Assert.Equal(("key.keyboard.v", "key.keyboard.v"), (Options(l.Paths.SharedOptionsFile)["key_key.jump"], Options(file)["key_key.jump"]));
    }

    /// <summary>A shared copy 1.8.9 started (no "version:" line) or one a newer version left its own data version in: a new 1.20.1 or
    /// 26.3 profile's options.txt gets its OWN version's data version (the client jar's world_version) before the game starts, never
    /// another version's, so Minecraft does not run its pre-1.13 key fix on the key names and reset every option. A version line the
    /// file has stays; 1.8.9 gets none (not the shared copy's, not Lunar's).</summary>
    [Fact]
    public async Task NewerVersionsGetTheirOwnDataVersionAnd189None()
    {
        using var l = new Launcher();
        File.Delete(l.Paths.SharedOptionsFile);
        Write(Path.Combine(l.Paths.SharedDirectory, SharedControls.MergedFile), "");
        Write(Path.Combine(l.Game189, "options.txt"), "fov:0.5\nkey_key.jump:47\nkey_key.drop:-97\n");
        await l.Profiles.PrepareProfileEnvironmentAsync(l.Profiles.GetProfile("1.8.9")!, null);
        Assert.False(Options(l.Paths.SharedOptionsFile).ContainsKey("version"));

        foreach (var (version, java, dataVersion) in new[] { ("1.20.1", 17, 3465), ("26.3", 25, 5023) })
        {
            var profile = l.Profiles.CreateProfile("New " + version, version, java, fabricVersion: "0.19.5");
            var game = l.Paths.GetProfileDirectory(profile);
            await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
            Assert.False(Options(Path.Combine(game, "options.txt")).ContainsKey("version"));
            Assert.Equal(("key.keyboard.v", "key.mouse.4"), (Options(Path.Combine(game, "options.txt"))["key_key.jump"], Options(Path.Combine(game, "options.txt"))["key_key.drop"]));

            ClientJar(game, version, dataVersion);
            GameSession.Configure(new System.Diagnostics.ProcessStartInfo("java"), game, l.Global, version); // after the install, before the start
            Assert.StartsWith($"version:{dataVersion}\n", File.ReadAllText(Path.Combine(game, "options.txt")));
            Assert.Equal("key.keyboard.v", Options(Path.Combine(game, "options.txt"))["key_key.jump"]);
            Assert.Equal("false", Options(Path.Combine(game, "options.txt"))["onboardAccessibility"]); // as the old-file fixes left it
            GameSession.Configure(new System.Diagnostics.ProcessStartInfo("java"), game, l.Global, version);
            Assert.Single(File.ReadAllLines(Path.Combine(game, "options.txt")), line => line.StartsWith("version:", StringComparison.Ordinal));
        }

        // The shared copy now carries 26.3's data version (its exit): never into another profile, nor into 1.8.9 (with Lunar's own neither).
        File.AppendAllText(l.Paths.SharedOptionsFile, "version:5023\n");
        Write(Path.Combine(l.Lunar, "profiles", "1.8", "options.txt"), "version:3700\nlang:en_us\n");
        var v1201 = l.Profiles.CreateProfile("Another 1.20.1", "1.20.1", 17, fabricVersion: "0.19.5");
        await l.Profiles.PrepareProfileEnvironmentAsync(v1201, null);
        Assert.False(Options(Path.Combine(l.Paths.GetProfileDirectory(v1201), "options.txt")).ContainsKey("version"));
        File.WriteAllText(Path.Combine(l.Game189, "options.txt"), "fov:0.5\nkey_key.jump:47\n");
        File.Delete(Path.Combine(l.Game189, GameOptionsService.TakenOptionsFile));
        await l.Profiles.PrepareProfileEnvironmentAsync(l.Profiles.GetProfile("1.8.9")!, null);
        GameSession.Configure(new System.Diagnostics.ProcessStartInfo("java"), l.Game189, l.Global, "1.8.9");
        Assert.False(Options(Path.Combine(l.Game189, "options.txt")).ContainsKey("version"));
        Assert.Equal("en_us", Options(Path.Combine(l.Game189, "options.txt"))["lang"]);

        // A version line the game wrote stays as it is.
        var own = Path.Combine(l.Paths.GetProfileDirectory(v1201), "options.txt");
        File.WriteAllText(own, "version:3337\nkey_key.jump:key.keyboard.v\n");
        ClientJar(l.Paths.GetProfileDirectory(v1201), "1.20.1", 3465);
        GameOptionsService.EnsureDataVersion(l.Paths.GetProfileDirectory(v1201), "1.20.1");
        Assert.Equal("version:3337\nkey_key.jump:key.keyboard.v\n", File.ReadAllText(own));
    }

    /// <summary>A game that could not load its options.txt played and saved it with every option at its default. Its exit sends
    /// nothing to the shared copy (the other versions keep their controls), and its next launch gives it the shared controls
    /// back; after a normal session again only what the player changed goes back.</summary>
    [Fact]
    public async Task AGameThatLostItsOptionsNeverResetsTheSharedControls()
    {
        using var l = new Launcher();
        var profile = l.Profiles.GetProfile("1.20.1")!;
        var game = l.Paths.GetProfileDirectory(profile);
        var file = Path.Combine(game, "options.txt");
        const string controls = "fov:0.5\nkey_key.jump:key.keyboard.v\nkey_key.drop:key.mouse.4\nkey_key.theladscore.zoom:key.mouse.5\n";
        File.WriteAllText(l.Paths.SharedOptionsFile, Launcher.SharedOptions + controls);
        await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
        var sharedBefore = File.ReadAllText(l.Paths.SharedOptionsFile);

        Write(Path.Combine(game, "logs", "latest.log"), "[04:02:09] [Render thread/INFO]: Loading Minecraft\n[04:02:09] [Render thread/ERROR]: Failed to load options\n"
            + "java.lang.NumberFormatException: For input string: \"key.mouse.right\"\n");
        File.WriteAllText(file, "version:3465\nfov:0.0\nkey_key.jump:key.keyboard.space\nkey_key.drop:key.keyboard.q\nkey_key.theladscore.zoom:key.keyboard.c\nguiScale:0\n");
        await l.Profiles.SyncProfileToSharedAsync(profile);
        Assert.Equal(sharedBefore, File.ReadAllText(l.Paths.SharedOptionsFile));

        await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
        var restored = Options(file);
        Assert.Equal(("0.5", "key.keyboard.v", "key.mouse.4", "key.mouse.5", "3465"), (restored["fov"], restored["key_key.jump"], restored["key_key.drop"],
            restored["key_key.theladscore.zoom"], restored["version"]));

        // A normal session: the one key the player changed goes back, the others stay.
        Write(Path.Combine(game, "logs", "latest.log"), "[05:00:00] [Render thread/INFO]: Loading Minecraft\n");
        File.WriteAllText(file, File.ReadAllText(file).Replace("key_key.drop:key.mouse.4", "key_key.drop:key.keyboard.g"));
        await l.Profiles.SyncProfileToSharedAsync(profile);
        var shared = Options(l.Paths.SharedOptionsFile);
        Assert.Equal(("0.5", "key.keyboard.v", "key.keyboard.g", "key.mouse.5"), (shared["fov"], shared["key_key.jump"], shared["key_key.drop"], shared["key_key.theladscore.zoom"]));
    }

    /// <summary>The first launch with 1.9.0 merges what is there: each control from the newest options.txt (or the shared copy) that
    /// has it, 1.8.9's codes and names read as the newer versions', keys only one version has kept; written into the shared copy
    /// and every profile's options.txt. The shared copy is backed up first, and the merge runs once. A profile added later takes
    /// the shared controls at its first launch and keeps its own other settings.</summary>
    [Fact]
    public async Task FirstRunMergesEveryVersionsControlsNewestFirstOnce()
    {
        using var l = new Launcher();
        string file189 = Path.Combine(l.Game189, "options.txt"), file1201 = Path.Combine(l.Paths.GetProfileDirectory(l.Profiles.GetProfile("1.20.1")!), "options.txt"),
            file263 = Path.Combine(l.Paths.GetProfileDirectory(l.Profiles.GetProfile("26.3")!), "options.txt");
        const string sharedBefore = "version:4671\nlang:en_us\nfov:0.0\nkey_key.chat:key.keyboard.t\nkey_key.jump:key.keyboard.space\n";
        var now = DateTime.UtcNow;
        void Save(string file, string text, int minutesAgo) { Write(file, text); File.SetLastWriteTimeUtc(file, now.AddMinutes(-minutesAgo)); }
        Save(file263, "fov:0.5\nkey_key.jump:key.keyboard.v\nkey_key.theladscore.zoom:key.keyboard.z\nkey_key.debug.improvedTransparency:key.keyboard.y\n", 40);
        Save(l.Paths.SharedOptionsFile, sharedBefore, 30);
        Save(file1201, "key_key.theladscore.zoom:key.keyboard.c\nkey_key.jei.showRecipe:key.keyboard.r\n", 20);
        Save(file189, "key_key.chat:28\nkey_key.jump:57\nkey_key.theladscore.screenshots:67\nkey_Chat Peek:44\nkey_Freelook:184\nkey_key.sprint:508\n", 10);

        Assert.Empty(SharedControls.MergeOnce(l.Paths, SharedControls.Targets(l.Profiles.GetProfiles())));
        var shared = Options(l.Paths.SharedOptionsFile);
        Assert.Equal(("key.keyboard.enter", "key.keyboard.space", "key.keyboard.c", "key.keyboard.y", "0.0", "key.keyboard.r"), (shared["key_key.chat"],
            shared["key_key.jump"], shared["key_key.theladscore.zoom"], shared["key_key.debug.improvedTransparency"], shared["fov"], shared["key_key.jei.showRecipe"]));
        Assert.Equal(("key.keyboard.f9", "key.keyboard.z", "key.keyboard.right.alt", "en_us"), (shared["key_key.lads_screenshots.open_screenshots_screen"],
            shared["key_keybind.name.CHAT_PEEK"], shared["key_Freelook"], shared["lang"]));
        Assert.False(shared.ContainsKey("key_key.sprint") || shared.ContainsKey("key_Chat Peek")); // a code without a key name; 1.8.9's own name
        Assert.Equal(sharedBefore, File.ReadAllText(Path.Combine(l.Paths.SharedDirectory, SharedControls.BackupFile)));
        var in263 = Options(file263);
        Assert.Equal(("key.keyboard.space", "key.keyboard.c", "key.keyboard.enter", "0.0"), (in263["key_key.jump"], in263["key_key.theladscore.zoom"], in263["key_key.chat"], in263["fov"]));
        var in189 = Options(file189);
        Assert.Equal(("28", "57", "508", "0.0"), (in189["key_key.chat"], in189["key_key.jump"], in189["key_key.sprint"], in189["fov"]));

        // Once: a later change is not merged again.
        Save(file1201, "key_key.theladscore.zoom:key.keyboard.x\n", 0);
        Assert.Empty(SharedControls.MergeOnce(l.Paths, SharedControls.Targets(l.Profiles.GetProfiles())));
        Assert.Equal(shared, Options(l.Paths.SharedOptionsFile));

        // A profile added now (its folder copied from elsewhere): the shared controls at its first launch, its own other settings.
        var added = l.Profiles.CreateProfile("Second 26.3", "26.3", 25, fabricVersion: "0.19.5");
        var addedFile = Path.Combine(l.Paths.GetProfileDirectory(added), "options.txt");
        Write(addedFile, "gamma:0.3\nkey_key.theladscore.zoom:key.keyboard.z\n");
        await l.Profiles.PrepareProfileEnvironmentAsync(added, null);
        Assert.Equal(("0.3", "key.keyboard.c"), (Options(addedFile)["gamma"], Options(addedFile)["key_key.theladscore.zoom"]));
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
        Assert.Equal(("1.8.9", 3), (manifest.MinecraftVersion, manifest.Mods.Count));
        Assert.DoesNotContain(Directory.EnumerateFiles(Path.Combine(bundle, "game-mods"), "*", SearchOption.AllDirectories),
            file => Path.GetFileName(file).Contains("optifine", StringComparison.OrdinalIgnoreCase)
                || (file.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) && FabricModMetadata.ReadForgeJar(file)?.Id == OptiFineInstaller.ModId));

        using var box = new ModSandbox();
        box.Choose(("resourcify", false), ("essential", false), ("skinlayers3d", false));
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
            // foreach ($gameVersion in @('1.8.9', ...)): the one list of Minecraft versions in the script.
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

    private static Dictionary<string, string> Options(string file) => GameOptionsService.ParseOptions(File.ReadAllText(file));

    /// <summary>A client jar as the launcher installs it, with only its version.json.</summary>
    private static void ClientJar(string game, string version, int dataVersion)
    {
        var jar = Path.Combine(game, "versions", version, version + ".jar");
        Directory.CreateDirectory(Path.GetDirectoryName(jar)!);
        using var zip = ZipFile.Open(jar, ZipArchiveMode.Create);
        using var writer = new StreamWriter(zip.CreateEntry("version.json").Open());
        writer.Write($"{{\"id\":\"{version}\",\"world_version\":{dataVersion}}}");
    }
}
