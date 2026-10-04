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
}
