using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public sealed record ModTogglePlan(IReadOnlyList<string> TargetIds, bool Enable, IReadOnlyList<string> AlsoDisable,
    IReadOnlyList<string> AlsoEnable, IReadOnlyList<string> Blockers /* non-empty => cannot apply */, IReadOnlyList<string> Warnings);

public sealed record ModToggleResult(bool Success, bool AppliedToFiles, bool RestartRequired, string Message);

/// <summary>
/// Launcher enable/disable workflow: plan the dependency cascade, save the choices in lads-mod-state.json and, when the
/// profile's game is not running, rename the jars now (all or nothing). Jar changes never affect a running game.
/// </summary>
public sealed class ModStateService
{
    public const string CoreDisableWarning =
        "The in-game Lads menu and all Lads features will be absent until you re-enable LadsCore here in the launcher.";
    private const string CoreId = BundledModInstaller.CoreModId;
    private static readonly JsonSerializerOptions Indented = new() { WriteIndented = true };
    private readonly Func<string, bool> isGameRunning;

    /// <param name="isGameRunning">Whether the game of this game directory is running; checked again when applying.</param>
    public ModStateService(Func<string, bool> isGameRunning) => this.isGameRunning = isGameRunning;

    public ModTogglePlan Plan(ModInventory inventory, IReadOnlyCollection<string> ids, bool enable)
    {
        var targets = ids.Distinct(StringComparer.Ordinal).ToList();
        var blockers = new List<string>();
        var warnings = new List<string>();
        var alsoDisable = new List<string>();
        var alsoEnable = new List<string>();
        // Retired copies leave at the next launch, so they neither provide nor need anything.
        var units = inventory.Entries.Where(e => e.Ownership is ModOwnership.Core or ModOwnership.Pack or ModOwnership.User
                && e.Status != ModEntryStatus.Unavailable)
            .GroupBy(e => e.Id, StringComparer.Ordinal).ToDictionary(g => g.Key, g => g.ToList(), StringComparer.Ordinal);
        // As ClientModInstaller.ValidateDependencies: Fabric Loader (0.15+) bundles MixinExtras on every version.
        var implicitIds = new HashSet<string>(StringComparer.Ordinal) { "minecraft", "java", "fabricloader", "mixinextras" };

        foreach (var id in targets)
        {
            var matches = Flatten(inventory.Entries).Where(e => e.Id == id).ToList();
            if (matches.Count == 0) blockers.Add($"'{id}' is not in this profile's mod list. Reload the Mods page.");
            else if (matches.Any(e => e.Ownership == ModOwnership.NativeModule))
                blockers.Add($"{id} is a Lads module: switch it with its module toggle, not as a mod.");
            else if (!units.ContainsKey(id) || !matches.Any(e => e.CanToggle))
                blockers.Add($"{matches[0].DisplayName}: {matches.Select(e => e.ToggleBlockedReason).FirstOrDefault(r => r != null) ?? "it cannot be switched here"}.");
        }
        var requested = units.Where(u => u.Value.Any(e => e.RequestedEnabled)).Select(u => u.Key).ToHashSet(StringComparer.Ordinal);
        var providers = units.ToDictionary(u => u.Key, u => Modules(u.Value).SelectMany(e => e.Provides.Append(e.Id)).ToHashSet(StringComparer.Ordinal),
            StringComparer.Ordinal);
        var needs = units.ToDictionary(u => u.Key, u => Modules(u.Value).SelectMany(e => e.Depends).Select(ParseDependency).Distinct().ToList(),
            StringComparer.Ordinal);
        HashSet<string> Provided(IEnumerable<string> set) => set.SelectMany(id => providers[id]).Concat(implicitIds).ToHashSet(StringComparer.Ordinal);
        string Name(string id) => units.TryGetValue(id, out var rows) ? rows[0].DisplayName : id;

        if (!enable)
        {
            // Disabling: every requested mod whose dependency was provided before and is not afterwards goes too, transitively.
            var before = Provided(requested);
            var remaining = requested.Except(targets).ToHashSet(StringComparer.Ordinal);
            while (true)
            {
                var after = Provided(remaining);
                var broken = remaining.Where(id => needs[id].Any(d => before.Contains(d.Id) && !after.Contains(d.Id)))
                    .OrderBy(id => id, StringComparer.Ordinal).ToList();
                if (broken.Count == 0) break;
                alsoDisable.AddRange(broken);
                remaining.ExceptWith(broken);
            }
            if (targets.Contains(CoreId) || alsoDisable.Contains(CoreId)) warnings.Add(CoreDisableWarning);
            var unknown = remaining.Count(id => units[id].Any(e => !e.DependenciesKnown));
            if (unknown > 0) warnings.Add($"{unknown} mods are not downloaded yet: their dependencies are checked at next launch.");
        }
        else
        {
            // Enabling: required dependencies that exist but are switched off come along; anything unobtainable blocks.
            var enabled = requested.Union(targets.Where(units.ContainsKey)).ToHashSet(StringComparer.Ordinal);
            var queue = new Queue<string>(targets.Where(units.ContainsKey));
            var unknown = new HashSet<string>(StringComparer.Ordinal);
            while (queue.Count > 0)
            {
                var id = queue.Dequeue();
                if (units[id].Any(e => !e.DependenciesKnown)) unknown.Add(id);
                foreach (var (dependency, predicate) in needs[id])
                {
                    if (implicitIds.Contains(dependency)) continue;
                    var provider = enabled.Where(p => providers[p].Contains(dependency)).OrderBy(p => p == dependency ? 0 : 1).FirstOrDefault()
                        ?? units.Keys.Where(p => !enabled.Contains(p) && providers[p].Contains(dependency) && units[p].Any(e => e.CanToggle))
                            .OrderBy(p => p == dependency ? 0 : 1).ThenBy(p => p, StringComparer.Ordinal).FirstOrDefault();
                    if (provider == null)
                    {
                        blockers.Add($"{Name(id)} requires {dependency} {predicate}, which is not installed and not in the Lads pack for " +
                            $"Minecraft {inventory.MinecraftVersion}. Add a mod that provides {dependency} first.");
                        continue;
                    }
                    if (enabled.Add(provider))
                    {
                        alsoEnable.Add(provider);
                        queue.Enqueue(provider);
                    }
                    var version = provider == dependency ? InstalledVersion(units[provider]) : null;
                    if (version != null && !FabricVersionPredicate.Matches(predicate, version))
                        warnings.Add($"{Name(id)} requires {dependency} {predicate}, but {version} is installed; Fabric may refuse to start.");
                }
            }
            if (unknown.Count > 0) warnings.Add($"{unknown.Count} mods are not downloaded yet: their dependencies are checked at next launch.");
            var changed = targets.Concat(alsoEnable).ToHashSet(StringComparer.Ordinal);
            foreach (var id in enabled)
                foreach (var (other, predicate) in Breaks(units[id]))
                {
                    var hit = enabled.FirstOrDefault(p => p != id && providers[p].Contains(other));
                    if (hit == null || (!changed.Contains(id) && !changed.Contains(hit))) continue;
                    var version = InstalledVersion(units[hit]);
                    if (version == null ? predicate == "*" : FabricVersionPredicate.Matches(predicate, version))
                        warnings.Add($"{Name(id)} declares that it breaks {Name(hit)} ({predicate}); Fabric may refuse to start.");
                }
        }
        return new ModTogglePlan(targets, enable, alsoDisable, alsoEnable, blockers.Distinct().ToList(), warnings.Distinct().ToList());
    }

