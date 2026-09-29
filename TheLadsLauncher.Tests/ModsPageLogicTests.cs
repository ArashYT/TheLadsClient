using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>The non-UI logic behind the launcher's Mods page: filters and search, the loaded-id list, the user-jar rules
/// (add / replace, never overwrite, never touch pack files) and the launch dependency fixes.</summary>
public sealed class ModsPageLogicTests : IDisposable
{
    private readonly ModSandbox box = new();
    private readonly byte[] packJar = ModSandbox.Jar("packmod", name: "Pack Mod Jar Name");

    public ModsPageLogicTests()
    {
        box.WriteManifest("26.2", new[] { box.Pin("packmod", packJar, "Pack-Mod-1.0.jar") });
        box.WriteCore("26.2", ModSandbox.Core("26.2", new[] { ("META-INF/jars/corelib.jar", ModSandbox.Jar("corelib")) }));
        File.WriteAllBytes(box.Mod("Pack-Mod-1.0.jar"), packJar);
        File.WriteAllBytes(box.Mod("user-mod.jar"), ModSandbox.Jar("usermod", depends: new() { ["libjar"] = "*" },
            nested: new[] { ("META-INF/jars/deep.jar", ModSandbox.Jar("nestedlib")) }));
        File.WriteAllBytes(box.Mod("off-mod.jar.disabled"), ModSandbox.Jar("offmod"));
        File.WriteAllBytes(box.Mod("lib-jar.jar"), ModSandbox.Jar("libjar", library: true));
    }

    public void Dispose() => box.Dispose();

    private Task<ModInventory> Inventory() => new ModInventoryService().BuildAsync(box.Bundle, box.Game, "26.2");

    private static List<string> Ids(IEnumerable<ModInventoryView.Row> rows) => rows.Select(r => r.Entry.Id).ToList();

    [Fact]
    public async Task EachFilterShowsItsKindAndResetShowsEverything()
    {
        var inventory = await Inventory();
        var all = ModInventoryView.Filter(inventory, ModListFilter.All, "");
        Assert.Equal(inventory.Entries.Select(e => e.Id), Ids(all));
        Assert.All(all, r => Assert.False(r.Expanded));

        Assert.Equal(new[] { "theladscore", ModInventoryService.CatalogPlaceholderId }, Ids(ModInventoryView.Filter(inventory, ModListFilter.LadsModules, null)));
        Assert.Equal(new[] { "libjar", "offmod", "packmod", "usermod" },
            Ids(ModInventoryView.Filter(inventory, ModListFilter.ThirdParty, null)).Order());
        var enabled = Ids(ModInventoryView.Filter(inventory, ModListFilter.Enabled, null));
        Assert.Contains("usermod", enabled);
        Assert.Contains("theladscore", enabled);
        Assert.DoesNotContain("offmod", enabled);
        Assert.DoesNotContain("minecraft", enabled);
        Assert.Equal(new[] { "offmod" }, Ids(ModInventoryView.Filter(inventory, ModListFilter.Disabled, null)));

        // Libraries: the badge-marked jar, the jar another mod needs, and mods that embed libraries (opened to show them).
        var libraries = ModInventoryView.Filter(inventory, ModListFilter.Libraries, null);
        Assert.Contains(libraries, r => r.Entry.Id == "libjar" && !r.Expanded);
        Assert.Contains(libraries, r => r.Entry.Id == "usermod" && r.Expanded);
        Assert.Contains(libraries, r => r.Entry.Id == "theladscore" && r.Expanded);
        Assert.DoesNotContain(libraries, r => r.Entry.Ownership == ModOwnership.Platform || r.Entry.Id == "offmod");

        Assert.Equal(Ids(all), Ids(ModInventoryView.Filter(inventory, ModListFilter.All, "  ")));
    }

    [Fact]
    public async Task SearchCoversNamesIdsFileNamesAndEmbeddedChildren()
    {
        var inventory = await Inventory();
        var nested = Assert.Single(ModInventoryView.Filter(inventory, ModListFilter.All, "NESTEDLIB"));
        Assert.Equal("usermod", nested.Entry.Id);
        Assert.True(nested.Expanded);
        Assert.Equal("packmod", Assert.Single(ModInventoryView.Filter(inventory, ModListFilter.All, "pack-mod-1.0")).Entry.Id);
        Assert.Equal("packmod", Assert.Single(ModInventoryView.Filter(inventory, ModListFilter.All, "mod packmod")).Entry.Id); // upstream name
        var own = Assert.Single(ModInventoryView.Filter(inventory, ModListFilter.All, "usermod"));
        Assert.False(own.Expanded);
        // The filter still applies: the embedded match belongs to a third-party mod, not to Lads modules.
        Assert.Empty(ModInventoryView.Filter(inventory, ModListFilter.LadsModules, "nestedlib"));
        Assert.Empty(ModInventoryView.Filter(inventory, ModListFilter.All, "no such mod"));
    }

