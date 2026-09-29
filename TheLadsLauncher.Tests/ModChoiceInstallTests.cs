using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>Saved choices (lads-mod-state.json) survive install, repair and update runs of both installers.</summary>
public sealed class ModChoiceInstallTests : IDisposable
{
    private readonly ModSandbox box = new();

    [Theory]
    [InlineData(null)]
    [InlineData("{ this is not json")]
    [InlineData("""{"mods":{"zoomify":{"enabled":true},"zoomify":{"enabled":true}}}""")]
    public async Task V122DisabledJarsStayDisabledWithoutAReadableStateFile(string? state)
    {
        var pack = ModSandbox.Jar("zoomify");
        var own = ModSandbox.Jar("usermod");
        var pin = box.Pin("zoomify", pack);
        box.WriteManifest("26.2", new[] { pin });
        box.WriteReceipt(("zoomify", pack));
        File.WriteAllBytes(box.Mod("lads-zoomify.jar.disabled"), pack);
        File.WriteAllBytes(box.Mod("user.jar.disabled"), own);
        if (state != null) File.WriteAllText(box.State, state);

        await box.Install();
        await box.Install();

        Assert.Equal(pack, File.ReadAllBytes(box.Mod(pin.FileName + ".disabled")));
        Assert.Equal(own, File.ReadAllBytes(box.Mod("user.jar.disabled")));
        Assert.Empty(Directory.GetFiles(box.Mods, "*.jar"));
        Assert.Equal(0, box.Downloads);
        if (state != null)
        {
            Assert.Equal(state, File.ReadAllText(box.State)); // The installer never rewrites choices.
            Assert.Contains(box.Messages, m => m.Contains("unreadable"));
        }
    }

    [Fact]
    public async Task YourOwnDisabledCopyNextToTheDisabledPackCopyDoesNotBlockTheLaunch()
    {
        var pack = ModSandbox.Jar("zoomify");
        var own = ModSandbox.Jar("zoomify", "own-build");
        var pin = box.Pin("zoomify", pack);
        box.WriteManifest("26.2", new[] { pin });
        box.WriteReceipt(("zoomify", pack));
        File.WriteAllBytes(box.Mod("lads-zoomify.jar.disabled"), pack);
        File.WriteAllBytes(box.Mod("zoomify-own.jar.disabled"), own);

        await box.Install();

        Assert.Equal(pack, File.ReadAllBytes(box.Mod(pin.FileName + ".disabled")));
        Assert.Equal(own, File.ReadAllBytes(box.Mod("zoomify-own.jar.disabled")));
        Assert.Empty(Directory.GetFiles(box.Mods, "*.jar"));

        // Enabling it would leave an enabled and a disabled copy: that is refused, naming both files.
        box.Choose(("zoomify", true));
        var error = await Assert.ThrowsAsync<IOException>(() => box.Install());
        Assert.Contains(box.Mod("zoomify-own.jar.disabled"), error.Message);
        Assert.Equal(own, File.ReadAllBytes(box.Mod("zoomify-own.jar.disabled")));
    }

    [Fact]
    public async Task ExplicitlyDisabledPackModIsNotDownloaded()
    {
        box.WriteManifest("26.2", new[] { box.Pin("zoomify", ModSandbox.Jar("zoomify")) });
        box.Choose(("zoomify", false));
        await box.Install();
        Assert.Empty(Directory.GetFiles(box.Mods));
        Assert.Equal(0, box.Downloads);
    }

    [Fact]
    public async Task DisablingAndReEnablingRenamesTheManagedJarWithExactBytes()
    {
        var bytes = ModSandbox.Jar("zoomify");
        var pin = box.Pin("zoomify", bytes);
        box.WriteManifest("26.2", new[] { pin });
        await box.Install();
        Assert.Equal(bytes, File.ReadAllBytes(box.Mod(pin.FileName)));

        box.Choose(("zoomify", false));
        await box.Install();
        Assert.Equal(bytes, File.ReadAllBytes(box.Mod(pin.FileName + ".disabled")));
        Assert.False(File.Exists(box.Mod(pin.FileName)));

        box.Choose(("zoomify", true));
        await box.Install();
        Assert.Equal(bytes, File.ReadAllBytes(box.Mod(pin.FileName)));
        Assert.False(File.Exists(box.Mod(pin.FileName + ".disabled")));
        Assert.Equal(1, box.Downloads);
    }