    /// <summary>
    /// The coherent ways out of a failed launch dependency check: disable the mods that need the missing ids (with their
    /// own dependents), or switch back on the disabled mods that provide every missing id. A way that has blockers, or that
    /// would not cover the problem, is null.
    /// </summary>
    public (ModTogglePlan? DisableDependents, ModTogglePlan? EnableProviders) DependencyFixes(ModInventory inventory,
        ClientModDependencyException problem)
    {
        bool Switchable(ModInventoryEntry e) => e.Ownership is ModOwnership.Core or ModOwnership.Pack or ModOwnership.User
            && e.Status != ModEntryStatus.Unavailable && e.CanToggle;
        var dependents = problem.DependentModIds.Where(id => inventory.Entries.Any(e => e.Id == id && Switchable(e) && e.RequestedEnabled)).ToList();
        var disable = dependents.Count > 0 && dependents.Count == problem.DependentModIds.Count ? Plan(inventory, dependents, false) : null;

        var providers = inventory.Entries.Where(e => Switchable(e) && !e.RequestedEnabled)
            .Select(e => (e.Id, Provides: Modules(new[] { e }).SelectMany(m => m.Provides.Append(m.Id)).ToHashSet(StringComparer.Ordinal)))
            .Where(p => p.Provides.Overlaps(problem.MissingModIds)).ToList();
        var covered = problem.MissingModIds.All(id => providers.Any(p => p.Provides.Contains(id)));
        var enable = providers.Count > 0 && covered ? Plan(inventory, providers.Select(p => p.Id).Distinct().ToList(), true) : null;
        return (disable?.Blockers.Count == 0 ? disable : null, enable?.Blockers.Count == 0 ? enable : null);
    }

