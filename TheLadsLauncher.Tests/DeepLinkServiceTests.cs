using System.IO.Pipes;
using System.Security.AccessControl;
using System.Security.Principal;
using TheLadsLauncher.Models;
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

    [Theory]
    [InlineData("lads://launch/Bad%0AName")]          // a control character (would forge a log line)
    [InlineData("lads://profile/%20%20")]             // blank
    [InlineData("lads://play/")]                      // none
    public void ParseUri_LaunchProfile_RefusesInvalidNames(string uri)
    {
        var action = DeepLinkService.ParseUri(uri);

        Assert.Equal(DeepLinkActionType.None, action.Type);
        Assert.Null(action.Target);
    }

    [Fact]
    public void ParseUri_RefusesOverlongNamesAndLinks()
    {
        Assert.Equal(DeepLinkActionType.LaunchProfile, DeepLinkService.ParseUri("lads://launch/" + new string('a', 100)).Type);
        Assert.Equal(DeepLinkActionType.None, DeepLinkService.ParseUri("lads://launch/" + new string('a', 101)).Type);
        Assert.Equal(DeepLinkActionType.None, DeepLinkService.ParseUri("lads://steal-data/" + new string('a', 3000)).Type);
    }

    [Fact]
    public void ProfileSwitchRefusal_UsesTheVersionPickersChecks()
    {
        static LauncherProfile Profile(string version, string? fabric = null) => new() { Name = "P", MinecraftVersion = version, FabricVersion = fabric };

        Assert.Null(DeepLinkService.ProfileSwitchRefusal(Profile("26.3", "0.19.5")));
        Assert.Null(DeepLinkService.ProfileSwitchRefusal(Profile("1.8.9")));
        Assert.Equal(GameVersionPolicy.PausedMessage, DeepLinkService.ProfileSwitchRefusal(Profile("26.2")));
        Assert.Equal(GameVersionPolicy.ComingNeverMessage, DeepLinkService.ProfileSwitchRefusal(Profile("1.7.10")));
        Assert.Contains("not offered", DeepLinkService.ProfileSwitchRefusal(Profile("1.21.11")));
        // A profile LoadProfiles keeps for repair: switching to it threw in ApplyProfile and closed the launcher.
        Assert.Contains("does not match", DeepLinkService.ProfileSwitchRefusal(Profile("26.3", "fabric-loader-0.19.5-1.20.1")));
    }

    [Fact]
    public async Task IpcPipe_IsPerUserAndOnlyTheCurrentUserCanOpenIt()
    {
        using var identity = WindowsIdentity.GetCurrent();
        Assert.EndsWith(identity.User!.Value, DeepLinkService.PipeName); // another Windows user's launcher has its own pipe

        string name = "lads-test-" + Guid.NewGuid().ToString("N");
        using var server = DeepLinkService.CreateIpcServer(name);
        var rules = server.GetAccessControl().GetAccessRules(true, true, typeof(SecurityIdentifier)).Cast<PipeAccessRule>().ToList();
        var rule = Assert.Single(rules);
        Assert.Equal(identity.Owner, rule.IdentityReference);
        Assert.Equal(AccessControlType.Allow, rule.AccessControlType);

        var received = Task.Run(async () =>
        {
            await server.WaitForConnectionAsync();
            using var reader = new StreamReader(server, leaveOpen: true);
            return await reader.ReadLineAsync();
        });
        Assert.True(await DeepLinkService.TryForwardToRunningInstanceAsync("lads://join/play.thelads.net", name));
        Assert.Equal("lads://join/play.thelads.net", await received.WaitAsync(TimeSpan.FromSeconds(10)));
        Assert.False(await DeepLinkService.TryForwardToRunningInstanceAsync("lads://join/x", "lads-test-" + Guid.NewGuid().ToString("N")));
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
