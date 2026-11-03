using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using Microsoft.Data.Sqlite;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public enum ImportCategory { Options, Servers, Packs, Mods, Launcher }

/// <summary>A client found on this PC. <paramref name="Basic"/>: its own settings format is not read, only the vanilla files.</summary>
public sealed record ImportSource(string Id, string Name, bool Basic, string Note, string Root, string? GameFolder);

/// <summary>Where the importer reads other clients from. Tests and the dry run point it at their own folders.</summary>
public sealed record ImportEnvironment(string Home, string AppData)
{
    public static ImportEnvironment Current => new(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData));
    public string Minecraft => Path.Combine(AppData, ".minecraft");
}

/// <summary>What the import changes. <paramref name="InGame"/>: asked from a running game, which owns thelads_config.json (it reloads
/// it right after) and whose launcher may hold its settings in memory (they wait in <see cref="ClientImport.PendingLauncherFile"/>).</summary>
public sealed record ImportTargets(IPathService Paths, IReadOnlyList<LauncherProfile> Profiles, string SharedRoot, string SettingsFile, bool InGame = false)
{
    public string ServersFile => Path.Combine(SharedRoot, "servers.dat");
    public string ServersLockFile => Path.Combine(SharedRoot, ".lads-servers.lock");
    public string PacksDirectory => Path.Combine(SharedRoot, "resourcepacks");
    public string ConfigFile => Path.Combine(Paths.SharedDirectory, "thelads_config.json");
    public string BackupsDirectory => Path.Combine(Paths.BaseDirectory, "import-backups");
}

public sealed class CategoryPlan
{
    public required ImportCategory Category { get; init; }
    public List<string> Changes { get; } = new();
    /// <summary>What is not imported and why; capped for the preview, <see cref="SkippedCount"/> has them all.</summary>
    public List<string> Skipped { get; } = new();
    public int SkippedCount { get; set; }
    /// <summary>Why nothing of this category can be imported (a malformed file, a running game); then it changes nothing.</summary>
    public string? Problem { get; set; }
    internal Dictionary<string, string> Options { get; } = new(StringComparer.Ordinal);
    internal List<ImportedServer> Servers { get; } = new();
    internal List<(string Source, string Name)> Packs { get; } = new();
    internal JsonObject? Config { get; set; }
    internal Dictionary<string, JsonNode> Launcher { get; } = new(StringComparer.Ordinal);
}

public sealed record ImportPlan(ImportSource Source, IReadOnlyDictionary<ImportCategory, CategoryPlan> Categories);

public sealed record ImportResult(string? BackupDirectory, IReadOnlyDictionary<ImportCategory, int> Applied, IReadOnlyList<string> Errors,
    bool ConfigChanged, bool RestartNeeded, bool LauncherPending);

/// <summary>
/// Imports settings from other clients (launcher Settings and the in-game dialog, which runs this through <see cref="ClientImportCli"/>):
/// detect, plan (a preview that writes nothing), apply with a timestamped backup, undo. Lives in the launcher because everything it writes
/// already has its owner here (<see cref="SharedControls"/>, <see cref="ServerListService"/>, the Lads config sync, launcher settings).
/// Other clients' folders are only read; only named settings files are opened, never accounts, tokens or sessions. Source files are
/// untrusted: size capped, typed reads, and a file that cannot be read stops its category before anything is written.
/// </summary>
public static class ClientImport
{
    public const string PendingLauncherFile = "lads-import-launcher-pending.json";
    internal const string ManifestFile = "manifest.json";
    private const long MaxSettingsBytes = 16 * 1024 * 1024;
    private const long MaxDbBytes = 64 * 1024 * 1024;
    private const int MaxListed = 500, MaxProfiles = 64;
    private const long MaxPackBytes = 1L << 30;
    private const int MaxPackFiles = 20000;
    private static readonly JsonSerializerOptions Indented = new(JsonSerializerOptions.Default) { WriteIndented = true };
    private static readonly JsonDocumentOptions JsonLimits = new() { MaxDepth = 64 };
    private static readonly HashSet<string> SensitiveNames = new(StringComparer.OrdinalIgnoreCase)
    {
        "accounts", "account", "auth", "authentication", "credentials", "tokens", "token",
        "session", "sessions", "cookies", "cookie", "essential", "oauth", "msa", "minecraft_users", "users"
    };
    private static readonly Lazy<JsonObject> Map = new(() =>
    {
        using var stream = typeof(ClientImport).Assembly.GetManifestResourceStream("client_import_map.json")
            ?? throw new InvalidOperationException("client_import_map.json is not embedded");
        return JsonNode.Parse(stream, null, new JsonDocumentOptions { MaxDepth = 64 })!.AsObject();
    });

    // --- Detect ---

    /// <summary>The clients installed here, the ones with the most we can read first.</summary>
    public static IReadOnlyList<ImportSource> Detect(ImportEnvironment env)
    {
        var found = new List<ImportSource>();
        string lunar = Path.Combine(env.Home, ".lunarclient"), mc = env.Minecraft, prism = Path.Combine(env.AppData, "PrismLauncher");
        if (Directory.Exists(Path.Combine(lunar, "settings")))
            found.Add(new("lunar", "Lunar Client", false, "Keybinds, video and audio, servers, packs, mods and HUD, launcher settings.", lunar, LunarGameFolder(lunar, mc)));
        if (Directory.Exists(Path.Combine(env.AppData, "Badlion Client")) || File.Exists(Path.Combine(mc, "badlion_settings.json"))
            || Directory.Exists(Path.Combine(mc, "BLClient-Mod-Profiles")))
            found.Add(new("badlion", "Badlion Client", false, "Keybinds, video and audio, servers, packs, mods and HUD. Badlion's launcher settings have no readable file.", mc, mc));
        if (Directory.Exists(Path.Combine(mc, "feather", "configuration")))
            found.Add(new("feather", "Feather Client", false, "Keybinds, video and audio, servers, packs, mods and HUD. Feather's launcher settings were not verified.", mc, mc));
        if (Directory.Exists(Path.Combine(mc, "labymod-neo")) || Directory.Exists(Path.Combine(mc, "laby-neo")))
            found.Add(new("labymod", "LabyMod 4", false, "Keybinds, video and audio, servers, packs, mods and HUD. LabyMod's launcher settings have no readable file.", mc, mc));
        if (DetectModrinth(env) is { } modrinth)
            found.Add(new("modrinth", "Modrinth App", true, "Basic import from the most recently modified profile, plus custom memory settings.", modrinth.Root, modrinth.Profile));
        if (Directory.Exists(prism) && MmcInstance(prism, "prismlauncher.cfg") is { } prismInst)
            found.Add(new("prism", "Prism Launcher", true, "Basic import from the last played instance, plus RAM and close-on-launch.", prism, prismInst));
        if (DetectMultiMc(env) is { } mmc)
            found.Add(new("multimc", "MultiMC", true, "Basic import from the last played instance, plus RAM and close-on-launch.", mmc.Root, mmc.Instance));
        if (DetectCurseForge(env) is { } cf)
            found.Add(new("curseforge", "CurseForge", true, "Basic import from the most recently modified instance, plus allocated RAM.", cf.Root, cf.Instance));
        if (File.Exists(Path.Combine(mc, "options.txt")))
            found.Add(new("vanilla", "Minecraft Launcher", true, "Basic import, plus RAM and keep-launcher-open.", mc, mc));
        return found;
    }

    /// <summary>Auto-detects what client, launcher root or game/instance folder <paramref name="path"/> is.</summary>
    public static ImportSource DetectCustomFolder(string path)
    {
        if (string.IsNullOrWhiteSpace(path)) throw new ArgumentException("Choose a folder to import from.");
        string full = Path.GetFullPath(path);
        if (full.StartsWith(@"\\", StringComparison.Ordinal)) throw new ArgumentException("Network paths are not supported.");
        string trimmed = Path.TrimEndingDirectorySeparator(full);
        string leaf = Path.GetFileName(trimmed);
        if (string.IsNullOrEmpty(leaf)) leaf = trimmed;

        if (Directory.Exists(Path.Combine(full, "settings", "game")) || File.Exists(Path.Combine(full, "settings", "launcher.json"))
            || leaf.Equals(".lunarclient", StringComparison.OrdinalIgnoreCase))
        {
            string multiver = Path.Combine(full, "offline", "multiver");
            string fallback = HasGameFiles(multiver) ? multiver : ProfileFoldersFrom(Path.Combine(full, "profiles")).FirstOrDefault(HasGameFiles) ?? full;
            return new("lunar", $"Lunar Client ({leaf})", false, "Keybinds, video and audio, servers, packs, mods and HUD, launcher settings.",
                full, LunarGameFolder(full, fallback));
        }

        if (leaf.Equals("BLClient-Mod-Profiles", StringComparison.OrdinalIgnoreCase) || leaf.Equals("BLClient", StringComparison.OrdinalIgnoreCase))
        {
            string mc = Directory.GetParent(trimmed)?.FullName ?? full;
            return new("badlion", $"Badlion Client ({leaf})", false, "Keybinds, video and audio, servers, packs, mods and HUD. Badlion's launcher settings have no readable file.", mc, mc);
        }
        if (Directory.Exists(Path.Combine(full, "BLClient-Mod-Profiles")) || File.Exists(Path.Combine(full, "badlion_settings.json")))
            return new("badlion", $"Badlion Client ({leaf})", false, "Keybinds, video and audio, servers, packs, mods and HUD. Badlion's launcher settings have no readable file.", full, full);

        if (Directory.Exists(Path.Combine(full, "feather", "configuration")))
            return new("feather", $"Feather Client ({leaf})", false, "Keybinds, video and audio, servers, packs, mods and HUD. Feather's launcher settings were not verified.", full, full);
        if (Directory.Exists(Path.Combine(full, "configuration", "profiles")) || File.Exists(Path.Combine(full, "configuration", "current.profile")))
        {
            string mc = Directory.GetParent(trimmed)?.FullName ?? full;
            string game = HasGameFiles(full) ? full : HasGameFiles(mc) ? mc : full;
            return new("feather", $"Feather Client ({leaf})", false, "Keybinds, video and audio, servers, packs, mods and HUD. Feather's launcher settings were not verified.", full, game);
        }

        if (Directory.Exists(Path.Combine(full, "labymod-neo")) || Directory.Exists(Path.Combine(full, "laby-neo")))
            return new("labymod", $"LabyMod 4 ({leaf})", false, "Keybinds, video and audio, servers, packs, mods and HUD. LabyMod's launcher settings have no readable file.", full, full);
        if (leaf.Equals("labymod-neo", StringComparison.OrdinalIgnoreCase) || leaf.Equals("laby-neo", StringComparison.OrdinalIgnoreCase)
            || Directory.Exists(Path.Combine(full, "configs", "labymod")) || File.Exists(Path.Combine(full, "hud", "widgets.json")))
        {
            string mc = Directory.GetParent(trimmed)?.FullName ?? full;
            string game = HasGameFiles(full) ? full : HasGameFiles(mc) ? mc : full;
            return new("labymod", $"LabyMod 4 ({leaf})", false, "Keybinds, video and audio, servers, packs, mods and HUD. LabyMod's launcher settings have no readable file.", full, game);
        }

        if (File.Exists(Path.Combine(full, "prismlauncher.cfg")))
            return new("prism", $"Prism Launcher ({leaf})", true, "Basic import from the last played instance, plus RAM and close-on-launch.", full, MmcInstance(full, "prismlauncher.cfg"));
        if (File.Exists(Path.Combine(full, "multimc.cfg")))
            return new("multimc", $"MultiMC ({leaf})", true, "Basic import from the last played instance, plus RAM and close-on-launch.", full, MmcInstance(full, "multimc.cfg"));
        if (File.Exists(Path.Combine(full, "instance.cfg")))
        {
            string? parent = Directory.GetParent(trimmed)?.FullName;
            string? grand = parent != null ? Directory.GetParent(parent)?.FullName : null;
            bool isMmc = (grand != null && File.Exists(Path.Combine(grand, "multimc.cfg"))) || (parent != null && File.Exists(Path.Combine(parent, "multimc.cfg")));
            string root = grand != null && (File.Exists(Path.Combine(grand, "multimc.cfg")) || File.Exists(Path.Combine(grand, "prismlauncher.cfg"))) ? grand : full;
            string game = new[] { Path.Combine(full, "minecraft"), Path.Combine(full, ".minecraft") }.FirstOrDefault(Directory.Exists) ?? full;
            return new(isMmc ? "multimc" : "prism", $"{(isMmc ? "MultiMC" : "Prism Launcher")} ({leaf})", true,
                "Basic import from this instance, plus RAM and close-on-launch.", root, game);
        }

        if (File.Exists(Path.Combine(full, "app.db"))
            || ((leaf.Equals("ModrinthApp", StringComparison.OrdinalIgnoreCase) || leaf.Equals("com.modrinth.theseus", StringComparison.OrdinalIgnoreCase))
                && Directory.Exists(Path.Combine(full, "profiles"))))
            return new("modrinth", $"Modrinth App ({leaf})", true, "Basic import from the most recently modified profile, plus custom memory settings.", full, LatestModrinthProfile(full));
        if (File.Exists(Path.Combine(full, "profile.json")) || IsModrinthProfileSubfolder(trimmed, out var detectedModrinthRoot))
        {
            string mRoot = IsModrinthProfileSubfolder(trimmed, out detectedModrinthRoot) && detectedModrinthRoot != null ? detectedModrinthRoot : full;
            return new("modrinth", $"Modrinth ({leaf})", true, "Basic import from this Modrinth profile, plus custom memory settings.", mRoot, full);
        }

        if (File.Exists(Path.Combine(full, "minecraftinstance.json")))
            return new("curseforge", $"CurseForge ({leaf})", true, "Basic import from this CurseForge instance, plus allocated RAM.", full, full);
        if (LatestCurseForgeInstance(Path.Combine(full, "Instances")) is { } cfSub)
            return new("curseforge", $"CurseForge ({Path.GetFileName(cfSub)})", true, "Basic import from the most recently modified instance, plus allocated RAM.", full, cfSub);
        if (Directory.Exists(full) && SafeSubdirectories(full, 64).Any(d => File.Exists(Path.Combine(d, "minecraftinstance.json")))
            && LatestCurseForgeInstance(full) is { } cfDirect)
            return new("curseforge", $"CurseForge ({Path.GetFileName(cfDirect)})", true, "Basic import from the most recently modified instance, plus allocated RAM.", full, cfDirect);

        string gameFolder = !HasGameFiles(full)
            ? new[] { Path.Combine(full, ".minecraft"), Path.Combine(full, "minecraft") }.FirstOrDefault(HasGameFiles) ?? full
            : full;
        if (File.Exists(Path.Combine(gameFolder, "launcher_profiles.json")))
            return new("vanilla", $"Minecraft ({leaf})", true, "Basic import, plus RAM and keep-launcher-open.", gameFolder, gameFolder);

        return new("custom", leaf, true,
            "Basic import only: options.txt, servers.dat and the resourcepacks folder. This client's own settings format is not read.", full, gameFolder);
    }

