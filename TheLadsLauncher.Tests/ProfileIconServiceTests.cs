using System;
using System.IO;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ProfileIconServiceTests
{
    [Fact]
    public void SetSymbol_ConfiguresProfileIconAndColors()
    {
        var profile = new LauncherProfile();

        ProfileIconService.SetSymbol(profile, "sword", "#FF0000", "#990000");

        Assert.Equal("sword", profile.IconKey);
        Assert.Equal("#FF0000", profile.IconColor);
        Assert.Equal("#990000", profile.IconSecondaryColor);
        Assert.Null(profile.CustomIconPath);
    }

    [Fact]
    public void Randomize_AssignsKnownSymbolAndPresetColors()
    {
        var profile = new LauncherProfile();

        ProfileIconService.Randomize(profile);

        Assert.Contains(profile.IconKey, ProfileIconService.BuiltInSymbols);
        Assert.NotNull(profile.IconColor);
        Assert.NotNull(profile.IconSecondaryColor);
    }

    [Fact]
    public void SetCustomImage_ValidatesImageFile()
    {
        using var dir = new TestDirectory();
        string imgPath = Path.Combine(dir.Path, "custom_icon.png");
        File.WriteAllBytes(imgPath, new byte[] { 1, 2, 3 });

        var profile = new LauncherProfile();
        ProfileIconService.SetCustomImage(profile, imgPath);

        Assert.Equal("custom", profile.IconKey);
        Assert.Equal(imgPath, profile.CustomIconPath);
    }

    [Fact]
    public void SetCustomImage_ThrowsOnUnsupportedExtension()
    {
        using var dir = new TestDirectory();
        string badFile = Path.Combine(dir.Path, "icon.txt");
        File.WriteAllText(badFile, "text");

        var profile = new LauncherProfile();
        Assert.Throws<ArgumentException>(() => ProfileIconService.SetCustomImage(profile, badFile));
    }

    [Theory]
    [InlineData("sword", "⚔")]
    [InlineData("pickaxe", "⛏")]
    [InlineData("shield", "🛡")]
    [InlineData("heart", "❤")]
    [InlineData("unknown", "🎮")]
    public void GetGlyph_ReturnsExpectedUnicodeSymbols(string iconKey, string expected)
    {
        Assert.Equal(expected, ProfileIconService.GetGlyph(iconKey));
    }
}
