using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using Microsoft.Data.Sqlite;
using Microsoft.Win32;

namespace TheLadsLauncher.Services;

public enum ExternalLauncher { CurseForge, Modrinth, Prism, MultiMC }

/// <summary>An instance found in another launcher's data. Read-only: it is started by that launcher, never by this one.</summary>
public sealed record ExternalInstance
{
    public ExternalLauncher Launcher { get; init; }
    /// <summary>What the launcher knows it by: CurseForge's guid, the Modrinth profile path, the Prism/MultiMC folder name.</summary>
    public string Id { get; init; } = "";
    public string Name { get; init; } = "";
    public string Directory { get; init; } = "";
    /// <summary>Where mods, saves and options.txt live (Prism keeps them in a minecraft or .minecraft subfolder).</summary>
    public string GameDirectory { get; init; } = "";
    public string? McVersion { get; init; }
    /// <summary>fabric, quilt, forge, neoforge, liteloader or vanilla; null when the launcher does not say.</summary>
    public string? Loader { get; init; }
    public string? LoaderVersion { get; init; }
    public DateTime? LastPlayedUtc { get; init; }
    public string? IconFile { get; init; }
    public string? IconUrl { get; init; }
    /// <summary>The launcher's data folder (Prism's --dir, the Modrinth App folder, CurseForge's Instances folder).</summary>
    public string DataRoot { get; init; } = "";
    public int ModCount { get; init; }
}

/// <summary>A folder holding one launcher's instances; Custom when the player added it.</summary>
public sealed record ExternalSource(ExternalLauncher Launcher, string Root, bool Custom);

public sealed record ExternalScan(List<ExternalSource> Sources, List<ExternalInstance> Instances, List<string> Problems);

/// <summary>
/// Finds CurseForge, Modrinth App, Prism Launcher and MultiMC instances and starts them through their own launcher (no Lads
/// account, settings sync or mods). Only ever reads those launchers' files; the Modrinth database is read from a temp copy.
/// </summary>
public static class ExternalInstances
{
    /// <summary>QA/sandbox: ';'-separated folders that replace the auto-detected ones (each classified like a custom folder).</summary>
    public const string RootsEnvironmentVariable = "LADS_EXTERNAL_ROOTS";
    public const string CustomFoldersFile = "external-launchers.json";
    public const int CurseForgeMinecraftGameId = 432;

    public static string LauncherName(ExternalLauncher launcher) => launcher switch
    {
        ExternalLauncher.CurseForge => "CurseForge",
        ExternalLauncher.Modrinth => "Modrinth App",
        ExternalLauncher.Prism => "Prism Launcher",
        _ => "MultiMC"
    };

    /// <summary>Modrinth App has no way to start a given instance from outside (its deep links only install content).</summary>
    public static bool CanStartInstance(ExternalLauncher launcher) => launcher != ExternalLauncher.Modrinth;

    // ── Where the launchers keep their instances ────────────────────────────

    public static async Task<ExternalScan> ScanAsync(string dataDirectory) => await Task.Run(() => Scan(dataDirectory));

