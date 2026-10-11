using System.Diagnostics;
using System.Text.RegularExpressions;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>The Lads early loading window on Forge 1.20.1 packs: its jar on the game's -cp and config/fml.toml naming it (nothing is started).</summary>
public class ModpackEarlyWindowTests
{
    // Zero SMP's own fml.toml as Forge 47.4.10 writes it (shortened).
    private const string ForgeToml = "#Early window height\nearlyWindowHeight = 480\n#Should we control the window.\nearlyWindowControl = true\n"
        + "#Early window provider\nearlyWindowProvider = \"fmlearlywindow\"\n#Early window width\nearlyWindowWidth = 854\n";

    private static string ClassPath(string arguments)
    {
        var match = Regex.Match(arguments, @"(?<=(^|\s)-cp\s+)(""[^""]*""|\S+)");
        Assert.True(match.Success, "no -cp in: " + arguments);
        return match.Value.Trim('"');
    }

    private static (string Data, string Game, string Jar) Folders(TestDirectory dir, string? toml)
    {
        string data = Path.Combine(dir.Path, "data"), game = Path.Combine(dir.Path, "instance", "minecraft"), jar = Path.Combine(dir.Path, "bundle", ModpackLauncher.EarlyWindowJar);
        Directory.CreateDirectory(Path.GetDirectoryName(jar)!);
        File.WriteAllBytes(jar, [1, 2, 3, 4]);
        if (toml != null)
        {
            Directory.CreateDirectory(Path.Combine(game, "config"));
            File.WriteAllText(Path.Combine(game, "config", "fml.toml"), toml);
        }
        return (data, game, jar);
    }

    [Fact]
    public async Task TheJarGoesOnTheForgeClassPathAndFmlTomlNamesIt()
    {
        using var dir = new TestDirectory();
        var (data, game, jar) = Folders(dir, ForgeToml);
        var process = await ModpackJoinTests.BuildForge1201();
        string before = ClassPath(process.StartInfo.Arguments);

        ModpackLauncher.UseEarlyWindow(process, data, game, jar, null);

        string staged = ClassPath(process.StartInfo.Arguments)[(before.Length + 1)..];
        Assert.Equal(before + Path.PathSeparator + staged, ClassPath(process.StartInfo.Arguments)); // appended, the rest kept
        Assert.Equal(ModpackLauncher.EarlyWindowJar, Path.GetFileName(staged));
        Assert.StartsWith(Path.Combine(ModpackLauncher.CacheRoot(data), "lads-earlywindow"), staged); // a copy: the launcher's own folder stays unlocked
        Assert.Equal(File.ReadAllBytes(jar), File.ReadAllBytes(staged));
        Assert.Contains("--launchTarget forgeclient", process.StartInfo.Arguments);
        Assert.Equal(ForgeToml.Replace("\"fmlearlywindow\"", "\"ladsearlywindow\""), File.ReadAllText(Path.Combine(game, "config", "fml.toml")));

        var again = await ModpackJoinTests.BuildForge1201(); // the next launch: same staged copy, fml.toml already right
        string againBefore = ClassPath(again.StartInfo.Arguments);
        ModpackLauncher.UseEarlyWindow(again, data, game, jar, null);
        Assert.Equal(againBefore + Path.PathSeparator + staged, ClassPath(again.StartInfo.Arguments));
    }

    /// <summary>PrepareAsync's order: LaunchToken.Hide, then the early window. The token stays off the command line and on stdin.</summary>
    [Fact]
    public async Task TheAccessTokenStaysOnStdinWithTheJarOnTheClassPath()
    {
        const string token = "eyJ.secret-token_1";
        using var dir = new TestDirectory();
        var (data, game, jar) = Folders(dir, ForgeToml);
        using (var zip = System.IO.Compression.ZipFile.Open(Path.Combine(Directory.CreateDirectory(Path.Combine(game, "mods")).FullName, "theladscore.jar"), System.IO.Compression.ZipArchiveMode.Create))
            zip.CreateEntry("com/thelads/core/v1_20_1/mixin/SessionTokenMixin.class");

        var forge = await ModpackJoinTests.BuildForge1201(session: new CmlLib.Core.Auth.MSession("Lad", token, "0123456789abcdef0123456789abcdef") { UserType = "msa" });
        LaunchToken.Hide(forge, game, token);
        ModpackLauncher.UseEarlyWindow(forge, data, game, jar, null);

        Assert.EndsWith(ModpackLauncher.EarlyWindowJar, ClassPath(forge.StartInfo.Arguments));
        Assert.DoesNotContain("secret", forge.StartInfo.Arguments);
        Assert.Contains("--accessToken " + LaunchToken.Placeholder, forge.StartInfo.Arguments);
        Assert.True(forge.StartInfo.RedirectStandardInput);

        // And the hand-over itself: cmd echoes its stdin; after rem is a game command line with -cp and the token.
        using var echo = new Process
        {
            StartInfo = new ProcessStartInfo("cmd.exe", $"/c findstr \"^\" & rem -cp a.jar;b.jar Main --accessToken {token} --userType msa")
            { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true }
        };
        LaunchToken.Hide(echo, game, token);
        ModpackLauncher.UseEarlyWindow(echo, data, game, jar, null);
        Assert.Contains(ModpackLauncher.EarlyWindowJar + "\" Main --accessToken " + LaunchToken.Placeholder, echo.StartInfo.Arguments);
        echo.Start();
        LaunchToken.Send(echo);
        Assert.Equal(token, echo.StandardOutput.ReadToEnd().Trim());
        echo.WaitForExit();
    }

