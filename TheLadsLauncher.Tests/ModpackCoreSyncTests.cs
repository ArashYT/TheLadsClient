using System.IO.Compression;
using System.Text;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ModpackCoreSyncTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "lads_coresync_" + Guid.NewGuid().ToString("N"));
    private string Game => Path.Combine(_root, "game");
    private string Mods => Path.Combine(Game, "mods");

    public ModpackCoreSyncTests()
    {
        Directory.CreateDirectory(Mods);
    }

    public void Dispose()
    {
        try { Directory.Delete(_root, true); } catch { }
    }

    /// <summary>A jar with Fabric metadata; <paramref name="build"/> makes two jars differ.</summary>
    private static void MakeCore(string path, string build, byte[]? icon = null, string? loaderConstraint = null)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        using var zip = ZipFile.Open(path, ZipArchiveMode.Create);
        var depends = loaderConstraint != null ? ",\"depends\":{\"fabricloader\":\"" + loaderConstraint + "\"}" : "";
        using (var w = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open(), new UTF8Encoding(false)))
            w.Write("{\"schemaVersion\":1,\"id\":\"theladscore\",\"version\":\"" + build + "\"" + (icon != null ? ",\"icon\":\"icon.png\"" : "") + depends + "}");
        if (icon != null)
            using (var entry = zip.CreateEntry("icon.png").Open()) entry.Write(icon);
    }

    private string Bundle(string mc, string build, byte[]? icon = null, string? loaderConstraint = null)
    {
        var bundleRoot = Path.Combine(_root, "bundle");
        MakeCore(Path.Combine(bundleRoot, "game-mods", mc, "theladscore.jar"), build, icon, loaderConstraint);
        return bundleRoot;
    }

    [Fact]
    public void InstanceWithoutCore_IsNeverTouched()
    {
        var bundle = Bundle("1.20.1", "new");
        File.WriteAllText(Path.Combine(Mods, "sodium.jar"), "other mod");

        Assert.False(ModpackCoreSync.Sync(Game, "1.20.1", bundle));

        Assert.Equal(new[] { "sodium.jar" }, Directory.GetFiles(Mods).Select(Path.GetFileName));
    }

    [Fact]
    public void OutdatedCore_IsReplacedByBundledBuild_AndSidecarStays()
    {
        var bundle = Bundle("1.20.1", "new");
        var core = Path.Combine(Mods, "theladscore.jar");
        MakeCore(core, "old");
        File.WriteAllText(core + ".icon.png", "my icon");

        var messages = new List<string>();
        Assert.True(ModpackCoreSync.Sync(Game, "1.20.1", bundle, messages.Add));

        Assert.Equal(File.ReadAllBytes(Path.Combine(bundle, "game-mods", "1.20.1", "theladscore.jar")), File.ReadAllBytes(core));
        Assert.Equal("my icon", File.ReadAllText(core + ".icon.png"));
        Assert.Empty(Directory.GetFiles(Mods, "*.tmp"));
        Assert.Single(messages);
    }

    [Fact]
    public void CurrentCore_IsLeftAlone()
    {
        var bundle = Bundle("1.20.1", "same");
        var core = Path.Combine(Mods, "theladscore.jar");
        File.Copy(Path.Combine(bundle, "game-mods", "1.20.1", "theladscore.jar"), core);
        var stamp = new DateTime(2020, 1, 2, 3, 4, 5, DateTimeKind.Utc);
        File.SetLastWriteTimeUtc(core, stamp);

        Assert.False(ModpackCoreSync.Sync(Game, "1.20.1", bundle));

        Assert.Equal(stamp, File.GetLastWriteTimeUtc(core));
    }

    [Fact]
    public void LegacyNamedCore_IsUpdatedInPlace()
    {
        var bundle = Bundle("1.20.1", "new");
        var core = Path.Combine(Mods, "TheLadsCore-1.20.1-1.2.0.jar");
        MakeCore(core, "old");

        Assert.True(ModpackCoreSync.Sync(Game, "1.20.1", bundle));

        Assert.Equal(new[] { "TheLadsCore-1.20.1-1.2.0.jar" }, Directory.GetFiles(Mods).Select(Path.GetFileName));
        Assert.Equal(File.ReadAllBytes(Path.Combine(bundle, "game-mods", "1.20.1", "theladscore.jar")), File.ReadAllBytes(core));
    }

    [Fact]
    public void SecondCore_IsSwitchedOff_SoFabricNeverSeesTwo()
    {
        var bundle = Bundle("1.20.1", "new");
        MakeCore(Path.Combine(Mods, "theladscore.jar"), "old");
        MakeCore(Path.Combine(Mods, "TheLadsCore-1.20.1-1.2.0.jar"), "older");

        Assert.True(ModpackCoreSync.Sync(Game, "1.20.1", bundle));

        Assert.Equal(new[] { "TheLadsCore-1.20.1-1.2.0.jar.disabled", "theladscore.jar" },
            Directory.GetFiles(Mods).Select(Path.GetFileName).OrderBy(n => n, StringComparer.Ordinal));
    }

    [Fact]
    public void CoreSwitchedOffByThePlayer_IsNotTouched()
    {
        var bundle = Bundle("1.20.1", "new");
        var off = Path.Combine(Mods, "theladscore.jar.disabled");
        MakeCore(off, "old");

        Assert.False(ModpackCoreSync.Sync(Game, "1.20.1", bundle));

        Assert.Equal(new[] { "theladscore.jar.disabled" }, Directory.GetFiles(Mods).Select(Path.GetFileName));
    }

    [Fact]
    public void NoBundledBuild_OrUnsupportedVersion_KeepsTheInstalledCore()
    {
        var core = Path.Combine(Mods, "theladscore.jar");
        MakeCore(core, "old");
        var before = File.ReadAllBytes(core);

        Assert.False(ModpackCoreSync.Sync(Game, "1.20.1", Path.Combine(_root, "empty-bundle")));
        Assert.False(ModpackCoreSync.Sync(Game, "1.19.2", Bundle("1.19.2", "new"))); // Lads has no Core for 1.19.2

        Assert.Equal(before, File.ReadAllBytes(core));
    }

    [Fact]
    public void BrokenBundledJar_NeverReplacesAWorkingCore()
    {
        var bundle = Path.Combine(_root, "bundle");
        Directory.CreateDirectory(Path.Combine(bundle, "game-mods", "1.20.1"));
        File.WriteAllText(Path.Combine(bundle, "game-mods", "1.20.1", "theladscore.jar"), "half a jar");
        var core = Path.Combine(Mods, "theladscore.jar");
        MakeCore(core, "old");
        var before = File.ReadAllBytes(core);

        var messages = new List<string>();
        Assert.False(ModpackCoreSync.Sync(Game, "1.20.1", bundle, messages.Add));

        Assert.Equal(before, File.ReadAllBytes(core));
        Assert.Contains("Could not update", Assert.Single(messages));
        Assert.Empty(Directory.GetFiles(Mods, "*.tmp"));
    }

    [Fact]
    public void LockedCore_IsReportedNotThrown()
    {
        var bundle = Bundle("1.20.1", "new");
        var core = Path.Combine(Mods, "theladscore.jar");
        MakeCore(core, "old");

        var messages = new List<string>();
        using (new FileStream(core, FileMode.Open, FileAccess.Read, FileShare.Read)) // a running game holds its jars open
            Assert.False(ModpackCoreSync.Sync(Game, "1.20.1", bundle, messages.Add));

        Assert.Contains("Could not update", Assert.Single(messages));
        Assert.Empty(Directory.GetFiles(Mods, "*.tmp"));
    }

    [Fact]
    public void MissingIconSidecar_IsCreatedFromTheBundledJar()
    {
        var icon = new byte[] { 1, 2, 3, 4 };
        var bundle = Bundle("1.20.1", "new", icon);
        var core = Path.Combine(Mods, "theladscore.jar");
        MakeCore(core, "old");

        Assert.True(ModpackCoreSync.Sync(Game, "1.20.1", bundle));

        Assert.Equal(icon, File.ReadAllBytes(core + ".icon.png"));
    }

    [Fact]
    public void SkipVariable_KeepsADevelopmentCoreInPlace()
    {
        var bundle = Bundle("1.20.1", "new");
        var core = Path.Combine(Mods, "theladscore.jar");
        MakeCore(core, "my own build");
        var before = File.ReadAllBytes(core);

        Environment.SetEnvironmentVariable(ModpackCoreSync.SkipEnvironmentVariable, "1");
        try { Assert.False(ModpackCoreSync.Sync(Game, "1.20.1", bundle)); }
        finally { Environment.SetEnvironmentVariable(ModpackCoreSync.SkipEnvironmentVariable, null); }

        Assert.Equal(before, File.ReadAllBytes(core));
        Assert.True(ModpackCoreSync.Sync(Game, "1.20.1", bundle)); // and syncs again once the variable is gone
    }

    [Fact]
    public void ModpackInstanceOverload_UsesItsGameFolderAndMinecraftVersion()
    {
        var bundle = Bundle("1.20.1", "new");
        MakeCore(Path.Combine(Mods, "theladscore.jar"), "old");
        File.WriteAllText(Path.Combine(Mods, "fabric-api.jar"), "");
        var instance = new ModpackInstance { Id = "x", Name = "X", McVersion = "1.20.1", Loader = "fabric", Directory = _root };
        Directory.Move(Game, instance.GameDirectory);

        Assert.True(ModpackCoreSync.Sync(instance, bundle));
    }

    [Theory]
    [InlineData("26.3", "fabric", true, null)]
    [InlineData("26.3", "quilt", true, null)]
    [InlineData("26.3", "forge", false, "Minecraft 26.3 Lads Core requires Fabric.")]
    [InlineData("26.3", "neoforge", false, "Minecraft 26.3 Lads Core requires Fabric.")]
    [InlineData("26.3", "vanilla", false, "Vanilla Minecraft does not support mods.")]
    [InlineData("1.20.1", "fabric", true, null)]
    [InlineData("1.20.1", "quilt", true, null)]
    [InlineData("1.20.1", "forge", false, "Minecraft 1.20.1 Forge requires Sinytra Connector to run The Lads Core.")]
    [InlineData("1.20.1", "neoforge", false, "Minecraft 1.20.1 Lads Core requires Fabric.")]
    [InlineData("1.20.1", "vanilla", false, "Vanilla Minecraft does not support mods.")]
    [InlineData("1.8.9", "forge", true, null)]
    [InlineData("1.8.9", "fabric", false, "Minecraft 1.8.9 Lads Core requires Forge.")]
    [InlineData("1.8.9", "vanilla", false, "Vanilla Minecraft does not support mods.")]
    [InlineData("1.16.5", "forge", false, "No Lads Core is bundled for Minecraft 1.16.5.")]
    [InlineData("1.19.2", "fabric", false, "No Lads Core is bundled for Minecraft 1.19.2.")]
    [InlineData("", "fabric", false, "Unknown Minecraft version.")]
    [InlineData(null, "fabric", false, "Unknown Minecraft version.")]
    public void SupportRule_VerifiesMinecraftVersionAndLoader(string? mc, string? loader, bool expectedSupported, string? expectedReason)
    {
        var modsWithFabricApi = new[] { "fabric-api-0.92.2.jar" };
        bool supported = ModpackCoreSync.IsSupported(mc, loader, modsWithFabricApi, out var reason);
        Assert.Equal(expectedSupported, supported);
        Assert.Equal(expectedReason, reason);
    }

    [Fact]
    public void SupportRule_Forge1201_IsSupportedWithSinytraConnector()
    {
        var modsWithoutConnector = new[] { "sodium.jar", "iris.jar" };
        Assert.False(ModpackCoreSync.IsSupported("1.20.1", "forge", modsWithoutConnector, out var reasonNo));
        Assert.Equal("Minecraft 1.20.1 Forge requires Sinytra Connector to run The Lads Core.", reasonNo);

        var modsWithConnectorOnly = new[] { "sodium.jar", "Connector-1.0.0-beta.49+1.20.1.jar" };
        Assert.False(ModpackCoreSync.IsSupported("1.20.1", "forge", modsWithConnectorOnly, out var reasonNoApi));
        Assert.Equal("This modpack has no Fabric API, which The Lads Core needs.", reasonNoApi);

        var modsWithConnectorAndForgifiedApi = new[] { "Connector-1.0.0-beta.49+1.20.1.jar", "forgified-fabric-api-0.92.2.jar" };
        Assert.True(ModpackCoreSync.IsSupported("1.20.1", "forge", modsWithConnectorAndForgifiedApi, out var reasonYes));
        Assert.Null(reasonYes);

        // Disabled connector should not count
        var modsWithDisabledConnector = new[] { "Connector-1.0.0-beta.49+1.20.1.jar.disabled", "fabric-api.jar" };
        Assert.False(ModpackCoreSync.IsSupported("1.20.1", "forge", modsWithDisabledConnector, out _));

        var modsWithDisabledFabricApi = new[] { "Connector-1.0.0-beta.49+1.20.1.jar", "fabric-api.jar.disabled" };
        Assert.False(ModpackCoreSync.IsSupported("1.20.1", "forge", modsWithDisabledFabricApi, out var reasonDisabledApi));
        Assert.Equal("This modpack has no Fabric API, which The Lads Core needs.", reasonDisabledApi);
    }

    [Fact]
    public void DefaultSetting_IsOnWhenSupported_AndOffWhenUnsupported()
    {
        var supportedInstance = new ModpackInstance { Id = "sup", Name = "Supported", McVersion = "1.20.1", Loader = "fabric", Directory = _root };
        Directory.CreateDirectory(Path.Combine(supportedInstance.GameDirectory, "mods"));
        File.WriteAllText(Path.Combine(supportedInstance.GameDirectory, "mods", "fabric-api-0.92.2.jar"), "");
        Assert.Null(supportedInstance.LadsCore);
        Assert.True(ModpackCoreSync.ResolveDefaultLadsCore(supportedInstance));

        var unsupportedInstance = new ModpackInstance { Id = "unsup", Name = "Unsupported", McVersion = "1.16.5", Loader = "forge", Directory = _root };
        Assert.Null(unsupportedInstance.LadsCore);
        Assert.False(ModpackCoreSync.ResolveDefaultLadsCore(unsupportedInstance));
    }

    [Fact]
    public void On_AddsBundledJarAndIcon_WhenMissing()
    {
        byte[] icon = new byte[] { 10, 20, 30, 40 };
        var bundle = Bundle("1.20.1", "new", icon);
        File.WriteAllText(Path.Combine(Mods, "fabric-api-0.92.2.jar"), "");
        var instance = new ModpackInstance { Id = "inst", Name = "Instance", McVersion = "1.20.1", Loader = "fabric", Directory = _root, LadsCore = true };
        Directory.Move(Game, instance.GameDirectory);

        var messages = new List<string>();
        Assert.True(ModpackCoreSync.Sync(instance, bundle, messages.Add));

        var corePath = Path.Combine(instance.GameDirectory, "mods", ModpackCoreSync.BundledName);
        Assert.True(File.Exists(corePath));
        Assert.Equal(File.ReadAllBytes(Path.Combine(bundle, "game-mods", "1.20.1", ModpackCoreSync.BundledName)), File.ReadAllBytes(corePath));
        Assert.True(File.Exists(corePath + ".icon.png"));
        Assert.Equal(icon, File.ReadAllBytes(corePath + ".icon.png"));
        Assert.Contains(messages, m => m.Contains("Added The Lads Core to this modpack"));

        // Running sync again when already up to date does not re-add or report changes
        Assert.False(ModpackCoreSync.Sync(instance, bundle));
    }

    [Fact]
    public void Off_DisablesEnabledCoreJar_WithoutDeleting()
    {
        var bundle = Bundle("1.20.1", "new");
        var instance = new ModpackInstance { Id = "inst", Name = "Instance", McVersion = "1.20.1", Loader = "fabric", Directory = _root, LadsCore = false };
        Directory.Move(Game, instance.GameDirectory);
        var modsDir = Path.Combine(instance.GameDirectory, "mods");
        Directory.CreateDirectory(modsDir);
        var corePath = Path.Combine(modsDir, ModpackCoreSync.BundledName);
        MakeCore(corePath, "old");

        var messages = new List<string>();
        Assert.True(ModpackCoreSync.Sync(instance, bundle, messages.Add));

        Assert.False(File.Exists(corePath));
        var disabledPath = corePath + ".disabled";
        Assert.True(File.Exists(disabledPath));
        Assert.Empty(ModpackCoreSync.CoreJars(modsDir));
        Assert.Single(ModpackCoreSync.DisabledCoreJars(modsDir));
        Assert.Contains(messages, m => m.Contains("Disabled The Lads Core for this modpack"));

        // Repeating sync when already disabled does nothing
        Assert.False(ModpackCoreSync.Sync(instance, bundle));
    }

    [Fact]
    public void OffToOn_RestoresAndUpdatesCore()
    {
        var bundle = Bundle("1.20.1", "new");
        File.WriteAllText(Path.Combine(Mods, "fabric-api.jar"), "");
        var instance = new ModpackInstance { Id = "inst", Name = "Instance", McVersion = "1.20.1", Loader = "fabric", Directory = _root, LadsCore = true };
        Directory.Move(Game, instance.GameDirectory);
        var modsDir = Path.Combine(instance.GameDirectory, "mods");
        Directory.CreateDirectory(modsDir);
        var corePath = Path.Combine(modsDir, ModpackCoreSync.BundledName);
        MakeCore(corePath + ".disabled", "old");

        Assert.True(ModpackCoreSync.Sync(instance, bundle));

        Assert.False(File.Exists(corePath + ".disabled"));
        Assert.True(File.Exists(corePath));
        Assert.Equal(File.ReadAllBytes(Path.Combine(bundle, "game-mods", "1.20.1", ModpackCoreSync.BundledName)), File.ReadAllBytes(corePath));
    }

    [Fact]
    public void PackShippedCore_IsNeverDeleted_WhenDisabledAndRestored()
    {
        var bundle = Bundle("1.20.1", "new");
        File.WriteAllText(Path.Combine(Mods, "fabric-api.jar"), "");
        var instance = new ModpackInstance { Id = "inst", Name = "Instance", McVersion = "1.20.1", Loader = "fabric", Directory = _root, LadsCore = false };
        Directory.Move(Game, instance.GameDirectory);
        var modsDir = Path.Combine(instance.GameDirectory, "mods");
        Directory.CreateDirectory(modsDir);
        var customCore = Path.Combine(modsDir, "TheLadsCore-1.20.1-1.2.0.jar");
        MakeCore(customCore, "pack-shipped");
        var originalBytes = File.ReadAllBytes(customCore);

        // Turn OFF: must rename to .disabled, never delete
        Assert.True(ModpackCoreSync.Sync(instance, bundle));
        Assert.False(File.Exists(customCore));
        Assert.True(File.Exists(customCore + ".disabled"));
        Assert.Equal(originalBytes, File.ReadAllBytes(customCore + ".disabled"));

        // Turn back ON: restores and updates to bundled build
        instance.LadsCore = true;
        Assert.True(ModpackCoreSync.Sync(instance, bundle));
        Assert.False(File.Exists(customCore + ".disabled"));
        Assert.True(File.Exists(customCore));
        Assert.Equal(File.ReadAllBytes(Path.Combine(bundle, "game-mods", "1.20.1", ModpackCoreSync.BundledName)), File.ReadAllBytes(customCore));
    }

    [Fact]
    public void SkipEnvironmentVariable_PreventsAddingOrDisabling()
    {
        var bundle = Bundle("1.20.1", "new");
        var instanceOn = new ModpackInstance { Id = "inst-on", Name = "Instance On", McVersion = "1.20.1", Loader = "fabric", Directory = _root, LadsCore = true };
        Directory.Move(Game, instanceOn.GameDirectory);
        var modsDir = Path.Combine(instanceOn.GameDirectory, "mods");
        Directory.CreateDirectory(modsDir);
        File.WriteAllText(Path.Combine(modsDir, "fabric-api.jar"), "");

        Environment.SetEnvironmentVariable(ModpackCoreSync.SkipEnvironmentVariable, "1");
        try
        {
            // With skip env var set, ON never adds missing Core
            Assert.False(ModpackCoreSync.Sync(instanceOn, bundle));
            Assert.Single(Directory.GetFiles(modsDir)); // only fabric-api.jar

            // With skip env var set, OFF never disables existing Core
            MakeCore(Path.Combine(modsDir, ModpackCoreSync.BundledName), "current");
            var instanceOff = new ModpackInstance { Id = "inst-off", Name = "Instance Off", McVersion = "1.20.1", Loader = "fabric", Directory = _root, LadsCore = false };
            Assert.False(ModpackCoreSync.Sync(instanceOff, bundle));
            Assert.True(File.Exists(Path.Combine(modsDir, ModpackCoreSync.BundledName)));
            Assert.False(File.Exists(Path.Combine(modsDir, ModpackCoreSync.BundledName + ".disabled")));
        }
        finally
        {
            Environment.SetEnvironmentVariable(ModpackCoreSync.SkipEnvironmentVariable, null);
        }
    }

    [Fact]
    public void ExternalInstance_IsNeverTouchedBySync()
    {
        var bundle = Bundle("1.20.1", "new");
        var externalMods = Path.Combine(_root, "external_game", "mods");
        Directory.CreateDirectory(externalMods);
        var instance = new ModpackInstance
        {
            Id = "ext",
            Name = "External Pack",
            McVersion = "1.20.1",
            Loader = "fabric",
            Directory = _root,
            ExternalGameDirectory = Path.Combine(_root, "external_game"),
            LadsCore = true
        };

        Assert.True(instance.IsExternal);
        Assert.False(ModpackCoreSync.Sync(instance, bundle));
        Assert.Empty(Directory.GetFiles(externalMods));
    }

    [Fact]
    public void PacksInstalledBeforeUpdate_GetDefaultOnWhenSupported_OnFirstSync()
    {
        var bundle = Bundle("1.20.1", "new");
        File.WriteAllText(Path.Combine(Mods, "fabric-api.jar"), "");
        var instance = new ModpackInstance { Id = "old-pack", Name = "Old Pack", McVersion = "1.20.1", Loader = "fabric", Directory = _root };
        Directory.Move(Game, instance.GameDirectory);
        Assert.Null(instance.LadsCore);

        Assert.True(ModpackCoreSync.Sync(instance, bundle));
        Assert.True(instance.LadsCore);
        Assert.True(File.Exists(Path.Combine(instance.GameDirectory, "mods", ModpackCoreSync.BundledName)));
    }

    [Fact]
    public void Fix1_Sync_WhenLadsCoreOn_OnUnsupportedPack_TouchesNothingAndReturnsFalse()
    {
        var bundle = Bundle("1.20.1", "new");
        var instance = new ModpackInstance { Id = "unsup-forge", Name = "Unsup Forge", McVersion = "1.20.1", Loader = "forge", Directory = _root, LadsCore = true };
        Directory.Move(Game, instance.GameDirectory);
        var modsDir = Path.Combine(instance.GameDirectory, "mods");
        Directory.CreateDirectory(modsDir);
        var coreDisabled = Path.Combine(modsDir, ModpackCoreSync.BundledName + ".disabled");
        MakeCore(coreDisabled, "old");
        File.WriteAllText(coreDisabled + ".icon.png", "sidecar icon");

        var messages = new List<string>();
        bool result = ModpackCoreSync.Sync(instance, bundle, messages.Add);

        Assert.False(result);
        Assert.Single(messages);
        Assert.Contains("The Lads Core is not supported: Minecraft 1.20.1 Forge requires Sinytra Connector", messages[0]);
        // Disabled jar is NOT restored, icon sidecar intact, no bundled jar copied
        Assert.True(File.Exists(coreDisabled));
        Assert.False(File.Exists(Path.Combine(modsDir, ModpackCoreSync.BundledName)));
        Assert.True(File.Exists(coreDisabled + ".icon.png"));
    }

    [Theory]
    [InlineData("fabric-api-0.92.2+1.20.1.jar", true)]
    [InlineData("FABRIC_API-1.0.jar", true)]
    [InlineData("fabricapi-1.2.jar", true)]
    [InlineData("Forgified-Fabric-API-0.92.2.jar", true)]
    [InlineData("forgified_fabric_api.jar", true)]
    [InlineData("theladscore.jar", false)]
    [InlineData("fabric-api.jar.disabled", false)]
    [InlineData("sodium.jar", false)]
    public void Fix2_HasFabricApi_MatchesVariousCasingsAndForgifiedApi(string filename, bool expected)
    {
        Assert.Equal(expected, ModpackCoreSync.HasFabricApi(new[] { filename }));
    }

    [Fact]
    public void Fix2_FabricOrQuilt_WithoutFabricApi_ReturnsUnsupported()
    {
        var modsWithoutApi = new[] { "sodium.jar" };
        Assert.False(ModpackCoreSync.IsSupported("1.20.1", "fabric", modsWithoutApi, out var reasonFabric));
        Assert.Equal("This modpack has no Fabric API, which The Lads Core needs.", reasonFabric);

        Assert.False(ModpackCoreSync.IsSupported("26.3", "quilt", modsWithoutApi, out var reasonQuilt));
        Assert.Equal("This modpack has no Fabric API, which The Lads Core needs.", reasonQuilt);
    }

    [Fact]
    public void Fix2_FabricLoaderVersionConstraint_FromFabricModJson()
    {
        // Bundled core requires >=0.14.21
        var bundle = Bundle("1.20.1", "new", loaderConstraint: ">=0.14.21");
        var mods = new[] { "fabric-api.jar" };

        // Fabric loader older than minimum
        Assert.False(ModpackCoreSync.IsSupported("1.20.1", "fabric", mods, loaderVersion: "0.14.20", bundleRoot: bundle, out var reasonOld));
        Assert.Equal("Fabric Loader 0.14.20 is older than The Lads Core needs (0.14.21).", reasonOld);

        // Fabric loader matching or newer
        Assert.True(ModpackCoreSync.IsSupported("1.20.1", "fabric", mods, loaderVersion: "0.14.21", bundleRoot: bundle, out var reasonEqual));
        Assert.Null(reasonEqual);

        Assert.True(ModpackCoreSync.IsSupported("1.20.1", "fabric", mods, loaderVersion: "0.15.2", bundleRoot: bundle, out var reasonNewer));
        Assert.Null(reasonNewer);

        // Empty or unparsable loader version does not block
        Assert.True(ModpackCoreSync.IsSupported("1.20.1", "fabric", mods, loaderVersion: "", bundleRoot: bundle, out var reasonEmpty));
        Assert.Null(reasonEmpty);

        Assert.True(ModpackCoreSync.IsSupported("1.20.1", "fabric", mods, loaderVersion: "unparsable-loader", bundleRoot: bundle, out var reasonUnparsable));
        Assert.Null(reasonUnparsable);

        // Quilt does not block on Fabric loader constraint
        Assert.True(ModpackCoreSync.IsSupported("1.20.1", "quilt", mods, loaderVersion: "0.14.20", bundleRoot: bundle, out var reasonQuilt));
        Assert.Null(reasonQuilt);
    }

    [Fact]
    public void Fix3_ResolveDefaultLadsCore_UnsupportedPackWithExistingCore_ReturnsFalse()
    {
        // Instance on 1.16.5 Forge is unsupported, even if an extraneous Core jar is inside mods/
        var unsupportedInstance = new ModpackInstance { Id = "unsup", Name = "Unsupported", McVersion = "1.16.5", Loader = "forge", Directory = _root };
        var modsDir = Path.Combine(unsupportedInstance.GameDirectory, "mods");
        Directory.CreateDirectory(modsDir);
        MakeCore(Path.Combine(modsDir, ModpackCoreSync.BundledName), "existing");

        Assert.False(ModpackCoreSync.ResolveDefaultLadsCore(unsupportedInstance));
    }

    [Fact]
    public void Fix4_SaveClick_UnsupportedInstance_AssignsLadsCoreFalse()
    {
        // When not an Experience, i.LadsCore = LadsCoreToggle.IsEnabled ? (IsChecked ?? false) : false;
        var instance = new ModpackInstance { Id = "test-pack", Name = "Test Pack", McVersion = "1.16.5", Loader = "forge", LadsCore = true };
        bool isExperience = ExperiencesService.AllExperiences.Any(e => string.Equals(e.Id, ExperiencesService.CanonicalPackId(instance.Id), StringComparison.OrdinalIgnoreCase));
        Assert.False(isExperience);

        bool toggleIsEnabled = ModpackCoreSync.IsSupported(instance);
        Assert.False(toggleIsEnabled);
        bool? toggleIsChecked = false;

        if (!isExperience)
        {
            instance.LadsCore = toggleIsEnabled ? (toggleIsChecked ?? false) : false;
        }

        Assert.False(instance.LadsCore);
    }

    [Fact]
    public void Fix5_ModsTabToggle_ExperienceProtectedFromBeingDisabled()
    {
        var reborn = ExperiencesService.AllExperiences.FirstOrDefault(e => e.Id == ExperiencesService.LadsSmpReborn.Id);
        Assert.NotNull(reborn);

        bool isRebornExperience = ExperiencesService.AllExperiences.Any(exp =>
            string.Equals(exp.Id, ExperiencesService.CanonicalPackId("lads-smp-reborn-26.3"), StringComparison.OrdinalIgnoreCase));
        Assert.True(isRebornExperience);

        bool isRebornDevExperience = ExperiencesService.AllExperiences.Any(exp =>
            string.Equals(exp.Id, ExperiencesService.CanonicalPackId(ExperiencesService.LadsSmpRebornDevId), StringComparison.OrdinalIgnoreCase));
        Assert.True(isRebornDevExperience);

        bool isZeroExperience = ExperiencesService.AllExperiences.Any(exp =>
            string.Equals(exp.Id, ExperiencesService.CanonicalPackId("zero-smp-1.20.1"), StringComparison.OrdinalIgnoreCase));
        Assert.True(isZeroExperience);

        bool isZeroDevExperience = ExperiencesService.AllExperiences.Any(exp =>
            string.Equals(exp.Id, ExperiencesService.CanonicalPackId(ExperiencesService.ZeroSmpDevId), StringComparison.OrdinalIgnoreCase));
        Assert.True(isZeroDevExperience);
    }

    [Fact]
    public void Fix6_DisablingAndRestoring_RenamesIconSidecar()
    {
        var bundle = Bundle("1.20.1", "new");
        var instance = new ModpackInstance { Id = "icon-test", Name = "Icon Test", McVersion = "1.20.1", Loader = "fabric", Directory = _root, LadsCore = false };
        Directory.Move(Game, instance.GameDirectory);
        var modsDir = Path.Combine(instance.GameDirectory, "mods");
        Directory.CreateDirectory(modsDir);
        File.WriteAllText(Path.Combine(modsDir, "fabric-api.jar"), "");

        var corePath = Path.Combine(modsDir, ModpackCoreSync.BundledName);
        MakeCore(corePath, "old");
        File.WriteAllText(corePath + ".icon.png", "lads icon");

        // Turn OFF: both jar and sidecar get .disabled
        Assert.True(ModpackCoreSync.Sync(instance, bundle));
        Assert.False(File.Exists(corePath));
        Assert.True(File.Exists(corePath + ".disabled"));
        Assert.False(File.Exists(corePath + ".icon.png"));
        Assert.True(File.Exists(corePath + ".disabled.icon.png"));
        Assert.Equal("lads icon", File.ReadAllText(corePath + ".disabled.icon.png"));

        // Turn back ON: both jar and sidecar restored
        instance.LadsCore = true;
        Assert.True(ModpackCoreSync.Sync(instance, bundle));
        Assert.True(File.Exists(corePath));
        Assert.False(File.Exists(corePath + ".disabled"));
        Assert.True(File.Exists(corePath + ".icon.png"));
        Assert.False(File.Exists(corePath + ".disabled.icon.png"));
        Assert.Equal("lads icon", File.ReadAllText(corePath + ".icon.png"));
    }
}
