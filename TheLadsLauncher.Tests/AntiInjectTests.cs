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

    [Theory]
    [InlineData(25, "-XX:+UseG1GC", "-XX:+UseCompactObjectHeaders", "appcds-jdk25.jsa")]
    [InlineData(21, "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=35", "appcds-jdk21.jsa")]
    [InlineData(17, "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=35", "appcds-jdk17.jsa")]
    public void EnsureSecurityJvmArguments_AddsVersionAwareGcAndAppCdsAndRespectsMinRam(int javaMajor, string expectedGc, string expectedExtra, string expectedJsa)
    {
        using var dir = new TestDirectory();
        var options = new MLaunchOption
        {
            MinimumRamMb = 512,
            MaximumRamMb = 4096
        };

        LaunchService.EnsureSecurityJvmArguments(options, javaMajor, dir.Path);

        Assert.Equal(512, options.MinimumRamMb);
        Assert.Equal(4096, options.MaximumRamMb);
        var values = options.ExtraJvmArguments!.SelectMany(a => a.Values).ToList();
        Assert.Contains("-XX:+IgnoreUnrecognizedVMOptions", values);
        Assert.Contains(expectedGc, values);
        Assert.Contains(expectedExtra, values);
        Assert.Contains("-XX:MaxGCPauseMillis=35", values);
        Assert.DoesNotContain("-XX:+UseZGC", values);
        Assert.Contains("-XX:+AutoCreateSharedArchive", values);
        Assert.Contains(values, v => v.StartsWith("-XX:SharedArchiveFile=", StringComparison.Ordinal) && v.EndsWith(expectedJsa, StringComparison.Ordinal));
        Assert.DoesNotContain("-XX:+AlwaysPreTouch", values);
        Assert.True(Directory.Exists(Path.Combine(dir.Path, "cache")));
    }

    [Fact]
    public void EnsureSecurityJvmArguments_Java8UsesTunedG1GcAndXshareAuto()
    {
        using var dir = new TestDirectory();
        var options = new MLaunchOption { MinimumRamMb = 512, MaximumRamMb = 2048 };
        LaunchService.EnsureSecurityJvmArguments(options, 8, dir.Path);

        var values = options.ExtraJvmArguments!.SelectMany(a => a.Values).ToList();
        Assert.Contains("-XX:+UseG1GC", values);
        Assert.Contains("-XX:+ParallelRefProcEnabled", values);
        Assert.Contains("-XX:MaxGCPauseMillis=35", values);
        Assert.Contains("-Xshare:auto", values);
        Assert.DoesNotContain("-XX:+AutoCreateSharedArchive", values);
    }

    [Fact]
    public void CachedNativesDirectory_ReusedWhenMarkerAndDllExist()
    {
        using var dir = new TestDirectory();
        var mcPath = new CmlLib.Core.MinecraftPath(dir.Path);
        const string versionId = "fabric-loader-0.19.5-1.20.1";
        var nativesDir = Path.Combine(mcPath.Versions, versionId, "natives");
        Directory.CreateDirectory(nativesDir);

        var opt1 = new MLaunchOption();
        LaunchService.ConfigureCachedNativesDirectory(mcPath, versionId, opt1);
        Assert.True(string.IsNullOrEmpty(opt1.NativesDirectory));

        File.WriteAllBytes(Path.Combine(nativesDir, "lwjgl.dll"), new byte[] { 1, 2, 3 });
        LaunchService.MarkCachedNativesReady(mcPath, versionId);
        Assert.True(File.Exists(Path.Combine(nativesDir, ".lads-natives-ok")));

        var opt2 = new MLaunchOption();
        LaunchService.ConfigureCachedNativesDirectory(mcPath, versionId, opt2);
        Assert.Equal(nativesDir, opt2.NativesDirectory);
    }

    [Fact]
    public void ValidateVersionJsonLibraries_VerifiesManifestAndLibraryJars()
    {
        using var dir = new TestDirectory();
        var mcPath = new CmlLib.Core.MinecraftPath(dir.Path);
        const string versionId = "fabric-loader-0.19.5-1.20.1";
        Assert.False(ModpackLauncher.ValidateVersionJsonLibraries(mcPath, versionId));

        var vDir = Path.Combine(mcPath.Versions, versionId);
        Directory.CreateDirectory(vDir);
        File.WriteAllText(Path.Combine(vDir, versionId + ".json"),
            """{"id":"fabric-loader-0.19.5-1.20.1","libraries":[{"name":"net.fabricmc:fabric-loader:0.19.5"}]}""");
        Assert.False(ModpackLauncher.ValidateVersionJsonLibraries(mcPath, versionId));

        var libJar = Path.Combine(mcPath.Library, "net", "fabricmc", "fabric-loader", "0.19.5", "fabric-loader-0.19.5.jar");
        Directory.CreateDirectory(Path.GetDirectoryName(libJar)!);
        File.WriteAllBytes(libJar, new byte[] { 0x50, 0x4B });
        Assert.True(ModpackLauncher.ValidateVersionJsonLibraries(mcPath, versionId));
    }

    [Fact]
    public void QuickLaunch_DefaultsToTrueAndPreservesExplicitFalse()
    {
        Assert.True(new LauncherSettings().QuickLaunch);
        Assert.True(System.Text.Json.JsonSerializer.Deserialize<LauncherSettings>("{}")!.QuickLaunch);
        Assert.False(System.Text.Json.JsonSerializer.Deserialize<LauncherSettings>("{\"QuickLaunch\":false}")!.QuickLaunch);
    }
}