    [Fact]
    public async Task UserJarsFollowExplicitChoicesByRenameOnly()
    {
        box.WriteManifest("26.2", Array.Empty<ClientModInstaller.Entry>());
        var own = ModSandbox.Jar("usermod");
        File.WriteAllBytes(box.Mod("My Mod (1.0).jar"), own);
        box.Choose(("usermod", false));
        await box.Install();
        Assert.Equal(own, File.ReadAllBytes(box.Mod("My Mod (1.0).jar.disabled")));
        box.Choose(("usermod", true));
        await box.Install();
        Assert.Equal(own, File.ReadAllBytes(box.Mod("My Mod (1.0).jar")));
    }

    // REQ-E-1: a version the Lads pack does not ship (Create Profile, e.g. 1.20.1) still applies saved choices and checks
    // dependencies; T17: nothing is retired there (the receipt says nothing about such a version).
    [Fact]
    public async Task WithoutALadsManifestChoicesStillApplyDependenciesAreCheckedAndNothingIsRetired()
    {
        var sodium = ModSandbox.Jar("sodium");
        File.WriteAllBytes(box.Mod("sodium.jar"), sodium);
        var leftover = ModSandbox.Jar("aaa");
        box.WriteReceipt(("aaa", leftover));
        File.WriteAllBytes(box.Mod("aaa-1.0.0.jar"), leftover);
        File.WriteAllBytes(box.Mod("mixer.jar"), ModSandbox.Jar("mixer", depends: new() { ["mixinextras"] = ">=0.3.2" })); // Fabric Loader's own
        box.Choose(("sodium", false));

        await box.Install("1.20.1");

        Assert.Equal(sodium, File.ReadAllBytes(box.Mod("sodium.jar.disabled")));
        Assert.False(File.Exists(box.Mod("sodium.jar")));
        Assert.Equal(leftover, File.ReadAllBytes(box.Mod("aaa-1.0.0.jar")));
        Assert.False(Directory.Exists(Path.Combine(box.Cache, "retired")));
        Assert.Equal(new[] { "aaa" }, box.ReadReceipt().Keys);

        File.WriteAllBytes(box.Mod("needy.jar"), ModSandbox.Jar("needy", depends: new() { ["sodium"] = "*" }));
        var problem = await Assert.ThrowsAsync<ClientModDependencyException>(() => box.Install("1.20.1"));
        Assert.Equal(new[] { "needy" }, problem.DependentModIds);
        Assert.True(File.Exists(box.Mod("needy.jar")));
    }

    // T14: disabled while playing, next to your own disabled copy: the pack copy is renamed, no false "Duplicate" error.
    [Fact]
    public async Task DisablingNextToYourOwnDisabledCopyRenamesThePackCopy()
    {
        var pack = ModSandbox.Jar("xxx");
        var own = ModSandbox.Jar("xxx", "own-build");
        var pin = box.Pin("xxx", pack);
        box.WriteManifest("26.2", new[] { pin });
        box.WriteReceipt(("xxx", pack));
        File.WriteAllBytes(box.Mod(pin.FileName), pack);
        File.WriteAllBytes(box.Mod("mine-xxx.jar.disabled"), own);
        box.Choose(("xxx", false));

        await box.Install();

        Assert.Equal(pack, File.ReadAllBytes(box.Mod(pin.FileName + ".disabled")));
        Assert.Equal(own, File.ReadAllBytes(box.Mod("mine-xxx.jar.disabled")));
        Assert.Empty(Directory.GetFiles(box.Mods, "*.jar"));
    }

    [Fact]
    public async Task ChoiceSavedDuringDownloadsAbortsTheCommit()
    {
        var bytes = ModSandbox.Jar("zoomify");
        box.WriteManifest("26.2", new[] { box.Pin("zoomify", bytes) });
        await Assert.ThrowsAsync<IOException>(() => box.Install(status: message =>
        {
            if (message == "Preparing client mod changes...") box.Choose(("zoomify", false));
        }));
        Assert.Empty(Directory.GetFiles(box.Mods));
        Assert.False(File.Exists(box.Receipt));
    }

