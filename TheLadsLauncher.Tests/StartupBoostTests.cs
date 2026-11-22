using System.Security.Cryptography;
using CmlLib.Core.ProcessBuilder;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class StartupBoostTests
{
    private static string ShippedJar()
    {
        for (var d = new DirectoryInfo(AppContext.BaseDirectory); d != null; d = d.Parent)
        {
            var p = Path.Combine(d.FullName, "TheLadsLauncher", "boost", "lads-boost.jar");
            if (File.Exists(p)) return p;
        }
        throw new FileNotFoundException("TheLadsLauncher/boost/lads-boost.jar");
    }

    [Fact]
    public void ShippedJar_MatchesPinnedHash()
    {
        using var s = File.OpenRead(ShippedJar());
        Assert.Equal(StartupBoost.JarSha256, Convert.ToHexString(SHA256.HashData(s)), ignoreCase: true);
    }

    [Theory]
    [InlineData("26.3", true)]
    [InlineData("1.20.1", true)]
    [InlineData("1.8.9", true)]
    [InlineData("1.21.11", false)]
    [InlineData(null, false)]
    public void Arguments_OnlyForSupportedVersions(string? version, bool expected)
    {
        var args = StartupBoost.Arguments(new LauncherSettings(), @"C:\game", version, ShippedJar(), out _);
        Assert.Equal(expected, args.Length == 2);
        if (expected)
        {
            Assert.Equal("-javaagent:" + ShippedJar(), args[0]);
            Assert.Equal(@"-Dlads.boost.dir=C:\game\.lads-boost", args[1]);
        }
    }

    [Fact]
    public void Arguments_NoneWhenOffInSettings()
    {
        var args = StartupBoost.Arguments(new LauncherSettings { StartupBoost = false }, @"C:\game", "26.3", ShippedJar(), out var why);
        Assert.Empty(args);
        Assert.Equal("off in settings", why);
    }

    [Fact]
    public void Arguments_NoneWhenJarChanged()
    {
        var copy = Path.Combine(Path.GetTempPath(), "lads-boost-test-" + Guid.NewGuid().ToString("N") + ".jar");
        File.Copy(ShippedJar(), copy);
        try
        {
            File.AppendAllText(copy, "x");
            Assert.Empty(StartupBoost.Arguments(new LauncherSettings(), @"C:\game", "26.3", copy, out var why));
            Assert.Equal("agent jar does not match its known hash", why);
            Assert.Empty(StartupBoost.Arguments(new LauncherSettings(), @"C:\game", "26.3", copy + ".missing", out why));
            Assert.Equal("agent jar missing", why);
        }
        finally { File.Delete(copy); }
    }

    /// <summary>The user's own agent is still refused; ours is only appended after that check.</summary>
    [Fact]
    public void UserAgent_StillBlocked_OursAddedAfterValidation()
    {
        var bad = new MLaunchOption { ExtraJvmArguments = new[] { MArgument.FromCommandLine("-javaagent:evil.jar") } };
        Assert.Throws<InvalidOperationException>(() => LaunchService.EnsureSecurityJvmArguments(bad));

        var ok = new MLaunchOption();
        LaunchService.EnsureSecurityJvmArguments(ok);
        var game = Path.GetTempPath();
        StartupBoost.Apply(ok, new LauncherSettings(), game, "26.3");
        var values = ok.ExtraJvmArguments!.SelectMany(a => a.Values).ToList();
        if (File.Exists(StartupBoost.JarPath))
            Assert.Contains("-javaagent:" + StartupBoost.JarPath, values);
        else
            Assert.DoesNotContain(values, v => v.StartsWith("-javaagent", StringComparison.OrdinalIgnoreCase));
    }
}
