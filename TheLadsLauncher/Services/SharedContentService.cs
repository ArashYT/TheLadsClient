using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public sealed record SharedContentReport(IReadOnlyList<string> Messages, IReadOnlyList<string> Warnings, bool Skipped,
    string? ReportPath, string? BackupPath, int Renamed, int Pending)
{
    public static SharedContentReport Empty { get; } = new(Array.Empty<string>(), Array.Empty<string>(), false, null, null, 0, 0);
}

public enum SharedFolderState { Shared, SeparateFolder, NotCreated, LinkedElsewhere, BrokenLink, GlobalFolder }

public sealed record SharedFolderStatus(string Name, string ProfilePath, string SharedPath, SharedFolderState State, string Detail);

/// <summary>Worlds and packs cannot be shared safely for this profile right now. The affected folder was left as it was (the message says so).</summary>
public sealed class SharedContentUnavailableException : Exception
{
    /// <summary>The choice to offer next to Cancel; it maps to <c>shareFolders: false</c> for that one launch and is not saved.</summary>
    public const string LaunchWithoutSharingChoice = "Launch without shared worlds/packs this time (this profile keeps its own folders)";

    public SharedContentUnavailableException(string message, Exception? inner = null) : base(message, inner) { }

    /// <summary>What the run did before stopping (moves, renames, pending items) when it got past the checks; the notice
    /// after a prepare needs it even though the prepare failed.</summary>
    public SharedContentReport? Report { get; init; }
}

/// <summary>
/// Worlds, resource packs, shader packs and the server list live once in the global Minecraft folder (the root, "G").
/// Each profile's saves/resourcepacks/shaderpacks become junctions to G; existing content is migrated without ever
/// overwriting or merging, and servers.dat is union-merged into G\servers.dat (LadsCore then reads G directly).
/// </summary>
public sealed class SharedContentService
{
    public const string RootEnvironmentVariable = "LADS_GLOBAL_MINECRAFT_DIR";
    public const string MigrationFolderName = ".lads-shared-migration";
    public const string DuplicatesFolderName = ".lads-shared-duplicates";
    public const string ServersBaseFileName = ".lads-servers-base.json";
    public static readonly IReadOnlyList<string> SharedFolders = new[] { "saves", "resourcepacks", "shaderpacks" };

    private static readonly Regex IncomingFolderName = new(@"\A\.lads-incoming-[0-9a-f]{32}\z");
    private static readonly TimeSpan MigrationLockTimeout = TimeSpan.FromSeconds(10);
    private static readonly TimeSpan ServersLockTimeout = TimeSpan.FromSeconds(3);
    private static readonly object InstanceLock = new();
    private static SharedContentService? _instance;

    private readonly string? _serverBackupsDirectory;
    private readonly ConcurrentDictionary<string, SemaphoreSlim> _gates = new(StringComparer.OrdinalIgnoreCase);
    private readonly ConcurrentDictionary<string, byte> _activeStaging = new(StringComparer.OrdinalIgnoreCase);
    private int _inFlight;

    /// <param name="root">The shared root G. Tests and QA pass a sandbox folder here.</param>
    /// <param name="serverBackupsDirectory">Where fallback server-list backups go (the launcher uses &lt;base&gt;\backups\servers).
    /// When null they stay inside each profile's migration folder.</param>
    public SharedContentService(string root, string? serverBackupsDirectory = null)
    {
        if (!Path.IsPathFullyQualified(root)) throw new ArgumentException($"The shared Minecraft folder must be an absolute path, not '{root}'.", nameof(root));
        Root = Normalize(root);
        _serverBackupsDirectory = serverBackupsDirectory;
    }

    public static SharedContentService Instance
    {
        get
        {
            lock (InstanceLock)
                return _instance ??= new SharedContentService(ResolveDefaultRoot(), Path.Combine(PathService.Instance.BaseDirectory, "backups", "servers"));
        }
    }

    /// <summary>LADS_GLOBAL_MINECRAFT_DIR, else &lt;THELADS_DIR&gt;\global-minecraft (a sandboxed launcher), else the OS default .minecraft.</summary>
    public static string ResolveDefaultRoot()
    {
        var configured = Environment.GetEnvironmentVariable(RootEnvironmentVariable);
        if (RootVariableProblem(configured) is { } problem) throw new InvalidOperationException(problem);
        if (!string.IsNullOrWhiteSpace(configured)) return configured;
        var sandbox = Environment.GetEnvironmentVariable("THELADS_DIR");
        if (!string.IsNullOrWhiteSpace(sandbox)) return Path.Combine(Path.GetFullPath(sandbox), "global-minecraft");
        return OsDefaultRoot();
    }

    /// <summary>Why a <see cref="RootEnvironmentVariable"/> value cannot be used, or null (unset, or an absolute path).
    /// Program.Main checks it before any window exists, so the launcher never starts with it.</summary>
    public static string? RootVariableProblem(string? value) =>
        string.IsNullOrWhiteSpace(value) || Path.IsPathFullyQualified(value) ? null
            : $"{RootEnvironmentVariable} must be an absolute folder path, but it is '{value}'. Fix or remove the variable and restart the launcher.";

    public static string OsDefaultRoot()
    {
        if (OperatingSystem.IsWindows())
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft");
        var home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        return OperatingSystem.IsMacOS() ? Path.Combine(home, "Library", "Application Support", "minecraft") : Path.Combine(home, ".minecraft");
    }

    /// <summary>Why worlds/packs/servers are NOT shared through the normal .minecraft folder, or null when they are.</summary>
    public string? RedirectedRootNotice => SafeFileOps.PathsEqual(Root, OsDefaultRoot()) ? null
        : Environment.GetEnvironmentVariable(RootEnvironmentVariable) is { Length: > 0 }
            ? $"Worlds, packs and servers are shared from '{Root}' because {RootEnvironmentVariable} is set, not from '{OsDefaultRoot()}'."
            : $"Worlds, packs and servers are shared from '{Root}' because THELADS_DIR is set (an isolated launcher folder), not from '{OsDefaultRoot()}'. Set {RootEnvironmentVariable} to choose the shared folder explicitly.";

    public string Root { get; }
    public string SavesDirectory => Path.Combine(Root, "saves");
    public string ResourcePacksDirectory => Path.Combine(Root, "resourcepacks");
    public string ShaderPacksDirectory => Path.Combine(Root, "shaderpacks");
    public string ScreenshotsDirectory => Path.Combine(Root, "screenshots");
    public string ServersFile => Path.Combine(Root, "servers.dat");
    private string ServersLockFile => Path.Combine(Root, ".lads-servers.lock");

    /// <summary>Test seam for volumes without reparse points (FAT32/exFAT).</summary>
    public Func<string, bool> SupportsReparsePoints { get; init; } = SafeFileOps.VolumeSupportsReparsePoints;

    /// <summary>Test seam for a shared folder on another drive: moving a migrated entry into it returns false (nothing moved),
    /// so the copy-verify-keep-original path runs.</summary>
    public Func<string, string, bool> MoveIntoShared { get; init; } = SafeFileOps.MoveNoCopy;

