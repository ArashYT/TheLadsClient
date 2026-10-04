using System;
using System.IO;
using System.Linq;

namespace TheLadsLauncher.Services;

/// <summary>
/// Configured Defaults, remade in the launcher (before the game starts, so before any mod or Minecraft reads a config): a pack
/// puts default files under &lt;game folder&gt;\configureddefaults\, laid out like the game folder, and each file the game
/// folder lacks is copied in. Nothing that exists is replaced, so a player's changes stay. options.txt is merged instead:
/// only the keys the player's options.txt lacks are added.
/// </summary>
public static class PackDefaults
{
    public const string FolderName = "configureddefaults";

    public static void Apply(string gameDirectory)
    {
        var source = Path.Combine(gameDirectory, FolderName);
        if (!Directory.Exists(source)) return;
        // The original mod, while still installed, does this itself.
        var mods = Path.Combine(gameDirectory, "mods");
        if (Directory.Exists(mods) && Directory.EnumerateFiles(mods, "*.jar")
                .Any(jar => Path.GetFileName(jar).StartsWith("ConfiguredDefaults", StringComparison.OrdinalIgnoreCase))) return;
        foreach (var file in Directory.EnumerateFiles(source, "*", SearchOption.AllDirectories))
        {
            var relative = Path.GetRelativePath(source, file);
            if (relative.Equals("README.md", StringComparison.OrdinalIgnoreCase)) continue; // notes about the folder, not a default
            var target = Path.Combine(gameDirectory, relative);
            try
            {
                if (relative.Equals("options.txt", StringComparison.OrdinalIgnoreCase) && File.Exists(target)) MergeOptions(file, target);
                else if (!File.Exists(target) && !Directory.Exists(target))
                {
                    Directory.CreateDirectory(Path.GetDirectoryName(target)!);
                    File.Copy(file, target);
                }
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { } // that default is skipped, the game keeps its own
        }
    }

    private static void MergeOptions(string defaults, string options)
    {
        var text = File.ReadAllText(options);
        var keys = text.Split('\n').Select(Key).Where(key => key != null).ToHashSet();
        var missing = File.ReadAllLines(defaults).Where(line => Key(line) is { } key && keys.Add(key)).ToList();
        if (missing.Count == 0) return;
        File.AppendAllText(options, (text.Length == 0 || text.EndsWith('\n') ? "" : "\n") + string.Join("\n", missing) + "\n");
    }

    private static string? Key(string line)
    {
        int colon = line.IndexOf(':');
        return colon > 0 ? line[..colon].Trim() : null;
    }
}
