using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class SkinRasterizerTests
{
    [Fact]
    public void Resize_ReusesExistingCapacityWhenShrinking()
    {
        var raster = new SkinRasterizer();
        raster.Resize(100, 100);
        Assert.Equal(100, raster.Width);
        Assert.Equal(100, raster.Height);
        Assert.True(raster.Pixels.Length >= 10000);
        int initialCap = raster.Pixels.Length;

        // Shrinking dimensions should retain buffer capacity
        raster.Resize(60, 60);
        Assert.Equal(60, raster.Width);
        Assert.Equal(60, raster.Height);
        Assert.Equal(initialCap, raster.Pixels.Length);

        // Growing within existing capacity should still not reallocate
        raster.Resize(80, 80);
        Assert.Equal(80, raster.Width);
        Assert.Equal(80, raster.Height);
        Assert.Equal(initialCap, raster.Pixels.Length);
    }

    [Fact]
    public void Resize_GrowsWithHeadroomWhenNeeded()
    {
        var raster = new SkinRasterizer();
        raster.Resize(50, 50);
        int cap1 = raster.Pixels.Length;

        raster.Resize(200, 200);
        Assert.True(raster.Pixels.Length >= 40000);
        Assert.True(raster.Pixels.Length > cap1);
    }

    [Fact]
    public void Downsample_AveragesSupersampledTexels()
    {
        var raster = new SkinRasterizer();
        raster.Resize(4, 4);
        // Fill a 2x2 block with 0xFF102030 (factor 2 downsample -> 1 pixel)
        raster.Pixels[0] = 0xFF102030;
        raster.Pixels[1] = 0xFF102030;
        raster.Pixels[4] = 0xFF102030;
        raster.Pixels[5] = 0xFF102030;

        uint[] target = new uint[4];
        raster.Downsample(target, 2);
        Assert.Equal(0xFF102030u, target[0]);
    }
}