    [Fact]
    public async Task WithoutTheJarForgeKeepsItsOwnWindow()
    {
        using var dir = new TestDirectory();
        var (data, game, _) = Folders(dir, ForgeToml);
        var process = await ModpackJoinTests.BuildForge1201();
        string arguments = process.StartInfo.Arguments;
        var fml = Path.Combine(game, "config", "fml.toml");
        var written = File.GetLastWriteTimeUtc(fml);

        ModpackLauncher.UseEarlyWindow(process, data, game, null, null);

        Assert.Equal(arguments, process.StartInfo.Arguments);
        Assert.Equal(ForgeToml, File.ReadAllText(fml));
        Assert.Equal(written, File.GetLastWriteTimeUtc(fml)); // left alone
        Assert.False(Directory.Exists(Path.Combine(ModpackLauncher.CacheRoot(data), "lads-earlywindow")));
    }

    [Fact]
    public void AMissingJarSetsForgesProviderBackAfterAnEarlierLadsLaunch()
    {
        string lads = ModpackLauncher.WithEarlyWindow(ForgeToml, true)!;
        Assert.Contains("earlyWindowProvider = \"ladsearlywindow\"", lads);
        Assert.Equal(ForgeToml, ModpackLauncher.WithEarlyWindow(lads, false)); // else Forge would find no provider and show no window at all
        Assert.Null(ModpackLauncher.WithEarlyWindow(null, false)); // no file: none is written
    }

    [Theory]
    [InlineData("earlyWindowControl = false\nearlyWindowProvider = \"fmlearlywindow\"\n")]
    [InlineData("  earlyWindowControl=false\r\n  earlyWindowProvider=\"other\"\r\n")]
    [InlineData("")]
    [InlineData(null)]
    public void FmlTomlTurnsTheLadsWindowOn(string? toml)
    {
        string edited = ModpackLauncher.WithEarlyWindow(toml, true)!;

        Assert.Matches(@"(?m)^\s*earlyWindowControl\s*=\s*true\r?$", edited);
        Assert.Matches(@"(?m)^\s*earlyWindowProvider\s*=\s*""ladsearlywindow""\r?$", edited);
        Assert.Single(Regex.Matches(edited, "earlyWindowControl"));
        Assert.Single(Regex.Matches(edited, "earlyWindowProvider"));
        Assert.Equal(edited, ModpackLauncher.WithEarlyWindow(edited, true));
    }

    [Theory]
    [InlineData("-Xmx2G -cp a.jar;b.jar Main", "-Xmx2G -cp \"a.jar;b.jar;X\" Main")]
    [InlineData("-cp \"C:\\Program Files\\a.jar\" Main", "-cp \"C:\\Program Files\\a.jar;X\" Main")]
    public void ClassPathInTheArgumentsString(string arguments, string expected)
    {
        if (Path.PathSeparator != ';') return; // the launcher's command lines are Windows ones
        var start = new ProcessStartInfo("java", arguments);

        Assert.True(ModpackLauncher.AppendClassPath(start, "X"));
        Assert.Equal(expected, start.Arguments);
    }

    [Fact]
    public void ClassPathInTheArgumentList()
    {
        var start = new ProcessStartInfo("java");
        foreach (var argument in new[] { "-cp", "a.jar", "Main" }) start.ArgumentList.Add(argument);

        Assert.True(ModpackLauncher.AppendClassPath(start, "X"));
        Assert.Equal(new[] { "-cp", "a.jar" + Path.PathSeparator + "X", "Main" }, start.ArgumentList);
        Assert.False(ModpackLauncher.AppendClassPath(new ProcessStartInfo("java", "-jar game.jar"), "X")); // no -cp: Forge's window stays
    }
}
