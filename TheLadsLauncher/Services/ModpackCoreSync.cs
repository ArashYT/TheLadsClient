using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;

namespace TheLadsLauncher.Services;

/// <summary>
/// Keeps the Lads Core inside a modpack instance at the newest build this launcher bundles (game-mods/&lt;mc&gt;/theladscore.jar):
/// on install, on update and before every launch. An instance without an enabled Core jar is never touched, and only instances
/// of this launcher's own list (Modpacks.List) reach it: CurseForge, Modrinth App, Prism and MultiMC instances (ExternalInstances),
/// started directly or by their own launchers, never pass through here (ModpackLauncher skips it for them).
/// </summary>
public static class ModpackCoreSync
{
    public const string BundledName = "theladscore.jar";

    /// <summary>Set to 1 to leave every modpack's Core alone, for a run that tests a Core built from source (the QA harness copies
    /// its own build into the instance and the launcher must not swap it back for the bundled one).</summary>
    public const string SkipEnvironmentVariable = "LADS_SKIP_MODPACK_CORE_SYNC";

    private static readonly ConcurrentDictionary<string, (long Length, long LastWriteTicks, string Sha256)> _sha256Cache = new(StringComparer.OrdinalIgnoreCase);
    private static readonly ConcurrentDictionary<string, (long Length, long LastWriteTicks, bool Valid)> _validCoreCache = new(StringComparer.OrdinalIgnoreCase);

    /// <summary>The bundled Core for <paramref name="mcVersion"/> (or another bundled jar of that version, <paramref name="fileName"/>):
    /// under <paramref name="bundleRoot"/> when given, else next to the launcher (and, for a development build, in the repository
    /// folders above it). Null when this launcher has none.</summary>
    public static string? FindBundledJar(string mcVersion, string? bundleRoot = null, string fileName = BundledName)
    {
        if (!GameVersionPolicy.RequiresBundledCore(mcVersion)) return null;
        var candidates = new List<string>();
        if (bundleRoot != null) candidates.Add(Path.Combine(bundleRoot, "game-mods", mcVersion, fileName));
        else
        {
            candidates.Add(Path.Combine(AppContext.BaseDirectory, "game-mods", mcVersion, fileName));
            for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir is { Exists: true }; dir = dir.Parent)
            {
                candidates.Add(Path.Combine(dir.FullName, "game-mods", mcVersion, fileName));
                candidates.Add(Path.Combine(dir.FullName, "TheLadsLauncher", "game-mods", mcVersion, fileName));
                candidates.Add(Path.Combine(dir.FullName, "artifacts", "client", "game-mods", mcVersion, fileName));
                candidates.Add(Path.Combine(dir.FullName, "Lads Client", "TheLadsLauncher", "game-mods", mcVersion, fileName));
                candidates.Add(Path.Combine(dir.FullName, "Lads Client", "artifacts", "client", "game-mods", mcVersion, fileName));
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
        if (Environment.GetEnvironmentVariable(SkipEnvironmentVariable) == "1") return false;
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
        var fullPath = Path.GetFullPath(jar);
        var fi = new FileInfo(fullPath);
        if (fi.Exists && _validCoreCache.TryGetValue(fullPath, out var cached)
            && cached.Length == fi.Length && cached.LastWriteTicks == fi.LastWriteTimeUtc.Ticks && cached.Valid)
        {
            return;
        }
        using var zip = ZipFile.OpenRead(fullPath); // InvalidDataException when it is not a zip
        if (zip.GetEntry("fabric.mod.json") == null && zip.GetEntry("mcmod.info") == null)
            throw new InvalidDataException($"The bundled Core '{jar}' has no mod metadata.");
        if (fi.Exists)
        {
            _validCoreCache[fullPath] = (fi.Length, fi.LastWriteTimeUtc.Ticks, true);
        }
    }

    internal static string GetSha256(string path)
    {
        var fullPath = Path.GetFullPath(path);
        var fi = new FileInfo(fullPath);
        if (fi.Exists && _sha256Cache.TryGetValue(fullPath, out var cached)
            && cached.Length == fi.Length && cached.LastWriteTicks == fi.LastWriteTimeUtc.Ticks)
        {
            return cached.Sha256;
        }
        using var stream = new FileStream(fullPath, FileMode.Open, FileAccess.Read, FileShare.Read | FileShare.Delete);
        var hash = Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
        if (fi.Exists)
        {
            _sha256Cache[fullPath] = (fi.Length, fi.LastWriteTimeUtc.Ticks, hash);
        }
        return hash;
    }

    private static bool SameBytes(string left, string right)
    {
        var leftInfo = new FileInfo(left);
        var rightInfo = new FileInfo(right);
        if (!leftInfo.Exists || !rightInfo.Exists || leftInfo.Length != rightInfo.Length) return false;
        return string.Equals(GetSha256(left), GetSha256(right), StringComparison.OrdinalIgnoreCase);
    }
}
