using System.IO.Compression;
using System.Text.Json.Nodes;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>1.13.24: a game folder with no options.txt starts from the player's .minecraft one, and Vignette and Chunk Fade go off once per
/// game folder (marker file), never again after the player turns them back on.</summary>
public class LadsDefaultsTests
{
    private static string Game(TestDirectory dir, string name = "game")
    {
        var game = Path.Combine(dir.Path, name);
        Directory.CreateDirectory(game);
        return game;
    }

    private static string Minecraft(TestDirectory dir, string options)
    {
        var root = Path.Combine(dir.Path, ".minecraft");
        Directory.CreateDirectory(root);
        File.WriteAllText(Path.Combine(root, "options.txt"), options);
        return root;
    }

    private static void ClientJar(string game, string version, int dataVersion)
    {
        var jar = Path.Combine(game, "versions", version, version + ".jar");
        Directory.CreateDirectory(Path.GetDirectoryName(jar)!);
        using var zip = ZipFile.Open(jar, ZipArchiveMode.Create);
        using var writer = new StreamWriter(zip.CreateEntry("version.json").Open());
        writer.Write("{\"world_version\":" + dataVersion + "}");
    }

    private static Dictionary<string, string> Options(string game) => GameOptionsService.ParseOptions(File.ReadAllText(Path.Combine(game, "options.txt")));

    [Theory]
    [InlineData("26.3", true)]
    [InlineData("26.2", true)]
    [InlineData("1.21.11", true)]
    [InlineData("1.21.1", false)]
    [InlineData("1.20.1", false)]
    [InlineData("1.8.9", false)]
    [InlineData("latest.release", false)]
    public void OnlyVersionsWhoseOptionsTxtHasTheTwoSettingsGetThem(string version, bool expected) =>
        Assert.Equal(expected, GameOptionsService.HasVideoDefaults(version));

    [Fact]
    public void NewProfileInheritsDotMinecraftOptionsWithTheDefaultsOnTop()
    {
        using var dir = new TestDirectory();
        var root = Minecraft(dir, "version:4325\nfov:0.7\nvignette:true\nchunkSectionFadeInTime:1.0\nkey_key.jump:key.keyboard.f\nsoundCategory_music:0.2\nresourcePacks:[\"vanilla\",\"file/Mine.zip\"]\nlastServer:play.example.org\n");
        var game = Game(dir);

        GameOptionsService.ApplyLadsDefaults(game, "26.3", root);

        var options = Options(game);
        Assert.Equal("0.7", options["fov"]);                       // inherited
        Assert.Equal("key.keyboard.f", options["key_key.jump"]);   // keybinds too
        Assert.Equal("0.2", options["soundCategory_music"]);
        Assert.Equal("false", options["vignette"]);                // the defaults win over the inherited values
        Assert.Equal("0.0", options["chunkSectionFadeInTime"]);
        Assert.False(options.ContainsKey("resourcePacks"));        // the Lads packs are not replaced
        Assert.False(options.ContainsKey("lastServer"));
        Assert.True(File.Exists(Path.Combine(game, GameOptionsService.DefaultsMarkerFile)));
        // .minecraft itself is only read.
        Assert.Equal("true", GameOptionsService.ParseOptions(File.ReadAllText(Path.Combine(root, "options.txt")))["vignette"]);
    }

    [Fact]
    public void ExistingOptionsAreNeverReplacedByTheInheritance()
    {
        using var dir = new TestDirectory();
        var root = Minecraft(dir, "version:4325\nfov:0.7\n");
        var game = Game(dir);
        File.WriteAllText(Path.Combine(game, "options.txt"), "version:4325\nfov:0.1\nvignette:true\n");

        Assert.False(GameOptionsService.InheritMinecraftOptions(game, "26.3", root));
        GameOptionsService.ApplyLadsDefaults(game, "26.3", root);

        var options = Options(game);
        Assert.Equal("0.1", options["fov"]);          // the profile's own value stays
        Assert.Equal("false", options["vignette"]);   // the one-time override still reaches an existing profile
    }

