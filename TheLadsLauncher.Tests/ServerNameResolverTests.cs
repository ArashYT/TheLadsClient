using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>The same cases as LadsCore's ServerNamesTest (common/src/test), which reads the same known_servers.json.</summary>
public class ServerNameResolverTests
{
    [Theory]
    [InlineData("mc.hypixel.net", "Hypixel")]
    [InlineData("hypixel.net", "Hypixel")]
    [InlineData("play.hypixel.net", "Hypixel")]
    [InlineData("stuck.hypixel.net", "Hypixel")]
    [InlineData("MC.Hypixel.NET:25565", "Hypixel")]
    [InlineData("  mc.hypixel.net.  ", "Hypixel")]
    [InlineData("play.cubecraft.net", "CubeCraft")]
    [InlineData("2b2t.org", "2b2t")]
    [InlineData("hub.mc-complex.com", "Complex Gaming")]
    [InlineData("play.craftrise.com.tr", "CraftRise")]
    [InlineData("minehut.com", "Minehut")]
    public void KnownServersMatchEveryAddressOfTheirDomain(string address, string name) => Assert.Equal(name, ServerNameResolver.Resolve(address));

    [Theory]
    [InlineData("play.funnyservername.com", "Funnyservername")]
    [InlineData("mc.my-cool_server.co.uk:25566", "My Cool Server")]
    [InlineData("eu.play.example.gg", "Example")]
    [InlineData("coolsmp.aternos.me", "Coolsmp")]
    [InlineData("funny.minehut.gg", "Funny")]
    [InlineData("notahypixel.net", "Notahypixel")] // a domain matches on whole labels only
    [InlineData("hypixel.net.evil.com", "Evil")]
    [InlineData("mc.hypixel.n", "Hypixel")] // being typed
    public void UnknownDomainsAreNamedAfterTheirRegistrableLabel(string address, string name) => Assert.Equal(name, ServerNameResolver.Resolve(address));

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("127.0.0.1")]
    [InlineData("192.168.1.20:25565")]
    [InlineData("192.168")]
    [InlineData("[::1]:25565")]
    [InlineData("::1")]
    [InlineData("localhost")]
    [InlineData("localhost:25565")]
    [InlineData("play.com")]
    [InlineData("mc.")]
    [InlineData("bad host.com")]
    [InlineData("a..b")]
    [InlineData("play.mc.net")]
    [InlineData("mc.hypixel")]
    public void IpsAndIncompleteAddressesGetNoName(string? address) => Assert.Null(ServerNameResolver.Resolve(address));

    [Fact]
    public void EveryKnownServerResolvesToItselfAndNamesAreUnique()
    {
        Assert.True(ServerNameResolver.Servers.Count >= 80, $"{ServerNameResolver.Servers.Count} known servers");
        Assert.Equal(ServerNameResolver.Servers.Count, ServerNameResolver.Servers.Select(s => s.Name).Distinct().Count());
        Assert.Equal(ServerNameResolver.Servers.Sum(s => s.Domains.Count), ServerNameResolver.Servers.SelectMany(s => s.Domains).Distinct().Count());
        foreach (var server in ServerNameResolver.Servers)
        {
            Assert.Equal(server.Name, ServerNameResolver.Resolve(server.Address));
            Assert.False(string.IsNullOrWhiteSpace(server.Category) || string.IsNullOrWhiteSpace(server.Description), server.Name);
        }
    }

    [Fact]
    public void DefaultNameFollowsTheAddressAndRevertsWhenItHasNone()
    {
        var auto = new ServerNameResolver.AutoName();
        var name = "Minecraft Server";
        Assert.Null(auto.Update(name, ""));
        Assert.Null(auto.Update(name, "mc.hypixel"));
        name = auto.Update(name, "mc.hypixel.n")!;
        Assert.Equal("Hypixel", name);
        Assert.Null(auto.Update(name, "mc.hypixel.n")); // the name field's own change is no address change
        Assert.Null(auto.Update(name, "mc.hypixel.net"));
        name = auto.Update(name, "play.cubecraft.net")!;
        Assert.Equal("CubeCraft", name);
        Assert.Equal("Minecraft Server", auto.Update(name, ""));
    }

    [Fact]
    public void ATypedNameIsNeverReplaced()
    {
        var auto = new ServerNameResolver.AutoName();
        Assert.Null(auto.Update("Minecraft Server", ""));
        Assert.Null(auto.Update("Lads SMP", ""));
        Assert.Null(auto.Update("Lads SMP", "mc.hypixel.net"));
        var edit = new ServerNameResolver.AutoName();
        Assert.Null(edit.Update("Hypixel", "mc.hypixel.net"));
        Assert.Null(edit.Update("Hypixel", "play.cubecraft.net"));
        var over = new ServerNameResolver.AutoName();
        Assert.Equal("Hypixel", over.Update("Minecraft Server", "hypixel.net"));
        Assert.Null(over.Update("Hypixel!", "hypixel.net"));
        Assert.Null(over.Update("Hypixel!", "play.cubecraft.net"));
    }

    [Fact]
    public void AnEmptyOrTranslatedDefaultNameIsFilledAndPutBack()
    {
        var empty = new ServerNameResolver.AutoName("Minecraft-Server");
        Assert.Equal("Funnyservername", empty.Update("", "play.funnyservername.com"));
        Assert.Equal("", empty.Update("Funnyservername", "192.168.0.2"));
        Assert.Equal("Hypixel", new ServerNameResolver.AutoName("Minecraft-Server").Update("Minecraft-Server", "mc.hypixel.net"));
        Assert.Equal("Hypixel", new ServerNameResolver.AutoName("Minecraft-Server").Update(ServerNameResolver.DefaultName, "mc.hypixel.net"));
    }
}
