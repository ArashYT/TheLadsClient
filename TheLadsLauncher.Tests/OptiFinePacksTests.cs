using System.IO.Compression;
using System.Text;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>1.8.9: resource packs with OptiFine features (CIT, CTM...) render through OptiFine instead of Ionium. Temp folders only.</summary>
public class OptiFinePacksTests
{
    private static byte[] Zip(params string[] entries)
    {
        using var bytes = new MemoryStream();
        using (var zip = new ZipArchive(bytes, ZipArchiveMode.Create, true))
            foreach (var entry in entries)
            {
                var (name, content) = entry.Split('|') is [var n, var c] ? (n, c) : (entry, "x");
                using var writer = new StreamWriter(zip.CreateEntry(name).Open(), Encoding.UTF8);
                writer.Write(content);
            }
        return bytes.ToArray();
    }

    private static byte[] ForgeJar(string id) => Zip($"mcmod.info|[{{\"modid\": \"{id}\", \"name\": \"{id}\"}}]");

    private static void EnablePacks(ModSandbox box, params string[] names) =>
        File.WriteAllText(Path.Combine(box.Game, "options.txt"), "lang:en_US\nresourcePacks:" + System.Text.Json.JsonSerializer.Serialize(names) + "\n");

    private static string Packs(ModSandbox box) => Directory.CreateDirectory(Path.Combine(box.Game, "resourcepacks")).FullName;

    [Theory]
    [InlineData("assets/minecraft/optifine/cit/swords/diamond.properties", "CIT")]
    [InlineData("assets/minecraft/mcpatcher/cit/pots.properties", "CIT")]
    [InlineData("assets/minecraft/optifine/ctm/glass/glass.properties", "CTM")]
    [InlineData("assets/minecraft/optifine/cem/creeper.jem", "CEM")]
    [InlineData("assets\\minecraft\\mcpatcher\\ctm\\glass.properties", "CTM")]
    [InlineData("assets/minecraft/mcpatcher/sky/world0/sky1.properties", "custom sky")]
    [InlineData("assets/minecraft/optifine/colormap/grass.png", "custom colors")]
    [InlineData("assets/minecraft/mcpatcher/color.properties", "custom colors")]
    [InlineData("assets/minecraft/optifine/emissive.properties", "emissive")]
    [InlineData("assets/minecraft/OptiFine/Natural.properties", "natural textures")]
    [InlineData("assets/minecraft/optifine/bettergrass.properties", "better grass")]
    [InlineData("assets/minecraft/optifine/cit/", null)] // an empty folder is no rule
    [InlineData("assets/minecraft/optifine/cit/sword.png", null)]
    [InlineData("assets/minecraft/textures/blocks/glass.png", null)]
    [InlineData("assets/minecraft/optifine/cit.properties", null)]
    public void FeatureNamesTheOptiFineFeatureOfAPackFile(string entry, string? feature) =>
        Assert.Equal(feature, OptiFinePacks.Feature(entry));

    [Fact]
    public void OnlyEnabledPacksOn189CountAndAlwaysIoniumNeverSwitches()
    {
        using var box = new ModSandbox();
        var packs = Packs(box);
        File.WriteAllBytes(Path.Combine(packs, "PvP.zip"), Zip("pack.mcmeta", "assets/minecraft/optifine/natural.properties",
            "assets/minecraft/optifine/cit/sword.properties", "assets/minecraft/textures/items/diamond_sword.png"));
        File.WriteAllBytes(Path.Combine(packs, "Plain.zip"), Zip("pack.mcmeta", "assets/minecraft/textures/blocks/stone.png"));

        Assert.Null(OptiFinePacks.Notice(box.Game, "1.8.9", OptiFinePacks.Auto)); // no options.txt yet
        EnablePacks(box, "Plain.zip");
        Assert.Null(OptiFinePacks.Notice(box.Game, "1.8.9", OptiFinePacks.Auto));
        EnablePacks(box, "Plain.zip", "PvP.zip", "Missing.zip");
        Assert.Equal("Using OptiFine: your resource pack uses OptiFine features (natural textures) that Ionium can't show yet",
            OptiFinePacks.Notice(box.Game, "1.8.9", OptiFinePacks.Auto)); // Lads Core draws the CIT under Ionium
        Assert.Contains("(CIT/natural textures)", OptiFinePacks.Notice(box.Game, "1.8.9", OptiFinePacks.AlwaysOptiFine));
        Assert.Null(OptiFinePacks.Notice(box.Game, "1.8.9", OptiFinePacks.AlwaysIonium));
        Assert.Null(OptiFinePacks.Notice(box.Game, "1.20.1", OptiFinePacks.Auto));
        // A name that leaves the folder is ignored.
        File.WriteAllBytes(Path.Combine(box.Root, "Outside.zip"), Zip("assets/minecraft/optifine/emissive.properties"));
        EnablePacks(box, "../Outside.zip", "..\\Outside.zip");
        Assert.Null(OptiFinePacks.Notice(box.Game, "1.8.9", OptiFinePacks.Auto));
    }

