using System;
using System.IO;
using System.Text.Json;
using CmlLib.Core.Auth;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class HorizonEngineTests : IDisposable
{
    private readonly string _home = Path.Combine(Path.GetTempPath(), "thelads_horizon_test_" + Guid.NewGuid().ToString("N"));

    public void Dispose()
    {
        if (Directory.Exists(_home)) Directory.Delete(_home, true);
    }

    [Fact]
    public void OnlyFor263()
    {
        Assert.True(HorizonEngine.Applies("26.3"));
        Assert.False(HorizonEngine.Applies("26.2"));
        Assert.False(HorizonEngine.Applies("1.8.9"));
        Assert.False(HorizonEngine.Applies(null));
        Assert.False(new LauncherSettings().UseHorizon);
    }

    [Fact]
    public void SessionFile_HasHorizonsFields_AndReplacesALeftoverOne()
    {
        var session = new MSession("Steve", "access-token-1", "0123456789abcdef0123456789abcdef") { Xuid = "2535" };
        string first = HorizonEngine.WriteSessionFile(_home, session);
        string second = HorizonEngine.WriteSessionFile(_home, session);

        Assert.False(File.Exists(first));
        using var doc = JsonDocument.Parse(File.ReadAllText(second));
        var root = doc.RootElement;
        Assert.Equal("Steve", root.GetProperty("name").GetString());
        Assert.Equal("0123456789abcdef0123456789abcdef", root.GetProperty("uuid").GetString());
        Assert.Equal("access-token-1", root.GetProperty("access_token").GetString());
        Assert.Equal("msa", root.GetProperty("user_type").GetString());
        Assert.Equal("2535", root.GetProperty("xuid").GetString());
    }
}
