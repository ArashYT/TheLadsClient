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
    private static void MakeCore(string path, string build, byte[]? icon = null)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        using var zip = ZipFile.Open(path, ZipArchiveMode.Create);
        using (var w = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open(), new UTF8Encoding(false)))
            w.Write("{\"schemaVersion\":1,\"id\":\"theladscore\",\"version\":\"" + build + "\"" + (icon != null ? ",\"icon\":\"icon.png\"" : "") + "}");
        if (icon != null)
            using (var entry = zip.CreateEntry("icon.png").Open()) entry.Write(icon);
    }

    private string Bundle(string mc, string build, byte[]? icon = null)
    {
        var bundleRoot = Path.Combine(_root, "bundle");
        MakeCore(Path.Combine(bundleRoot, "game-mods", mc, "theladscore.jar"), build, icon);
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
    public void ModpackInstanceOverload_UsesItsGameFolderAndMinecraftVersion()
    {
        var bundle = Bundle("1.20.1", "new");
        MakeCore(Path.Combine(Mods, "theladscore.jar"), "old");
        var instance = new ModpackInstance { Id = "x", Name = "X", McVersion = "1.20.1", Directory = _root };
        Directory.Move(Game, instance.GameDirectory);

        Assert.True(ModpackCoreSync.Sync(instance, bundle));
    }
}