    /// <summary>True while a profile is being prepared or migrated; the auto-updater must not restart the launcher then.</summary>
    public bool IsBusy => Volatile.Read(ref _inFlight) > 0;

    /// <summary>
    /// Links the profile's saves/resourcepacks/shaderpacks to the shared folders, migrates what the profile had, and merges
    /// its server lists into the shared one. Idempotent and resumable. Runs one at a time per game folder (a second caller waits).
    /// </summary>
    /// <param name="coreEnabled">LadsCore reads the shared servers.dat itself; without it the profile gets a synced copy.</param>
    /// <param name="shareFolders">False only after the user chose <see cref="SharedContentUnavailableException.LaunchWithoutSharingChoice"/>:
    /// the server list is still handled, the folders are left alone for this launch.</param>
    /// <param name="keepOwnFolders">Minecraft 1.8.9 (<see cref="GameVersionPolicy.KeepsOwnWorlds"/>): the folders are never
    /// linked or migrated, and a link this service made is removed (the shared content stays). Only the server list is shared.
    /// A profile that is the global folder itself, or has a folder linked anywhere else, is refused with InvalidOperationException.</param>
    /// <exception cref="SharedContentUnavailableException">A folder cannot be shared safely; it was left as it was (the other
    /// folders are still shared) and the message says what to fix.</exception>
    public async Task<SharedContentReport> PrepareProfileAsync(string gameDirectory, string profileLabel, string? legacySharedServersFile,
        bool coreEnabled, IProgress<string>? progress = null, CancellationToken cancellationToken = default, bool shareFolders = true,
        bool keepOwnFolders = false)
    {
        var game = Normalize(gameDirectory);
        var gate = _gates.GetOrAdd(game, _ => new SemaphoreSlim(1, 1));
        Interlocked.Increment(ref _inFlight);
        try
        {
            if (!gate.Wait(0))
            {
                progress?.Report("Waiting for the shared-content check already running for this profile...");
                await gate.WaitAsync(cancellationToken);
            }
            try
            {
                var run = new MigrationRun(this, game, profileLabel, legacySharedServersFile, coreEnabled, shareFolders, keepOwnFolders, progress, cancellationToken);
                return await Task.Run(run.ExecuteAsync, cancellationToken);
            }
            finally
            {
                gate.Release();
            }
        }
        finally
        {
            Interlocked.Decrement(ref _inFlight);
        }
    }

