using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class SkinDetectorTests
{
    private static byte[] CreatePng(int width, int height, Action<Bitmap> draw)
    {
        using var bmp = new Bitmap(width, height, PixelFormat.Format32bppArgb);
        draw(bmp);
        using var ms = new MemoryStream();
        bmp.Save(ms, ImageFormat.Png);
        return ms.ToArray();
    }

    [Fact]
    public void Legacy64x32SkinIsAlwaysClassic()
    {
        byte[] png = CreatePng(64, 32, bmp =>
        {
            // All transparent or partially opaque
            bmp.SetPixel(10, 10, Color.FromArgb(255, 200, 100, 50));
        });

        Assert.Equal("classic", SkinDetector.DetectModel(png));
        Assert.False(SkinDetector.IsSlim((x, y) => 0, 64, 32));
        Assert.False(SkinDetector.IsSlim(new uint[64 * 32], 64, 32));
    }

    [Fact]
    public void Classic64x64SkinDetectedFromArmPixels()
    {
        byte[] png = CreatePng(64, 64, bmp =>
        {
            // Head and body
            bmp.SetPixel(0, 0, Color.FromArgb(255, 50, 50, 50));
            // Right arm 4th column base face: x=55, y=20
            bmp.SetPixel(55, 20, Color.FromArgb(255, 120, 150, 180));
        });

        Assert.Equal("classic", SkinDetector.DetectModel(png));
        Assert.False(SkinDetector.IsSlim(png));

        var pixels = new uint[64 * 64];
        pixels[20 * 64 + 55] = 0xFF789ABCu;
        Assert.False(SkinDetector.IsSlim(pixels, 64, 64));
        Assert.Equal("classic", SkinDetector.DetectModel(pixels, 64, 64));
        Assert.Equal("classic", SkinDetector.DetectModel(new SkinImage(pixels, 64, 64)));
    }

    [Fact]
    public void Slim64x64SkinDetectedWhenArm4thColumnTransparent()
    {
        byte[] png = CreatePng(64, 64, bmp =>
        {
            // Opaque head and body
            for (int x = 0; x < 32; x++)
                for (int y = 0; y < 32; y++)
                    bmp.SetPixel(x, y, Color.FromArgb(255, 80, 80, 80));

            // 3 px arm columns can be opaque: e.g. x=53, y=20 (not 4th column)
            bmp.SetPixel(53, 20, Color.FromArgb(255, 200, 100, 100));
        });

        Assert.Equal("slim", SkinDetector.DetectModel(png));
        Assert.True(SkinDetector.IsSlim(png));

        var pixels = new uint[64 * 64];
        pixels[20 * 64 + 53] = 0xFFC86464u;
        Assert.True(SkinDetector.IsSlim(pixels, 64, 64));
        Assert.Equal("slim", SkinDetector.DetectModel(pixels, 64, 64));
        Assert.Equal("slim", SkinDetector.DetectModel(new SkinImage(pixels, 64, 64)));
    }

    [Fact]
    public void ClassicSkinWithSomeTransparentPixelsRemainsClassic()
    {
        // An artist erased a few pixels in the arm, but the 4th column still contains opaque pixels
        byte[] png = CreatePng(64, 64, bmp =>
        {
            bmp.SetPixel(55, 36, Color.FromArgb(255, 255, 255, 255));
        });

        Assert.Equal("classic", SkinDetector.DetectModel(png));
    }

    [Fact]
    public void HdSkinScale2ClassicAndSlim()
    {
        byte[] classicHd = CreatePng(128, 128, bmp =>
        {
            bmp.SetPixel(55 * 2, 20 * 2, Color.FromArgb(255, 255, 255, 255));
        });
        Assert.Equal("classic", SkinDetector.DetectModel(classicHd));
        Assert.False(SkinDetector.IsSlim(classicHd));

        byte[] slimHd = CreatePng(128, 128, bmp =>
        {
            bmp.SetPixel(53 * 2, 20 * 2, Color.FromArgb(255, 255, 255, 255));
        });
        Assert.Equal("slim", SkinDetector.DetectModel(slimHd));
        Assert.True(SkinDetector.IsSlim(slimHd));
    }

    [Fact]
    public void EachOfTheEightArmRegionsIndependentlyTriggersClassic()
    {
        int[][] samples = [
            [50, 16], // Right arm base top/bottom
            [54, 20], // Right arm base faces
            [50, 32], // Right arm overlay top/bottom
            [54, 36], // Right arm overlay faces
            [42, 48], // Left arm base top/bottom
            [46, 52], // Left arm base faces
            [58, 48], // Left arm overlay top/bottom
            [62, 52]  // Left arm overlay faces
        ];

        foreach (var pt in samples)
        {
            int sampleX = pt[0], sampleY = pt[1];
            bool isSlim = SkinDetector.IsSlim((x, y) => (x == sampleX && y == sampleY) ? 0xFFFFFFFFu : 0u, 64, 64);
            Assert.False(isSlim, $"Expected point ({sampleX}, {sampleY}) to trigger classic detection");
        }
    }

    [Fact]
    public void InvalidBytesFallbackToRequestedModel()
    {
        byte[] invalid = [1, 2, 3, 4, 5];
        Assert.Equal("classic", SkinDetector.DetectModel(invalid, "classic"));
        Assert.Equal("slim", SkinDetector.DetectModel(invalid, "slim"));
    }
}
