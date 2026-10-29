using System.Linq;
using CmlLib.Core.ProcessBuilder;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class JvmArgumentsTests
{
    [Fact]
    public void BuildOptimizedJvmArguments_IncludesStringDeduplicationAndOmitsAlwaysPreTouch()
    {
        var flags = LaunchService.BuildOptimizedJvmArguments(21, null, "1.20.1");

        Assert.DoesNotContain("-XX:+AlwaysPreTouch", flags);
        Assert.Contains("-XX:+UseStringDeduplication", flags);
    }

    [Fact]
    public void BuildOptimizedJvmArguments_IncludesDeduplicationAndOmitsPreTouchOnAllJavaVersions()
    {
        var flags17 = LaunchService.BuildOptimizedJvmArguments(17, null, "1.20.1");
        var flags25 = LaunchService.BuildOptimizedJvmArguments(25, null, "26.3");
        var flags8 = LaunchService.BuildOptimizedJvmArguments(8, null, "1.8.9");

        Assert.DoesNotContain("-XX:+AlwaysPreTouch", flags17);
        Assert.Contains("-XX:+UseStringDeduplication", flags17);
        Assert.DoesNotContain("-XX:+AlwaysPreTouch", flags25);
        Assert.Contains("-XX:+UseStringDeduplication", flags25);
        Assert.DoesNotContain("-XX:+AlwaysPreTouch", flags8);
        Assert.Contains("-XX:+UseStringDeduplication", flags8);
    }

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
}