    [Fact]
    public void LinesOnlyTheLauncherWroteDoNotCountAsOptionsOfItsOwnAndStayAsTheyAre()
    {
        using var dir = new TestDirectory();
        var root = Minecraft(dir, string.Join("\n", "version:4325", "fov:0.7", "preferredGraphicsBackend:\"opengl\"", "fullscreen:false", "key_key.jump:key.keyboard.f", ""));
        var game = Game(dir);
        // The launcher puts its renderer choice and fullscreen-on-launch in before the game has ever run.
        File.WriteAllText(Path.Combine(game, "options.txt"), string.Join("\n", "preferredGraphicsBackend:\"vulkan\"", "fullscreen:true", ""));

        GameOptionsService.ApplyLadsDefaults(game, "26.3", root);

        var options = Options(game);
        Assert.Equal("0.7", options["fov"]);
        Assert.Equal("key.keyboard.f", options["key_key.jump"]);
        Assert.Equal("\"vulkan\"", options["preferredGraphicsBackend"]); // the launcher's values win
        Assert.Equal("true", options["fullscreen"]);
        Assert.Equal("false", options["vignette"]);
    }

    [Fact]
    public void OneTimeOverrideAppliesOncePerFolderAndAPlayersLaterChoiceStays()
    {
        using var dir = new TestDirectory();
        var game = Game(dir);
        var options = Path.Combine(game, "options.txt");
        File.WriteAllText(options, "version:4325\nfov:0.5\nvignette:true\nchunkSectionFadeInTime:2.0\n");

        GameOptionsService.ApplyLadsDefaults(game, "26.3", null);
        Assert.Equal("false", Options(game)["vignette"]);
        Assert.Equal("0.0", Options(game)["chunkSectionFadeInTime"]);
        Assert.Equal("0.5", Options(game)["fov"]);

        // The player turns both back on in game; the next launches keep that.
        File.WriteAllText(options, "version:4325\nfov:0.5\nvignette:true\nchunkSectionFadeInTime:1.5\n");
        GameOptionsService.ApplyLadsDefaults(game, "26.3", null);
        GameOptionsService.ApplyLadsDefaults(game, "26.3", null);
        Assert.Equal("true", Options(game)["vignette"]);
        Assert.Equal("1.5", Options(game)["chunkSectionFadeInTime"]);
    }

    [Fact]
    public void OtherLinesAndTheLineEndingOfTheFileStayWhenTheKeysAreSet()
    {
        using var dir = new TestDirectory();
        var file = Path.Combine(dir.Path, "options.txt");
        File.WriteAllText(file, "version:4325\r\nvignette:true\r\nfov:0.5\r\n");
        GameOptionsService.SetOptions(file, GameOptionsService.VideoDefaults);
        Assert.Equal("version:4325\r\nvignette:false\r\nfov:0.5\r\nchunkSectionFadeInTime:0.0\r\n", File.ReadAllText(file));
    }

    [Fact]
    public void FreshInstallGetsAnOptionsFileWithTheDataVersionAndTheDefaults()
    {
        using var dir = new TestDirectory();
        var game = Game(dir);
        ClientJar(game, "26.3", 5023);

        GameOptionsService.ApplyLadsDefaults(game, "26.3", Path.Combine(dir.Path, "no-dot-minecraft"));

        var options = Options(game);
        Assert.Equal("5023", options["version"]);
        Assert.Equal("false", options["vignette"]);
        Assert.Equal("0.0", options["chunkSectionFadeInTime"]);
    }

    [Fact]
    public void WithNoOptionsFileAndNoJarToReadTheDataVersionFromTheDefaultsWaitForTheGamesOwnFile()
    {
        using var dir = new TestDirectory();
        var game = Game(dir);

        GameOptionsService.ApplyLadsDefaults(game, "26.3", null);
        Assert.False(File.Exists(Path.Combine(game, "options.txt")));
        Assert.False(File.Exists(Path.Combine(game, GameOptionsService.DefaultsMarkerFile)));

        File.WriteAllText(Path.Combine(game, "options.txt"), "version:5023\nvignette:true\n");  // the game made it
        GameOptionsService.ApplyLadsDefaults(game, "26.3", null);
        Assert.Equal("false", Options(game)["vignette"]);
    }

