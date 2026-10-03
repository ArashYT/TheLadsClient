using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace TheLadsLauncher.Services;

public sealed class ModpackSource
{
    public string Type { get; set; } = "modrinth";
    public string ProjectId { get; set; } = "";
    public string VersionId { get; set; } = "";
}

/// <summary>
/// instance.json of a modpack instance (shared 1.6.0 convention: &lt;launcher data&gt;/instances/&lt;id&gt;/instance.json, game dir
/// &lt;id&gt;/minecraft/). author, packVersion, maxRamMb and lastPlayedUtc are this launcher's optional extras.
/// </summary>
public sealed class ModpackInstance
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string McVersion { get; set; } = "";
    public string Loader { get; set; } = "vanilla";
    public string LoaderVersion { get; set; } = "";
    public string? IconPath { get; set; }
    public ModpackSource? Source { get; set; }
    public DateTime CreatedUtc { get; set; }
    public string? Author { get; set; }
    public string? PackVersion { get; set; }
    public int MaxRamMb { get; set; }
    public DateTime? LastPlayedUtc { get; set; }

    [JsonIgnore] public string Directory { get; set; } = "";
    [JsonIgnore] public string GameDirectory => Path.Combine(Directory, "minecraft");
    [JsonIgnore] public string? IconFile => IconPath is { Length: > 0 } icon ? Mrpack.SafePath(Directory, icon) : null;
}

public sealed record ModpackMod(string FileName, string Path, bool Enabled);

public static class Modpacks
{
    public static readonly string[] Loaders = { "fabric", "quilt", "forge", "neoforge", "vanilla" };
    private static readonly JsonSerializerOptions Json = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping, // names stay readable in instance.json
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        WriteIndented = true
    };

    public static string Root(string dataDirectory) => Path.Combine(dataDirectory, "instances");

    /// <summary>Every readable instance, newest first. A folder without a valid instance.json (an install in progress, a
    /// broken file) is skipped.</summary>
    public static List<ModpackInstance> List(string dataDirectory)
    {
        var root = Root(dataDirectory);
        var list = new List<ModpackInstance>();
        if (!System.IO.Directory.Exists(root)) return list;
        foreach (var dir in System.IO.Directory.EnumerateDirectories(root))
        {
            try
            {
                var instance = JsonSerializer.Deserialize<ModpackInstance>(File.ReadAllText(Path.Combine(dir, "instance.json")), Json);
                if (instance == null || instance.Id != Path.GetFileName(dir)) continue;
                instance.Directory = dir;
                list.Add(instance);
            }
            catch (Exception e) when (e is IOException or JsonException or UnauthorizedAccessException or InvalidDataException) { }
        }
        return list.OrderByDescending(i => i.CreatedUtc).ToList();
    }

    /// <summary>A new, empty instance folder named after <paramref name="name"/> (a-z, 0-9 and '-', numbered when taken).
    /// instance.json is written by <see cref="Save"/>.</summary>
    public static ModpackInstance NewInstance(string dataDirectory, string name)
    {
        var root = Root(dataDirectory);
        System.IO.Directory.CreateDirectory(root);
        var stem = Regex.Replace(name.ToLowerInvariant(), "[^a-z0-9]+", "-").Trim('-');
        if (stem.Length > 40) stem = stem[..40].Trim('-');
        if (stem.Length == 0) stem = "modpack";
        for (int n = 1; ; n++)
        {
            var id = n == 1 ? stem : $"{stem}-{n}";
            var dir = Path.Combine(root, id);
            if (System.IO.Directory.Exists(dir) || File.Exists(dir)) continue;
            System.IO.Directory.CreateDirectory(Path.Combine(dir, "minecraft"));
            return new ModpackInstance { Id = id, Name = name.Trim(), Directory = dir, CreatedUtc = DateTime.UtcNow };
        }
    }

    public static void Save(ModpackInstance instance)
    {
        if (!Loaders.Contains(instance.Loader)) throw new InvalidDataException($"Unknown loader '{instance.Loader}'.");
        var file = Path.Combine(instance.Directory, "instance.json");
        var temp = file + ".tmp";
        File.WriteAllText(temp, JsonSerializer.Serialize(instance, Json), new UTF8Encoding(false));
        File.Move(temp, file, overwrite: true);
    }

    public static List<ModpackMod> Mods(ModpackInstance instance)
    {
        var dir = Path.Combine(instance.GameDirectory, "mods");
        if (!System.IO.Directory.Exists(dir)) return new List<ModpackMod>();
        return System.IO.Directory.EnumerateFiles(dir)
            .Select(f => (File: f, Name: Path.GetFileName(f)))
            .Where(f => f.Name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || f.Name.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase))
            .Select(f => new ModpackMod(f.Name.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase) ? f.Name[..^".disabled".Length] : f.Name,
                f.File, !f.Name.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)))
            .OrderBy(m => m.FileName, StringComparer.OrdinalIgnoreCase).ToList();
    }

    /// <summary>On/off is the usual launcher convention: a switched-off mod is renamed to "&lt;name&gt;.jar.disabled".</summary>
    public static void SetModEnabled(ModpackMod mod, bool enabled)
    {
        if (mod.Enabled == enabled) return;
        File.Move(mod.Path, enabled ? mod.Path[..^".disabled".Length] : mod.Path + ".disabled");
    }
}
