using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>Minecraft 1.8.9 (Forge, Java 8) in the launcher: version policy, Java 8, Forge output and mods, the default
/// profile, and the world-safety trip-wire (1.8.9 never gets the shared saves, resource packs, shader packs or options).</summary>
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
        Assert.Equal(("1.8.9", "The Lads Client 1.8.9", (string?)null, 8), (profile.Id, profile.Name, profile.FabricVersion, profile.JavaMajorVersion));
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
    }

    // ------------------------------------------------------------------ world safety

    /// <summary>
    /// The trip-wire for the hard world-safety rule. Whatever a 1.8.9 game folder held before (the links a newer version
    /// made in it, or its own 1.8.9 worlds and packs) and whichever entry point runs (the startup pass, a launch, the "launch
    /// without sharing" retry, the post-game sync), it ends with its own saves, resourcepacks and shaderpacks: no links and no
    /// copies of shared content, the shared folders byte for byte as they were, and options.txt never shared either way.
    /// servers.dat may be shared.
    /// </summary>
    [Fact]
    public async Task Minecraft189NeverGetsTheSharedWorldsPacksOrSettings()
    {
        using var l = new Launcher();
        var shared = l.SharedTree();
        // An existing folder that a newer version had linked (a profile folder re-used for 1.8.9).
        await l.Shared.PrepareProfileAsync(l.Game189, "26.3 before", null, coreEnabled: true);
        Assert.All(SharedContentService.SharedFolders, folder => Assert.True(SafeFileOps.IsLink(Path.Combine(l.Game189, folder))));
        // A second 1.8.9 profile with its own worlds, packs and settings, and IsIsolated off.
        var second = new LauncherProfile { Id = "second-189", Name = "Second 1.8.9", MinecraftVersion = "1.8.9", FabricVersion = null, JavaMajorVersion = 8 };
        l.Profiles.SaveProfile(second);
        var game2 = l.Paths.GetProfileDirectory(second);
        Write(Path.Combine(game2, "saves", "Old 1.8.9 World", "level.dat"), "1.8.9 world");
        Write(Path.Combine(game2, "resourcepacks", "faithful-1.8.zip"), "pack_format 1");
        Write(Path.Combine(game2, "options.txt"), "fov:0.0\n");

        await l.Profiles.PrepareAllProfilesSharedContentAsync();
        foreach (var profile in new[] { l.Profiles.GetProfile("1.8.9")!, second })
        {
            await l.Profiles.PrepareProfileEnvironmentAsync(profile, null);
            await l.Profiles.PrepareProfileEnvironmentAsync(profile, null, withoutSharedFolders: true);
            await l.Profiles.SyncProfileToSharedAsync(profile);
        }
        Write(Path.Combine(l.Game189, "saves", "New 1.8.9 World", "level.dat"), "played after the first launch");
        await l.Profiles.PrepareAllProfilesSharedContentAsync();
        await l.Profiles.PrepareProfileEnvironmentAsync(l.Profiles.GetProfile("1.8.9")!, null);

        var sharedHashes = shared.Values.ToHashSet();
        foreach (var game in new[] { l.Game189, game2 })
        {
            Assert.All(SharedContentService.SharedFolders, folder => Assert.False(SafeFileOps.IsLink(Path.Combine(game, folder)), $"{game}: {folder} is a link"));
            Assert.DoesNotContain(Directory.EnumerateFiles(game, "*", SearchOption.AllDirectories), file => sharedHashes.Contains(Sha(file)));
            Assert.False(File.Exists(Path.Combine(game, WorldCatalogService.GameSourcesFile)));
            Assert.Equal(Sha(l.G("servers.dat")), Sha(Path.Combine(game, "servers.dat"))); // the server list is shared
        }
        Assert.Equal(shared, l.SharedTree()); // nothing changed, removed or added (no 1.8.9 world moved in)
        Assert.Equal("1.8.9 world", File.ReadAllText(Path.Combine(game2, "saves", "Old 1.8.9 World", "level.dat")));
        Assert.Equal("played after the first launch", File.ReadAllText(Path.Combine(l.Game189, "saves", "New 1.8.9 World", "level.dat")));
        Assert.Equal("fov:0.0\n", File.ReadAllText(Path.Combine(game2, "options.txt")));
        Assert.False(File.Exists(Path.Combine(l.Game189, "options.txt")));
        Assert.Equal(Launcher.SharedOptions, File.ReadAllText(l.Paths.SharedOptionsFile));
    }

    [Fact]
    public async Task Minecraft189RefusesTheGlobalFolderAForeignLinkAndANewerProfilesFolder()
    {
        using var l = new Launcher();
        var shared = l.SharedTree();
        var direct = new LauncherProfile { Name = "1.8.9 in .minecraft", MinecraftVersion = "1.8.9", FabricVersion = null, CustomGameDir = l.Global };
        var refused = await Assert.ThrowsAsync<InvalidOperationException>(() => l.Profiles.PrepareProfileEnvironmentAsync(direct, null));
        Assert.Contains("game folder of its own", refused.Message);
        await Assert.ThrowsAsync<InvalidOperationException>(() => l.Shared.PrepareProfileAsync(l.Global, "QA", null, false, keepOwnFolders: true));

        // A folder linked anywhere but the shared folder may lead to newer worlds: refused, and the link is left alone.
        var elsewhere = Path.Combine(l.Root, "other-launcher", "saves");
        Write(Path.Combine(elsewhere, "World", "level.dat"), "some other world");
        Directory.CreateDirectory(l.Game189);
        SafeFileOps.CreateJunction(Path.Combine(l.Game189, "saves"), elsewhere);
        await Assert.ThrowsAsync<InvalidOperationException>(() => l.Profiles.PrepareProfileEnvironmentAsync(l.Profiles.GetProfile("1.8.9")!, null));
        Assert.True(SafeFileOps.IsLink(Path.Combine(l.Game189, "saves")));

        var newer = l.Paths.GetProfileDirectory(l.Profiles.GetProfile("26.3")!);
        var inNewer = new LauncherProfile { Name = "1.8.9 in the 26.3 folder", MinecraftVersion = "1.8.9", FabricVersion = null, CustomGameDir = newer };
        await Assert.ThrowsAsync<InvalidOperationException>(() => l.Profiles.PrepareProfileEnvironmentAsync(inNewer, null));
        Assert.False(Directory.Exists(newer));
        Assert.Equal(shared, l.SharedTree());
    }

    [Fact]
    public async Task NewerVersionsInGameWorldPickerNeverLists189Worlds()
    {
        using var l = new Launcher();
        var modern = l.Profiles.GetProfile("26.3")!;
        await l.Profiles.PrepareProfileEnvironmentAsync(modern, null);
        var sources = JsonSerializer.Deserialize<List<WorldSource>>(File.ReadAllText(Path.Combine(l.Paths.GetProfileDirectory(modern), WorldCatalogService.GameSourcesFile)))!;
        Assert.DoesNotContain(sources, s => SafeFileOps.PathsEqual(s.GameDirectory, l.Game189));
        Assert.Contains(sources, s => s.Version == "1.21.1");
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

    // ------------------------------------------------------------------ helpers

    /// <summary>A sandboxed launcher whose shared folder holds newer-version content: a 26.3 world, packs, a server list,
    /// and the launcher's shared options.txt.</summary>
    private sealed class Launcher : IDisposable
    {
        public const string SharedOptions = "version:4671\nguiScale:2\n";
        private readonly TestDirectory _dir = new();
        public string Root => _dir.Path;
        public string Global => Path.Combine(Root, "global");
        public PathService Paths { get; }
        public SharedContentService Shared { get; }
        public ProfileService Profiles { get; }
        public string Game189 => Paths.GetProfileDirectory(Profiles.GetProfile("1.8.9")!);

        public Launcher()
        {
            Paths = new PathService(Path.Combine(Root, "launcher"));
            Shared = new SharedContentService(Global, Path.Combine(Root, "launcher", "backups", "servers"));
            Profiles = new ProfileService(Paths, Shared);
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