    public Task<ModToggleResult> ApplyAsync(string gameDirectory, ModInventory inventory, ModTogglePlan plan, CancellationToken cancellationToken = default)
    {
        if (plan.Blockers.Count > 0)
            return Task.FromResult(new ModToggleResult(false, false, false, string.Join(" ", plan.Blockers)));
        var choices = new Dictionary<string, bool>(StringComparer.Ordinal);
        foreach (var id in plan.Enable ? plan.AlsoDisable : plan.TargetIds.Concat(plan.AlsoDisable)) choices[id] = false;
        foreach (var id in plan.Enable ? plan.TargetIds.Concat(plan.AlsoEnable) : plan.AlsoEnable) choices[id] = true;
        return ApplyChoicesAsync(Path.GetFullPath(gameDirectory), inventory, choices, false, cancellationToken);
    }

    /// <summary>Clears all saved mod choices and switches the pack mods and LadsCore back on (user jars keep their disk state).</summary>
    public Task<ModToggleResult> RestoreDefaultsAsync(string gameDirectory, ModInventory inventory, CancellationToken cancellationToken = default)
    {
        // Without a saved choice a disabled file stays disabled, so disabled pack files get an explicit "enabled".
        var choices = inventory.Entries.Where(e => e.Ownership is ModOwnership.Core or ModOwnership.Pack && e.FilePath != null)
            .GroupBy(e => e.Id, StringComparer.Ordinal).Where(g => g.All(e => !e.EnabledOnDisk))
            .ToDictionary(g => g.Key, _ => true, StringComparer.Ordinal);
        return ApplyChoicesAsync(Path.GetFullPath(gameDirectory), inventory, choices, true, cancellationToken);
    }

