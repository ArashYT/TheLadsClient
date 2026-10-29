using System.Diagnostics;
using System.IO.Compression;
using System.Security.Cryptography;
using CmlLib.Core.Auth;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>Play on another launcher's instance runs the modpack pipeline, and nothing but the game writes to that instance's folder.</summary>
public sealed class ExternalLaunchTests
{
    private const string Token = "secret-access-token-0123456789";

    [Fact]
    public async Task PrepareWritesNothingToAnExternalInstanceButDoesToAnOwnOne()
    {
        using var dir = new TestDirectory();
        string data = Path.Combine(dir.Path, "data");
        FakeVersion(data, "26.3");
        var java = new FakeJava(dir.Path);
        var status = new List<string>();

        string external = Seed(Path.Combine(dir.Path, "curseforge", "Instances", "Pack"));
        var before = Snapshot(external);
        var ext = new ModpackInstance
        {
            Id = "external-test", Name = "Pack", McVersion = "26.3", Loader = "vanilla", Directory = external, ExternalGameDirectory = external,
            MaxRamMb = 3072, MinRamMb = 768, JvmArguments = new[] { "-Xss4m", "-Dpack.flag=on" }
        };
        using (var process = await ModpackLauncher.PrepareAsync(data, ext, Session(), Settings(), java, new HttpClient(), status.Add, CancellationToken.None))
        {
            string args = Args(process);
            Assert.Contains("-Xmx3072m", args);
            Assert.Contains("-Xms768m", args);
            Assert.Contains("-Xss4m", args);
            Assert.Contains("-Dpack.flag=on", args);
            Assert.Contains("-XX:+DisableAttachMechanism", args); // the security flags stay
            Assert.DoesNotContain("-XX:+UseG1GC", args); // its own JVM arguments: no Lads GC tuning next to them
            Assert.Contains(external, args); // --gameDir is the instance's own folder
            Assert.Equal(java.Path, process.StartInfo.FileName);
        }
        Assert.Throws<InvalidOperationException>(() => Modpacks.Save(ext));
        Assert.Equal(before, Snapshot(external)); // no instance.json, config sync, renderer, jar renames, switch files
        Assert.DoesNotContain(status, s => s.Contains(Token));

        // Control: the same files as a Lads instance get the Lads steps, so the snapshot above would have seen them.
        var own = new ModpackInstance { Id = "own", Name = "Own", McVersion = "26.3", Loader = "vanilla", Directory = Path.Combine(data, "instances", "own") };
        Seed(own.GameDirectory);
        var ownBefore = Snapshot(own.GameDirectory);
        using (await ModpackLauncher.PrepareAsync(data, own, Session(), Settings(), java, new HttpClient(), null, CancellationToken.None)) { }
        Assert.NotEqual(ownBefore, Snapshot(own.GameDirectory));
        Assert.True(File.Exists(Path.Combine(own.GameDirectory, "mods", "iris.jar.disabled"))); // suspended on Vulkan
        Assert.True(File.Exists(Path.Combine(own.GameDirectory, VersionSwitch.VersionsFile)));
        Assert.True(File.Exists(Path.Combine(own.GameDirectory, GraphicsRenderer.StateFile)));
    }

    [Fact]
    public async Task UsesThePinnedJavaOnlyWhenItIsTheVersionsMajor()
    {
        using var dir = new TestDirectory();
        string data = Path.Combine(dir.Path, "data");
        FakeVersion(data, "26.3");
        string game = Seed(Path.Combine(dir.Path, "prism", "instances", "A", "minecraft"));
        string jdk = Path.Combine(dir.Path, "jdk-25", "bin", "javaw.exe");
        Directory.CreateDirectory(Path.GetDirectoryName(jdk)!);
        File.WriteAllText(jdk, "");
        var instance = new ModpackInstance { Id = "external-a", Name = "A", McVersion = "26.3", Loader = "vanilla", Directory = Path.GetDirectoryName(game)!, ExternalGameDirectory = game, JavaPath = jdk };

        using (var p = await ModpackLauncher.PrepareAsync(data, instance, Session(), Settings(), new FakeJava(dir.Path, jdkMajor: 25), new HttpClient(), null, CancellationToken.None))
            Assert.Equal(jdk, p.StartInfo.FileName);
        var other = new FakeJava(dir.Path, jdkMajor: 21);
        using (var p = await ModpackLauncher.PrepareAsync(data, instance, Session(), Settings(), other, new HttpClient(), null, CancellationToken.None))
        {
            Assert.Equal(other.Path, p.StartInfo.FileName); // a Java 21 pin cannot run 26.3: ours of the right major
            Assert.Contains("-XX:+UseG1GC", Args(p)); // no JVM arguments of its own: the Lads tuning applies
        }
    }

