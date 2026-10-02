using System.Text.Json.Nodes;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class ModStateServiceTests : IDisposable
{
    private readonly ModSandbox box = new();
    private bool running;
    private ModStateService Service => new(_ => running);

    public ModStateServiceTests() => box.WriteManifest("26.2", Array.Empty<ClientModInstaller.Entry>());

    private Task<ModInventory> Inventory() => new ModInventoryService().BuildAsync(box.Bundle, box.Game, "26.2");

    private void Add(string file, string id, Dictionary<string, string>? depends = null, string version = "1.0.0", string[]? provides = null,
        (string, byte[])[]? nested = null) =>
        File.WriteAllBytes(box.Mod(file), ModSandbox.Jar(id, version, depends, provides, nested));

    private void LibraryChain(string suffix = "")
    {
        Add("lib.jar" + suffix, "lib");
        Add("mid.jar" + suffix, "mid", new() { ["lib"] = "*" });
        Add("top.jar" + suffix, "top", new() { ["mid"] = ">=1.0.0", ["minecraft"] = "*" });
        Add("other.jar", "other");
    }

    [Fact]
    public async Task DisablingALibraryListsEveryDependentTransitively()
    {
        LibraryChain();
        var plan = Service.Plan(await Inventory(), new[] { "lib" }, false);
        Assert.Equal(new[] { "mid", "top" }, plan.AlsoDisable);
        Assert.Empty(plan.Blockers);
        Assert.Empty(plan.AlsoEnable);
    }

    [Fact]
    public async Task AnotherProviderKeepsDependentsSatisfied()
    {
        LibraryChain();
        Add("alternative.jar", "altlib", provides: new[] { "lib" });
        Assert.Empty(Service.Plan(await Inventory(), new[] { "lib" }, false).AlsoDisable);
    }

    [Fact]
    public async Task EmbeddedLibrariesCascadeThroughTheirParentAndCannotBeSwitchedAlone()
    {
        Add("parent.jar", "parent", nested: new[] { ("META-INF/jars/nested.jar", ModSandbox.Jar("nestedlib")) });
        Add("consumer.jar", "consumer", new() { ["nestedlib"] = "*" });
        var inventory = await Inventory();
        Assert.Equal(new[] { "consumer" }, Service.Plan(inventory, new[] { "parent" }, false).AlsoDisable);
        var blocked = Service.Plan(inventory, new[] { "nestedlib" }, false);
        Assert.Contains("Embedded inside parent; disable parent to remove it", Assert.Single(blocked.Blockers));
    }

    [Fact]
    public async Task EnablingAModAlsoEnablesItsDisabledDependencies()
    {
        LibraryChain(".disabled");
        var plan = Service.Plan(await Inventory(), new[] { "top" }, true);
        Assert.Equal(new[] { "mid", "lib" }, plan.AlsoEnable);
        Assert.Empty(plan.Blockers);
        Assert.Empty(plan.Warnings);
    }

    // As the launch check (REQ-E-1): Fabric Loader supplies MixinExtras on every version, also one without a Lads pack.
    [Fact]
    public async Task MixinExtrasIsSuppliedByTheLoaderOnVersionsWithoutALadsPack()
    {
        Add("mixer.jar.disabled", "mixer", new() { ["mixinextras"] = ">=0.3.2" });
        var plan = Service.Plan(await new ModInventoryService().BuildAsync(box.Bundle, box.Game, "1.20.1"), new[] { "mixer" }, true);
        Assert.Empty(plan.Blockers);
        Assert.Empty(plan.AlsoEnable);
    }

    [Fact]
    public async Task UnobtainableDependencyBlocksAndVersionMismatchWarns()
    {
        Add("needy.jar.disabled", "needy", new() { ["nowhere"] = ">=1" });
        Add("strict.jar.disabled", "strict", new() { ["other"] = ">=2.0.0" });
        Add("other.jar", "other");
        var inventory = await Inventory();
        Assert.Contains("requires nowhere >=1, which is not installed", Assert.Single(Service.Plan(inventory, new[] { "needy" }, true).Blockers));
        var warned = Service.Plan(inventory, new[] { "strict" }, true);
        Assert.Empty(warned.Blockers);
        Assert.Contains(warned.Warnings, w => w.Contains("other >=2.0.0") && w.Contains("1.0.0"));
    }

    [Fact]
    public async Task PendingDownloadsAreWarnedAboutAndNotDownloadedDependenciesComeAlong()
    {
        LibraryChain();
        var pending = box.Pin("pendingmod", ModSandbox.Jar("pendingmod"));
        var library = box.Pin("offlib", ModSandbox.Jar("offlib"));
        box.WriteManifest("26.2", new[] { pending, library });
        box.Choose(("offlib", false));
        Add("wants.jar.disabled", "wants", new() { ["offlib"] = "*" });
        var inventory = await Inventory();

        Assert.Contains("1 mods are not downloaded yet: their dependencies are checked at next launch.",
            Service.Plan(inventory, new[] { "lib" }, false).Warnings);
        var enable = Service.Plan(inventory, new[] { "wants" }, true);
        Assert.Equal(new[] { "offlib" }, enable.AlsoEnable);
        Assert.Contains(enable.Warnings, w => w.Contains("not downloaded yet"));
    }

    [Fact]
    public async Task DisablingLadsCoreWarnsAndPlatformEntriesCannotBeSwitched()
    {
        box.WriteCore("26.2", ModSandbox.Core("26.2"));
        var inventory = await Inventory();
        Assert.Contains(ModStateService.CoreDisableWarning, Service.Plan(inventory, new[] { "theladscore" }, false).Warnings);
        Assert.NotEmpty(Service.Plan(inventory, new[] { "minecraft" }, false).Blockers);
        Assert.NotEmpty(Service.Plan(inventory, new[] { "unknown-mod" }, false).Blockers);
    }

    [Fact]
    public async Task ApplyRenamesTheWholePlanAndSavesTheChoices()
    {
        LibraryChain();
        var inventory = await Inventory();
        var result = await Service.ApplyAsync(box.Game, inventory, Service.Plan(inventory, new[] { "lib" }, false));

        Assert.Equal((true, true, false), (result.Success, result.AppliedToFiles, result.RestartRequired));
        foreach (var file in new[] { "lib.jar", "mid.jar", "top.jar" }) Assert.True(File.Exists(box.Mod(file + ".disabled")), file);
        Assert.True(File.Exists(box.Mod("other.jar")));
        var preferences = ModPreferences.Load(box.Game);
        Assert.All(new[] { "lib", "mid", "top" }, id => Assert.False(preferences.GetEnabled(id, null)));
        Assert.Null(preferences.GetEnabled("other", null));

        var reloaded = await Inventory();
        var back = await Service.ApplyAsync(box.Game, reloaded, Service.Plan(reloaded, new[] { "top" }, true));
        Assert.True(back.Success, back.Message);
        foreach (var file in new[] { "lib.jar", "mid.jar", "top.jar" }) Assert.True(File.Exists(box.Mod(file)), file);
    }

    [Fact]
    public async Task WhileTheGameRunsOnlyTheChoiceIsSaved()
    {
        LibraryChain();
        var inventory = await Inventory();
        running = true;
        var result = await Service.ApplyAsync(box.Game, inventory, Service.Plan(inventory, new[] { "lib" }, false));

        Assert.Equal((true, false, true), (result.Success, result.AppliedToFiles, result.RestartRequired));
        Assert.True(File.Exists(box.Mod("lib.jar")));
        Assert.Empty(Directory.GetFiles(box.Mods, "*.disabled"));
        Assert.False(ModPreferences.Load(box.Game).GetEnabled("lib", null));
    }

    [Fact]
    public async Task FailedRenameRestoresFilesAndSavedChoices()
    {
        LibraryChain();
        File.WriteAllText(box.State, """{"schema":1,"extra":"keep","mods":{"mid":{"enabled":true,"source":"game"}}}""");
        var before = JsonNode.Parse(File.ReadAllText(box.State))!["mods"]!.ToJsonString();
        var inventory = await Inventory();
        var plan = Service.Plan(inventory, new[] { "lib" }, false);

        ModToggleResult result;
        using (new FileStream(box.Mod("top.jar"), FileMode.Open, FileAccess.Read, FileShare.None))
            result = await Service.ApplyAsync(box.Game, inventory, plan);

        Assert.False(result.Success);
        Assert.Contains("top.jar", result.Message);
        Assert.Contains("restored", result.Message);
        foreach (var file in new[] { "lib.jar", "mid.jar", "top.jar" }) Assert.True(File.Exists(box.Mod(file)), file);
        Assert.Empty(Directory.GetFiles(box.Mods, "*.disabled"));
        var state = JsonNode.Parse(File.ReadAllText(box.State))!;
        Assert.Equal("keep", state["extra"]!.GetValue<string>());
        Assert.Equal(before, state["mods"]!.ToJsonString());
    }

    // T3: disabling next to a same-named .disabled twin (the v1.2.2 "reinstalled next to a disabled copy" state).
    [Theory]
    [InlineData("theladscore", false)] // LadsCore: the twin stays the disabled copy, the enabled one goes to mods-disabled
    [InlineData("uuu", true)]          // identical bytes: the same
    [InlineData("uuu", false)]         // a different jar: disabled under a free name, both kept
    public async Task DisablingNextToADisabledTwinKeepsEveryFile(string id, bool identical)
    {
        var enabled = id == "theladscore" ? ModSandbox.Core("26.2") : ModSandbox.Jar(id);
        var twin = identical ? enabled : id == "theladscore" ? ModSandbox.Jar(id, "1.1.0", new() { ["minecraft"] = "26.2" }) : ModSandbox.Jar(id, "0.9.0");
        File.WriteAllBytes(box.Mod(id + ".jar"), enabled);
        File.WriteAllBytes(box.Mod(id + ".jar.disabled"), twin);
        var inventory = await Inventory();

        var result = await Service.ApplyAsync(box.Game, inventory, Service.Plan(inventory, new[] { id }, false));

        Assert.True(result.Success, result.Message);
        Assert.False(File.Exists(box.Mod(id + ".jar")));
        Assert.Equal(twin, File.ReadAllBytes(box.Mod(id + ".jar.disabled")));
        var moved = id == "theladscore" || identical
            ? Assert.Single(Directory.GetFiles(Path.Combine(box.Game, "mods-disabled"), id + ".jar", SearchOption.AllDirectories))
            : box.Mod(id + " (2).jar.disabled");
        Assert.Equal(enabled, File.ReadAllBytes(moved));
        Assert.Contains(Path.GetFileName(moved), result.Message);
        Assert.False(ModPreferences.Load(box.Game).GetEnabled(id, null));
    }

    [Fact]
    public async Task NativeModuleToggleEditsOnlyItsEnabledFlagAndIsRefusedWhileRunning()
    {
        var config = Path.Combine(box.Game, "thelads_config.json");
        File.WriteAllText(config, """{"modules":{"FPS":{"enabled":true,"options":{"Scale":1.25},"favorite":true},"Zoom":{"enabled":false}},"hud":{"positions":{"FPS":[4,8]}},"future":1}""");

        running = true;
        var refused = await Service.SetNativeModuleAsync(box.Game, "FPS", false);
        Assert.False(refused.Success);
        Assert.Contains("in-game Lads menu", refused.Message);
        Assert.True(JsonNode.Parse(File.ReadAllText(config))!["modules"]!["FPS"]!["enabled"]!.GetValue<bool>());

        running = false;
        var applied = await Service.SetNativeModuleAsync(box.Game, "FPS", false);
        Assert.True(applied.Success, applied.Message);
        var root = JsonNode.Parse(File.ReadAllText(config))!;
        Assert.False(root["modules"]!["FPS"]!["enabled"]!.GetValue<bool>());
        Assert.Equal(1.25, root["modules"]!["FPS"]!["options"]!["Scale"]!.GetValue<double>());
        Assert.True(root["modules"]!["FPS"]!["favorite"]!.GetValue<bool>());
        Assert.False(root["modules"]!["Zoom"]!["enabled"]!.GetValue<bool>());
        Assert.Equal(8, root["hud"]!["positions"]!["FPS"]![1]!.GetValue<int>());
        Assert.Equal(1, root["future"]!.GetValue<int>());

        foreach (var unreadable in new[] { "{ not json", """{"modules":{"FPS":{"enabled":true},"FPS":{"enabled":false}}}""" })
        {
            File.WriteAllText(config, unreadable);
            Assert.False((await Service.SetNativeModuleAsync(box.Game, "FPS", true)).Success);
            Assert.Equal(unreadable, File.ReadAllText(config));
        }
    }

    [Fact]
    public async Task RestoreDefaultsClearsChoicesAndReEnablesThePack()
    {
        var bytes = ModSandbox.Jar("zoomify");
        var pin = box.Pin("zoomify", bytes);
        box.WriteManifest("26.2", new[] { pin });
        File.WriteAllBytes(box.Mod(pin.FileName + ".disabled"), bytes);
        Add("user.jar.disabled", "usermod");
        box.Choose(("zoomify", false), ("usermod", false));

        var result = await Service.RestoreDefaultsAsync(box.Game, await Inventory());

        Assert.True(result.Success, result.Message);
        Assert.Equal(bytes, File.ReadAllBytes(box.Mod(pin.FileName)));
        Assert.True(File.Exists(box.Mod("user.jar.disabled")));
        var preferences = ModPreferences.Load(box.Game);
        Assert.True(preferences.GetEnabled("zoomify", null));
        Assert.Null(preferences.GetEnabled("usermod", null));
    }

    private readonly List<string> recycled = new();
    private async Task<ModToggleResult> Reset()
    {
        using var client = box.Client();
        return await Service.ResetModsFolderAsync(box.Bundle, box.Game, "26.2", httpClient: client,
            recycle: path => { recycled.Add(Path.GetFileName(path)); File.Delete(path); });
    }

    [Fact]
    public async Task ResetModsFolderRecyclesEveryJarClearsChoicesAndReinstallsThePack()
    {
        var pin = box.Pin("sodium", ModSandbox.Jar("sodium"));
        box.WriteManifest("26.2", new[] { pin });
        box.WriteCore("26.2", ModSandbox.Core("26.2"));
        await box.Install();
        File.Move(box.Mod(pin.FileName), box.Mod(pin.FileName + ".disabled"));
        Add("own-1.0.0.jar", "own");
        Add("old-1.0.0.jar.disabled", "old");
        File.WriteAllText(box.State, """{"schema":1,"extra":"keep","mods":{"sodium":{"enabled":false},"own":{"enabled":true}}}""");
        var renderer = Path.Combine(box.Game, GraphicsRenderer.StateFile);
        File.WriteAllText(renderer, """{"Vulkan":true,"SuspendedMods":[]}""");

        var result = await Reset();

        Assert.True(result.Success, result.Message);
        Assert.Equal(new[] { "old-1.0.0.jar.disabled", "own-1.0.0.jar", pin.FileName + ".disabled" }, recycled.Order());
        Assert.Equal(new[] { pin.FileName, "theladscore.jar" }, Directory.GetFiles(box.Mods).Select(Path.GetFileName).Order());
        Assert.Null(ModPreferences.Load(box.Game).GetEnabled("sodium", null));
        Assert.Null(ModPreferences.Load(box.Game).GetEnabled("own", null));
        Assert.Contains("\"extra\": \"keep\"", File.ReadAllText(box.State));
        Assert.Equal(pin.Sha512, box.ReadReceipt()["sodium"]);
        Assert.Equal("""{"Vulkan":true,"SuspendedMods":[]}""", File.ReadAllText(renderer));
        Assert.Equal(1, box.Downloads); // the verified cached jar is reused
    }

    [Fact]
    public async Task ResetModsFolderClearsTheReceiptEvenWhenThePackCannotBeInstalledNow()
    {
        var sodium = ModSandbox.Jar("sodium");
        box.WriteManifest("26.2", new[] { box.Pin("sodium", sodium) with { Url = "https://cdn.modrinth.com/data/sodium/versions/offline/sodium.jar" } });
        box.WriteReceipt(("sodium", sodium));
        File.WriteAllBytes(box.Mod("sodium-1.0.0.jar"), sodium);

        var result = await Reset();

        Assert.False(result.Success);
        Assert.Contains("installed at the next launch", result.Message);
        Assert.Equal(new[] { "sodium-1.0.0.jar" }, recycled);
        Assert.Empty(Directory.GetFiles(box.Mods));
        Assert.False(File.Exists(box.Receipt));
    }

    [Fact]
    public async Task ResetModsFolderIsRefusedWhileTheGameRuns()
    {
        Add("own-1.0.0.jar", "own");
        box.Choose(("own", false));
        running = true;

        var result = await Reset();

        Assert.False(result.Success);
        Assert.Contains("running", result.Message);
        Assert.Empty(recycled);
        Assert.True(File.Exists(box.Mod("own-1.0.0.jar")));
        Assert.False(ModPreferences.Load(box.Game).GetEnabled("own", null));
    }

    public void Dispose() => box.Dispose();
}
