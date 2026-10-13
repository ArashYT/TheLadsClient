using CmlLib.Core.ProcessBuilder;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>
/// Unit tests for ANTIINJECT lane: ensuring -XX:+DisableAttachMechanism is passed to prevent
/// dynamic JVM attachment, and unexpected -javaagent / -agentpath / -agentlib flags are rejected.
/// </summary>
public class AntiInjectTests
{
    [Fact]
    public void EnsureSecurityJvmArguments_AddsDisableAttachMechanismAndDisableExplicitGC()
    {
        var options = new MLaunchOption();
        LaunchService.EnsureSecurityJvmArguments(options);

        var values = options.ExtraJvmArguments!.SelectMany(a => a.Values).ToList();
        Assert.Contains("-XX:+DisableAttachMechanism", values);
        Assert.Contains("-XX:+DisableExplicitGC", values);
    }

    [Fact]
    public void EnsureSecurityJvmArguments_DoesNotDuplicateIfAlreadyPresent()
    {
        var options = new MLaunchOption
        {
            ExtraJvmArguments = new[]
            {
                MArgument.FromCommandLine("-XX:+DisableAttachMechanism -XX:+DisableExplicitGC -Dsome.prop=true")
            }
        };

        LaunchService.EnsureSecurityJvmArguments(options);

        var values = options.ExtraJvmArguments!.SelectMany(a => a.Values).ToList();
        Assert.Equal(1, values.Count(v => v.Contains("DisableAttachMechanism", StringComparison.OrdinalIgnoreCase)));
        Assert.Equal(1, values.Count(v => v.Contains("DisableExplicitGC", StringComparison.OrdinalIgnoreCase)));
        Assert.Contains("-Dsome.prop=true", values);
    }

    [Theory]
    [InlineData("-javaagent:vape.jar")]
    [InlineData("-javaagent:C:\\cheats\\vape_v4.jar")]
    [InlineData("-agentpath:C:\\cheats\\inject.dll")]
    [InlineData("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")]
    [InlineData("-JAVAAGENT:payload.jar")]
    public void ValidateJvmArguments_ThrowsOnUnexpectedAgentFlags(string agentArg)
    {
        var ex = Assert.Throws<InvalidOperationException>(() =>
        {
            LaunchService.ValidateJvmArguments(agentArg);
        });

        Assert.Contains("External JVM agent", ex.Message);
        Assert.Contains("blocked for client integrity", ex.Message);
    }

    [Fact]
    public void EnsureSecurityJvmArguments_ThrowsIfOptionContainsAgent()
    {
        var options = new MLaunchOption
        {
            ExtraJvmArguments = new[]
            {
                MArgument.FromCommandLine("-Xmx4G -javaagent:injected-agent.jar")
            }
        };

        var ex = Assert.Throws<InvalidOperationException>(() =>
        {
            LaunchService.EnsureSecurityJvmArguments(options);
        });

        Assert.Contains("External JVM agent", ex.Message);
    }

    [Theory]
    [InlineData("-Xmx4G")]
    [InlineData("-Xms2G")]
    [InlineData("-XX:+UseG1GC")]
    [InlineData("-Dthelads.test=true")]
    [InlineData("-Dmax.bg.threads=4")]
    public void ValidateJvmArguments_AllowsNormalFlags(string validArg)
    {
        // Should not throw
        LaunchService.ValidateJvmArguments(validArg);
    }

    [Fact]
    public async Task ModpackLauncher_IncludesDisableAttachMechanismOnCommandLine()
    {
        var process = await ModpackJoinTests.BuildForge1201(configure: o => LaunchService.EnsureSecurityJvmArguments(o));
        Assert.Contains("-XX:+DisableAttachMechanism", process.StartInfo.Arguments);
    }
}

