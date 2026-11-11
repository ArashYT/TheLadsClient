using System.Linq;
using CmlLib.Core.ProcessBuilder;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class JvmArgumentsTests
{
    [Fact]
    public void BuildOptimizedJvmArguments_Java17Plus_FavoursLoadTime()
    {
        foreach (var flags in new[] { LaunchService.BuildOptimizedJvmArguments(21, null, "1.20.1"), LaunchService.BuildOptimizedJvmArguments(25, null, "26.3") })
        {
            Assert.DoesNotContain("-XX:+AlwaysPreTouch", flags);
            Assert.DoesNotContain("-XX:+UseStringDeduplication", flags);
            Assert.DoesNotContain("-XX:G1HeapRegionSize=32M", flags);
            Assert.DoesNotContain("-XX:G1ReservePercent=20", flags);
            Assert.Contains("-XX:G1NewSizePercent=30", flags);
            Assert.Contains("-XX:ReservedCodeCacheSize=512m", flags);
        }
    }

    [Fact]
    public void BuildOptimizedJvmArguments_Java8_KeepsBenchmarkedSet()
    {
        var flags8 = LaunchService.BuildOptimizedJvmArguments(8, null, "1.8.9");

        Assert.DoesNotContain("-XX:+AlwaysPreTouch", flags8);
        Assert.Contains("-XX:+UseStringDeduplication", flags8);
        Assert.Contains("-XX:MaxGCPauseMillis=35", flags8);
    }

    [Theory]
    [InlineData(512, 4096, 2048)]
    [InlineData(3000, 4096, 3000)]
    [InlineData(512, 1024, 1024)]
    public void StartupHeapMb_FloorsAtTwoGbNeverAboveMax(int min, int max, int expected) =>
        Assert.Equal(expected, LaunchService.StartupHeapMb(min, max));

    [Fact]
    public void EnsureLoadingScreenColors_AppendsCustomColors()
    {
        var options = new MLaunchOption();
        var settings = new LauncherSettings
        {
            LoadingScreenBgColor = "#112233",
            LoadingScreenBarColor = "#445566",
            LoadingScreenGlowColor = "#778899"
        };

        LaunchService.EnsureLoadingScreenColors(options, settings);

        var args = options.ExtraJvmArguments!.SelectMany(a => a.Values).ToList();
        Assert.Contains("-Dthelads.loadingBg=#112233", args);
        Assert.Contains("-Dthelads.loadingBar=#445566", args);
        Assert.Contains("-Dthelads.loadingGlow=#778899", args);
    }

    [Theory]
    [InlineData("#112233", true)]
    [InlineData("112233", true)]
    [InlineData("#11223344", true)]
    [InlineData("AABBCC", true)]
    [InlineData("aabbccdd", true)]
    [InlineData("dark red", false)]
    [InlineData("#111 -Xbootclasspath/a:x.jar", false)]
    [InlineData("xyz", false)]
    [InlineData("#12", false)]
    [InlineData("", false)]
    [InlineData("   ", false)]
    [InlineData(null, false)]
    public void IsValidLoadingScreenColor_ValidatesCorrectly(string? input, bool expected)
    {
        Assert.Equal(expected, LaunchService.IsValidLoadingScreenColor(input));
    }

    [Fact]
    public void EnsureLoadingScreenColors_WithInvalidValuesBlanked_OmitsInvalidArguments()
    {
        var options = new MLaunchOption();
        var settings = new LauncherSettings
        {
            LoadingScreenBgColor = "dark red",
            LoadingScreenBarColor = "#111 -Xbootclasspath/a:x.jar",
            LoadingScreenGlowColor = "#112233"
        };

        var launchSettings = settings.Clone();
        if (!LaunchService.IsValidLoadingScreenColor(launchSettings.LoadingScreenBgColor)) launchSettings.LoadingScreenBgColor = "";
        if (!LaunchService.IsValidLoadingScreenColor(launchSettings.LoadingScreenBarColor)) launchSettings.LoadingScreenBarColor = "";
        if (!LaunchService.IsValidLoadingScreenColor(launchSettings.LoadingScreenGlowColor)) launchSettings.LoadingScreenGlowColor = "";

        LaunchService.EnsureLoadingScreenColors(options, launchSettings);

        var args = options.ExtraJvmArguments!.SelectMany(a => a.Values).ToList();
        Assert.DoesNotContain(args, a => a.Contains("loadingBg"));
        Assert.DoesNotContain(args, a => a.Contains("loadingBar"));
        Assert.Contains("-Dthelads.loadingGlow=#112233", args);
    }

    [Fact]
    public void EnsureLoadingScreenColors_WithInvalidHexDirectly_OmitsInvalidArguments()
    {
        var options = new MLaunchOption();
        var settings = new LauncherSettings
        {
            LoadingScreenBgColor = "dark red",
            LoadingScreenBarColor = "#111 -Xbootclasspath/a:x.jar",
            LoadingScreenGlowColor = "#112233"
        };

        LaunchService.EnsureLoadingScreenColors(options, settings);

        var args = options.ExtraJvmArguments!.SelectMany(a => a.Values).ToList();
        Assert.DoesNotContain(args, a => a.Contains("loadingBg"));
        Assert.DoesNotContain(args, a => a.Contains("loadingBar"));
        Assert.Contains("-Dthelads.loadingGlow=#112233", args);
    }
}
