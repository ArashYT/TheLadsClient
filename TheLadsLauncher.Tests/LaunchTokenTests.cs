using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>The access token goes to the game on stdin, not its command line, when the game's Core can read it there.</summary>
public class LaunchTokenTests
{
    private static void Core(string gameDirectory, bool readsStdin)
    {
        Directory.CreateDirectory(Path.Combine(gameDirectory, "mods"));
        using var zip = ZipFile.Open(Path.Combine(gameDirectory, "mods", "theladscore.jar"), ZipArchiveMode.Create);
        zip.CreateEntry("fabric.mod.json");
        if (readsStdin) zip.CreateEntry("com/thelads/core/v1_20_1/mixin/SessionTokenMixin.class");
    }

    // cmd echoes its stdin; everything after rem is only there to be the game's command line.
    private static Process Game(string token) => new()
    {
        StartInfo = new ProcessStartInfo("cmd.exe", $"/c findstr \"^\" & rem --username Lad --accessToken {token} --userType msa")
        { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true }
    };

    [Fact]
    public void TheTokenGoesToStdinNotTheCommandLine()
    {
        using var dir = new TestDirectory();
        Core(dir.Path, readsStdin: true);
        using var game = Game("eyJ.secret-token_1");

        LaunchToken.Hide(game, dir.Path, "eyJ.secret-token_1");

        Assert.DoesNotContain("secret", game.StartInfo.Arguments);
        Assert.Contains("--accessToken " + LaunchToken.Placeholder + " --userType", game.StartInfo.Arguments);
        game.Start();
        LaunchToken.Send(game);
        Assert.Equal("eyJ.secret-token_1", game.StandardOutput.ReadToEnd().Trim());
        game.WaitForExit();
    }

    [Fact]
    public void AnOfflineTokenGoesTheSameWay()
    {
        using var dir = new TestDirectory();
        Core(dir.Path, readsStdin: true);
        using var game = Game("0");

        LaunchToken.Hide(game, dir.Path, "0");

        Assert.Contains("--accessToken " + LaunchToken.Placeholder + " ", game.StartInfo.Arguments);
        Assert.Contains("--username Lad", game.StartInfo.Arguments); // other 0s untouched
    }

    [Theory]
    [InlineData(false)] // an older Core
    [InlineData(null)]  // no Core
    public void WithoutAStdinCoreTheCommandLineKeepsTheToken(bool? core)
    {
        using var dir = new TestDirectory();
        if (core is { } readsStdin) Core(dir.Path, readsStdin);
        using var game = Game("eyJ.secret");

        LaunchToken.Hide(game, dir.Path, "eyJ.secret");

        Assert.Contains("--accessToken eyJ.secret", game.StartInfo.Arguments);
        Assert.False(game.StartInfo.RedirectStandardInput);
    }
}
