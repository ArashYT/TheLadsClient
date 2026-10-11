using System;
using System.IO;
using System.Linq;

namespace TheLadsLauncher.Services;

/// <summary>
/// Forge 1.20.1 constructs mods in parallel, and two Zero SMP mod pairs are not safe with that (about 3 launches in 50 on the laptop,
/// 1.9.x research). Create Deco runs Create's item list on its own loading thread while Create fills the same Registrate table, so an
/// item is lost ("Registry entry not present: create:potato_cannon"). Supplementaries subscribes to Quark's Zeta event bus while Quark
/// does, and the bus's hash map breaks mid-resize. The install is fine and the next launch loads, so the launchers start the game once
/// more when the crash report written by this launch shows one of these. Every other crash is left alone.
/// </summary>
public static class ModLoadingRace
{
    /// <summary>The race shown by the newest crash report written at or after <paramref name="sinceUtc"/>, or null.</summary>
    public static string? FindSince(string gameDirectory, DateTime sinceUtc)
    {
        try
        {
            var dir = new DirectoryInfo(Path.Combine(gameDirectory, "crash-reports"));
            if (!dir.Exists) return null;
            var newest = dir.EnumerateFiles("crash-*.txt").Where(f => f.LastWriteTimeUtc >= sinceUtc).MaxBy(f => f.LastWriteTimeUtc);
            return newest == null ? null : Match(File.ReadAllText(newest.FullName)) is { } race ? $"{race} ({newest.Name})" : null;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return null; }
    }

    /// <summary>What went wrong, when this crash report is one of the known mod-loading races; otherwise null.</summary>
    public static string? Match(string report)
    {
        // Only crashes while the game is still loading: "Initializing game" (thrown in Minecraft's constructor) or Forge's mod loading
        // error screen ("Mod loading error has occurred").
        if (!report.Contains("Description: Initializing game") && !report.Contains("Description: Mod loading error has occurred")) return null;
        if (report.Contains("Registry entry not present: ") && report.Contains("com.tterrag.registrate"))
            return "a Registrate entry was lost while mods loaded in parallel";
        // Zeta's bus map breaks mid-resize (rehash) or is read half-resized (find: ArrayIndexOutOfBoundsException, Arash-PC 2026-10-11).
        if (report.Contains("FMLModContainer.constructMod") && (report.Contains(".rehash(") || report.Contains("HashMap.resize(") || report.Contains("ConcurrentModificationException")
            || (report.Contains("ZetaEventBus") && report.Contains("OpenHashMap.find("))))
            return "a shared map broke while mods loaded in parallel";
        return null;
    }
}