    [Fact]
    public async Task CountsLineAndLoadedIdsMatchTheFiles()
    {
        var inventory = await Inventory();
        Assert.Equal("3 enabled · 1 disabled · 1 pending · 0 unavailable", ModInventoryView.CountsText(inventory.Counts));
        // Loaded at start = the enabled top-level jars: not offmod (disabled), not nestedlib (embedded), not the bundled Core
        // that is not in Mods yet.
        Assert.Equal(new[] { "libjar", "packmod", "usermod" }, ModInventoryView.EnabledJarIds(inventory));
        // Jars without readable Fabric metadata are listed (as Invalid) but never recorded as loaded.
        File.WriteAllBytes(box.Mod("broken.jar"), new byte[] { 1, 2, 3 });
        File.WriteAllBytes(box.Mod("plain-library.jar"), ModSandbox.PlainJar());
        var withBroken = await Inventory();
        Assert.Equal(2, withBroken.Entries.Count(e => e.Status == ModEntryStatus.Invalid));
        Assert.Equal(new[] { "libjar", "packmod", "usermod" }, ModInventoryView.EnabledJarIds(withBroken));
    }

    // UI-1: right after a profile switch the old list may still be on screen; the page changes only the active profile's list.
    [Fact]
    public async Task AnInventoryBelongsOnlyToItsOwnProfileFolder()
    {
        var inventory = await Inventory();
        Assert.True(ModInventoryView.IsFor(inventory, box.Game + Path.DirectorySeparatorChar));
        Assert.True(ModInventoryView.IsFor(inventory, box.Game.ToUpperInvariant()));
        Assert.False(ModInventoryView.IsFor(inventory, Path.Combine(box.Root, "other-profile")));
        Assert.False(ModInventoryView.IsFor(null, box.Game));
    }

    [Fact]
    public async Task ANestedLibraryPointsAtTheTopLevelJarToSwitchOff()
    {
        File.WriteAllBytes(box.Mod("outer.jar"), ModSandbox.Jar("outermod", name: "Outer Mod",
            nested: new[] { ("META-INF/jars/middle.jar", ModSandbox.Jar("middlelib", name: "Middle Lib",
                nested: new[] { ("META-INF/jars/inner.jar", ModSandbox.Jar("innerlib")) })) }));
        var outer = (await Inventory()).Entries.Single(e => e.Id == "outermod");
        var middle = Assert.Single(outer.Children);
        Assert.Equal("Embedded inside Outer Mod; disable Outer Mod to remove it", middle.ToggleBlockedReason);
        // "Middle Lib" cannot be switched itself: the offered operation is on the jar in Mods.
        Assert.Equal("Embedded inside Middle Lib; disable Outer Mod to remove it", Assert.Single(middle.Children).ToggleBlockedReason);
    }

    [Fact]
    public async Task AddingNeverOverwritesAndRefusesPackMods()
    {
        var source = Path.Combine(box.Root, "incoming");
        Directory.CreateDirectory(source);
        string Drop(string name, byte[] bytes) { var path = Path.Combine(source, name); File.WriteAllBytes(path, bytes); return path; }

        var added = UserModFiles.Add(await Inventory(), Drop("fresh.jar", ModSandbox.Jar("fresh")));
        Assert.Equal(box.Mod("fresh.jar"), added);
        Assert.Equal("fresh", FabricModMetadata.ReadJar(added)!.Id);

        var inventory = await Inventory();
        var pack = Assert.Throws<InvalidOperationException>(() => UserModFiles.Add(inventory, Drop("other-name.jar", ModSandbox.Jar("packmod", "2.0.0"))));
        Assert.Contains("Switch the pack mod", pack.Message);
        Assert.Equal(packJar, File.ReadAllBytes(box.Mod("Pack-Mod-1.0.jar")));
        Assert.Throws<InvalidOperationException>(() => UserModFiles.Add(inventory, Drop("theladscore.jar", ModSandbox.Core("26.2"))));

        var before = File.ReadAllBytes(box.Mod("lib-jar.jar"));
        Assert.Throws<IOException>(() => UserModFiles.Add(inventory, Drop("lib-jar.jar", ModSandbox.Jar("brandnew"))));
        Assert.Equal(before, File.ReadAllBytes(box.Mod("lib-jar.jar")));
        Assert.Throws<IOException>(() => UserModFiles.Add(inventory, Drop("off-mod.jar", ModSandbox.Jar("brandnew2")))); // disabled twin
        Assert.Contains("already in Mods", Assert.Throws<InvalidOperationException>(() =>
            UserModFiles.Add(inventory, Drop("usermod-copy.jar", ModSandbox.Jar("usermod")))).Message);
        Assert.Throws<InvalidDataException>(() => UserModFiles.Add(inventory, Drop("plain.jar", ModSandbox.PlainJar())));
        Assert.False(File.Exists(box.Mod("brandnew.jar")) || File.Exists(box.Mod("usermod-copy.jar")) || File.Exists(box.Mod("plain.jar")));
        Assert.Empty(Directory.GetFiles(box.Cache, "incoming-*"));
    }

