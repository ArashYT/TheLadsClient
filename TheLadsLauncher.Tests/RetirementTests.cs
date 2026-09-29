using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>Mods removed from the pack (receipt ids no longer in the manifest, and the manifest's "retired" list, e.g. GoodMC).</summary>
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

    [Fact]
    public async Task UserAddedIdenticalJarWithoutReceiptIsLeftInPlace()
    {
        WritePackWithoutGoodMc();
        File.WriteAllBytes(box.Mod("GoodMC-Fabric-26.3-6.0.1f.jar"), goodmc);
        await box.Install();
        Assert.Equal(goodmc, File.ReadAllBytes(box.Mod("GoodMC-Fabric-26.3-6.0.1f.jar")));
        Assert.False(Directory.Exists(Path.Combine(box.Cache, "retired")));
    }

    [Fact]
    public async Task JarAddedBackAfterRetirementStays()
    {
        WritePackWithoutGoodMc();
        box.WriteReceipt(("goodmc", goodmc));
        File.WriteAllBytes(box.Mod("lads-goodmc.jar"), goodmc);
        await box.Install();
        Assert.True(File.Exists(Retired("lads-goodmc.jar")));

        File.WriteAllBytes(box.Mod("GoodMC-Fabric-26.3-6.0.1f.jar"), goodmc);
        await box.Install();

        Assert.Equal(goodmc, File.ReadAllBytes(box.Mod("GoodMC-Fabric-26.3-6.0.1f.jar")));
        Assert.Single(Directory.GetFiles(Path.Combine(box.Cache, "retired", "goodmc")));
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
