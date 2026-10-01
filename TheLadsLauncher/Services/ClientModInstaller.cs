using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>Installs a reviewed, pinned Fabric pack. Upstream jars are downloaded directly, never repackaged,
/// and keep their original file names. Explicit choices in lads-mod-state.json decide enabled/disabled; without one
/// the current disk state is kept.</summary>
public static class ClientModInstaller
{
    public sealed record Entry(string ProjectId, string ProjectSlug, string Name, string ModId,
        string VersionId, string Version, string FileName, string Url, string Sha512, long Size,
        string License, string? SourceUrl, string ProjectUrl);
    /// <summary>A mod removed from the pack; its published hashes identify managed copies to retire.</summary>
    public sealed record RetiredEntry(string ModId, string? ProjectId, string? Name, List<string>? Sha512, string? Reason);
    public sealed record Manifest(string MinecraftVersion, List<Entry> Mods, bool ResolveThroughApi = false, List<RetiredEntry>? Retired = null);
    private sealed record LocalJar(string Path, string Hash, bool Disabled, FabricModInfo? Info)
    {
        public string? Id => Info?.Id;
    }
    private sealed record Change(string Destination, string? Staged, string? PreviousHash, string? NewHash, string BackupName, string Status,
        string? BackupPath = null);
    private sealed record Move(string From, string To, string Hash);
    private const long MaximumJarSize = FabricModMetadata.MaximumJarSize;
    private const string DisabledSuffix = ".disabled";
    private static readonly JsonSerializerOptions Json = new() { PropertyNameCaseInsensitive = true, WriteIndented = true };
    private static readonly HttpClient Http = CreateClient();
    private static readonly SemaphoreSlim InstallLock = new(1, 1);
    private static readonly StringComparer Paths = OperatingSystem.IsWindows() ? StringComparer.OrdinalIgnoreCase : StringComparer.Ordinal;
    private static readonly string[] ReservedNames = { "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7",
        "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" };
    private static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromMinutes(3) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("TheLadsClient/1.1");
        return client;
    }

    public static async Task InstallAsync(string bundleRoot, string gameDir, string minecraftVersion,
        Action<string>? status = null, CancellationToken cancellationToken = default, HttpClient? httpClient = null)
    {
        GameVersionPolicy.ValidateMinecraftVersion(minecraftVersion);
        cancellationToken.ThrowIfCancellationRequested();
        var bundle = Path.GetFullPath(bundleRoot);
        var game = Path.GetFullPath(gameDir);
        var manifestPath = SafeChild(bundle, Path.Combine(bundle, "game-mods", minecraftVersion, "client-mods.json"));
        // No Lads pack for this version: an empty pack, so saved choices still rename the jars in Mods and dependencies are
        // checked. Nothing is retired: without a manifest the receipt says nothing about what left the pack.
        var packShipped = File.Exists(manifestPath);
        if (!packShipped)
        {
            if (GameVersionPolicy.RequiresBundledCore(minecraftVersion))
                throw new FileNotFoundException($"Client mod manifest for {minecraftVersion} is missing. Reinstall the complete launcher folder.", manifestPath);
            if (!Directory.Exists(Path.Combine(game, "mods"))) return;
        }
        var manifest = packShipped ? ReadManifest(await File.ReadAllTextAsync(manifestPath, cancellationToken))
            : new Manifest(minecraftVersion, new List<Entry>());
        if (manifest.MinecraftVersion != minecraftVersion || manifest.Mods is null || manifest.Mods.Count > 256
            || manifest.Mods.Any(m => m is null)
            || manifest.Mods.Select(m => m.ModId).Distinct(StringComparer.Ordinal).Count() != manifest.Mods.Count)
            throw new InvalidDataException("Client mod manifest does not match this Minecraft version or contains duplicate mod IDs.");
        foreach (var entry in manifest.Mods) Validate(entry);
        if (manifest.Mods.Select(m => m.FileName).Distinct(StringComparer.OrdinalIgnoreCase).Count() != manifest.Mods.Count)
            throw new InvalidDataException("Client mod manifest contains duplicate file names.");
        var retiredHashes = RetiredHashes(manifest);
        var client = httpClient ?? Http;

        await InstallLock.WaitAsync(cancellationToken);
        // Only unique files created by this invocation may be cleaned up.
        var staged = new Dictionary<string, string?>(Paths);
        try
        {
            var mods = SafeChild(game, Path.Combine(game, "mods"));
            var cache = SafeChild(game, Path.Combine(game, ".lads-mod-cache"));
            Directory.CreateDirectory(mods);
            Directory.CreateDirectory(cache);
            var receiptPath = SafeChild(game, Path.Combine(cache, "installed.json"));
            var receiptBytes = File.Exists(receiptPath) ? await File.ReadAllBytesAsync(receiptPath, cancellationToken) : null;
            var originalReceipt = ReadReceipt(receiptBytes);
            var receipt = new Dictionary<string, string>(originalReceipt, StringComparer.Ordinal);
            var receiptHash = receiptBytes is null ? null : Hash(receiptBytes);
            var preferencesPath = SafeChild(game, Path.Combine(game, ModPreferences.FileName));
            var preferencesBytes = ModPreferences.ReadShared(preferencesPath);
            var preferencesHash = preferencesBytes is null ? null : Hash(preferencesBytes);
            var preferences = ModPreferences.Parse(preferencesBytes, preferencesPath);
            var rendererBlocked = GraphicsRenderer.Suspended(game);
            var rendererChoices = new Dictionary<string, (bool Enabled, string? Project)>();
            var rendererJars = new Dictionary<string, ModPreferences.RendererJar?>();
            if (preferences.Error != null) status?.Invoke(preferences.Error);
            var inventory = await ReadInventory(game, mods, minecraftVersion, cancellationToken);
            // The planned final Mods folder. Every change updates it in commit order, so later checks see earlier moves.
            var final = inventory.ToDictionary(j => j.Path, Paths);
            var changes = new List<Change>();
            var desired = manifest.Mods.Select(m => m.ModId).ToHashSet(StringComparer.Ordinal);
            var reservedBackups = new HashSet<string>(Paths);
            var retiredDirectories = new HashSet<string>(Paths);

            // Plan retirements now, but do not move anything until every download and dependency verifies.
            // Only copies with Lads-ownership evidence are retired; anything else is the user's and stays.
            var retireIds = receipt.Keys.Where(id => packShipped && !desired.Contains(id)).Concat(retiredHashes.Keys).ToHashSet(StringComparer.Ordinal);
            // Disabled copies whose file name was the only record of the choice (v1.2.2, or renamed by hand): id -> project id.
            var keepDisabled = new Dictionary<string, string?>(StringComparer.Ordinal);
            foreach (var jar in inventory.Where(j => j.Id != null && retireIds.Contains(j.Id)))
            {
                var id = jar.Id!;
                var published = retiredHashes.TryGetValue(id, out var hashes) && hashes.Contains(jar.Hash);
                var owned = receipt.TryGetValue(id, out var ownedHash) && (SameHash(jar.Hash, ownedHash) || published);
                if (!owned && !(published && IsLegacyName(Path.GetFileName(jar.Path), id)))
                {
                    status?.Invoke($"Kept '{jar.Path}': {id} is no longer part of the Lads pack and this copy was added or modified by you.");
                    continue;
                }
                var project = manifest.Retired?.FirstOrDefault(r => r?.ModId == id)?.ProjectId;
                if (jar.Disabled && preferences.GetEnabled(id, project) == null && inventory.Where(j => j.Id == id).All(j => j.Disabled))
                    keepDisabled[id] = project;
                var directory = SafeChild(game, Path.Combine(cache, "retired", id));
                changes.Add(new(jar.Path, null, jar.Hash, null, "retired-" + id, $"Retired {id}.",
                    UniquePath(directory, Path.GetFileName(jar.Path), reservedBackups)));
                final.Remove(jar.Path);
                retiredDirectories.Add(directory);
            }
            foreach (var id in retireIds) receipt.Remove(id);

            var pending = new List<(Entry Entry, string Destination, string? PreviousHash)>();
            var disabledByChoice = new Dictionary<string, string>(StringComparer.Ordinal);
            foreach (var entry in manifest.Mods)
            {
                cancellationToken.ThrowIfCancellationRequested();
                var target = SafeChild(game, Path.Combine(mods, entry.FileName));
                var copies = final.Values.Where(j => j.Id == entry.ModId).OrderBy(j => j.Path, Paths).ToList();
                originalReceipt.TryGetValue(entry.ModId, out var receiptPin);
                bool Managed(LocalJar jar) => SameHash(jar.Hash, entry.Sha512) || (receiptPin != null && SameHash(jar.Hash, receiptPin));
                int NameRank(LocalJar jar) => BaseName(jar.Path).Equals(entry.FileName, StringComparison.OrdinalIgnoreCase) ? 0
                    : IsLegacyName(Path.GetFileName(jar.Path), entry.ModId) ? 1 : 2;
                var wantEnabled = preferences.GetEnabled(entry.ModId, entry.ProjectId)
                    ?? !(copies.Any(j => j.Disabled) && copies.All(j => j.Disabled));
                if (rendererBlocked.Contains(entry.ModId))
                {
                    if (preferences.GetEnabled(entry.ModId, entry.ProjectId) == null)
                        rendererChoices[entry.ModId] = (wantEnabled, entry.ProjectId);
                    wantEnabled = false;
                    status?.Invoke(entry.Name + ": suspended for Vulkan; available with OpenGL.");
                }
                // Keep one copy: enabling prefers an enabled copy, then Lads-managed bytes; disabling prefers the managed copy (it is
                // renamed; your own extra disabled copy may stay), then a disabled one. Then the original, legacy and other names.
                var primary = (wantEnabled ? copies.OrderBy(j => j.Disabled ? 1 : 0).ThenBy(j => Managed(j) ? 0 : 1)
                        : copies.OrderBy(j => Managed(j) ? 0 : 1).ThenBy(j => j.Disabled ? 0 : 1))
                    .ThenBy(NameRank).FirstOrDefault();
                foreach (var other in copies.Where(j => j != primary))
                {
                    var bothManaged = Managed(other) && Managed(primary!);
                    // Fabric never loads a disabled copy, so your extra one may stay while the mod stays disabled (as in v1.2.2).
                    if (!bothManaged && !wantEnabled && other.Disabled) continue;
                    if (!bothManaged)
                        throw new IOException($"Duplicate {entry.Name} mods: '{primary!.Path}' and '{other.Path}'. Fabric cannot load two copies; " +
                            "move one of them out of Mods, then retry. Your files were preserved.");
                    changes.Add(new(other.Path, null, other.Hash, null, "duplicate-" + entry.ModId, $"Moved a duplicate {entry.Name} to backup."));
                    final.Remove(other.Path);
                }

                if (primary == null)
                {
                    if (!wantEnabled)
                    {
                        disabledByChoice[entry.ModId] = entry.Name;
                        continue; // Disabled by choice: not downloaded.
                    }
                    if (Occupied(target, final, inventory))
                        throw new IOException($"Cannot replace unrecognized file '{target}'. Move it aside and retry.");
                    pending.Add((entry, target, null));
                    continue;
                }
                if (!Managed(primary))
                {
                    if (wantEnabled)
                        throw new IOException($"Your {entry.Name} jar '{primary.Path}' differs from the tested {minecraftVersion} pack. " +
                            $"Delete your copy (Mods page) or disable {entry.Name}, then retry; your file has been preserved.");
                    if (!primary.Disabled) Rename(primary, primary.Path + DisabledSuffix, entry.ModId, false);
                    continue;
                }

                var pinned = SameHash(primary.Hash, entry.Sha512);
                // Verified pinned bytes are Lads-managed under any name.
                if (pinned) receipt[entry.ModId] = entry.Sha512;
                var legacyName = NameRank(primary) == 1;
                if (wantEnabled && !pinned)
                {
                    // Upgrade, also when re-enabling a disabled older pin; the old copy goes to backup.
                    if (Paths.Equals(primary.Path, target))
                    {
                        final.Remove(target);
                        pending.Add((entry, target, primary.Hash));
                        continue;
                    }
                    changes.Add(new(primary.Path, null, primary.Hash, null, "previous-" + entry.ModId, $"Replaced {entry.Name}."));
                    final.Remove(primary.Path);
                    if (Occupied(target, final, inventory))
                        throw new IOException($"Cannot replace unrecognized file '{target}'. Move it aside and retry.");
                    pending.Add((entry, target, null));
                    continue;
                }
                // User-chosen names are kept; legacy lads-<id>.jar names get the release's original name.
                var name = legacyName ? entry.FileName : BaseName(primary.Path);
                if (legacyName && !pinned && !wantEnabled)
                    name = await OriginalFileName(client, primary.Hash, entry, status, cancellationToken) ?? Path.GetFileName(primary.Path)[..^(primary.Disabled ? DisabledSuffix.Length : 0)];
                var destination = SafeChild(game, Path.Combine(mods, wantEnabled ? name : name + DisabledSuffix));
                if (!Paths.Equals(destination, primary.Path))
                    Rename(primary, destination, entry.ModId, legacyName && wantEnabled != primary.Disabled);
            }

            // Jars outside the pack follow explicit choices by rename only; LadsCore is handled by BundledModInstaller.
            foreach (var jar in final.Values.Where(j => j.Id != null && !desired.Contains(j.Id) && j.Id != BundledModInstaller.CoreModId).ToList())
            {
                var want = preferences.GetEnabled(jar.Id!, null);
                if (rendererBlocked.Contains(jar.Id!))
                {
                    // A disabled older copy must not overwrite the id's active choice. Read the
                    // original inventory because earlier renames have already changed final.
                    if (want == null) rendererChoices.TryAdd(jar.Id!, (inventory.Any(copy => copy.Id == jar.Id && !copy.Disabled), null));
                    if (!jar.Disabled) rendererJars[jar.Id!] = new(BaseName(jar.Path), jar.Hash);
                    want = false;
                }
                else if (preferences.GetRendererSuspendedJar(jar.Id!) is { } suspended)
                {
                    if (want == true)
                    {
                        if (!final.Values.Any(copy => copy.Id == jar.Id && !copy.Disabled))
                        {
                            var restore = final.Values.FirstOrDefault(copy => copy.Id == jar.Id
                                && Paths.Equals(BaseName(copy.Path), suspended.FileName) && SameHash(copy.Hash, suspended.Sha512));
                            if (restore == null)
                                throw new IOException($"Cannot restore {jar.Id}: the previously active '{suspended.FileName}' was changed or removed. " +
                                    "Restore that copy, disable this mod in Mods, or manually enable your chosen jar file, then retry. Your files were preserved.");
                            if (!Paths.Equals(jar.Path, restore.Path)) continue;
                        }
                        rendererJars[jar.Id!] = null;
                    }
                    else if (want == false) rendererJars[jar.Id!] = null;
                }
                if (want == null || want == !jar.Disabled) continue;
                if (want == true && final.Values.Any(j => j.Id == jar.Id && !j.Disabled)) continue; // Never a second enabled copy.
                Rename(jar, want == true ? jar.Path[..^DisabledSuffix.Length] : jar.Path + DisabledSuffix, jar.Id!, false);
            }

            using (var slots = new SemaphoreSlim(4))
                await Task.WhenAll(pending.Select(async item =>
                {
                    await slots.WaitAsync(cancellationToken);
                    try { await Download(game, cache, item.Entry, client, status, cancellationToken,
                        manifest.ResolveThroughApi, minecraftVersion); }
                    finally { slots.Release(); }
                }));

            foreach (var (entry, destination, previousHash) in pending)
            {
                if (Occupied(destination, final, inventory))
                    throw new IOException($"Cannot replace unrecognized file '{destination}'. Move it aside and retry.");
                var temp = SafeChild(game, destination + "." + Guid.NewGuid().ToString("N") + ".tmp");
                var cached = SafeChild(game, Path.Combine(cache, entry.Sha512 + ".jar"));
                await using (var input = new FileStream(cached, FileMode.Open, FileAccess.Read, FileShare.Read, 81920, true))
                await using (var output = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, true))
                {
                    staged.Add(temp, null);
                    await input.CopyToAsync(output, cancellationToken);
                    await output.FlushAsync(cancellationToken);
                }
                var actual = await HashAsync(game, temp, cancellationToken);
                staged[temp] = actual;
                if (!SameHash(actual, entry.Sha512))
                    throw new InvalidDataException($"Cached download changed for {entry.Name}. Retry the launch.");
                var info = ReadMod(temp, minecraftVersion, cancellationToken);
                if (info?.Id != entry.ModId) throw new InvalidDataException($"Wrong mod downloaded for {entry.Name}.");
                receipt[entry.ModId] = entry.Sha512;
                changes.Add(new(destination, temp, previousHash, actual, "previous-" + entry.ModId, $"Installed {entry.Name}."));
                final[destination] = new(destination, actual, false, info);
            }

            ValidateDependencies(final.Values, disabledByChoice);
            bool updatePreferences = keepDisabled.Count > 0 || rendererChoices.Count > 0 || rendererJars.Count > 0;
            if (updatePreferences)
            {
                // Retiring moves away the file that held the choice: record it in the same commit, or a later pack that ships
                // the mod again would download it enabled. An unreadable state file is kept as .corrupt-<time>, as a normal write does.
                var temp = SafeChild(game, Path.Combine(cache, "mod-state-" + Guid.NewGuid().ToString("N") + ".tmp"));
                var bytes = ModPreferences.Rewrite(preferencesBytes, preferencesPath,
                    root => {
                        foreach (var (id, project) in keepDisabled) ModPreferences.SetMod(root, id, false, project);
                        foreach (var (id, choice) in rendererChoices) ModPreferences.SetMod(root, id, choice.Enabled, choice.Project, "renderer");
                        foreach (var (id, jar) in rendererJars) ModPreferences.SetRendererSuspendedJar(root, id, jar);
                    });
                await using (var output = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, true))
                {
                    staged.Add(temp, null);
                    await output.WriteAsync(bytes, cancellationToken);
                    await output.FlushAsync(cancellationToken);
                }
                staged[temp] = Hash(bytes);
                var corrupt = preferencesBytes != null && preferences.Error != null ? ModPreferences.CorruptPath(preferencesPath) : null;
                changes.Add(new(preferencesPath, temp, preferencesHash, staged[temp], "previous-mod-state",
                    "Preserved mod choices while retiring or suspending renderer-specific mods.", corrupt));
            }
            if (receipt.Count != originalReceipt.Count
                || receipt.Any(p => !originalReceipt.TryGetValue(p.Key, out var hash) || !SameHash(hash, p.Value)))
            {
                var temp = SafeChild(game, Path.Combine(cache, "receipt-" + Guid.NewGuid().ToString("N") + ".tmp"));
                var bytes = JsonSerializer.SerializeToUtf8Bytes(receipt, Json);
                await using (var output = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, true))
                {
                    staged.Add(temp, null);
                    await output.WriteAsync(bytes, cancellationToken);
                    await output.FlushAsync(cancellationToken);
                }
                staged[temp] = Hash(bytes);
                changes.Add(new(receiptPath, temp, receiptHash, staged[temp], "previous-receipt", "Saved client mod receipt."));
            }

            status?.Invoke("Preparing client mod changes...");
            // Detect changes made in the Mods page or by another process while downloads were in flight.
            var current = await ReadInventory(game, mods, minecraftVersion, cancellationToken);
            if (current.Count != inventory.Count || current.Any(j => !inventory.Any(old =>
                Paths.Equals(old.Path, j.Path) && SameHash(old.Hash, j.Hash))))
                throw new IOException("The Mods folder changed during installation. Retry the launch; your changes were preserved.");
            // Writing the choices file: hold its writers' lock (the launcher's Mods page and LadsCore) until the commit is done.
            await using var preferencesLock = updatePreferences
                ? await LockFiles.AcquireAsync(preferencesPath + ".lock", TimeSpan.FromSeconds(3), cancellationToken) : null;
            await Expect(game, receiptPath, receiptHash, cancellationToken);
            await Expect(game, preferencesPath, preferencesHash, cancellationToken);
            status?.Invoke("Applying client mod changes...");
            cancellationToken.ThrowIfCancellationRequested();

            foreach (var directory in retiredDirectories) Directory.CreateDirectory(directory);
            // There is no portable atomic rename of multiple files. Finish or roll back this bounded
            // commit before observing cancellation; never leave a cancelled install with a stale receipt.
            await Commit(game, cache, changes, status);
            status?.Invoke($"Client mods ready for Minecraft {minecraftVersion}.");

            // A rename is a Change whose staged side is the existing jar; that path is never registered for cleanup.
            void Rename(LocalJar jar, string destination, string id, bool keepOnCollision)
            {
                destination = SafeChild(game, destination);
                if (Occupied(destination, final, inventory))
                {
                    if (keepOnCollision)
                    {
                        status?.Invoke($"Kept '{jar.Path}' under its old name because '{destination}' belongs to another mod.");
                        return;
                    }
                    throw new IOException($"Cannot rename '{jar.Path}' to '{destination}' because that file already exists. " +
                        "Move one of them out of Mods, then retry; your files were preserved.");
                }
                changes.Add(new(destination, jar.Path, null, jar.Hash, "renamed-" + id,
                    $"Renamed '{Path.GetFileName(jar.Path)}' to '{Path.GetFileName(destination)}'."));
                final.Remove(jar.Path);
                final[destination] = jar with { Path = destination, Disabled = destination.EndsWith(DisabledSuffix, StringComparison.OrdinalIgnoreCase) };
            }
        }
        finally
        {
            foreach (var temp in staged) Cleanup(game, temp.Key, temp.Value);
            InstallLock.Release();
        }
    }

    internal static Manifest ReadManifest(string json) =>
        JsonSerializer.Deserialize<Manifest>(json, Json) ?? throw new InvalidDataException("Empty client mod manifest.");

    /// <summary>installed.json stays a flat modId → SHA-512 map so older launchers can still read it.</summary>
    internal static Dictionary<string, string> ReadReceipt(byte[]? bytes)
    {
        var receipt = bytes is null ? new Dictionary<string, string>()
            : JsonSerializer.Deserialize<Dictionary<string, string>>(bytes, Json) ?? throw new InvalidDataException("Empty installed-mod receipt.");
        if (receipt.Any(p => !ValidId(p.Key) || !ValidHash(p.Value)))
            throw new InvalidDataException("Invalid installed-mod receipt.");
        return receipt;
    }

    internal static Dictionary<string, HashSet<string>> RetiredHashes(Manifest manifest)
    {
        var result = new Dictionary<string, HashSet<string>>(StringComparer.Ordinal);
        foreach (var retired in manifest.Retired ?? new())
        {
            if (retired is null || !ValidId(retired.ModId) || retired.Sha512 is null || retired.Sha512.Any(h => !ValidHash(h))
                || result.ContainsKey(retired.ModId) || manifest.Mods.Any(m => m.ModId == retired.ModId))
                throw new InvalidDataException("Client mod manifest has an invalid retired entry.");
            result[retired.ModId] = retired.Sha512.ToHashSet(StringComparer.OrdinalIgnoreCase);
        }
        return result;
    }

    internal static bool IsLegacyName(string fileName, string id) =>
        fileName.Equals("lads-" + id + ".jar", StringComparison.OrdinalIgnoreCase)
        || fileName.Equals("lads-" + id + ".jar" + DisabledSuffix, StringComparison.OrdinalIgnoreCase);

    /// <summary>A plain jar file name for Mods: no paths, reserved names, LadsCore or legacy lads- names.</summary>
    internal static bool ValidFileName(string? name) =>
        !string.IsNullOrEmpty(name) && name.Length <= 200 && name == name.Trim()
        && name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)
        && !name.Equals("theladscore.jar", StringComparison.OrdinalIgnoreCase)
        && !name.StartsWith("lads-", StringComparison.OrdinalIgnoreCase)
        && !name.Contains("..", StringComparison.Ordinal) && name.IndexOfAny(new[] { '/', '\\', ':' }) < 0
        && name.IndexOfAny(Path.GetInvalidFileNameChars()) < 0 && !name.Any(char.IsControl)
        && !ReservedNames.Contains(name.Split('.')[0].TrimEnd(), StringComparer.OrdinalIgnoreCase);

    // The Modrinth hash lookup returns metadata only. Offline or on any API problem the jar keeps its name until a later launch.
    private static async Task<string?> OriginalFileName(HttpClient client, string hash, Entry entry, Action<string>? status, CancellationToken token)
    {
        try
        {
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
            timeout.CancelAfter(TimeSpan.FromSeconds(5)); // Metadata only; never hold up a launch for long.
            using var request = new HttpRequestMessage(HttpMethod.Get, $"https://api.modrinth.com/v2/version_file/{hash}?algorithm=sha512");
            request.Headers.UserAgent.ParseAdd("TheLadsClient/1.2.3");
            using var response = await client.SendAsync(request, timeout.Token);
            response.EnsureSuccessStatusCode();
            using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync(timeout.Token));
            var files = document.RootElement.GetProperty("files").EnumerateArray().ToList();
            var file = files.Where(f => SameHash(f.GetProperty("hashes").GetProperty("sha512").GetString() ?? "", hash))
                .Concat(files.Where(f => f.TryGetProperty("primary", out var primary) && primary.ValueKind == JsonValueKind.True)).FirstOrDefault();
            var name = file.ValueKind == JsonValueKind.Object ? file.GetProperty("filename").GetString() : null;
            if (ValidFileName(name)) return name;
            status?.Invoke($"Modrinth returned no usable file name for the disabled older {entry.Name}; it keeps its current name.");
            return null;
        }
        catch (Exception e) when (!token.IsCancellationRequested && e is HttpRequestException or JsonException or OperationCanceledException
            or InvalidOperationException or KeyNotFoundException)
        {
            status?.Invoke($"Could not look up the original file name of the disabled older {entry.Name} ({e.Message}); " +
                "it keeps its current name until a later launch.");
            return null;
        }
    }

    private static bool Occupied(string path, Dictionary<string, LocalJar> final, List<LocalJar> inventory) =>
        final.ContainsKey(path) || (Exists(path) && !inventory.Any(j => Paths.Equals(j.Path, path)));

    private static string BaseName(string path)
    {
        var name = Path.GetFileName(path);
        return name.EndsWith(DisabledSuffix, StringComparison.OrdinalIgnoreCase) ? name[..^DisabledSuffix.Length] : name;
    }

    // Keeps the name (and its .disabled suffix) and adds " (2)", " (3)"... before ".jar" when it is taken.
    private static string UniquePath(string directory, string fileName, HashSet<string> reserved)
    {
        var suffix = fileName.EndsWith(".jar" + DisabledSuffix, StringComparison.OrdinalIgnoreCase) ? ".jar" + DisabledSuffix
            : fileName.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) ? ".jar" : "";
        var stem = fileName[..^suffix.Length];
        var candidate = Path.Combine(directory, fileName);
        for (var number = 2; Exists(candidate) || reserved.Contains(candidate); number++)
            candidate = Path.Combine(directory, $"{stem} ({number}){suffix}");
        reserved.Add(candidate);
        return candidate;
    }

    private static async Task Download(string game, string cache, Entry entry, HttpClient client,
        Action<string>? status, CancellationToken token, bool resolveThroughApi, string minecraftVersion)
    {
        var cached = SafeChild(game, Path.Combine(cache, entry.Sha512 + ".jar"));
        if (Exists(cached))
        {
            if (!SameHash(await HashAsync(game, cached, token), entry.Sha512))
                throw new InvalidDataException($"Cached file '{cached}' is damaged. Move it aside and retry; it was preserved.");
            if (ReadMod(cached, minecraftVersion, token)?.Id != entry.ModId)
                throw new InvalidDataException($"Wrong mod cached for {entry.Name}.");
            return;
        }
        status?.Invoke($"Downloading {entry.Name} {entry.Version}...");
        if (resolveThroughApi)
            await ModrinthReleaseVerifier.VerifyAsync(client, entry, minecraftVersion, token);
        var temp = SafeChild(game, cached + "." + Guid.NewGuid().ToString("N") + ".tmp");
        var created = false;
        try
        {
            // ResponseHeadersRead does not apply HttpClient.Timeout to the body. Jars reach hundreds of MB (Flashback), so the
            // body only fails when it stalls, or past a ceiling any working connection meets; hashing is outside both.
            using var transfer = CancellationTokenSource.CreateLinkedTokenSource(token);
            transfer.CancelAfter(TimeSpan.FromMinutes(1));
            try
            {
                using var response = await client.GetAsync(entry.Url, HttpCompletionOption.ResponseHeadersRead, transfer.Token);
                response.EnsureSuccessStatusCode();
                if (response.Content.Headers.ContentLength is long length && length != entry.Size)
                    throw new InvalidDataException($"Unexpected download size for {entry.Name}.");
                transfer.CancelAfter(TimeSpan.FromMinutes(45));
                await using var input = await response.Content.ReadAsStreamAsync(transfer.Token);
                await using var output = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, true);
                created = true;
                if (await CopyWithStallTimeoutAsync(input, output, entry.Size, TimeSpan.FromSeconds(60), entry.Name, transfer.Token) != entry.Size)
                    throw new InvalidDataException($"Incomplete download for {entry.Name}.");
            }
            catch (OperationCanceledException) when (!token.IsCancellationRequested)
            {
                throw new TimeoutException($"Downloading {entry.Name} timed out. Check your connection and retry the launch.");
            }
            if (!SameHash(await HashAsync(game, temp, token), entry.Sha512))
                throw new InvalidDataException($"Download verification failed for {entry.Name}. Retry the launch.");
            if (ReadMod(temp, minecraftVersion, token)?.Id != entry.ModId)
                throw new InvalidDataException($"Wrong mod downloaded for {entry.Name}.");
            // Never overwrite even an unexpected cache file created during the download.
            File.Move(SafeChild(game, temp), SafeChild(game, cached));
            created = false;
        }
        finally { if (created) Cleanup(game, temp, null); }
    }

    /// <summary>Copies at most <paramref name="limit"/> bytes and returns the count. A slow transfer never times out here;
    /// only <paramref name="idle"/> without a single byte does (TimeoutException).</summary>
    public static async Task<long> CopyWithStallTimeoutAsync(Stream input, Stream output, long limit, TimeSpan idle, string name,
        CancellationToken token)
    {
        using var stall = CancellationTokenSource.CreateLinkedTokenSource(token);
        var buffer = new byte[81920]; long total = 0; int count;
        try
        {
            while (true)
            {
                stall.CancelAfter(idle);
                count = await input.ReadAsync(buffer, stall.Token);
                stall.CancelAfter(Timeout.InfiniteTimeSpan); // a slow disk write is not a network stall
                if (count == 0) return total;
                total += count;
                if (total > limit) throw new InvalidDataException($"Oversized download for {name}.");
                await output.WriteAsync(buffer.AsMemory(0, count), token);
            }
        }
        catch (OperationCanceledException) when (!token.IsCancellationRequested)
        {
            throw new TimeoutException($"Downloading {name} stalled: no data for {idle.TotalSeconds:0} seconds. Check your connection and retry the launch.");
        }
    }

    private static async Task Commit(string game, string cache, List<Change> changes, Action<string>? status)
    {
        var moves = new List<Move>();
        try
        {
            foreach (var change in changes)
            {
                await Expect(game, change.Destination, change.PreviousHash, CancellationToken.None);
                if (change.Staged != null)
                    await Expect(game, change.Staged, change.NewHash, CancellationToken.None);
                if (change.PreviousHash != null)
                {
                    var extension = Path.GetExtension(change.Destination);
                    var backup = SafeChild(game, change.BackupPath
                        ?? Path.Combine(cache, change.BackupName + "-" + Guid.NewGuid().ToString("N") + extension));
                    File.Move(SafeChild(game, change.Destination), backup);
                    moves.Add(new(change.Destination, backup, change.PreviousHash));
                }
                if (change.Staged != null)
                {
                    File.Move(SafeChild(game, change.Staged), SafeChild(game, change.Destination));
                    moves.Add(new(change.Staged, change.Destination, change.NewHash!));
                }
                status?.Invoke(change.Status);
            }
        }
        catch (Exception failure)
        {
            var errors = new List<Exception> { failure };
            for (var i = moves.Count - 1; i >= 0; i--)
            {
                var move = moves[i];
                try
                {
                    // Refuse to overwrite or discard content changed by somebody else during rollback.
                    await Expect(game, move.To, move.Hash, CancellationToken.None);
                    await Expect(game, move.From, null, CancellationToken.None);
                    File.Move(SafeChild(game, move.To), SafeChild(game, move.From));
                }
                catch (Exception rollbackFailure) { errors.Add(rollbackFailure); }
            }
            if (errors.Count > 1)
                throw new IOException($"Client mod rollback could not finish because files changed or became inaccessible. " +
                    $"Original files are preserved in '{cache}'. Close Minecraft and restore those backups before retrying.",
                    new AggregateException(errors));
            throw;
        }
    }

    private static void Validate(Entry entry)
    {
        if (!ValidId(entry.ModId) || !ValidHash(entry.Sha512)
            || entry.Size <= 0 || entry.Size > MaximumJarSize
            || !Uri.TryCreate(entry.Url, UriKind.Absolute, out var url) || url.Scheme != "https"
            || url.Host != "cdn.modrinth.com" || !string.IsNullOrEmpty(url.UserInfo))
            throw new InvalidDataException("Invalid client mod download metadata.");
        if (!ValidFileName(entry.FileName))
            throw new InvalidDataException($"Invalid client mod file name '{entry.FileName}' for {entry.ModId}.");
    }

    private static async Task<List<LocalJar>> ReadInventory(string game, string mods, string minecraftVersion, CancellationToken token)
    {
        var result = new List<LocalJar>();
        foreach (var file in Directory.EnumerateFiles(SafeChild(game, mods)).Where(p =>
            p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || p.EndsWith(".jar" + DisabledSuffix, StringComparison.OrdinalIgnoreCase)))
        {
            token.ThrowIfCancellationRequested();
            var path = SafeChild(game, file);
            var hash = await HashAsync(game, path, token);
            FabricModInfo? info;
            try { info = ReadMod(path, minecraftVersion, token); }
            catch (Exception e) when (e is InvalidDataException or InvalidOperationException)
            {
                throw new InvalidDataException($"'{path}' is not a readable Fabric mod ({e.Message}). Move it out of Mods, then retry.", e);
            }
            result.Add(new(path, hash, path.EndsWith(DisabledSuffix, StringComparison.OrdinalIgnoreCase), info));
        }
        return result;
    }

    private static void ValidateDependencies(IEnumerable<LocalJar> jars, Dictionary<string, string> disabledByChoice)
    {
        var all = jars.Where(j => j.Info != null).ToList();
        var mods = all.Where(j => !j.Disabled).SelectMany(j => FabricModMetadata.ClientModules(j.Info!).Select(m => (Mod: m, Top: j.Info!))).ToList();
        var available = mods.SelectMany(m => m.Mod.Provides.Append(m.Mod.Id)).ToHashSet(StringComparer.Ordinal);
        // These are supplied by the selected launch runtime, not by jars in Mods.
        available.UnionWith(new[] { "minecraft", "java", "fabricloader" });
        // Fabric Loader bundles MixinExtras inside fabric-loader.jar since 0.15 (0.19.3 ships META-INF/jars/mixinextras-fabric-0.5.4.jar);
        // the core profiles use 0.19.x and Create Profile 0.16.9. It is intentionally absent from the pinned Mods directory.
        available.Add("mixinextras");
        // Disabled provider id -> the top-level mod the user can enable.
        var disabled = new Dictionary<string, string>(disabledByChoice, StringComparer.Ordinal);
        foreach (var jar in all.Where(j => j.Disabled))
            foreach (var mod in FabricModMetadata.ClientModules(jar.Info!))
                foreach (var id in mod.Provides.Append(mod.Id)) disabled.TryAdd(id, jar.Info!.Name ?? jar.Info.Id);
        var unsatisfied = mods.SelectMany(m => m.Mod.Depends.Where(d => !available.Contains(d.Key)).Select(d => (Top: m.Top.Id, Missing: d.Key,
            Text: $"{m.Mod.Name} ({m.Mod.Id}) requires {d.Key} {d.Value}, which is " + (disabled.TryGetValue(d.Key, out var provider)
                ? $"disabled: enable {provider} or disable {m.Top.Name} in Mods"
                : $"missing: disable {m.Top.Name} in Mods or add a mod that provides {d.Key}")))).ToList();
        if (unsatisfied.Count > 0)
            throw new ClientModDependencyException("Client mod dependencies are not satisfied: " +
                string.Join("; ", unsatisfied.Select(u => u.Text).Distinct()) + ". Your files were preserved.",
                unsatisfied.Select(u => u.Top).Distinct().Order(StringComparer.Ordinal).ToList(),
                unsatisfied.Select(u => u.Missing).Distinct().Order(StringComparer.Ordinal).ToList());
        // Fabric itself resolves version predicates, alternative nested versions and incompatibilities.
    }

    // The jar as the version's loader sees it: fabric.mod.json, or on Forge (1.8.9) mcmod.info, OptiFine or the file name. Forge
    // skips what it cannot read and never loads a Fabric jar, so those have no metadata there and never fail an install.
    private static FabricModInfo? ReadMod(string path, string minecraftVersion, CancellationToken token) =>
        GameVersionPolicy.UsesForge(minecraftVersion) ? ModInventoryService.ScanFor(path, minecraftVersion, token).Info
            : FabricModMetadata.ReadJar(path, token: token);

    private static bool ValidId(string? value) => FabricModMetadata.ValidId(value);
    private static bool ValidHash(string? value) => Regex.IsMatch(value ?? "", "^[a-fA-F0-9]{128}$");
    internal static bool SameHash(string left, string right) => left.Equals(right, StringComparison.OrdinalIgnoreCase);
    private static string Hash(byte[] bytes) => Convert.ToHexString(SHA512.HashData(bytes)).ToLowerInvariant();

    private static async Task<string> HashAsync(string game, string path, CancellationToken token)
    {
        await using var stream = new FileStream(SafeChild(game, path), FileMode.Open, FileAccess.Read, FileShare.Read, 81920, true);
        return Convert.ToHexString(await SHA512.HashDataAsync(stream, token)).ToLowerInvariant();
    }

    private static async Task Expect(string game, string path, string? expected, CancellationToken token)
    {
        path = SafeChild(game, path);
        if (expected is null ? Exists(path) : !File.Exists(path) || !SameHash(await HashAsync(game, path, token), expected))
            throw new IOException($"Mod installation path '{path}' changed or is occupied. Retry after checking Mods; existing content was preserved.");
    }

    private static bool Exists(string path)
    {
        try { File.GetAttributes(path); return true; }
        catch (FileNotFoundException) { return false; }
        catch (DirectoryNotFoundException) { return false; }
    }

    internal static string SafeChild(string root, string path)
    {
        var full = Path.GetFullPath(path);
        var relative = Path.GetRelativePath(root, full);
        if (Path.IsPathRooted(relative) || relative == ".." || relative.StartsWith(".." + Path.DirectorySeparatorChar, StringComparison.Ordinal))
            throw new IOException($"Mod path '{full}' escapes '{root}'.");
        for (string? current = full; current != null; current = Path.GetDirectoryName(current))
        {
            try
            {
                if ((File.GetAttributes(current) & FileAttributes.ReparsePoint) != 0)
                    throw new IOException($"Managed mod path is a link: '{current}'. Use a regular profile directory.");
            }
            catch (FileNotFoundException) { }
            catch (DirectoryNotFoundException) { }
        }
        return full;
    }

    private static void Cleanup(string game, string path, string? expectedHash)
    {
        try
        {
            path = SafeChild(game, path);
            if (!File.Exists(path)) return;
            if (expectedHash != null)
            {
                using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
                if (!SameHash(Convert.ToHexString(SHA512.HashData(stream)), expectedHash)) return;
            }
            File.Delete(path);
        }
        catch (IOException) { } // Preserve inaccessible or externally changed staging files.
        catch (UnauthorizedAccessException) { }
    }
}

/// <summary>The requested mods cannot load together: <see cref="DependentModIds"/> (top-level mods in Mods) need
/// <see cref="MissingModIds"/>, which no enabled jar provides. The launcher offers disabling the dependents or switching
/// a disabled provider back on. (InvalidDataException, the installer's other validation error, is sealed.)</summary>
public sealed class ClientModDependencyException : InvalidOperationException
{
    public ClientModDependencyException(string message, IReadOnlyList<string> dependentModIds, IReadOnlyList<string> missingModIds)
        : base(message)
    {
        DependentModIds = dependentModIds;
        MissingModIds = missingModIds;
    }

    public IReadOnlyList<string> DependentModIds { get; }
    public IReadOnlyList<string> MissingModIds { get; }
}