    [Fact]
    public async Task APackWhoseOnlyOptiFineFeatureIsCitKeepsIoniumUnderAuto()
    {
        using var box = new ModSandbox();
        box.WriteManifest("1.8.9", new[] { box.Pin(OptiFineInstaller.PylonModId, ForgeJar("pylon")), box.Pin(OptiFineInstaller.IoniumModId, ForgeJar("ionium")) });
        var ionium = box.Mod("ionium-1.0.0.jar");
        var packs = Packs(box);
        File.WriteAllBytes(Path.Combine(packs, "Skyblock.zip"), Zip("assets/minecraft/optifine/cit/swords/aote.properties",
            "assets/minecraft/mcpatcher/cit/potions/heal.properties", "assets/minecraft/optifine/cit/swords/aote.png"));
        File.WriteAllBytes(Path.Combine(packs, "Sky.zip"), Zip("assets/minecraft/optifine/sky/world0/sky1.properties"));
        EnablePacks(box, "Skyblock.zip");

        Assert.Null(OptiFinePacks.Notice(box.Game, "1.8.9", OptiFinePacks.Auto));
        Assert.Contains("(CIT)", OptiFinePacks.Notice(box.Game, "1.8.9", OptiFinePacks.AlwaysOptiFine)); // the player's choice still wins
        using (var client = box.Client())
            Assert.Null(await ClientModInstaller.InstallAsync(box.Bundle, box.Game, "1.8.9", box.Messages.Add, default, client, OptiFinePacks.Auto));
        Assert.True(File.Exists(ionium));
        Assert.False(File.Exists(Path.Combine(box.Cache, ClientModInstaller.IoniumOffForPackMarker)));

        // Another feature beside it still needs OptiFine, and the notice names only that one.
        EnablePacks(box, "Skyblock.zip", "Sky.zip");
        Assert.Equal("Using OptiFine: your resource pack uses OptiFine features (custom sky) that Ionium can't show yet",
            OptiFinePacks.Notice(box.Game, "1.8.9", OptiFinePacks.Auto));
    }

    [Fact]
    public void FolderPacksAndPacksInsidePackFoldersAreFound()
    {
        using var box = new ModSandbox();
        var packs = Packs(box);
        var folderPack = Path.Combine(packs, "Sky Pack");
        Directory.CreateDirectory(Path.Combine(folderPack, "assets", "minecraft", "mcpatcher", "sky", "world0"));
        File.WriteAllText(Path.Combine(folderPack, "pack.mcmeta"), "{}");
        File.WriteAllText(Path.Combine(folderPack, "assets", "minecraft", "mcpatcher", "sky", "world0", "sky1.properties"), "");
        var nested = Directory.CreateDirectory(Path.Combine(packs, "PvP", "Bedwars")).FullName; // plain folders, no pack.mcmeta
        File.WriteAllBytes(Path.Combine(nested, "Glass.zip"), Zip("assets/minecraft/optifine/natural.properties"));

        EnablePacks(box, "Sky Pack", "Glass.zip");
        Assert.Equal(new[] { folderPack, Path.Combine(nested, "Glass.zip") }, OptiFinePacks.EnabledPacks(box.Game));
        Assert.Contains("(custom sky/natural textures)", OptiFinePacks.Notice(box.Game, "1.8.9", OptiFinePacks.Auto));
    }

