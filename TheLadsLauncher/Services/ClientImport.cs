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
    private const int MaxListed = 500, MaxProfiles = 32;
    private const long MaxPackBytes = 1L << 30;
    private const int MaxPackFiles = 20000;
    private static readonly JsonSerializerOptions Indented = new(JsonSerializerOptions.Default) { WriteIndented = true };
    private static readonly JsonDocumentOptions JsonLimits = new() { MaxDepth = 64 };
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
        if (Directory.Exists(Path.Combine(env.AppData, "Badlion Client")) || File.Exists(Path.Combine(mc, "badlion_settings.json")))
            found.Add(new("badlion", "Badlion Client", false, "Keybinds, video and audio, servers, packs, mods and HUD. Badlion's launcher settings have no readable file.", mc, mc));
        if (Directory.Exists(Path.Combine(mc, "feather", "configuration")))
            found.Add(new("feather", "Feather Client", false, "Keybinds, video and audio, servers, packs, mods and HUD. Feather's launcher settings were not verified.", mc, mc));
        if (Directory.Exists(prism) && PrismInstance(prism) is { } instance)
            found.Add(new("prism", "Prism Launcher", true, "Basic import from the last played instance, plus RAM and close-on-launch.", prism, instance));
        if (Directory.Exists(Path.Combine(mc, "labymod-neo")))
            found.Add(new("labymod", "LabyMod", true, "Basic import only: LabyMod's module settings could not be verified.", mc, mc));
        if (File.Exists(Path.Combine(mc, "options.txt")))
            found.Add(new("vanilla", "Minecraft Launcher", true, "Basic import, plus RAM and keep-launcher-open.", mc, mc));
        return found;
    }

    /// <summary>Any other client (Salwyrr, PvP Lounge, MultiMC...): the vanilla files of a game folder the player picks.</summary>
    public static ImportSource FromFolder(string folder) =>
        new("folder", Path.GetFileName(Path.TrimEndingDirectorySeparator(folder)), true,
            "Basic import only: options.txt, servers.dat and the resourcepacks folder. This client's own settings format is not read.", folder, folder);

    private static string LunarGameFolder(string lunar, string fallback)
    {
        // launcher.json names the folder Lunar plays in; an entry that is not a plain absolute path is ignored.
        var dir = (TryParseObject(Path.Combine(lunar, "settings", "launcher.json"))?["settings"] as JsonObject)?["gameDirectory"] is JsonValue v
            && v.TryGetValue<string>(out var s) ? s : null;
        return dir != null && Path.IsPathFullyQualified(dir) && !dir.StartsWith(@"\\", StringComparison.Ordinal) && Directory.Exists(dir) ? dir : fallback;
    }

    private static string? PrismInstance(string prism)
    {
        try
        {
            return Directory.EnumerateDirectories(Path.Combine(prism, "instances")).Take(256)
                .SelectMany(i => new[] { Path.Combine(i, "minecraft"), Path.Combine(i, ".minecraft") })
                .Where(d => File.Exists(Path.Combine(d, "options.txt")))
                .OrderByDescending(d => File.GetLastWriteTimeUtc(Path.Combine(d, "options.txt"))).FirstOrDefault();
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return null; }
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
            else if (!ImportedOptions.TryGetValue(key, out var range)) continue;
            else
            {
                converted = ConvertOption(key, value, range, degrees);
                if (converted == null) { Skip(plan, $"{key}: unusable value"); continue; }
            }
            plan.Options[key] = converted;
            if (current.Get(key) != converted) plan.Changes.Add($"{key} = {converted}");
        }
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
                plan.Servers.Add(new ImportedServer(name.Length > 0 ? name : ip, ip, icon));
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
    private static IEnumerable<string> ProfileFolders(ImportSource source)
    {
        var profiles = Path.Combine(source.Root, "profiles");
        return source.Id == "lunar" && Directory.Exists(profiles)
            ? Directory.EnumerateDirectories(profiles).Where(d => !SafeFileOps.IsLink(d)).Take(MaxProfiles).ToList() : Array.Empty<string>();
    }

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

    private static bool TooLarge(string path)
    {
        if (File.Exists(path)) return new FileInfo(path).Length > MaxPackBytes;
        long total = 0; int count = 0;
        foreach (var file in new DirectoryInfo(path).EnumerateFiles("*", SearchOption.AllDirectories))
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
        var folder = Path.Combine(source.Root, "feather", "configuration");
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

    /// <summary>ARGB from Lunar's {"value":argb}, Badlion's {"set","enabled","color":{red,green,blue,alpha}} (signed bytes) or Feather's
    /// "[chroma/]r/g/b/a". An unset Badlion colour is null: the client's default, nothing to carry over.</summary>
    internal static int? ToColor(JsonNode? node)
    {
        if (node is JsonValue text && text.TryGetValue<string>(out var s))
        {
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
        return obj["value"] is JsonValue value && value.TryGetValue<long>(out var argb) ? unchecked((int)argb) : null;
    }

    private static int Argb(int a, int r, int g, int b) => unchecked((int)((uint)a << 24 | (uint)r << 16 | (uint)g << 8 | (uint)b));

    // --- HUD positions ---

    /// <summary>A HUD place: per axis the point of the screen (0 start, 1 centre, 2 end), the HUD's own point, and the offset in GUI pixels.</summary>
    internal sealed record HudPlace(int RefX, int PinX, int OffX, int RefY, int PinY, int OffY);

    private static HudPlace? ReadAnchor(string client, JsonObject mod)
    {
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
            {
                var file = Path.Combine(source.Root, "prismlauncher.cfg");
                if (!File.Exists(file)) return;
                var cfg = ParseIni(Encoding.UTF8.GetString(ReadBounded(file, 1024 * 1024)));
                if (cfg.TryGetValue("MaxMemAlloc", out var max)) Memory(ToNumber(JsonValue.Create(max)), "MaxRamMb", "Maximum RAM");
                if (cfg.TryGetValue("MinMemAlloc", out var min)) Memory(ToNumber(JsonValue.Create(min)), "MinRamMb", "Minimum RAM");
                if (cfg.TryGetValue("CloseAfterLaunch", out var close) && bool.TryParse(close, out var closes)) Set("KeepLauncherOpen", JsonValue.Create(!closes), $"Keep the launcher open: {!closes}");
                if (cfg.ContainsKey("JvmArgs")) Skip(plan, "JVM arguments: the Lads launcher chooses its own for each version");
                if (cfg.ContainsKey("MinecraftWinWidth")) Skip(plan, "Game window size: the Lads launcher has no such setting");
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
            case "folder" or "labymod" or "badlion" or "feather":
                Skip(plan, $"{source.Name}: no verified launcher settings to read");
                break;
        }
    }

    /// <summary>key=value lines of a Prism or MultiMC .cfg; only the keys asked for above are looked at.</summary>
    private static Dictionary<string, string> ParseIni(string text)
    {
        var values = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var line in text.Split('\n'))
            if (line.IndexOf('=') is > 0 and var at) values[line[..at].Trim()] = line[(at + 1)..].Trim();
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
        foreach (var entry in new DirectoryInfo(source).EnumerateFileSystemInfos("*", SearchOption.AllDirectories))
        {
            if (entry.LinkTarget != null) continue; // links in a pack would reach outside it
            string relative = Path.GetRelativePath(source, entry.FullName), target = Path.Combine(destination, relative);
            if (entry is DirectoryInfo) Directory.CreateDirectory(target);
            else { Directory.CreateDirectory(Path.GetDirectoryName(target)!); File.Copy(entry.FullName, target, false); }
        }
    }

    // --- Undo ---

    /// <summary>The newest backup not yet undone, or null.</summary>
    public static string? LatestBackup(ImportTargets targets)
    {
        if (!Directory.Exists(targets.BackupsDirectory)) return null;
        return Directory.GetDirectories(targets.BackupsDirectory).OrderByDescending(Path.GetFileName, StringComparer.Ordinal)
            .FirstOrDefault(d => TryParseObject(Path.Combine(d, ManifestFile)) is { } m && ToBool(m["undone"]) == false);
    }

    /// <summary>Puts back what <see cref="Apply"/> overwrote and removes the packs it added. Paths outside the Lads folders are refused.</summary>
    public static ImportResult Undo(string backup, ImportTargets targets, LauncherSettings? live = null)
    {
        var errors = new List<string>();
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
            if (pack.TryGetValue<string>(out var path)) DeletePack(path, targets);
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
        manifest["undone"] = true;
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