    [Fact]
    public async Task InstallReplacesOnlyYourCopyKeepsItDisabledAndBacksItUp()
    {
        var oldBytes = File.ReadAllBytes(box.Mod("off-mod.jar.disabled"));
        var download = Path.Combine(box.Root, "download.tmp");
        File.WriteAllBytes(download, ModSandbox.Jar("offmod", "2.0.0"));

        var inventory = await Inventory();
        Assert.Throws<InvalidDataException>(() => UserModFiles.Install(inventory, download, "off-mod-2.jar", expectedId: "usermod"));
        Assert.True(File.Exists(download));
        Assert.Equal(oldBytes, File.ReadAllBytes(box.Mod("off-mod.jar.disabled")));

        var path = UserModFiles.Install(inventory, download, "off-mod-2.jar", expectedId: "offmod");
        Assert.Equal(box.Mod("off-mod-2.jar.disabled"), path);
        Assert.False(File.Exists(download));
        Assert.False(File.Exists(box.Mod("off-mod.jar.disabled")));
        Assert.Equal("2.0.0", FabricModMetadata.ReadJar(path)!.Version);
        var backup = Assert.Single(Directory.GetFiles(Path.Combine(box.Cache, UserModFiles.BackupFolder), "*", SearchOption.AllDirectories));
        Assert.Equal(oldBytes, File.ReadAllBytes(backup));

        File.WriteAllBytes(download, ModSandbox.Jar("packmod", "9.0.0"));
        inventory = await Inventory();
        Assert.Throws<InvalidOperationException>(() => UserModFiles.Install(inventory, download, "Pack-Mod-9.jar"));
        Assert.Equal(packJar, File.ReadAllBytes(box.Mod("Pack-Mod-1.0.jar")));
        Assert.True(File.Exists(download));
    }

    [Theory]
    [InlineData("../escape.jar")]
    [InlineData("lads-sneaky.jar")]
    [InlineData("stream.jar:hidden")]
    public async Task InstallNeverLetsAnUntrustedNameOrVersionLeaveMods(string offeredName)
    {
        var download = Path.Combine(box.Root, "download.tmp");
        // The fallback name uses the jar's own version, which is untrusted text.
        File.WriteAllBytes(download, ModSandbox.Jar("travelmod", @"..\..\..\escaped"));

        var path = UserModFiles.Install(await Inventory(), download, offeredName);

        Assert.Equal(Path.GetFullPath(box.Mods), Path.GetDirectoryName(Path.GetFullPath(path)));
        Assert.StartsWith("travelmod-", Path.GetFileName(path));
        Assert.DoesNotContain("..", Path.GetFileName(path));
        Assert.Empty(Directory.GetFiles(box.Root, "escaped*", SearchOption.AllDirectories));
    }

    [Fact]
    public async Task UnsatisfiedDependenciesThrowATypedErrorWithBothWaysOut()
    {
        using var sandbox = new ModSandbox();
        sandbox.WriteManifest("26.2", Array.Empty<ClientModInstaller.Entry>());
        File.WriteAllBytes(sandbox.Mod("needy.jar"), ModSandbox.Jar("needy", depends: new() { ["lib"] = "*" }));
        File.WriteAllBytes(sandbox.Mod("lib.jar.disabled"), ModSandbox.Jar("lib"));

        var problem = await Assert.ThrowsAsync<ClientModDependencyException>(() => sandbox.Install());
                Assert.Equal(new[] { "needy" }, problem.DependentModIds);
        Assert.Equal(new[] { "lib" }, problem.MissingModIds);
        Assert.Contains("enable lib or disable needy in Mods", problem.Message);

        var state = new ModStateService(_ => false);
        var inventory = await new ModInventoryService().BuildAsync(sandbox.Bundle, sandbox.Game, "26.2");
        var (disable, enable) = state.DependencyFixes(inventory, problem);
        Assert.Equal(new[] { "needy" }, disable!.TargetIds);
        Assert.False(disable.Enable);
        Assert.Equal(new[] { "lib" }, enable!.TargetIds);
        Assert.True(enable.Enable);

        Assert.True((await state.ApplyAsync(sandbox.Game, inventory, enable)).Success);
        await sandbox.Install();
        Assert.True(File.Exists(sandbox.Mod("lib.jar")));
    }

    [Fact]
    public async Task AMissingLibraryOffersOnlyDisablingItsDependents()
    {
        using var sandbox = new ModSandbox();
        sandbox.WriteManifest("26.2", Array.Empty<ClientModInstaller.Entry>());
        File.WriteAllBytes(sandbox.Mod("needy.jar"), ModSandbox.Jar("needy", depends: new() { ["nowhere"] = "*" }));
        var problem = await Assert.ThrowsAsync<ClientModDependencyException>(() => sandbox.Install());
        var (disable, enable) = new ModStateService(_ => false).DependencyFixes(
            await new ModInventoryService().BuildAsync(sandbox.Bundle, sandbox.Game, "26.2"), problem);
        Assert.Equal(new[] { "needy" }, disable!.TargetIds);
        Assert.Null(enable);
    }
}
