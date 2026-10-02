using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>Mods removed from the pack (receipt ids no longer in the manifest, and the manifest's "retired" list, e.g. GoodMC), and
/// bytes Lads published that have no receipt.</summary>
public sealed class RetirementTests : IDisposable
{
    private readonly ModSandbox box = new();
    private readonly byte[] goodmc = ModSandbox.Jar("goodmc", "6.0.1f");
    private string Retired(string fileName) => Path.Combine(box.Cache, "retired", "goodmc", fileName);

    private void WritePackWithoutGoodMc() =>
        box.WriteManifest("26.2", new[] { box.Pin("sodium", ModSandbox.Jar("sodium")) },
            new() { new("goodmc", "hwir46QE", "GoodMC: Old Combat & Blockhitting", new() { ModSandbox.Sha(goodmc) }, "Removed from the pack.") });

    [Theory]
    [InlineData("lads-goodmc.jar")]
    [InlineData("lads-goodmc.jar.disabled")]
    [InlineData("GoodMC-Fabric-26.3-6.0.1f.jar")]
    [InlineData("GoodMC-Fabric-26.3-6.0.1f.jar.disabled")]
    public async Task ManagedCopiesAreMovedToTheRetiredBackupWithExactBytes(string fileName)
    {
        WritePackWithoutGoodMc();
        box.WriteReceipt(("goodmc", goodmc));
        File.WriteAllBytes(box.Mod(fileName), goodmc);

        await box.Install();

        Assert.False(File.Exists(box.Mod(fileName)));
        Assert.Equal(goodmc, File.ReadAllBytes(Retired(fileName)));
        Assert.False(box.ReadReceipt().ContainsKey("goodmc"));
        Assert.Contains("Retired goodmc.", box.Messages);
    }

    [Fact]
    public async Task LegacyNameWithAPublishedHashIsRetiredEvenWithoutAReceipt()
    {
        WritePackWithoutGoodMc();
        File.WriteAllBytes(box.Mod("lads-goodmc.jar.disabled"), goodmc);
        await box.Install();
        Assert.Equal(goodmc, File.ReadAllBytes(Retired("lads-goodmc.jar.disabled")));
    }

    // 1.4.8: bytes Lads published are Lads' own under any name, receipt or not (a lost receipt left them behind forever).
    [Fact]
    public async Task PublishedJarWithoutReceiptIsRetiredWhateverItsName()
    {
        WritePackWithoutGoodMc();
        File.WriteAllBytes(box.Mod("GoodMC-Fabric-26.3-6.0.1f.jar"), goodmc);
        await box.Install();
        Assert.False(File.Exists(box.Mod("GoodMC-Fabric-26.3-6.0.1f.jar")));
        Assert.Equal(goodmc, File.ReadAllBytes(Retired("GoodMC-Fabric-26.3-6.0.1f.jar")));
    }

    // A standalone jar of a mod Core now embeds switches Core's copy off: an older published pin goes too, without a receipt.
    [Fact]
    public async Task StalePublishedStandaloneJarOfANativeModIsRetiredWithoutAReceipt()
    {
        var older = ModSandbox.Jar("entityculling", "1.8.0");
        box.WriteManifest("26.2", new[] { box.Pin("sodium", ModSandbox.Jar("sodium")) },
            new() { new("entityculling", "NNAgCjsB", "Entity Culling", new() { ModSandbox.Sha(older), ModSandbox.Sha(ModSandbox.Jar("entityculling", "1.9.0")) },
                "Replaced by native Lads Core functionality in 1.4.6.") });
        box.WriteReceipt(("sodium", ModSandbox.Jar("sodium")));
        File.WriteAllBytes(box.Mod("entityculling-fabric-1.8.0.jar"), older);

        await box.Install();

        Assert.False(File.Exists(box.Mod("entityculling-fabric-1.8.0.jar")));
        Assert.Equal(older, File.ReadAllBytes(Path.Combine(box.Cache, "retired", "entityculling", "entityculling-fabric-1.8.0.jar")));
        Assert.Contains("Retired entityculling.", box.Messages);
    }

    [Fact]
    public async Task NeverPublishedBuildOfARetiredModIsKeptAndStaysInTheReceipt()
    {
        WritePackWithoutGoodMc();
        box.WriteReceipt(("goodmc", goodmc));
        var own = ModSandbox.Jar("goodmc", "6.0.2");
        File.WriteAllBytes(box.Mod("GoodMC-6.0.2.jar"), own);

        await box.Install();
        await box.Install();

        Assert.Equal(own, File.ReadAllBytes(box.Mod("GoodMC-6.0.2.jar")));
        Assert.False(Directory.Exists(Path.Combine(box.Cache, "retired")));
        Assert.Contains(box.Messages, m => m.Contains("Kept") && m.Contains("added or modified by you"));
        Assert.Equal(ModSandbox.Sha(goodmc), box.ReadReceipt()["goodmc"]);
    }

    [Fact]
    public async Task PublishedJarAddedBackAfterRetirementIsRetiredAgain()
    {
        WritePackWithoutGoodMc();
        box.WriteReceipt(("goodmc", goodmc));
        File.WriteAllBytes(box.Mod("lads-goodmc.jar"), goodmc);
        await box.Install();
        Assert.True(File.Exists(Retired("lads-goodmc.jar")));

        File.WriteAllBytes(box.Mod("GoodMC-Fabric-26.3-6.0.1f.jar"), goodmc);
        await box.Install();

        Assert.False(File.Exists(box.Mod("GoodMC-Fabric-26.3-6.0.1f.jar")));
        Assert.Equal(2, Directory.GetFiles(Path.Combine(box.Cache, "retired", "goodmc")).Length);
    }

    [Fact]
    public async Task ModifiedManagedCopyStaysAndDoesNotBlockTheLaunch()
    {
        WritePackWithoutGoodMc();
        box.WriteReceipt(("goodmc", goodmc));
        var modified = ModSandbox.Jar("goodmc", "6.0.1f-patched");
        File.WriteAllBytes(box.Mod("lads-goodmc.jar"), modified);

        await box.Install();

        Assert.Equal(modified, File.ReadAllBytes(box.Mod("lads-goodmc.jar")));
        Assert.Contains(box.Messages, m => m.Contains("Kept") && m.Contains("goodmc"));
        Assert.True(File.Exists(box.Mod("sodium-1.0.0.jar")));
    }

    [Fact]
    public async Task OlderPublishedPinIsUpgradedWithoutAReceipt()
    {
        var older = ModSandbox.Jar("sodium", "0.8.0");
        var pin = box.Pin("sodium", ModSandbox.Jar("sodium", "0.8.1"), version: "0.8.1");
        box.WriteManifest("26.2", new[] { pin }, published: new() { ["sodium"] = new() { ModSandbox.Sha(older) } });
        File.WriteAllBytes(box.Mod("sodium-fabric-0.8.0.jar"), older);
        var listed = (await new ModInventoryService().BuildAsync(box.Bundle, box.Game, "26.2")).Entries.Single(e => e.FileName == "sodium-fabric-0.8.0.jar");
        Assert.Equal(ModOwnership.Pack, listed.Ownership);

        await box.Install();

        Assert.False(File.Exists(box.Mod("sodium-fabric-0.8.0.jar")));
        Assert.Equal(pin.Sha512, ModSandbox.Sha(File.ReadAllBytes(box.Mod(pin.FileName))));
        Assert.Equal(pin.Sha512, box.ReadReceipt()["sodium"]);
        Assert.Contains("Installed Mod sodium.", box.Messages);
    }

    // T1: the disabled file was the only record of the choice (v1.2.2 or a manual rename); retiring it must not lose it.
    [Fact]
    public async Task RetiringADisabledCopyKeepsTheChoiceWhenThePackShipsTheModAgain()
    {
        var yyy = ModSandbox.Jar("yyy");
        box.WriteManifest("26.2", Array.Empty<ClientModInstaller.Entry>());
        box.WriteReceipt(("yyy", yyy));
        File.WriteAllBytes(box.Mod("yyy-1.0.0.jar.disabled"), yyy);
        File.WriteAllText(box.State, """{"schema":1,"extra":"keep","mods":{"other":{"enabled":true}}}""");

        await box.Install();

        Assert.Equal(yyy, File.ReadAllBytes(Path.Combine(box.Cache, "retired", "yyy", "yyy-1.0.0.jar.disabled")));
        var saved = ModPreferences.Load(box.Game);
        Assert.False(saved.GetEnabled("yyy", null));
        Assert.True(saved.GetEnabled("other", null));
        Assert.Contains("\"extra\": \"keep\"", File.ReadAllText(box.State));

        // A later pack ships it again: it stays disabled and is not downloaded.
        box.WriteManifest("26.2", new[] { box.Pin("yyy", yyy) });
        await box.Install();
        Assert.Empty(Directory.GetFiles(box.Mods));
        Assert.Equal(0, box.Downloads);
    }

    [Fact]
    public async Task ReceiptOnlyRetirementKeepsBothNamesWhenTheBackupNameIsTaken()
    {
        box.WriteManifest("26.2", Array.Empty<ClientModInstaller.Entry>());
        box.WriteReceipt(("goodmc", goodmc));
        Directory.CreateDirectory(Path.GetDirectoryName(Retired("x"))!);
        File.WriteAllText(Retired("lads-goodmc.jar"), "an earlier backup");
        File.WriteAllBytes(box.Mod("lads-goodmc.jar"), goodmc);

        await box.Install();

        Assert.Equal("an earlier backup", File.ReadAllText(Retired("lads-goodmc.jar")));
        Assert.Equal(goodmc, File.ReadAllBytes(Retired("lads-goodmc (2).jar")));
    }

    public void Dispose() => box.Dispose();
}