    [Fact]
    public void AZipScanIsCachedBySizeAndTime()
    {
        using var box = new ModSandbox();
        var pack = Path.Combine(Packs(box), "Cit.zip");
        var bytes = Zip("assets/minecraft/optifine/cit/a.properties");
        File.WriteAllBytes(pack, bytes);
        var time = new DateTime(2026, 1, 2, 3, 4, 5, DateTimeKind.Utc);
        File.SetLastWriteTimeUtc(pack, time);
        Assert.Equal(new[] { "CIT" }, OptiFinePacks.Features(pack));

        // Same size and time: the cached result (the bytes are not read again).
        File.WriteAllBytes(pack, new byte[bytes.Length]);
        File.SetLastWriteTimeUtc(pack, time);
        Assert.Equal(new[] { "CIT" }, OptiFinePacks.Features(pack));

        // Changed: scanned again (now unreadable: no features, no exception).
        File.SetLastWriteTimeUtc(pack, time.AddMinutes(1));
        Assert.Empty(OptiFinePacks.Features(pack));
    }

    [Fact]
    public async Task ALaunchWithAnOptiFineFeaturePackKeepsIoniumOffOnlyForThatLaunch()
    {
        using var box = new ModSandbox();
        box.WriteManifest("1.8.9", new[] { box.Pin(OptiFineInstaller.PylonModId, ForgeJar("pylon")), box.Pin(OptiFineInstaller.IoniumModId, ForgeJar("ionium")) });
        var ionium = box.Mod("ionium-1.0.0.jar");
        var marker = Path.Combine(box.Cache, ClientModInstaller.IoniumOffForPackMarker);
        File.WriteAllBytes(Path.Combine(Packs(box), "PvP.zip"), Zip("assets/minecraft/optifine/natural.properties"));
        async Task<string?> Launch(string? setting)
        {
            using var client = box.Client();
            return await ClientModInstaller.InstallAsync(box.Bundle, box.Game, "1.8.9", box.Messages.Add, default, client, setting);
        }

        Assert.Null(await Launch(OptiFinePacks.Auto));
        Assert.True(File.Exists(ionium));

        // The CTM pack enabled: Ionium off (renamed, never deleted), the notice returned, the marker kept.
        EnablePacks(box, "PvP.zip");
        Assert.Contains("(natural textures)", await Launch(OptiFinePacks.Auto));
        Assert.True(File.Exists(ionium + ".disabled"));
        Assert.True(File.Exists(marker));
        Assert.Contains(box.Messages, m => m.StartsWith("Using OptiFine", StringComparison.Ordinal));
        Assert.NotNull(await Launch(OptiFinePacks.Auto)); // the next launch still knows why it is off

        // The Mods page (no launch, no check) and a launch without the pack bring it back: the disk state was not the player's.
        Assert.Null(await Launch(null));
        Assert.True(File.Exists(ionium));
        Assert.False(File.Exists(marker));
        Assert.NotNull(await Launch(OptiFinePacks.Auto));
        EnablePacks(box);
        Assert.Null(await Launch(OptiFinePacks.Auto));
        Assert.True(File.Exists(ionium));
        Assert.False(File.Exists(marker));

        // Always Ionium keeps it; a Mods page choice of off stays off without the marker.
        EnablePacks(box, "PvP.zip");
        Assert.Null(await Launch(OptiFinePacks.AlwaysIonium));
        Assert.True(File.Exists(ionium));
        box.Choose((OptiFineInstaller.IoniumModId, false));
        Assert.Null(await Launch(OptiFinePacks.Auto));
        Assert.True(File.Exists(ionium + ".disabled"));
        Assert.False(File.Exists(marker));

        // A chosen-on Ionium is still switched off for the launch (Auto); the choice itself stays.
        box.Choose((OptiFineInstaller.IoniumModId, true));
        Assert.NotNull(await Launch(OptiFinePacks.Auto));
        Assert.True(File.Exists(ionium + ".disabled"));
        Assert.Equal(true, ModPreferences.Load(box.Game).GetEnabled(OptiFineInstaller.IoniumModId, null));
    }

    [Fact]
    public void APackWithCtmAndCemKeepsIoniumUnderAuto()
    {
        using var box = new ModSandbox();
        var packs = Packs(box);
        File.WriteAllBytes(Path.Combine(packs, "IoniumPack.zip"), Zip("pack.mcmeta",
            "assets/minecraft/optifine/ctm/glass.properties",
            "assets/minecraft/optifine/cem/creeper.jem",
            "assets/minecraft/optifine/colormap/grass.png"));
        EnablePacks(box, "IoniumPack.zip");
        Assert.Null(OptiFinePacks.Notice(box.Game, "1.8.9", OptiFinePacks.Auto));
    }
}