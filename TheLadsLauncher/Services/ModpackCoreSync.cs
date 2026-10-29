using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using System.Text.Json;

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

    /// <summary>The disabled Core jars of a mods folder (theladscore*.jar.disabled, *TheLadsCore*.jar.disabled).</summary>
    public static List<string> DisabledCoreJars(string modsDirectory)
    {
        if (!Directory.Exists(modsDirectory)) return new List<string>();
        return Directory.EnumerateFiles(modsDirectory)
            .Where(f => f.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase)
                        && Path.GetFileName(f) is var name
                        && (name.StartsWith("theladscore", StringComparison.OrdinalIgnoreCase) || name.Contains("TheLadsCore", StringComparison.OrdinalIgnoreCase)))
            .OrderBy(f => !Path.GetFileName(f).Equals(BundledName + ".disabled", StringComparison.OrdinalIgnoreCase))
            .ThenBy(f => f, StringComparer.OrdinalIgnoreCase).ToList();
    }

    /// <summary>Checks whether a list of mod files contains Sinytra Connector.</summary>
    public static bool HasSinytraConnector(IEnumerable<string>? modFiles)
    {
        if (modFiles == null) return false;
        foreach (var file in modFiles)
        {
            var name = Path.GetFileName(file);
            if (name.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)) continue;
            if (!name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)) continue;
            if (name.StartsWith("theladscore", StringComparison.OrdinalIgnoreCase) ||
                name.Contains("TheLadsCore", StringComparison.OrdinalIgnoreCase))
                continue;
            if (name.Contains("connector", StringComparison.OrdinalIgnoreCase) ||
                name.Contains("sinytra", StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
        }
        return false;
    }

    /// <summary>Checks whether a mods directory contains an enabled Sinytra Connector jar.</summary>
    public static bool HasSinytraConnector(string? modsDirectory)
    {
        if (string.IsNullOrEmpty(modsDirectory) || !Directory.Exists(modsDirectory)) return false;
        try { return HasSinytraConnector(Directory.EnumerateFiles(modsDirectory)); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { return false; }
    }

    /// <summary>Checks whether a list of mod files contains an enabled Fabric API jar (fabric-api, fabric_api, fabricapi; forgified-fabric-api counts).</summary>
    public static bool HasFabricApi(IEnumerable<string>? modFiles)
    {
        if (modFiles == null) return false;
        foreach (var file in modFiles)
        {
            var name = Path.GetFileName(file);
            if (name.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)) continue;
            if (!name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)) continue;
            if (name.StartsWith("theladscore", StringComparison.OrdinalIgnoreCase) ||
                name.Contains("TheLadsCore", StringComparison.OrdinalIgnoreCase))
                continue;
            if (name.Contains("fabric-api", StringComparison.OrdinalIgnoreCase) ||
                name.Contains("fabric_api", StringComparison.OrdinalIgnoreCase) ||
                name.Contains("fabricapi", StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
        }
        return false;
    }

    /// <summary>Checks whether a mods directory contains an enabled Fabric API jar.</summary>
    public static bool HasFabricApi(string? modsDirectory)
    {
        if (string.IsNullOrEmpty(modsDirectory) || !Directory.Exists(modsDirectory)) return false;
        try { return HasFabricApi(Directory.EnumerateFiles(modsDirectory)); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { return false; }
    }

    private static readonly ConcurrentDictionary<string, (long LastWriteTicks, Version? MinVersion, string? MinStr)> _fabricLoaderConstraintCache = new(StringComparer.OrdinalIgnoreCase);

    public static bool TryParseVersion(string? str, out Version? version)
    {
        version = null;
        if (string.IsNullOrWhiteSpace(str)) return false;
        var s = str.Trim().TrimStart('v', 'V', '>', '=', ' ', '^', '~');
        int dash = s.IndexOf('-');
        if (dash >= 0) s = s[..dash];
        int plus = s.IndexOf('+');
        if (plus >= 0) s = s[..plus];
        if (string.IsNullOrEmpty(s)) return false;
        if (!s.Contains('.')) s += ".0";
        return Version.TryParse(s, out version);
    }

    public static (Version? MinVersion, string? MinStr) GetBundledFabricLoaderConstraint(string jarPath)
    {
        var fullPath = Path.GetFullPath(jarPath);
        var fi = new FileInfo(fullPath);
        if (!fi.Exists) return (null, null);

        long ticks = fi.LastWriteTimeUtc.Ticks;
        if (_fabricLoaderConstraintCache.TryGetValue(fullPath, out var cached) && cached.LastWriteTicks == ticks)
        {
            return (cached.MinVersion, cached.MinStr);
        }

        try
        {
            using var zip = ZipFile.OpenRead(fullPath);
            var entry = zip.GetEntry("fabric.mod.json");
            if (entry != null)
            {
                using var s = entry.Open();
                using var doc = JsonDocument.Parse(s);
                if (doc.RootElement.TryGetProperty("depends", out var depends) &&
                    depends.TryGetProperty("fabricloader", out var flProp))
                {
                    string? raw = null;
                    if (flProp.ValueKind == JsonValueKind.String)
                        raw = flProp.GetString();
                    else if (flProp.ValueKind == JsonValueKind.Array && flProp.GetArrayLength() > 0)
                        raw = flProp[0].GetString();

                    if (!string.IsNullOrWhiteSpace(raw))
                    {
                        string minStr = raw.Trim().TrimStart('>', '=', ' ', '^', '~');
                        if (TryParseVersion(minStr, out var ver))
                        {
                            _fabricLoaderConstraintCache[fullPath] = (ticks, ver, minStr);
                            return (ver, minStr);
                        }
                    }
                }
            }
        }
        catch { }

        _fabricLoaderConstraintCache[fullPath] = (ticks, null, null);
        return (null, null);
    }

    /// <summary>
    /// Checks whether The Lads Core is supported for a given Minecraft version, loader, and installed mods.
    /// "Supports" means this launcher bundles a Core for the pack's version AND the pack's loader can load it.
    /// </summary>
    public static bool IsSupported(
        string? mcVersion,
        string? loader,
        IEnumerable<string>? modFiles,
        string? loaderVersion,
        string? bundleRoot,
        out string? reason)
    {
        if (string.IsNullOrWhiteSpace(mcVersion))
        {
            reason = "Unknown Minecraft version.";
            return false;
        }

        var normalizedLoader = (loader ?? "vanilla").Trim().ToLowerInvariant();
        if (normalizedLoader == "vanilla")
        {
            reason = "Vanilla Minecraft does not support mods.";
            return false;
        }

        if (!GameVersionPolicy.RequiresBundledCore(mcVersion))
        {
            reason = $"No Lads Core is bundled for Minecraft {mcVersion}.";
            return false;
        }

        if (mcVersion == GameVersionPolicy.ForgeMinecraftVersion) // 1.8.9
        {
            if (normalizedLoader == "forge")
            {
                reason = null;
                return true;
            }
            reason = "Minecraft 1.8.9 Lads Core requires Forge.";
            return false;
        }

        // For all other bundled versions (1.20.1, 1.21.11, 26.2, 26.3), the Core is a Fabric mod
        if (mcVersion == "1.20.1" && normalizedLoader == "forge")
        {
            if (!HasSinytraConnector(modFiles))
            {
                reason = "Minecraft 1.20.1 Forge requires Sinytra Connector to run The Lads Core.";
                return false;
            }
            if (!HasFabricApi(modFiles))
            {
                reason = "This modpack has no Fabric API, which The Lads Core needs.";
                return false;
            }
            reason = null;
            return true;
        }

        if (normalizedLoader is "fabric" or "quilt")
        {
            if (!HasFabricApi(modFiles))
            {
                reason = "This modpack has no Fabric API, which The Lads Core needs.";
                return false;
            }

            if (normalizedLoader == "fabric")
            {
                var bundled = FindBundledJar(mcVersion, bundleRoot);
                if (bundled != null)
                {
                    var (minVer, minStr) = GetBundledFabricLoaderConstraint(bundled);
                    if (minVer != null && TryParseVersion(loaderVersion, out var lv) && lv != null && lv < minVer)
                    {
                        reason = $"Fabric Loader {loaderVersion} is older than The Lads Core needs ({minStr}).";
                        return false;
                    }
                }
            }

            reason = null;
            return true;
        }

        reason = $"Minecraft {mcVersion} Lads Core requires Fabric.";
        return false;
    }

    public static bool IsSupported(string? mcVersion, string? loader, IEnumerable<string>? modFiles, out string? reason) =>
        IsSupported(mcVersion, loader, modFiles, null, null, out reason);

    public static bool IsSupported(
        string? mcVersion,
        string? loader,
        string? modsDirectory,
        string? loaderVersion,
        string? bundleRoot,
        out string? reason)
    {
        IEnumerable<string>? files = null;
        if (!string.IsNullOrEmpty(modsDirectory) && Directory.Exists(modsDirectory))
        {
            try { files = Directory.EnumerateFiles(modsDirectory); } catch { }
        }
        return IsSupported(mcVersion, loader, files, loaderVersion, bundleRoot, out reason);
    }

    public static bool IsSupported(string? mcVersion, string? loader, string? modsDirectory, out string? reason) =>
        IsSupported(mcVersion, loader, modsDirectory, null, null, out reason);

    public static bool IsSupported(ModpackInstance instance, string? bundleRoot, out string? reason) =>
        IsSupported(instance.McVersion, instance.Loader, Path.Combine(instance.GameDirectory, "mods"), instance.LoaderVersion, bundleRoot, out reason);

    public static bool IsSupported(ModpackInstance instance, out string? reason) =>
        IsSupported(instance, (string?)null, out reason);

    public static bool IsSupported(ModpackInstance instance) =>
        IsSupported(instance, out _);

    public static bool IsSupported(string? mcVersion, string? loader) =>
        IsSupported(mcVersion, loader, (IEnumerable<string>?)null, out _);

    /// <summary>
    /// Resolves the default setting for Lads Core on an instance (true when supported).
    /// </summary>
    public static bool ResolveDefaultLadsCore(ModpackInstance instance, string? bundleRoot = null) =>
        IsSupported(instance, bundleRoot, out _);

    public static bool ResolveDefaultLadsCore(ModpackInstance instance) =>
        ResolveDefaultLadsCore(instance, null);

    /// <summary>Disables any root Jade jar in <paramref name="modsDirectory"/> when Core is present for Minecraft 26.3 (Jade is built into Core 26.3).</summary>
    public static bool DisableRootJadeJars(string modsDirectory, Action<string>? status = null)
    {
        if (!Directory.Exists(modsDirectory)) return false;
        bool disabledAny = false;
        try
        {
            var files = Directory.EnumerateFiles(modsDirectory)
                .Where(f => f.EndsWith(".jar", StringComparison.OrdinalIgnoreCase))
                .ToList();

            foreach (var jar in files)
            {
                var name = Path.GetFileName(jar);
                if (name.StartsWith("theladscore", StringComparison.OrdinalIgnoreCase) ||
                    name.Contains("TheLadsCore", StringComparison.OrdinalIgnoreCase))
                    continue;

                try
                {
                    var info = FabricModMetadata.ReadJar(jar);
                    if (string.Equals(info?.Id, "jade", StringComparison.OrdinalIgnoreCase))
                    {
                        var dest = jar + ".disabled";
                        File.Move(jar, dest, overwrite: true);
                        var icon = jar + ".icon.png";
                        if (File.Exists(icon))
                        {
                            try { File.Move(icon, jar + ".disabled.icon.png", overwrite: true); } catch { }
                        }
                        status?.Invoke($"Disabled '{name}': Jade is built into The Lads Core.");
                        disabledAny = true;
                    }
                }
                catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException) { }
            }
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        return disabledAny;
    }

    /// <summary>Brings the Core of an instance up to date according to its <see cref="ModpackInstance.LadsCore"/> setting:
    /// - ON: adds the bundled jar (mods\theladscore.jar) when missing, restores disabled jars, and syncs to bundled build.
    /// - OFF: renames any enabled Core jars to .disabled (never deletes any jar, even pack-shipped jars).
    /// - Packs installed before this update get the default (ON when supported) the first time opened/launched.
    /// - External instances and runs with LADS_SKIP_MODPACK_CORE_SYNC=1 are left untouched.
    /// - Official Experiences keep their Core always enabled.
    /// Returns false when nothing changed.</summary>
    public static bool Sync(ModpackInstance instance, string? bundleRoot = null, Action<string>? status = null)
    {
        if (instance.IsExternal) return false;
        if (Environment.GetEnvironmentVariable(SkipEnvironmentVariable) == "1") return false;

        // Experiences keep Core unconditionally enabled
        bool isExperience = ExperiencesService.AllExperiences.Any(e =>
            string.Equals(e.Id, ExperiencesService.CanonicalPackId(instance.Id), StringComparison.OrdinalIgnoreCase));
        if (isExperience)
        {
            instance.LadsCore = true;
        }

        // Packs installed before this update get the default (ON when supported)
        if (!instance.LadsCore.HasValue)
        {
            instance.LadsCore = ResolveDefaultLadsCore(instance, bundleRoot);
            try { Modpacks.Save(instance); } catch { }
        }

        var modsDirectory = Path.Combine(instance.GameDirectory, "mods");
        bool enabled = instance.LadsCore.Value;

        if (!enabled)
        {
            var enabledCores = CoreJars(modsDirectory);
            if (enabledCores.Count == 0) return false;
            foreach (var jar in enabledCores)
            {
                try
                {
                    File.Move(jar, jar + ".disabled", overwrite: true);
                    var icon = jar + ".icon.png";
                    if (File.Exists(icon))
                    {
                        try { File.Move(icon, jar + ".disabled.icon.png", overwrite: true); } catch { }
                    }
                }
                catch (Exception e) when (e is IOException or UnauthorizedAccessException)
                {
                    status?.Invoke($"Could not disable The Lads Core ({e.Message}).");
                    return false;
                }
            }
            status?.Invoke("Disabled The Lads Core for this modpack.");
            return true;
        }

        // Enabled branch: check IsSupported FIRST before touching anything!
        if (!IsSupported(instance, bundleRoot, out var reason))
        {
            status?.Invoke($"The Lads Core is not supported: {reason}");
            return false;
        }

        var bundled = FindBundledJar(instance.McVersion, bundleRoot);
        if (bundled == null) return false;

        bool disabledJade = false;
        if (instance.McVersion is "26.3" or "26.2")
        {
            disabledJade = DisableRootJadeJars(modsDirectory, status);
        }

        // Restore any disabled Core jars first (OFF -> ON restores)
        var disabledCores = DisabledCoreJars(modsDirectory);
        foreach (var disabled in disabledCores)
        {
            try
            {
                var restored = disabled[..^".disabled".Length];
                File.Move(disabled, restored, overwrite: true);
                var disabledIcon = disabled + ".icon.png";
                if (File.Exists(disabledIcon))
                {
                    try { File.Move(disabledIcon, restored + ".icon.png", overwrite: true); } catch { }
                }
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        }

        bool newlyAdded = false;
        var cores = CoreJars(modsDirectory);
        if (cores.Count == 0)
        {
            try
            {
                Directory.CreateDirectory(modsDirectory);
                var dest = Path.Combine(modsDirectory, BundledName);
                File.Copy(bundled, dest, overwrite: true);
                EnsureIcon(dest, bundled);
                newlyAdded = true;
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                status?.Invoke($"Could not copy The Lads Core ({e.Message}).");
                return false;
            }
        }

        try
        {
            var updated = SyncJar(modsDirectory, bundled);
            if (newlyAdded || updated || disabledJade)
            {
                if (newlyAdded || updated)
                    status?.Invoke(newlyAdded ? "Added The Lads Core to this modpack." : "Updated The Lads Core to this launcher's build.");
                return true;
            }
            return false;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException)
        {
            status?.Invoke($"Could not update The Lads Core ({e.Message}); the installed one is kept.");
            return disabledJade;
        }
    }

    public static bool Sync(string gameDirectory, string mcVersion, string? bundleRoot = null, Action<string>? status = null)
    {
        if (Environment.GetEnvironmentVariable(SkipEnvironmentVariable) == "1") return false;
        var modsDirectory = Path.Combine(gameDirectory, "mods");
        if (CoreJars(modsDirectory).Count == 0) return false;
        var bundled = FindBundledJar(mcVersion, bundleRoot);
        if (bundled == null) return false;
        bool disabledJade = false;
        if (mcVersion is "26.3" or "26.2")
        {
            disabledJade = DisableRootJadeJars(modsDirectory, status);
        }
        try
        {
            var updated = SyncJar(modsDirectory, bundled);
            if (updated) status?.Invoke("Updated The Lads Core to this launcher's build.");
            return updated || disabledJade;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or InvalidDataException)
        {
            status?.Invoke($"Could not update The Lads Core ({e.Message}); the installed one is kept.");
            return disabledJade;
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
            var extraIcon = extra + ".icon.png";
            if (File.Exists(extraIcon))
            {
                try { File.Move(extraIcon, extra + ".disabled.icon.png", overwrite: true); } catch { }
            }
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