    /// <summary>Any client or game folder the player picks (auto-detects known clients; otherwise reads the vanilla files).</summary>
    public static ImportSource FromFolder(string folder) => DetectCustomFolder(folder);

    private static bool IsModrinthProfileSubfolder(string trimmed, out string? appRoot)
    {
        appRoot = null;
        if (Directory.GetParent(trimmed) is not { } profilesDir || !profilesDir.Name.Equals("profiles", StringComparison.OrdinalIgnoreCase))
            return false;
        if (Directory.GetParent(profilesDir.FullName) is not { } parent) return false;
        if (!File.Exists(Path.Combine(parent.FullName, "app.db"))
            && !parent.Name.Equals("ModrinthApp", StringComparison.OrdinalIgnoreCase)
            && !parent.Name.Equals("com.modrinth.theseus", StringComparison.OrdinalIgnoreCase))
            return false;
        appRoot = parent.FullName;
        return true;
    }

    private static string LunarGameFolder(string lunar, string fallback)
    {
        // launcher.json names the folder Lunar plays in; an entry that is not a plain absolute path is ignored.
        var dir = (TryParseObject(Path.Combine(lunar, "settings", "launcher.json"))?["settings"] as JsonObject)?["gameDirectory"] is JsonValue v
            && v.TryGetValue<string>(out var s) ? s : null;
        return dir != null && Path.IsPathFullyQualified(dir) && !dir.StartsWith(@"\\", StringComparison.Ordinal) && Directory.Exists(dir) ? dir : fallback;
    }

    private static IEnumerable<string> SafeSubdirectories(string folder, int max = 256)
    {
        if (!Directory.Exists(folder)) return Array.Empty<string>();
        try { return Directory.EnumerateDirectories(folder).Where(d => !SafeFileOps.IsLink(d)).Take(max).ToList(); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return Array.Empty<string>(); }
    }

    private static bool HasGameFiles(string dir) =>
        Directory.Exists(dir) && (File.Exists(Path.Combine(dir, "options.txt")) || File.Exists(Path.Combine(dir, "servers.dat"))
            || Directory.Exists(Path.Combine(dir, "resourcepacks")));

    private static bool HasInstanceContent(string dir, string? markerFile = null) =>
        HasGameFiles(dir) || (markerFile != null && File.Exists(Path.Combine(dir, markerFile)));

    private static DateTime InstanceTimestamp(string gameDir, string? instanceDir = null)
    {
        string opt = Path.Combine(gameDir, "options.txt");
        if (File.Exists(opt)) return File.GetLastWriteTimeUtc(opt);
        string srv = Path.Combine(gameDir, "servers.dat");
        if (File.Exists(srv)) return File.GetLastWriteTimeUtc(srv);
        string metaDir = instanceDir ?? gameDir;
        foreach (var meta in new[] { "minecraftinstance.json", "profile.json", "instance.cfg" })
        {
            string file = Path.Combine(metaDir, meta);
            if (File.Exists(file)) return File.GetLastWriteTimeUtc(file);
        }
        return Directory.GetLastWriteTimeUtc(metaDir);
    }

    private static string? MmcInstance(string root, string cfgName)
    {
        try
        {
            string instancesDir = Path.Combine(root, "instances");
            string cfgFile = Path.Combine(root, cfgName);
            if (File.Exists(cfgFile))
            {
                var cfg = ParseIni(Encoding.UTF8.GetString(ReadBounded(cfgFile, 1024 * 1024)));
                if (cfg.TryGetValue("InstanceDir", out var customDir) && customDir.Length > 0 && !customDir.StartsWith(@"\\", StringComparison.Ordinal))
                {
                    string resolved = Path.IsPathFullyQualified(customDir) ? customDir : Path.GetFullPath(Path.Combine(root, customDir));
                    if (Directory.Exists(resolved)) instancesDir = resolved;
                }
            }
            return SafeSubdirectories(instancesDir)
                .SelectMany(i => new[] { (Instance: i, Game: Path.Combine(i, "minecraft")), (Instance: i, Game: Path.Combine(i, ".minecraft")) })
                .Where(x => HasGameFiles(x.Game))
                .OrderByDescending(x => File.Exists(Path.Combine(x.Game, "options.txt")) ? 1 : 0)
                .ThenByDescending(x => InstanceTimestamp(x.Game, x.Instance))
                .Select(x => x.Game)
                .FirstOrDefault();
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException) { return null; }
    }

    private static (string Root, string Instance)? DetectMultiMc(ImportEnvironment env)
    {
        foreach (var root in new[] { Path.Combine(env.AppData, "MultiMC"), Path.Combine(env.Home, "MultiMC"), Path.Combine(env.Home, "scoop", "apps", "multimc", "current") })
            if (Directory.Exists(root) && MmcInstance(root, "multimc.cfg") is { } instance)
                return (root, instance);
        return null;
    }

    private static (string Root, string Profile)? DetectModrinth(ImportEnvironment env)
    {
        (string Root, string Profile, DateTime When)? best = null;
        foreach (var root in new[] { Path.Combine(env.AppData, "ModrinthApp"), Path.Combine(env.AppData, "com.modrinth.theseus") })
        {
            if (!Directory.Exists(root)) continue;
            if (LatestModrinthProfile(root) is not { } profile) continue;
            var when = InstanceTimestamp(profile);
            if (best == null || when > best.Value.When) best = (root, profile, when);
        }
        return best is { } b ? (b.Root, b.Profile) : null;
    }

    private static string? LatestModrinthProfile(string root)
    {
        var profileRoots = new List<string> { Path.Combine(root, "profiles") };
        if (TryModrinthCustomDir(Path.Combine(root, "app.db")) is { } custom && !custom.StartsWith(@"\\", StringComparison.Ordinal))
            profileRoots.Add(Path.Combine(custom, "profiles"));
        return profileRoots.Distinct(StringComparer.OrdinalIgnoreCase)
            .SelectMany(r => SafeSubdirectories(r))
            .Where(d => HasInstanceContent(d, "profile.json"))
            .OrderByDescending(d => InstanceTimestamp(d))
            .FirstOrDefault();
    }

    private static (string Root, string Instance)? DetectCurseForge(ImportEnvironment env)
    {
        var roots = new List<string>();
        if (CurseForgeStorageRoot(env.AppData) is { } custom && !custom.StartsWith(@"\\", StringComparison.Ordinal) && Directory.Exists(custom))
            roots.Add(custom);
        roots.Add(Path.Combine(env.Home, "curseforge", "minecraft"));
        foreach (var root in roots.Distinct(StringComparer.OrdinalIgnoreCase))
            if (LatestCurseForgeInstance(Path.Combine(root, "Instances")) is { } instance)
                return (root, instance);
        return null;
    }

    private static string? LatestCurseForgeInstance(string instancesDir) =>
        SafeSubdirectories(instancesDir)
            .Where(d => HasInstanceContent(d, "minecraftinstance.json"))
            .OrderByDescending(d => InstanceTimestamp(d))
            .FirstOrDefault();

    private static string? CurseForgeStorageRoot(string appData)
    {
        var file = Path.Combine(appData, "CurseForge", "storage.json");
        if (TryParseObject(file)?["minecraft-settings"] is not { } node) return null;
        try
        {
            JsonObject? inner = node switch
            {
                JsonObject o => o,
                JsonValue v when v.TryGetValue<string>(out var s) => JsonNode.Parse(s, null, JsonLimits) as JsonObject,
                _ => null
            };
            return inner?["minecraftRoot"] is JsonValue r && r.TryGetValue<string>(out var path) && Path.IsPathFullyQualified(path) ? path : null;
        }
        catch (JsonException) { return null; }
    }

    // --- Plan (reads only) ---

    public static ImportPlan Plan(ImportSource source, ImportTargets targets)
    {
        var categories = new Dictionary<ImportCategory, CategoryPlan>();
        void Add(ImportCategory category, Action<CategoryPlan> fill)
        {
            var plan = categories[category] = new CategoryPlan { Category = category };
            try { fill(plan); }
            catch (Exception e) // whatever an untrusted file does to a parser, it costs its category and nothing else
            {
                Fail(plan, e.Message);
            }
        }
        Add(ImportCategory.Options, p => PlanOptions(source, targets, p));
        Add(ImportCategory.Servers, p => PlanServers(source, targets, p));
        Add(ImportCategory.Packs, p => PlanPacks(source, targets, p));
        Add(ImportCategory.Mods, p => PlanMods(source, targets, p));
        Add(ImportCategory.Launcher, p => PlanLauncher(source, targets, p));
        return new ImportPlan(source, categories);
    }

