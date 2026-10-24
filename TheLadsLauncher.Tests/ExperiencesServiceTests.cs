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
    public void LadsSmpReborn_Metadata_Is_Valid()
    {
        var exp = ExperiencesService.LadsSmpReborn;
        Assert.Equal("lads-smp-reborn-26.3", exp.Id);
        Assert.Equal("The Lads SMP Reborn", exp.Title);
        Assert.Equal("26.3", exp.McVersion);
        Assert.Equal("fabric", exp.Loader);
        Assert.Equal("0.19.5", exp.LoaderVersion);
        Assert.Equal("avares://TheLadsLauncher/Assets/lads_smp_reborn_icon.png", exp.IconAsset);
        Assert.Equal(335, exp.ModCount);
        Assert.Equal(8192, exp.RecommendedRamMb);
        Assert.StartsWith("https://", exp.PackArchiveUrl);
        Assert.Equal(64, exp.PackArchiveSha256.Length);
        Assert.Contains(exp, ExperiencesService.AllExperiences);
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
        Assert.StartsWith("https://", exp.PackArchiveUrl);
        Assert.Equal(64, exp.PackArchiveSha256.Length);
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
    public async Task StaleArchive_IsDownloadedAgain_WhenThePinnedHashChanges()
    {
        var oldZip = PackZip(SamplePack);
        var newZip = PackZip(SamplePack.Append(("overrides/mods/delta.jar", "delta")).ToArray());
        var handler = new FakeHandler { Body = oldZip };
        using var http = new HttpClient(handler);
        var exp = ExperiencesService.ZeroSmp with
        {
            PackArchiveUrl = "https://packs.example.invalid/zero-smp.zip",
            PackArchiveSha256 = Convert.ToHexString(SHA256.HashData(oldZip))
        };
        await ExperiencesService.EnsurePackSourceAsync(_tempDir, exp, http, null, CancellationToken.None);

        // A launcher update pins a new pack: the cached archive no longer matches, so it is replaced and re-extracted.
        handler.Body = newZip;
        var updated = exp with { PackArchiveSha256 = Convert.ToHexString(SHA256.HashData(newZip)) };
        var source = await ExperiencesService.EnsurePackSourceAsync(_tempDir, updated, http, null, CancellationToken.None);

        Assert.Equal(2, handler.Requests);
        Assert.True(File.Exists(Path.Combine(source, "overrides", "mods", "delta.jar")));
        Assert.Equal(newZip, File.ReadAllBytes(ExperiencesService.PackArchivePath(_tempDir, exp.Id)));
    }

    private sealed class OfflineHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token) =>
            throw new HttpRequestException("offline");
    }

    [Fact]
    public async Task StaleArchive_IsStillUsed_WhenTheNewPackCannotBeDownloaded()
    {
        var oldZip = PackZip(SamplePack);
        var archive = ExperiencesService.PackArchivePath(_tempDir, ExperiencesService.ZeroSmp.Id);
        Directory.CreateDirectory(Path.GetDirectoryName(archive)!);
        File.WriteAllBytes(archive, oldZip);
        using var http = new HttpClient(new OfflineHandler());
        var exp = ExperiencesService.ZeroSmp with { PackArchiveUrl = "https://packs.example.invalid/zero-smp.zip", PackArchiveSha256 = new string('A', 64) };

        var source = await ExperiencesService.EnsurePackSourceAsync(_tempDir, exp, http, null, CancellationToken.None);

        Assert.True(File.Exists(Path.Combine(source, "overrides", "mods", "alpha.jar")));
        Assert.Equal(oldZip, File.ReadAllBytes(archive));
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

    [Fact]
    public void ZeroSmpButtonState_ReflectsInstalledAndNotInstalledStates()
    {
        // 1. Installed: Show "Play"
        var installed = ExperiencesService.GetZeroSmpButtonState(installed: true);
        Assert.Equal("Play", installed.Text);
        Assert.True(installed.IsEnabled);
        Assert.Null(installed.Hint);

        // 2. Not installed: Show "Install & Play Zero SMP"
        var notInstalled = ExperiencesService.GetZeroSmpButtonState(installed: false);
        Assert.Equal("Install & Play Zero SMP", notInstalled.Text);
        Assert.True(notInstalled.IsEnabled);
        Assert.Null(notInstalled.Hint);

        // ExperiencesView delegate matches
        Assert.Equal("Play", TheLadsLauncher.Views.ExperiencesView.GetButtonState(installed: true).Text);
        Assert.Equal("Install & Play Zero SMP", TheLadsLauncher.Views.ExperiencesView.GetButtonState(installed: false).Text);

        // Running & busy states
        Assert.Equal("Game Running", ExperiencesService.GetZeroSmpButtonState(installed: true, isRunning: true).Text);
        Assert.False(ExperiencesService.GetZeroSmpButtonState(installed: true, isRunning: true).IsEnabled);
        Assert.False(ExperiencesService.GetZeroSmpButtonState(installed: true, isBusy: true).IsEnabled);

        // Multi-instance allowed: Play button remains enabled while already running (unless busy)
        var multiRunning = ExperiencesService.GetZeroSmpButtonState(installed: true, isRunning: true, isBusy: false, allowMultiInstance: true);
        Assert.Equal("Play", multiRunning.Text);
        Assert.True(multiRunning.IsEnabled);
        Assert.Equal("Play", TheLadsLauncher.Views.ExperiencesView.GetButtonState(installed: true, isRunning: true, isBusy: false, allowMultiInstance: true).Text);
        Assert.True(TheLadsLauncher.Views.ExperiencesView.GetButtonState(installed: true, isRunning: true, isBusy: false, allowMultiInstance: true).IsEnabled);
        Assert.False(ExperiencesService.GetZeroSmpButtonState(installed: true, isRunning: true, isBusy: true, allowMultiInstance: true).IsEnabled);
    }

    [Fact]
    public void EnsureZeroSmpServer_CreatesAndUpdatesServerList()
    {
        var gameDir = Path.Combine(_tempDir, "minecraft");
        Directory.CreateDirectory(gameDir);

        Assert.True(ExperiencesService.EnsureZeroSmpServer(gameDir));
        var serversFile = Path.Combine(gameDir, "servers.dat");
        Assert.True(File.Exists(serversFile));

        var list = ServerListFile.Read(serversFile);
        Assert.Single(list.Entries);
        Assert.Equal(ExperiencesService.ZeroSmp.Title, list.Entries[0].Name);
        Assert.Equal(ExperiencesService.ZeroSmp.ServerAddress, list.Entries[0].Ip);

        // Idempotent
        Assert.False(ExperiencesService.EnsureZeroSmpServer(gameDir));
        var repeatList = ServerListFile.Read(serversFile);
        Assert.Single(repeatList.Entries);
    }

    [Fact]
    public async Task InstallZeroSmpAsync_AutoAddsZeroSmpServerAndPreservesExistingServers()
    {
        var archive = ExperiencesService.PackArchivePath(_tempDir, "zero-smp-1.20.1");
        Directory.CreateDirectory(Path.GetDirectoryName(archive)!);
        File.WriteAllBytes(archive, PackZip(SamplePack));

        // Pre-create user's servers.dat with a custom server
        var instDir = ExperiencesService.InstanceDirectory(_tempDir, "zero-smp-1.20.1");
        var gameDir = Path.Combine(instDir, "minecraft");
        Directory.CreateDirectory(gameDir);

        var userList = ServerListFile.CreateEmpty();
        userList.Add(new NbtCompound
        {
            ["name"] = new NbtString("Custom SMP"),
            ["ip"] = new NbtString("play.customsmp.net")
        });
        await userList.WriteAsync(Path.Combine(gameDir, "servers.dat"));

        var instance = await ExperiencesService.InstallZeroSmpAsync(_tempDir, null, CancellationToken.None);

        var serversFile = Path.Combine(instance.GameDirectory, "servers.dat");
        Assert.True(File.Exists(serversFile));

        var finalList = ServerListFile.Read(serversFile);
        Assert.Equal(2, finalList.Entries.Count);
        Assert.Equal("Custom SMP", finalList.Entries[0].Name);
        Assert.Equal("play.customsmp.net", finalList.Entries[0].Ip);
        Assert.Equal(ExperiencesService.ZeroSmp.Title, finalList.Entries[1].Name);
        Assert.Equal(ExperiencesService.ZeroSmp.ServerAddress, finalList.Entries[1].Ip);
    }

    [Fact]
    public async Task DevZeroSmpInstance_SeedsFromRegularInstanceAndKeepsSeparateDirectory()
    {
        var archive = ExperiencesService.PackArchivePath(_tempDir, "zero-smp-1.20.1");
        Directory.CreateDirectory(Path.GetDirectoryName(archive)!);
        File.WriteAllBytes(archive, PackZip(SamplePack));

        var regular = await ExperiencesService.InstallZeroSmpAsync(_tempDir, null, CancellationToken.None);
        Assert.Equal("zero-smp-1.20.1", regular.Id);
        Assert.Equal("zero-smp-1.20.1-dev", ExperiencesService.ActiveZeroSmpInstanceId(isDevLauncher: true));
        Assert.Equal("zero-smp-1.20.1", ExperiencesService.ActiveZeroSmpInstanceId(isDevLauncher: false));
        Assert.Equal("zero-smp-1.20.1", ExperiencesService.CanonicalPackId(ExperiencesService.ZeroSmpDevId));

        ExperiencesService.EnsureDevZeroSmpSeeded(_tempDir);
        Assert.True(ExperiencesService.IsInstalled(_tempDir, ExperiencesService.ZeroSmpDevId));

        var dev = ExperiencesService.GetInstance(_tempDir, ExperiencesService.ZeroSmpDevId);
        Assert.NotNull(dev);
        Assert.Equal("Zero SMP (Dev)", dev!.Name);
        Assert.NotEqual(regular.GameDirectory, dev.GameDirectory);
    }

    [Fact]
    public void LadsSmpRebornButtonState_ReflectsInstalledAndNotInstalledStates()
    {
        var installed = ExperiencesService.GetLadsSmpRebornButtonState(installed: true);
        Assert.Equal("Play", installed.Text);
        Assert.True(installed.IsEnabled);

        var notInstalled = ExperiencesService.GetLadsSmpRebornButtonState(installed: false);
        Assert.Equal("Install & Play The Lads SMP Reborn", notInstalled.Text);
        Assert.True(notInstalled.IsEnabled);

        Assert.Equal("Play", TheLadsLauncher.Views.ExperiencesView.GetLadsSmpRebornButtonState(installed: true).Text);
        Assert.Equal("Install & Play The Lads SMP Reborn", TheLadsLauncher.Views.ExperiencesView.GetLadsSmpRebornButtonState(installed: false).Text);
        Assert.Equal("Game Running", ExperiencesService.GetLadsSmpRebornButtonState(installed: true, isRunning: true).Text);
        Assert.False(ExperiencesService.GetLadsSmpRebornButtonState(installed: true, isRunning: true).IsEnabled);
    }

    [Fact]
    public void ExperiencesRamConversion_UsesBinaryGigabytes()
    {
        Assert.Equal(1024, TheLadsLauncher.Views.ExperiencesView.RamGbToBinaryMb(1m, 4096));
        Assert.Equal(2048, TheLadsLauncher.Views.ExperiencesView.RamGbToBinaryMb(2m, 4096));
        Assert.Equal(6144, TheLadsLauncher.Views.ExperiencesView.RamGbToBinaryMb(6m, 4096));
        Assert.Equal(8192, TheLadsLauncher.Views.ExperiencesView.RamGbToBinaryMb(8m, 4096));

        Assert.Equal(1, TheLadsLauncher.Views.ExperiencesView.BinaryMbToRamGb(1024));
        Assert.Equal(2, TheLadsLauncher.Views.ExperiencesView.BinaryMbToRamGb(2048));
        Assert.Equal(6, TheLadsLauncher.Views.ExperiencesView.BinaryMbToRamGb(6144));
        Assert.Equal(8, TheLadsLauncher.Views.ExperiencesView.BinaryMbToRamGb(8192));
    }

    [Fact]
    public async Task DevLadsSmpRebornInstance_SeedsFromRegularInstanceAndKeepsSeparateDirectory()
    {
        var archive = ExperiencesService.PackArchivePath(_tempDir, "lads-smp-reborn-26.3");
        Directory.CreateDirectory(Path.GetDirectoryName(archive)!);
        File.WriteAllBytes(archive, PackZip(SamplePack));

        var regular = await ExperiencesService.InstallLadsSmpRebornAsync(_tempDir, null, CancellationToken.None);
        Assert.Equal("lads-smp-reborn-26.3", regular.Id);
        Assert.Equal("lads-smp-reborn-26.3-dev", ExperiencesService.ActiveLadsSmpRebornInstanceId(isDevLauncher: true));
        Assert.Equal("lads-smp-reborn-26.3", ExperiencesService.ActiveLadsSmpRebornInstanceId(isDevLauncher: false));
        Assert.Equal("lads-smp-reborn-26.3", ExperiencesService.CanonicalPackId(ExperiencesService.LadsSmpRebornDevId));

        ExperiencesService.EnsureDevLadsSmpRebornSeeded(_tempDir);
        Assert.True(ExperiencesService.IsInstalled(_tempDir, ExperiencesService.LadsSmpRebornDevId));

        var dev = ExperiencesService.GetInstance(_tempDir, ExperiencesService.LadsSmpRebornDevId);
        Assert.NotNull(dev);
        Assert.Equal("The Lads SMP Reborn (Dev)", dev!.Name);
        Assert.Equal("26.3", dev.McVersion);
        Assert.Equal("fabric", dev.Loader);
        Assert.Equal("0.19.5", dev.LoaderVersion);
        Assert.NotEqual(regular.GameDirectory, dev.GameDirectory);
    }

    [Fact]
    public async Task DownloadWithoutPublishedSha256_FailsClosed_WithoutSavingArchive()
    {
        var handler = new FakeHandler { Body = PackZip(SamplePack) };
        using var http = new HttpClient(handler);
        var exp = ExperiencesService.ZeroSmp with
        {
            PackArchiveUrl = "https://packs.example.invalid/zero-smp.zip",
            PackArchiveSha256 = ""
        };

        await Assert.ThrowsAsync<InvalidDataException>(() => ExperiencesService.EnsurePackSourceAsync(_tempDir, exp, http, null, CancellationToken.None));

        Assert.False(File.Exists(ExperiencesService.PackArchivePath(_tempDir, exp.Id)));
        Assert.False(File.Exists(ExperiencesService.PackArchivePath(_tempDir, exp.Id) + ".part"));
    }

    [Fact]
    public async Task InviteOnly_ShowsOnlyWhenBotSaysInvited_AndKeepsLastAnswerOffline()
    {
        var exp = ExperiencesService.LadsSmpReborn;
        Assert.True(await ExperiencesService.HasAccessAsync(_tempDir, ExperiencesService.ZeroSmp, Array.Empty<string>()));
        Assert.False(await ExperiencesService.HasAccessAsync(_tempDir, exp, Array.Empty<string>()));

        var routes = new RouteHandler();
        routes.Add(ExperiencesService.AccessEndpoint + exp.Id + "/access?u=alice&u=bob", Encoding.UTF8.GetBytes("{\"allowed\":true}"));
        Assert.True(await ExperiencesService.HasAccessAsync(_tempDir, exp, new[] { "Bob", "alice" }, new HttpClient(routes)));

        var offline = new HttpClient(new RouteHandler()); // every request 404s
        Assert.True(await ExperiencesService.HasAccessAsync(_tempDir, exp, new[] { "alice", "bob" }, offline));
        Assert.False(await ExperiencesService.HasAccessAsync(_tempDir, exp, new[] { "mallory" }, offline));
    }

    private sealed class RouteHandler : HttpMessageHandler
    {
        private readonly System.Collections.Generic.Dictionary<string, byte[]> _routes = new(StringComparer.OrdinalIgnoreCase);
        public void Add(string url, byte[] body) => _routes[url] = body;

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            var url = request.RequestUri?.ToString() ?? "";
            if (_routes.TryGetValue(url, out var body))
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(body) });
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotFound));
        }
    }

    [Fact]
    public async Task ManifestPack_DownloadsFilesFromCdn_VerifiesHashes_AndAppliesJarOverlays()
    {
        var upstreamJarBytes = PackZip(("fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"mod_a\",\"depends\":{\"minecraft\":\"~26.1\"}}"), ("code.txt", "v1"));
        var overlayZipBytes = PackZip(("fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"mod_a\",\"depends\":{\"minecraft\":\">=26.1\"}}"));
        var cfJarBytes = PackZip(("META-INF/mods.toml", "modId=\"mod_b\""));

        string upSha1 = Convert.ToHexString(SHA1.HashData(upstreamJarBytes)).ToLowerInvariant();
        string upSha512 = Convert.ToHexString(SHA512.HashData(upstreamJarBytes)).ToLowerInvariant();
        string cfSha1 = Convert.ToHexString(SHA1.HashData(cfJarBytes)).ToLowerInvariant();
        string cfSha512 = Convert.ToHexString(SHA512.HashData(cfJarBytes)).ToLowerInvariant();
        string overlaySha256 = Convert.ToHexString(SHA256.HashData(overlayZipBytes)).ToLowerInvariant();

        string modrinthUrl = "https://cdn.modrinth.com/data/AABBCCDD/versions/11223344/modA.jar";
        string cfUrl = "https://edge.forgecdn.net/files/5000/100/modB.jar";

        string indexJson = $$"""
        {
          "formatVersion": 1,
          "game": "minecraft",
          "versionId": "0.2.1",
          "name": "The Lads SMP Reborn",
          "dependencies": {
            "minecraft": "26.3",
            "fabric-loader": "0.19.5"
          },
          "files": [
            {
              "path": "mods/modA.jar",
              "hashes": { "sha1": "{{upSha1}}", "sha512": "{{upSha512}}" },
              "downloads": [ "{{modrinthUrl}}" ],
              "fileSize": {{upstreamJarBytes.Length}}
            },
            {
              "path": "mods/modB.jar",
              "hashes": { "sha1": "{{cfSha1}}", "sha512": "{{cfSha512}}" },
              "downloads": [ "{{cfUrl}}" ],
              "fileSize": {{cfJarBytes.Length}}
            }
          ]
        }
        """;

        string overlayManifest = $$"""
        {
          "formatVersion": 1,
          "overlays": [
            {
              "path": "mods/modA.jar",
              "overlayZip": "jar-overlays/modA.jar.overlay.zip",
              "overlaySha256": "{{overlaySha256}}"
            }
          ]
        }
        """;

        using var packStream = new MemoryStream();
        using (var zip = new ZipArchive(packStream, ZipArchiveMode.Create, leaveOpen: true))
        {
            void AddText(string name, string text)
            {
                using var w = new StreamWriter(zip.CreateEntry(name).Open(), new UTF8Encoding(false));
                w.Write(text);
            }
            AddText("pack.json", "{\"packId\":\"lads-smp-reborn-26.3\",\"packVersion\":\"0.2.1\",\"appliedPatches\":[]}");
            AddText("modrinth.index.json", indexJson);
            AddText("overrides/config/test.toml", "ok=true");
            AddText("overrides/mods/ladssmp-0.2.1.jar", "first-party");
            AddText("jar-overlays/manifest.json", overlayManifest);
            using (var s = zip.CreateEntry("jar-overlays/modA.jar.overlay.zip").Open())
                s.Write(overlayZipBytes, 0, overlayZipBytes.Length);
        }

        var archive = ExperiencesService.PackArchivePath(_tempDir, "lads-smp-reborn-26.3");
        Directory.CreateDirectory(Path.GetDirectoryName(archive)!);
        File.WriteAllBytes(archive, packStream.ToArray());

        var router = new RouteHandler();
        router.Add(modrinthUrl, upstreamJarBytes);
        router.Add(cfUrl, cfJarBytes);
        using var http = new HttpClient(router);

        var instance = await ExperiencesService.InstallLadsSmpRebornAsync(_tempDir, null, CancellationToken.None, http);
        var modsDir = Path.Combine(instance.GameDirectory, "mods");

        Assert.True(File.Exists(Path.Combine(modsDir, "ladssmp-0.2.1.jar")));
        Assert.True(File.Exists(Path.Combine(modsDir, "modB.jar")));
        Assert.Equal(cfJarBytes, File.ReadAllBytes(Path.Combine(modsDir, "modB.jar")));

        // Verify modA.jar was downloaded and its fabric.mod.json entry was overlaid in-place while preserving code.txt
        using var modAZip = ZipFile.OpenRead(Path.Combine(modsDir, "modA.jar"));
        using var fmjReader = new StreamReader(modAZip.GetEntry("fabric.mod.json")!.Open());
        Assert.Contains(">=26.1", fmjReader.ReadToEnd());
        using var codeReader = new StreamReader(modAZip.GetEntry("code.txt")!.Open());
        Assert.Equal("v1", codeReader.ReadToEnd());
    }

    [Theory]
    [InlineData("zero-smp-1.20.1", true)]
    [InlineData("zero-smp-1.20.1-dev", true)]
    [InlineData("lads-smp-reborn-26.3", true)]
    [InlineData("my-fabric-pack", false)]
    public void ExperienceInstancesAreKeptOutOfMyModpacks(string instanceId, bool experience)
    {
        Assert.Equal(experience, ExperiencesService.IsExperienceInstance(instanceId));
    }
}