    public static ExternalScan Scan(string dataDirectory)
    {
        var problems = new List<string>();
        var sources = new List<ExternalSource>();
        var overrideRoots = Environment.GetEnvironmentVariable(RootsEnvironmentVariable);
        if (!string.IsNullOrWhiteSpace(overrideRoots))
            sources.AddRange(overrideRoots.Split(';', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).Select(r => Classify(r, custom: false)).OfType<ExternalSource>());
        else sources.AddRange(DefaultSources());
        foreach (var folder in LoadCustomFolders(dataDirectory))
        {
            if (Classify(folder, custom: true) is { } source) sources.Add(source);
            else problems.Add($"No CurseForge, Modrinth, Prism or MultiMC instances found in {folder}.");
        }
        sources = sources.GroupBy(s => Path.GetFullPath(s.Root).TrimEnd('\\', '/'), StringComparer.OrdinalIgnoreCase).Select(g => g.First()).ToList();

        var instances = new List<ExternalInstance>();
        foreach (var source in sources)
        {
            try { instances.AddRange(Read(source)); }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException or SqliteException or JsonException or InvalidOperationException)
            {
                problems.Add($"Could not read {LauncherName(source.Launcher)} ({source.Root}): {e.Message}");
            }
        }
        instances = instances.GroupBy(i => (i.Launcher, Path.GetFullPath(i.Directory).TrimEnd('\\', '/').ToLowerInvariant())).Select(g => g.First()).ToList();
        return new ExternalScan(sources, instances, problems);
    }

    /// <summary>The launchers' usual folders on this PC (only those that exist).</summary>
    public static List<ExternalSource> DefaultSources()
    {
        var list = new List<ExternalSource>();
        string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        string home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        string? cfRoot = CurseForgeMinecraftRoot(appData) ?? Path.Combine(home, "curseforge", "minecraft");
        void Add(ExternalLauncher launcher, string? root) { if (root != null && System.IO.Directory.Exists(root)) list.Add(new ExternalSource(launcher, root, false)); }
        Add(ExternalLauncher.CurseForge, Path.Combine(cfRoot, "Instances"));
        Add(ExternalLauncher.Modrinth, Path.Combine(appData, "ModrinthApp"));
        Add(ExternalLauncher.Modrinth, Path.Combine(appData, "com.modrinth.theseus"));
        Add(ExternalLauncher.Prism, Path.Combine(appData, "PrismLauncher"));
        // Portable Prism (Scoop / a portable.txt install keeps the data next to the exe).
        if (FindPrismExecutable(null) is { } exe && Path.GetDirectoryName(exe) is { } exeDir
            && (File.Exists(Path.Combine(exeDir, "portable.txt")) || System.IO.Directory.Exists(Path.Combine(exeDir, "UserData"))))
            Add(ExternalLauncher.Prism, System.IO.Directory.Exists(Path.Combine(exeDir, "UserData")) ? Path.Combine(exeDir, "UserData") : exeDir);
        return list;
    }

    /// <summary>CurseForge's "minecraftRoot" setting (Settings ▸ Minecraft ▸ Modpacks folder), when changed from the default.</summary>
    internal static string? CurseForgeMinecraftRoot(string appData)
    {
        try
        {
            var file = Path.Combine(appData, "CurseForge", "storage.json");
            if (!File.Exists(file)) return null;
            using var doc = JsonDocument.Parse(File.ReadAllText(file));
            if (!doc.RootElement.TryGetProperty("minecraft-settings", out var settings)) return null;
            // The value is itself a JSON string.
            using var inner = settings.ValueKind == JsonValueKind.String ? JsonDocument.Parse(settings.GetString()!) : JsonDocument.Parse(settings.GetRawText());
            return inner.RootElement.TryGetProperty("minecraftRoot", out var root) && root.ValueKind == JsonValueKind.String && root.GetString() is { Length: > 0 } path ? path : null;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { return null; }
    }

    /// <summary>
    /// What a folder the player picked is: a launcher's data folder (Prism/MultiMC with its .cfg, the Modrinth App with app.db or
    /// profiles), an instances folder (CurseForge Instances, Prism instances) or a single instance; null when none of these.
    /// </summary>
    public static ExternalSource? Classify(string folder, bool custom)
    {
        try
        {
            if (!System.IO.Directory.Exists(folder)) return null;
            if (File.Exists(Path.Combine(folder, "prismlauncher.cfg"))) return new ExternalSource(ExternalLauncher.Prism, folder, custom);
            if (File.Exists(Path.Combine(folder, "multimc.cfg"))) return new ExternalSource(ExternalLauncher.MultiMC, folder, custom);
            if (File.Exists(Path.Combine(folder, "app.db"))
                || (System.IO.Directory.Exists(Path.Combine(folder, "profiles")) && (System.IO.Directory.Exists(Path.Combine(folder, "meta")) || File.Exists(Path.Combine(folder, "settings.json")))))
                return new ExternalSource(ExternalLauncher.Modrinth, folder, custom);
            if (System.IO.Directory.Exists(Path.Combine(folder, "Instances")) && HasChild(Path.Combine(folder, "Instances"), "minecraftinstance.json"))
                return new ExternalSource(ExternalLauncher.CurseForge, Path.Combine(folder, "Instances"), custom);
            if (File.Exists(Path.Combine(folder, "minecraftinstance.json")) || HasChild(folder, "minecraftinstance.json"))
                return new ExternalSource(ExternalLauncher.CurseForge, folder, custom);
            if (File.Exists(Path.Combine(folder, "instance.cfg")) || HasChild(folder, "instance.cfg"))
                return new ExternalSource(ExternalLauncher.Prism, folder, custom);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        return null;
    }

    private static bool HasChild(string folder, string file) =>
        System.IO.Directory.Exists(folder) && System.IO.Directory.EnumerateDirectories(folder).Any(d => File.Exists(Path.Combine(d, file)));

    // ── Custom folders (launcher data/external-launchers.json) ──────────────

    private sealed class CustomFolders { public List<string> Folders { get; set; } = new(); }
    private static readonly JsonSerializerOptions Json = new() { PropertyNamingPolicy = JsonNamingPolicy.CamelCase, WriteIndented = true };

    public static List<string> LoadCustomFolders(string dataDirectory)
    {
        try
        {
            var file = Path.Combine(dataDirectory, CustomFoldersFile);
            return File.Exists(file) ? JsonSerializer.Deserialize<CustomFolders>(File.ReadAllText(file), Json)?.Folders ?? new() : new();
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { return new(); }
    }

    public static void SaveCustomFolders(string dataDirectory, IEnumerable<string> folders)
    {
        System.IO.Directory.CreateDirectory(dataDirectory);
        var file = Path.Combine(dataDirectory, CustomFoldersFile);
        var temp = file + ".tmp";
        File.WriteAllText(temp, JsonSerializer.Serialize(new CustomFolders { Folders = folders.Distinct(StringComparer.OrdinalIgnoreCase).ToList() }, Json), new UTF8Encoding(false));
        File.Move(temp, file, overwrite: true);
    }

    // ── Reading one source ──────────────────────────────────────────────────

    public static List<ExternalInstance> Read(ExternalSource source) => source.Launcher switch
    {
        ExternalLauncher.CurseForge => ReadCurseForge(source.Root),
        ExternalLauncher.Modrinth => ReadModrinth(source.Root),
        _ => ReadPrism(source.Root, source.Launcher)
    };

    private static IEnumerable<string> InstanceFolders(string root, string marker) =>
        File.Exists(Path.Combine(root, marker)) ? new[] { root } : System.IO.Directory.EnumerateDirectories(root).Where(d => File.Exists(Path.Combine(d, marker)));

    private static List<ExternalInstance> ReadCurseForge(string root)
    {
        var list = new List<ExternalInstance>();
        foreach (var dir in InstanceFolders(root, "minecraftinstance.json"))
        {
            try
            {
                if (ParseCurseForge(File.ReadAllText(Path.Combine(dir, "minecraftinstance.json")), dir, root) is { } instance)
                    list.Add(instance with { ModCount = CountMods(instance.GameDirectory) });
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { }
        }
        return list;
    }

    /// <summary>minecraftinstance.json: name, gameVersion, baseModLoader.name ("forge-47.4.0", "fabric-0.15.11-1.20.1"), guid, lastPlayed.</summary>
    public static ExternalInstance? ParseCurseForge(string json, string folder, string root)
    {
        using var doc = JsonDocument.Parse(json);
        var r = doc.RootElement;
        string? name = Str(r, "name");
        if (string.IsNullOrWhiteSpace(name)) return null;
        string? mc = Str(r, "gameVersion");
        string? loader = "vanilla", loaderVersion = null;
        if (r.TryGetProperty("baseModLoader", out var baseLoader) && baseLoader.ValueKind == JsonValueKind.Object && Str(baseLoader, "name") is { Length: > 0 } loaderName)
        {
            mc ??= Str(baseLoader, "minecraftVersion");
            (loader, loaderVersion) = SplitCurseForgeLoader(loaderName, mc);
        }
        string? icon = Str(r, "profileImagePath") is { Length: > 0 } image && File.Exists(image) ? image : null;
        string? iconUrl = r.TryGetProperty("installedModpack", out var pack) && pack.ValueKind == JsonValueKind.Object ? Str(pack, "thumbnailUrl") : null;
        return new ExternalInstance
        {
            Launcher = ExternalLauncher.CurseForge, Id = Str(r, "guid") ?? "", Name = name.Trim(), Directory = folder, GameDirectory = folder,
            McVersion = mc, Loader = loader, LoaderVersion = loaderVersion, LastPlayedUtc = Date(Str(r, "lastPlayed")),
            IconFile = icon, IconUrl = iconUrl, DataRoot = root
        };
    }

    public static (string Loader, string? Version) SplitCurseForgeLoader(string name, string? mc)
    {
        int dash = name.IndexOf('-');
        string loader = (dash < 0 ? name : name[..dash]).ToLowerInvariant();
        string? version = dash < 0 ? null : name[(dash + 1)..];
        if (version != null && mc != null && version.EndsWith("-" + mc, StringComparison.Ordinal)) version = version[..^(mc.Length + 1)];
        if (version != null && mc != null && version.StartsWith(mc + "-", StringComparison.Ordinal)) version = version[(mc.Length + 1)..];
        return (NormalizeLoader(loader) ?? "vanilla", version);
    }

    private static List<ExternalInstance> ReadPrism(string root, ExternalLauncher launcher)
    {
        // A data folder (with its .cfg) or a bare instances folder.
        string cfgFile = Path.Combine(root, launcher == ExternalLauncher.MultiMC ? "multimc.cfg" : "prismlauncher.cfg");
        var cfg = File.Exists(cfgFile) ? ParseIni(File.ReadAllText(cfgFile)) : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        string dataRoot = File.Exists(cfgFile) ? root : Path.GetDirectoryName(root.TrimEnd('\\', '/')) ?? root;
        string instancesDir = File.Exists(cfgFile) ? Resolve(root, cfg.GetValueOrDefault("InstanceDir") is { Length: > 0 } d ? d : "instances") : root;
        string iconsDir = Resolve(dataRoot, cfg.GetValueOrDefault("IconsDir") is { Length: > 0 } i ? i : "icons");
        var list = new List<ExternalInstance>();
        if (!System.IO.Directory.Exists(instancesDir)) return list;
        foreach (var dir in InstanceFolders(instancesDir, "instance.cfg"))
        {
            try
            {
                var pack = Path.Combine(dir, "mmc-pack.json");
                if (ParsePrism(File.ReadAllText(Path.Combine(dir, "instance.cfg")), File.Exists(pack) ? File.ReadAllText(pack) : null, dir, dataRoot, iconsDir, launcher) is { } instance)
                    list.Add(instance with { ModCount = CountMods(instance.GameDirectory) });
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { }
        }
        return list;
    }

    private static string Resolve(string root, string path) => Path.IsPathRooted(path) ? path : Path.GetFullPath(Path.Combine(root, path));

    /// <summary>instance.cfg (name, iconKey, lastLaunchTime in ms) + mmc-pack.json (net.minecraft and loader components).</summary>
    public static ExternalInstance? ParsePrism(string instanceCfg, string? mmcPackJson, string folder, string dataRoot, string? iconsDir, ExternalLauncher launcher = ExternalLauncher.Prism)
    {
        var cfg = ParseIni(instanceCfg);
        string id = Path.GetFileName(folder.TrimEnd('\\', '/'));
        string name = cfg.GetValueOrDefault("name") is { Length: > 0 } n ? n : id;
        var (mc, loader, loaderVersion) = mmcPackJson != null ? ParseMmcPack(mmcPackJson) : (cfg.GetValueOrDefault("IntendedVersion"), null, null);
        DateTime? played = long.TryParse(cfg.GetValueOrDefault("lastLaunchTime"), out long ms) && ms > 0 ? DateTimeOffset.FromUnixTimeMilliseconds(ms).UtcDateTime : null;
        string game = new[] { "minecraft", ".minecraft" }.Select(s => Path.Combine(folder, s)).FirstOrDefault(System.IO.Directory.Exists) ?? Path.Combine(folder, ".minecraft");
        return new ExternalInstance
        {
            Launcher = launcher, Id = id, Name = name, Directory = folder, GameDirectory = game, McVersion = mc, Loader = loader, LoaderVersion = loaderVersion,
            LastPlayedUtc = played, IconFile = PrismIcon(cfg.GetValueOrDefault("iconKey"), folder, iconsDir), DataRoot = dataRoot
        };
    }

    private static string? PrismIcon(string? key, string folder, string? iconsDir)
    {
        if (string.IsNullOrEmpty(key) || key == "default" || key.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0) return null;
        foreach (var dir in new[] { iconsDir, folder }.OfType<string>())
            foreach (var ext in new[] { ".png", ".jpg", ".jpeg", ".gif", ".ico", ".webp", "" })
            {
                var file = Path.Combine(dir, key + ext);
                if (File.Exists(file)) return file;
            }
        return null; // a built-in Prism icon (grass, flame...): drawn as the initial
    }

    /// <summary>The components that matter: net.minecraft's version and the mod loader's uid/version.</summary>
    public static (string? Mc, string? Loader, string? LoaderVersion) ParseMmcPack(string json)
    {
        using var doc = JsonDocument.Parse(json);
        string? mc = null, loader = null, loaderVersion = null;
        if (!doc.RootElement.TryGetProperty("components", out var components) || components.ValueKind != JsonValueKind.Array) return (null, null, null);
        foreach (var c in components.EnumerateArray())
        {
            string? uid = Str(c, "uid"), version = Str(c, "version") ?? Str(c, "cachedVersion");
            string? known = uid switch
            {
                "net.fabricmc.fabric-loader" => "fabric",
                "org.quiltmc.quilt-loader" => "quilt",
                "net.minecraftforge" => "forge",
                "net.neoforged" => "neoforge",
                "com.mumfrey.liteloader" => "liteloader",
                _ => null
            };
            if (uid == "net.minecraft") mc = version;
            else if (known != null && loader == null) (loader, loaderVersion) = (known, version);
        }
        return (mc, loader ?? (mc != null ? "vanilla" : null), loaderVersion);
    }

    /// <summary>key=value lines; [sections] and ; or # comments ignored (instance.cfg is flat in practice).</summary>
    public static Dictionary<string, string> ParseIni(string text)
    {
        var map = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var raw in text.Split('\n'))
        {
            var line = raw.Trim();
            if (line.Length == 0 || line[0] is '[' or ';' or '#') continue;
            int eq = line.IndexOf('=');
            if (eq <= 0) continue;
            var value = line[(eq + 1)..].Trim();
            if (value.Length >= 2 && value[0] == '"' && value[^1] == '"') value = value[1..^1].Replace("\\\"", "\"").Replace("\\\\", "\\");
            map[line[..eq].Trim()] = value;
        }
        return map;
    }

    private static List<ExternalInstance> ReadModrinth(string root)
    {
        // Profiles may live in a custom folder (the app's "App directory" setting); the database says where.
        var list = new List<ExternalInstance>();
        var db = Path.Combine(root, "app.db");
        if (File.Exists(db)) list.AddRange(ReadModrinthDatabase(db, root));
        if (list.Count == 0 && System.IO.Directory.Exists(Path.Combine(root, "profiles")))
        {
            // Older Theseus builds: profiles/<name>/profile.json; otherwise at least the folder names.
            foreach (var dir in System.IO.Directory.EnumerateDirectories(Path.Combine(root, "profiles")))
            {
                try
                {
                    var file = Path.Combine(dir, "profile.json");
                    var instance = File.Exists(file) ? ParseModrinthProfileJson(File.ReadAllText(file), dir, root) : null;
                    instance ??= new ExternalInstance { Launcher = ExternalLauncher.Modrinth, Id = Path.GetFileName(dir), Name = Path.GetFileName(dir), Directory = dir, GameDirectory = dir, DataRoot = root };
                    list.Add(instance with { ModCount = CountMods(instance.GameDirectory) });
                }
                catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { }
            }
        }
        return list;
    }

    /// <summary>Older Modrinth App profile.json: {"path", "metadata": {"name", "icon", "game_version", "loader", "loader_version", "last_played"}}.</summary>
    public static ExternalInstance? ParseModrinthProfileJson(string json, string folder, string root)
    {
        using var doc = JsonDocument.Parse(json);
        var r = doc.RootElement;
        var meta = r.TryGetProperty("metadata", out var m) && m.ValueKind == JsonValueKind.Object ? m : r;
        string? name = Str(meta, "name");
        if (string.IsNullOrWhiteSpace(name)) return null;
        string? icon = Str(meta, "icon") ?? Str(meta, "icon_path");
        return new ExternalInstance
        {
            Launcher = ExternalLauncher.Modrinth, Id = Path.GetFileName(folder.TrimEnd('\\', '/')), Name = name, Directory = folder, GameDirectory = folder,
            McVersion = Str(meta, "game_version"), Loader = NormalizeLoader(Str(meta, "loader")), LoaderVersion = LoaderVersionOf(meta),
            LastPlayedUtc = Date(Str(meta, "last_played")), IconFile = icon != null && File.Exists(icon) ? icon : null, DataRoot = root
        };
    }

    private static string? LoaderVersionOf(JsonElement meta) => meta.TryGetProperty("loader_version", out var v) ? v.ValueKind switch
    {
        JsonValueKind.String => v.GetString(),
        JsonValueKind.Object => Str(v, "id"),
        _ => null
    } : null;

    /// <summary>
    /// app.db (SQLite). Newer apps: instances + instance_content_sets (game_version, loader); 2024-2026 builds: profiles. Read from a
    /// temp copy (with its -wal) so the running app's database is never opened, locked or changed.
    /// </summary>
    public static List<ExternalInstance> ReadModrinthDatabase(string dbFile, string root)
    {
        string temp = Path.Combine(Path.GetTempPath(), "lads-modrinth-" + Guid.NewGuid().ToString("N"));
        System.IO.Directory.CreateDirectory(temp);
        try
        {
            var copy = Path.Combine(temp, "app.db");
            CopyShared(dbFile, copy);
            if (File.Exists(dbFile + "-wal")) CopyShared(dbFile + "-wal", copy + "-wal");
            using var connection = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = copy, Mode = SqliteOpenMode.ReadWrite, Pooling = false }.ToString());
            connection.Open();
            var tables = Strings(connection, "SELECT name FROM sqlite_master WHERE type = 'table'");
            string profilesDir = Path.Combine(ModrinthAppDirectory(connection, tables) ?? root, "profiles");
            string sql = tables.Contains("instances") && tables.Contains("instance_content_sets")
                ? "SELECT i.path, i.name, i.icon_path, i.last_played, cs.game_version, cs.loader, cs.loader_version FROM instances i LEFT JOIN instance_content_sets cs ON cs.id = i.applied_content_set_id"
                : tables.Contains("profiles")
                    ? "SELECT path, name, icon_path, last_played, game_version, mod_loader, mod_loader_version FROM profiles"
                    : "";
            var list = new List<ExternalInstance>();
            if (sql.Length == 0) return list;
            using var command = connection.CreateCommand();
            command.CommandText = sql;
            using var reader = command.ExecuteReader();
            while (reader.Read())
            {
                string path = reader.IsDBNull(0) ? "" : reader.GetString(0);
                if (path.Length == 0) continue;
                string dir = Path.Combine(profilesDir, path);
                string? icon = reader.IsDBNull(2) ? null : reader.GetString(2);
                list.Add(new ExternalInstance
                {
                    Launcher = ExternalLauncher.Modrinth, Id = path, Name = reader.IsDBNull(1) ? path : reader.GetString(1), Directory = dir, GameDirectory = dir,
                    LastPlayedUtc = reader.IsDBNull(3) ? null : UnixTime(reader.GetInt64(3)),
                    McVersion = reader.IsDBNull(4) ? null : reader.GetString(4), Loader = reader.IsDBNull(5) ? null : NormalizeLoader(reader.GetString(5)),
                    LoaderVersion = reader.IsDBNull(6) ? null : reader.GetString(6),
                    IconFile = icon != null && File.Exists(icon) ? icon : null, DataRoot = root, ModCount = CountMods(dir)
                });
            }
            return list;
        }
        finally
        {
            SqliteConnection.ClearAllPools();
            try { System.IO.Directory.Delete(temp, true); } catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        }
    }

    private static void CopyShared(string from, string to)
    {
        using var source = new FileStream(from, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        using var target = File.Create(to);
        source.CopyTo(target);
    }

    private static HashSet<string> Strings(SqliteConnection connection, string sql)
    {
        using var command = connection.CreateCommand();
        command.CommandText = sql;
        using var reader = command.ExecuteReader();
        var set = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        while (reader.Read()) if (!reader.IsDBNull(0)) set.Add(reader.GetString(0));
        return set;
    }

    private static string? ModrinthAppDirectory(SqliteConnection connection, HashSet<string> tables)
    {
        if (!tables.Contains("settings")) return null;
        try
        {
            using var command = connection.CreateCommand();
            command.CommandText = "SELECT custom_dir FROM settings LIMIT 1";
            return command.ExecuteScalar() is string dir && dir.Length > 0 && System.IO.Directory.Exists(dir) ? dir : null;
        }
        catch (SqliteException) { return null; } // no such column in this version
    }

    // ── Starting an instance through its own launcher ───────────────────────

    /// <summary>The launcher's program, or null when it is not installed.</summary>
    public static string? FindExecutable(ExternalLauncher launcher, string? dataRoot) => launcher switch
    {
        ExternalLauncher.CurseForge => ProtocolHandlerExe("curseforge")
            ?? Existing(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "CurseForge Windows", "CurseForge.exe")),
        ExternalLauncher.Modrinth => ProtocolHandlerExe("modrinth")
            ?? Existing(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Modrinth App", "Modrinth App.exe"))
            ?? Existing(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Modrinth App", "Modrinth App.exe")),
        ExternalLauncher.Prism => FindPrismExecutable(dataRoot),
        _ => dataRoot == null ? null : Existing(Path.Combine(dataRoot, "MultiMC.exe")) // MultiMC is always portable: the exe sits in its data folder
    };

    internal static string? FindPrismExecutable(string? dataRoot)
    {
        string local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        var candidates = new List<string?>();
        if (dataRoot != null) { candidates.Add(Path.Combine(dataRoot, "prismlauncher.exe")); candidates.Add(Path.Combine(dataRoot, "..", "prismlauncher.exe")); }
        candidates.Add(UninstallLocation("PrismLauncher") is { } install ? Path.Combine(install, "prismlauncher.exe") : null);
        candidates.Add(Path.Combine(local, "Programs", "PrismLauncher", "prismlauncher.exe"));
        candidates.Add(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "PrismLauncher", "prismlauncher.exe"));
        candidates.Add(Path.Combine(home, "scoop", "apps", "prismlauncher", "current", "prismlauncher.exe"));
        return candidates.OfType<string>().Select(Existing).FirstOrDefault(c => c != null);
    }

    /// <summary>
    /// How Play starts the instance (pure, so it is unit-tested):
    /// CurseForge: CurseForge.exe "curseforge://launch-game?instanceId=&lt;guid&gt;&amp;gameId=432" — the deep link CurseForge's own
    /// "Create shortcut" writes. Prism / MultiMC: exe [--dir &lt;data&gt;] --launch &lt;instance folder name&gt;. Modrinth App: the app
    /// opens (it cannot be told to start an instance).
    /// </summary>
    public static ProcessStartInfo LaunchCommand(ExternalInstance instance, string exe)
    {
        var start = new ProcessStartInfo(exe) { UseShellExecute = false, WorkingDirectory = Path.GetDirectoryName(exe) ?? "" };
        switch (instance.Launcher)
        {
            case ExternalLauncher.CurseForge:
                start.ArgumentList.Add($"curseforge://launch-game?instanceId={Uri.EscapeDataString(instance.Id)}&gameId={CurseForgeMinecraftGameId}");
                break;
            case ExternalLauncher.Prism:
            case ExternalLauncher.MultiMC:
                if (!IsDefaultDataRoot(instance, exe)) { start.ArgumentList.Add("--dir"); start.ArgumentList.Add(instance.DataRoot); }
                start.ArgumentList.Add("--launch");
                start.ArgumentList.Add(instance.Id);
                break;
        }
        return start;
    }

    // Prism finds its default folder itself (and a running Prism takes the launch request); --dir only for another folder.
    private static bool IsDefaultDataRoot(ExternalInstance instance, string exe)
    {
        if (string.IsNullOrEmpty(instance.DataRoot)) return true;
        string root = Path.GetFullPath(instance.DataRoot).TrimEnd('\\', '/');
        string exeDir = Path.GetFullPath(Path.GetDirectoryName(exe) ?? "").TrimEnd('\\', '/');
        string appData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), instance.Launcher == ExternalLauncher.Prism ? "PrismLauncher" : "MultiMC");
        return root.Equals(exeDir, StringComparison.OrdinalIgnoreCase) || root.Equals(Path.Combine(exeDir, "UserData"), StringComparison.OrdinalIgnoreCase)
            || root.Equals(Path.GetFullPath(appData).TrimEnd('\\', '/'), StringComparison.OrdinalIgnoreCase);
    }

    public static bool CanLaunch(ExternalInstance instance, string? exe) =>
        exe != null && (instance.Launcher != ExternalLauncher.CurseForge || Guid.TryParse(instance.Id, out _)) && (instance.Launcher is not (ExternalLauncher.Prism or ExternalLauncher.MultiMC) || instance.Id.Length > 0);

    private static string? Existing(string path) { try { var full = Path.GetFullPath(path); return File.Exists(full) ? full : null; } catch (Exception e) when (e is ArgumentException or NotSupportedException or IOException) { return null; } }

    private static string? ProtocolHandlerExe(string scheme)
    {
        if (!OperatingSystem.IsWindows()) return null;
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey($@"Software\Classes\{scheme}\shell\open\command") ?? Registry.ClassesRoot.OpenSubKey($@"{scheme}\shell\open\command");
            return key?.GetValue(null) is string command ? Existing(CommandExe(command) ?? "") : null;
        }
        catch (Exception e) when (e is System.Security.SecurityException or UnauthorizedAccessException or IOException) { return null; }
    }

    /// <summary>The program of a registry command line: "C:\x y\app.exe" "%1" → C:\x y\app.exe.</summary>
    public static string? CommandExe(string command)
    {
        command = command.Trim();
        if (command.StartsWith('"')) { int end = command.IndexOf('"', 1); return end > 1 ? command[1..end] : null; }
        int exe = command.IndexOf(".exe", StringComparison.OrdinalIgnoreCase);
        return exe > 0 ? command[..(exe + 4)] : command.Split(' ')[0];
    }

    private static string? UninstallLocation(string name)
    {
        if (!OperatingSystem.IsWindows()) return null;
        foreach (var hive in new[] { Registry.CurrentUser, Registry.LocalMachine })
        {
            try
            {
                using var key = hive.OpenSubKey($@"Software\Microsoft\Windows\CurrentVersion\Uninstall\{name}");
                if (key?.GetValue("InstallLocation") is string location && location.Length > 0) return location.Trim('"');
            }
            catch (Exception e) when (e is System.Security.SecurityException or UnauthorizedAccessException or IOException) { }
        }
        return null;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    public static int CountMods(string gameDirectory)
    {
        try
        {
            var mods = Path.Combine(gameDirectory, "mods");
            return System.IO.Directory.Exists(mods) ? System.IO.Directory.EnumerateFiles(mods).Count(f => f.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)) : 0;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return 0; }
    }

    private static string? NormalizeLoader(string? loader) => loader?.Trim().ToLowerInvariant() switch
    {
        null or "" => null,
        "neo" or "neoforged" or "neoforge" => "neoforge",
        "minecraftforge" or "forge" => "forge",
        var other => other
    };

    private static string? Str(JsonElement e, string name) => e.ValueKind == JsonValueKind.Object && e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;

    private static DateTime? Date(string? text) =>
        DateTime.TryParse(text, CultureInfo.InvariantCulture, DateTimeStyles.AdjustToUniversal | DateTimeStyles.AssumeUniversal, out var d) && d.Year > 2000 ? d : null;

    // Theseus stores seconds; tolerate milliseconds.
    private static DateTime? UnixTime(long value) => value <= 0 ? null : value > 100_000_000_000 ? DateTimeOffset.FromUnixTimeMilliseconds(value).UtcDateTime : DateTimeOffset.FromUnixTimeSeconds(value).UtcDateTime;
}
