using System.Diagnostics;
using System.Security.Cryptography;
using System.Text.Json.Nodes;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class SharedContentServiceTests
{
    // --preview-shared/--preview-mods run the startup migration: Program.Main starts them only for sandbox folders.
    [Fact]
    public void PreviewSwitchesAcceptOnlyFoldersOutsideTheRealGameAndLauncherFolders()
    {
        var guard = typeof(SafeFileOps).Assembly.GetType("TheLadsLauncher.Program")!
            .GetMethod("IsSandboxFolder", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Static)!;
        bool Allowed(string? dir) => (bool)guard.Invoke(null, new object?[] { dir })!;
        using var sandbox = new TestDirectory();
        var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        Assert.True(Allowed(sandbox.Path));
        Assert.True(Allowed(System.IO.Path.Combine(sandbox.Path, "not-created-yet")));
        Assert.False(Allowed(System.IO.Path.Combine(appData, ".minecraft")));
        Assert.False(Allowed(System.IO.Path.Combine(appData, ".MINECRAFT") + System.IO.Path.DirectorySeparatorChar));
        Assert.False(Allowed(System.IO.Path.Combine(appData, ".theladsclient", "profiles", "26.3")));
        Assert.False(Allowed("relative-folder"));
        Assert.False(Allowed(""));
        Assert.False(Allowed(null));
    }

    private static string Today => DateTime.Now.ToString("yyyy-MM-dd");

    private sealed class Sandbox : IDisposable
    {
        private readonly TestDirectory _dir = new();
        public string Path => _dir.Path;
        public string Global => System.IO.Path.Combine(Path, "global");
        public string Game => System.IO.Path.Combine(Path, "profile");
        public string Backups => System.IO.Path.Combine(Path, "server-backups");
        public string Legacy => System.IO.Path.Combine(Path, "launcher", "shared", "servers.dat");
        public string MigrationRoot => System.IO.Path.Combine(Game, SharedContentService.MigrationFolderName);
        public SharedContentService Service { get; }
        public Sandbox(Func<string, bool>? supportsReparsePoints = null)
        {
            Service = supportsReparsePoints == null
                ? new SharedContentService(Global, Backups)
                : new SharedContentService(Global, Backups) { SupportsReparsePoints = supportsReparsePoints };
        }
        public string G(params string[] parts) => System.IO.Path.Combine(new[] { Global }.Concat(parts).ToArray());
        public string P(params string[] parts) => System.IO.Path.Combine(new[] { Game }.Concat(parts).ToArray());
        public Task<SharedContentReport> Prepare(bool core = true, string label = "26.3", bool legacy = false, bool shareFolders = true) =>
            Service.PrepareProfileAsync(Game, label, legacy ? Legacy : null, core, shareFolders: shareFolders);
        public string[] StampDirs() => Directory.Exists(MigrationRoot) ? Directory.GetDirectories(MigrationRoot) : Array.Empty<string>();
        public void Dispose() => _dir.Dispose();
    }

    private static void Write(string path, string content)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllText(path, content);
    }

    private static byte[] Servers(params (string Ip, string Name, bool Hidden)[] servers)
    {
        var file = ServerListFile.CreateEmpty();
        foreach (var (ip, name, hidden) in servers)
            file.Add(new NbtCompound { ["name"] = new NbtString(name), ["ip"] = new NbtString(ip), ["hidden"] = new NbtNumber(NbtTagType.Byte, hidden ? 1 : 0) });
        return file.ToBytes();
    }

    private static void WriteServers(string path, params (string Ip, string Name, bool Hidden)[] servers)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllBytes(path, Servers(servers));
    }

    private static List<(string Ip, string Name, bool Hidden)> ReadServers(string path) =>
        ServerListFile.Read(path).Entries.Select(e => (e.Ip, e.Name, e.Hidden)).ToList();

    private static string Sha(string path) => Convert.ToHexString(SHA256.HashData(File.ReadAllBytes(path)));

    private static bool LinksTo(string link, string target) =>
        SafeFileOps.GetLinkTarget(link) is { } actual && SafeFileOps.PathsEqual(actual, target);

    // ------------------------------------------------------------------ folders

    [Fact]
    public async Task FreshProfileGetsWorkingLinksAndNoMigrationRecord()
    {
        using var s = new Sandbox();
        var report = await s.Prepare();
        foreach (var folder in SharedContentService.SharedFolders)
            Assert.True(LinksTo(s.P(folder), s.G(folder)), folder);
        File.WriteAllText(s.P("resourcepacks", "new.zip"), "pack");
        Assert.Equal("pack", File.ReadAllText(s.G("resourcepacks", "new.zip")));
        Assert.Empty(s.StampDirs());
        Assert.Null(report.ReportPath);
        Assert.All(s.Service.GetStatus(s.Game), status => Assert.Equal(SharedFolderState.Shared, status.State));
        Assert.True(s.Service.IsInsideSharedLink(s.P("saves", "World", "level.dat"), s.Game));
        Assert.False(s.Service.IsInsideSharedLink(s.P("saves"), s.Game));
        Assert.False(s.Service.IsInsideSharedLink(s.P("mods", "x.jar"), s.Game));
    }

    [Fact]
    public async Task ExistingContentIsMigratedWithoutOverwritingOrMerging()
    {
        using var s = new Sandbox();
        Write(s.G("saves", "World", "level.dat"), "global world");
        Write(s.G("saves", "Same", "level.dat"), "same world");
        Write(s.G("saves", "Same", "session.lock"), "global lock");
        Write(s.G("resourcepacks", "pack.zip"), "global pack");
        Write(s.G("resourcepacks", "same.zip"), "same pack");
        Write(s.P("saves", "World", "level.dat"), "profile world");
        Write(s.P("saves", "Same", "level.dat"), "same world");
        Write(s.P("saves", "Same", "session.lock"), "profile lock"); // session.lock never makes worlds differ
        Write(s.P("saves", "Unique", "level.dat"), "unique world");
        Write(s.P("resourcepacks", "pack.zip"), "profile pack");
        Write(s.P("resourcepacks", "same.zip"), "same pack");
        Write(s.P("shaderpacks", "shader.zip"), "shader");
        Write(s.P("shaderpacks", "_0EuphoriaPatches_ErrorShader", "x.txt"), "generated");
        Write(s.P("shaderpacks", "_0EUPHORIA_PATCHES_ERROR_LOGS.txt"), "log");

        var report = await s.Prepare(label: "My:Profile/26.3 with a very long descriptive name.. ");

        var tag = $"(My_Profile_26.3 with a very long descrip {Today})";
        Assert.Equal("global world", File.ReadAllText(s.G("saves", "World", "level.dat")));
        Assert.Equal("profile world", File.ReadAllText(s.G("saves", $"World {tag}", "level.dat")));
        Assert.Equal("unique world", File.ReadAllText(s.G("saves", "Unique", "level.dat")));
        Assert.Equal("global lock", File.ReadAllText(s.G("saves", "Same", "session.lock")));
        Assert.Equal("global pack", File.ReadAllText(s.G("resourcepacks", "pack.zip")));
        Assert.Equal("profile pack", File.ReadAllText(s.G("resourcepacks", $"pack {tag}.zip")));
        Assert.Equal("shader", File.ReadAllText(s.G("shaderpacks", "shader.zip")));
        Assert.Empty(Directory.GetFileSystemEntries(s.G("shaderpacks"), "_0*"));
        Assert.Equal(2, report.Renamed);
        Assert.Equal(0, report.Pending);
        Assert.Contains(report.Messages, m => m.Contains($"World → World {tag} (both kept)"));

        // Identical copies and Euphoria output are kept as backups, never deleted.
        var duplicates = Assert.Single(Directory.GetDirectories(Path.Combine(s.Game, SharedContentService.DuplicatesFolderName)));
        Assert.Equal("profile lock", File.ReadAllText(Path.Combine(duplicates, "saves", "Same", "session.lock")));
        Assert.Equal("same pack", File.ReadAllText(Path.Combine(duplicates, "resourcepacks", "same.zip")));
        Assert.True(File.Exists(Path.Combine(duplicates, "shaderpacks", "_0EuphoriaPatches_ErrorShader", "x.txt")));
        Assert.True(File.Exists(Path.Combine(duplicates, "shaderpacks", "_0EUPHORIA_PATCHES_ERROR_LOGS.txt")));
        Assert.Equal(duplicates, report.BackupPath);

        var stamp = Assert.Single(s.StampDirs());
        Assert.False(Directory.Exists(Path.Combine(stamp, "pending")));
        var inventory = JsonNode.Parse(File.ReadAllText(Path.Combine(stamp, "inventory.json")))!;
        var profileSaves = inventory["folders"]!.AsArray().Single(f => (string?)f!["owner"] == "profile" && (string?)f["folder"] == "saves")!;
        Assert.Equal(3, profileSaves["entries"]!.AsArray().Count); // recorded before anything moved
        var actions = SharedContentService.ReadReport(report.ReportPath!).Select(e => (string?)e["action"]).ToList();
        Assert.Contains("move-to-pending", actions);
        Assert.Contains("link", actions);
        Assert.Contains("duplicate", actions);
        Assert.True(actions.IndexOf("move-to-pending") < actions.IndexOf("link"));
    }

    [Fact]
    public async Task InterruptedMigrationResumesFromPendingAndDropsOnlyOurIncompleteCopy()
    {
        using var s = new Sandbox();
        // Interrupted after the rename to pending and during a cross-drive copy: saves is missing, the copy is half done.
        var pending = Path.Combine(s.MigrationRoot, "20200101-000000", "pending");
        Write(Path.Combine(pending, "saves", "World", "level.dat"), "pending world");
        Write(Path.Combine(pending, "resourcepacks", "a.zip"), "pending pack");
        var incomplete = s.G("saves", ".lads-incoming-" + Guid.NewGuid().ToString("N"));
        Write(Path.Combine(incomplete, "World", "level.dat"), "half");
        Write(s.G("saves", ".lads-incoming-notours", "keep.txt"), "not ours");

        var report = await s.Prepare();

        Assert.Equal("pending world", File.ReadAllText(s.G("saves", "World", "level.dat")));
        Assert.Equal("pending pack", File.ReadAllText(s.G("resourcepacks", "a.zip")));
        Assert.False(Directory.Exists(incomplete));
        Assert.True(File.Exists(s.G("saves", ".lads-incoming-notours", "keep.txt")));
        Assert.False(Directory.Exists(Path.Combine(s.MigrationRoot, "20200101-000000")));
        Assert.True(LinksTo(s.P("saves"), s.G("saves")));
        Assert.Equal(0, report.Pending);
    }

    [Fact]
    public async Task OpenDownloadStagingFolderSurvivesAConcurrentMigration()
    {
        using var s = new Sandbox();
        Directory.CreateDirectory(s.G("resourcepacks"));
        using (var incoming = s.Service.CreateIncomingFolder(s.Service.ResourcePacksDirectory))
        {
            File.WriteAllText(Path.Combine(incoming.Path, "download.zip"), "partial");
            await s.Prepare();
            Assert.True(File.Exists(Path.Combine(incoming.Path, "download.zip")));
            Assert.True(SafeFileOps.MoveNoCopy(Path.Combine(incoming.Path, "download.zip"), s.G("resourcepacks", "download.zip")));
        }
        Assert.Equal("partial", File.ReadAllText(s.G("resourcepacks", "download.zip")));
        Assert.Empty(Directory.GetDirectories(s.G("resourcepacks"), ".lads-incoming-*"));
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task SecondRunChangesNothing(bool core)
    {
        using var s = new Sandbox();
        Write(s.P("saves", "World", "level.dat"), "w");
        WriteServers(s.G("servers.dat"), ("a.example", "A", false));
        WriteServers(s.P("servers.dat"), ("b.example", "B", false));
        await s.Prepare(core);
        var stamps = s.StampDirs();
        var global = Directory.GetFileSystemEntries(s.Global, "*", SearchOption.AllDirectories).Order()
            .Select(p => (p, File.GetLastWriteTimeUtc(p))).ToList();
        var profile = Directory.GetFiles(s.Game).Order().Select(p => (p, File.GetLastWriteTimeUtc(p))).ToList();

        var second = await s.Prepare(core);

        Assert.Empty(second.Messages);
        Assert.Empty(second.Warnings);
        Assert.Null(second.ReportPath);
        Assert.Equal(stamps, s.StampDirs());
        Assert.Equal(global, Directory.GetFileSystemEntries(s.Global, "*", SearchOption.AllDirectories).Order()
            .Select(p => (p, File.GetLastWriteTimeUtc(p))).ToList());
        Assert.Equal(profile, Directory.GetFiles(s.Game).Order().Select(p => (p, File.GetLastWriteTimeUtc(p))).ToList());
    }

    [Fact]
    public async Task ProfileThatIsTheGlobalFolderIsLeftCompletelyAlone()
    {
        using var s = new Sandbox();
        Write(s.G("saves", "World", "level.dat"), "w");
        WriteServers(s.G("servers.dat"), ("a.example", "A", false));
        var before = File.ReadAllBytes(s.G("servers.dat"));
        SafeFileOps.CreateJunction(Path.Combine(s.Path, "alias"), s.Global);

        foreach (var game in new[] { s.Global, Path.Combine(s.Path, "alias") })
        {
            var report = await s.Service.PrepareProfileAsync(game, "global", null, coreEnabled: true);
            Assert.True(report.Skipped);
        }
        Assert.False(SafeFileOps.IsLink(s.G("saves")));
        Assert.Equal(before, File.ReadAllBytes(s.G("servers.dat")));
        Assert.False(Directory.Exists(s.G(SharedContentService.MigrationFolderName)));
        Assert.All(s.Service.GetStatus(s.Global), status => Assert.Equal(SharedFolderState.GlobalFolder, status.State));
    }

    [Theory]
    [InlineData("root-inside-profile")]
    [InlineData("profile-inside-shared-saves")]
    [InlineData("shared-saves-links-into-profile")]
    public async Task UnsafePathRelationshipsThrowBeforeAnyChange(string layout)
    {
        using var dir = new TestDirectory();
        var game = Path.Combine(dir.Path, "profile");
        var root = Path.Combine(dir.Path, "global");
        Write(Path.Combine(game, "saves", "World", "level.dat"), "w");
        switch (layout)
        {
            case "root-inside-profile":
                root = Path.Combine(game, "global");
                break;
            case "profile-inside-shared-saves":
                game = Path.Combine(root, "saves", "profile");
                Write(Path.Combine(game, "saves", "World", "level.dat"), "w");
                break;
            default:
                Directory.CreateDirectory(root);
                SafeFileOps.CreateJunction(Path.Combine(root, "saves"), Path.Combine(game, "saves"));
                break;
        }
        var service = new SharedContentService(root);
        await Assert.ThrowsAsync<SharedContentUnavailableException>(() => service.PrepareProfileAsync(game, "p", null, true));
        Assert.False(SafeFileOps.IsLink(Path.Combine(game, "saves")));
        Assert.Equal("w", File.ReadAllText(Path.Combine(game, "saves", "World", "level.dat")));
        Assert.False(Directory.Exists(Path.Combine(game, SharedContentService.MigrationFolderName)));
    }

    [Fact]
    public async Task ForeignLinkWithContentIsNeverSilentlyDisconnected()
    {
        using var s = new Sandbox();
        var other = Path.Combine(s.Path, "curseforge-saves");
        Write(Path.Combine(other, "World", "level.dat"), "elsewhere");
        Directory.CreateDirectory(s.Game);
        SafeFileOps.CreateJunction(s.P("saves"), other);

        var error = await Assert.ThrowsAsync<SharedContentUnavailableException>(() => s.Prepare());
        Assert.Contains(other, error.Message);
        Assert.Contains(s.G("saves"), error.Message);
        Assert.True(LinksTo(s.P("saves"), other));
        Assert.Equal("elsewhere", File.ReadAllText(Path.Combine(other, "World", "level.dat")));
        Assert.True(LinksTo(s.P("resourcepacks"), s.G("resourcepacks"))); // the other folders are still shared
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task ForeignLinkToEmptyOrMissingFolderIsRelinked(bool targetMissing)
    {
        using var s = new Sandbox();
        var other = Path.Combine(s.Path, "old-target");
        Directory.CreateDirectory(other);
        Directory.CreateDirectory(s.Game);
        SafeFileOps.CreateJunction(s.P("saves"), other);
        if (targetMissing) Directory.Delete(other);

        var report = await s.Prepare();

        Assert.True(LinksTo(s.P("saves"), s.G("saves")));
        Assert.Equal(!targetMissing, Directory.Exists(other));
        Assert.Contains(report.Messages, m => m.Contains("Re-linked saves"));
    }

    [Fact]
    public async Task FailedLinkProofPutsTheFolderBack()
    {
        using var s = new Sandbox();
        // The shared saves folder is a link to a folder that no longer exists, so the new link cannot be opened.
        Directory.CreateDirectory(s.Global);
        var gone = Path.Combine(s.Path, "gone");
        Directory.CreateDirectory(gone);
        SafeFileOps.CreateJunction(s.G("saves"), gone);
        Directory.Delete(gone);
        Write(s.P("saves", "World", "level.dat"), "mine");

        await Assert.ThrowsAsync<SharedContentUnavailableException>(() => s.Prepare());

        Assert.False(SafeFileOps.IsLink(s.P("saves")));
        Assert.Equal("mine", File.ReadAllText(s.P("saves", "World", "level.dat")));
        Assert.Empty(Directory.GetDirectories(s.MigrationRoot, "pending", SearchOption.AllDirectories)
            .Where(p => Directory.EnumerateFileSystemEntries(p).Any()));
    }

    [Fact]
    public async Task SharedFolderThatCannotBeCreatedPutsTheProfileFolderBack()
    {
        using var s = new Sandbox();
        Write(s.G("saves"), "a file where the shared saves folder should be");
        Write(s.P("saves", "World", "level.dat"), "mine");
        Write(s.P("resourcepacks", "pack.zip"), "pack");

        var error = await Assert.ThrowsAsync<SharedContentUnavailableException>(() => s.Prepare());

        Assert.Contains("put back", error.Message);
        Assert.False(SafeFileOps.IsLink(s.P("saves")));
        Assert.Equal("mine", File.ReadAllText(s.P("saves", "World", "level.dat")));
        Assert.Equal("pack", File.ReadAllText(s.G("resourcepacks", "pack.zip"))); // the other folders are still shared
        Assert.Empty(Directory.GetDirectories(s.MigrationRoot, "pending", SearchOption.AllDirectories)
            .Where(p => Directory.EnumerateFileSystemEntries(p).Any()));
        // The failed prepare still tells the launcher what it moved, for the migration notice.
        Assert.Contains(error.Report!.Messages, m => m.Contains("Moved 1 item(s) into the shared resourcepacks folder"));
        Assert.Contains("restore", SharedContentService.ReadReport(error.Report.ReportPath!).Select(e => (string?)e["action"]));
    }

    [Fact]
    public async Task EntriesForASharedFolderOnAnotherDriveAreCopiedVerifiedAndTheOriginalsKept()
    {
        using var dir = new TestDirectory();
        var game = Path.Combine(dir.Path, "profile");
        var root = Path.Combine(dir.Path, "global");
        // Every move into the shared folder fails like MoveFileEx across drives (nothing moved).
        var service = new SharedContentService(root, Path.Combine(dir.Path, "backups")) { MoveIntoShared = (_, _) => false };
        Write(Path.Combine(root, "saves", "World", "level.dat"), "global");
        Write(Path.Combine(game, "saves", "World", "level.dat"), "profile");
        Write(Path.Combine(game, "saves", "World", "region", "r.0.0.mca"), "region");
        Write(Path.Combine(game, "resourcepacks", "pack.zip"), "pack");

        var report = await service.PrepareProfileAsync(game, "26.3", null, coreEnabled: true);

        var renamed = Path.Combine(root, "saves", $"World (26.3 {Today})");
        Assert.Equal("profile", File.ReadAllText(Path.Combine(renamed, "level.dat")));
        Assert.Equal("region", File.ReadAllText(Path.Combine(renamed, "region", "r.0.0.mca")));
        Assert.Equal("global", File.ReadAllText(Path.Combine(root, "saves", "World", "level.dat")));
        Assert.Equal("pack", File.ReadAllText(Path.Combine(root, "resourcepacks", "pack.zip")));
        // The originals stay as backups and no staging folder is left behind.
        Assert.Equal("profile", File.ReadAllText(Path.Combine(report.BackupPath!, "saves", "World", "level.dat")));
        Assert.Equal("pack", File.ReadAllText(Path.Combine(report.BackupPath!, "resourcepacks", "pack.zip")));
        Assert.Empty(Directory.GetDirectories(Path.Combine(root, "saves"), ".lads-incoming-*"));
        Assert.Empty(Directory.GetDirectories(Path.Combine(root, "resourcepacks"), ".lads-incoming-*"));
        Assert.Equal(0, report.Pending);
        Assert.Equal(2, SharedContentService.ReadReport(report.ReportPath!).Count(e => (string?)e["action"] == "copy"));
    }

    [Fact]
    public async Task WorldOpenInARunningGameIsNotMovedUntilItIsClosed()
    {
        using var s = new Sandbox();
        Write(s.G("saves", "World", "level.dat"), "global");
        Write(s.G("saves", "World", "session.lock"), "☃");
        Write(s.P("saves", "World", "level.dat"), "profile");
        SharedContentReport report;
        // Minecraft locks session.lock with FileChannel.tryLock (share read/write/delete + a byte-range lock).
        using (var held = new FileStream(s.G("saves", "World", "session.lock"), FileMode.Open, FileAccess.ReadWrite, FileShare.ReadWrite | FileShare.Delete))
        {
            held.Lock(0, long.MaxValue);
            report = await s.Prepare();
            held.Unlock(0, long.MaxValue);
        }
        Assert.Equal(1, report.Pending);
        Assert.Contains(report.Warnings, w => w.Contains("open in a running game"));
        Assert.Single(Directory.GetDirectories(s.G("saves")));

        var retry = await s.Prepare();
        Assert.Equal(0, retry.Pending);
        Assert.Equal("profile", File.ReadAllText(s.G("saves", $"World (26.3 {Today})", "level.dat")));
    }

    [Fact]
    public async Task UnsupportedVolumeOffersLaunchWithoutSharing()
    {
        using var s = new Sandbox(supportsReparsePoints: _ => false);
        Write(s.P("saves", "World", "level.dat"), "mine");
        WriteServers(s.G("servers.dat"), ("a.example", "A", false));

        var error = await Assert.ThrowsAsync<SharedContentUnavailableException>(() => s.Prepare(core: false));
        Assert.Contains("local NTFS drive", error.Message);
        Assert.False(SafeFileOps.IsLink(s.P("saves")));

        var report = await s.Prepare(core: false, shareFolders: false);
        Assert.False(SafeFileOps.IsLink(s.P("saves")));
        Assert.Equal("mine", File.ReadAllText(s.P("saves", "World", "level.dat")));
        Assert.Contains(report.Warnings, w => w.Contains("off for this launch"));
        Assert.Equal(File.ReadAllBytes(s.G("servers.dat")), File.ReadAllBytes(s.P("servers.dat")));
    }

    [Fact]
    public async Task RecycleBinHelperRefusesLinksAndPendingMigrations()
    {
        using var s = new Sandbox();
        await s.Prepare();
        Assert.Throws<InvalidOperationException>(() => SafeFileOps.DeleteToRecycleBin(s.P("saves")));
        var pending = Path.Combine(s.MigrationRoot, "20200101-000000", "pending", "saves", "World");
        Directory.CreateDirectory(pending);
        Assert.Throws<InvalidOperationException>(() => SafeFileOps.DeleteToRecycleBin(s.MigrationRoot));
        Assert.Throws<InvalidOperationException>(() => SafeFileOps.DeleteToRecycleBin(pending));
        Assert.True(Directory.Exists(pending));
    }

    [Fact]
    public void DeleteTreeRemovesLinksWithoutTouchingTheirTargets()
    {
        using var dir = new TestDirectory();
        var outside = Path.Combine(dir.Path, "outside");
        Write(Path.Combine(outside, "world.dat"), "keep");
        var tree = Path.Combine(dir.Path, "tree");
        Write(Path.Combine(tree, "a", "file.txt"), "x");
        SafeFileOps.CreateJunction(Path.Combine(tree, "a", "link"), outside);
        SafeFileOps.DeleteTree(tree);
        Assert.False(Directory.Exists(tree));
        Assert.Equal("keep", File.ReadAllText(Path.Combine(outside, "world.dat")));
    }

    [Fact]
    public void MoveNeverReplacesAndCopyIsVerified()
    {
        using var dir = new TestDirectory();
        Write(Path.Combine(dir.Path, "a.txt"), "a");
        Write(Path.Combine(dir.Path, "b.txt"), "b");
        Assert.Throws<IOException>(() => SafeFileOps.MoveNoCopy(Path.Combine(dir.Path, "a.txt"), Path.Combine(dir.Path, "b.txt")));
        Assert.Equal("b", File.ReadAllText(Path.Combine(dir.Path, "b.txt")));
        Write(Path.Combine(dir.Path, "world", "region", "r.0.0.mca"), "region");
        Write(Path.Combine(dir.Path, "world", "level.dat"), "level");
        SafeFileOps.CopyVerified(Path.Combine(dir.Path, "world"), Path.Combine(dir.Path, "copy"));
        Assert.Equal("region", File.ReadAllText(Path.Combine(dir.Path, "copy", "region", "r.0.0.mca")));
        Assert.Throws<IOException>(() => SafeFileOps.CopyVerified(Path.Combine(dir.Path, "a.txt"), Path.Combine(dir.Path, "b.txt")));
    }

    [Fact]
    public void DownloadedPackTakesTheNextFreeNameAndNeverReplacesAnExistingPack()
    {
        using var dir = new TestDirectory();
        var packs = Path.Combine(dir.Path, "resourcepacks");
        Write(Path.Combine(packs, "Faithful.zip"), "installed");
        Directory.CreateDirectory(Path.Combine(packs, "Faithful (2).zip")); // an unzipped pack folder with that name
        Write(Path.Combine(dir.Path, "incoming", "Faithful.zip"), "downloaded");
        Write(Path.Combine(dir.Path, "incoming", "Plain"), "no extension");

        var final = SafeFileOps.MoveToFreeName(Path.Combine(dir.Path, "incoming", "Faithful.zip"), packs, "Faithful.zip");
        Assert.Equal(Path.Combine(packs, "Faithful (3).zip"), final);
        Assert.Equal("downloaded", File.ReadAllText(final));
        Assert.Equal("installed", File.ReadAllText(Path.Combine(packs, "Faithful.zip")));
        Assert.Equal(Path.Combine(packs, "Plain"), SafeFileOps.MoveToFreeName(Path.Combine(dir.Path, "incoming", "Plain"), packs, "Plain"));
        Write(Path.Combine(dir.Path, "incoming", "Plain"), "again");
        Assert.Equal(Path.Combine(packs, "Plain (2)"), SafeFileOps.MoveToFreeName(Path.Combine(dir.Path, "incoming", "Plain"), packs, "Plain"));
    }

    // SEC-3: a pack name from a (custom) API must never place the download outside the shared resourcepacks folder.
    [Theory]
    [InlineData(@"..\escaped.zip")]
    [InlineData("..")]
    [InlineData(@"sub\x.zip")]
    [InlineData("")]
    public void DownloadedPackNameCannotLeaveTheSharedFolder(string name)
    {
        using var dir = new TestDirectory();
        var packs = Path.Combine(dir.Path, "global", "resourcepacks");
        Directory.CreateDirectory(Path.Combine(packs, "sub"));
        var source = Path.Combine(dir.Path, "incoming.tmp");
        Write(source, "downloaded");

        Assert.False(SafeFileOps.IsPlainFileName(name));
        Assert.Throws<ArgumentException>(() => SafeFileOps.MoveToFreeName(source, packs, name));

        Assert.Equal("downloaded", File.ReadAllText(source));
        Assert.Equal(new[] { source }, Directory.GetFiles(dir.Path, "*", SearchOption.AllDirectories));
        Assert.True(SafeFileOps.IsPlainFileName("Faithful 32x (1.21).zip"));
    }

    // UI-2 / FS-1 / SEC-1: the shell gets the real path (links resolved) and must warn before destroying what it cannot
    // recycle; answering No there deletes nothing and is reported, never logged as "moved to the Recycle Bin".
    [Fact]
    public void RecycleBinDeleteWarnsBeforeAPermanentDeleteAndUsesTheRealPath()
    {
        using var dir = new TestDirectory();
        var real = Path.Combine(dir.Path, "global", "saves");
        Write(Path.Combine(real, "World", "level.dat"), "keep");
        var profileSaves = Path.Combine(dir.Path, "profile", "saves");
        Directory.CreateDirectory(Path.GetDirectoryName(profileSaves)!);
        SafeFileOps.CreateJunction(profileSaves, real);
        var calls = new List<(string Path, int Flags)>();

        var error = Assert.Throws<OperationCanceledException>(() => SafeFileOps.DeleteToRecycleBin(Path.Combine(profileSaves, "World"),
            (path, flags) => { calls.Add((path, flags)); return (0, true); })); // the user answered No at the shell's warning

        var (shellPath, shellFlags) = Assert.Single(calls);
        Assert.True(SafeFileOps.PathsEqual(Path.Combine(SafeFileOps.GetFinalPath(real), "World"), shellPath), shellPath);
        const int wanted = SafeFileOps.FofAllowUndo | SafeFileOps.FofWantNukeWarning;
        Assert.Equal(wanted, shellFlags & wanted);
        Assert.Contains("not deleted", error.Message);
        Assert.Equal("keep", File.ReadAllText(Path.Combine(real, "World", "level.dat")));
    }

    [Fact]
    public void ShellDeleteMarshalsTheShellCall()
    {
        // No FOF_ALLOWUNDO: this scratch file is deleted for good, so the test never fills the real Recycle Bin.
        using var dir = new TestDirectory();
        var file = Path.Combine(dir.Path, "scratch.txt");
        Write(file, "x");
        Assert.Equal((0, false), SafeFileOps.ShellDelete(file, SafeFileOps.FofNoConfirmation | SafeFileOps.FofSilent | SafeFileOps.FofNoErrorUi));
        Assert.False(File.Exists(file));
    }

    // UI-7 / SEC-4: Program.Main shows this before any window instead of crashing in a field initializer.
    [Theory]
    [InlineData(".minecraft", true)]
    [InlineData("D:mc", true)]
    [InlineData(@" C:\games\mc", true)]
    [InlineData(@"%APPDATA%\.minecraft", true)]
    [InlineData(@"C:\games\mc", false)]
    [InlineData("", false)]
    [InlineData(null, false)]
    public void AnUnusableSharedRootVariableIsReportedWithItsFix(string? value, bool refused)
    {
        var problem = SharedContentService.RootVariableProblem(value);
        Assert.Equal(refused, problem != null);
        if (refused) Assert.Contains("Fix or remove the variable", problem);
    }

    // UI-6: the preview modes migrate every profile folder; one outside THELADS_DIR (also through a link) makes them refuse.
    [Fact]
    public void PreviewGuardFindsProfileFoldersOutsideTheSandbox()
    {
        using var dir = new TestDirectory();
        var sandbox = Path.Combine(dir.Path, "sandbox");
        var outside = Path.Combine(dir.Path, "real-minecraft");
        Directory.CreateDirectory(outside);
        var profiles = new ProfileService(new PathService(sandbox), new SharedContentService(Path.Combine(sandbox, "global-minecraft")));
        Assert.Empty(profiles.ProfilesOutside(sandbox));
        var custom = profiles.CreateProfile("Custom", "26.3", 25, false, "0.19.5");
        custom.CustomGameDir = outside;
        profiles.SaveProfile(custom);
        var linked = profiles.CreateProfile("Linked", "26.3", 25, false, "0.19.5");
        SafeFileOps.CreateJunction(Path.Combine(sandbox, "linked"), outside);
        linked.CustomGameDir = Path.Combine(sandbox, "linked", "game");
        profiles.SaveProfile(linked);
        var own = profiles.CreateProfile("Own", "26.3", 25, false, "0.19.5");
        own.CustomGameDir = Path.Combine(sandbox, "custom-game");
        profiles.SaveProfile(own);

        Assert.Equal(new[] { "Custom", "Linked" }, profiles.ProfilesOutside(sandbox).Select(p => p.Name).Order());
    }

    // UI-8: packwiz-installer writes and deletes through the shared links, so a pack managing those folders is found first.
    [Fact]
    public async Task PackwizPackThatManagesSharedFoldersIsFoundBeforeItRuns()
    {
        using var http = new HttpClient(new StaticFiles(new()
        {
            ["https://packs.example/lads/pack.toml"] = "name = \"Lads\"\r\npack-format = \"packwiz:1.1.0\"\r\n\r\n[index]\r\nfile = \"index.toml\"\r\n" +
                "hash-format = \"sha256\"\r\nhash = \"00\"\r\n\r\n[versions]\r\nminecraft = \"26.3\"\r\n",
            ["https://packs.example/lads/index.toml"] = "hash-format = \"sha256\"\n\n[[files]]\nfile = \"mods/sodium.pw.toml\"\nhash = \"1\"\nmetafile = true\n\n" +
                "[[files]]\nfile = \"resourcepacks/Pack.zip\"\nhash = \"2\"\n\n[[files]]\nfile = 'shaderpacks/complementary.pw.toml'\nhash = \"3\"\n" +
                "metafile = true\n\n[[files]]\nfile = \"config/resourcepacks.json\"\nhash = \"4\"\n",
            ["https://packs.example/mods-only/pack.toml"] = "[index]\nfile = \"index.toml\"\n",
            ["https://packs.example/mods-only/index.toml"] = "[[files]]\nfile = \"mods/sodium.pw.toml\"\nmetafile = true\n"
        }));

        Assert.Equal(new[] { "resourcepacks/Pack.zip", "shaderpacks/complementary.pw.toml" },
            await PackwizIndex.FilesInFoldersAsync("https://packs.example/lads/pack.toml", SharedContentService.SharedFolders, http));
        Assert.Empty(await PackwizIndex.FilesInFoldersAsync("https://packs.example/lads/pack.toml", new[] { "saves" }, http));
        Assert.Empty(await PackwizIndex.FilesInFoldersAsync("https://packs.example/mods-only/pack.toml", SharedContentService.SharedFolders, http));
    }

    // ------------------------------------------------------------------ servers.dat

    // FS-3: a game killed between Minecraft's two renames leaves only servers.dat_old; that is the list, not "no list".
    [Fact]
    public async Task SharedListLeftOnlyAsServersDatOldIsKeptAndRestored()
    {
        using var s = new Sandbox();
        WriteServers(s.G("servers.dat_old"), ("a.example", "A", false), ("b.example", "B", false));
        WriteServers(s.P("servers.dat"), ("p.example", "P", false));

        var report = await s.Prepare();

        Assert.Equal(new[] { "a.example", "b.example", "p.example" }, ReadServers(s.G("servers.dat")).Select(e => e.Ip));
        Assert.Contains(report.Messages, m => m.Contains("restored from servers.dat_old"));
    }

    [Fact]
    public async Task ProfileListLeftOnlyAsServersDatOldIsMergedBeforeItIsKeptAside()
    {
        using var s = new Sandbox();
        WriteServers(s.G("servers.dat"), ("a.example", "A", false));
        WriteServers(s.P("servers.dat_old"), ("p.example", "P", false));

        await s.Prepare();

        Assert.Equal(new[] { "a.example", "p.example" }, ReadServers(s.G("servers.dat")).Select(e => e.Ip));
        Assert.False(File.Exists(s.P("servers.dat_old")));
    }

    // FS-3, the reconcile side: an unreadable servers.dat_old of a tracked copy is kept aside (it is the file that was read),
    // instead of failing on the missing servers.dat at every launch and exit.
    [Fact]
    public async Task TrackedCopyLeftOnlyAsAnUnreadableServersDatOldIsKeptAside()
    {
        using var s = new Sandbox();
        WriteServers(s.G("servers.dat"), ("a.example", "A", false));
        await s.Prepare(core: false);
        File.Delete(s.P("servers.dat"));
        File.WriteAllText(s.P("servers.dat_old"), "not a server list");

        var report = await s.Service.ReconcileFallbackServersAsync(s.Game);

        Assert.Contains(report.Warnings, w => w.Contains("unreadable"));
        Assert.Equal("not a server list", File.ReadAllText(Assert.Single(Directory.GetFiles(s.MigrationRoot, "servers-unreadable-*.dat"))));
        Assert.False(File.Exists(s.P("servers.dat_old")));
        Assert.False(File.Exists(s.P(SharedContentService.ServersBaseFileName)));
        Assert.Equal(new[] { "a.example" }, ReadServers(s.G("servers.dat")).Select(e => e.Ip));
    }

    [Fact]
    public async Task ServerListsAreUnionMergedVisibleWinsAndLegacyIsMergedOnce()
    {
        using var s = new Sandbox();
        WriteServers(s.G("servers.dat"), ("a.example", "Alpha", false), ("B.example ", "Beta", true));
        var canonicalBefore = File.ReadAllBytes(s.G("servers.dat"));
        WriteServers(s.P("servers.dat"), ("b.example", "Bravo", false), ("c.example", "Charlie", true));
        File.WriteAllText(s.P("servers.dat_old"), "old");
        WriteServers(s.Legacy, ("d.example", "Delta", false));

        var report = await s.Prepare(legacy: true);

        Assert.Equal(new[] { ("a.example", "Alpha", false), ("B.example ", "Beta", false), ("c.example", "Charlie", true), ("d.example", "Delta", false) },
            ReadServers(s.G("servers.dat")));
        Assert.Contains(report.Messages, m => m.Contains("kept the shared name 'Beta'"));
        var stamp = Assert.Single(s.StampDirs());
        Assert.Equal(canonicalBefore, File.ReadAllBytes(Path.Combine(stamp, "servers", "servers.dat.canonical-before")));
        Assert.False(File.Exists(s.P("servers.dat")));
        Assert.True(File.Exists(Path.Combine(stamp, "servers", "servers.dat")));
        Assert.True(File.Exists(Path.Combine(stamp, "servers", "servers.dat_old")));
        Assert.True(File.Exists(s.Legacy));

        // A server deleted in game later must not come back from the old launcher's list.
        WriteServers(s.G("servers.dat"), ("a.example", "Alpha", false));
        await s.Prepare(legacy: true);
        Assert.Equal(new[] { ("a.example", "Alpha", false) }, ReadServers(s.G("servers.dat")));
    }

    [Fact]
    public async Task NothingToAddLeavesTheSharedListUntouched()
    {
        using var s = new Sandbox();
        WriteServers(s.G("servers.dat"), ("a.example", "Alpha", false));
        File.SetLastWriteTimeUtc(s.G("servers.dat"), new DateTime(2024, 1, 1, 0, 0, 0, DateTimeKind.Utc));
        WriteServers(s.P("servers.dat"), (" A.EXAMPLE", "Alpha", false));
        await s.Prepare();
        Assert.Equal(new DateTime(2024, 1, 1, 0, 0, 0, DateTimeKind.Utc), File.GetLastWriteTimeUtc(s.G("servers.dat")));
        var stamp = Assert.Single(s.StampDirs());
        Assert.False(File.Exists(Path.Combine(stamp, "servers", "servers.dat.canonical-before")));
    }

    [Fact]
    public async Task UnreadableSharedListStopsTheServerStepWithoutMovingAnything()
    {
        using var s = new Sandbox();
        var garbage = new byte[] { 0x0A, 0x00, 0x00, 0x09, 0x00 };
        Directory.CreateDirectory(s.Global);
        File.WriteAllBytes(s.G("servers.dat"), garbage);
        WriteServers(s.P("servers.dat"), ("b.example", "B", false));
        var profileBefore = File.ReadAllBytes(s.P("servers.dat"));

        var report = await s.Prepare();

        Assert.Equal(garbage, File.ReadAllBytes(s.G("servers.dat")));
        Assert.Equal(profileBefore, File.ReadAllBytes(s.P("servers.dat")));
        Assert.Contains(report.Warnings, w => w.Contains("could not be read") && w.Contains("left unchanged"));
        Assert.True(LinksTo(s.P("saves"), s.G("saves"))); // folders are still shared
    }

    [Fact]
    public async Task SharedListMissingInTheMiddleOfAMinecraftSaveIsWaitedFor()
    {
        using var s = new Sandbox();
        // A game is saving: servers.dat was renamed to servers.dat_old and the new file is not moved in yet.
        WriteServers(s.G("servers.dat_old"), ("a.example", "A", false));
        WriteServers(s.G("servers-new.tmp"), ("a.example", "A", false), ("n.example", "N", false));
        WriteServers(s.P("servers.dat"), ("p.example", "P", false));
        var save = Task.Run(async () =>
        {
            await Task.Delay(300);
            File.Move(s.G("servers-new.tmp"), s.G("servers.dat")); // fails if the launcher wrote a list meanwhile
        });

        await s.Prepare();
        await save;

        Assert.Equal(new[] { "a.example", "n.example", "p.example" }, ReadServers(s.G("servers.dat")).Select(e => e.Ip));
    }

    [Fact]
    public async Task EmptyNineteenByteListsNeverReplaceTheRealList()
    {
        using var s = new Sandbox();
        // Real-data shape: ~30 servers with formatted/emoji names, icons and extra fields vs 19-byte empty lists.
        var shared = ServerListFile.CreateEmpty();
        for (var i = 0; i < 30; i++)
            shared.Add(new NbtCompound
            {
                ["name"] = new NbtString($"§{i % 10}Server {i} ★ Ünïcödé 🌟"),
                ["ip"] = new NbtString($"play{i}.example.net:{25565 + i}"),
                ["icon"] = new NbtString(Convert.ToBase64String(Enumerable.Range(0, 4000).Select(b => (byte)(b * i)).ToArray())),
                ["acceptTextures"] = new NbtNumber(NbtTagType.Byte, i % 2),
                ["acceptedCodeOfConduct"] = new NbtNumber(NbtTagType.Int, i),
                ["hidden"] = new NbtNumber(NbtTagType.Byte, i >= 28 ? 1 : 0)
            });
        Directory.CreateDirectory(s.Global);
        File.WriteAllBytes(s.G("servers.dat"), shared.ToBytes());
        var before = File.ReadAllBytes(s.G("servers.dat"));
        var written = File.GetLastWriteTimeUtc(s.G("servers.dat"));
        var empty = ServerListFile.CreateEmpty().ToBytes();
        Assert.Equal(19, empty.Length);
        Directory.CreateDirectory(s.Game);
        File.WriteAllBytes(s.P("servers.dat"), empty);
        Directory.CreateDirectory(Path.GetDirectoryName(s.Legacy)!);
        File.WriteAllBytes(s.Legacy, empty);

        await s.Prepare(core: true, legacy: true);
        Assert.Equal(before, File.ReadAllBytes(s.G("servers.dat")));
        Assert.Equal(written, File.GetLastWriteTimeUtc(s.G("servers.dat")));
        Assert.Equal(30, ServerListFile.Read(s.G("servers.dat")).Entries.Count);
        Assert.Equal(28, MinecraftServerListReader.ReadFile(s.G("servers.dat")).Count);

        // Without LadsCore the profile gets an exact copy and remembers it.
        var other = Path.Combine(s.Path, "other-profile");
        Directory.CreateDirectory(other);
        File.WriteAllBytes(Path.Combine(other, "servers.dat"), empty);
        await s.Service.PrepareProfileAsync(other, "1.21.1", s.Legacy, coreEnabled: false);
        Assert.Equal(before, File.ReadAllBytes(s.G("servers.dat")));
        Assert.Equal(before, File.ReadAllBytes(Path.Combine(other, "servers.dat")));
        var baseJson = JsonNode.Parse(File.ReadAllText(Path.Combine(other, SharedContentService.ServersBaseFileName)))!;
        Assert.Equal(Sha(s.G("servers.dat")), (string?)baseJson["sha256"]);
    }

    [Fact]
    public async Task FallbackReconcileIsThreeWayAndNeverLosesEdits()
    {
        using var s = new Sandbox();
        WriteServers(s.G("servers.dat"), ("a.example", "A", false));
        await s.Prepare(core: false);
        Assert.Equal(File.ReadAllBytes(s.G("servers.dat")), File.ReadAllBytes(s.P("servers.dat")));

        // Profile unchanged since the copy: nothing to do.
        var unchanged = await s.Service.ReconcileFallbackServersAsync(s.Game);
        Assert.Empty(unchanged.Messages.Concat(unchanged.Warnings));

        // Only the profile changed: the shared list takes the profile copy (a removal there is honoured too).
        WriteServers(s.P("servers.dat"), ("p.example", "P", false));
        await s.Service.ReconcileFallbackServersAsync(s.Game);
        Assert.Equal(new[] { ("p.example", "P", false) }, ReadServers(s.G("servers.dat")));
        Assert.Contains(Directory.GetFiles(s.Backups, "servers-*-shared.dat"), f => ReadServers(f).Single().Ip == "a.example");

        // Both changed: union, nothing removed, both backed up, reported.
        WriteServers(s.G("servers.dat"), ("p.example", "P", false), ("q.example", "Q", false));
        WriteServers(s.P("servers.dat"), ("p.example", "P", false), ("r.example", "R", false));
        var conflict = await s.Service.ReconcileFallbackServersAsync(s.Game);
        Assert.Equal(new[] { "p.example", "q.example", "r.example" }, ReadServers(s.G("servers.dat")).Select(e => e.Ip));
        Assert.Contains(conflict.Warnings, w => w.Contains("changed both"));
        Assert.NotEmpty(Directory.GetFiles(s.Backups, "servers-*-profile.dat"));
        Assert.Equal(File.ReadAllBytes(s.G("servers.dat")), File.ReadAllBytes(s.P("servers.dat")));

        // The launcher died before the exit hook ran: the next prepare merges first instead of copying over the edit.
        WriteServers(s.P("servers.dat"), ("p.example", "P", false), ("q.example", "Q", false), ("r.example", "R", false), ("s.example", "S", false));
        await s.Prepare(core: false);
        Assert.Contains(ReadServers(s.G("servers.dat")), e => e.Ip == "s.example");
        Assert.Equal(File.ReadAllBytes(s.G("servers.dat")), File.ReadAllBytes(s.P("servers.dat")));

        // LadsCore re-enabled: the last edit is folded in, then the profile copy is kept aside (never deleted).
        WriteServers(s.P("servers.dat"), ("p.example", "P", false), ("t.example", "T", false));
        await s.Prepare(core: true);
        Assert.Equal(new[] { "p.example", "t.example" }, ReadServers(s.G("servers.dat")).Select(e => e.Ip));
        Assert.False(File.Exists(s.P("servers.dat")));
        Assert.False(File.Exists(s.P(SharedContentService.ServersBaseFileName)));
        Assert.Contains(Directory.GetFiles(s.MigrationRoot, "servers.dat", SearchOption.AllDirectories), f => ReadServers(f).Any(e => e.Ip == "t.example"));
        Assert.True(Directory.GetFiles(s.Backups, "servers-*.dat").Length <= 10);
    }

    // ------------------------------------------------------------------ profile wiring

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task EveryProfileSharesContentAndIsolationOnlyKeepsGameSettings(bool isolated)
    {
        using var dir = new TestDirectory();
        var paths = new PathService(Path.Combine(dir.Path, "launcher"));
        var shared = new SharedContentService(Path.Combine(dir.Path, "global"));
        var profiles = new ProfileService(paths, shared);
        var profile = profiles.CreateProfile("Custom", "26.3", 25, isolated, "0.19.5");
        var game = paths.GetProfileDirectory(profile);
        Write(Path.Combine(game, "options.txt"), "fov:0.1\n");
        File.SetLastWriteTimeUtc(Path.Combine(game, "options.txt"), new DateTime(2024, 1, 1, 0, 0, 0, DateTimeKind.Utc));
        Write(paths.SharedOptionsFile, "fov:0.5\ngamma:1.0\n");
        WriteServers(Path.Combine(game, "servers.dat"), ("x.example", "X", false));

        var report = await profiles.PrepareProfileEnvironmentAsync(profile, null);

        Assert.True(LinksTo(Path.Combine(game, "saves"), shared.SavesDirectory));
        // Not isolated: missing settings come at once, and a setting changed in the shared copy since the last launch replaces the game's own.
        Assert.Equal(isolated ? "fov:0.1\n" : "fov:0.1\ngamma:1.0\n", File.ReadAllText(Path.Combine(game, "options.txt")));
        Write(paths.SharedOptionsFile, "fov:0.75\ngamma:1.0\n");
        await profiles.PrepareProfileEnvironmentAsync(profile, null);
        Assert.Equal(isolated ? "fov:0.1\n" : "fov:0.75\ngamma:1.0\n", File.ReadAllText(Path.Combine(game, "options.txt")));
        Assert.Contains(ReadServers(shared.ServersFile), e => e.Ip == "x.example");
        Assert.False(File.Exists(Path.Combine(game, "servers.dat"))); // 26.3 + Fabric runs LadsCore, which reads the shared list
        Assert.Empty(report.Warnings);
    }

    // R1: only an explicit key disables Core. A lone theladscore.jar.disabled (v1.2.2 leftover) is ambiguous: the installer
    // enables Core, so the server list must follow LadsCore's shared file too.
    [Theory]
    [InlineData(null, false, false, true)]
    [InlineData(null, true, false, true)]
    [InlineData(null, true, true, true)]
    [InlineData("{\"mods\":{\"theladscore\":{\"enabled\":false}}}", false, true, false)]
    [InlineData("{\"mods\":{\"theladscore\":{\"enabled\":true}}}", true, false, true)]
    [InlineData("{\"mods\":{\"other\":{\"enabled\":false}},\"extra\":1}", true, false, true)]
    [InlineData("{ not json", true, false, true)]
    public void CoreRequestUsesExplicitKeyElseEnabled(string? state, bool disabledJar, bool enabledJar, bool expected)
    {
        using var dir = new TestDirectory();
        if (state != null) File.WriteAllText(Path.Combine(dir.Path, "lads-mod-state.json"), state);
        Directory.CreateDirectory(Path.Combine(dir.Path, "mods"));
        if (disabledJar) File.WriteAllText(Path.Combine(dir.Path, "mods", "theladscore.jar.disabled"), "");
        if (enabledJar) File.WriteAllText(Path.Combine(dir.Path, "mods", "theladscore.jar"), "");
        Assert.Equal(expected, SharedContentService.IsCoreRequested(dir.Path, out var error));
        Assert.Equal(state == "{ not json", error != null);
    }

    [Fact]
    public async Task StartupPassPreparesEveryExistingProfileAndReportsFailuresPerProfile()
    {
        using var s = new Sandbox();
        Write(s.P("saves", "World", "level.dat"), "w");
        var blocked = Path.Combine(s.Path, "blocked");
        var elsewhere = Path.Combine(s.Path, "elsewhere");
        Write(Path.Combine(elsewhere, "Other", "level.dat"), "elsewhere");
        Directory.CreateDirectory(blocked);
        SafeFileOps.CreateJunction(Path.Combine(blocked, "saves"), elsewhere);
        Write(Path.Combine(blocked, "resourcepacks", "pack.zip"), "pack");
        var neverLaunched = Path.Combine(s.Path, "never-launched");

        var results = await s.Service.PrepareAllAsync(new[] { (blocked, "Blocked", true), (s.Game, "26.3", true), (neverLaunched, "New", true) }, null);

        Assert.False(results[s.Game].Skipped);
        Assert.Equal("w", File.ReadAllText(s.G("saves", "World", "level.dat")));
        var failed = results[blocked];
        Assert.True(failed.Skipped);
        Assert.Contains(failed.Warnings, w => w.StartsWith("Blocked: ") && w.Contains(elsewhere));
        Assert.Contains(failed.Messages, m => m.Contains("Moved 1 item(s) into the shared resourcepacks folder"));
        Assert.True(LinksTo(Path.Combine(blocked, "saves"), elsewhere));
        Assert.False(results.ContainsKey(neverLaunched));
        Assert.False(Directory.Exists(neverLaunched));
    }

    [Fact]
    public async Task RunningGameIsNeverMigrated()
    {
        using var s = new Sandbox();
        Write(s.P("saves", "World", "level.dat"), "in use");
        using var java = RunningJava.Start(s.Path);
        if (java == null) return; // no Java on this machine: the marker cannot describe a live game
        RunningGameMarker.Write(s.Game, java.Process.Id, java.Process.StartTime.ToUniversalTime(), new[] { "theladscore" });

        var report = await s.Prepare();

        Assert.True(report.Skipped);
        Assert.False(SafeFileOps.IsLink(s.P("saves")));
        Assert.True(File.Exists(s.P("saves", "World", "level.dat")));
    }
}

/// <summary>Serves fixed texts by URL; anything else is 404.</summary>
internal sealed class StaticFiles(Dictionary<string, string> files) : HttpMessageHandler
{
    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token) =>
        Task.FromResult(files.TryGetValue(request.RequestUri!.AbsoluteUri, out var text)
            ? new HttpResponseMessage(System.Net.HttpStatusCode.OK) { Content = new StringContent(text) }
            : new HttpResponseMessage(System.Net.HttpStatusCode.NotFound));
}