    [Fact]
    public void ADataVersionNewerThanTheGamesOwnIsDroppedFromAnInheritedFile()
    {
        using var dir = new TestDirectory();
        var root = Minecraft(dir, "version:9999\nfov:0.7\n");
        var game = Game(dir);
        ClientJar(game, "1.21.11", 4671);

        GameOptionsService.ApplyLadsDefaults(game, "1.21.11", root);

        // Dropped, then put back as this game's own by EnsureDataVersion in the launch path.
        Assert.NotEqual("9999", Options(game).GetValueOrDefault("version"));
        Assert.Equal("0.7", Options(game)["fov"]);
    }

    [Fact]
    public void OnlyTheSameKindOfOptionsFileIsInherited()
    {
        using var dir = new TestDirectory();
        var modern = Minecraft(dir, "version:4325\nfov:0.7\n");
        var legacyGame = Game(dir, "legacy");
        Assert.False(GameOptionsService.InheritMinecraftOptions(legacyGame, "1.8.9", modern)); // a 1.13+ file is not a 1.8.9 one
        Assert.False(File.Exists(Path.Combine(legacyGame, "options.txt")));

        var oldRoot = Path.Combine(dir.Path, "old-minecraft");
        Directory.CreateDirectory(oldRoot);
        File.WriteAllText(Path.Combine(oldRoot, "options.txt"), "fov:90.0\nkey_key.attack:-100\n");
        Assert.True(GameOptionsService.InheritMinecraftOptions(legacyGame, "1.8.9", oldRoot));
        Assert.Equal("90.0", Options(legacyGame)["fov"]);
        Assert.False(GameOptionsService.InheritMinecraftOptions(Game(dir, "modern"), "26.3", oldRoot)); // and not the other way round
    }

    [Fact]
    public void Legacy189VignetteIsSetInAnExistingLadsConfigOnceAndOtherSettingsStay()
    {
        using var dir = new TestDirectory();
        var game = Game(dir);
        var config = Path.Combine(game, "thelads_config.json");

        GameOptionsService.ApplyLadsDefaults(game, "1.8.9", null);
        Assert.False(File.Exists(config));                                    // no config yet: nothing made, the pass waits
        Assert.False(File.Exists(Path.Combine(game, GameOptionsService.DefaultsMarkerFile)));

        File.WriteAllText(config, "{\"modules\":{\"OptiFine189\":{\"enabled\":true,\"options\":{\"Vignette\":0,\"Trees\":2}},\"Sprint\":{\"enabled\":true}},\"hud\":{}}");
        GameOptionsService.ApplyLadsDefaults(game, "1.8.9", null);
        var json = JsonNode.Parse(File.ReadAllText(config))!;
        Assert.Equal(1, (int)json["modules"]!["OptiFine189"]!["options"]!["Vignette"]!);
        Assert.Equal(2, (int)json["modules"]!["OptiFine189"]!["options"]!["Trees"]!);
        Assert.True((bool)json["modules"]!["Sprint"]!["enabled"]!);

        File.WriteAllText(config, "{\"modules\":{\"OptiFine189\":{\"options\":{\"Vignette\":2}}}}"); // the player picks Fancy
        GameOptionsService.ApplyLadsDefaults(game, "1.8.9", null);
        Assert.Equal(2, (int)JsonNode.Parse(File.ReadAllText(config))!["modules"]!["OptiFine189"]!["options"]!["Vignette"]!);
    }

    [Fact]
    public void Legacy189GetsNoVanillaKeysAnd1201GetsNothing()
    {
        using var dir = new TestDirectory();
        var legacy = Game(dir, "legacy");
        File.WriteAllText(Path.Combine(legacy, "options.txt"), "fov:90.0\n");
        GameOptionsService.ApplyLadsDefaults(legacy, "1.8.9", null);
        Assert.Equal("fov:90.0\n", File.ReadAllText(Path.Combine(legacy, "options.txt")));

        var v1201 = Game(dir, "v1201");
        File.WriteAllText(Path.Combine(v1201, "options.txt"), "version:3465\nfov:0.5\n");
        GameOptionsService.ApplyLadsDefaults(v1201, "1.20.1", null);
        Assert.Equal("version:3465\nfov:0.5\n", File.ReadAllText(Path.Combine(v1201, "options.txt")));
    }
}
