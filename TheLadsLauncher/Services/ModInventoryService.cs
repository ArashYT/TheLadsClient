using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public enum ModOwnership { Core, NativeModule, Pack, User, Embedded, Platform, Retired }
public enum ModEntryStatus { Installed, Disabled, PendingDownload, NotDownloaded, Unavailable, Unsupported, Embedded, RetiredCopy, Invalid }

/// <summary>One row of the mod inventory. Depends items are "&lt;id&gt; &lt;Fabric predicate&gt;".
/// DependenciesKnown is false for pack entries whose jar has not been downloaded (or cached) yet.</summary>
public sealed record ModInventoryEntry(string Id, string DisplayName, string? UpstreamName, string? Version, string? FileName,
    string? FilePath, ModOwnership Ownership, ModEntryStatus Status, bool EnabledOnDisk, bool RequestedEnabled, bool? LoadedNow,
    bool RestartRequired, bool CanToggle, string? ToggleBlockedReason, string? ProjectId, string? ProjectUrl, string? License,
    IReadOnlyList<string> Authors, IReadOnlyList<string> Depends, IReadOnlyList<string> Provides, bool IsLibrary, string? Note,
    string? ParentId, IReadOnlyList<ModInventoryEntry> Children, bool DependenciesKnown = true);

public sealed record ModInventory(string MinecraftVersion, string GameDirectory, bool GameRunning,
    IReadOnlyList<ModInventoryEntry> Entries, ModInventoryCounts Counts);

public sealed record ModInventoryCounts(int EnabledFiles, int DisabledFiles, int PendingDownloads, int Unavailable, int NativeModules, int Embedded);