    [Fact]
    public async Task DependencyFailureNamesTheProblemAndTheFix()
    {
        var library = box.Pin("cloth-config", ModSandbox.Jar("cloth-config", name: "Cloth Config"));
        var consumer = box.Pin("betterf3", ModSandbox.Jar("betterf3", depends: new() { ["cloth-config"] = ">=26.2.0" }, name: "BetterF3"));
        box.WriteManifest("26.2", new[] { library, consumer });
        box.Choose(("cloth-config", false));
        var error = await Assert.ThrowsAsync<ClientModDependencyException>(() => box.Install());
        Assert.Contains("BetterF3 (betterf3) requires cloth-config >=26.2.0, which is disabled", error.Message);
        Assert.Contains("enable Mod cloth-config or disable BetterF3 in Mods", error.Message);
        Assert.Empty(Directory.GetFiles(box.Mods));
    }

    [Fact]
    public async Task ExplicitCoreDisableNeedsNoBundleAndInstallsNothing()
    {
        box.Choose(("theladscore", false));
        Assert.False(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "26.2"));
        Assert.Empty(Directory.GetFiles(box.Mods));
    }

    [Fact]
    public async Task CoreDisabledSurvivesBothInstallersAndComesBackWhenEnabled()
    {
        var core = ModSandbox.Core("26.2");
        box.WriteCore("26.2", core);
        var bytes = ModSandbox.Jar("zoomify");
        var pin = box.Pin("zoomify", bytes);
        box.WriteManifest("26.2", new[] { pin });
        Assert.True(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "26.2"));
        await box.Install();

        box.Choose(("theladscore", false));
        for (var run = 0; run < 2; run++)
        {
            await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "26.2");
            await box.Install();
            Assert.Equal(core, File.ReadAllBytes(box.Mod("theladscore.jar.disabled")));
            Assert.False(File.Exists(box.Mod("theladscore.jar")));
            Assert.Equal(bytes, File.ReadAllBytes(box.Mod(pin.FileName)));
        }

        box.Choose(("theladscore", true));
        Assert.True(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "26.2"));
        Assert.Equal(core, File.ReadAllBytes(box.Mod("theladscore.jar")));
        Assert.False(File.Exists(box.Mod("theladscore.jar.disabled")));
        Assert.Equal(core, File.ReadAllBytes(Directory.GetFiles(Path.Combine(box.Game, "mods-disabled"), "theladscore.jar.disabled",
            SearchOption.AllDirectories).Single()));
    }

    [Fact]
    public async Task DisabledCoreNextToAReinstalledCopyKeepsOnlyTheDisabledOne()
    {
        var core = ModSandbox.Core("26.2");
        var older = ModSandbox.Jar("theladscore", "1.1.0", new() { ["minecraft"] = "26.2" });
        box.WriteCore("26.2", core);
        File.WriteAllBytes(box.Mod("theladscore.jar.disabled"), older);
        File.WriteAllBytes(box.Mod("theladscore.jar"), core);
        box.Choose(("theladscore", false));

        Assert.True(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "26.2"));

        Assert.Equal(older, File.ReadAllBytes(box.Mod("theladscore.jar.disabled")));
        Assert.False(File.Exists(box.Mod("theladscore.jar")));
        Assert.Equal(core, File.ReadAllBytes(Directory.GetFiles(Path.Combine(box.Game, "mods-disabled"), "theladscore.jar",
            SearchOption.AllDirectories).Single()));
    }

    [Fact]
    public async Task WithoutAChoiceALeftoverDisabledCoreIsBackedUpAndCoreInstalled()
    {
        var core = ModSandbox.Core("26.2");
        var older = ModSandbox.Jar("theladscore", "1.1.0", new() { ["minecraft"] = "26.2" });
        box.WriteCore("26.2", core);
        File.WriteAllBytes(box.Mod("theladscore.jar.disabled"), older);

        Assert.True(await BundledModInstaller.InstallAsync(box.Bundle, box.Game, "26.2"));

        Assert.Equal(core, File.ReadAllBytes(box.Mod("theladscore.jar")));
        Assert.False(File.Exists(box.Mod("theladscore.jar.disabled")));
        Assert.Equal(older, File.ReadAllBytes(Directory.GetFiles(Path.Combine(box.Game, "mods-disabled"), "theladscore.jar.disabled",
            SearchOption.AllDirectories).Single()));
    }

    public void Dispose() => box.Dispose();
}
