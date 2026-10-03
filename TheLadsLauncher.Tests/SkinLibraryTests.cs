using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class SkinLibraryTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "lads-skins-" + Guid.NewGuid().ToString("N"));
    public SkinLibraryTests() => Directory.CreateDirectory(_root);
    public void Dispose() => Directory.Delete(_root, true);

    /// <summary>The PNG signature and IHDR size: all the library reads.</summary>
    internal static byte[] Png(int width, int height, byte fill = 0)
    {
        var png = new byte[64];
        new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, (byte)'I', (byte)'H', (byte)'D', (byte)'R' }.CopyTo(png, 0);
        BitConverter.GetBytes(System.Buffers.Binary.BinaryPrimitives.ReverseEndianness(width)).CopyTo(png, 16);
        BitConverter.GetBytes(System.Buffers.Binary.BinaryPrimitives.ReverseEndianness(height)).CopyTo(png, 20);
        png[63] = fill;
        return png;
    }

    [Fact]
    public void SkinsAndCombosSurviveAReloadInSkinsFolderAndJson()
    {
        var library = new SkinLibrary(_root);
        var steve = library.Add(Png(64, 64, 1), "  Steve  ", "classic");
        var alex = library.Add(Png(64, 32, 2), "Alex", "SLIM");
        library.Update(steve.Id, "Steve (blue)", "slim");
        library.AddCombo("Weekend", alex, "classic", "cape-1", "Migrator");
        library.AddCombo("Plain", steve, "slim", null, "ignored");

        var reloaded = new SkinLibrary(_root);
        Assert.Equal(new[] { "Steve (blue)", "Alex" }, reloaded.Skins.Select(s => s.Name));
        Assert.Equal(new[] { "slim", "slim" }, reloaded.Skins.Select(s => s.Model));
        Assert.Equal(Png(64, 32, 2), File.ReadAllBytes(reloaded.PathOf(reloaded.Skins[1])));
        Assert.Equal(Path.Combine(_root, "skins", alex.Id + ".png"), reloaded.PathOf(reloaded.Skins[1]));
        Assert.True(File.Exists(Path.Combine(_root, "skins.json")));
        Assert.Equal(new SkinCombo(reloaded.Combos[0].Id, "Weekend", alex.Id, "classic", "cape-1", "Migrator"), reloaded.Combos[0]);
        Assert.Null(reloaded.Combos[1].CapeId);
        Assert.Null(reloaded.Combos[1].CapeName);
    }

    [Theory]
    [InlineData(64, 16)]
    [InlineData(128, 128)]
    [InlineData(32, 64)]
    public void OnlySkinSizedPngsAreAccepted(int width, int height)
    {
        var library = new SkinLibrary(_root);
        var error = Assert.Throws<InvalidDataException>(() => library.Add(Png(width, height), "x", "classic"));
        Assert.Contains($"{width}x{height}", error.Message);
        Assert.Throws<InvalidDataException>(() => library.Add("GIF89a not a png at all, long enough"u8.ToArray(), "x", "classic"));
        Assert.Empty(library.Skins);
        Assert.False(Directory.Exists(Path.Combine(_root, "skins")) && Directory.EnumerateFiles(Path.Combine(_root, "skins")).Any());
    }

    [Fact]
    public void RemovingASkinDeletesItsFileAndTheCombosUsingIt()
    {
        var library = new SkinLibrary(_root);
        var keep = library.Add(Png(64, 64), "Keep", "classic");
        var gone = library.Add(Png(64, 64), "Gone", "classic");
        library.AddCombo("uses gone", gone, "classic", null, null);
        library.AddCombo("uses keep", keep, "classic", null, null);
        library.Remove(gone.Id);
        Assert.False(File.Exists(library.PathOf(gone)));
        Assert.Equal("uses keep", Assert.Single(new SkinLibrary(_root).Combos).Name);
    }

    [Fact]
    public void HandEditedIdsCannotPointOutsideTheSkinsFolderAndMissingFilesAreDropped()
    {
        var library = new SkinLibrary(_root);
        var real = library.Add(Png(64, 64), "Real", "classic");
        var missing = library.Add(Png(64, 64), "Missing", "classic");
        File.Delete(library.PathOf(missing));
        string json = File.ReadAllText(Path.Combine(_root, "skins.json"));
        File.WriteAllText(Path.Combine(_root, "skins.json"), json.Replace("\"Skins\": [", "\"Skins\": [{\"Id\":\"..\\\\..\\\\escape\",\"Name\":\"Bad\",\"Model\":\"classic\",\"AddedUtc\":\"2026-01-01T00:00:00Z\"},"));
        Assert.Equal(real.Id, Assert.Single(new SkinLibrary(_root).Skins).Id);
    }

    [Fact]
    public void AnUnreadableIndexIsKeptAsBadInsteadOfOverwritten()
    {
        File.WriteAllText(Path.Combine(_root, "skins.json"), "{ not json");
        var library = new SkinLibrary(_root);
        Assert.Empty(library.Skins);
        Assert.Equal("{ not json", File.ReadAllText(Path.Combine(_root, "skins.json.bad")));
    }

    [Fact]
    public void OldEditorPresetsAreImportedOnce()
    {
        string presets = Path.Combine(_root, "presets", "Lad");
        Directory.CreateDirectory(presets);
        File.WriteAllBytes(Path.Combine(presets, "Preset_1.png"), Png(64, 64));
        File.WriteAllBytes(Path.Combine(presets, "broken.png"), Png(10, 10));
        Assert.Equal("Preset_1", Assert.Single(new SkinLibrary(_root).Skins).Name);
        Assert.Single(new SkinLibrary(_root).Skins); // skins.json now exists: no second import
    }
}