/// <summary>
/// Reconciles a profile's Mods folder with the selected pack manifest, the other shipped manifests, the install receipt,
/// saved choices, retired mods, the bundled LadsCore, LadsCore's module catalog and the platform. Nothing is hard-coded:
/// every row comes from a file on disk or a shipped manifest.
/// </summary>
public sealed class ModInventoryService
{
    public const string CatalogPlaceholderId = "lads-modules";
    public const string NativeRunningReason = "Change it in the in-game Lads menu (applies immediately)";
    internal const string ServerOnlyNote = "Server-only module; not loaded on the client";
    internal const string OptiFineNote = "Downloaded from optifine.net by the launcher (OptiFine may not be redistributed)";
    private const string CoreId = BundledModInstaller.CoreModId;
    private const int CacheLimit = 4096;
    private static readonly JsonSerializerOptions ReadJson = new() { PropertyNameCaseInsensitive = true };
    private static readonly JsonSerializerOptions SnapshotJson = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = true,
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.CamelCase) }
    };
    // ponytail: whole-cache reset when full; switch to LRU only if a profile ever holds more than CacheLimit jars.
    private static readonly ConcurrentDictionary<(string Path, long Length, DateTime Modified), ScannedJar> Scans = new();
    private readonly Func<string, IReadOnlyCollection<string>?>? loadedModsProvider;
    private readonly Func<string?>? rendererSelection;

    internal sealed record ScannedJar(string? Hash, FabricModInfo? Info, string? Error);
    private sealed record CatalogModule(string Name, string? Description, string? Category, string? Support, string? Label,
        string? Detail, string? ExternalModId, bool? Enabled, bool? Toggleable);
    private sealed record CoreCatalog(int Schema, string? CoreVersion, string? MinecraftVersion, List<CatalogModule>? Modules);

    /// <param name="loadedModsProvider">Returns the mod ids loaded by the profile's running game, or null when it is not running.</param>
    /// <param name="rendererSelection">The launcher's saved renderer choice: renderer locks then show what the next launch does.
    /// Without it they show what the last launch applied.</param>
    public ModInventoryService(Func<string, IReadOnlyCollection<string>?>? loadedModsProvider = null, Func<string?>? rendererSelection = null)
    {
        this.loadedModsProvider = loadedModsProvider;
        this.rendererSelection = rendererSelection;
    }

    public Task<ModInventory> BuildAsync(string bundleRoot, string gameDirectory, string minecraftVersion,
        CancellationToken cancellationToken = default) =>
        Task.Run(() => Build(Path.GetFullPath(bundleRoot), Path.GetFullPath(gameDirectory), minecraftVersion, cancellationToken), cancellationToken);

    /// <summary>Writes &lt;gameDir&gt;\.lads-mod-cache\inventory.json for the in-game Mods view (camelCase, enums as camelCase strings).</summary>
    public Task WriteSnapshotAsync(ModInventory inventory, CancellationToken cancellationToken = default) =>
        LockFiles.WriteAtomicallyAsync(Path.Combine(inventory.GameDirectory, ".lads-mod-cache", "inventory.json"),
            JsonSerializer.SerializeToUtf8Bytes(new
            {
                schema = 1,
                minecraftVersion = inventory.MinecraftVersion,
                generatedAt = DateTime.UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'", CultureInfo.InvariantCulture),
                entries = inventory.Entries
            }, SnapshotJson), cancellationToken);

    private ModInventory Build(string bundle, string game, string version, CancellationToken token)
    {
        GameVersionPolicy.ValidateMinecraftVersion(version);
        var loaded = loadedModsProvider?.Invoke(game);
        var running = loaded != null;
        var loadedIds = loaded?.ToHashSet(StringComparer.Ordinal) ?? new HashSet<string>(StringComparer.Ordinal);
        var preferences = ModPreferences.Load(game);
        var cache = Path.Combine(game, ".lads-mod-cache");
        var receiptPath = Path.Combine(cache, "installed.json");
        Dictionary<string, string> receipt;
        try { receipt = ClientModInstaller.ReadReceipt(File.Exists(receiptPath) ? File.ReadAllBytes(receiptPath) : null); }
        catch (Exception e) when (e is JsonException or InvalidDataException)
        {
            throw new InvalidDataException($"Installed-mod receipt '{receiptPath}' is unreadable ({e.Message}). Move it aside; the next launch rebuilds it from the installed pack.", e);
        }
        var manifests = ReadManifests(bundle);
        manifests.TryGetValue(version, out var manifest);
        var pack = manifest?.Mods.ToDictionary(m => m.ModId, StringComparer.Ordinal) ?? new Dictionary<string, ClientModInstaller.Entry>();
        var retiredHashes = manifest == null ? new Dictionary<string, HashSet<string>>() : ClientModInstaller.RetiredHashes(manifest);
        var publishedHashes = manifest == null ? new Dictionary<string, HashSet<string>>() : ClientModInstaller.PublishedHashes(manifest);
        bool Published(string id, string hash) => publishedHashes.TryGetValue(id, out var hashes) && hashes.Contains(hash);
        // As in the installer: without a manifest for this version nothing is retired, so receipt jars are ordinary user jars.
        var retireIds = receipt.Keys.Where(id => manifest != null && !pack.ContainsKey(id)).Concat(retiredHashes.Keys).ToHashSet(StringComparer.Ordinal);

        var mods = Path.Combine(game, "mods");
        var forge = GameVersionPolicy.UsesForge(version);
        var files = (Directory.Exists(mods) ? Directory.EnumerateFiles(mods) : Enumerable.Empty<string>())
            .Where(p => p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || p.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase))
            .OrderBy(p => p, StringComparer.OrdinalIgnoreCase)
            .Select(p => (Path: p, Disabled: p.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase), Scan: ForLoader(Scan(p, token), p, forge))).ToList();
        var copies = files.Where(f => f.Scan.Info != null).ToLookup(f => f.Scan.Info!.Id, StringComparer.Ordinal);
        // Without an explicit choice an id keeps its disk state; LadsCore defaults to enabled (BundledModInstaller installs it).
        bool Requested(string id, string? projectId) => preferences.GetEnabled(id, projectId)
            ?? (id == CoreId || !(copies[id].Any(f => f.Disabled) && copies[id].All(f => f.Disabled)));
        bool? LoadedNow(string id) => running ? loadedIds.Contains(id) : null;

        var hasCore = copies[CoreId].Any() || File.Exists(Path.Combine(bundle, "game-mods", version, "theladscore.jar"))
            || GameVersionPolicy.RequiresBundledCore(version);
        var coreRequested = Requested(CoreId, null);
        var (catalog, catalogError) = hasCore ? ReadCatalog(game) : (null, null);
        var integrations = (catalog?.Modules ?? new()).Where(m => m.Support == "external" && m.ExternalModId != null && !string.IsNullOrEmpty(m.Name))
            .ToLookup(m => m.ExternalModId!, m => m.Name, StringComparer.Ordinal);
        var entries = new List<ModInventoryEntry>();

        foreach (var file in files)
        {
            token.ThrowIfCancellationRequested();
            var name = Path.GetFileName(file.Path);
            var info = file.Scan.Info;
            if (info == null)
            {
                var problem = $"Not a loadable {(forge ? "Forge" : "Fabric")} mod: " + (file.Scan.Error?.TrimEnd('.') ?? "it has no fabric.mod.json") + ". Delete or replace it.";
                entries.Add(new(name, name, null, null, name, file.Path, ModOwnership.User, ModEntryStatus.Invalid, !file.Disabled,
                    !file.Disabled, running ? false : null, false, false, problem, null, null, null, Array.Empty<string>(),
                    Array.Empty<string>(), Array.Empty<string>(), false, problem, null, Array.Empty<ModInventoryEntry>()));
                continue;
            }
            var id = info.Id;
            pack.TryGetValue(id, out var entry);
            var requested = Requested(id, entry?.ProjectId);
            var ownership = id == CoreId ? ModOwnership.Core : ModOwnership.User;
            var status = file.Disabled ? ModEntryStatus.Disabled : ModEntryStatus.Installed;
            var notes = new List<string>();
            string? blocked = null;
            if (entry != null && ownership != ModOwnership.Core)
            {
                var pinned = ClientModInstaller.SameHash(file.Scan.Hash!, entry.Sha512);
                if (Published(id, file.Scan.Hash!) || (receipt.TryGetValue(id, out var owned) && ClientModInstaller.SameHash(file.Scan.Hash!, owned)))
                {
                    ownership = ModOwnership.Pack;
                    if (!pinned) notes.Add(file.Disabled ? "Disabled — outdated; updates when enabled" : $"Updates to {entry.Version} at the next launch");
                }
                else if (requested)
                {
                    status = ModEntryStatus.Invalid;
                    notes.Add($"Conflicts with pack mod {entry.Name}: delete your copy (Mods page) or disable {entry.Name}");
                }
                else notes.Add($"Your own copy of pack mod {entry.Name}; it differs from the tested pack");
            }
            else if (retireIds.Contains(id))
            {
                // As in the installer: published bytes under any name, or the receipt's copy.
                if (Published(id, file.Scan.Hash!) || (receipt.TryGetValue(id, out var owned) && ClientModInstaller.SameHash(file.Scan.Hash!, owned)))
                {
                    ownership = ModOwnership.Retired;
                    status = ModEntryStatus.RetiredCopy;
                    blocked = "Removed from the Lads pack; it is moved to .lads-mod-cache\\retired at the next launch";
                    notes.Add(blocked);
                }
                else notes.Add("Removed from the Lads pack — this copy was added or modified by you");
            }
            else if (forge && id == OptiFineInstaller.ModId && OptiFineInstaller.IsLaunchersFile(name))
            {
                ownership = ModOwnership.Pack; // checked and replaced when damaged at every launch (OptiFineInstaller)
                notes.Add(OptiFineNote);
            }
            if (status is ModEntryStatus.Installed or ModEntryStatus.Disabled && info.Depends.TryGetValue("minecraft", out var minecraft)
                && !FabricVersionPredicate.Matches(minecraft, version))
            {
                status = ModEntryStatus.Unsupported;
                notes.Add($"Requires Minecraft {minecraft}");
            }
            // The id as a whole (another copy may already be in the requested state).
            if (!running && requested != copies[id].Any(f => !f.Disabled) && status != ModEntryStatus.RetiredCopy)
                notes.Add(requested ? "Enabled at the next launch" : "Disabled at the next launch");
            notes.AddRange(integrations[id].Select(module => "Lads integration: " + module));
            if (entry?.ProjectId == ModWelcomeSettings.EssentialProjectId) notes.Add(ModWelcomeSettings.EssentialNote);
            var loadedNow = LoadedNow(id);
            var displayName = entry?.Name ?? info.Name ?? id;
            entries.Add(new(id, displayName, entry?.Name, info.Version, name, file.Path, ownership, status, !file.Disabled, requested,
                loadedNow, running && loadedNow != requested, blocked == null, blocked, entry?.ProjectId, entry?.ProjectUrl,
                entry?.License ?? info.License, info.Authors, Depends(info), info.Provides, info.IsLibraryBadge, Join(notes), null,
                Children(info, displayName, !file.Disabled, requested, loadedNow, running && loadedNow != requested)));
        }

        // Pack entries without a Lads-owned copy on disk.
        foreach (var entry in manifest?.Mods ?? new List<ClientModInstaller.Entry>())
        {
            if (entries.Any(e => e.Id == entry.ModId && e.Ownership == ModOwnership.Pack)) continue;
            var requested = Requested(entry.ModId, entry.ProjectId);
            var cached = Path.Combine(cache, entry.Sha512 + ".jar");
            var scan = File.Exists(cached) ? Scan(cached, token) : null;
            var info = scan?.Hash != null && ClientModInstaller.SameHash(scan.Hash, entry.Sha512) ? scan.Info : null;
            var notes = new List<string> { requested ? "Downloaded at the next launch" : "Disabled — not downloaded" };
            if (info == null) notes.Add("Its dependencies are checked when it is downloaded");
            notes.AddRange(integrations[entry.ModId].Select(module => "Lads integration: " + module));
            if (entry.ProjectId == ModWelcomeSettings.EssentialProjectId) notes.Add(ModWelcomeSettings.EssentialNote);
            entries.Add(new(entry.ModId, entry.Name, entry.Name, entry.Version, entry.FileName, null, ModOwnership.Pack,
                requested ? ModEntryStatus.PendingDownload : ModEntryStatus.NotDownloaded, false, requested, running ? false : null,
                running && requested, true, null, entry.ProjectId, entry.ProjectUrl, entry.License, info?.Authors ?? Array.Empty<string>(),
                info == null ? Array.Empty<string>() : Depends(info), info?.Provides ?? Array.Empty<string>(), info?.IsLibraryBadge ?? false,
                Join(notes), null, info == null ? Array.Empty<ModInventoryEntry>() : Children(info, entry.Name, false, requested, running ? false : null, running && requested),
                DependenciesKnown: info != null));
        }

        // OptiFine may not be redistributed, so no manifest lists it: the launcher downloads it at launch (OptiFineInstaller).
        if (forge && !entries.Any(e => e.Id == OptiFineInstaller.ModId))
        {
            var requested = Requested(OptiFineInstaller.ModId, null);
            var note = requested ? "Downloaded from optifine.net at the next launch" : "Disabled — not downloaded";
            entries.Add(new(OptiFineInstaller.ModId, "OptiFine", null, OptiFineInstaller.Version, OptiFineInstaller.M5.FileName, null,
                ModOwnership.Pack, requested ? ModEntryStatus.PendingDownload : ModEntryStatus.NotDownloaded, false, requested,
                running ? false : null, running && requested, true, null, null, null, null, new[] { "sp614x" }, Array.Empty<string>(),
                Array.Empty<string>(), false, note, null, Array.Empty<ModInventoryEntry>()));
        }

        // The bundled LadsCore when it is not in Mods (installed at the next launch unless disabled).
        if (hasCore && !copies[CoreId].Any())
        {
            var bundled = Path.Combine(bundle, "game-mods", version, "theladscore.jar");
            // Read as this version's loader reads it (1.8.9: mcmod.info): the other loader's Core is no Core here.
            var info = File.Exists(bundled) && Scan(bundled, token).Info is { } scanned && scanned.Forge == forge && scanned.Id == CoreId ? scanned : null;
            var missing = info == null ? $"The Lads Core bundle for Minecraft {version} is missing or unreadable ('{bundled}'). Reinstall the launcher." : null;
            entries.Add(new(CoreId, info?.Name ?? "The Lads Core", null, info?.Version, "theladscore.jar", null, ModOwnership.Core,
                missing != null ? ModEntryStatus.Invalid : coreRequested ? ModEntryStatus.PendingDownload : ModEntryStatus.NotDownloaded,
                false, coreRequested, running ? false : null, running && coreRequested, true, null, null, null, info?.License,
                info?.Authors ?? Array.Empty<string>(), info == null ? Array.Empty<string>() : Depends(info), info?.Provides ?? Array.Empty<string>(),
                false, missing ?? (coreRequested ? "Installed at the next launch" : "Disabled — not installed"), null,
                info == null ? Array.Empty<ModInventoryEntry>() : Children(info, info.Name!, false, coreRequested, running ? false : null, running && coreRequested)));
        }

        var replacements = new Dictionary<string, string>(StringComparer.Ordinal);
        if (hasCore) entries.AddRange(NativeEntries(game, version, running, coreRequested, catalog, catalogError, replacements));

        // Mods shipped for other Minecraft versions only: listed with the version limitation, never hidden.
        var retiredAnywhere = manifests.Values.SelectMany(m => m.Retired ?? new()).Select(r => r.ModId).ToHashSet(StringComparer.Ordinal);
        var others = manifests.Where(m => m.Key != version).OrderBy(m => System.Version.TryParse(m.Key, out var v) ? v : new Version())
            .SelectMany(m => m.Value.Mods.Select(mod => (Version: m.Key, Mod: mod)));
        foreach (var group in others.GroupBy(o => o.Mod.ModId, StringComparer.Ordinal))
        {
            if (pack.ContainsKey(group.Key) || retiredAnywhere.Contains(group.Key) || group.Key == CoreId || entries.Any(e => e.Id == group.Key))
                continue;
            var latest = group.Last().Mod;
            var note = $"Not in the Lads pack for {version} (included for {string.Join(", ", group.Select(o => o.Version).Distinct())})";
            if (replacements.TryGetValue(group.Key, out var module)) note += $". Lads provides its own {module} instead";
            entries.Add(new(group.Key, latest.Name, latest.Name, latest.Version, latest.FileName, null, ModOwnership.Pack,
                ModEntryStatus.Unavailable, false, false, running ? false : null, false, false, note, latest.ProjectId, latest.ProjectUrl,
                latest.License, Array.Empty<string>(), Array.Empty<string>(), Array.Empty<string>(), false, note, null,
                Array.Empty<ModInventoryEntry>()));
        }

        const string platformReason = "Platform component; it is not a mod toggle";
        var java = forge ? GameVersionPolicy.GetRequiredJavaMajor(version).ToString(CultureInfo.InvariantCulture) // exactly
            : GameVersionPolicy.RequiresBundledCore(version) ? GameVersionPolicy.GetRequiredJavaMajor(version) + "+" : null;
        var loader = forge ? ("forge", "Forge", (string?)GameVersionPolicy.ForgeBuild) : ("fabricloader", "Fabric Loader", (string?)null);
        foreach (var (id, name, platformVersion) in new[] { ("minecraft", "Minecraft", (string?)version), loader, ("java", "Java", java) })
            entries.Add(new(id, name, null, platformVersion, null, null, ModOwnership.Platform, ModEntryStatus.Installed, true, true,
                running ? true : null, false, false, platformReason, null, null, null, Array.Empty<string>(), Array.Empty<string>(),
                Array.Empty<string>(), false, null, null, Array.Empty<ModInventoryEntry>()));

        var rendererBlocked = rendererSelection == null ? GraphicsRenderer.Suspended(game)
            : GraphicsRenderer.Suspended(game, version, rendererSelection(), files.Select(f => f.Scan.Info));
        entries = entries.Select(e => rendererBlocked.Contains(e.Id)
            ? e with { RequestedEnabled = false, CanToggle = false, ToggleBlockedReason = GraphicsRenderer.OpenGlRequired,
                Status = e.Status == ModEntryStatus.PendingDownload ? ModEntryStatus.NotDownloaded : e.Status,
                Note = GraphicsRenderer.OpenGlRequired, RestartRequired = running && e.LoadedNow == true } : e).ToList();
        var ordered = entries.OrderBy(Group).ThenBy(e => e.DisplayName, StringComparer.OrdinalIgnoreCase).ToList();
        var counts = new ModInventoryCounts(files.Count(f => !f.Disabled), files.Count(f => f.Disabled),
            ordered.Count(e => e.Status == ModEntryStatus.PendingDownload),
            ordered.Count(e => e.Status == ModEntryStatus.Unavailable && e.Ownership != ModOwnership.NativeModule),
            ordered.Count(e => e.Ownership == ModOwnership.NativeModule && e.Id != CatalogPlaceholderId),
            ordered.Sum(e => CountEmbedded(e.Children)));
        return new ModInventory(version, game, running, ordered, counts);

        // A nested library names its jar-in-jar parent, but the operation offered is always on the top-level jar.
        List<ModInventoryEntry> Children(FabricModInfo parent, string parentName, bool onDisk, bool requested, bool? loadedNow, bool restart,
            string? topName = null) =>
            parent.Children.Select(child => new ModInventoryEntry(child.Id, child.Name ?? child.Id, null, child.Version,
                child.NestedPath?[(child.NestedPath.LastIndexOf('/') + 1)..], null, ModOwnership.Embedded, ModEntryStatus.Embedded,
                onDisk, requested, loadedNow, restart, false, $"Embedded inside {parentName}; disable {topName ?? parentName} to remove it",
                null, null, child.License, child.Authors, Depends(child), child.Provides, true,
                !child.HasMetadata ? "Library jar without Fabric metadata" : child.IsServerOnly ? ServerOnlyNote : null,
                parent.Id, Children(child, child.Name ?? child.Id, onDisk, requested, loadedNow, restart, topName ?? parentName))).ToList();
    }

    private static List<ModInventoryEntry> NativeEntries(string game, string version, bool running, bool coreRequested,
        CoreCatalog? catalog, string? catalogError, Dictionary<string, string> replacements)
    {
        if (catalog?.Modules == null)
        {
            // LadsCore writes the list, so a profile with Core disabled cannot get one by launching.
            var reason = catalogError ?? (coreRequested ? "Launch this profile once to list Lads modules"
                : "LadsCore is disabled for this profile; enable LadsCore and launch once to list Lads modules");
            return new() { new(CatalogPlaceholderId, "Lads modules", null, null, null, null, ModOwnership.NativeModule, ModEntryStatus.Unavailable,
                false, false, null, false, false, reason, null, null, null, Array.Empty<string>(), Array.Empty<string>(),
                Array.Empty<string>(), false, reason, null, Array.Empty<ModInventoryEntry>()) };
        }
        var configPath = Path.Combine(game, "thelads_config.json");
        JsonObject? moduleStates = null;
        string? configError = null;
        if (File.Exists(configPath))
        {
            try
            {
                var node = JsonNode.Parse(ModPreferences.ReadShared(configPath) ?? Array.Empty<byte>()) as JsonObject
                    ?? throw new JsonException("the file is not a JSON object");
                ModPreferences.Materialize(node);
                moduleStates = node["modules"] as JsonObject;
            }
            catch (Exception e) when (e is JsonException or ArgumentException)
            {
                configError = $"'{configPath}' is unreadable ({e.Message}); fix or delete it in the game folder";
            }
        }
        var rows = new List<ModInventoryEntry>();
        foreach (var module in catalog.Modules.Where(m => !string.IsNullOrEmpty(m.Name) && m.Support is "builtIn" or "pending" or "unavailable"))
        {
            var builtIn = module.Support == "builtIn";
            if (builtIn && module.ExternalModId != null) replacements.TryAdd(module.ExternalModId, module.Name);
            var enabled = moduleStates?[module.Name] is JsonObject state && state["enabled"] is JsonValue value && value.TryGetValue<bool>(out var stored)
                ? stored : module.Enabled ?? false;
            var explanation = Join(new[] { module.Label, module.Detail }.Where(t => !string.IsNullOrWhiteSpace(t)).Select(t => t!).ToList());
            var blocked = !builtIn ? explanation ?? "This Lads module cannot be switched"
                : module.Toggleable != true ? "This Lads module cannot be switched on or off yet"
                : !coreRequested ? "LadsCore is disabled for this profile; enable LadsCore to use Lads modules"
                : catalog.MinecraftVersion != version ? $"The Lads module list is from Minecraft {catalog.MinecraftVersion}; launch this profile once to refresh it"
                : configError ?? (running ? NativeRunningReason : null);
            rows.Add(new(module.Name, module.Name, null, catalog.CoreVersion, null, null, ModOwnership.NativeModule,
                builtIn ? enabled ? ModEntryStatus.Installed : ModEntryStatus.Disabled : ModEntryStatus.Unavailable,
                enabled, enabled, running ? enabled : null, false, blocked == null, blocked, null, null, null, Array.Empty<string>(),
                Array.Empty<string>(), Array.Empty<string>(), false, builtIn ? module.Description : explanation, null,
                Array.Empty<ModInventoryEntry>()));
        }
        return rows;
    }

    private static (CoreCatalog? Catalog, string? Error) ReadCatalog(string game)
    {
        var path = Path.Combine(game, "lads-core-catalog.json");
        var bytes = ModPreferences.ReadShared(path);
        if (bytes == null) return (null, null);
        try { return (JsonSerializer.Deserialize<CoreCatalog>(bytes, ReadJson), null); }
        catch (JsonException e) { return (null, $"The Lads module list '{path}' is unreadable ({e.Message}); launch this profile once to rewrite it"); }
    }

    private static Dictionary<string, ClientModInstaller.Manifest> ReadManifests(string bundle)
    {
        var result = new Dictionary<string, ClientModInstaller.Manifest>(StringComparer.Ordinal);
        var root = Path.Combine(bundle, "game-mods");
        if (!Directory.Exists(root)) return result;
        foreach (var path in Directory.EnumerateFiles(root, "client-mods.json", SearchOption.AllDirectories))
        {
            ClientModInstaller.Manifest manifest;
            try { manifest = ClientModInstaller.ReadManifest(File.ReadAllText(path)); }
            catch (JsonException e) { throw new InvalidDataException($"Shipped mod manifest '{path}' is unreadable: {e.Message}. Reinstall the launcher.", e); }
            if (Path.GetFileName(Path.GetDirectoryName(path)) != manifest.MinecraftVersion) continue;
            if (manifest.Mods == null || manifest.Mods.Any(m => m is null)
                || manifest.Mods.Select(m => m.ModId).Distinct(StringComparer.Ordinal).Count() != manifest.Mods.Count)
                throw new InvalidDataException($"Shipped mod manifest '{path}' is damaged. Reinstall the launcher.");
            result[manifest.MinecraftVersion] = manifest;
        }
        return result;
    }

    /// <summary>Hash and metadata of a jar, cached by (path, length, modified time). Unreadable metadata becomes Error.</summary>
    internal static ScannedJar Scan(string path, CancellationToken token = default)
    {
        FileInfo file;
        try { file = new FileInfo(path); if (!file.Exists) return new(null, null, "the file no longer exists"); }
        catch (IOException e) { return new(null, null, e.Message); }
        var key = (file.FullName, file.Length, file.LastWriteTimeUtc);
        if (Scans.TryGetValue(key, out var hit)) return hit;
        string hash;
        try
        {
            using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read | FileShare.Delete);
            hash = Convert.ToHexString(SHA512.HashData(stream)).ToLowerInvariant();
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return new(null, null, e.Message); } // Not cached: may be transient.
        ScannedJar result;
        try { result = new(hash, FabricModMetadata.ReadJar(path, token: token) ?? FabricModMetadata.ReadForgeJar(path), null); }
        catch (Exception e) when (e is InvalidDataException or InvalidOperationException) { result = new(hash, null, e.Message); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return new(hash, null, e.Message); }
        if (Scans.Count >= CacheLimit) Scans.Clear();
        Scans[key] = result;
        return result;
    }

    /// <summary><see cref="Scan(string, CancellationToken)"/> as the loader of <paramref name="minecraftVersion"/> sees the jar.</summary>
    internal static ScannedJar ScanFor(string path, string minecraftVersion, CancellationToken token = default) =>
        ForLoader(Scan(path, token), path, GameVersionPolicy.UsesForge(minecraftVersion));

    /// <summary>A jar as the profile's loader sees it: the other loader's mod does not load (on Fabric it reads as before, "no
    /// fabric.mod.json"); on Forge a jar without mcmod.info (a coremod) still loads and is named after its file.</summary>
    private static ScannedJar ForLoader(ScannedJar scan, string path, bool forge)
    {
        if (scan.Info is { } info && info.Forge != forge)
            return scan with { Info = null, Error = forge ? "it is a Fabric mod" : null };
        if (scan.Info != null || scan.Error != null || !forge) return scan;
        var id = Path.GetFileName(path);
        if (id.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)) id = id[..^".disabled".Length];
        id = Path.GetFileNameWithoutExtension(id);
        return scan with { Info = new FabricModInfo(id, id, null, null, Array.Empty<string>(), null, null, Array.Empty<string>(),
            new Dictionary<string, string>(), false, Array.Empty<FabricModInfo>(), null) { Forge = true } };
    }

    private static IReadOnlyList<string> Depends(FabricModInfo info) => info.Depends.Select(d => d.Key + " " + d.Value).ToList();

    private static string? Join(List<string> notes) => notes.Count == 0 ? null : string.Join(". ", notes);

    private static int CountEmbedded(IReadOnlyList<ModInventoryEntry> children) => children.Count + children.Sum(c => CountEmbedded(c.Children));

    private static int Group(ModInventoryEntry entry) => entry.Ownership switch
    {
        ModOwnership.Core => 0,
        ModOwnership.Pack when entry.Status != ModEntryStatus.Unavailable => 1,
        ModOwnership.User => 2,
        ModOwnership.Retired => 3,
        ModOwnership.NativeModule => 4,
        ModOwnership.Pack => 5,
        _ => 6
    };
}
