using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ServerStatusServiceTests
{
    private static byte[] CreatePngHeader(int width, int height)
    {
        byte[] bytes = new byte[24];
        byte[] signature = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];
        signature.CopyTo(bytes, 0);
        bytes[8] = 0; bytes[9] = 0; bytes[10] = 0; bytes[11] = 13;
        bytes[12] = (byte)'I'; bytes[13] = (byte)'H'; bytes[14] = (byte)'D'; bytes[15] = (byte)'R';
        bytes[16] = (byte)(width >> 24);
        bytes[17] = (byte)(width >> 16);
        bytes[18] = (byte)(width >> 8);
        bytes[19] = (byte)width;
        bytes[20] = (byte)(height >> 24);
        bytes[21] = (byte)(height >> 16);
        bytes[22] = (byte)(height >> 8);
        bytes[23] = (byte)height;
        return bytes;
    }

    [Fact]
    public void IsValidFaviconPng_Accepts64x64Png()
    {
        var validPng = CreatePngHeader(64, 64);
        Assert.True(ServerStatusService.IsValidFaviconPng(validPng));
    }

    [Fact]
    public void IsValidFaviconPng_RejectsNon64Dimensions()
    {
        Assert.False(ServerStatusService.IsValidFaviconPng(CreatePngHeader(128, 128)));
        Assert.False(ServerStatusService.IsValidFaviconPng(CreatePngHeader(32, 32)));
        Assert.False(ServerStatusService.IsValidFaviconPng(CreatePngHeader(64, 32)));
    }

    [Fact]
    public void IsValidFaviconPng_RejectsInvalidHeaders()
    {
        Assert.False(ServerStatusService.IsValidFaviconPng(new byte[] { 1, 2, 3 }));
        Assert.False(ServerStatusService.IsValidFaviconPng(new byte[24]));
    }

    [Fact]
    public void Parse_ParsesBase64FaviconBytes()
    {
        string base64 = Convert.ToBase64String(CreatePngHeader(64, 64));
        string json = "{\"online\": true, \"icon\": \"data:image/png;base64," + base64 + "\", \"players\": {\"online\": 5, \"max\": 20}}";
        var status = ServerStatusService.Parse(json);
        Assert.True(status.Online);
        Assert.NotNull(status.Icon);
        Assert.True(ServerStatusService.IsValidFaviconPng(status.Icon));
    }
}