    private static void Fail(CategoryPlan plan, string problem)
    {
        plan.Problem = problem;
        plan.Changes.Clear(); plan.Options.Clear(); plan.Servers.Clear(); plan.Packs.Clear(); plan.Launcher.Clear(); plan.Config = null;
    }

    private static string Quote(string file) => "'" + Path.GetFileName(file) + "'";

    /// <summary>A settings file, or an InvalidDataException when it is over the cap. Opened for reading only, alongside a running client.</summary>
    private static byte[] ReadBounded(string file, long max = MaxSettingsBytes)
    {
        using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        if (stream.Length > max) throw new InvalidDataException($"{Quote(file)} is too large to be a settings file.");
        var bytes = new byte[stream.Length];
        stream.ReadExactly(bytes);
        return bytes;
    }

    private static JsonObject ParseObject(byte[] bytes, string file)
    {
        try { return JsonNode.Parse(bytes, null, JsonLimits) as JsonObject ?? throw new InvalidDataException($"{Quote(file)} is not in the expected format."); }
        catch (JsonException) { throw new InvalidDataException($"{Quote(file)} is damaged or in an unknown format."); }
    }

    private static JsonObject? TryParseObject(string file)
    {
        try { return File.Exists(file) ? ParseObject(ReadBounded(file), file) : null; }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException) { return null; }
    }

    // --- Options ---

    /// <summary>Settings carried over besides the vanilla key binds, all in the newer versions' names and formats; the range is checked
    /// (booleans: empty). 1.8.9-only formats (fancyGraphics) and anything naming a server or account are not on it.</summary>
    private static readonly Dictionary<string, (double Min, double Max)?> ImportedOptions = new(StringComparer.Ordinal)
    {
        ["fov"] = (-1, 1), ["mouseSensitivity"] = (0, 1), ["gamma"] = (0, 15), ["renderDistance"] = (2, 64), ["simulationDistance"] = (2, 64),
        ["maxFps"] = (10, 260), ["guiScale"] = (0, 16), ["particles"] = (0, 2), ["mipmapLevels"] = (0, 4), ["chatOpacity"] = (0, 1), ["chatScale"] = (0, 1),
        ["chatWidth"] = (0, 1), ["chatHeightFocused"] = (0, 1), ["chatHeightUnfocused"] = (0, 1), ["chatLineSpacing"] = (0, 1), ["textBackgroundOpacity"] = (0, 1),
        ["biomeBlendRadius"] = (0, 7), ["entityDistanceScaling"] = (0.5, 5), ["fovEffectScale"] = (0, 1), ["screenEffectScale"] = (0, 1), ["attackIndicator"] = (0, 2),
        ["soundCategory_master"] = (0, 1), ["soundCategory_music"] = (0, 1), ["soundCategory_record"] = (0, 1), ["soundCategory_weather"] = (0, 1),
        ["soundCategory_block"] = (0, 1), ["soundCategory_hostile"] = (0, 1), ["soundCategory_neutral"] = (0, 1), ["soundCategory_player"] = (0, 1),
        ["soundCategory_ambient"] = (0, 1), ["soundCategory_voice"] = (0, 1),
        ["enableVsync"] = null, ["bobView"] = null, ["invertYMouse"] = null, ["entityShadows"] = null, ["ao"] = null, ["autoJump"] = null, ["toggleCrouch"] = null,
        ["toggleSprint"] = null, ["rawMouseInput"] = null, ["discrete_mouse_scroll"] = null, ["chatColors"] = null, ["chatLinks"] = null, ["chatLinksPrompt"] = null,
        ["autoSuggestions"] = null, ["advancedItemTooltips"] = null, ["pauseOnLostFocus"] = null, ["heldItemTooltips"] = null,
    };

    private static void PlanOptions(ImportSource source, ImportTargets targets, CategoryPlan plan)
    {
        if (source.GameFolder == null) return;
        string lc = Path.Combine(source.GameFolder, "optionsLC.txt"), txt = Path.Combine(source.GameFolder, "options.txt");
        Dictionary<string, string> raw;
        bool legacy, degrees = false;
        if (source.Id == "lunar" && File.Exists(lc))
        {
            raw = new(StringComparer.Ordinal);
            foreach (var (key, node) in ParseObject(ReadBounded(lc), lc))
                if (node is JsonValue value && value.TryGetValue<string>(out var text)) raw[key] = text;
            legacy = false;
            degrees = true; // Lunar's own file holds the field of view in degrees
        }
        else if (File.Exists(txt))
        {
            var bytes = ReadBounded(txt, 4 * 1024 * 1024);
            if (bytes.Contains((byte)0)) throw new InvalidDataException($"{Quote(txt)} is damaged.");
            raw = GameOptionsService.ParseOptions(Encoding.UTF8.GetString(bytes));
            legacy = !raw.ContainsKey("version"); // 1.8.9 writes no data version; its key codes convert below
        }
        else return;
        if (raw.Count == 0 || !raw.Keys.Any(k => ImportedOptions.ContainsKey(k) || k.StartsWith("key_", StringComparison.Ordinal)))
            throw new InvalidDataException($"The options in {source.Name}'s folder are damaged or in an unknown format.");

        var vanilla = GameControls.VanillaKeys("1.8.9").Concat(GameControls.VanillaKeys("26.3")).Select(k => "key_" + k.Id).ToHashSet(StringComparer.Ordinal);
        var current = GameOptionsFile.Parse(File.Exists(targets.Paths.SharedOptionsFile) ? File.ReadAllText(targets.Paths.SharedOptionsFile) : null);
        int leftAlone = 0;
        foreach (var (stored, value) in raw)
        {
            string key = legacy ? SharedControls.OptionKey(stored, false) : stored;
            string? converted;
            if (key.StartsWith("key_", StringComparison.Ordinal))
            {
                if (!vanilla.Contains(key)) { Skip(plan, $"{key[4..]}: another mod's key bind"); continue; }
                converted = SharedControls.FromVersion(key, value);
                if (converted == null) { Skip(plan, $"{key[4..]}: no matching key"); continue; }
            }
            else if (!ImportedOptions.TryGetValue(key, out var range)) { leftAlone++; continue; }
            else
            {
                converted = ConvertOption(key, value, range, degrees);
                if (converted == null) { Skip(plan, $"{key}: unusable value"); continue; }
            }
            plan.Options[key] = converted;
            if (current.Get(key) != converted) plan.Changes.Add($"{key} = {converted}");
        }
        if (leftAlone > 0) Skip(plan, $"{leftAlone} other settings (language, last server, version-specific graphics...) are left as they are");
        // Unchanged values need no write; only what differs goes to the profiles.
        foreach (var key in plan.Options.Keys.Where(k => current.Get(k) == plan.Options[k]).ToList()) plan.Options.Remove(key);
    }

    private static string? ConvertOption(string key, string value, (double Min, double Max)? range, bool degrees)
    {
        value = value.Trim();
        if (range == null)
        {
            if (key == "ao" && int.TryParse(value, out int ao)) return ao is >= 0 and <= 2 ? (ao > 0 ? "true" : "false") : null; // 1.8.9: 0 off, 1 and 2 on
            return value is "true" or "false" ? value : null;
        }
        if (!double.TryParse(value, NumberStyles.Float, CultureInfo.InvariantCulture, out double number) || double.IsNaN(number)) return null;
        if (key == "fov" && degrees)
            return number is >= 30 and <= 110 ? GameControls.FovStored((int)Math.Round(number)) : null;
        return number >= range.Value.Min && number <= range.Value.Max ? value : null;
    }

    private static void Skip(CategoryPlan plan, string line)
    {
        plan.SkippedCount++;
        if (plan.Skipped.Count < MaxListed) plan.Skipped.Add(line);
    }

    // --- Servers ---

    private static readonly Regex ServerAddress = new(@"^[^\s/\\\p{C}]{1,255}$", RegexOptions.CultureInvariant);

    /// <summary>The game folder's list, then the client's other profiles (a Lunar profile keeps its own). The game folder's
    /// list damaged stops the category; another profile's is only skipped.</summary>
    private static void PlanServers(ImportSource source, ImportTargets targets, CategoryPlan plan)
    {
        var files = new List<string>();
        if (source.GameFolder != null) files.Add(Path.Combine(source.GameFolder, "servers.dat"));
        files.AddRange(ProfileFolders(source).Select(d => Path.Combine(d, "servers.dat")));
        files = files.Where(File.Exists).Distinct(StringComparer.OrdinalIgnoreCase).ToList();
        var known = new HashSet<string>(StringComparer.Ordinal);
        foreach (var existing in ExistingServers(targets)) known.Add(existing);
        foreach (var file in files)
        {
            if (SafeFileOps.PathsEqual(file, targets.ServersFile)) { Skip(plan, $"{source.Name}'s server list is already the shared list"); continue; }
            ServerListFile list;
            try { list = ServerListFile.Parse(ReadBounded(file, Nbt.ServerListMaxBytes)); }
            catch (Exception)
            {
                if (source.GameFolder != null && SafeFileOps.PathsEqual(Path.GetDirectoryName(file)!, source.GameFolder))
                    throw new InvalidDataException($"{Quote(file)} is damaged.");
                Skip(plan, $"{Quote(file)} of a profile is damaged"); continue;
            }
            foreach (var entry in list.Entries)
            {
                string ip = entry.Ip.Trim();
                if (entry.Hidden || ip.Length == 0) continue;
                if (!ServerAddress.IsMatch(ip)) { Skip(plan, "A server with an unusable address"); continue; }
                if (!known.Add(ip.ToLowerInvariant())) continue;
                string name = new string(entry.Name.Where(c => !char.IsControl(c)).Take(128).ToArray());
                string? icon = entry.Raw["icon"] is NbtString { Value.Length: > 0 and <= 256 * 1024 } i ? i.Value : null;
                plan.Servers.Add(new ImportedServer(name.Length > 0 ? name : ip, ip, icon, entry.Raw["acceptTextures"] is NbtNumber { RawValue: 0 }));
                plan.Changes.Add($"Add server {(name.Length > 0 ? name : ip)}");
            }
        }
    }

    private static IEnumerable<string> ExistingServers(ImportTargets targets)
    {
        var target = new ServerListTarget("shared", targets.ServersFile, targets.ServersLockFile);
        return ServerListService.Read(target).Select(e => e.Ip.Trim().ToLowerInvariant());
    }

    /// <summary>Lunar's per-version game folders (.lunarclient/profiles/*); no other client has them.</summary>
    private static IEnumerable<string> ProfileFolders(ImportSource source) =>
        source.Id == "lunar" ? ProfileFoldersFrom(Path.Combine(source.Root, "profiles")) : Array.Empty<string>();

    private static IEnumerable<string> ProfileFoldersFrom(string profiles) =>
        Directory.Exists(profiles) ? SafeSubdirectories(profiles, MaxProfiles) : Array.Empty<string>();

    // --- Packs ---

    private static void PlanPacks(ImportSource source, ImportTargets targets, CategoryPlan plan)
    {
        var folders = new List<string>();
        if (source.GameFolder != null) folders.Add(Path.Combine(source.GameFolder, "resourcepacks"));
        folders.AddRange(ProfileFolders(source).Select(d => Path.Combine(d, "resourcepacks")));
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var folder in folders.Where(Directory.Exists).Distinct(StringComparer.OrdinalIgnoreCase))
        {
            if (SafeFileOps.PathsEqual(folder, targets.PacksDirectory)) continue; // already the shared folder
            foreach (var path in Directory.EnumerateFileSystemEntries(folder).Take(MaxListed))
            {
                string name = Path.GetFileName(path);
                bool isDirectory = Directory.Exists(path);
                if (!isDirectory && !name.EndsWith(".zip", StringComparison.OrdinalIgnoreCase)) continue;
                if (isDirectory && !File.Exists(Path.Combine(path, "pack.mcmeta"))) continue;
                if (!SafeFileOps.IsPlainFileName(name) || SafeFileOps.IsLink(path)) { Skip(plan, $"{name}: not a plain pack"); continue; }
                if (!seen.Add(name)) continue;
                string target = Path.Combine(targets.PacksDirectory, name);
                if (File.Exists(target) || Directory.Exists(target)) { Skip(plan, $"{name}: already in the shared packs folder"); continue; }
                if (TooLarge(path)) { Skip(plan, $"{name}: too large to copy"); continue; }
                plan.Packs.Add((path, name));
                plan.Changes.Add($"Copy pack {name}");
            }
        }
    }

    /// <summary>A pack folder's content; links and junctions in it, which could reach outside it, are not.</summary>
    private static readonly EnumerationOptions PackEntries = new() { RecurseSubdirectories = true, AttributesToSkip = FileAttributes.ReparsePoint };

    private static bool TooLarge(string path)
    {
        if (File.Exists(path)) return new FileInfo(path).Length > MaxPackBytes;
        long total = 0; int count = 0;
        foreach (var file in new DirectoryInfo(path).EnumerateFiles("*", PackEntries))
        {
            total += file.Length;
            if (++count > MaxPackFiles || total > MaxPackBytes) return true;
        }
        return false;
    }

    // --- Mods and HUD ---

    private static void PlanMods(ImportSource source, ImportTargets targets, CategoryPlan plan)
    {
        JsonObject? data = source.Id switch
        {
            "lunar" => LunarMods(source),
            "badlion" => BadlionMods(source),
            "feather" => FeatherMods(source),
            "labymod" => LabyModMods(source),
            _ => null
        };
        if (data == null)
        {
            if (!source.Basic) Skip(plan, $"{source.Name}'s mod settings were not found");
            return;
        }
        if (!targets.InGame && targets.Profiles.Any(p => RunningGameMarker.IsRunning(targets.Paths.GetProfileDirectory(p))))
            throw new InvalidOperationException("A Lads game is running. Close it, or import from inside the game (Lads settings).");
        var map = Map.Value[source.Id]!.AsObject();
        var config = ReadConfig(targets.ConfigFile);
        var modules = EnsureObject(config, "modules");
        var hud = EnsureObject(config, "hud");
        var ignore = (map["ignore"] as JsonArray ?? new()).Select(n => n!.GetValue<string>()).ToHashSet(StringComparer.Ordinal);
        var mapped = new HashSet<string>(StringComparer.Ordinal);
        foreach (var entry in map["mods"]!.AsArray().Select(n => n!.AsObject()))
        {
            string from = entry["from"]!.GetValue<string>(), to = entry["to"]!.GetValue<string>();
            mapped.Add(from);
            if (data[from] is not JsonObject mod) continue;
            var used = new HashSet<string>(StringComparer.Ordinal);
            var enablePath = entry.ContainsKey("enable") ? entry["enable"]?.GetValue<string>() : map["enable"]!.GetValue<string>();
            if (enablePath != null && ToBool(Walk(mod, enablePath)) is { } enabled)
                SetModule(plan, modules, to, "enabled", JsonValue.Create(enabled), $"{to}: {(enabled ? "on" : "off")}");
            bool isHud = entry["hud"]?.GetValue<bool>() == true;
            var anchorObject = entry["pos"] is JsonValue pos ? mod[pos.GetValue<string>()] as JsonObject : mod;
            if (entry["pos"] is JsonValue posKey) used.Add(posKey.GetValue<string>());
            if (entry["note"] is JsonValue note && mod.Count > 0) Skip(plan, $"{from}: {note.GetValue<string>()}");
            var options = new List<(string Path, string Option, string Conv)>();
            if (entry["opts"] is JsonArray own) options.AddRange(own.Select(Triple));
            if (isHud && entry["common"]?.GetValue<bool>() != false && map["common"] is JsonArray common) options.AddRange(common.Select(Triple));
            foreach (var (path, option, conv) in options)
            {
                used.Add(path.Split('/')[^1]);
                if (path.Contains('/') && path.Split('/')[0] != "options") used.Add(path.Split('/')[0]);
                var holder = isHud && map["common"] is JsonArray c && c.Any(t => t![0]!.GetValue<string>() == path) ? anchorObject : mod;
                if (holder == null || Walk(holder, path) is not { } node) continue;
                if (ConvertValue(node, conv) is { } value) SetOption(plan, modules, to, option, value);
                else Skip(plan, $"{from}: {path}: unusable value");
            }
            if (isHud && anchorObject != null && source.Id != "badlion" && ReadAnchor(source.Id, anchorObject) is { } anchor)
                SetAnchor(plan, hud, to, anchor);
            ListUnmapped(plan, from, mod, used, ignore);
        }
        if (map["boxes"] is JsonObject boxMap && data["boxes"] is JsonArray boxes)
        {
            foreach (var box in boxes.Take(256).OfType<JsonObject>())
            {
                if (box["identifier"] is not JsonValue id || !id.TryGetValue<string>(out var identifier)) continue;
                if (boxMap[identifier] is not JsonValue to) { Skip(plan, $"HUD position of {identifier}: no Lads module"); continue; }
                if (BadlionAnchor(box) is { } anchor) SetAnchor(plan, hud, to.GetValue<string>(), anchor);
            }
        }
        bool badlion = source.Id == "badlion";
        foreach (var (key, node) in data.Take(MaxListed))
            if (node is JsonObject obj && !mapped.Contains(key) && key != "boxes" && (!badlion || obj["enabled"] is JsonValue))
                Skip(plan, $"{key}: no matching Lads module");
        if (plan.Changes.Count > 0) plan.Config = config;
    }

    private static (string, string, string) Triple(JsonNode? node) =>
        (node![0]!.GetValue<string>(), node[1]!.GetValue<string>(), node[2]!.GetValue<string>());

    private static void ListUnmapped(CategoryPlan plan, string from, JsonObject mod, HashSet<string> used, HashSet<string> ignore)
    {
        foreach (var (key, _) in mod)
            if (!ignore.Contains(key) && !used.Contains(key)) Skip(plan, $"{from}: {key} has no Lads option");
        if (mod["options"] is JsonObject options)
            foreach (var (key, _) in options)
                if (!used.Contains(key)) Skip(plan, $"{from}: {key} has no Lads option");
    }

    private static JsonObject? LunarMods(ImportSource source)
    {
        var game = Path.Combine(source.Root, "settings", "game");
        string name = "Default";
        // profile_manager.json names the active profile; a name that is not one plain folder falls back to Default.
        if (File.Exists(Path.Combine(game, "profile_manager.json")))
        {
            var bytes = ReadBounded(Path.Combine(game, "profile_manager.json"));
            try
            {
                if (JsonNode.Parse(bytes, null, JsonLimits) is JsonArray list)
                    foreach (var profile in list.OfType<JsonObject>())
                        if (ToBool(profile["active"]) == true && profile["name"] is JsonValue n && n.TryGetValue<string>(out var active) && SafeFileOps.IsPlainFileName(active))
                            name = active;
            }
            catch (JsonException) { }
        }
        var file = Path.Combine(game, name, "mods.json");
        return File.Exists(file) ? ParseObject(ReadBounded(file), file) : null;
    }

    private static JsonObject? BadlionMods(ImportSource source)
    {
        var folder = Path.Combine(source.Root, "BLClient-Mod-Profiles");
        string name = "Default";
        if (TryParseObject(Path.Combine(source.Root, "badlion_settings.json"))?["activeProfile"] is JsonValue v && v.TryGetValue<string>(out var active)
            && SafeFileOps.IsPlainFileName(active + ".zip") && File.Exists(Path.Combine(folder, active + ".zip")))
            name = active;
        var zip = Path.Combine(folder, name + ".zip");
        if (!File.Exists(zip)) return null;
        if (new FileInfo(zip).Length > MaxSettingsBytes) throw new InvalidDataException($"{Quote(zip)} is too large.");
        using var archive = ZipFile.OpenRead(zip);
        var data = archive.GetEntry("data.json") ?? throw new InvalidDataException($"{Quote(zip)} has no mod settings.");
        if (data.Length > MaxSettingsBytes) throw new InvalidDataException($"{Quote(zip)} is too large.");
        using var stream = data.Open();
        using var buffer = new MemoryStream();
        stream.CopyTo(buffer);
        return ParseObject(buffer.ToArray(), zip);
    }

    private static JsonObject? FeatherMods(ImportSource source)
    {
        var folder = Directory.Exists(Path.Combine(source.Root, "feather", "configuration"))
            ? Path.Combine(source.Root, "feather", "configuration")
            : Path.Combine(source.Root, "configuration");
        string name = "default";
        var current = Path.Combine(folder, "current.profile");
        if (File.Exists(current))
        {
            var chosen = Encoding.UTF8.GetString(ReadBounded(current, 4096)).Trim();
            if (SafeFileOps.IsPlainFileName(chosen + ".json")) name = chosen;
        }
        var file = Path.Combine(folder, "profiles", name + ".json");
        return File.Exists(file) ? ParseObject(ReadBounded(file), file) : null;
    }

    private static JsonObject? LabyModMods(ImportSource source)
    {
        var laby = new[] { Path.Combine(source.Root, "labymod-neo"), Path.Combine(source.Root, "laby-neo"), source.Root }
            .FirstOrDefault(d => Directory.Exists(Path.Combine(d, "configs")) || Directory.Exists(Path.Combine(d, "hud")));
        if (laby == null) return null;

        var known = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        var compact = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var entry in Map.Value["labymod"]!["mods"]!.AsArray().Select(n => n!.AsObject()))
        {
            string from = entry["from"]!.GetValue<string>();
            known.TryAdd(from, from);
            compact.TryAdd(from.Replace("_", "").Replace("-", ""), from);
        }

        string Canonical(string raw)
        {
            string stripped = raw.StartsWith("labymod:", StringComparison.OrdinalIgnoreCase) ? raw[8..] : raw;
            if (stripped.IndexOf(':') is > 0 and var colon)
            {
                string ns = stripped[..colon], local = stripped[(colon + 1)..];
                if (ns.Equals(local, StringComparison.OrdinalIgnoreCase) || known.ContainsKey(local) || compact.ContainsKey(local.Replace("_", "").Replace("-", "")))
                    stripped = local;
                else if (known.ContainsKey(ns) || compact.ContainsKey(ns.Replace("_", "").Replace("-", "")))
                    stripped = ns;
            }
            if (known.TryGetValue(stripped, out var exact)) return exact;
            if (compact.TryGetValue(stripped.Replace("_", "").Replace("-", ""), out var norm)) return norm;
            return stripped;
        }

        var result = new JsonObject();
        string configsDir = Path.Combine(laby, "configs");
        if (Directory.Exists(configsDir))
        {
            foreach (var dir in SafeSubdirectories(configsDir, MaxProfiles))
            {
                string ns = Path.GetFileName(dir);
                if (!SafeFileOps.IsPlainFileName(ns) || SensitiveNames.Contains(ns)) continue;
                string settingsFile = Path.Combine(dir, "settings.json");
                if (!File.Exists(settingsFile)) continue;
                var parsed = ParseObject(ReadBounded(settingsFile), settingsFile);
                if (ns.Equals("labymod", StringComparison.OrdinalIgnoreCase))
                {
                    foreach (var (key, node) in parsed)
                    {
                        if (SensitiveNames.Contains(key) || node is not JsonObject child) continue;
                        if (key is "ingame" or "appearance" or "multiplayer" or "other" or "hotkeys" && child["enabled"] is not JsonValue)
                        {
                            foreach (var (subKey, subNode) in child)
                                if (!SensitiveNames.Contains(subKey) && subNode is JsonObject subObj)
                                    MergeLabyEntry(result, Canonical(subKey), subObj);
                        }
                        else MergeLabyEntry(result, Canonical(key), child);
                    }
                }
                else MergeLabyEntry(result, Canonical(ns), parsed);
            }
        }

        var widgetFiles = new[]
        {
            Path.Combine(laby, "configs", "labymod", "hud", "widgets.json"),
            Path.Combine(laby, "configs", "hud", "widgets.json"),
            Path.Combine(laby, "hud", "widgets.json"),
            Path.Combine(laby, "configs", "widgets.json")
        };
        foreach (var wFile in widgetFiles.Where(File.Exists).Distinct(StringComparer.OrdinalIgnoreCase))
        {
            var wRoot = ParseObject(ReadBounded(wFile), wFile);
            if (wRoot["widgets"] is JsonArray wArr)
            {
                foreach (var wObj in wArr.Take(256).OfType<JsonObject>())
                {
                    string? id = (wObj["id"] ?? wObj["identifier"] ?? wObj["type"]) is JsonValue v && v.TryGetValue<string>(out var s) ? s : null;
                    if (!string.IsNullOrWhiteSpace(id) && !SensitiveNames.Contains(id)) MergeLabyEntry(result, Canonical(id), wObj);
                }
            }
            else
            {
                var container = wRoot["widgets"] as JsonObject ?? wRoot;
                foreach (var (rawId, wNode) in container.Take(256))
                    if (!SensitiveNames.Contains(rawId) && wNode is JsonObject wObj)
                        MergeLabyEntry(result, Canonical(rawId), wObj);
            }
        }

        return result.Count > 0 ? result : null;
    }

    private static void MergeLabyEntry(JsonObject result, string key, JsonObject src)
    {
        var dst = EnsureObject(result, key);
        var opts = EnsureObject(dst, "options");
        foreach (var (k, v) in src)
        {
            if (v == null || SensitiveNames.Contains(k)) continue;
            if (k is "id" or "identifier" or "type" or "bounds" or "parentId") continue;
            if (k == "enabled") dst["enabled"] = v.DeepClone();
            else if (k is "options" or "settings" or "properties" && v is JsonObject nested)
            {
                foreach (var (nk, nv) in nested)
                {
                    if (nv == null || SensitiveNames.Contains(nk) || nk is "id" or "identifier" or "type" or "bounds" or "parentId") continue;
                    if (nk == "enabled") dst["enabled"] ??= nv.DeepClone();
                    else opts[nk] = nv.DeepClone();
                }
            }
            else if (k is "x" or "y" or "offsetX" or "offsetY" or "relativeX" or "relativeY" or "anchor" or "alignment" or "area" or "areaIdentifier")
                dst[k] = v.DeepClone();
            else if (k == "scale")
                opts["scale"] = v.DeepClone();
            else
                opts[k] = v.DeepClone();
        }
    }

    private static JsonObject ReadConfig(string file)
    {
        if (!File.Exists(file))
        {
            // The markers a saved config has: without them the game would reset to its preset and run its one-time overrides over the import.
            var hud = new JsonObject();
            foreach (var flag in new[] { "shadowOnDefault", "hudFpsCapOnDefault", "hud1910OverrideApplied", "betterF31911Applied", "keystrokesStyleMigrated",
                "keystrokesCleanGlobalMigrated", "cloudHeight1914Applied", "mobKillBanner1914Applied" }) hud[flag] = true;
            return new JsonObject { ["modules"] = new JsonObject(), ["hud"] = hud, ["lads197PresetApplied"] = true };
        }
        try { return ParseObject(ReadBounded(file), file); }
        catch (InvalidDataException) { throw new InvalidDataException("Your Lads settings file is damaged; it was left as it is."); }
    }

    private static JsonObject EnsureObject(JsonObject parent, string key)
    {
        if (parent[key] is JsonObject found) return found;
        var created = new JsonObject();
        parent[key] = created;
        return created;
    }

    private static void SetModule(CategoryPlan plan, JsonObject modules, string module, string key, JsonNode? value, string line)
    {
        var target = EnsureObject(modules, module);
        if (Same(target[key], value)) return;
        target[key] = value?.DeepClone();
        plan.Changes.Add(line);
    }

    private static void SetOption(CategoryPlan plan, JsonObject modules, string module, string option, JsonNode value)
    {
        var options = EnsureObject(EnsureObject(modules, module), "options");
        if (Same(options[option], value)) return;
        options[option] = value.DeepClone();
        plan.Changes.Add($"{module}: {option}");
        if (option == "Text Color") // ConfigManager reads these beside the option; the option, read after them, stays the one that counts
        {
            modules[module]!["useGlobalColor"] = false;
            modules[module]!["customColor"] = value["color"]!.DeepClone();
        }
    }

    /// <summary>JSON equality where 100 and 100.0 are one value: the game saves its sliders as 100.0.</summary>
    internal static bool Same(JsonNode? a, JsonNode? b)
    {
        if (a is JsonObject x && b is JsonObject y) return x.Count == y.Count && x.All(p => y.ContainsKey(p.Key) && Same(p.Value, y[p.Key]));
        if (a is JsonArray u && b is JsonArray v) return u.Count == v.Count && u.Zip(v).All(p => Same(p.First, p.Second));
        if (a is JsonValue && b is JsonValue)
            return ToNumber(a) is { } m && ToNumber(b) is { } n ? Math.Abs(m - n) < 1e-9 : JsonNode.DeepEquals(a, b);
        return a is null && b is null;
    }

    private static JsonNode? Walk(JsonObject root, string path)
    {
        JsonNode? node = root;
        foreach (var part in path.Split('/'))
        {
            if (node is not JsonObject obj) return null;
            node = obj[part];
        }
        return node;
    }

    private static JsonNode? ConvertValue(JsonNode node, string conv) => conv switch
    {
        "bool" => ToBool(node) is { } b ? JsonValue.Create(b) : null,
        "not" => ToBool(node) is { } b ? JsonValue.Create(!b) : null,
        "num" => ToNumber(node) is { } n ? JsonValue.Create(n) : null,
        "text" => node is JsonValue v && v.TryGetValue<string>(out var s) && s.Length <= 256 && !s.Any(char.IsControl) ? JsonValue.Create(s) : null,
        "color" => ToColor(node) is { } c ? new JsonObject { ["useGlobal"] = false, ["color"] = c } : null,
        _ when conv.StartsWith("mul:", StringComparison.Ordinal) && ToNumber(node) is { } n
            => JsonValue.Create(Math.Round(n * double.Parse(conv[4..], CultureInfo.InvariantCulture), 3)),
        _ => null
    };

    /// <summary>A boolean as the clients store it: true, "true" (Feather), {"value":true} (Badlion).</summary>
    private static bool? ToBool(JsonNode? node)
    {
        if (node is JsonObject wrapped) node = wrapped["value"];
        if (node is not JsonValue value) return null;
        if (value.TryGetValue<bool>(out var b)) return b;
        return value.TryGetValue<string>(out var s) && bool.TryParse(s, out b) ? b : null;
    }

    private static double? ToNumber(JsonNode? node)
    {
        if (node is JsonObject wrapped) node = wrapped["value"];
        if (node is not JsonValue value) return null;
        var text = value.TryGetValue<string>(out var s) ? s : value.ToJsonString(); // numbers from a file or made here, and Feather's strings
        return double.TryParse(text, NumberStyles.Float, CultureInfo.InvariantCulture, out var d) && double.IsFinite(d) ? d : null;
    }

    /// <summary>ARGB from Lunar's {"value":argb}, LabyMod's integer/{"color":argb}, Badlion's {"set","enabled","color":{red,green,blue,alpha}}
    /// (signed bytes) or Feather's "[chroma/]r/g/b/a". An unset Badlion colour is null: the client's default, nothing to carry over.</summary>
    internal static int? ToColor(JsonNode? node)
    {
        if (node is JsonValue val)
        {
            if (val.TryGetValue<long>(out var directArgb)) return unchecked((int)directArgb);
            if (!val.TryGetValue<string>(out var s)) return null;
            var parts = s.Split('/');
            if (parts.Length == 5 && bool.TryParse(parts[0], out _)) parts = parts[1..];
            if (parts.Length != 4) return null;
            var channels = new int[4];
            for (int i = 0; i < 4; i++)
                if (!int.TryParse(parts[i], NumberStyles.Integer, CultureInfo.InvariantCulture, out channels[i]) || channels[i] is < 0 or > 255) return null;
            return Argb(channels[3], channels[0], channels[1], channels[2]);
        }
        if (node is not JsonObject obj) return null;
        if (obj["color"] is JsonObject rgba)
        {
            if (ToBool(obj["set"]) == false) return null;
            if (ToNumber(rgba["red"]) is not { } r || ToNumber(rgba["green"]) is not { } g || ToNumber(rgba["blue"]) is not { } b || ToNumber(rgba["alpha"]) is not { } a) return null;
            return ToBool(obj["enabled"]) == false ? 0 : Argb((int)a & 255, (int)r & 255, (int)g & 255, (int)b & 255);
        }
        if (obj["color"] is JsonValue colVal && colVal.TryGetValue<long>(out var colArgb)) return unchecked((int)colArgb);
        return obj["value"] is JsonValue value && value.TryGetValue<long>(out var argb) ? unchecked((int)argb) : null;
    }

    private static int Argb(int a, int r, int g, int b) => unchecked((int)((uint)a << 24 | (uint)r << 16 | (uint)g << 8 | (uint)b));

    // --- HUD positions ---

    /// <summary>A HUD place: per axis the point of the screen (0 start, 1 centre, 2 end), the HUD's own point, and the offset in GUI pixels.</summary>
    internal sealed record HudPlace(int RefX, int PinX, int OffX, int RefY, int PinY, int OffY);

    private static HudPlace? ReadAnchor(string client, JsonObject mod)
    {
        if (client == "labymod") return LabyModAnchor(mod);
        if (mod[client == "lunar" ? "position" : "hudAnchor"] is not JsonValue anchorName || !anchorName.TryGetValue<string>(out var name)) return null;
        double? x = client == "lunar" ? ToNumber(mod["x"]) ?? 0 : ToNumber(mod["hudRelativeX"]);
        double? y = client == "lunar" ? ToNumber(mod["y"]) ?? 0 : ToNumber(mod["hudRelativeY"]);
        if (x is null or > 8000 or < -8000 || y is null or > 8000 or < -8000) return null;
        string key = name.Replace("_", "").ToLowerInvariant();
        (int Ref, int Pin)? horizontal = null;
        if (key.EndsWith("centerleft", StringComparison.Ordinal)) horizontal = (1, 2); // Lunar: docked left of the hotbar
        else if (key.EndsWith("centerright", StringComparison.Ordinal)) horizontal = (1, 0);
        else if (key.EndsWith("left", StringComparison.Ordinal)) horizontal = (0, 0);
        else if (key.EndsWith("center", StringComparison.Ordinal)) horizontal = (1, 1);
        else if (key.EndsWith("right", StringComparison.Ordinal)) horizontal = (2, 2);
        int? vertical = key.StartsWith("top", StringComparison.Ordinal) ? 0 : key.StartsWith("middle", StringComparison.Ordinal) ? 1 : key.StartsWith("bottom", StringComparison.Ordinal) ? 2 : null;
        if (horizontal == null || vertical == null) return null;
        return new HudPlace(horizontal.Value.Ref, horizontal.Value.Pin, (int)Math.Round(x.Value), vertical.Value, vertical.Value, (int)Math.Round(y.Value));
    }

    private static HudPlace? LabyModAnchor(JsonObject mod)
    {
        int? rx = null, ry = null;
        if ((mod["anchor"] as JsonObject ?? mod["alignment"] as JsonObject) is { } obj)
        {
            string? hx = (obj["x"] ?? obj["horizontal"]) is JsonValue vx && vx.TryGetValue<string>(out var sx) ? sx.ToLowerInvariant() : null;
            string? vy = (obj["y"] ?? obj["vertical"]) is JsonValue vyv && vyv.TryGetValue<string>(out var sy) ? sy.ToLowerInvariant() : null;
            rx = hx switch { "left" => 0, "center" or "middle" => 1, "right" => 2, _ => null };
            ry = vy switch { "top" => 0, "center" or "middle" => 1, "bottom" => 2, _ => null };
        }
        else if ((mod["anchor"] ?? mod["alignment"] ?? mod["area"] ?? mod["areaIdentifier"]) is JsonValue v && v.TryGetValue<string>(out var s))
        {
            string key = s.Replace("_", "").Replace("-", "").ToLowerInvariant();
            rx = key.Contains("left", StringComparison.Ordinal) ? 0 : key.Contains("right", StringComparison.Ordinal) ? 2 : key.Contains("center", StringComparison.Ordinal) || key.Contains("middle", StringComparison.Ordinal) ? 1 : null;
            ry = key.StartsWith("top", StringComparison.Ordinal) ? 0 : key.StartsWith("bottom", StringComparison.Ordinal) ? 2 : key.StartsWith("middle", StringComparison.Ordinal) || key.StartsWith("center", StringComparison.Ordinal) ? 1 : null;
        }

        double? xNode = ToNumber(mod["x"] ?? mod["offsetX"] ?? mod["relativeX"]);
        double? yNode = ToNumber(mod["y"] ?? mod["offsetY"] ?? mod["relativeY"]);
        if (rx == null || ry == null)
        {
            if (xNode is >= 0 and <= 1 && yNode is >= 0 and <= 1)
            {
                rx = xNode < 0.34 ? 0 : xNode > 0.66 ? 2 : 1;
                ry = yNode < 0.34 ? 0 : yNode > 0.66 ? 2 : 1;
            }
            else return null;
        }

        double x = xNode ?? 0, y = yNode ?? 0;
        if (x is > 8000 or < -8000 || y is > 8000 or < -8000) return null;
        int offX, offY;
        if (x is >= 0 and <= 1 && y is >= 0 and <= 1 && ((x > 0 && x < 1) || (y > 0 && y < 1)))
        {
            if (x > 0.5 || y > 0.5)
            {
                offX = (int)Math.Round((x - rx.Value / 2.0) * 960);
                offY = (int)Math.Round((y - ry.Value / 2.0) * 540);
            }
            else
            {
                offX = (int)Math.Round((rx.Value == 2 ? -x : x) * 960);
                offY = (int)Math.Round((ry.Value == 2 ? -y : y) * 540);
            }
        }
        else
        {
            offX = (int)Math.Round(x);
            offY = (int)Math.Round(y);
        }
        return new HudPlace(rx.Value, rx.Value, offX, ry.Value, ry.Value, offY);
    }

    /// <summary>Badlion: the box's corner at (xPosition, yPosition) of a screenWidth wide screen, in fractions; its sizes are at GUI scale 2.</summary>
    private static HudPlace? BadlionAnchor(JsonObject box)
    {
        if (box["quadrant"] is not JsonValue q || !q.TryGetValue<string>(out var quadrant)) return null;
        if (ToNumber(box["xPosition"]) is not { } x || ToNumber(box["yPosition"]) is not { } y || x is < 0 or > 1 || y is < 0 or > 1) return null;
        double w = Math.Max(ToNumber(box["screenWidth"]) ?? 1920, 320) / 2, h = Math.Max(ToNumber(box["screenHeight"]) ?? 1080, 240) / 2;
        int? rx = quadrant.EndsWith("LEFT", StringComparison.Ordinal) ? 0 : quadrant.EndsWith("CENTER", StringComparison.Ordinal) ? 1 : quadrant.EndsWith("RIGHT", StringComparison.Ordinal) ? 2 : null;
        int? ry = quadrant.StartsWith("TOP", StringComparison.Ordinal) ? 0 : quadrant.StartsWith("MIDDLE", StringComparison.Ordinal) ? 1 : quadrant.StartsWith("BOTTOM", StringComparison.Ordinal) ? 2 : null;
        if (rx == null || ry == null) return null;
        return new HudPlace(rx.Value, rx.Value, (int)Math.Round((x - rx.Value / 2.0) * w), ry.Value, ry.Value, (int)Math.Round((y - ry.Value / 2.0) * h));
    }

    private static void SetAnchor(CategoryPlan plan, JsonObject hud, string module, HudPlace p)
    {
        // Saved position and anchor must agree (HudSettings.getAnchor); the game lays it out from the anchor, on a nominal 960x540 here.
        int atX = p.RefX * 480 + p.OffX, atY = p.RefY * 270 + p.OffY;
        var position = new JsonArray(atX, atY);
        var anchor = new JsonObject { ["at"] = new JsonArray(atX, atY), ["x"] = new JsonArray(p.RefX, p.PinX, p.OffX, 0), ["y"] = new JsonArray(p.RefY, p.PinY, p.OffY, 0) };
        var positions = EnsureObject(hud, "positions");
        var anchors = EnsureObject(hud, "anchors");
        if (Same(positions[module], position) && Same(anchors[module], anchor)) return;
        positions[module] = position;
        anchors[module] = anchor;
        plan.Changes.Add($"{module}: HUD position");
    }

    // --- Launcher ---

    private static void PlanLauncher(ImportSource source, ImportTargets targets, CategoryPlan plan)
    {
        var current = LauncherSettings.LoadFrom(targets.SettingsFile);
        void Set(string key, JsonNode value, string line)
        {
            if (Same(ReadLauncher(current, key), value)) return;
            plan.Launcher[key] = value;
            plan.Changes.Add(line);
        }
        void Memory(double? mb, string key, string label)
        {
            if (mb is null) return;
            if (mb is < 512 or > 65536) { Skip(plan, $"{label}: unusable value"); return; }
            Set(key, JsonValue.Create((int)mb.Value), $"{label}: {(int)mb.Value} MB");
        }
        switch (source.Id)
        {
            case "lunar":
            {
                var file = Path.Combine(source.Root, "settings", "launcher.json");
                if (!File.Exists(file)) return;
                if (ParseObject(ReadBounded(file), file)["settings"] is not JsonObject settings) throw new InvalidDataException($"{Quote(file)} is not in the expected format.");
                Memory(ToNumber(settings["allocatedMemory"]), "MaxRamMb", "Maximum RAM");
                if (Walk(settings, "gameResolution/fullscreen") is { } fullscreen && ToBool(fullscreen) is { } full) Set("FullscreenOnLaunch", JsonValue.Create(full), $"Launch in fullscreen: {full}");
                if (settings["gameResolution"] != null) Skip(plan, "Game window size: the Lads launcher has no such setting");
                if (settings["afterLaunchAction"] is JsonValue action && action.TryGetValue<string>(out var after))
                {
                    if (after == "HIDE")
                    {
                        Set("KeepLauncherOpen", JsonValue.Create(false), "Keep the launcher open: False");
                        Set("MinimizeOnLaunch", JsonValue.Create(true), "Hide the launcher on launch: True");
                    }
                    else Skip(plan, $"After launch action {after}: not verified");
                }
                if (Walk(settings, "discordRichPresence/enabled") is { } rpc && ToBool(rpc) is { } discord) Set("DiscordRichPresence", JsonValue.Create(discord), $"Discord presence: {discord}");
                break;
            }
            case "prism":
            case "multimc":
            {
                var cfgName = source.Id == "multimc" ? "multimc.cfg" : "prismlauncher.cfg";
                var file = Path.Combine(source.Root, cfgName);
                var cfg = File.Exists(file) ? ParseIni(Encoding.UTF8.GetString(ReadBounded(file, 1024 * 1024))) : new Dictionary<string, string>(StringComparer.Ordinal);
                string? instanceDir = source.GameFolder != null && Path.GetFileName(source.GameFolder) is "minecraft" or ".minecraft"
                    ? Directory.GetParent(source.GameFolder)?.FullName : source.GameFolder;
                string? instCfgFile = instanceDir != null ? Path.Combine(instanceDir, "instance.cfg") : null;
                var instCfg = instCfgFile != null && File.Exists(instCfgFile)
                    ? ParseIni(Encoding.UTF8.GetString(ReadBounded(instCfgFile, 1024 * 1024)))
                    : new Dictionary<string, string>(StringComparer.Ordinal);
                if (cfg.Count == 0 && instCfg.Count == 0) return;

                bool overrideMem = instCfg.TryGetValue("OverrideMemory", out var om) && bool.TryParse(om, out var omb) && omb;
                string? max = overrideMem && instCfg.TryGetValue("MaxMemAlloc", out var imax) ? imax : cfg.GetValueOrDefault("MaxMemAlloc") ?? instCfg.GetValueOrDefault("MaxMemAlloc");
                string? min = overrideMem && instCfg.TryGetValue("MinMemAlloc", out var imin) ? imin : cfg.GetValueOrDefault("MinMemAlloc") ?? instCfg.GetValueOrDefault("MinMemAlloc");
                if (max != null) Memory(ToNumber(JsonValue.Create(max)), "MaxRamMb", "Maximum RAM");
                if (min != null) Memory(ToNumber(JsonValue.Create(min)), "MinRamMb", "Minimum RAM");

                string? close = instCfg.TryGetValue("OverrideWindow", out var ow) && ow == "true" && instCfg.TryGetValue("CloseAfterLaunch", out var ic) ? ic : cfg.GetValueOrDefault("CloseAfterLaunch") ?? instCfg.GetValueOrDefault("CloseAfterLaunch");
                if (close != null && bool.TryParse(close, out var closes)) Set("KeepLauncherOpen", JsonValue.Create(!closes), $"Keep the launcher open: {!closes}");
                if (cfg.ContainsKey("JvmArgs") || instCfg.ContainsKey("JvmArgs")) Skip(plan, "JVM arguments: the Lads launcher chooses its own for each version");
                if (cfg.ContainsKey("MinecraftWinWidth") || instCfg.ContainsKey("MinecraftWinWidth")) Skip(plan, "Game window size: the Lads launcher has no such setting");
                break;
            }
            case "modrinth":
            {
                double? ramMb = null;
                bool hasJvmArgs = false;
                if (source.GameFolder != null && File.Exists(Path.Combine(source.GameFolder, "profile.json")))
                {
                    var profileFile = Path.Combine(source.GameFolder, "profile.json");
                    var obj = ParseObject(ReadBounded(profileFile), profileFile);
                    if (ToNumber(Walk(obj, "memory/maximum") ?? obj["override_mc_memory_max"]) is > 0 and var profRam) ramMb = profRam;
                    if (Walk(obj, "java/extra_arguments") is JsonArray { Count: > 0 } || (Walk(obj, "java/extra_arguments") is JsonValue s && s.ToString().Length > 0))
                        hasJvmArgs = true;
                }
                var dbFile = Path.Combine(source.Root, "app.db");
                if (File.Exists(dbFile))
                {
                    var (dbRam, dbArgs) = ReadModrinthMemory(dbFile, source.GameFolder);
                    ramMb ??= dbRam;
                    hasJvmArgs |= dbArgs;
                }
                Memory(ramMb, "MaxRamMb", "Maximum RAM");
                if (hasJvmArgs) Skip(plan, "JVM arguments: the Lads launcher chooses its own for each version");
                break;
            }
            case "curseforge":
            {
                string? folder = source.GameFolder ?? source.Root;
                var file = Path.Combine(folder, "minecraftinstance.json");
                if (!File.Exists(file)) return;
                var inst = ParseObject(ReadBounded(file), file);
                bool overrideOff = ToBool(inst["isMemoryOverride"]) == false;
                double? alloc = ToNumber(inst["allocatedMemory"]);
                double? rec = Walk(inst, "manifest/minecraft/recommendedRam") is { } r ? ToNumber(r) : null;
                double? chosen = !overrideOff && alloc is > 0 ? alloc : rec is > 0 ? rec : alloc is > 0 ? alloc : null;
                Memory(chosen, "MaxRamMb", "Maximum RAM");
                if ((inst["javaArgsOverride"] is JsonValue ja && ja.TryGetValue<string>(out var jas) && jas.Trim().Length > 0)
                    || (Walk(inst, "manifest/minecraft/additionalJavaArgs") is JsonValue ma && ma.TryGetValue<string>(out var mas) && mas.Trim().Length > 0))
                    Skip(plan, "JVM arguments: the Lads launcher chooses its own for each version");
                break;
            }
            case "vanilla":
            {
                var file = Path.Combine(source.Root, "launcher_profiles.json");
                if (!File.Exists(file)) return;
                var root = ParseObject(ReadBounded(file), file); // only "settings" and each profile's lastUsed and javaArgs are read
                if (Walk(root, "settings/keepLauncherOpen") is { } keep && ToBool(keep) is { } stays) Set("KeepLauncherOpen", JsonValue.Create(stays), $"Keep the launcher open: {stays}");
                var last = (root["profiles"] as JsonObject)?.Select(p => p.Value).OfType<JsonObject>()
                    .Where(p => p["javaArgs"] is JsonValue)
                    .OrderByDescending(p => p["lastUsed"] is JsonValue u && u.TryGetValue<string>(out var s) ? s : "", StringComparer.Ordinal).FirstOrDefault();
                if (last?["javaArgs"] is JsonValue args && args.TryGetValue<string>(out var line))
                {
                    var xmx = Regex.Match(line, @"(?:^|\s)-Xmx(\d{1,7})([GgMm])(?:\s|$)");
                    if (xmx.Success) Memory(double.Parse(xmx.Groups[1].Value, CultureInfo.InvariantCulture) * (xmx.Groups[2].Value is "G" or "g" ? 1024 : 1), "MaxRamMb", "Maximum RAM");
                    Skip(plan, "JVM arguments: the Lads launcher chooses its own for each version");
                }
                break;
            }
            case "custom" or "folder" or "labymod" or "badlion" or "feather":
                Skip(plan, $"{source.Name}: no verified launcher settings to read");
                break;
        }
    }

    /// <summary>Reads only custom_dir from Modrinth's settings table on a temp copy; never touches user/credential tables.</summary>
    private static string? TryModrinthCustomDir(string dbFile)
    {
        if (!File.Exists(dbFile)) return null;
        try { return WithModrinthDb(dbFile, (conn, tables) => QueryModrinthCustomDir(conn, tables)); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException or SqliteException) { return null; }
    }

    /// <summary>Reads only memory and extra_launch_args from Modrinth's instances/profiles and settings tables; never touches auth tables.</summary>
    private static (double? RamMb, bool HasJvmArgs) ReadModrinthMemory(string dbFile, string? profileFolder)
    {
        try
        {
            return WithModrinthDb(dbFile, (conn, tables) =>
            {
                double? ram = null;
                bool hasArgs = false;
                string? profileName = profileFolder != null ? Path.GetFileName(Path.TrimEndingDirectorySeparator(profileFolder)) : null;
                string? profileTable = tables.Contains("instances") ? "instances" : tables.Contains("profiles") ? "profiles" : null;
                if (profileTable != null && !string.IsNullOrEmpty(profileName))
                {
                    var cols = QueryStrings(conn, $"SELECT name FROM pragma_table_info('{profileTable}')");
                    if (cols.Contains("path") && (cols.Contains("override_mc_memory_max") || cols.Contains("override_extra_launch_args")))
                    {
                        string memCol = cols.Contains("override_mc_memory_max") ? "override_mc_memory_max" : "NULL";
                        string argCol = cols.Contains("override_extra_launch_args") ? "override_extra_launch_args" : "NULL";
                        using var cmd = conn.CreateCommand();
                        cmd.CommandText = $"SELECT {memCol}, {argCol} FROM {profileTable} WHERE path = $p LIMIT 1";
                        cmd.Parameters.AddWithValue("$p", profileName);
                        using var reader = cmd.ExecuteReader();
                        if (reader.Read())
                        {
                            if (!reader.IsDBNull(0) && reader.GetValue(0) is long m && m > 0) ram = m;
                            if (!reader.IsDBNull(1) && reader.GetValue(1) is string a && HasNonEmptyArgs(a)) hasArgs = true;
                        }
                    }
                }
                if (tables.Contains("settings"))
                {
                    var sCols = QueryStrings(conn, "SELECT name FROM pragma_table_info('settings')");
                    if (ram == null && sCols.Contains("mc_memory_max"))
                    {
                        using var cmd = conn.CreateCommand();
                        cmd.CommandText = "SELECT mc_memory_max FROM settings LIMIT 1";
                        if (cmd.ExecuteScalar() is long gm && gm > 0) ram = gm;
                    }
                    if (!hasArgs && sCols.Contains("extra_launch_args"))
                    {
                        using var cmd = conn.CreateCommand();
                        cmd.CommandText = "SELECT extra_launch_args FROM settings LIMIT 1";
                        if (cmd.ExecuteScalar() is string ga && HasNonEmptyArgs(ga)) hasArgs = true;
                    }
                }
                return (ram, hasArgs);
            });
        }
        catch (SqliteException) { throw new InvalidDataException($"{Quote(dbFile)} is damaged or in an unknown format."); }
    }

    private static bool HasNonEmptyArgs(string raw)
    {
        raw = raw.Trim();
        return raw.Length > 0 && raw != "[]" && raw != "\"\"";
    }

    private static T WithModrinthDb<T>(string dbFile, Func<SqliteConnection, HashSet<string>, T> work)
    {
        SQLitePCL.Batteries_V2.Init();
        string temp = Path.Combine(Path.GetTempPath(), "lads-import-modrinth-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(temp);
        try
        {
            var copy = Path.Combine(temp, "app.db");
            File.WriteAllBytes(copy, ReadBounded(dbFile, MaxDbBytes));
            if (File.Exists(dbFile + "-wal")) File.WriteAllBytes(copy + "-wal", ReadBounded(dbFile + "-wal", MaxDbBytes));
            using var conn = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = copy, Mode = SqliteOpenMode.ReadWrite, Pooling = false }.ToString());
            conn.Open();
            var tables = QueryStrings(conn, "SELECT name FROM sqlite_master WHERE type = 'table'");
            return work(conn, tables);
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            try { Directory.Delete(temp, true); } catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        }
    }

    private static string? QueryModrinthCustomDir(SqliteConnection conn, HashSet<string> tables)
    {
        if (!tables.Contains("settings")) return null;
        var cols = QueryStrings(conn, "SELECT name FROM pragma_table_info('settings')");
        if (!cols.Contains("custom_dir")) return null;
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "SELECT custom_dir FROM settings LIMIT 1";
        return cmd.ExecuteScalar() is string dir && Path.IsPathFullyQualified(dir) && Directory.Exists(dir) ? dir : null;
    }

    private static HashSet<string> QueryStrings(SqliteConnection conn, string sql)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = sql;
        using var reader = cmd.ExecuteReader();
        var set = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        while (reader.Read()) if (!reader.IsDBNull(0)) set.Add(reader.GetString(0));
        return set;
    }

    /// <summary>key=value lines of a Prism or MultiMC .cfg; only the keys asked for above are looked at.</summary>
    private static Dictionary<string, string> ParseIni(string text)
    {
        var values = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var raw in text.Split('\n'))
        {
            var line = raw.Trim();
            if (line.Length == 0 || line[0] is '[' or ';' or '#') continue;
            if (line.IndexOf('=') is > 0 and var at)
            {
                var val = line[(at + 1)..].Trim();
                if (val.Length >= 2 && val[0] == '"' && val[^1] == '"') val = val[1..^1];
                values[line[..at].Trim()] = val;
            }
        }
        return values;
    }

    private static JsonNode? ReadLauncher(LauncherSettings settings, string key) => key switch
    {
        "MaxRamMb" => settings.RamChosenByUser ? JsonValue.Create(settings.MaxRamMb) : null,
        "MinRamMb" => JsonValue.Create(settings.MinRamMb),
        "FullscreenOnLaunch" => JsonValue.Create(settings.FullscreenOnLaunch),
        "KeepLauncherOpen" => JsonValue.Create(settings.KeepLauncherOpen),
        "MinimizeOnLaunch" => JsonValue.Create(settings.MinimizeOnLaunch),
        "DiscordRichPresence" => JsonValue.Create(settings.DiscordRichPresence),
        _ => null
    };

    private static void WriteLauncher(LauncherSettings settings, string key, JsonNode? value)
    {
        if (value is null)
        {
            if (key == "MaxRamMb") settings.RamChosenByUser = false; // undo of an amount the player never chose
            return;
        }
        switch (key)
        {
            case "MaxRamMb": settings.MaxRamMb = value.GetValue<int>(); settings.RamChosenByUser = true; break;
            case "MinRamMb": settings.MinRamMb = value.GetValue<int>(); break;
            case "FullscreenOnLaunch": settings.FullscreenOnLaunch = value.GetValue<bool>(); break;
            case "KeepLauncherOpen": settings.KeepLauncherOpen = value.GetValue<bool>(); break;
            case "MinimizeOnLaunch": settings.MinimizeOnLaunch = value.GetValue<bool>(); break;
            case "DiscordRichPresence": settings.DiscordRichPresence = value.GetValue<bool>(); break;
        }
    }

    /// <summary>The launcher settings the in-game import left waiting, into <paramref name="settings"/> (the caller saves). False without any.</summary>
    public static bool ApplyPendingLauncher(string baseDirectory, LauncherSettings settings)
    {
        var file = Path.Combine(baseDirectory, PendingLauncherFile);
        if (!File.Exists(file)) return false;
        try
        {
            foreach (var (key, value) in ParseObject(ReadBounded(file), file)) WriteLauncher(settings, key, value);
        }
        catch (Exception e) when (e is IOException or InvalidDataException or InvalidOperationException or FormatException) { }
        try { File.Delete(file); } catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        return true;
    }

    // --- Apply ---

    /// <summary>
    /// The chosen categories of <paramref name="plan"/>, after copying what they overwrite into a timestamped folder. A category that fails
    /// midway is put back from that copy, so it applies fully or not at all. <paramref name="live"/>: the launcher's own settings object,
    /// changed and saved instead of the file. Importing what is already there changes nothing and takes no backup.
    /// </summary>
    public static ImportResult Apply(ImportPlan plan, IReadOnlyCollection<ImportCategory> chosen, ImportTargets targets, LauncherSettings? live = null)
    {
        var applied = new Dictionary<ImportCategory, int>();
        var errors = new List<string>();
        var selected = plan.Categories.Values.Where(c => chosen.Contains(c.Category) && c.Problem == null && c.Changes.Count > 0).ToList();
        if (selected.Count == 0) return new(null, applied, errors, false, false, false);
        var backup = FreeBackupFolder(targets, plan.Source.Id);
        var manifest = new JsonObject { ["client"] = plan.Source.Name, ["when"] = DateTime.UtcNow.ToString("O", CultureInfo.InvariantCulture), ["inGame"] = targets.InGame,
            ["files"] = new JsonArray(), ["packs"] = new JsonArray(), ["undone"] = false };
        bool configChanged = false, restart = false, pendingLauncher = false;
        foreach (var category in selected)
        {
            var files = FilesOf(category.Category, targets);
            var snapshot = new List<(string File, string? Copy)>();
            var added = new List<string>();
            JsonObject? launcherBefore = null;
            try
            {
                foreach (var file in files) snapshot.Add((file, Snapshot(file, backup, manifest)));
                SaveManifest(backup, manifest);
                switch (category.Category)
                {
                    case ImportCategory.Options:
                        var saved = SharedControls.Save(targets.Paths, SharedControls.Targets(targets.Profiles), category.Options);
                        if (saved.Errors.Count > 0) throw new IOException(string.Join("; ", saved.Errors));
                        restart = saved.Deferred.Count > 0 || targets.InGame;
                        break;
                    case ImportCategory.Servers:
                        ServerListService.MergeAsync(new ServerListTarget("shared", targets.ServersFile, targets.ServersLockFile), category.Servers).GetAwaiter().GetResult();
                        break;
                    case ImportCategory.Packs:
                        Directory.CreateDirectory(targets.PacksDirectory);
                        foreach (var (source, name) in category.Packs)
                        {
                            string destination = Path.Combine(targets.PacksDirectory, name);
                            added.Add(destination);
                            manifest["packs"]!.AsArray().Add(destination);
                            SaveManifest(backup, manifest);
                            CopyPack(source, destination);
                        }
                        break;
                    case ImportCategory.Mods:
                        WriteConfig(targets, category.Config!);
                        configChanged = true;
                        break;
                    case ImportCategory.Launcher:
                        launcherBefore = ApplyLauncher(category, targets, live, ref pendingLauncher);
                        manifest["launcher"] = launcherBefore;
                        break;
                }
                SaveManifest(backup, manifest);
                applied[category.Category] = category.Changes.Count;
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException or TimeoutException)
            {
                errors.Add($"{category.Category}: {e.Message}");
                try
                {
                    foreach (var (file, copy) in snapshot) Restore(file, copy, targets);
                    foreach (var pack in added) DeletePack(pack, targets);
                }
                catch (Exception undo) when (undo is IOException or UnauthorizedAccessException) { errors.Add($"Could not put {category.Category} back: {undo.Message}. The backup is in {backup}."); }
                foreach (var pack in added) manifest["packs"]!.AsArray().Remove(manifest["packs"]!.AsArray().FirstOrDefault(n => n!.GetValue<string>() == pack));
                SaveManifest(backup, manifest);
            }
        }
        return new(backup, applied, errors, configChanged, restart, pendingLauncher);
    }

    private static JsonObject ApplyLauncher(CategoryPlan category, ImportTargets targets, LauncherSettings? live, ref bool pending)
    {
        var settings = live ?? LauncherSettings.LoadFrom(targets.SettingsFile);
        var before = new JsonObject();
        foreach (var key in category.Launcher.Keys) before[key] = ReadLauncher(settings, key)?.DeepClone();
        if (targets.InGame)
        {
            // A running launcher would save its own copy over the file: it takes these at its next start or when the game closes.
            var waiting = new JsonObject();
            foreach (var (key, value) in category.Launcher) waiting[key] = value.DeepClone();
            WriteJson(Path.Combine(targets.Paths.BaseDirectory, PendingLauncherFile), waiting);
            pending = true;
            return before;
        }
        foreach (var (key, value) in category.Launcher) WriteLauncher(settings, key, value);
        settings.SaveTo(targets.SettingsFile);
        return before;
    }

    private static IReadOnlyList<string> FilesOf(ImportCategory category, ImportTargets targets) => category switch
    {
        ImportCategory.Options => new[] { targets.Paths.SharedOptionsFile }.Concat(SharedControls.Targets(targets.Profiles).SelectMany(p =>
        {
            var dir = targets.Paths.GetProfileDirectory(p);
            return new[] { Path.Combine(dir, "options.txt"), Path.Combine(dir, SharedControls.PendingFile) };
        })).ToList(),
        ImportCategory.Servers => new[] { targets.ServersFile },
        ImportCategory.Mods => new[] { targets.ConfigFile },
        _ => Array.Empty<string>()
    };

    private static string FreeBackupFolder(ImportTargets targets, string client)
    {
        var stamp = DateTime.Now.ToString("yyyyMMdd-HHmmss", CultureInfo.InvariantCulture);
        for (int i = 0; ; i++)
        {
            var dir = Path.Combine(targets.BackupsDirectory, $"{stamp}{(i == 0 ? "" : "-" + i)}-{client}");
            if (Directory.Exists(dir)) continue;
            Directory.CreateDirectory(dir);
            return dir;
        }
    }

    private static string? Snapshot(string file, string backup, JsonObject manifest)
    {
        var files = manifest["files"]!.AsArray();
        string? copy = null;
        if (File.Exists(file))
        {
            copy = $"{files.Count}-{Path.GetFileName(file)}";
            File.Copy(file, Path.Combine(backup, copy), false);
        }
        files.Add(new JsonObject { ["path"] = file, ["copy"] = copy });
        return copy == null ? null : Path.Combine(backup, copy);
    }

    private static void SaveManifest(string backup, JsonObject manifest) => WriteJson(Path.Combine(backup, ManifestFile), manifest);

    private static void WriteJson(string file, JsonNode node) =>
        LockFiles.WriteAtomicallyAsync(file, Encoding.UTF8.GetBytes(node.ToJsonString(Indented))).GetAwaiter().GetResult();

    private static void WriteConfig(ImportTargets targets, JsonObject config)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(targets.ConfigFile)!);
        WriteJson(targets.ConfigFile, config);
        SharedContentService.SyncLadsSettingsAcrossAllInstances(targets.Paths.BaseDirectory);
    }

    private static void CopyPack(string source, string destination)
    {
        if (File.Exists(source)) { SafeFileOps.CopyVerified(source, destination); return; }
        Directory.CreateDirectory(destination);
        foreach (var entry in new DirectoryInfo(source).EnumerateFileSystemInfos("*", PackEntries))
        {
            string relative = Path.GetRelativePath(source, entry.FullName), target = Path.Combine(destination, relative);
            if (entry is DirectoryInfo) Directory.CreateDirectory(target);
            else { Directory.CreateDirectory(Path.GetDirectoryName(target)!); File.Copy(entry.FullName, target, false); }
        }
    }

    // --- Undo ---

    // A snapshot goes stale as the player changes settings: past this it is no longer offered.
    private static readonly TimeSpan UndoWindow = TimeSpan.FromDays(7);

    /// <summary>The newest backup not yet undone and young enough to restore, or null.</summary>
    public static string? LatestBackup(ImportTargets targets)
    {
        if (!Directory.Exists(targets.BackupsDirectory)) return null;
        return Directory.GetDirectories(targets.BackupsDirectory).Where(d => DateTime.UtcNow - Directory.GetCreationTimeUtc(d) < UndoWindow)
            .OrderByDescending(Path.GetFileName, StringComparer.Ordinal)
            .FirstOrDefault(d => TryParseObject(Path.Combine(d, ManifestFile)) is { } m && ToBool(m["undone"]) == false);
    }

    /// <summary>Puts back what <see cref="Apply"/> overwrote and removes the packs it added. Paths outside the Lads folders are refused.</summary>
    public static ImportResult Undo(string backup, ImportTargets targets, LauncherSettings? live = null)
    {
        var errors = new List<string>();
        if (!targets.InGame && targets.Profiles.Any(p => RunningGameMarker.IsRunning(targets.Paths.GetProfileDirectory(p))))
        {
            errors.Add("Close the game first: it would write its own settings back over the undo.");
            return new(backup, new Dictionary<ImportCategory, int>(), errors, false, false, false);
        }
        var manifest = ParseObject(ReadBounded(Path.Combine(backup, ManifestFile)), ManifestFile);
        bool configChanged = false, options = false, pending = false;
        foreach (var entry in (manifest["files"] as JsonArray ?? new()).OfType<JsonObject>())
        {
            if (entry["path"] is not JsonValue p || !p.TryGetValue<string>(out var path)) continue;
            string? name = (entry["copy"] as JsonValue)?.GetValue<string>();
            if (!Inside(path, targets) || name != null && !SafeFileOps.IsPlainFileName(name)) { errors.Add($"Refused to restore outside the Lads folders: {Quote(path)}"); continue; }
            try
            {
                Restore(path, name == null ? null : Path.Combine(backup, name), targets);
                configChanged |= SafeFileOps.PathsEqual(path, targets.ConfigFile);
                options |= Path.GetFileName(path) == "options.txt";
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { errors.Add($"{Quote(path)}: {e.Message}"); }
        }
        foreach (var pack in (manifest["packs"] as JsonArray ?? new()).OfType<JsonValue>())
            if (pack.TryGetValue<string>(out var path))
                try { DeletePack(path, targets); }
                catch (Exception e) when (e is IOException or UnauthorizedAccessException) { errors.Add($"{Quote(path)}: {e.Message}"); }
        if (manifest["launcher"] is JsonObject launcher)
        {
            if (targets.InGame)
            {
                WriteJson(Path.Combine(targets.Paths.BaseDirectory, PendingLauncherFile), launcher.DeepClone());
                pending = true;
            }
            else
            {
                var settings = live ?? LauncherSettings.LoadFrom(targets.SettingsFile);
                foreach (var (key, value) in launcher) WriteLauncher(settings, key, value);
                settings.SaveTo(targets.SettingsFile);
            }
        }
        if (configChanged && File.Exists(targets.ConfigFile)) File.SetLastWriteTimeUtc(targets.ConfigFile, DateTime.UtcNow);
        if (configChanged) SharedContentService.SyncLadsSettingsAcrossAllInstances(targets.Paths.BaseDirectory);
        manifest["undone"] = errors.Count == 0; // a failed part stays offered, to try again
        SaveManifest(backup, manifest);
        return new(backup, new Dictionary<ImportCategory, int>(), errors, configChanged, options && targets.InGame, pending);
    }

    private static bool Inside(string path, ImportTargets targets) =>
        SafeFileOps.IsSameOrInside(path, targets.Paths.BaseDirectory) || SafeFileOps.IsSameOrInside(path, targets.SharedRoot)
        || targets.Profiles.Any(p => SafeFileOps.IsSameOrInside(path, targets.Paths.GetProfileDirectory(p)));

    private static void Restore(string file, string? copy, ImportTargets targets)
    {
        if (copy != null)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(file)!);
            File.Copy(copy, file, true);
            return;
        }
        DeleteIfThere(file);
        // The config had no copies before: a copy left in another folder would come back as the newest by the next sync.
        if (SafeFileOps.PathsEqual(file, targets.ConfigFile))
        {
            DeleteIfThere(Path.Combine(targets.Paths.BaseDirectory, Path.GetFileName(file)));
            foreach (var profile in targets.Profiles) DeleteIfThere(Path.Combine(targets.Paths.GetProfileDirectory(profile), Path.GetFileName(file)));
        }
    }

    private static void DeleteIfThere(string file)
    {
        if (File.Exists(file)) File.Delete(file);
    }

    private static void DeletePack(string path, ImportTargets targets)
    {
        if (!SafeFileOps.PathsEqual(Path.GetDirectoryName(path)!, targets.PacksDirectory)) return;
        if (File.Exists(path)) File.Delete(path);
        else if (Directory.Exists(path)) SafeFileOps.DeleteTree(path);
    }
}
