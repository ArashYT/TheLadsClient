using System.Text.Json;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class FilenameMigrationTests : IDisposable
{
    private readonly ModSandbox box = new();

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task LegacyManagedJarOnTheCurrentPinGetsTheOriginalNameWithExactBytes(bool disabled)
    {
        var bytes = ModSandbox.Jar("sodium");
        var pin = box.Pin("sodium", bytes, "sodium-fabric-0.8.0+mc26.2.jar");
        box.WriteManifest("26.2", new[] { pin });
        box.WriteReceipt(("sodium", bytes));
        var suffix = disabled ? ".disabled" : "";
        File.WriteAllBytes(box.Mod("lads-sodium.jar" + suffix), bytes);

        await box.Install();

        Assert.Equal(bytes, File.ReadAllBytes(box.Mod(pin.FileName + suffix)));
        Assert.False(File.Exists(box.Mod("lads-sodium.jar" + suffix)));
        Assert.False(File.Exists(box.Mod(disabled ? pin.FileName : pin.FileName + ".disabled")));
        Assert.Equal(0, box.Downloads);
        // The receipt stays the flat id -> SHA-512 map older launchers read.
        Assert.Equal(ModSandbox.Sha(bytes), box.ReadReceipt()["sodium"], ignoreCase: true);
    }

    [Fact]
    public async Task DisabledOlderPinKeepsItsLegacyNameWhileModrinthIsUnreachable()
    {
        var old = ModSandbox.Jar("sodium", "0.7.0");
        var next = ModSandbox.Jar("sodium", "0.8.0");
        box.WriteManifest("26.2", new[] { box.Pin("sodium", next) });
        box.WriteReceipt(("sodium", old));
        File.WriteAllBytes(box.Mod("lads-sodium.jar.disabled"), old);

        await box.Install();

        Assert.Equal(old, File.ReadAllBytes(box.Mod("lads-sodium.jar.disabled")));
        Assert.Single(Directory.GetFiles(box.Mods));
        Assert.Equal(0, box.Downloads);
        Assert.Contains(box.Messages, m => m.Contains("Could not look up the original file name"));
        Assert.Contains(box.Requests, r => r == $"https://api.modrinth.com/v2/version_file/{ModSandbox.Sha(old)}?algorithm=sha512");
    }

    [Fact]
    public async Task DisabledOlderPinIsRenamedToTheNameModrinthPublishedForThoseBytes()
    {
        var old = ModSandbox.Jar("sodium", "0.7.0");
        box.WriteManifest("26.2", new[] { box.Pin("sodium", ModSandbox.Jar("sodium", "0.8.0")) });
        box.WriteReceipt(("sodium", old));
        File.WriteAllBytes(box.Mod("lads-sodium.jar.disabled"), old);
        box.VersionFile = _ => JsonSerializer.Serialize(new { files = new object[] {
            new { filename = "sodium-sources.jar", primary = false, hashes = new { sha512 = new string('0', 128) } },
            new { filename = "sodium-fabric-0.7.0.jar", primary = true, hashes = new { sha512 = ModSandbox.Sha(old) } } } });

        await box.Install();
        Assert.Equal(old, File.ReadAllBytes(box.Mod("sodium-fabric-0.7.0.jar.disabled")));
        Assert.Single(Directory.GetFiles(box.Mods));

        // Once it has its original name nothing is looked up again, and it stays disabled.
        box.VersionFile = null;
        var lookups = box.Requests.Count;
        await box.Install();
        Assert.Equal(lookups, box.Requests.Count);
        Assert.Equal(old, File.ReadAllBytes(box.Mod("sodium-fabric-0.7.0.jar.disabled")));
    }

    [Fact]
    public async Task ReEnablingADisabledOlderPinUpgradesItUnderTheOriginalName()
    {
        var old = ModSandbox.Jar("sodium", "0.7.0");
        var next = ModSandbox.Jar("sodium", "0.8.0");
        var pin = box.Pin("sodium", next);
        box.WriteManifest("26.2", new[] { pin });
        box.WriteReceipt(("sodium", old));
        File.WriteAllBytes(box.Mod("lads-sodium.jar.disabled"), old);
        box.Choose(("sodium", true));

        await box.Install();

        Assert.Equal(next, File.ReadAllBytes(box.Mod(pin.FileName)));
        Assert.Single(Directory.GetFiles(box.Mods));
        Assert.Equal(old, File.ReadAllBytes(Assert.Single(Directory.GetFiles(box.Cache, "previous-sodium-*"))));
        Assert.Equal(ModSandbox.Sha(next), box.ReadReceipt()["sodium"], ignoreCase: true);
    }

    [Fact]
    public async Task IdenticalCopyAtTheOriginalNameSendsTheLegacyDuplicateToBackup()
    {
        var bytes = ModSandbox.Jar("sodium");
        var pin = box.Pin("sodium", bytes);
        box.WriteManifest("26.2", new[] { pin });
        File.WriteAllBytes(box.Mod(pin.FileName), bytes);
        File.WriteAllBytes(box.Mod("lads-sodium.jar"), bytes);

        await box.Install();

        Assert.Equal(bytes, File.ReadAllBytes(box.Mod(pin.FileName)));
        Assert.False(File.Exists(box.Mod("lads-sodium.jar")));
        Assert.Equal(bytes, File.ReadAllBytes(Assert.Single(Directory.GetFiles(box.Cache, "duplicate-sodium-*"))));
    }

    [Fact]
    public async Task DifferentCopyOfTheSameModAtTheOriginalNameIsAnActionableErrorAndNothingMoves()
    {
        var bytes = ModSandbox.Jar("sodium");
        var own = ModSandbox.Jar("sodium", "user-build");
        var pin = box.Pin("sodium", bytes);
        box.WriteManifest("26.2", new[] { pin });
        box.WriteReceipt(("sodium", bytes));
        var receipt = File.ReadAllBytes(box.Receipt);
        File.WriteAllBytes(box.Mod(pin.FileName), own);
        File.WriteAllBytes(box.Mod("lads-sodium.jar"), bytes);

        var error = await Assert.ThrowsAsync<IOException>(() => box.Install());

        Assert.Contains(box.Mod(pin.FileName), error.Message);
        Assert.Contains(box.Mod("lads-sodium.jar"), error.Message);
        Assert.Equal(own, File.ReadAllBytes(box.Mod(pin.FileName)));
        Assert.Equal(bytes, File.ReadAllBytes(box.Mod("lads-sodium.jar")));
        Assert.Equal(receipt, File.ReadAllBytes(box.Receipt));
    }

    [Fact]
    public async Task OriginalNameTakenByAnotherModLeavesTheLegacyJarUntouched()
    {
        var bytes = ModSandbox.Jar("sodium");
        var other = ModSandbox.Jar("othermod");
        var pin = box.Pin("sodium", bytes, "shared-name.jar");
        box.WriteManifest("26.2", new[] { pin });
        File.WriteAllBytes(box.Mod("shared-name.jar"), other);
        File.WriteAllBytes(box.Mod("lads-sodium.jar"), bytes);

        await box.Install();

        Assert.Equal(other, File.ReadAllBytes(box.Mod("shared-name.jar")));
        Assert.Equal(bytes, File.ReadAllBytes(box.Mod("lads-sodium.jar")));
        Assert.Contains(box.Messages, m => m.Contains("Kept") && m.Contains("lads-sodium.jar"));
        Assert.Equal(0, box.Downloads);
    }

    [Fact]
    public async Task FailingRenameRollsBackEveryRenameAndTheReceipt()
    {
        var first = ModSandbox.Jar("alpha");
        var second = ModSandbox.Jar("beta");
        var alpha = box.Pin("alpha", first);
        var beta = box.Pin("beta", second);
        box.WriteManifest("26.2", new[] { alpha, beta });
        File.WriteAllBytes(box.Mod("lads-alpha.jar"), first);
        File.WriteAllBytes(box.Mod("lads-beta.jar.disabled"), second);
        box.WriteReceipt(("alpha", first));
        var receipt = File.ReadAllBytes(box.Receipt);
        var renames = 0;

        await Assert.ThrowsAsync<IOException>(() => box.Install(status: message =>
        {
            if (message.StartsWith("Renamed", StringComparison.Ordinal) && ++renames == 2) throw new IOException("Injected rename failure");
        }));

        Assert.Equal(first, File.ReadAllBytes(box.Mod("lads-alpha.jar")));
        Assert.Equal(second, File.ReadAllBytes(box.Mod("lads-beta.jar.disabled")));
        Assert.Equal(2, Directory.GetFiles(box.Mods).Length);
        Assert.Equal(receipt, File.ReadAllBytes(box.Receipt));
        Assert.Empty(Directory.GetFiles(box.Mods, "*.tmp"));
    }

    [Theory]
    [InlineData("../escape.jar")]
    [InlineData("sub\\dir.jar")]
    [InlineData("lads-sodium.jar")]
    [InlineData("theladscore.jar")]
    [InlineData("sodium.zip")]
    [InlineData("CON.jar")]
    [InlineData("a:b.jar")]
    [InlineData(" sodium.jar")]
    public async Task UnsafeFileNamesAreRejectedBeforeAnyDownload(string fileName)
    {
        box.WriteManifest("26.2", new[] { box.Pin("sodium", ModSandbox.Jar("sodium")) with { FileName = fileName } });
        await Assert.ThrowsAsync<InvalidDataException>(() => box.Install());
        Assert.Empty(box.Requests);
        Assert.Empty(Directory.GetFiles(box.Mods));
    }

    [Fact]
    public async Task FileNamesMustBeUniqueIgnoringCase()
    {
        box.WriteManifest("26.2", new[] { box.Pin("alpha", ModSandbox.Jar("alpha"), "Shared.jar"), box.Pin("beta", ModSandbox.Jar("beta"), "shared.JAR") });
        await Assert.ThrowsAsync<InvalidDataException>(() => box.Install());
        Assert.Empty(box.Requests);
    }

    public void Dispose() => box.Dispose();
}
