using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class ServerStatusServiceTests
{
    private static async Task<int> ReadVarInt(System.Net.Sockets.NetworkStream s)
    {
        int value = 0, shift = 0, b;
        do { b = s.ReadByte(); value |= (b & 0x7F) << shift; shift += 7; } while ((b & 0x80) != 0);
        return await Task.FromResult(value);
    }

    [Fact]
    public async Task SlpAsync_ReadsTheStatusOfAServerSpeakingTheProtocol()
    {
        var listener = new System.Net.Sockets.TcpListener(System.Net.IPAddress.Loopback, 0);
        listener.Start();
        int port = ((System.Net.IPEndPoint)listener.LocalEndpoint).Port, nextState = 0, requestId = -1;
        var server = Task.Run(async () =>
        {
            using var client = await listener.AcceptTcpClientAsync();
            var s = client.GetStream();
            var handshake = new byte[await ReadVarInt(s)];
            await s.ReadExactlyAsync(handshake);
            nextState = handshake[^1];
            await ReadVarInt(s);
            requestId = await ReadVarInt(s);
            var json = System.Text.Encoding.UTF8.GetBytes("{\"version\":{\"name\":\"Velocity\",\"protocol\":47},\"players\":{\"online\":200,\"max\":42069}}");
            // length, id 0, string length (one byte under 128, the two-byte VarInt path is the player count's read of `length`)
            await s.WriteAsync(new byte[] { (byte)(json.Length + 2), 0, (byte)json.Length });
            await s.WriteAsync(json);
        });

        var status = await ServerStatusService.SlpAsync("127.0.0.1", port);
        await server;
        listener.Stop();

        Assert.Equal(1, nextState);
        Assert.Equal(0, requestId);
        Assert.NotNull(status);
        Assert.True(status!.Online);
        Assert.Equal(200, status.Players);
        Assert.Equal(42069, status.MaxPlayers);
        Assert.Equal("Velocity", status.Version);
    }

    [Fact]
    public async Task SlpAsync_NoServerOrGarbage_IsNull()
    {
        var listener = new System.Net.Sockets.TcpListener(System.Net.IPAddress.Loopback, 0);
        listener.Start();
        int port = ((System.Net.IPEndPoint)listener.LocalEndpoint).Port;
        var server = Task.Run(async () =>
        {
            using var client = await listener.AcceptTcpClientAsync();
            await client.GetStream().WriteAsync(new byte[] { 3, 0, 1, (byte)'{' });
        });
        Assert.Null(await ServerStatusService.SlpAsync("127.0.0.1", port));
        await server;
        listener.Stop();
        Assert.Null(await ServerStatusService.SlpAsync("127.0.0.1", port, 500)); // nothing listening any more
    }

    /// <summary>Live: set LADS_LIVE_PING=1 to ping The Lads Network for real (tests stay offline otherwise).</summary>
    [Fact]
    public async Task SlpAsync_LiveNetworkPing()
    {
        if (Environment.GetEnvironmentVariable("LADS_LIVE_PING") != "1") return;
        var status = await ServerStatusService.SlpAsync(ServerListService.PinnedAddress);
        Assert.NotNull(status);
        Assert.True(status!.MaxPlayers > 0);
        Console.WriteLine($"LIVE {ServerListService.PinnedAddress}: {status.Players}/{status.MaxPlayers} {status.Version}");
    }

    [Fact]
    public void AutoJoinNetworkIsOffByDefault() => Assert.False(new TheLadsLauncher.LauncherSettings().AutoJoinNetwork);

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
