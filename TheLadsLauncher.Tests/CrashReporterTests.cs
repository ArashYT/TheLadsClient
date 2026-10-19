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
}
