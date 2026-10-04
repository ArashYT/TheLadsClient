using System.IO;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class DiscordPresenceTests
{
    [Fact]
    public async Task FramesRoundTripLittleEndianWithTheLadsButton()
    {
        using var pipe = new MemoryStream();
        await DiscordPresence.WriteAsync(pipe, 1, new JsonObject { ["cmd"] = "SET_ACTIVITY", ["args"] = new JsonObject { ["activity"] = DiscordPresence.Activity("In the launcher", "Browsing mods") } });
        Assert.Equal(new byte[] { 1, 0, 0, 0 }, pipe.ToArray()[..4]);
        pipe.Position = 0;
        var (op, json) = await DiscordPresence.ReadAsync(pipe);
        Assert.Equal(1, op);
        var activity = json["args"]!["activity"]!;
        Assert.Equal("Browsing mods", (string?)activity["state"]);
        Assert.True((long)activity["timestamps"]!["start"]! > 1_700_000_000_000);
        Assert.Equal(DiscordPresence.Site, (string?)activity["buttons"]![0]!["url"]);
    }

    [Theory]
    [InlineData("Home"), InlineData("Worlds"), InlineData("Servers"), InlineData("Modpacks"), InlineData("Profiles"), InlineData("Accounts"),
     InlineData("Skins"), InlineData("Settings"), InlineData("Mods"), InlineData("BrowseMods"), InlineData("ModSettings"), InlineData("Packs"),
     InlineData("Shaders"), InlineData("DataPacks"), InlineData("Files"), InlineData("Gallery"), InlineData("Logs")]
    public void EveryLauncherPageHasItsOwnLabel(string page) => Assert.NotEqual("In the launcher", DiscordPresence.PageLabel(page));
}