/// <summary>A real, idle java process (so RunningGameMarker sees a live java game); killed on Dispose.</summary>
internal sealed class RunningJava : IDisposable
{
    private RunningJava(Process process) => Process = process;
    public Process Process { get; }

    /// <summary>Null when Java is not installed.</summary>
    public static RunningJava? Start(string workDirectory)
    {
        var home = Environment.GetEnvironmentVariable("JAVA_HOME");
        var java = home != null && File.Exists(Path.Combine(home, "bin", "java.exe")) ? Path.Combine(home, "bin", "java.exe")
            : (Environment.GetEnvironmentVariable("PATH") ?? "").Split(Path.PathSeparator)
                .Select(p => Path.Combine(p.Trim('"'), "java.exe")).FirstOrDefault(File.Exists);
        if (java == null) return null;
        var source = Path.Combine(workDirectory, "Idle.java");
        File.WriteAllText(source, "class Idle { public static void main(String[] a) throws Exception { Thread.sleep(120000); } }");
        return new RunningJava(Process.Start(new ProcessStartInfo(java, $"\"{source}\"") { UseShellExecute = false, CreateNoWindow = true })!);
    }

    public void Dispose()
    {
        if (!Process.HasExited) Process.Kill(entireProcessTree: true);
        Process.WaitForExit();
        Process.Dispose();
    }
}