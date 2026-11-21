using System;
using System.Collections.Generic;
using System.IO;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class LastPlayedTests
{
    private static readonly DateTime Now = new(2026, 11, 21, 12, 0, 0, DateTimeKind.Utc);

    [Theory]
    [InlineData(0, "just now")]
    [InlineData(59, "just now")]
    [InlineData(60, "1 min ago")]
    [InlineData(59 * 60, "59 min ago")]
    [InlineData(3600, "1 h ago")]
    [InlineData(2 * 3600 + 120, "2 h ago")]
    [InlineData(23 * 3600, "23 h ago")]
    [InlineData(86400, "1 d ago")]
    [InlineData(6 * 86400, "6 d ago")]
    [InlineData(-300, "just now")]
    public void RelativeFormatsElapsedTime(int secondsAgo, string expected) =>
        Assert.Equal(expected, LastPlayed.Relative(Now.AddSeconds(-secondsAgo), Now));

    [Fact]
    public void RelativeFallsBackToADateAfterAWeek() =>
        Assert.Equal(Now.AddDays(-30).ToLocalTime().ToString("MMM d, yyyy", System.Globalization.CultureInfo.InvariantCulture),
            LastPlayed.Relative(Now.AddDays(-30), Now));

    [Fact]
    public void HeroTextIsHiddenWhenNeverPlayed()
    {
        Assert.Null(LastPlayed.HeroText(null, "a", Now));
        Assert.Null(LastPlayed.HeroText(new Dictionary<string, DateTime>(), "a", Now));
        Assert.Null(LastPlayed.HeroText(new Dictionary<string, DateTime> { ["b"] = Now }, "a", Now));
        Assert.Null(LastPlayed.HeroText(new Dictionary<string, DateTime> { ["a"] = Now }, null, Now));
        Assert.Equal("Last played 2 h ago", LastPlayed.HeroText(new Dictionary<string, DateTime> { ["a"] = Now.AddHours(-2) }, "a", Now));
    }

    [Fact]
    public void ExperienceLineAndButtonLabel()
    {
        Assert.Equal("Not installed", LastPlayed.ExperienceLine(false, false, false, null, null));
        Assert.Equal("Installed", LastPlayed.ExperienceLine(true, false, false, null, null));
        Assert.Equal("Installed · v1.2", LastPlayed.ExperienceLine(true, false, false, "1.2", null));
        Assert.Equal("Running", LastPlayed.ExperienceLine(true, true, false, "1.2", null));
        Assert.Equal("Downloading 40%", LastPlayed.ExperienceLine(true, false, true, "1.2", "Downloading 40%"));
        Assert.Equal("Play", LastPlayed.ButtonLabel(true, true, "x"));
        Assert.Equal("Install", LastPlayed.ButtonLabel(false, true, "x"));
        Assert.Equal("Running", LastPlayed.ButtonLabel(true, false, "Running"));
    }

    [Fact]
    public void SettingsRoundTripKeepsLastPlayedAndOldFilesLoad()
    {
        string dir = Path.Combine(Path.GetTempPath(), "lp-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(dir);
        try
        {
            string path = Path.Combine(dir, "settings.json");
            var s = new LauncherSettings();
            s.ProfileLastPlayedUtc["p1"] = Now;
            s.SaveTo(path);
            Assert.Equal(Now, LauncherSettings.LoadFrom(path).ProfileLastPlayedUtc["p1"].ToUniversalTime());

            File.WriteAllText(path, "{}");
            Assert.Empty(LauncherSettings.LoadFrom(path).ProfileLastPlayedUtc);
            File.WriteAllText(path, "{\"ProfileLastPlayedUtc\": null}");
            Assert.Empty(LauncherSettings.LoadFrom(path).ProfileLastPlayedUtc);
        }
        finally { Directory.Delete(dir, true); }
    }
}
