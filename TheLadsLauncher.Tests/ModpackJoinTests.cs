using CmlLib.Core;
using CmlLib.Core.Auth;
using CmlLib.Core.ProcessBuilder;
using CmlLib.Core.VersionLoader;
using TheLadsLauncher;
using TheLadsLauncher.Services;
using Xunit;
using Xunit.Abstractions;

namespace TheLadsLauncher.Tests;

/// <summary>Zero SMP's "Auto-join server": what the launcher puts on the game's command line (nothing is started).</summary>
public class ModpackJoinTests
{
    private readonly ITestOutputHelper output;
    public ModpackJoinTests(ITestOutputHelper output) => this.output = output;

    // The game arguments of vanilla 1.20.1 and the Forge 47.4.10 profile on top of it, as the launcher's version folder has them.
    private const string Vanilla = """
        {"id":"1.20.1","type":"release","mainClass":"net.minecraft.client.main.Main","assets":"5","assetIndex":{"id":"5","url":"https://example.invalid/5.json","sha1":"00","size":1,"totalSize":1},
         "libraries":[],"downloads":{"client":{"url":"https://example.invalid/client.jar","sha1":"00","size":1}},
         "arguments":{"game":["--username","${auth_player_name}","--version","${version_name}","--gameDir","${game_directory}","--assetsDir","${assets_root}","--assetIndex","${assets_index_name}","--uuid","${auth_uuid}","--accessToken","${auth_access_token}","--clientId","${clientid}","--xuid","${auth_xuid}","--userType","${user_type}","--versionType","${version_type}",
           {"rules":[{"action":"allow","features":{"is_demo_user":true}}],"value":"--demo"},
           {"rules":[{"action":"allow","features":{"has_custom_resolution":true}}],"value":["--width","${resolution_width}","--height","${resolution_height}"]},
           {"rules":[{"action":"allow","features":{"has_quick_plays_support":true}}],"value":["--quickPlayPath","${quickPlayPath}"]},
           {"rules":[{"action":"allow","features":{"is_quick_play_singleplayer":true}}],"value":["--quickPlaySingleplayer","${quickPlaySingleplayer}"]},
           {"rules":[{"action":"allow","features":{"is_quick_play_multiplayer":true}}],"value":["--quickPlayMultiplayer","${quickPlayMultiplayer}"]},
           {"rules":[{"action":"allow","features":{"is_quick_play_realms":true}}],"value":["--quickPlayRealms","${quickPlayRealms}"]}],
          "jvm":["-Djava.library.path=${natives_directory}","-cp","${classpath}"]}}
        """;
    private const string Forge = """
        {"id":"1.20.1-forge-47.4.10","type":"release","inheritsFrom":"1.20.1","mainClass":"cpw.mods.bootstraplauncher.BootstrapLauncher","libraries":[],
         "arguments":{"game":["--launchTarget","forgeclient","--fml.forgeVersion","47.4.10","--fml.mcVersion","1.20.1","--fml.forgeGroup","net.minecraftforge","--fml.mcpVersion","20230612.114412"],
          "jvm":["-DlibraryDirectory=${library_directory}"]}}
        """;

    /// <summary>The Forge 1.20.1 command line the launcher builds (version files only, offline); nothing is started.</summary>
    private async Task<System.Diagnostics.Process> Build(string? joinServer)
    {
        using var dir = new TestDirectory();
        var path = new MinecraftPath(dir.Path);
        foreach (var (id, json) in new[] { ("1.20.1", Vanilla), ("1.20.1-forge-47.4.10", Forge) })
        {
            Directory.CreateDirectory(Path.Combine(path.Versions, id));
            File.WriteAllText(Path.Combine(path.Versions, id, id + ".json"), json);
        }
        var parameters = MinecraftLauncherParameters.CreateDefault(path);
        parameters.VersionLoader = new LocalJsonVersionLoader(path);
        var options = new MLaunchOption { Session = MSession.CreateOfflineSession("QaPlayer"), JavaPath = "java", MaximumRamMb = 2048, MinimumRamMb = 512 };
        if (joinServer != null) ModpackLauncher.JoinServer(options, joinServer);
        var process = await new MinecraftLauncher(parameters).BuildProcessAsync("1.20.1-forge-47.4.10", options);
        output.WriteLine(process.StartInfo.Arguments);
        return process;
    }

    [Theory]
    [InlineData("nikzerosmp.my.pebble.host", "nikzerosmp.my.pebble.host")]
    [InlineData("127.0.0.1:1", "127.0.0.1:1")]
    [InlineData(" play.example.net:25570 ", "play.example.net:25570")]
    public async Task AutoJoinPutsTheQuickPlayServerOnTheForgeCommandLine(string address, string expected)
    {
        var process = await Build(address);

        Assert.Equal(expected, ModpackLauncher.QuickPlayServer(process));
        Assert.Contains("--launchTarget forgeclient", process.StartInfo.Arguments); // Forge's own arguments stay
        Assert.DoesNotContain("--server ", process.StartInfo.Arguments); // 1.20.1 takes quick play, not the old --server/--port
    }

    [Fact]
    public async Task WithoutAutoJoinTheGameStartsAtTheTitleScreen()
    {
        var process = await Build(null);

        Assert.Null(ModpackLauncher.QuickPlayServer(process));
        Assert.DoesNotContain("quickPlay", process.StartInfo.Arguments);
    }

    [Fact]
    public void AutoJoinIsOffByDefaultAndTheServerIsTheExperiencesOwn()
    {
        Assert.False(new LauncherSettings().ZeroSmpAutoJoin);
        Assert.Equal("nikzerosmp.my.pebble.host", ExperiencesService.ZeroSmp.ServerAddress);
    }
}
