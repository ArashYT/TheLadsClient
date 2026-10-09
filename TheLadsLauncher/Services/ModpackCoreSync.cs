using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;

namespace TheLadsLauncher.Services;

/// <summary>
/// Keeps the Lads Core inside a modpack instance at the newest build this launcher bundles (game-mods/&lt;mc&gt;/theladscore.jar):
/// on install, on update and before every launch. An instance without an enabled Core jar is never touched, and only instances
/// of this launcher's own list (Modpacks.List) reach it: CurseForge, Modrinth App, Prism and MultiMC instances are started by
/// their own launchers (ExternalInstances) and never pass through here.
/// </summary>
public static class ModpackCoreSync
{
    public const string BundledName = "theladscore.jar";

    /// <summary>The bundled Core for <paramref name="mcVersion"/>: under <paramref name="bundleRoot"/> when given, else next to the
    /// launcher (and, for a development build, in the repository folders above it). Null when this launcher has none.</summary>
    public static string? FindBundledJar(string mcVersion, string? bundleRoot = null)
    {
        if (!GameVersionPolicy.RequiresBundledCore(mcVersion)) return null;
        var candidates = new List<string>();
        if (bundleRoot != null) candidates.Add(Path.Combine(bundleRoot, "game-mods", mcVersion, BundledName));
        else
        {
            candidates.Add(Path.Combine(AppContext.BaseDirectory, "game-mods", mcVersion, BundledName));
            for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir is { Exists: true }; dir = dir.Parent)
            {
                candidates.Add(Path.Combine(dir.FullName, "game-mods", mcVersion, BundledName));
                candidates.Add(Path.Combine(dir.FullName, "TheLadsLauncher", "game-mods", mcVersion, BundledName));
                candidates.Add(Path.Combine(dir.FullName, "artifacts", "client", "game-mods", mcVersion, BundledName));
                candidates.Add(Path.Combine(dir.FullName, "Lads Client", "TheLadsLauncher", "game-mods", mcVersion, BundledName));
                candidates.Add(Path.Combine(dir.FullName, "Lads Client", "artifacts", "client", "game-mods", mcVersion, BundledName));
            }
        }
        return candidates.Where(File.Exists).Select(Path.GetFullPath).FirstOrDefault();
    }

    /// <summary>The enabled Core jars of a mods folder (theladscore*.jar, *TheLadsCore*.jar), the canonical theladscore.jar first.
    /// A jar switched off (.jar.disabled) is the player's choice and is not a Core to update.</summary>
    public static List<string> CoreJars(string modsDirectory)
    {
        if (!Directory.Exists(modsDirectory)) return new List<string>();
        return Directory.EnumerateFiles(modsDirectory)
            .Where(f => f.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)
                        && Path.GetFileName(f) is var name
                        && (name.StartsWith("theladscore", StringComparison.OrdinalIgnoreCase) || name.Contains("TheLadsCore", StringComparison.OrdinalIgnoreCase)))
            .OrderBy(f => !Path.GetFileName(f).Equals(BundledName, StringComparison.OrdinalIgnoreCase))
            .ThenBy(f => f, StringComparer.OrdinalIgnoreCase).ToList();
    }

    /// <summary>Brings the Core of an instance up to date; false when nothing changed (no Core in it, no bundled build, same bytes,
    /// or the jar could not be replaced; <paramref name="status"/> says which of the last).</summary>
    public static bool Sync(ModpackInstance instance, string? bundleRoot = null, Action<string>? status = null) =>
        Sync(instance.GameDirectory, instance.McVersion, bundleRoot, status);

    public static bool Sync(string gameDirectory, string mcVersion, string? bundleRoot = null, Action<string>? status = null)
    {
        var modsDirectory = Path.Combine(gameDirectory, "mods");
        if (CoreJars(modsDirectory).Count == 0) return false;
        var bundled = FindBundledJar(mcVersion, bundleRoot);
        if (bundled == null) return false;
        try
        {
            var updated = SyncJar(modsDirectory, bundled);
            if (updated) status?.Invoke("Updated The Lads Core to this launcher's build.");
            return updated;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException)
        {
            status?.Invoke($"Could not update The Lads Core ({e.Message}); the installed one is kept.");
            return false;
        }
    }

    /// <summary>Replaces the first Core jar when its bytes differ from <paramref name="bundledJar"/>, switches any further Core
    /// jar off (two Cores are a duplicate-mod crash) and keeps the icon sidecar, creating it when missing.</summary>
    public static bool SyncJar(string modsDirectory, string bundledJar)
    {
        var cores = CoreJars(modsDirectory);
        if (cores.Count == 0) return false;
        RequireCoreJar(bundledJar); // a half-copied bundle must never replace a working Core
        var primary = cores[0];
        var changed = false;

        if (!SameBytes(primary, bundledJar))
        {
            var staged = Path.Combine(modsDirectory, $".thelads-core-{Guid.NewGuid():N}.tmp");
            try
            {
                File.Copy(bundledJar, staged);
                File.Move(staged, primary, overwrite: true); // same folder, same volume: the swap is atomic
                changed = true;
            }
            finally { if (File.Exists(staged)) File.Delete(staged); }
        }
        foreach (var extra in cores.Skip(1))
        {
            File.Move(extra, extra + ".disabled", overwrite: true); // the launcher's own off switch, reversible
            changed = true;
        }
        EnsureIcon(primary, bundledJar);
        return changed;
    }

    private static void EnsureIcon(string coreJar, string bundledJar)
    {
        var sidecar = coreJar + ".icon.png";
        if (File.Exists(sidecar)) return;
        try
        {
            if (FabricModMetadata.ReadJar(bundledJar, includeIcon: true)?.Icon is { } icon) File.WriteAllBytes(sidecar, icon);
        }
        catch (Exception e) when (e is IOException or InvalidDataException or UnauthorizedAccessException) { } // the card shows its placeholder
    }

    private static void RequireCoreJar(string jar)
    {
        using var zip = ZipFile.OpenRead(jar); // InvalidDataException when it is not a zip
        if (zip.GetEntry("fabric.mod.json") == null && zip.GetEntry("mcmod.info") == null)
            throw new InvalidDataException($"The bundled Core '{jar}' has no mod metadata.");
    }

    private static bool SameBytes(string left, string right)
    {
        if (new FileInfo(left).Length != new FileInfo(right).Length) return false;
        using var a = File.OpenRead(left);
        using var b = File.OpenRead(right);
        return SHA256.HashData(a).AsSpan().SequenceEqual(SHA256.HashData(b));
    }
}
