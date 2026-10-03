using System.Text.Json;
using System.Text.Json.Nodes;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class ModInventoryServiceTests : IDisposable
{
    private readonly ModSandbox box = new();

    private Task<ModInventory> Build(IReadOnlyCollection<string>? loaded = null) =>
        new ModInventoryService(_ => loaded).BuildAsync(box.Bundle, box.Game, "26.2");

    private static ModInventoryEntry Row(ModInventory inventory, string id, ModOwnership? ownership = null) =>
        inventory.Entries.Single(e => e.Id == id && (ownership == null || e.Ownership == ownership));

    private void WriteCatalog(string minecraftVersion = "26.2") =>
        File.WriteAllText(Path.Combine(box.Game, "lads-core-catalog.json"), JsonSerializer.Serialize(new
        {
            schema = 1, coreVersion = "1.2.0", minecraftVersion, writtenAt = "2026-09-29T12:00:00Z",
            modules = new object[]
            {
                new { name = "FPS", description = "Frame counter", category = "HUD", support = "builtIn", label = "Built in", detail = "", externalModId = (string?)null, enabled = true, toggleable = true },
                new { name = "DiscordRPC", description = "Presence", category = "General", support = "builtIn", label = "Soon", detail = "Coming soon", externalModId = (string?)null, enabled = false, toggleable = false },
                new { name = "Exordium", description = "", category = "Performance", support = "unavailable", label = "Unavailable", detail = "No release for this version", externalModId = "exordium", enabled = false, toggleable = false },
                new { name = "Lithium", description = "", category = "Performance", support = "external", label = "Lithium", detail = "", externalModId = "alpha", enabled = true, toggleable = false },
                new { name = "Zoom", description = "Zoom key", category = "General", support = "builtIn", label = "Built in", detail = "", externalModId = "zeta", enabled = true, toggleable = true }
            }
        }));

    private (ClientModInstaller.Entry Alpha, ClientModInstaller.Entry Beta, ClientModInstaller.Entry Gamma, ClientModInstaller.Entry Delta,
        ClientModInstaller.Entry Epsilon) SeedProfile()
    {
        var core = ModSandbox.Core("26.2", new[] {
            ("META-INF/jars/catconfig.jar", ModSandbox.Jar("catconfig-mc", library: true)),
            ("META-INF/jars/java-objc-bridge-1.0.0.jar", ModSandbox.PlainJar()) });
        box.WriteCore("26.2", core);
        File.WriteAllBytes(box.Mod("theladscore.jar"), core);
        var alphaBytes = ModSandbox.Jar("alpha", "2.0.0");
        var alpha = box.Pin("alpha", alphaBytes);
        File.WriteAllBytes(box.Mod(alpha.FileName), alphaBytes);
        var betaBytes = ModSandbox.Jar("beta");
        var beta = box.Pin("beta", betaBytes);
        File.WriteAllBytes(box.Mod("lads-beta.jar.disabled"), betaBytes);
        var gamma = box.Pin("gamma", ModSandbox.Jar("gamma"));
        File.WriteAllBytes(box.Mod("gamma-mine.jar"), ModSandbox.Jar("gamma", "mine"));
        var deltaBytes = ModSandbox.Jar("delta", depends: new() { ["alpha"] = ">=2.0.0" },
            nested: new[] { ("META-INF/jars/deltalib.jar", ModSandbox.Jar("deltalib")) });
        var delta = box.Pin("delta", deltaBytes);
        Directory.CreateDirectory(box.Cache);
        File.WriteAllBytes(Path.Combine(box.Cache, delta.Sha512 + ".jar"), deltaBytes);
        var epsilon = box.Pin("epsilon", ModSandbox.Jar("epsilon"));
        box.Choose(("epsilon", false));
        File.WriteAllBytes(box.Mod("usermod.jar"), ModSandbox.Jar("usermod", nested: new[] {
            ("META-INF/jars/helper.jar", ModSandbox.Jar("helperlib")), ("META-INF/jars/plain-lib.jar", ModSandbox.PlainJar()) }));
        File.WriteAllBytes(box.Mod("broken.jar"), new byte[] { 1, 2, 3 });
        File.WriteAllBytes(box.Mod("plain.jar.disabled"), ModSandbox.PlainJar());
        var gone = ModSandbox.Jar("gone");
        File.WriteAllBytes(box.Mod("lads-gone.jar"), gone);
        File.WriteAllBytes(box.Mod("oldmc.jar"), ModSandbox.Jar("oldmc", depends: new() { ["minecraft"] = "1.21.1" }));
        box.WriteReceipt(("alpha", alphaBytes), ("gone", gone));
        box.WriteManifest("26.2", new[] { alpha, beta, gamma, delta, epsilon });
        box.WriteManifest("26.3", new[] { box.Pin("alpha", alphaBytes), box.Pin("zeta", ModSandbox.Jar("zeta")) });
        WriteCatalog();
        File.WriteAllText(Path.Combine(box.Game, "thelads_config.json"), """{"modules":{"FPS":{"enabled":false}},"hud":{"locked":[]}}""");
        return (alpha, beta, gamma, delta, epsilon);
    }

    [Fact]
    public async Task InventoryListsEveryFileEveryPackEntryAndEveryLimitation()
    {
        var pins = SeedProfile();
        var inventory = await Build();

        // Counts are the real files in Mods; every file is exactly one row.
        Assert.Equal(Directory.GetFiles(box.Mods, "*.jar").Length, inventory.Counts.EnabledFiles);
        Assert.Equal(Directory.GetFiles(box.Mods, "*.jar.disabled").Length, inventory.Counts.DisabledFiles);
        var fileRows = inventory.Entries.Where(e => e.FilePath != null).Select(e => e.FilePath!).OrderBy(p => p).ToList();
        Assert.Equal(Directory.GetFiles(box.Mods).OrderBy(p => p), fileRows);

        var alpha = Row(inventory, "alpha");
        Assert.Equal((ModOwnership.Pack, ModEntryStatus.Installed, "Mod alpha", "2.0.0"), (alpha.Ownership, alpha.Status, alpha.DisplayName, alpha.Version));
        Assert.Equal(pins.Alpha.ProjectUrl, alpha.ProjectUrl);
        Assert.Contains("Lads integration: Lithium", alpha.Note);
        var beta = Row(inventory, "beta");
        Assert.Equal((ModOwnership.Pack, ModEntryStatus.Disabled, false), (beta.Ownership, beta.Status, beta.RequestedEnabled));
        Assert.Equal(ModEntryStatus.Invalid, Row(inventory, "gamma", ModOwnership.User).Status);
        Assert.Contains("Conflicts with pack mod", Row(inventory, "gamma", ModOwnership.User).Note);
        Assert.Equal(ModEntryStatus.PendingDownload, Row(inventory, "gamma", ModOwnership.Pack).Status);
        var delta = Row(inventory, "delta");
        Assert.Equal((ModEntryStatus.PendingDownload, true), (delta.Status, delta.DependenciesKnown));
        Assert.Contains("alpha >=2.0.0", delta.Depends);
        Assert.Equal("deltalib", Assert.Single(delta.Children).Id);
        var epsilon = Row(inventory, "epsilon");
        Assert.Equal((ModEntryStatus.NotDownloaded, false, false), (epsilon.Status, epsilon.RequestedEnabled, epsilon.DependenciesKnown));
        Assert.Equal(inventory.Entries.Count(e => e.Status == ModEntryStatus.PendingDownload), inventory.Counts.PendingDownloads);

        var user = Row(inventory, "usermod");
        Assert.Equal(ModOwnership.User, user.Ownership);
        Assert.Equal(new[] { "helperlib", "plain-lib.jar" }, user.Children.Select(c => c.Id));
        Assert.All(user.Children, child =>
        {
            Assert.Equal((ModOwnership.Embedded, ModEntryStatus.Embedded, false, "usermod"), (child.Ownership, child.Status, child.CanToggle, child.ParentId));
            Assert.Equal("Embedded inside usermod; disable usermod to remove it", child.ToggleBlockedReason);
        });
        var core = Row(inventory, "theladscore");
        Assert.Equal((ModOwnership.Core, ModEntryStatus.Installed, true), (core.Ownership, core.Status, core.CanToggle));
        Assert.Equal(new[] { "catconfig-mc", "java-objc-bridge-1.0.0.jar" }, core.Children.Select(c => c.Id));
        Assert.True(core.Children[0].IsLibrary);
        Assert.Equal(5, inventory.Counts.Embedded);

        Assert.Equal(ModEntryStatus.Invalid, Row(inventory, "broken.jar").Status);
        Assert.Equal(ModEntryStatus.Invalid, Row(inventory, "plain.jar.disabled").Status);
        Assert.False(Row(inventory, "plain.jar.disabled").EnabledOnDisk);
        var gone = Row(inventory, "gone");
        Assert.Equal((ModOwnership.Retired, ModEntryStatus.RetiredCopy, false), (gone.Ownership, gone.Status, gone.CanToggle));
        Assert.Equal(ModEntryStatus.Unsupported, Row(inventory, "oldmc").Status);

        var zeta = Row(inventory, "zeta");
        Assert.Equal((ModEntryStatus.Unavailable, false), (zeta.Status, zeta.CanToggle));
        Assert.Equal("Not in the Lads pack for 26.2 (included for 26.3). Lads provides its own Zoom instead", zeta.Note);
        Assert.Equal(1, inventory.Counts.Unavailable);

        foreach (var id in new[] { "minecraft", "fabricloader", "java" })
            Assert.Equal((ModOwnership.Platform, false), (Row(inventory, id).Ownership, Row(inventory, id).CanToggle));
        Assert.Equal("26.2", Row(inventory, "minecraft").Version);

        var fps = Row(inventory, "FPS");
        Assert.Equal((ModOwnership.NativeModule, ModEntryStatus.Disabled, true), (fps.Ownership, fps.Status, fps.CanToggle));
        Assert.False(Row(inventory, "DiscordRPC").CanToggle);
        Assert.Equal("This Lads module cannot be switched on or off yet", Row(inventory, "DiscordRPC").ToggleBlockedReason);
        Assert.Equal(ModEntryStatus.Unavailable, Row(inventory, "Exordium").Status);
        Assert.DoesNotContain(inventory.Entries, e => e.Id == "Lithium");
        Assert.Equal(4, inventory.Counts.NativeModules);
        Assert.False(inventory.GameRunning);
        Assert.All(Flatten(inventory.Entries), e => Assert.Null(e.LoadedNow));
    }

    [Fact]
    public async Task RunningGameShowsLoadedStateSeparatelyFromTheNextLaunch()
    {
        SeedProfile();
        var inventory = await Build(new[] { "alpha", "beta", "theladscore" });

        Assert.True(inventory.GameRunning);
        Assert.Equal((true, true, false), (Row(inventory, "alpha").LoadedNow, Row(inventory, "alpha").RequestedEnabled, Row(inventory, "alpha").RestartRequired));
        Assert.Equal((true, false, true), (Row(inventory, "beta").LoadedNow, Row(inventory, "beta").RequestedEnabled, Row(inventory, "beta").RestartRequired));
        Assert.Equal((false, true), (Row(inventory, "delta").LoadedNow, Row(inventory, "delta").RestartRequired));
        Assert.Equal((false, true), (Row(inventory, "delta").Children[0].LoadedNow, Row(inventory, "delta").Children[0].RestartRequired));
        var fps = Row(inventory, "FPS");
        Assert.Equal((false, ModInventoryService.NativeRunningReason), (fps.CanToggle, fps.ToggleBlockedReason));
    }

    [Fact]
    public async Task MissingOrStaleLadsModuleListIsExplainedInsteadOfHidden()
    {
        SeedProfile();
        File.Delete(Path.Combine(box.Game, "lads-core-catalog.json"));
        var missing = Row(await Build(), ModInventoryService.CatalogPlaceholderId);
        Assert.Equal("Launch this profile once to list Lads modules", missing.ToggleBlockedReason);

        WriteCatalog("26.3");
        var stale = Row(await Build(), "FPS");
        Assert.False(stale.CanToggle);
        Assert.Contains("from Minecraft 26.3", stale.ToggleBlockedReason);

        WriteCatalog();
        box.Choose(("theladscore", false));
        var coreOff = await Build();
        Assert.Contains("LadsCore is disabled", Row(coreOff, "FPS").ToggleBlockedReason);
        Assert.False(Row(coreOff, "theladscore").RequestedEnabled);

        // With Core disabled, launching cannot write the list, so the placeholder says what to do instead.
        File.Delete(Path.Combine(box.Game, "lads-core-catalog.json"));
        Assert.Contains("enable LadsCore", Row(await Build(), ModInventoryService.CatalogPlaceholderId).ToggleBlockedReason);
    }

    [Theory]
    [InlineData("[1,2]")]
    [InlineData("""{"modules":{"FPS":{"enabled":true},"FPS":{"enabled":false}}}""")]
    [InlineData("{ not json")]
    public async Task UnreadableModuleSettingsBlockNativeTogglesInsteadOfFailingTheList(string config)
    {
        SeedProfile();
        File.WriteAllText(Path.Combine(box.Game, "thelads_config.json"), config);
        var fps = Row(await Build(), "FPS");
        Assert.False(fps.CanToggle);
        Assert.Contains("thelads_config.json", fps.ToggleBlockedReason);
        Assert.Contains("unreadable", fps.ToggleBlockedReason);
    }

    [Fact]
    public async Task SnapshotIsCamelCaseJsonWithNestedChildren()
    {
        SeedProfile();
        var service = new ModInventoryService();
        var inventory = await service.BuildAsync(box.Bundle, box.Game, "26.2");
        await service.WriteSnapshotAsync(inventory);

        var root = JsonNode.Parse(File.ReadAllText(Path.Combine(box.Cache, "inventory.json")))!;
        Assert.Equal(1, root["schema"]!.GetValue<int>());
        Assert.Equal("26.2", root["minecraftVersion"]!.GetValue<string>());
        var entries = root["entries"]!.AsArray();
        Assert.Equal(inventory.Entries.Count, entries.Count);
        var delta = entries.Single(e => e!["id"]!.GetValue<string>() == "delta")!;
        Assert.Equal("pack", delta["ownership"]!.GetValue<string>());
        Assert.Equal("pendingDownload", delta["status"]!.GetValue<string>());
        Assert.Equal("Mod delta", delta["displayName"]!.GetValue<string>());
        var core = entries.Single(e => e!["id"]!.GetValue<string>() == "theladscore")!;
        Assert.Equal("embedded", core["children"]![0]!["status"]!.GetValue<string>());
        Assert.Contains(entries, e => e!["ownership"]!.GetValue<string>() == "nativeModule");
    }

    [Theory]
    [InlineData("1.21.11")]
    [InlineData("26.2")]
    [InlineData("26.3")]
    public async Task ShippedManifestsAreListedCompletelyForAnEmptyProfile(string version)
    {
        // The launcher's own game-mods folder (copied next to the tests); only read.
        var bundle = AppContext.BaseDirectory;
        var manifests = Directory.GetDirectories(Path.Combine(bundle, "game-mods")).ToDictionary(d => Path.GetFileName(d)!,
            d => ClientModInstallerManifest(Path.Combine(d, "client-mods.json")));
        var inventory = await new ModInventoryService().BuildAsync(bundle, box.Game, version);

        var selected = manifests[version]!.Mods.Select(m => m.ModId).ToHashSet();
        Assert.Equal(selected.OrderBy(i => i), inventory.Entries.Where(e => e.Status == ModEntryStatus.PendingDownload && e.Ownership == ModOwnership.Pack)
            .Select(e => e.Id).OrderBy(i => i));
        var retired = manifests.Values.SelectMany(m => m!.Retired ?? new()).Select(r => r.ModId).ToHashSet();
        var elsewhere = manifests.Where(m => m.Key != version).SelectMany(m => m.Value!.Mods).Select(m => m.ModId)
            .Where(id => !selected.Contains(id) && !retired.Contains(id)).ToHashSet();
        Assert.Equal(elsewhere.OrderBy(i => i), inventory.Entries.Where(e => e.Status == ModEntryStatus.Unavailable && e.Ownership == ModOwnership.Pack)
            .Select(e => e.Id).OrderBy(i => i));
        Assert.Equal((0, 0, elsewhere.Count), (inventory.Counts.EnabledFiles, inventory.Counts.DisabledFiles, inventory.Counts.Unavailable));
    }

    // T17: the installer retires nothing for a version without a Lads manifest, so its receipt jars are ordinary, switchable jars.
    [Fact]
    public async Task WithoutAManifestReceiptJarsAreNotShownAsRetired()
    {
        var aaa = ModSandbox.Jar("aaa");
        box.WriteReceipt(("aaa", aaa));
        File.WriteAllBytes(box.Mod("aaa-1.0.0.jar"), aaa);

        var row = Row(await new ModInventoryService().BuildAsync(box.Bundle, box.Game, "1.20.1"), "aaa");

        Assert.Equal((ModOwnership.User, ModEntryStatus.Installed), (row.Ownership, row.Status));
        Assert.True(row.CanToggle);
        Assert.Null(row.ToggleBlockedReason);
    }

    private static IEnumerable<ModInventoryEntry> Flatten(IEnumerable<ModInventoryEntry> rows) =>
        rows.SelectMany(row => Flatten(row.Children).Prepend(row));

    private static ClientModInstaller.Manifest? ClientModInstallerManifest(string path) =>
        JsonSerializer.Deserialize<ClientModInstaller.Manifest>(File.ReadAllText(path), new JsonSerializerOptions { PropertyNameCaseInsensitive = true });

    public void Dispose() => box.Dispose();
}
