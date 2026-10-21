using System;
using System.IO;

namespace TheLadsLauncher.Services;

/// <summary>
/// Auto-detects Minecraft player skin model variant: "classic" (Steve 4 px arms) vs "slim" (Alex 3 px arms).
/// </summary>
public static class SkinDetector
{
    public delegate uint PixelAccessor(int x, int y);

    /// <summary>
    /// Checks whether a skin uses the slim (3 px arm) model.
    /// Legacy skins (64x32 or 2:1 aspect ratio) always return false (classic).
    /// For square skins (64x64 or HD scaled), examines the 8 check regions corresponding
    /// to the 4th column of right and left arms (base layer and overlay).
    /// If ANY pixel in ANY of the 8 regions has alpha >= 128, the skin is classic.
    /// If ALL pixels across all 8 regions are transparent (alpha &lt; 128), the skin is slim.
    /// </summary>
    public static bool IsSlim(PixelAccessor accessor, int width, int height)
    {
        if (width <= 0 || height <= 0) return false;
        // Legacy skins (64x32 or 2:1 aspect ratio) never have slim arms
        if (height * 2 == width || height != width) return false;

        int scale = width / 64;
        if (scale <= 0) return false;

        // The 8 regions corresponding to the 4th arm column on 64x64 skins:
        // 1. Right arm base top/bottom:   x in [50, 52), y in [16, 20)
        // 2. Right arm base faces:        x in [54, 56), y in [20, 32)
        // 3. Right arm overlay top/bottom:x in [50, 52), y in [32, 36)
        // 4. Right arm overlay faces:     x in [54, 56), y in [36, 48)
        // 5. Left arm base top/bottom:    x in [42, 44), y in [48, 52)
        // 6. Left arm base faces:         x in [46, 48), y in [52, 64)
        // 7. Left arm overlay top/bottom: x in [58, 60), y in [48, 52)
        // 8. Left arm overlay faces:      x in [62, 64), y in [52, 64)
        int[][] regions = [
            [50, 16, 52, 20],
            [54, 20, 56, 32],
            [50, 32, 52, 36],
            [54, 36, 56, 48],
            [42, 48, 44, 52],
            [46, 52, 48, 64],
            [58, 48, 60, 52],
            [62, 52, 64, 64]
        ];

        foreach (var r in regions)
        {
            int x1 = r[0] * scale;
            int y1 = r[1] * scale;
            int x2 = r[2] * scale;
            int y2 = r[3] * scale;

            for (int y = y1; y < y2; y++)
            {
                for (int x = x1; x < x2; x++)
                {
                    uint pixel = accessor(x, y);
                    uint alpha = (pixel >> 24) & 0xFF;
                    if (alpha >= 128)
                    {
                        // Found an opaque pixel in the 4th arm column -> Classic
                        return false;
                    }
                }
            }
        }

        // All checked 4th column pixels are transparent -> Slim
        return true;
    }

    public static bool IsSlim(uint[] pixels, int width, int height)
    {
        return IsSlim((x, y) => pixels[y * width + x], width, height);
    }

    public static bool IsSlim(SkinImage image)
    {
        return IsSlim((x, y) => image[x, y], image.Width, image.Height);
    }

    public static bool IsSlim(System.Drawing.Bitmap bitmap)
    {
        return IsSlim((x, y) => (uint)bitmap.GetPixel(x, y).ToArgb(), bitmap.Width, bitmap.Height);
    }

    public static bool IsSlim(byte[] png, bool fallbackSlim = false)
    {
        try
        {
            using var ms = new MemoryStream(png);
            using var bmp = new System.Drawing.Bitmap(ms);
            return IsSlim(bmp);
        }
        catch
        {
            return fallbackSlim;
        }
    }

    public static string DetectModel(byte[] png, string fallback = "classic")
    {
        bool fallbackSlim = string.Equals(fallback, "slim", StringComparison.OrdinalIgnoreCase);
        return IsSlim(png, fallbackSlim) ? "slim" : "classic";
    }

    public static string DetectModel(SkinImage image)
    {
        return IsSlim(image) ? "slim" : "classic";
    }

    public static string DetectModel(uint[] pixels, int width, int height)
    {
        return IsSlim(pixels, width, height) ? "slim" : "classic";
    }
}
