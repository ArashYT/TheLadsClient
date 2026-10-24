using System.Linq;
using CmlLib.Core.ProcessBuilder;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class JvmArgumentsTests
{
    [Fact]
    public void BuildOptimizedJvmArguments_IncludesAlwaysPreTouchAndStringDeduplication()
    {
        var flags = LaunchService.BuildOptimizedJvmArguments(21, null, "1.20.1");

        Assert.Contains("-XX:+AlwaysPreTouch", flags);
        Assert.Contains("-XX:+UseStringDeduplication", flags);
    }

    [Fact]
    public void BuildOptimizedJvmArguments_IncludesPreTouchAndDeduplicationOnAllJavaVersions()
    {
        var flags17 = LaunchService.BuildOptimizedJvmArguments(17, null, "1.20.1");
        var flags25 = LaunchService.BuildOptimizedJvmArguments(25, null, "26.3");
        var flags8 = LaunchService.BuildOptimizedJvmArguments(8, null, "1.8.9");

        Assert.Contains("-XX:+AlwaysPreTouch", flags17);
        Assert.Contains("-XX:+UseStringDeduplication", flags17);
        Assert.Contains("-XX:+AlwaysPreTouch", flags25);
        Assert.Contains("-XX:+UseStringDeduplication", flags25);
        Assert.Contains("-XX:+AlwaysPreTouch", flags8);
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
}
