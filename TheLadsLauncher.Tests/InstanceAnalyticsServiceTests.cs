using System;
using System.IO;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class InstanceAnalyticsServiceTests
{
    [Fact]
    public void RecordLaunch_IncrementsLaunchCount()
    {
        using var dir = new TestDirectory();
        string instId = "test-instance";

        InstanceAnalyticsService.RecordLaunch(dir.Path, instId);
        var stats = InstanceAnalyticsService.GetAnalytics(dir.Path, instId);

        Assert.Equal(1, stats.LaunchCount);
        Assert.NotNull(stats.LastPlayedUtc);

        InstanceAnalyticsService.RecordLaunch(dir.Path, instId);
        stats = InstanceAnalyticsService.GetAnalytics(dir.Path, instId);
        Assert.Equal(2, stats.LaunchCount);
    }

    [Fact]
    public void RecordSessionExit_AccumulatesPlaytime()
    {
        using var dir = new TestDirectory();
        string instId = "test-instance-playtime";

        InstanceAnalyticsService.RecordSessionExit(dir.Path, instId, TimeSpan.FromMinutes(25));
        var stats = InstanceAnalyticsService.GetAnalytics(dir.Path, instId);

        Assert.Equal(1500, stats.TotalPlaySeconds);
        Assert.Equal(1500, stats.LastSessionDurationSeconds);

        InstanceAnalyticsService.RecordSessionExit(dir.Path, instId, TimeSpan.FromMinutes(10));
        stats = InstanceAnalyticsService.GetAnalytics(dir.Path, instId);
        Assert.Equal(2100, stats.TotalPlaySeconds);
    }

    [Theory]
    [InlineData(45, "45s")]
    [InlineData(120, "2m")]
    [InlineData(3665, "1h 1m")]
    [InlineData(7200, "2h 0m")]
    public void FormatPlayTime_FormatsReadableString(long seconds, string expected)
    {
        string formatted = InstanceAnalyticsService.FormatPlayTime(seconds);
        Assert.Equal(expected, formatted);
    }
}