    /// <summary>
    /// Startup pass: prepares every existing game folder in turn. Running profiles are skipped; a failure in one profile is
    /// reported in its entry and does not stop the others.
    /// </summary>
    public async Task<IReadOnlyDictionary<string, SharedContentReport>> PrepareAllAsync(
        IEnumerable<(string GameDirectory, string ProfileLabel, bool CoreEnabled)> profiles, string? legacySharedServersFile,
        IProgress<string>? progress = null, CancellationToken cancellationToken = default)
    {
        var results = new Dictionary<string, SharedContentReport>(StringComparer.OrdinalIgnoreCase);
        foreach (var (gameDirectory, label, coreEnabled) in profiles)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var game = Normalize(gameDirectory);
            // A profile that was never launched has nothing to migrate; it is prepared at its first launch.
            if (results.ContainsKey(game) || !Directory.Exists(game)) continue;
            try
            {
                results[game] = await PrepareProfileAsync(game, label, legacySharedServersFile, coreEnabled, progress, cancellationToken);
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                // Keep what the run did before it stopped (e.g. renamed worlds) so the notice can still list it.
                var partial = (ex as SharedContentUnavailableException)?.Report ?? SharedContentReport.Empty;
                results[game] = partial with { Warnings = partial.Warnings.Append($"{label}: {ex.Message}").ToList(), Skipped = true };
            }
        }
        return results;
    }

    /// <summary>
    /// Folds edits a game made to the profile's servers.dat copy (only used while LadsCore is disabled) back into the
    /// shared list: 3-way by SHA-256 against &lt;gameDir&gt;\.lads-servers-base.json. Safe to call any number of times.
    /// </summary>
    public async Task<SharedContentReport> ReconcileFallbackServersAsync(string gameDirectory, CancellationToken cancellationToken = default)
    {
        var game = Normalize(gameDirectory);
        if (!File.Exists(Path.Combine(game, ServersBaseFileName))) return SharedContentReport.Empty;
        if (RunningGameMarker.IsRunning(game))
            return new SharedContentReport(new[] { "The game is still running; its server list is merged after it closes." }, Array.Empty<string>(),
                true, null, null, 0, 0);
        var messages = new List<string>();
        var warnings = new List<string>();
        using (await LockFiles.AcquireAsync(ServersLockFile, ServersLockTimeout, cancellationToken))
            await ReconcileLockedAsync(game, messages, warnings, cancellationToken);
        return new SharedContentReport(messages, warnings, false, null, messages.Count + warnings.Count > 0 ? ServerBackupsFor(game) : null, 0, 0);
    }

    /// <summary>Per shared folder: is the profile's folder linked to the shared one (for UI badges). Changes nothing.</summary>
    public IReadOnlyList<SharedFolderStatus> GetStatus(string gameDirectory)
    {
        var game = Normalize(gameDirectory);
        var direct = UsesRootDirectly(game);
        return SharedFolders.Select(folder =>
        {
            var link = Path.Combine(game, folder);
            var target = Path.Combine(Root, folder);
            if (direct) return new SharedFolderStatus(folder, link, target, SharedFolderState.GlobalFolder, "This profile uses the global folder directly.");
            if (SafeFileOps.IsLink(link))
            {
                if (!IsOurLink(link, target))
                    return new SharedFolderStatus(folder, link, target, SharedFolderState.LinkedElsewhere, $"Links to '{SafeFileOps.GetLinkTarget(link)}' instead of the shared folder.");
                return Directory.Exists(target) && CanList(target)
                    ? new SharedFolderStatus(folder, link, target, SharedFolderState.Shared, $"Shared with every version: '{target}'.")
                    : new SharedFolderStatus(folder, link, target, SharedFolderState.BrokenLink, $"The shared folder '{target}' cannot be opened; it is checked again at the next launch.");
            }
            return Directory.Exists(link)
                ? new SharedFolderStatus(folder, link, target, SharedFolderState.SeparateFolder, "Separate folder; its content moves into the shared folder at the next launch.")
                : new SharedFolderStatus(folder, link, target, SharedFolderState.NotCreated, "Linked to the shared folder at the next launch.");
        }).ToList();
    }

    /// <summary>True when <paramref name="path"/> is inside (not equal to) one of the profile's shared-folder links.</summary>
    public bool IsInsideSharedLink(string path, string gameDirectory)
    {
        var full = Normalize(path);
        var game = Normalize(gameDirectory);
        var direct = UsesRootDirectly(game);
        return SharedFolders.Select(folder => Path.Combine(game, folder)).Any(link =>
            SafeFileOps.IsSameOrInside(full, link) && !SafeFileOps.PathsEqual(full, link) && (direct || SafeFileOps.IsLink(link)));
    }

    /// <summary>
    /// Whether the profile wants LadsCore: the explicit <c>mods.theladscore.enabled</c> key of lads-mod-state.json, else enabled
    /// (design rev2 R1: the mods folder is ambiguous for Core, so the installer enables it and moves a leftover
    /// theladscore.jar.disabled aside). A corrupt state file counts as no key and is described in <paramref name="stateFileError"/>.
    /// </summary>
    public static bool IsCoreRequested(string gameDirectory, out string? stateFileError)
    {
        stateFileError = null;
        var statePath = Path.Combine(gameDirectory, "lads-mod-state.json");
        if (File.Exists(statePath))
        {
            try
            {
                using var stream = new FileStream(statePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
                if (JsonNode.Parse(stream) is JsonObject state && state["mods"] is JsonObject mods && mods["theladscore"] is JsonObject core
                    && core["enabled"] is JsonValue enabled && enabled.TryGetValue(out bool value))
                    return value;
            }
            catch (Exception e) when (e is JsonException or ArgumentException)
            {
                stateFileError = $"'{statePath}' is not valid ({e.Message}), so LadsCore stays enabled until the file is fixed.";
            }
        }
        return true;
    }

    /// <summary>
    /// A staging folder <c>&lt;sharedDirectory&gt;\.lads-incoming-&lt;guid&gt;</c> (e.g. for a resource-pack download) so the final
    /// rename into the shared folder stays on one volume. Migrations in this process leave it alone until it is disposed;
    /// dispose deletes whatever is still in it. Left over after a crash, the next migration removes it.
    /// </summary>
    public IncomingFolder CreateIncomingFolder(string sharedDirectory)
    {
        var path = Path.Combine(sharedDirectory, ".lads-incoming-" + Guid.NewGuid().ToString("N"));
        _activeStaging[path] = 0;
        try
        {
            Directory.CreateDirectory(path);
        }
        catch
        {
            _activeStaging.TryRemove(path, out _);
            throw;
        }
        return new IncomingFolder(path, () =>
        {
            try
            {
                SafeFileOps.DeleteTree(path);
            }
            finally
            {
                _activeStaging.TryRemove(path, out _);
            }
        });
    }

    public sealed class IncomingFolder : IDisposable
    {
        private readonly Action _release;
        private bool _disposed;
        internal IncomingFolder(string path, Action release) { Path = path; _release = release; }
        public string Path { get; }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;
            _release();
        }
    }

    /// <summary>Reads a migration report (JSON Lines). A torn last line from an interrupted run is ignored.</summary>
    public static IReadOnlyList<JsonObject> ReadReport(string reportPath)
    {
        var lines = File.ReadAllLines(reportPath);
        var entries = new List<JsonObject>();
        for (var i = 0; i < lines.Length; i++)
        {
            if (lines[i].Length == 0) continue;
            try
            {
                entries.Add(JsonNode.Parse(lines[i])!.AsObject());
            }
            catch (JsonException) when (i == lines.Length - 1)
            {
                break; // the run was interrupted while writing this line
            }
        }
        return entries;
    }

    private static string Normalize(string path) => Path.TrimEndingDirectorySeparator(Path.GetFullPath(path));

    private bool UsesRootDirectly(string game) => SafeFileOps.PathsEqual(SafeFileOps.GetFinalPath(game), SafeFileOps.GetFinalPath(Root));

    private static bool IsOurLink(string link, string target)
    {
        var linkTarget = SafeFileOps.GetLinkTarget(link);
        if (linkTarget == null) return false;
        if (SafeFileOps.PathsEqual(linkTarget, target)) return true;
        return Directory.Exists(target) && Directory.Exists(linkTarget)
            && SafeFileOps.PathsEqual(SafeFileOps.GetFinalPath(link), SafeFileOps.GetFinalPath(target));
    }

    private static bool CanList(string folder)
    {
        try
        {
            using var entries = Directory.EnumerateFileSystemEntries(folder).GetEnumerator();
            entries.MoveNext();
            return true;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            return false;
        }
    }

    private static string Hash(byte[] data) => Convert.ToHexString(SHA256.HashData(data));

    private static string ServerKey(string ip) => ip.Trim().ToLowerInvariant();

    private string ServerBackupsFor(string game) => _serverBackupsDirectory ?? Path.Combine(game, MigrationFolderName, "server-backups");

    /// <summary>Reads a server list, retrying I/O errors (Minecraft may be replacing it). Returns null when neither it nor its
    /// "_old" copy exists.</summary>
    private static async Task<byte[]?> ReadWithRetryAsync(string path, CancellationToken cancellationToken)
    {
        for (var attempt = 1; ; attempt++)
        {
            try
            {
                if (File.Exists(path)) return ServerListFile.ReadBytes(path);
                // Minecraft saves by renaming servers.dat to servers.dat_old, then moving the new file in: missing next to
                // _old can be the middle of a save, so it is waited for. Still missing, the game died between the two renames
                // and _old is the list; treating it as "no list" would replace (and a later save delete) the real one.
                if (!File.Exists(path + "_old")) return null;
                if (attempt >= 5) return ServerListFile.ReadBytes(path + "_old");
            }
            catch (IOException) when (attempt < 5)
            {
                // Retried after the delay; the fifth failure propagates.
            }
            await Task.Delay(200, cancellationToken);
        }
    }

    /// <summary>
    /// Union merge by server address (trimmed, case-insensitive). Shared entries keep their order and fields, except that a
    /// server visible in <paramref name="other"/> stays visible; servers only in <paramref name="other"/> are appended with all
    /// their fields. Returns the number of changes.
    /// </summary>
    private static int MergeServers(ServerListFile shared, ServerListFile other, string sourceLabel, List<string> messages)
    {
        var known = new Dictionary<string, ServerListEntry>();
        foreach (var entry in shared.Entries)
            if (ServerKey(entry.Ip).Length > 0) known.TryAdd(ServerKey(entry.Ip), entry);
        int added = 0, shown = 0, withoutAddress = 0;
        foreach (var entry in other.Entries)
        {
            var key = ServerKey(entry.Ip);
            if (key.Length == 0) { withoutAddress++; continue; }
            if (known.TryGetValue(key, out var existing))
            {
                if (existing.Hidden && !entry.Hidden)
                {
                    existing.Raw["hidden"] = new NbtNumber(NbtTagType.Byte, 0);
                    known[key] = existing with { Hidden = false };
                    shown++;
                }
                if (!string.Equals(existing.Name, entry.Name, StringComparison.Ordinal))
                    messages.Add($"Server {entry.Ip.Trim()}: kept the shared name '{existing.Name}' ({sourceLabel} called it '{entry.Name}').");
                continue;
            }
            shared.Add(entry.Raw);
            known[key] = entry;
            added++;
        }
        if (added > 0) messages.Add($"Added {added} server(s) from {sourceLabel} to the shared server list.");
        if (shown > 0) messages.Add($"{shown} server(s) hidden in the shared list are visible again because {sourceLabel} listed them.");
        if (withoutAddress > 0) messages.Add($"{withoutAddress} server entr(ies) without an address in {sourceLabel} were not merged.");
        return added + shown;
    }

    private string WriteServerBackup(string game, byte[] data, string kind)
    {
        var directory = ServerBackupsFor(game);
        Directory.CreateDirectory(directory);
        var stamp = DateTime.Now.ToString("yyyyMMdd-HHmmss-fff", CultureInfo.InvariantCulture);
        File.WriteAllBytes(Path.Combine(directory, $"servers-{stamp}-{kind}.dat"), data);
        foreach (var old in Directory.GetFiles(directory, "servers-*.dat").OrderByDescending(Path.GetFileName, StringComparer.Ordinal).Skip(10))
            File.Delete(old);
        return directory;
    }

    private static async Task WriteServersBaseAsync(string game, string hash, CancellationToken cancellationToken)
    {
        var json = new JsonObject { ["sha256"] = hash, ["copiedAt"] = DateTime.UtcNow.ToString("o", CultureInfo.InvariantCulture) };
        await LockFiles.WriteAtomicallyAsync(Path.Combine(game, ServersBaseFileName), Encoding.UTF8.GetBytes(json.ToJsonString()), cancellationToken);
    }

    /// <summary>Caller holds the servers lock. Ends with profile copy == shared list == base, or leaves everything untouched.</summary>
    private async Task ReconcileLockedAsync(string game, List<string> messages, List<string> warnings, CancellationToken cancellationToken)
    {
        var basePath = Path.Combine(game, ServersBaseFileName);
        if (!File.Exists(basePath)) return;
        var profileFile = Path.Combine(game, "servers.dat");
        var profileBytes = await ReadWithRetryAsync(profileFile, cancellationToken);
        if (profileBytes == null)
        {
            File.Delete(basePath); // nothing left to fold back
            return;
        }
        string? baseHash = null;
        try
        {
            baseHash = JsonNode.Parse(File.ReadAllText(basePath))?["sha256"]?.GetValue<string>();
        }
        catch (Exception e) when (e is JsonException or InvalidOperationException)
        {
            warnings.Add($"'{basePath}' is unreadable ({e.Message}); the profile's server list is merged without removing anything.");
        }
        var profileHash = Hash(profileBytes);
        if (profileHash == baseHash) return;

        ServerListFile profile;
        try
        {
            profile = ServerListFile.Parse(profileBytes);
        }
        catch (InvalidDataException e)
        {
            // Kept next to the migration records (never pruned like the rolling backups).
            var kept = Path.Combine(game, MigrationFolderName, $"servers-unreadable-{DateTime.Now.ToString("yyyyMMdd-HHmmss-fff", CultureInfo.InvariantCulture)}.dat");
            Directory.CreateDirectory(Path.GetDirectoryName(kept)!);
            var read = File.Exists(profileFile) ? profileFile : profileFile + "_old"; // the file ReadWithRetryAsync returned
            if (!SafeFileOps.MoveNoCopy(read, kept)) throw new IOException($"Could not move '{read}' to '{kept}': different drives.");
            File.Delete(basePath);
            warnings.Add($"The server list in '{game}' is unreadable ({e.Message}). It was kept as '{kept}' and the shared list was not changed.");
            return;
        }

        var sharedBytes = await ReadWithRetryAsync(ServersFile, cancellationToken);
        byte[] result;
        if (sharedBytes == null || Hash(sharedBytes) == baseHash)
        {
            if (sharedBytes != null) WriteServerBackup(game, sharedBytes, "shared");
            result = profileBytes;
            await LockFiles.WriteAtomicallyAsync(ServersFile, result, cancellationToken);
            messages.Add($"Saved the server list changes made in '{game}' to the shared server list.");
        }
        else
        {
            ServerListFile shared;
            try
            {
                shared = ServerListFile.Parse(sharedBytes);
            }
            catch (InvalidDataException e)
            {
                warnings.Add($"The shared server list '{ServersFile}' is unreadable ({e.Message}). It was left unchanged; the profile's copy in '{game}' is kept and merged later.");
                return;
            }
            WriteServerBackup(game, sharedBytes, "shared");
            var backups = WriteServerBackup(game, profileBytes, "profile");
            var changes = MergeServers(shared, profile, $"'{game}'", messages);
            result = changes > 0 ? shared.ToBytes() : sharedBytes;
            if (changes > 0) await LockFiles.WriteAtomicallyAsync(ServersFile, result, cancellationToken);
            warnings.Add($"The server list was changed both in '{game}' (without LadsCore) and elsewhere. Both were merged and nothing was removed; backups are in '{backups}'.");
        }
        if (Hash(result) != profileHash) await LockFiles.WriteAtomicallyAsync(profileFile, result, cancellationToken);
        await WriteServersBaseAsync(game, Hash(result), cancellationToken);
    }

    /// <summary>One PrepareProfileAsync call: its report, backups and hash cache.</summary>
    private sealed class MigrationRun
    {
        private readonly SharedContentService _s;
        private readonly string _game;
        private readonly string _label;
        private readonly string? _legacy;
        private readonly bool _coreEnabled;
        private readonly bool _shareFolders;
        private readonly bool _ownFolders;
        private readonly IProgress<string>? _progress;
        private readonly CancellationToken _ct;
        private readonly List<string> _messages = new();
        private readonly List<string> _warnings = new();
        private readonly Dictionary<(string, long, DateTime), string> _hashes = new();
        private string? _stampDir;
        private string? _reportPath;
        private string? _duplicatesDir;
        private int _renamed;

        public MigrationRun(SharedContentService service, string game, string label, string? legacy, bool coreEnabled, bool shareFolders,
            bool ownFolders, IProgress<string>? progress, CancellationToken cancellationToken)
        {
            _s = service;
            _game = game;
            _label = label;
            _legacy = legacy;
            _coreEnabled = coreEnabled;
            _shareFolders = shareFolders;
            _ownFolders = ownFolders;
            _progress = progress;
            _ct = cancellationToken;
        }

        private string MigrationRoot => Path.Combine(_game, MigrationFolderName);

        public async Task<SharedContentReport> ExecuteAsync()
        {
            if (_ownFolders && SafeFileOps.PathsEqual(SafeFileOps.GetFinalPath(_s.Root), SafeFileOps.GetFinalPath(_game)))
                throw new InvalidOperationException($"'{_label}' uses the global Minecraft folder '{_s.Root}' as its game folder, and every newer version shares its worlds and packs. Minecraft 1.8.9 corrupts newer worlds, so it needs a game folder of its own. Nothing was changed.");
            if (RunningGameMarker.IsRunning(_game))
                return Skip("Skipped: the game is running for this profile. Shared content is checked again at the next launch.");
            Directory.CreateDirectory(_game);
            var finalRoot = SafeFileOps.GetFinalPath(_s.Root);
            var finalGame = SafeFileOps.GetFinalPath(_game);
            if (SafeFileOps.PathsEqual(finalRoot, finalGame))
                return Skip($"This profile uses the global folder '{_s.Root}' directly, so its worlds, packs and server list are already shared.");
            if (_shareFolders && !_ownFolders) Validate(finalRoot, finalGame);

            using var migrationLock = await LockFiles.AcquireAsync(Path.Combine(MigrationRoot, ".lock"), MigrationLockTimeout, _ct);
            // Every folder is checked (a foreign link throws) before any link is removed or the server list is touched.
            if (_ownFolders)
                foreach (var folder in SharedFolders.Where(IsSharedLinkToRemove).ToList()) RemoveSharedLink(folder);
            _progress?.Report("Checking the shared server list...");
            await PrepareServersAsync();
            // Pending items were meant for the shared folders before this became a 1.8.9 folder: they wait for a newer version.
            if (_ownFolders) return new SharedContentReport(_messages, _warnings, false, _reportPath, _stampDir, 0, 0);
            // One folder that cannot be shared does not keep the others from being shared; the problems are raised together.
            var problems = new List<string>();
            if (_shareFolders)
            {
                foreach (var folder in SharedFolders)
                {
                    _ct.ThrowIfCancellationRequested();
                    _progress?.Report($"Checking shared {folder}...");
                    try
                    {
                        PrepareFolder(folder);
                    }
                    catch (SharedContentUnavailableException e)
                    {
                        problems.Add(e.Message);
                    }
                }
            }
            else
            {
                _warnings.Add("Shared worlds, resource packs and shader packs are off for this launch; this profile uses its own folders.");
            }

            var pending = CountPending();
            if (pending > 0)
                _warnings.Add($"{pending} item(s) are still waiting in '{MigrationRoot}' and are retried at the next launch.");
            var report = new SharedContentReport(_messages, _warnings, false, _reportPath, _duplicatesDir ?? _stampDir, _renamed, pending);
            if (problems.Count > 0) throw new SharedContentUnavailableException(string.Join(" ", problems)) { Report = report };
            return report;
        }

        private static SharedContentReport Skip(string message) =>
            new(new[] { message }, Array.Empty<string>(), true, null, null, 0, 0);

        private static SharedContentUnavailableException Unavailable(string message) => new(message);

        private void Validate(string finalRoot, string finalGame)
        {
            if (SafeFileOps.IsNetworkPath(finalRoot))
                throw Unavailable($"The shared Minecraft folder '{_s.Root}' is on a network location ('{finalRoot}'). Shared worlds and packs need it on a local drive. Nothing was changed.");
            if (SafeFileOps.IsNetworkPath(finalGame) || !_s.SupportsReparsePoints(finalGame))
                throw Unavailable($"Shared worlds/packs need the profile game folder on a local NTFS drive: '{_game}'. Move the profile's game folder to a local NTFS drive, or launch without shared worlds/packs this time. Nothing was changed.");
            if (SafeFileOps.IsSameOrInside(finalRoot, finalGame))
                throw Unavailable($"The shared Minecraft folder '{_s.Root}' is inside this profile's game folder '{_game}'. Use a game folder outside it. Nothing was changed.");
            foreach (var folder in SharedFolders)
            {
                var shared = Path.Combine(_s.Root, folder);
                var finalShared = SafeFileOps.GetFinalPath(shared);
                if (SafeFileOps.IsSameOrInside(finalShared, finalGame))
                    throw Unavailable($"'{shared}' leads into this profile's game folder ('{finalShared}'). Make '{shared}' a normal folder again (move the content, then remove the link), and try again. Nothing was changed.");
                if (SafeFileOps.IsSameOrInside(finalGame, finalShared))
                    throw Unavailable($"This profile's game folder '{_game}' is inside the shared folder '{finalShared}'. Use a game folder outside it. Nothing was changed.");
            }
        }

        // ---------------------------------------------------------------- folders

        /// <summary>1.8.9: true when the folder is this service's link to the shared folder (it is removed). A link anywhere else
        /// is refused, since it may lead to newer worlds or packs.</summary>
        private bool IsSharedLinkToRemove(string folder)
        {
            var link = Path.Combine(_game, folder);
            if (!SafeFileOps.IsLink(link)) return false;
            if (IsOurLink(link, Path.Combine(_s.Root, folder))) return true;
            throw new InvalidOperationException($"'{link}' is a link to '{SafeFileOps.GetLinkTarget(link)}'. Minecraft 1.8.9 needs a {folder} folder of its own because it corrupts newer worlds. Remove that link (the folder it leads to is not changed) and launch again. Nothing was changed.");
        }

        private void RemoveSharedLink(string folder)
        {
            var link = Path.Combine(_game, folder);
            var target = Path.Combine(_s.Root, folder);
            Append("unlink", link, target, "Minecraft 1.8.9 keeps its own " + folder);
            SafeFileOps.RemoveLink(link);
            _messages.Add($"Minecraft 1.8.9 keeps its own {folder}: the link to the shared folder '{target}' was removed. The shared content was not changed.");
        }

        private void PrepareFolder(string folder)
        {
            var link = Path.Combine(_game, folder);
            var target = Path.Combine(_s.Root, folder);
            string? movedTo = null;
            if (SafeFileOps.IsLink(link))
            {
                if (IsOurLink(link, target))
                {
                    var problem = ProveLink(link, target);
                    if (problem != null)
                        throw Unavailable($"The shared {folder} link '{link}' → '{target}' does not work ({problem}). Check that '{target}' exists and can be opened; the link was left as it was.");
                    ProcessPending(folder, target);
                    return;
                }
                var other = SafeFileOps.GetLinkTarget(link)!;
                if (Directory.Exists(other) && Directory.EnumerateFileSystemEntries(other).Any())
                    throw Unavailable($"'{link}' links to '{other}' instead of the shared folder '{target}', and that folder has content. Move its content into '{target}' (or remove the link '{link}' yourself), then try again. The link was left as it was.");
                Append("relink", link, target, $"the old link target '{other}' was missing or empty");
                SafeFileOps.RemoveLink(link);
                _messages.Add($"Re-linked {folder}: its old target '{other}' was missing or empty.");
            }
            else if (File.Exists(link))
            {
                _warnings.Add($"'{link}' is a file, not a folder, so {folder} is not shared for this profile. Rename or remove that file.");
                return;
            }
            else if (Directory.Exists(link))
            {
                if (!Directory.EnumerateFileSystemEntries(link).Any())
                {
                    Directory.Delete(link);
                }
                else
                {
                    var pending = Path.Combine(EnsureStamp(), "pending", folder);
                    Directory.CreateDirectory(Path.GetDirectoryName(pending)!);
                    Append("move-to-pending", link, pending, null);
                    try
                    {
                        if (!SafeFileOps.MoveNoCopy(link, pending)) throw new IOException("the migration folder is on another drive");
                    }
                    catch (Exception e) when (e is IOException or UnauthorizedAccessException)
                    {
                        Append("move-to-pending-failed", link, pending, e.Message);
                        RemoveIfEmpty(Path.GetDirectoryName(pending)!);
                        _warnings.Add($"{folder} stays a separate folder for this profile this time: '{link}' could not be moved ({e.Message}). Close programs that use it; it is retried at the next launch.");
                        return;
                    }
                    movedTo = pending;
                }
            }

            string? failure;
            try
            {
                SafeFileOps.CreateJunction(link, target);
                failure = ProveLink(link, target);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                failure = e.Message;
            }
            if (failure != null)
            {
                Restore(folder, link, movedTo, failure);
                throw Unavailable($"Could not link '{link}' to the shared folder '{target}' ({failure}). The {folder} folder was put back as it was.");
            }
            // Linking an empty profile folder is not worth a migration record of its own.
            if (_stampDir != null) Append("link", link, target, null);
            _messages.Add($"Linked {folder} to the shared folder '{target}'.");
            ProcessPending(folder, target);
        }

        /// <summary>Creates the shared folder if needed (so the link never dangles), then returns null when the link opens and
        /// resolves to it, else the problem.</summary>
        private static string? ProveLink(string link, string target)
        {
            try
            {
                Directory.CreateDirectory(target);
                using (var entries = Directory.EnumerateFileSystemEntries(link).GetEnumerator()) entries.MoveNext();
                return SafeFileOps.PathsEqual(SafeFileOps.GetFinalPath(link), SafeFileOps.GetFinalPath(target)) ? null : "it opens a different folder";
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                return e.Message;
            }
        }

        /// <summary>Exact reverse of the rename to pending, so a failed link leaves the profile as it was.</summary>
        private void Restore(string folder, string link, string? movedTo, string reason)
        {
            if (SafeFileOps.IsLink(link)) SafeFileOps.RemoveLink(link);
            if (movedTo == null) return;
            if (Directory.Exists(link) && !Directory.EnumerateFileSystemEntries(link).Any()) Directory.Delete(link);
            try
            {
                if (!SafeFileOps.MoveNoCopy(movedTo, link)) throw new IOException("different drives");
            }
            catch (IOException e)
            {
                throw new IOException($"Linking {folder} failed ({reason}) and the folder could not be put back ({e.Message}). Your {folder} content is safe in '{movedTo}'; move it back to '{link}'.", e);
            }
            Append("restore", movedTo, link, reason);
            RemoveIfEmpty(Path.GetDirectoryName(movedTo)!);
        }

        private void ProcessPending(string folder, string target)
        {
            RemoveIncompleteCopies(target);
            if (!Directory.Exists(MigrationRoot)) return;
            int moved = 0, identical = 0;
            foreach (var stampDir in Directory.GetDirectories(MigrationRoot).OrderBy(Path.GetFileName, StringComparer.Ordinal))
            {
                var pending = Path.Combine(stampDir, "pending", folder);
                if (!Directory.Exists(pending) || SafeFileOps.IsLink(pending)) continue;
                foreach (var entry in Directory.GetFileSystemEntries(pending).OrderBy(Path.GetFileName, StringComparer.Ordinal))
                {
                    _ct.ThrowIfCancellationRequested();
                    _progress?.Report($"Moving {folder}: {Path.GetFileName(entry)}...");
                    try
                    {
                        switch (ProcessEntry(folder, target, entry))
                        {
                            case EntryOutcome.Moved: moved++; break;
                            case EntryOutcome.Duplicate: identical++; break;
                        }
                    }
                    catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException)
                    {
                        Append("failed", entry, target, e.Message);
                        _warnings.Add($"{folder}: '{Path.GetFileName(entry)}' could not be moved into the shared folder ({e.Message}). It stays in '{pending}' and is retried at the next launch.");
                    }
                }
                RemoveIfEmpty(pending);
                RemoveIfEmpty(Path.Combine(stampDir, "pending"));
                RemoveIfEmpty(stampDir);
            }
            if (moved > 0) _messages.Add($"Moved {moved} item(s) into the shared {folder} folder.");
            if (identical > 0) _messages.Add($"{identical} {folder} item(s) were identical to the shared copies; this profile's copies are kept in '{_duplicatesDir}'.");
        }

        private enum EntryOutcome { Moved, Renamed, Duplicate, Skipped }

        private EntryOutcome ProcessEntry(string folder, string target, string entry)
        {
            var name = Path.GetFileName(entry);
            if (name.StartsWith("_0EuphoriaPatches_", StringComparison.OrdinalIgnoreCase) || name.StartsWith("_0EUPHORIA_PATCHES_", StringComparison.OrdinalIgnoreCase))
            {
                // Euphoria Patcher regenerates its output inside the shared folder; the profile's copy is only a backup.
                MoveToDuplicates(folder, entry, "Euphoria Patcher output, recreated by the patcher");
                return EntryOutcome.Skipped;
            }
            var destination = Path.Combine(target, name);
            if (!Exists(destination))
            {
                MoveInto(folder, target, entry, destination);
                return EntryOutcome.Moved;
            }
            if (folder == "saves" && IsWorldLocked(destination))
            {
                Append("skipped-locked", entry, destination, "the shared world is open in a running game");
                _warnings.Add($"saves: '{name}' was not moved because the shared world with that name is open in a running game. It stays in '{Path.GetDirectoryName(entry)}' and is retried at the next launch.");
                return EntryOutcome.Skipped;
            }
            if (Identical(entry, destination))
            {
                MoveToDuplicates(folder, entry, "identical to the shared copy");
                return EntryOutcome.Duplicate;
            }
            var renamed = CollisionName(name, File.Exists(entry), target);
            MoveInto(folder, target, entry, Path.Combine(target, renamed));
            _renamed++;
            _messages.Add($"{folder}: {name} → {renamed} (both kept)");
            return EntryOutcome.Renamed;
        }

        private void MoveInto(string folder, string target, string source, string destination)
        {
            if (_s.MoveIntoShared(source, destination))
            {
                Append("move", source, destination, null);
                return;
            }
            // Another drive: copy one level below a staging folder (never seen as a world or pack), verify, then rename into place.
            using (var stage = _s.CreateIncomingFolder(target))
            {
                var staged = Path.Combine(stage.Path, Path.GetFileName(destination));
                SafeFileOps.CopyVerified(source, staged);
                if (!SafeFileOps.MoveNoCopy(staged, destination)) throw new IOException($"'{stage.Path}' and '{destination}' are on different drives.");
            }
            Append("copy", source, destination, "copied from another drive and verified");
            MoveToDuplicates(folder, source, "original of a verified copy on another drive");
        }

        private void MoveToDuplicates(string folder, string source, string reason)
        {
            EnsureStamp();
            _duplicatesDir = Path.Combine(_game, DuplicatesFolderName, Path.GetFileName(_stampDir)!);
            var directory = Path.Combine(_duplicatesDir, folder);
            Directory.CreateDirectory(directory);
            var destination = UniquePath(Path.Combine(directory, Path.GetFileName(source)));
            if (!SafeFileOps.MoveNoCopy(source, destination)) throw new IOException($"'{directory}' is on a different drive than '{source}'.");
            Append("duplicate", source, destination, reason);
        }

        private void RemoveIncompleteCopies(string target)
        {
            foreach (var stage in Directory.GetDirectories(target, ".lads-incoming-*"))
            {
                if (!IncomingFolderName.IsMatch(Path.GetFileName(stage)) || _s._activeStaging.ContainsKey(stage)) continue;
                Append("remove-incomplete-copy", stage, null, "left over from an interrupted copy");
                SafeFileOps.DeleteTree(stage);
            }
        }

        private static bool IsWorldLocked(string world)
        {
            var lockFile = Path.Combine(world, "session.lock");
            if (!File.Exists(lockFile)) return false;
            try
            {
                using var stream = new FileStream(lockFile, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
                stream.Lock(0, 1);
                stream.Unlock(0, 1);
                return false;
            }
            catch (IOException e) when (LockFiles.IsSharingOrLockViolation(e))
            {
                return true;
            }
        }

        private bool Identical(string a, string b)
        {
            if (SafeFileOps.IsLink(a) || SafeFileOps.IsLink(b)) return false;
            try
            {
                if (File.Exists(a) && File.Exists(b))
                    return new FileInfo(a).Length == new FileInfo(b).Length && FileHash(a) == FileHash(b);
                if (!Directory.Exists(a) || !Directory.Exists(b)) return false;
                var left = SafeFileOps.ListTree(a);
                var right = SafeFileOps.ListTree(b);
                if (left.Links.Count > 0 || right.Links.Count > 0) return false;
                static bool Compared(string relative) => !Path.GetFileName(relative).Equals("session.lock", StringComparison.OrdinalIgnoreCase);
                var leftFiles = left.Files.Where(f => Compared(f.Key)).ToList();
                if (leftFiles.Count != right.Files.Count(f => Compared(f.Key))
                    || leftFiles.Any(f => !right.Files.TryGetValue(f.Key, out var length) || length != f.Value)) return false;
                return leftFiles.All(f => FileHash(Path.Combine(a, f.Key)) == FileHash(Path.Combine(b, f.Key)));
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                return false; // unreadable counts as different: both copies are kept
            }
        }

        private string FileHash(string path)
        {
            var info = new FileInfo(path);
            var key = (info.FullName, info.Length, info.LastWriteTimeUtc);
            if (!_hashes.TryGetValue(key, out var hash)) _hashes[key] = hash = SafeFileOps.Sha256(path);
            return hash;
        }

        private string CollisionName(string name, bool isFile, string directory)
        {
            var tag = $"({SanitizeLabel(_label)} {DateTime.Now.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)})";
            var stem = isFile ? Path.GetFileNameWithoutExtension(name) : name;
            var extension = isFile ? Path.GetExtension(name) : "";
            for (var n = 1; ; n++)
            {
                var candidate = n == 1 ? $"{stem} {tag}{extension}" : $"{stem} {tag} {n}{extension}";
                if (!Exists(Path.Combine(directory, candidate))) return candidate;
            }
        }

        private static string SanitizeLabel(string label)
        {
            var invalid = Path.GetInvalidFileNameChars();
            var clean = new string(label.Select(c => invalid.Contains(c) ? '_' : c).ToArray()).Trim().TrimEnd('.', ' ');
            if (clean.Length > 40) clean = clean[..40].TrimEnd('.', ' ');
            return clean.Length == 0 ? "profile" : clean;
        }

        private static bool Exists(string path) => File.Exists(path) || Directory.Exists(path);

        private static string UniquePath(string path)
        {
            var candidate = path;
            for (var n = 2; Exists(candidate); n++) candidate = $"{path} {n}";
            return candidate;
        }

        private static void RemoveIfEmpty(string directory)
        {
            if (Directory.Exists(directory) && !SafeFileOps.IsLink(directory) && !Directory.EnumerateFileSystemEntries(directory).Any())
                Directory.Delete(directory);
        }

        private int CountPending()
        {
            if (!Directory.Exists(MigrationRoot)) return 0;
            return Directory.GetDirectories(MigrationRoot)
                .Select(stamp => Path.Combine(stamp, "pending"))
                .Where(Directory.Exists)
                .SelectMany(Directory.GetDirectories)
                .Sum(folder => Directory.GetFileSystemEntries(folder).Length);
        }

        // ---------------------------------------------------------------- servers

        private async Task PrepareServersAsync()
        {
            FileStream serversLock;
            try
            {
                serversLock = await LockFiles.AcquireAsync(_s.ServersLockFile, ServersLockTimeout, _ct);
            }
            catch (IOException e)
            {
                _warnings.Add($"The server list was not checked this time: {e.Message}");
                return;
            }
            using (serversLock)
            {
                try
                {
                    await _s.ReconcileLockedAsync(_game, _messages, _warnings, _ct);
                    await MergeServerListsAsync();
                }
                catch (Exception e) when (e is IOException or UnauthorizedAccessException)
                {
                    _warnings.Add($"The server list step stopped early ({e.Message}); it continues at the next launch.");
                }
            }
        }

        private async Task MergeServerListsAsync()
        {
            byte[]? sharedBytes;
            ServerListFile shared;
            try
            {
                sharedBytes = await ReadWithRetryAsync(_s.ServersFile, _ct);
                shared = sharedBytes == null ? ServerListFile.CreateEmpty() : ServerListFile.Parse(sharedBytes);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException)
            {
                // Never move or replace the only copy of the shared list: skip the whole step instead.
                _warnings.Add($"The shared server list '{_s.ServersFile}' could not be read ({e.Message}). It was left unchanged, and no server lists were merged or moved this time.");
                return;
            }

            // A tracked copy (base file present) was already folded back by the reconcile; an untracked one is merged here,
            // also when only its servers.dat_old is left (a game killed while saving).
            var profileFile = Path.Combine(_game, "servers.dat");
            var tracked = File.Exists(Path.Combine(_game, ServersBaseFileName));
            var changes = 0;
            if (sharedBytes != null && !File.Exists(_s.ServersFile))
            {
                // The shared list survived only as servers.dat_old: it is the canonical list and is written back as servers.dat.
                changes++;
                _messages.Add($"'{_s.ServersFile}' was missing (a game closed while saving it); the server list was restored from servers.dat_old.");
            }
            if (!tracked && (File.Exists(profileFile) || File.Exists(profileFile + "_old")))
            {
                var profile = await ParseInputAsync(profileFile, "This profile's server list");
                if (profile != null) changes += MergeServers(shared, profile, "this profile", _messages);
            }

            string? legacyHash = null;
            if (_legacy != null && File.Exists(_legacy))
            {
                var legacyBytes = await ReadWithRetryAsync(_legacy, _ct);
                var markerPath = _legacy + ".lads-merged";
                if (legacyBytes != null && (!File.Exists(markerPath) || File.ReadAllText(markerPath).Trim() != Hash(legacyBytes)))
                {
                    legacyHash = Hash(legacyBytes);
                    try
                    {
                        changes += MergeServers(shared, ServerListFile.Parse(legacyBytes), "the old launcher's shared list", _messages);
                    }
                    catch (InvalidDataException e)
                    {
                        _warnings.Add($"The old launcher's server list '{_legacy}' is unreadable ({e.Message}); it was left in place and not merged.");
                    }
                }
            }

            if (changes > 0)
            {
                if (sharedBytes != null)
                {
                    var backup = UniquePath(Path.Combine(ServersBackupFolder(), "servers.dat.canonical-before"));
                    File.WriteAllBytes(backup, sharedBytes);
                    Append("backup", _s.ServersFile, backup, "shared server list before merging");
                }
                sharedBytes = shared.ToBytes();
                await LockFiles.WriteAtomicallyAsync(_s.ServersFile, sharedBytes, _ct);
                Append("merge-servers", null, _s.ServersFile, $"{changes} change(s)");
            }
            if (legacyHash != null) File.WriteAllText(_legacy + ".lads-merged", legacyHash);

            if (_coreEnabled)
            {
                // LadsCore reads and writes the shared file directly; the profile copy is retired as a backup.
                foreach (var name in new[] { "servers.dat", "servers.dat_old" })
                    if (File.Exists(Path.Combine(_game, name))) MoveToServersBackup(Path.Combine(_game, name), "LadsCore uses the shared server list");
                if (tracked) File.Delete(Path.Combine(_game, ServersBaseFileName));
            }
            else if (sharedBytes != null)
            {
                // Without LadsCore the game reads the profile copy: give it the shared list and remember what it started from.
                var hash = Hash(sharedBytes);
                var current = File.Exists(profileFile) ? await ReadWithRetryAsync(profileFile, _ct) : null;
                var copied = current == null || Hash(current) != hash;
                if (copied)
                {
                    if (current != null && !tracked) File.WriteAllBytes(UniquePath(Path.Combine(ServersBackupFolder(), "servers.dat")), current);
                    await LockFiles.WriteAtomicallyAsync(profileFile, sharedBytes, _ct);
                    Append("copy-servers-to-profile", _s.ServersFile, profileFile, "LadsCore is disabled for this profile");
                }
                // A tracked copy that already equals the shared list has a valid base (the reconcile guarantees it).
                if (copied || !tracked) await WriteServersBaseAsync(_game, hash, _ct);
            }
        }

        /// <summary>Parses a non-shared input. An unreadable one is moved aside (kept) and null is returned.</summary>
        private async Task<ServerListFile?> ParseInputAsync(string path, string description)
        {
            var bytes = await ReadWithRetryAsync(path, _ct);
            if (bytes == null) return null;
            try
            {
                return ServerListFile.Parse(bytes);
            }
            catch (InvalidDataException e)
            {
                MoveToServersBackup(File.Exists(path) ? path : path + "_old", "unreadable, kept");
                _warnings.Add($"{description} '{path}' is unreadable ({e.Message}); it was kept in '{ServersBackupFolder()}'.");
                return null;
            }
        }

        private string ServersBackupFolder()
        {
            var directory = Path.Combine(EnsureStamp(), "servers");
            Directory.CreateDirectory(directory);
            return directory;
        }

        private void MoveToServersBackup(string file, string reason)
        {
            var destination = UniquePath(Path.Combine(ServersBackupFolder(), Path.GetFileName(file)));
            if (!SafeFileOps.MoveNoCopy(file, destination)) throw new IOException($"Could not move '{file}' to '{destination}': different drives.");
            Append("move-servers", file, destination, reason);
        }

        // ---------------------------------------------------------------- report

        /// <summary>Creates this run's stamp folder on first use and records the inventory before anything is moved.</summary>
        private string EnsureStamp()
        {
            if (_stampDir != null) return _stampDir;
            var stamp = DateTime.Now.ToString("yyyyMMdd-HHmmss", CultureInfo.InvariantCulture);
            var directory = Path.Combine(MigrationRoot, stamp);
            for (var n = 2; Directory.Exists(directory); n++) directory = Path.Combine(MigrationRoot, $"{stamp}-{n}");
            Directory.CreateDirectory(directory);
            _stampDir = directory;
            _reportPath = Path.Combine(directory, "report.jsonl");
            WriteInventory(Path.Combine(directory, "inventory.json"));
            return directory;
        }

        private void Append(string action, string? source, string? destination, string? detail)
        {
            EnsureStamp();
            var line = new JsonObject
            {
                ["time"] = DateTime.UtcNow.ToString("o", CultureInfo.InvariantCulture),
                ["action"] = action,
                ["source"] = source,
                ["destination"] = destination,
                ["detail"] = detail
            }.ToJsonString() + "\n";
            using var stream = new FileStream(_reportPath!, FileMode.Append, FileAccess.Write, FileShare.Read);
            stream.Write(Encoding.UTF8.GetBytes(line));
            stream.Flush(flushToDisk: true);
        }

        private void WriteInventory(string path)
        {
            var folders = new JsonArray();
            foreach (var (owner, parent) in new[] { ("profile", _game), ("shared", _s.Root) })
                foreach (var folder in SharedFolders)
                    folders.Add(FolderInventory(owner, Path.Combine(parent, folder), folder));
            var inventory = new JsonObject
            {
                ["createdAt"] = DateTime.UtcNow.ToString("o", CultureInfo.InvariantCulture),
                ["gameDirectory"] = _game,
                ["sharedRoot"] = _s.Root,
                ["folders"] = folders,
                ["serverLists"] = new JsonArray(ServerListInventory("profile", Path.Combine(_game, "servers.dat")),
                    ServerListInventory("legacy", _legacy), ServerListInventory("shared", _s.ServersFile))
            };
            File.WriteAllText(path, inventory.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
        }

        private static JsonObject FolderInventory(string owner, string path, string folder)
        {
            var node = new JsonObject { ["owner"] = owner, ["folder"] = folder, ["path"] = path };
            if (SafeFileOps.IsLink(path)) { node["linkTarget"] = SafeFileOps.GetLinkTarget(path); return node; }
            if (!Directory.Exists(path)) { node["exists"] = false; return node; }
            var entries = new JsonArray();
            foreach (var entry in new DirectoryInfo(path).EnumerateFileSystemInfos("*", new EnumerationOptions { AttributesToSkip = 0 }))
            {
                var item = new JsonObject { ["name"] = entry.Name, ["modified"] = entry.LastWriteTimeUtc.ToString("o", CultureInfo.InvariantCulture) };
                if (entry.LinkTarget != null) { item["kind"] = "link"; item["linkTarget"] = entry.LinkTarget; }
                else if (entry is FileInfo file) { item["kind"] = "file"; item["size"] = file.Length; item["fileCount"] = 1; }
                else
                {
                    item["kind"] = "directory";
                    try
                    {
                        var tree = SafeFileOps.ListTree(entry.FullName);
                        item["size"] = tree.Files.Values.Sum();
                        item["fileCount"] = tree.Files.Count;
                    }
                    catch (Exception e) when (e is IOException or UnauthorizedAccessException)
                    {
                        item["error"] = e.Message;
                    }
                }
                entries.Add(item);
            }
            node["entries"] = entries;
            return node;
        }

        private static JsonObject ServerListInventory(string role, string? path)
        {
            var node = new JsonObject { ["role"] = role, ["path"] = path };
            if (path == null || !File.Exists(path)) { node["exists"] = false; return node; }
            var data = ServerListFile.ReadBytes(path);
            node["size"] = data.Length;
            node["sha256"] = Hash(data);
            try
            {
                node["entryCount"] = ServerListFile.Parse(data).Entries.Count;
            }
            catch (InvalidDataException e)
            {
                node["error"] = e.Message;
            }
            return node;
        }
    }
}
