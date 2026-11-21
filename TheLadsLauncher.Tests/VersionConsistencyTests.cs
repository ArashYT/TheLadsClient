using System;
using System.IO;
using System.Text.RegularExpressions;
using Xunit;

namespace TheLadsLauncher.Tests;

public class VersionConsistencyTests
{
    static string Root()
    {
        var d = new DirectoryInfo(AppContext.BaseDirectory);
        while (d != null && !File.Exists(Path.Combine(d.FullName, "VERSION"))) d = d.Parent;
        return d?.FullName ?? throw new FileNotFoundException("VERSION not found above " + AppContext.BaseDirectory);
    }

    [Fact]
    public void LauncherAndModVersionsMatchVersionFile()
    {
        var root = Root();
        var version = File.ReadAllText(Path.Combine(root, "VERSION")).Trim();
        Assert.Matches(@"^\d+\.\d+\.\d+$", version);
        Assert.Equal(version, Program.Version);
        var props = File.ReadAllText(Path.Combine(root, "TheLadsCore", "gradle.properties"));
        var m = Regex.Match(props, @"^mod_version=(.+)$", RegexOptions.Multiline);
        Assert.True(m.Success, "mod_version missing");
        Assert.Equal(version, m.Groups[1].Value.Trim());
    }
}
