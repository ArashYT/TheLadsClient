using System;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class AutoRelaunchPolicyTests
{
    [Fact]
    public void ShouldRelaunch_RefusesWhenCancelled()
    {
        var policy = new AutoRelaunchPolicy();
        bool allowed = policy.ShouldRelaunch(TimeSpan.FromSeconds(60), wasCancelled: true, DateTimeOffset.UtcNow, out var reason);

        Assert.False(allowed);
        Assert.Contains("cancelled", reason, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void ShouldRelaunch_RefusesWhenUptimeTooShort()
    {
        var policy = new AutoRelaunchPolicy();
        bool allowed = policy.ShouldRelaunch(TimeSpan.FromSeconds(5), wasCancelled: false, DateTimeOffset.UtcNow, out var reason);

        Assert.False(allowed);
        Assert.Contains("too quickly", reason, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void ShouldRelaunch_AllowsUpToLimitThenPauses()
    {
        var policy = new AutoRelaunchPolicy();
        var now = DateTimeOffset.UtcNow;

        // Attempt 1: allowed
        bool attempt1 = policy.ShouldRelaunch(TimeSpan.FromSeconds(30), wasCancelled: false, now, out var reason1);
        Assert.True(attempt1);
        Assert.Contains("attempt 1", reason1, StringComparison.OrdinalIgnoreCase);

        // Attempt 2: allowed
        bool attempt2 = policy.ShouldRelaunch(TimeSpan.FromSeconds(45), wasCancelled: false, now.AddMinutes(1), out var reason2);
        Assert.True(attempt2);
        Assert.Contains("attempt 2", reason2, StringComparison.OrdinalIgnoreCase);

        // Attempt 3: paused (within 10 min)
        bool attempt3 = policy.ShouldRelaunch(TimeSpan.FromSeconds(45), wasCancelled: false, now.AddMinutes(2), out var reason3);
        Assert.False(attempt3);
        Assert.Contains("reached limit", reason3, StringComparison.OrdinalIgnoreCase);

        // After window expires (11 minutes later), allowed again
        bool attempt4 = policy.ShouldRelaunch(TimeSpan.FromSeconds(45), wasCancelled: false, now.AddMinutes(12), out var reason4);
        Assert.True(attempt4);
    }

    [Fact]
    public void ShouldSendCrashReport_RateLimitsReportsWithinWindow()
    {
        var policy = new AutoRelaunchPolicy();
        var now = DateTimeOffset.UtcNow;

        // First report: allowed
        Assert.True(policy.ShouldSendCrashReport(now));

        // Rapid crash within window: suppressed
        Assert.False(policy.ShouldSendCrashReport(now.AddSeconds(30)));
        Assert.False(policy.ShouldSendCrashReport(now.AddMinutes(5)));

        // After 10 min: allowed
        Assert.True(policy.ShouldSendCrashReport(now.AddMinutes(11)));
    }
}
