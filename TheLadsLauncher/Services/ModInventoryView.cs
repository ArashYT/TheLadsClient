using System;
using System.Collections.Generic;
using System.Linq;

namespace TheLadsLauncher.Services;

/// <summary>The Mods page filters, in the order of its filter box.</summary>
public enum ModListFilter { All, LadsModules, ThirdParty, Enabled, Disabled, Libraries }

/// <summary>What the launcher's Mods page shows of a <see cref="ModInventory"/>: filtered rows, the counts line and the
/// ids a launch records as loaded. Pure functions of the inventory, so the page, the preview and tests agree.</summary>
public static class ModInventoryView
{
    public static readonly IReadOnlyList<string> FilterLabels =
        new[] { "All", "Lads modules", "Third-party", "Enabled", "Disabled", "Libraries & dependencies" };

    /// <summary>A visible top-level row; <paramref name="Expanded"/> shows its embedded children.</summary>
    public sealed record Row(ModInventoryEntry Entry, bool Expanded);

    /// <summary>
    /// Rows for a filter and a search (display name, upstream name, mod id, file name; case-insensitive). Children count:
    /// a row is shown when it or one of its embedded children matches the filter and the search, and it opens when a child
    /// matches the search, or matches a filter the row itself does not (Libraries shows a mod's embedded libraries).
    /// </summary>
    public static IReadOnlyList<Row> Filter(ModInventory inventory, ModListFilter filter, string? search)
    {
        var text = search?.Trim() ?? "";
        var needed = inventory.Entries.SelectMany(Flatten).SelectMany(e => e.Depends)
            .Select(d => d.Split(' ', 2)[0]).ToHashSet(StringComparer.Ordinal);
        bool Kind(ModInventoryEntry e) => filter switch
        {
            ModListFilter.LadsModules => e.Ownership is ModOwnership.Core or ModOwnership.NativeModule,
            ModListFilter.ThirdParty => e.Ownership is ModOwnership.Pack or ModOwnership.User or ModOwnership.Retired,
            ModListFilter.Enabled => IsModOrModule(e) && e.RequestedEnabled && e.Status != ModEntryStatus.Unavailable,
            ModListFilter.Disabled => IsModOrModule(e) && !e.RequestedEnabled && e.Status != ModEntryStatus.Unavailable,
            ModListFilter.Libraries => e.Ownership != ModOwnership.Platform
                && (e.IsLibrary || needed.Contains(e.Id) || e.Provides.Any(needed.Contains)),
            _ => true
        };
        bool Found(ModInventoryEntry e) => text.Length == 0
            || new[] { e.DisplayName, e.UpstreamName, e.Id, e.FileName }.Any(s => s?.Contains(text, StringComparison.OrdinalIgnoreCase) == true);

        var rows = new List<Row>();
        foreach (var entry in inventory.Entries)
        {
            var children = entry.Children.SelectMany(Flatten).ToList();
            var kindHere = Kind(entry);
            var kindInside = children.Any(Kind);
            var foundInside = text.Length > 0 && children.Any(Found);
            if ((kindHere || kindInside) && (Found(entry) || foundInside))
                rows.Add(new Row(entry, foundInside || (!kindHere && kindInside)));
        }
        return rows;
    }

    /// <summary>"88 enabled · 0 disabled · 3 pending · 12 unavailable" (files in Mods; pack mods not downloaded yet; mods
    /// shipped only for other Minecraft versions).</summary>
    public static string CountsText(ModInventoryCounts counts) =>
        $"{counts.EnabledFiles} enabled · {counts.DisabledFiles} disabled · {counts.PendingDownloads} pending · {counts.Unavailable} unavailable";

    /// <summary>Fabric ids of the enabled jars in Mods: what a game started now loads (recorded in the running marker).</summary>
    public static IReadOnlyList<string> EnabledJarIds(ModInventory inventory) => inventory.Entries
        .Where(e => e.FilePath != null && e.EnabledOnDisk && FabricModMetadata.ValidId(e.Id))
        .Select(e => e.Id).Distinct(StringComparer.Ordinal).OrderBy(id => id, StringComparer.Ordinal).ToList();

    /// <summary>Whether <paramref name="inventory"/> lists <paramref name="gameDirectory"/>: the Mods page changes only the
    /// active profile's list, never one still shown from before a profile switch.</summary>
    public static bool IsFor(ModInventory? inventory, string gameDirectory) =>
        inventory != null && SafeFileOps.PathsEqual(inventory.GameDirectory, gameDirectory);

    public static IEnumerable<ModInventoryEntry> Flatten(ModInventoryEntry entry) =>
        entry.Children.SelectMany(Flatten).Prepend(entry);

    // Enabled/Disabled are about switchable things: mods and Lads modules, not the platform or embedded libraries.
    private static bool IsModOrModule(ModInventoryEntry e) => e.Ownership is not (ModOwnership.Platform or ModOwnership.Embedded);
}
