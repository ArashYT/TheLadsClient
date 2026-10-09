using System;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ExperiencesServiceTests : IDisposable
{
    private readonly string _tempDir;

    public ExperiencesServiceTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), "thelads_exp_test_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        try { Directory.Delete(_tempDir, true); } catch { }
    }

    [Fact]
    public void ZeroSmp_Metadata_Is_Valid()
    {
        var exp = ExperiencesService.ZeroSmp;
        Assert.Equal("zero-smp-1.20.1", exp.Id);
        Assert.Equal("Zero SMP", exp.Title);
        Assert.Equal("1.20.1", exp.McVersion);
        Assert.Equal("forge", exp.Loader);
        Assert.Equal("47.4.10", exp.LoaderVersion);
        Assert.Equal("nikzerosmp.my.pebble.host", exp.ServerAddress);
        Assert.True(exp.ModCount > 100);
        Assert.True(exp.RecommendedRamMb >= 4096);
        Assert.Contains(exp, ExperiencesService.AllExperiences);
    }

    [Fact]
    public void InstanceDirectory_CalculatesCorrectPath()
    {
        var path = ExperiencesService.InstanceDirectory(_tempDir, "zero-smp-1.20.1");
        var expected = Path.Combine(_tempDir, "instances", "zero-smp-1.20.1");
        Assert.Equal(expected, path);
    }

    [Fact]
    public void PackSource_LivesInTheDataFolder_NotInAFixedUserPath()
    {
        Assert.Equal(Path.Combine(_tempDir, "experiences", "zero-smp-1.20.1", "pack.zip"), ExperiencesService.PackArchivePath(_tempDir, "zero-smp-1.20.1"));
        Assert.Equal(Path.Combine(_tempDir, "experiences", "zero-smp-1.20.1", "source"), ExperiencesService.PackSourceDirectory(_tempDir, "zero-smp-1.20.1"));
    }

    [Fact]
    public void IsInstalled_ReturnsFalse_WhenNotInstalled()
    {
        Assert.False(ExperiencesService.IsInstalled(_tempDir, "zero-smp-1.20.1"));
    }

    [Fact]
    public void IsInstalled_ReturnsTrue_WhenValidInstanceExists()
    {
        var instDir = ExperiencesService.InstanceDirectory(_tempDir, "zero-smp-1.20.1");
        var modsDir = Path.Combine(instDir, "minecraft", "mods");
        Directory.CreateDirectory(modsDir);
        File.WriteAllText(Path.Combine(instDir, "instance.json"), "{}");
        File.WriteAllText(Path.Combine(modsDir, "testmod.jar"), "dummy");

        Assert.True(ExperiencesService.IsInstalled(_tempDir, "zero-smp-1.20.1"));
    }

    [Fact]
    public void SyncLadsCore_LeavesAnInstanceWithoutACoreAlone()
    {
        var instDir = ExperiencesService.InstanceDirectory(_tempDir, "zero-smp-1.20.1");
        var modsDir = Path.Combine(instDir, "minecraft", "mods");
        Directory.CreateDirectory(modsDir);

        Assert.False(ExperiencesService.SyncLadsCore(_tempDir, "zero-smp-1.20.1"));
        Assert.Empty(Directory.EnumerateFiles(modsDir));
    }

    // ── Pack source ─────────────────────────────────────────────────────────

    private static byte[] PackZip(params (string Name, string Text)[] files)
    {
        using var stream = new MemoryStream();
        using (var zip = new ZipArchive(stream, ZipArchiveMode.Create, leaveOpen: true))
            foreach (var (name, text) in files)
                using (var w = new StreamWriter(zip.CreateEntry(name).Open(), new UTF8Encoding(false))) w.Write(text);
        return stream.ToArray();
    }

    private static readonly (string, string)[] SamplePack =
    {
        ("pack.json", "{\"packId\":\"zero-smp-1.20.1\",\"packVersion\":\"1.0.1\",\"appliedPatches\":[\"1.0.1\"]}"),
        ("overrides/config/foo.toml", "a=1"),
        ("overrides/mods/alpha.jar", "alpha"),
        ("overrides/mods/beta.jar", "beta"),
        ("overrides/icon.png", "png"),
        ("overrides/hs_err_pid1.log", "crash"),
        ("all_mods/gamma.jar", "gamma"),
        ("all_mods/pack.zip", "resourcepack")
    };

    [Fact]
    public async Task Install_FromADroppedArchive_ExtractsItIntoTheDataFolder()
    {
        var archive = ExperiencesService.PackArchivePath(_tempDir, "zero-smp-1.20.1");
        Directory.CreateDirectory(Path.GetDirectoryName(archive)!);
        File.WriteAllBytes(archive, PackZip(SamplePack));

        var instance = await ExperiencesService.InstallZeroSmpAsync(_tempDir, null, CancellationToken.None);

        var game = instance.GameDirectory;
        Assert.Equal("1.0.1", instance.PackVersion);
        Assert.Equal("a=1", File.ReadAllText(Path.Combine(game, "config", "foo.toml")));
        Assert.True(File.Exists(Path.Combine(game, "mods", "alpha.jar")));
        Assert.True(File.Exists(Path.Combine(game, "mods", "gamma.jar")));
        Assert.True(File.Exists(Path.Combine(game, "resourcepacks", "pack.zip")));
        Assert.True(File.Exists(Path.Combine(instance.Directory, "icon.png")));
        Assert.False(File.Exists(Path.Combine(game, "hs_err_pid1.log")));
        Assert.True(ExperiencesService.IsInstalled(_tempDir, "zero-smp-1.20.1"));
        // The archive already contains its patches: they are recorded, not applied again.
        Assert.Contains("1.0.1", ModpackPatchEngine.GetAppliedPatches(instance.Directory));
        Assert.True(Directory.Exists(ExperiencesService.PackSourceDirectory(_tempDir, "zero-smp-1.20.1")));
    }

    [Fact]
    public async Task Install_FromAnExtractedSourceFolder_NeedsNoArchive()
    {
        var source = Path.Combine(ExperiencesService.PackSourceDirectory(_tempDir, "zero-smp-1.20.1"), "overrides", "mods");
        Directory.CreateDirectory(source);
        File.WriteAllText(Path.Combine(source, "alpha.jar"), "alpha");

        var instance = await ExperiencesService.InstallZeroSmpAsync(_tempDir, null, CancellationToken.None);

        Assert.True(File.Exists(Path.Combine(instance.GameDirectory, "mods", "alpha.jar")));
        Assert.Equal("1.0.0", instance.PackVersion);
    }

    [Fact]
    public async Task Install_WithoutAnyPack_SaysWhereToPutTheArchive()
    {
        var error = await Assert.ThrowsAsync<FileNotFoundException>(() => ExperiencesService.InstallZeroSmpAsync(_tempDir, null, CancellationToken.None));

        Assert.Contains(ExperiencesService.PackArchivePath(_tempDir, "zero-smp-1.20.1"), error.Message);
        Assert.False(ExperiencesService.IsInstalled(_tempDir, "zero-smp-1.20.1"));
    }

    [Fact]
    public async Task NewerArchive_ReplacesTheExtractedCopy()
    {
        var archive = ExperiencesService.PackArchivePath(_tempDir, "zero-smp-1.20.1");
        Directory.CreateDirectory(Path.GetDirectoryName(archive)!);
        File.WriteAllBytes(archive, PackZip(("overrides/mods/old.jar", "old")));
        await ExperiencesService.InstallZeroSmpAsync(_tempDir, null, CancellationToken.None);

        File.WriteAllBytes(archive, PackZip(("overrides/mods/new.jar", "new"), ("overrides/config/x.toml", "x")));
        var instance = await ExperiencesService.InstallZeroSmpAsync(_tempDir, null, CancellationToken.None);

        var source = ExperiencesService.PackSourceDirectory(_tempDir, "zero-smp-1.20.1");
        Assert.False(File.Exists(Path.Combine(source, "overrides", "mods", "old.jar")));
        Assert.True(File.Exists(Path.Combine(instance.GameDirectory, "mods", "new.jar")));
    }

    [Fact]
    public async Task ArchiveEntryOutsideTheSource_IsRefused()
    {
        var archive = ExperiencesService.PackArchivePath(_tempDir, "zero-smp-1.20.1");
        Directory.CreateDirectory(Path.GetDirectoryName(archive)!);
        File.WriteAllBytes(archive, PackZip(("overrides/mods/a.jar", "a"), ("../evil.txt", "x")));

        await Assert.ThrowsAsync<InvalidDataException>(() => ExperiencesService.InstallZeroSmpAsync(_tempDir, null, CancellationToken.None));

        Assert.False(File.Exists(Path.Combine(_tempDir, "experiences", "evil.txt")));
        Assert.False(Directory.Exists(ExperiencesService.PackSourceDirectory(_tempDir, "zero-smp-1.20.1")));
    }

    private sealed class FakeHandler : HttpMessageHandler
    {
        public byte[] Body = Array.Empty<byte>();
        public int Requests;
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            Requests++;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(Body) });
        }
    }

    [Fact]
    public async Task FreshInstall_DownloadsTheArchive_AndChecksItsHash()
    {
        var zip = PackZip(SamplePack);
        var handler = new FakeHandler { Body = zip };
        using var http = new HttpClient(handler);
        var exp = ExperiencesService.ZeroSmp with
        {
            PackArchiveUrl = "https://packs.example.invalid/zero-smp.zip",
            PackArchiveSha256 = Convert.ToHexString(SHA256.HashData(zip))
        };

        var source = await ExperiencesService.EnsurePackSourceAsync(_tempDir, exp, http, null, CancellationToken.None);

        Assert.True(File.Exists(Path.Combine(source, "overrides", "mods", "alpha.jar")));
        Assert.True(File.Exists(ExperiencesService.PackArchivePath(_tempDir, exp.Id)));
        Assert.False(File.Exists(ExperiencesService.PackArchivePath(_tempDir, exp.Id) + ".part"));

        // Cached: a second call does not download again.
        await ExperiencesService.EnsurePackSourceAsync(_tempDir, exp, http, null, CancellationToken.None);
        Assert.Equal(1, handler.Requests);
    }

    [Fact]
    public async Task DownloadWithTheWrongHash_IsRejected_AndNothingIsKept()
    {
        using var http = new HttpClient(new FakeHandler { Body = PackZip(SamplePack) });
        var exp = ExperiencesService.ZeroSmp with { PackArchiveUrl = "https://packs.example.invalid/zero-smp.zip", PackArchiveSha256 = new string('0', 64) };

        await Assert.ThrowsAsync<InvalidDataException>(() => ExperiencesService.EnsurePackSourceAsync(_tempDir, exp, http, null, CancellationToken.None));

        Assert.False(File.Exists(ExperiencesService.PackArchivePath(_tempDir, exp.Id)));
        Assert.False(File.Exists(ExperiencesService.PackArchivePath(_tempDir, exp.Id) + ".part"));
    }

    [Fact]
    public async Task PlainHttpUrl_IsNotDownloaded()
    {
        var handler = new FakeHandler { Body = PackZip(SamplePack) };
        using var http = new HttpClient(handler);
        var exp = ExperiencesService.ZeroSmp with { PackArchiveUrl = "http://packs.example.invalid/zero-smp.zip" };

        await Assert.ThrowsAsync<FileNotFoundException>(() => ExperiencesService.EnsurePackSourceAsync(_tempDir, exp, http, null, CancellationToken.None));

        Assert.Equal(0, handler.Requests);
    }

    // ── Patches ─────────────────────────────────────────────────────────────

    [Fact]
    public async Task PendingPatches_AreAppliedOnce_OldestFirst()
    {
        var root = ExperiencesService.PackRoot(_tempDir, "zero-smp-1.20.1");
        var patches = Path.Combine(root, "patches");
        Directory.CreateDirectory(patches);
        File.WriteAllText(Path.Combine(patches, "update-1.0.2.json"),
            "{\"packId\":\"zero-smp-1.20.1\",\"version\":\"1.0.2\",\"keybinds\":{\"defaults\":{\"key_second\":\"key.keyboard.k\"}}}");
        File.WriteAllText(Path.Combine(patches, "update-1.0.10.json"),
            "{\"packId\":\"zero-smp-1.20.1\",\"version\":\"1.0.10\",\"keybinds\":{\"defaults\":{\"key_third\":\"key.keyboard.l\"}}}");
        File.WriteAllText(Path.Combine(patches, "update-9.0.0.json"), "{\"packId\":\"some-other-pack\",\"version\":\"9.0.0\"}");
        File.WriteAllText(Path.Combine(patches, "update-bad.json"), "not json");
        var instDir = ExperiencesService.InstanceDirectory(_tempDir, "zero-smp-1.20.1");
        Directory.CreateDirectory(Path.Combine(instDir, "minecraft"));

        var order = new System.Collections.Generic.List<string>();
        Assert.Equal(2, await ExperiencesService.ApplyPendingPatchesAsync(_tempDir, "zero-smp-1.20.1", null, m => order.Add(m), CancellationToken.None));
        Assert.Equal(0, await ExperiencesService.ApplyPendingPatchesAsync(_tempDir, "zero-smp-1.20.1", null, null, CancellationToken.None));

        Assert.Equal(new[] { "1.0.2", "1.0.10" }, ModpackPatchEngine.GetAppliedPatches(instDir));
        var options = File.ReadAllText(Path.Combine(instDir, "minecraft", "options.txt"));
        Assert.Contains("key_second:key.keyboard.k", options);
        Assert.Contains("key_third:key.keyboard.l", options);
        Assert.Contains("1.0.2", order[0]);
    }

    [Fact]
    public async Task PatchMods_AreHashChecked_AndOnlyWellFormedNamesAndHttpsUrlsAreUsed()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        var mods = Path.Combine(gameDir, "mods");
        Directory.CreateDirectory(mods);
        File.WriteAllText(Path.Combine(mods, "old.jar"), "old");
        File.WriteAllText(Path.Combine(mods, "off.jar.disabled"), "off");
        File.WriteAllText(Path.Combine(gameDir, "keep.txt"), "must survive"); // one level above mods/
        var jar = Encoding.UTF8.GetBytes("new mod");
        using var http = new HttpClient(new FakeHandler { Body = jar });
        var patch = new ModpackPatch
        {
            PackId = "p",
            Version = "1.0.1",
            Mods = new PatchMods
            {
                Remove = new() { "old.jar", "off.jar", @"..\keep.txt", "../keep.txt" },
                Add = new()
                {
                    new PatchModFile { FileName = "new.jar", Url = "https://cdn.example.invalid/new.jar", Sha1 = Convert.ToHexString(SHA1.HashData(jar)).ToLowerInvariant() },
                    new PatchModFile { FileName = "plain-http.jar", Url = "http://cdn.example.invalid/x.jar" },
                    new PatchModFile { FileName = @"..\escape.jar", Url = "https://cdn.example.invalid/x.jar" }
                }
            }
        };

        await ModpackPatchEngine.ApplyPatchAsync(_tempDir, patch, http, null);

        Assert.False(File.Exists(Path.Combine(mods, "old.jar")));
        Assert.False(File.Exists(Path.Combine(mods, "off.jar.disabled"))); // the switched-off copy goes too
        Assert.Equal(jar, File.ReadAllBytes(Path.Combine(mods, "new.jar")));
        Assert.False(File.Exists(Path.Combine(mods, "plain-http.jar")));
        Assert.False(File.Exists(Path.Combine(gameDir, "escape.jar")));
        Assert.True(File.Exists(Path.Combine(gameDir, "keep.txt")));
        Assert.Empty(Directory.GetFiles(mods, "*.part"));

        var bad = new ModpackPatch
        {
            PackId = "p",
            Version = "1.0.2",
            Mods = new PatchMods { Add = new() { new PatchModFile { FileName = "tampered.jar", Url = "https://cdn.example.invalid/t.jar", Sha1 = new string('0', 40) } } }
        };
        await Assert.ThrowsAsync<InvalidDataException>(() => ModpackPatchEngine.ApplyPatchAsync(_tempDir, bad, http, null));
        Assert.False(File.Exists(Path.Combine(mods, "tampered.jar")));
        Assert.DoesNotContain("1.0.2", ModpackPatchEngine.GetAppliedPatches(_tempDir)); // retried at the next launch
    }
}