    /// <summary>Switches a Lads module in thelads_config.json (modules.&lt;name&gt;.enabled only). Refused while the game runs:
    /// LadsCore rewrites that file and applies in-game changes immediately.</summary>
    public async Task<ModToggleResult> SetNativeModuleAsync(string gameDirectory, string moduleName, bool enabled,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(moduleName)) throw new ArgumentException("A Lads module name is required.", nameof(moduleName));
        var game = Path.GetFullPath(gameDirectory);
        if (isGameRunning(game)) return new(false, false, false, ModInventoryService.NativeRunningReason + ".");
        var path = Path.Combine(game, "thelads_config.json");
        JsonObject root;
        try
        {
            var bytes = ModPreferences.ReadShared(path);
            root = bytes == null ? new JsonObject() : JsonNode.Parse(bytes) as JsonObject
                ?? throw new JsonException("the file is not a JSON object");
            ModPreferences.Materialize(root);
        }
        catch (Exception e) when (e is JsonException or ArgumentException)
        {
            return new(false, false, false, $"'{path}' is unreadable ({e.Message}). Fix or delete it, then try again.");
        }
        if (root["modules"] is not (null or JsonObject) || root["modules"]?[moduleName] is not (null or JsonObject))
            return new(false, false, false, $"'{path}' has an unexpected layout for {moduleName}. Fix or delete it, then try again.");
        var modules = root["modules"] as JsonObject ?? (JsonObject)(root["modules"] = new JsonObject())!;
        var module = modules[moduleName] as JsonObject ?? (JsonObject)(modules[moduleName] = new JsonObject())!;
        module["enabled"] = enabled;
        try
        {
            await LockFiles.WriteAtomicallyAsync(path, Encoding.UTF8.GetBytes(root.ToJsonString(Indented)), cancellationToken);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            return new(false, false, false, $"Could not save {moduleName}: {e.Message}");
        }
        return new(true, true, false, $"{moduleName} is {(enabled ? "on" : "off")} for the next launch of this profile.");
    }

    private async Task<ModToggleResult> ApplyChoicesAsync(string game, ModInventory inventory, Dictionary<string, bool> choices,
        bool clearOthers, CancellationToken cancellationToken)
    {
        var previous = new Dictionary<string, JsonNode?>(StringComparer.Ordinal);
        JsonNode? previousMods = null;
        try
        {
            await ModPreferences.UpdateAsync(game, root =>
            {
                var mods = (JsonObject)root["mods"]!;
                if (clearOthers)
                {
                    previousMods = mods.DeepClone();
                    mods.Clear();
                }
                foreach (var (id, enabled) in choices)
                {
                    previous[id] = mods[id]?.DeepClone();
                    ModPreferences.SetMod(root, id, enabled,
                        Flatten(inventory.Entries).Where(e => e.Id == id).Select(e => e.ProjectId).FirstOrDefault(p => p != null));
                }
            }, cancellationToken);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            return new(false, false, false, $"Could not save your mod choices: {e.Message}");
        }

        if (isGameRunning(game))
            return new(true, false, true, "Saved. Minecraft is running for this profile, so the change applies after you restart the game.");

        var done = new List<(string From, string To)>();
        var notes = new List<string>();
        try
        {
            foreach (var (from, planned, id) in Renames(inventory, choices))
            {
                cancellationToken.ThrowIfCancellationRequested();
                // The inventory may be stale: rename only a file that still holds the same mod.
                if (ModInventoryService.ScanFor(from, inventory.MinecraftVersion, cancellationToken).Info?.Id != id)
                    throw new IOException($"'{from}' changed since the mod list was loaded. Reload the Mods page and try again.");
                var to = planned.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase) ? DisabledDestination(game, from, id, notes) : planned;
                try { File.Move(from, to); }
                catch (IOException e) { throw new IOException($"Could not rename '{Path.GetFileName(from)}' to '{Path.GetFileName(to)}' ({e.Message})", e); }
                done.Add((from, to));
            }
        }
        catch (Exception failure) when (failure is IOException or UnauthorizedAccessException or OperationCanceledException)
        {
            var errors = new List<string>();
            for (var i = done.Count - 1; i >= 0; i--)
            {
                try { File.Move(done[i].To, done[i].From); }
                catch (Exception e) when (e is IOException or UnauthorizedAccessException) { errors.Add($"'{done[i].To}' ({e.Message})"); }
            }
            try
            {
                await ModPreferences.UpdateAsync(game, root =>
                {
                    var mods = (JsonObject)root["mods"]!;
                    if (previousMods != null) root["mods"] = previousMods.DeepClone();
                    else
                        foreach (var (id, node) in previous)
                        {
                            mods.Remove(id);
                            if (node != null) mods[id] = node.DeepClone();
                        }
                }, CancellationToken.None);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { errors.Add($"saved choices ({e.Message})"); }
            return new(false, false, false, $"{failure.Message}. " + (errors.Count == 0
                ? "Your files and saved choices were restored."
                : "These could not be restored, check the Mods folder: " + string.Join("; ", errors) + "."));
        }
        var later = choices.Where(c => c.Value && !inventory.Entries.Any(e => e.Id == c.Key && e.FilePath != null)).Select(c => c.Key).ToList();
        if (later.Count > 0) notes.Add($"{string.Join(", ", later)} will be installed at the next launch.");
        return new(true, true, false, string.Join(" ", notes.Prepend("Applied.")));
    }

    /// <summary>
    /// Where an enabled jar goes when it is disabled. Normally "&lt;name&gt;.disabled"; when that twin already exists (e.g. the
    /// v1.2.2 "reinstalled next to a disabled copy" state), LadsCore and a byte-identical copy go to mods-disabled\&lt;time&gt;\ and
    /// the twin stays the disabled copy (as BundledModInstaller does); a different jar gets a free "&lt;name&gt; (2).jar.disabled".
    /// </summary>
    private static string DisabledDestination(string game, string from, string id, List<string> notes)
    {
        var twin = from + ".disabled";
        if (!File.Exists(twin) && !Directory.Exists(twin)) return twin;
        var hash = ModInventoryService.Scan(from).Hash;
        if (id == CoreId || (hash != null && hash == ModInventoryService.Scan(twin).Hash))
        {
            var folder = Path.Combine(game, "mods-disabled", $"{DateTime.UtcNow:yyyyMMddTHHmmssfffZ}-{Guid.NewGuid():N}");
            Directory.CreateDirectory(folder);
            var backup = Path.Combine(folder, Path.GetFileName(from));
            notes.Add($"Kept '{Path.GetFileName(twin)}' as the disabled copy; '{Path.GetFileName(from)}' was moved to '{backup}'.");
            return backup;
        }
        var stem = from[..^".jar".Length];
        for (var n = 2; ; n++)
        {
            var free = $"{stem} ({n}).jar.disabled";
            if (File.Exists(free) || Directory.Exists(free)) continue;
            notes.Add($"'{Path.GetFileName(twin)}' already exists (a different jar), so '{Path.GetFileName(from)}' was disabled as '{Path.GetFileName(free)}'; both are kept.");
            return free;
        }
    }

    // Disabling renames every enabled copy; enabling renames one disabled copy unless an enabled copy already exists.
    private static IEnumerable<(string From, string To, string Id)> Renames(ModInventory inventory, Dictionary<string, bool> choices)
    {
        foreach (var (id, enabled) in choices)
        {
            var files = inventory.Entries.Where(e => e.Id == id && e.FilePath != null
                && e.Ownership is ModOwnership.Core or ModOwnership.Pack or ModOwnership.User).ToList();
            if (!enabled)
            {
                foreach (var file in files.Where(f => f.EnabledOnDisk))
                    yield return (file.FilePath!, file.FilePath + ".disabled", id);
            }
            else if (!files.Any(f => f.EnabledOnDisk))
            {
                var file = files.Where(f => f.Status != ModEntryStatus.Invalid).OrderBy(f => f.Ownership == ModOwnership.User ? 1 : 0).FirstOrDefault();
                if (file != null) yield return (file.FilePath!, file.FilePath![..^".disabled".Length], id);
            }
        }
    }

    // The mod and its embedded Fabric modules that load on the client (file-name ids of plain library jars are not mod ids).
    private static IEnumerable<ModInventoryEntry> Modules(IEnumerable<ModInventoryEntry> rows) =>
        rows.SelectMany(row => Flatten(new[] { row })).Where(e => FabricModMetadata.ValidId(e.Id) && e.Note != ModInventoryService.ServerOnlyNote);

    private static IEnumerable<ModInventoryEntry> Flatten(IEnumerable<ModInventoryEntry> rows) =>
        rows.SelectMany(row => Flatten(row.Children).Prepend(row));

    private static IEnumerable<(string Id, string Predicate)> Breaks(IEnumerable<ModInventoryEntry> rows) =>
        rows.Where(r => r.FilePath != null).SelectMany(r => ModInventoryService.Scan(r.FilePath!).Info?.Breaks
            ?? new Dictionary<string, string>()).Select(b => (b.Key, b.Value));

    // Fabric versions are known only from jars on disk; manifest versions of pending entries are Modrinth version numbers.
    private static string? InstalledVersion(IEnumerable<ModInventoryEntry> rows) =>
        rows.Where(e => e.FilePath != null).Select(e => e.Version).FirstOrDefault(v => v != null);

    private static (string Id, string Predicate) ParseDependency(string dependency)
    {
        var space = dependency.IndexOf(' ');
        return space < 0 ? (dependency, "*") : (dependency[..space], dependency[(space + 1)..]);
    }
}
