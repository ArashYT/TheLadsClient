using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class PackDefaultsTests
{
    private static void Write(string path, string text)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        File.WriteAllText(path, text);
    }

    [Fact]
    public void MissingDefaultsAreCopiedAndThePlayersFilesKept()
    {
        using var dir = new TestDirectory();
        var defaults = Path.Combine(dir.Path, PackDefaults.FolderName);
        Write(Path.Combine(defaults, "config", "sodium-options.json"), "{\"pack\":true}");
        Write(Path.Combine(defaults, "config", "nested", "a.toml"), "a = 1");
        Write(Path.Combine(defaults, "config", "changed.json"), "pack default");
        Write(Path.Combine(defaults, "README.md"), "about this folder");
        Write(Path.Combine(dir.Path, "config", "changed.json"), "player's own");

        PackDefaults.Apply(dir.Path);

        Assert.Equal("{\"pack\":true}", File.ReadAllText(Path.Combine(dir.Path, "config", "sodium-options.json")));
        Assert.Equal("a = 1", File.ReadAllText(Path.Combine(dir.Path, "config", "nested", "a.toml")));
        Assert.Equal("player's own", File.ReadAllText(Path.Combine(dir.Path, "config", "changed.json")));
        Assert.False(File.Exists(Path.Combine(dir.Path, "README.md")));

        // Next launch: the player edited a copied default; it stays.
        File.WriteAllText(Path.Combine(dir.Path, "config", "sodium-options.json"), "edited");
        PackDefaults.Apply(dir.Path);
        Assert.Equal("edited", File.ReadAllText(Path.Combine(dir.Path, "config", "sodium-options.json")));
    }

    [Fact]
    public void OptionsGainOnlyTheKeysThePlayerLacks()
    {
        using var dir = new TestDirectory();
        Write(Path.Combine(dir.Path, PackDefaults.FolderName, "options.txt"), "guiScale:3\nkey_key.zoom:key.keyboard.c\nlang:en_us\n");
        var options = Path.Combine(dir.Path, "options.txt");
        File.WriteAllText(options, "guiScale:2\nmaxFps:120"); // no trailing newline

        PackDefaults.Apply(dir.Path);
        PackDefaults.Apply(dir.Path); // a second launch adds nothing

        Assert.Equal("guiScale:2\nmaxFps:120\nkey_key.zoom:key.keyboard.c\nlang:en_us\n", File.ReadAllText(options));
    }

    [Fact]
    public void FirstLaunchCopiesTheWholeOptionsFile()
    {
        using var dir = new TestDirectory();
        Write(Path.Combine(dir.Path, PackDefaults.FolderName, "options.txt"), "guiScale:3\n");
        PackDefaults.Apply(dir.Path);
        Assert.Equal("guiScale:3\n", File.ReadAllText(Path.Combine(dir.Path, "options.txt")));
    }

    [Fact]
    public void StandsDownWhileTheOriginalModIsInstalled()
    {
        using var dir = new TestDirectory();
        Write(Path.Combine(dir.Path, PackDefaults.FolderName, "config", "x.json"), "{}");
        Write(Path.Combine(dir.Path, "mods", "ConfiguredDefaults-v26.2.0-mc26.2.x-Fabric.jar"), "");
        PackDefaults.Apply(dir.Path);
        Assert.False(File.Exists(Path.Combine(dir.Path, "config", "x.json")));
        PackDefaults.Apply(Path.Combine(dir.Path, "no-such-profile")); // no defaults folder: nothing to do
    }

    [Fact]
    public void BundledConfiguredDefaultsAreInstalledAndAppliedWithoutOverwriting()
    {
        using var bundle = new TestDirectory();
        using var game = new TestDirectory();
        var bundledDefaults = Path.Combine(bundle.Path, "game-mods", "1.20.1", PackDefaults.FolderName);
        Write(Path.Combine(bundledDefaults, "config", "modernfix-mixins.properties"), "mixin.perf.dynamic_resources=true\n");
        Write(Path.Combine(game.Path, "config", "existing.txt"), "untouched");

        BundledModInstaller.InstallConfiguredDefaults(bundle.Path, game.Path, "1.20.1");
        PackDefaults.Apply(game.Path);

        Assert.Equal("mixin.perf.dynamic_resources=true\n", File.ReadAllText(Path.Combine(game.Path, "config", "modernfix-mixins.properties")));
        Assert.Equal("untouched", File.ReadAllText(Path.Combine(game.Path, "config", "existing.txt")));
    }

    [Fact]
    public void ExistingModernFixDynamicResourcesIsMigratedToFalse()
    {
        using var bundle = new TestDirectory();
        using var game = new TestDirectory();
        var bundledDefaults = Path.Combine(bundle.Path, "game-mods", "1.20.1", PackDefaults.FolderName);
        Write(Path.Combine(bundledDefaults, "config", "modernfix-mixins.properties"), "mixin.perf.dynamic_resources=false\n");
        Write(Path.Combine(game.Path, PackDefaults.FolderName, "config", "modernfix-mixins.properties"), "mixin.perf.dynamic_resources=true\n");
        Write(Path.Combine(game.Path, "config", "modernfix-mixins.properties"), "# ModernFix\nmixin.perf.dynamic_resources=true\n");

        BundledModInstaller.InstallConfiguredDefaults(bundle.Path, game.Path, "1.20.1");
        PackDefaults.Apply(game.Path);

        Assert.Equal("mixin.perf.dynamic_resources=false\n", File.ReadAllText(Path.Combine(game.Path, PackDefaults.FolderName, "config", "modernfix-mixins.properties")));
        Assert.Equal("# ModernFix\nmixin.perf.dynamic_resources=false\n", File.ReadAllText(Path.Combine(game.Path, "config", "modernfix-mixins.properties")));
    }

    [Fact]
    public void PackDefaultsSkipsLadsStateFiles()
    {
        using var dir = new TestDirectory();
        var defaults = Path.Combine(dir.Path, PackDefaults.FolderName);
        Write(Path.Combine(defaults, "server_bypasses.json"), "{\"planted\":true}");
        Write(Path.Combine(defaults, "thelads_config.json"), "{\"planted\":true}");
        Write(Path.Combine(defaults, "lads-mod-state.json"), "{\"planted\":true}");
        Write(Path.Combine(defaults, "config", "valid.json"), "{\"valid\":true}");

        PackDefaults.Apply(dir.Path);

        Assert.Equal("{\"valid\":true}", File.ReadAllText(Path.Combine(dir.Path, "config", "valid.json")));
        Assert.False(File.Exists(Path.Combine(dir.Path, "server_bypasses.json")));
        Assert.False(File.Exists(Path.Combine(dir.Path, "thelads_config.json")));
        Assert.False(File.Exists(Path.Combine(dir.Path, "lads-mod-state.json")));
        Assert.False(File.Exists(Path.Combine(defaults, "server_bypasses.json")));
    }

    [Fact]
    public void PackDefaultsPurgesLadsStateFilesEvenWhenConfiguredDefaultsModIsInstalled()
    {
        using var dir = new TestDirectory();
        var defaults = Path.Combine(dir.Path, PackDefaults.FolderName);
        Write(Path.Combine(defaults, "server_bypasses.json"), "{\"planted\":true}");
        Write(Path.Combine(defaults, "config", "x.json"), "{}");
        Write(Path.Combine(dir.Path, "mods", "ConfiguredDefaults-v26.2.0-mc26.2.x-Fabric.jar"), "");

        PackDefaults.Apply(dir.Path);

        Assert.False(File.Exists(Path.Combine(defaults, "server_bypasses.json")));
        Assert.False(File.Exists(Path.Combine(dir.Path, "server_bypasses.json")));
        Assert.False(File.Exists(Path.Combine(dir.Path, "config", "x.json")));
    }
}