    [Fact]
    public async Task PrepareAsync_RemovesJdkJavaOptionsAndOtherJavaOptionVariables()
    {
        Environment.SetEnvironmentVariable("JDK_JAVA_OPTIONS", "-javaagent:malware.jar");
        Environment.SetEnvironmentVariable("JAVA_TOOL_OPTIONS", "-javaagent:malware.jar");
        Environment.SetEnvironmentVariable("_JAVA_OPTIONS", "-javaagent:malware.jar");
        try
        {
            using var dir = new TestDirectory();
            string data = Path.Combine(dir.Path, "data");
            FakeVersion(data, "26.3");
            var java = new FakeJava(dir.Path);
            var instance = new ModpackInstance
            {
                Id = "test-pack", Name = "Test Pack", McVersion = "26.3", Loader = "vanilla",
                Directory = Path.Combine(data, "instances", "test-pack")
            };
            using var process = await ModpackLauncher.PrepareAsync(data, instance, Session(), Settings(), java, new HttpClient(), null, CancellationToken.None);
            Assert.DoesNotContain(process.StartInfo.Environment.Keys, k => GameSession.JavaOptionVariables.Contains(k, StringComparer.OrdinalIgnoreCase));
            Assert.False(process.StartInfo.Environment.ContainsKey("JDK_JAVA_OPTIONS"));
        }
        finally
        {
            Environment.SetEnvironmentVariable("JDK_JAVA_OPTIONS", null);
            Environment.SetEnvironmentVariable("JAVA_TOOL_OPTIONS", null);
            Environment.SetEnvironmentVariable("_JAVA_OPTIONS", null);
        }
    }


    // ── Fixtures ────────────────────────────────────────────────────────────

    private static MSession Session() => new() { Username = "LadsQA", UUID = "00000000000000000000000000000001", AccessToken = Token, UserType = "msa" };

    private static LauncherSettings Settings() => new() { AutoDetectJava = true, QuickLaunch = true, MaxRamMb = 4096, MinRamMb = 512, FullscreenOnLaunch = false };

    /// <summary>A version manifest in the modpack cache, marked verified, so PrepareAsync builds the process offline.</summary>
    private static void FakeVersion(string data, string id)
    {
        string folder = Path.Combine(ModpackLauncher.CacheRoot(data), "versions", id);
        Directory.CreateDirectory(folder);
        File.WriteAllText(Path.Combine(folder, id + ".json"), $$$"""
            {"id":"{{{id}}}","type":"release","mainClass":"net.minecraft.client.main.Main","javaVersion":{"component":"java-runtime-epsilon","majorVersion":25},
             "assetIndex":{"id":"30","sha1":"0","size":1,"totalSize":1,"url":"https://example.invalid/30.json"},"assets":"30","libraries":[],
             "arguments":{"game":["--username","${auth_player_name}","--gameDir","${game_directory}","--accessToken","${auth_access_token}"],"jvm":["-cp","${classpath}"]}}
            """);
        File.WriteAllText(Path.Combine(folder, ".lads-verified"), "test");
    }

    /// <summary>An instance a player has used: a world, options, a config, and Iris (which a Lads 26.3 launch suspends on Vulkan).</summary>
    private static string Seed(string game)
    {
        Directory.CreateDirectory(Path.Combine(game, "mods"));
        Directory.CreateDirectory(Path.Combine(game, "saves", "World"));
        Directory.CreateDirectory(Path.Combine(game, "config"));
        File.WriteAllText(Path.Combine(game, "saves", "World", "level.dat"), "world");
        File.WriteAllText(Path.Combine(game, "options.txt"), "fov:0.0\nrenderDistance:8\n");
        File.WriteAllText(Path.Combine(game, "config", "fml.toml"), "earlyWindowControl = false\n");
        using (var zip = ZipFile.Open(Path.Combine(game, "mods", "iris.jar"), ZipArchiveMode.Create))
        using (var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open()))
            writer.Write("""{"schemaVersion":1,"id":"iris","version":"1.8.0","name":"Iris"}""");
        return game;
    }

    /// <summary>Every file and folder under <paramref name="root"/> with its SHA-256 and write time.</summary>
    private static string Snapshot(string root) => string.Join("\n", Directory.EnumerateFileSystemEntries(root, "*", SearchOption.AllDirectories)
        .Order(StringComparer.Ordinal)
        .Select(p => Path.GetRelativePath(root, p) + (File.Exists(p) ? $" {Convert.ToHexString(SHA256.HashData(File.ReadAllBytes(p)))} {File.GetLastWriteTimeUtc(p).Ticks}" : "/")));

    private static string Args(Process p) => p.StartInfo.ArgumentList.Count > 0 ? string.Join(' ', p.StartInfo.ArgumentList) : p.StartInfo.Arguments;

    private sealed class FakeJava : IJavaService
    {
        private readonly int? jdkMajor;
        public string Path { get; }
        public FakeJava(string root, int? jdkMajor = null)
        {
            this.jdkMajor = jdkMajor;
            Path = System.IO.Path.Combine(root, "lads-runtime", "bin", "javaw.exe");
            Directory.CreateDirectory(System.IO.Path.GetDirectoryName(Path)!);
            File.WriteAllText(Path, "");
        }
        public Task<string?> DetectInstalledJavaAsync(int majorVersion) => Task.FromResult<string?>(Path);
        public Task<string> EnsureJavaAsync(int majorVersion, IProgress<double>? progress = null, Action<string>? statusCallback = null, CancellationToken cancellationToken = default) => Task.FromResult(Path);
        public Task<string> DownloadAndInstallAdoptiumJavaAsync(int majorVersion, IProgress<double>? progress = null, Action<string>? statusCallback = null, CancellationToken cancellationToken = default) => Task.FromResult(Path);
        public IReadOnlyList<string> ScanAllSystemJavas() => new[] { Path };
        public int? GetJavaMajorVersion(string javaExecutablePath) => javaExecutablePath == Path ? 25 : jdkMajor;
    }
}
