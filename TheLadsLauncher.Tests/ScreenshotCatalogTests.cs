using System.Runtime.InteropServices;
using System.Text.Json;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class ScreenshotCatalogTests : IDisposable
{
    private readonly TestDirectory _sandbox = new();
    private string P(params string[] segments) => Path.Combine(new[] { _sandbox.Path }.Concat(segments).ToArray());
    private ScreenshotCatalogService Catalog => new(P("shared"), P("home"), P("roaming"), P("local"), P("documents"));
    private string Write(string relative, string value = "image")
    {
        string file = P(relative);
        Directory.CreateDirectory(Path.GetDirectoryName(file)!);
        File.WriteAllText(file, value);
        return SafeFileOps.GetFinalPath(file);
    }

    [Fact]
    public void MissingLaunchersAreEmptyWithoutCreatingTheirFolders()
    {
        var scan = Catalog.Scan();
        Assert.Empty(scan.Entries);
        Assert.Empty(scan.Warnings);
        Assert.False(Directory.Exists(P("roaming")));
        Assert.False(Directory.Exists(P("shared")));
    }

    [Fact]
    public void DiscoversAllThreeLaunchersWithInstanceLabelsAndSharedImages()
    {
        string shared = Write("shared/screenshots/shared.png");
        Write("roaming/com.modrinth.theseus/profiles/Pack/screenshots/same.png");
        Write("home/curseforge/minecraft/Instances/Pack/screenshots/same.png");
        Write("roaming/PrismLauncher/instances/Pack/.minecraft/screenshots/same.JPG");
        Write("roaming/PrismLauncher/instances/Pack/.minecraft/mods/not-an-image.png");
        Write("roaming/PrismLauncher/instances/Pack/.minecraft/screenshots/sidecar.json");
        var scan = Catalog.Scan();
        Assert.Equal(4, scan.Entries.Count);
        Assert.Equal(4, scan.Folders);
        Assert.False(scan.Entries.Single(e => e.Path == shared).IsExternal);
        Assert.Equal(3, scan.Entries.Count(e => e.IsExternal));
        Assert.Contains(scan.Entries, e => e.Source == "Prism · Pack");
        Assert.Contains(scan.Entries, e => e.Source == "CurseForge · Pack");
        Assert.Contains(scan.Entries, e => e.Source == "Modrinth · Pack");
    }

    [Fact]
    public void CustomModrinthPrismAndCurseForgeConfigurationsAreReadWithoutMutation()
    {
        var modrinth = P("custom-mr");
        var curse = P("custom-cf");
        string mr = Write("roaming/ModrinthApp/settings.json", JsonSerializer.Serialize(new { custom_dir = modrinth }));
        string cf = Write("local/CurseForge/settings.json", JsonSerializer.Serialize(new { minecraft = new { minecraftModdingFolder = curse } }));
        string prism = Write("roaming/PrismLauncher/prismlauncher.cfg", "[General]\nInstanceDir=../../custom-prism\n");
        Write("custom-mr/profiles/A/screenshots/a.png");
        Write("custom-cf/Instances/B/screenshots/b.png");
        Write("custom-prism/C/minecraft/screenshots/c.jpeg");
        var before = new[] { mr, cf, prism }.ToDictionary(f => f, File.ReadAllText);
        Assert.Equal(3, Catalog.Scan().Entries.Count);
        Assert.All(before, pair => Assert.Equal(pair.Value, File.ReadAllText(pair.Key)));
    }

    [Fact]
    public void CurrentModrinthSqliteCustomDirectoryIsReadOnly()
    {
        string db = P("roaming/com.modrinth.theseus/app.db");
        Directory.CreateDirectory(Path.GetDirectoryName(db)!);
        Assert.Equal(0, sqlite3_open_v2(db, out IntPtr handle, 6, IntPtr.Zero));
        try
        {
            string path = P("custom-database").Replace("'", "''");
            Assert.Equal(0, sqlite3_exec(handle, $"CREATE TABLE settings(custom_dir TEXT); INSERT INTO settings VALUES ('{path}');", IntPtr.Zero, IntPtr.Zero, IntPtr.Zero));
        }
        finally { sqlite3_close(handle); }
        var bytes = File.ReadAllBytes(db);
        Write("custom-database/profiles/Database Pack/screenshots/db.png");
        Assert.Single(Catalog.Scan().Entries);
        Assert.Equal(bytes, File.ReadAllBytes(db));
    }

    [Fact]
    public void SharedJunctionsAndCyclesAppearOnlyOnce()
    {
        string file = Write("shared/screenshots/shared.png");
        string instance = P("roaming/PrismLauncher/instances/Linked/.minecraft");
        Directory.CreateDirectory(instance);
        SafeFileOps.CreateJunction(Path.Combine(instance, "screenshots"), P("shared/screenshots"));
        SafeFileOps.CreateJunction(Path.Combine(instance, "cycle"), P("roaming/PrismLauncher/instances"));
        var scan = Catalog.Scan();
        Assert.Equal(file, Assert.Single(scan.Entries).Path);
        Assert.Equal(1, scan.Folders);
        Assert.Empty(scan.Warnings);
    }

    [Fact]
    public void AddedPortableRootPersistsSharedContractAndDeduplicatesAliases()
    {
        Write("portable/instances/Test/.minecraft/screenshots/one.png");
        Catalog.AddRoot(P("portable"));
        SafeFileOps.CreateJunction(P("portable-link"), P("portable"));
        Catalog.AddRoot(P("portable-link"));
        Assert.Single(Catalog.LoadCustomRoots());
        Assert.Single(Catalog.Scan().Entries);
        using var json = JsonDocument.Parse(File.ReadAllText(P("shared/config/lads-screenshot-sources.json")));
        Assert.Equal(1, json.RootElement.GetProperty("version").GetInt32());
        Assert.Equal(P("portable"), json.RootElement.GetProperty("roots")[0].GetProperty("path").GetString());
    }

    [Fact]
    public void MalformedCustomConfigIsReportedAndDoesNotHideOtherSources()
    {
        Write("shared/config/lads-screenshot-sources.json", "{bad");
        Write("shared/screenshots/one.png");
        var result = Catalog.Scan();
        Assert.Single(result.Entries);
        Assert.Single(result.Warnings);
        Assert.Throws<JsonException>(() => Catalog.AddRoot(P("shared")));
        Assert.Equal("{bad", File.ReadAllText(P("shared/config/lads-screenshot-sources.json")));
    }

    [Fact]
    public void CancellationBeforeOrBetweenRootsNeverReturnsPartialSuccess()
    {
        using var cancelled = new CancellationTokenSource();
        cancelled.Cancel();
        Assert.ThrowsAny<OperationCanceledException>(() => Catalog.Scan(token: cancelled.Token));
        using var during = new CancellationTokenSource();
        IEnumerable<ScreenshotRoot> Roots()
        {
            yield return new(P("additional"), "Custom");
            during.Cancel();
            yield return new(P("second"), "Custom");
        }
        Assert.ThrowsAny<OperationCanceledException>(() => Catalog.Scan(Roots(), during.Token));
    }

    [Fact]
    public void BoundedDepthAvoidsUnrelatedTreesAndDoesNotChangeOriginals()
    {
        string included = Write("portable/Pack/.minecraft/screenshots/one.png");
        string tooDeep = Write("portable/a/b/c/d/e/screenshots/deep.png");
        string skipped = Write("portable/assets/screenshots/not-minecraft.png");
        Catalog.AddRoot(P("portable"));
        var result = Catalog.Scan();
        Assert.Equal(included, Assert.Single(result.Entries).Path);
        Assert.All(new[] { included, tooDeep, skipped }, file => Assert.Equal("image", File.ReadAllText(file)));
    }

    [Fact]
    public void GameSourceExportPreservesLowercaseContractAndLadsProfiles()
    {
        Directory.CreateDirectory(P("game"));
        Catalog.WriteGameSources(P("game"), new[] { new ScreenshotRoot(P("custom-profile"), "Lads · Custom") });
        using var doc = JsonDocument.Parse(File.ReadAllText(P("game/lads-screenshot-discovered.json")));
        Assert.Contains(doc.RootElement.GetProperty("roots").EnumerateArray(), e => e.GetProperty("path").GetString() == P("custom-profile"));
    }

    [Fact]
    public void NarrowCustomRootCanExtendAnEarlierDepthLimitedScan()
    {
        string image = Write("portable/a/b/c/d/e/screenshots/deep.png");
        Catalog.AddRoot(P("portable"));
        Catalog.AddRoot(P("portable/a/b/c"));
        Assert.Equal(image, Assert.Single(Catalog.Scan().Entries).Path);
    }

    [Fact]
    public void DamagedModrinthDatabaseProducesNoticeWithoutBlockingOtherImages()
    {
        Write("roaming/com.modrinth.theseus/app.db", "not a database");
        Write("shared/screenshots/one.png");
        var scan = Catalog.Scan();
        Assert.Single(scan.Entries);
        Assert.Single(scan.Warnings);
    }

    [Fact]
    public void ExplicitImageFolderSupportsRenamedArchivesWithoutCollectingLauncherIcons()
    {
        string chosen = Write("Pictures Archive/favorite.png");
        string nested = Write("Pictures Archive/Pack/screenshots/second.png");
        Write("roaming/PrismLauncher/instances/icon.png");
        Catalog.AddRoot(P("Pictures Archive"));
        var entries = Catalog.Scan().Entries;
        Assert.Equal(2, entries.Count);
        Assert.Contains(entries, e => e.Path == chosen);
        Assert.Contains(entries, e => e.Path == nested);
    }

    public void Dispose() => _sandbox.Dispose();
    [DllImport("winsqlite3", CallingConvention = CallingConvention.Cdecl)] private static extern int sqlite3_open_v2([MarshalAs(UnmanagedType.LPUTF8Str)] string filename, out IntPtr db, int flags, IntPtr vfs);
    [DllImport("winsqlite3", CallingConvention = CallingConvention.Cdecl)] private static extern int sqlite3_exec(IntPtr db, [MarshalAs(UnmanagedType.LPUTF8Str)] string sql, IntPtr callback, IntPtr context, IntPtr error);
    [DllImport("winsqlite3", CallingConvention = CallingConvention.Cdecl)] private static extern int sqlite3_close(IntPtr db);
}
