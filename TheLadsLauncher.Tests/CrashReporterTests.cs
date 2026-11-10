using System;
using System.IO;
using TheLadsLauncher.Services;
using Xunit;

public class CrashReporterTests
{
    [Fact]
    public void ScrubRemovesUserFolderAndTokens()
    {
        string home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        string user = Path.GetFileName(home);
        string text = CrashReporter.Scrub(
            $"at {home}\\AppData\\Roaming\\.theladsclient\nfile:/C:/Users/{user}/x.jar\n--accessToken eyJhbGciOiJIUzI1.eyJzdWIiOiIxMjM0NTY3.SflKxwRJSMeKKF2QT4fw\n(Session ID is token:0123456789abcdef0123:uuid)");
        Assert.DoesNotContain(user + "\\", text);
        Assert.DoesNotContain("/" + user + "/", text);
        Assert.DoesNotContain("eyJhbGciOiJIUzI1", text);
        Assert.DoesNotContain("0123456789abcdef0123", text);
        Assert.Contains("%USERPROFILE%\\AppData", text);
        Assert.Equal("USERNAME=<hidden>\r\nOS=Windows_NT", CrashReporter.Scrub("USERNAME=steph\r\nOS=Windows_NT"));
    }

    [Fact]
    public void ReadKeepsTheEndOfALongLog()
    {
        string path = Path.GetTempFileName();
        File.WriteAllText(path, new string('a', 300_000) + "LAST");
        try
        {
            Assert.EndsWith("LAST", CrashReporter.Read(path, tail: true));
            Assert.Equal("", CrashReporter.Read(path + ".missing", tail: true));
        }
        finally { File.Delete(path); }
    }

    [Fact]
    public void CaughtReportsAreTheMarkedOnesFromThisSession()
    {
        string game = Path.Combine(Path.GetTempPath(), "lads-caught-" + Guid.NewGuid().ToString("N"));
        string dir = Directory.CreateDirectory(Path.Combine(game, "crash-reports")).FullName;
        try
        {
            var start = DateTime.Now.AddMinutes(-5);
            string Write(string name, string text, DateTime time)
            {
                string path = Path.Combine(dir, name);
                File.WriteAllText(path, text);
                File.SetLastWriteTime(path, time);
                return path;
            }
            string caught = Write("crash-new-client.txt", "---- Minecraft Crash Report ----\n-- Lads Crash Catcher --\nDetails:\n\tCaught: yes", start.AddMinutes(1));
            Write("crash-plain-client.txt", "---- Minecraft Crash Report ----\n-- Head --", start.AddMinutes(2));
            Write("crash-old-client.txt", "-- Lads Crash Catcher --", start.AddMinutes(-60));
            Assert.Equal(new[] { caught }, CrashReporter.CaughtReports(game, start));
            Assert.Empty(CrashReporter.CaughtReports(Path.Combine(game, "missing"), start));
        }
        finally { Directory.Delete(game, recursive: true); }
    }

    [Fact]
    public void NonZeroExitWithoutEvidenceIsAWindowCloseNotACrash()
    {
        string dir = Path.Combine(Path.GetTempPath(), "crash-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(dir);
        try
        {
            var start = DateTime.Now.AddMinutes(-5);
            var up = TimeSpan.FromMinutes(5);
            var window = TimeSpan.FromSeconds(20);
            Assert.False(CrashReporter.IsRealCrash(dir, 1, start, up, window));
            Assert.False(CrashReporter.IsRealCrash(dir, 0, start, TimeSpan.Zero, window));
            Assert.True(CrashReporter.IsRealCrash(dir, 1, start, TimeSpan.FromSeconds(3), window));
            Assert.True(CrashReporter.IsRealCrash(dir, unchecked((int)0xC0000005), start, up, window));
            File.WriteAllText(Path.Combine(dir, "hs_err_pid42.log"), "x");
            Assert.True(CrashReporter.IsRealCrash(dir, 1, start, up, window));
            File.Delete(Path.Combine(dir, "hs_err_pid42.log"));
            Directory.CreateDirectory(Path.Combine(dir, "crash-reports"));
            File.WriteAllText(Path.Combine(dir, "crash-reports", "crash-1.txt"), "x");
            Assert.True(CrashReporter.IsRealCrash(dir, 1, start, up, window));
        }
        finally { Directory.Delete(dir, true); }
    }
}
