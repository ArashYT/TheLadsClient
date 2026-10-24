using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class DeepLinkServiceTests
{
    [Theory]
    [InlineData("lads://join/play.thelads.net", "play.thelads.net", 25565)]
    [InlineData("lads://connect/hypixel.net:25566", "hypixel.net", 25566)]
    [InlineData("lads://server/mc.hypixel.net", "mc.hypixel.net", 25565)]
    public void ParseUri_ServerJoin_ParsedCorrectly(string uri, string expectedHost, int expectedPort)
    {
        var action = DeepLinkService.ParseUri(uri);

        Assert.Equal(DeepLinkActionType.JoinServer, action.Type);
        Assert.Equal(expectedHost, action.Target);
        Assert.Equal(expectedPort, action.Port);
    }

    [Theory]
    [InlineData("lads://launch/ZeroSMP", "ZeroSMP")]
    [InlineData("lads://profile/26.3%20OptiFine", "26.3 OptiFine")]
    [InlineData("lads://play/Forge189", "Forge189")]
    public void ParseUri_LaunchProfile_ParsedCorrectly(string uri, string expectedProfile)
    {
        var action = DeepLinkService.ParseUri(uri);

        Assert.Equal(DeepLinkActionType.LaunchProfile, action.Type);
        Assert.Equal(expectedProfile, action.Target);
    }

    [Theory]
    [InlineData("https://example.com")]
    [InlineData("lads://unknown/something")]
    [InlineData("")]
    [InlineData(null)]
    public void ParseUri_InvalidOrUnknown_ReturnsNone(string? uri)
    {
        var action = DeepLinkService.ParseUri(uri);

        Assert.Equal(DeepLinkActionType.None, action.Type);
    }
}
