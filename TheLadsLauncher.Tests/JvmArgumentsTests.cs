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
}
