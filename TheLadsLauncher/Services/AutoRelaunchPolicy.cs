using System;
using System.Collections.Generic;

namespace TheLadsLauncher.Services;

/// <summary>
/// Guard against infinite crash loops and excessive crash reporting.
/// </summary>
public sealed class AutoRelaunchPolicy
{
    public const int MaxRelaunchesInWindow = 2;
    public static readonly TimeSpan TimeWindow = TimeSpan.FromMinutes(10);
    public static readonly TimeSpan MinUptimeForRelaunch = TimeSpan.FromSeconds(20);

    private readonly List<DateTimeOffset> _relaunchTimestamps = new();
    private DateTimeOffset? _lastCrashReportTime;

    public bool ShouldRelaunch(TimeSpan gameUptime, bool wasCancelled, DateTimeOffset now, out string reason)
    {
        if (wasCancelled)
        {
            reason = "Game session was cancelled by user.";
            return false;
        }

        if (gameUptime < MinUptimeForRelaunch)
        {
            reason = $"Game crashed too quickly (ran for {gameUptime.TotalSeconds:F1}s, minimum {MinUptimeForRelaunch.TotalSeconds:0}s). Loop guard stopped auto-relaunch.";
            return false;
        }

        Prune(now);

        if (_relaunchTimestamps.Count >= MaxRelaunchesInWindow)
        {
            reason = $"Auto-relaunch paused: reached limit of {MaxRelaunchesInWindow} automatic relaunches within {TimeWindow.TotalMinutes:0} minutes.";
            return false;
        }

        _relaunchTimestamps.Add(now);
        reason = $"Auto-relaunch allowed (attempt {_relaunchTimestamps.Count} of {MaxRelaunchesInWindow}).";
        return true;
    }

    public bool ShouldSendCrashReport(DateTimeOffset now)
    {
        if (_lastCrashReportTime != null && now - _lastCrashReportTime.Value < TimeWindow)
            return false;

        _lastCrashReportTime = now;
        return true;
    }

    public void Reset()
    {
        _relaunchTimestamps.Clear();
        _lastCrashReportTime = null;
    }

    private void Prune(DateTimeOffset now)
    {
        _relaunchTimestamps.RemoveAll(ts => now - ts > TimeWindow);
    }
}
