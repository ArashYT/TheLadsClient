using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class DeepLinkServiceTests
{
    [Fact]
    public void ParseUri_ServerJoin_ValidDnsAndPort_ParsedCorrectly()
    {
        var action = DeepLinkService.ParseUri("lads://join/play.thelads.net:25565");

        Assert.Equal(DeepLinkActionType.JoinServer, action.Type);
        Assert.Equal("play.thelads.net", action.Target);
        Assert.Equal(25565, action.Port);
    }

    [Fact]
    public void ParseUri_ServerJoin_ValidIPv4_DefaultPort()
    {
        var action = DeepLinkService.ParseUri("lads://connect/192.168.1.100");

        Assert.Equal(DeepLinkActionType.JoinServer, action.Type);
        Assert.Equal("192.168.1.100", action.Target);
        Assert.Equal(25565, action.Port);
    }

    [Fact]
    public void ParseUri_ServerJoin_RejectsInvalidHostOrPort()
    {
        var actionBadHost = DeepLinkService.ParseUri("lads://join/invalid host with spaces;cmd");
        var actionBadPort = DeepLinkService.ParseUri("lads://join/play.thelads.net:999999");
        var actionNegativePort = DeepLinkService.ParseUri("lads://join/play.thelads.net:-5");

        Assert.Equal(DeepLinkActionType.None, actionBadHost.Type);
        Assert.Equal(DeepLinkActionType.None, actionBadPort.Type);
        Assert.Equal(DeepLinkActionType.None, actionNegativePort.Type);
    }

    [Fact]
    public void ParseUri_LaunchProfile_ParsedCorrectly()
    {
        var action = DeepLinkService.ParseUri("lads://launch/Hardcore-26.3");

        Assert.Equal(DeepLinkActionType.LaunchProfile, action.Type);
        Assert.Equal("Hardcore-26.3", action.Target);
    }

    [Fact]
    public void ParseUri_UnknownAction_ReturnsUnknownType()
    {
        var action = DeepLinkService.ParseUri("lads://steal-data/payload");

        Assert.Equal(DeepLinkActionType.Unknown, action.Type);
        Assert.Equal("steal-data", action.Target);
    }

    [Fact]
    public void ParseUri_InvalidScheme_ReturnsNone()
    {
        var action = DeepLinkService.ParseUri("http://play.thelads.net");

        Assert.Equal(DeepLinkActionType.None, action.Type);
        Assert.Null(action.Target);
    }

    [Theory]
    [InlineData("play.thelads.net", true)]
    [InlineData("127.0.0.1", true)]
    [InlineData("mc.hypixel.net", true)]
    [InlineData("invalid host", false)]
    [InlineData("test;rm -rf", false)]
    [InlineData("test\"inject", false)]
    [InlineData("", false)]
    [InlineData(null, false)]
    public void IsValidHost_ValidatesProperly(string? host, bool expected)
    {
        Assert.Equal(expected, DeepLinkService.IsValidHost(host));
    }

    [Theory]
    [InlineData(25565, true)]
    [InlineData(1, true)]
    [InlineData(65535, true)]
    [InlineData(0, false)]
    [InlineData(-1, false)]
    [InlineData(65536, false)]
    public void IsValidPort_ValidatesRange(int port, bool expected)
    {
        Assert.Equal(expected, DeepLinkService.IsValidPort(port));
    }

    [Fact]
    public void OnlyAnInstalledLauncherOwnsLinks_AndRepairsAKeyPointingElsewhere()
    {
        string root = Path.Combine(Path.GetTempPath(), "lads-deeplink-" + Guid.NewGuid().ToString("N"));
        string current = Directory.CreateDirectory(Path.Combine(root, "current")).FullName;
        try
        {
            string exe = Path.Combine(current, "TheLadsLauncher.exe"), stub = Path.Combine(root, "The Lads Client.exe");
            File.WriteAllText(exe, ""); File.WriteAllText(stub, "");
            Assert.Null(DeepLinkService.InstalledStub(exe)); // no Update.exe: a dev/QA build
            File.WriteAllText(Path.Combine(root, "Update.exe"), "");
            Assert.Equal(stub, DeepLinkService.InstalledStub(exe));
            File.WriteAllText(Path.Combine(root, ".portable"), "");
            Assert.Null(DeepLinkService.InstalledStub(exe)); // portable
            Assert.Null(DeepLinkService.InstalledStub(@"C:\dev\bin\Debug\TheLadsLauncher.exe"));

            Assert.True(DeepLinkService.NeedsRegistration(null, stub));
            Assert.True(DeepLinkService.NeedsRegistration("\"C:\\Temp\\wqa\\TheLadsLauncher.exe\" \"%1\"", stub));
            Assert.False(DeepLinkService.NeedsRegistration("\"" + stub + "\" \"%1\"", stub));
        }
        finally { Directory.Delete(root, true); }
    }
}
