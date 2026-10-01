using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public static class BundledModInstaller
{
    public const string CoreModId = "theladscore";

    /// <summary>Installs only the exact version's core jar; returns whether any files changed.</summary>
    public static async Task<bool> InstallAsync(string bundleRoot, string gameDir, string minecraftVersion,
        CancellationToken cancellationToken = default)
    {
        GameVersionPolicy.ValidateMinecraftVersion(minecraftVersion);
        cancellationToken.ThrowIfCancellationRequested();
        // 1.8.9's Core is a Forge mod (mcmod.info); every other version's is the Fabric one. Neither is ever used for the other.
        var forge = GameVersionPolicy.UsesForge(minecraftVersion);
        var bundle = Path.GetFullPath(bundleRoot);
        var game = Path.GetFullPath(gameDir);
        var source = SafeChild(bundle, Path.Combine(bundle, "game-mods", minecraftVersion, "theladscore.jar"));
        var mods = SafeChild(game, Path.Combine(game, "mods"));
        var destination = SafeChild(game, Path.Combine(mods, "theladscore.jar"));
        // Only an explicit choice disables Core: a v1.2.2 leftover .disabled copy next to a reinstalled jar is ambiguous.
        var preferencesPath = SafeChild(game, Path.Combine(game, ModPreferences.FileName));
        if (ModPreferences.Parse(ModPreferences.ReadShared(preferencesPath), preferencesPath).GetEnabled(CoreModId, null) == false)
            return Disable(game, mods, destination, forge, cancellationToken);
        if (!File.Exists(source))
        {
            if (GameVersionPolicy.RequiresBundledCore(minecraftVersion))
                throw new FileNotFoundException($"The Lads Core bundle for Minecraft {minecraftVersion} is missing. " +
                    $"Build and deploy the {minecraftVersion} core module so '{source}' exists, then launch again.", source);
            return false;
        }

        // Keep the validated source open against writes while hashing/copying it.
        await using var input = new FileStream(source, FileMode.Open, FileAccess.Read, FileShare.Read,
            81920, FileOptions.Asynchronous | FileOptions.SequentialScan);
        if (forge)
        {
            var core = FabricModMetadata.ReadForgeJar(input);
            if (core?.Id != CoreModId)
                throw new InvalidDataException(OtherLoadersCore(source, forge, minecraftVersion) is { } wrong ? $"Bundled jar {wrong}. Rebuild/deploy the {minecraftVersion} core."
                    : $"Bundled jar '{source}' must declare Forge mod id '{CoreModId}' in mcmod.info. Rebuild/deploy the correct core.");
            if (core.McVersion != minecraftVersion)
                throw new InvalidDataException($"Bundled core '{source}' must declare mcversion '{minecraftVersion}' in mcmod.info. Rebuild/deploy this version's core.");
        }
        else using (var metadata = ReadMetadata(input))
        {
            var root = metadata?.RootElement;
            if (root == null || GetModId(root.Value) != CoreModId)
                throw new InvalidDataException(root == null && OtherLoadersCore(source, forge, minecraftVersion) is { } wrong
                    ? $"Bundled jar {wrong}. Rebuild/deploy the {minecraftVersion} core."
                    : $"Bundled jar '{source}' must declare Fabric mod id '{CoreModId}'. Rebuild/deploy the correct core.");
            if (!root.Value.TryGetProperty("depends", out var depends) || depends.ValueKind != JsonValueKind.Object
                || !depends.TryGetProperty("minecraft", out var mc) || !MatchesExactVersion(mc, minecraftVersion))
                throw new InvalidDataException($"Bundled core '{source}' must declare the exact Minecraft dependency '{minecraftVersion}'. Rebuild/deploy this version's core.");
        }
        input.Position = 0;
        var sourceHash = await SHA256.HashDataAsync(input, cancellationToken);

        var legacyCores = new List<string>();
        var destinationIsCore = false;
        if (Directory.Exists(mods))
        {
            // Disabled Core copies count too, so enabling never leaves a second (disabled) Core behind.
            foreach (var jar in Directory.EnumerateFiles(mods).Where(IsJar))
            {
                cancellationToken.ThrowIfCancellationRequested();
                var safeJar = SafeChild(game, jar);
                var isCore = IsCore(safeJar, forge);

                if (SamePath(safeJar, destination))
                {
                    if (!isCore)
                        throw new IOException($"{OtherLoadersCore(destination, forge, minecraftVersion) ?? $"'{destination}' is not a verified {CoreModId} jar"}. Move it aside manually; other mods will not be overwritten.");
                    destinationIsCore = true;
                }
                else if (isCore)
                {
                    legacyCores.Add(safeJar);
                }
            }
        }

        var contentChanged = true;
        if (destinationIsCore)
        {
            await using var current = new FileStream(destination, FileMode.Open, FileAccess.Read, FileShare.Read,
                81920, FileOptions.Asynchronous | FileOptions.SequentialScan);
            var currentHash = await SHA256.HashDataAsync(current, cancellationToken);
            contentChanged = !sourceHash.AsSpan().SequenceEqual(currentHash);
        }
        if (!contentChanged && legacyCores.Count == 0)
            return false;
        if (contentChanged && destinationIsCore)
            legacyCores.Add(destination);

        Directory.CreateDirectory(mods);
        string? staged = null;
        var moved = new List<(string Original, string Backup)>();
        try
        {
            if (contentChanged)
            {
                staged = SafeChild(game, Path.Combine(mods, $".thelads-install-{Guid.NewGuid():N}.tmp"));
                input.Position = 0;
                await using (var output = new FileStream(staged, FileMode.CreateNew, FileAccess.Write, FileShare.None,
                    81920, FileOptions.Asynchronous))
                {
                    await input.CopyToAsync(output, cancellationToken);
                    await output.FlushAsync(cancellationToken);
                }
            }
            cancellationToken.ThrowIfCancellationRequested();

            // Once the commit starts, complete or roll back it before observing cancellation.
            var disabled = SafeChild(game, Path.Combine(game, "mods-disabled",
                $"{DateTime.UtcNow:yyyyMMddTHHmmssfffZ}-{Guid.NewGuid():N}"));
            if (legacyCores.Count > 0)
                Directory.CreateDirectory(disabled);
            foreach (var legacy in legacyCores)
            {
                var original = SafeChild(game, legacy);
                var backup = SafeChild(game, Path.Combine(disabled, Path.GetFileName(legacy)));
                File.Move(original, backup);
                moved.Add((original, backup));
            }
            if (staged != null)
            {
                File.Move(SafeChild(game, staged), SafeChild(game, destination));
                staged = null;
            }
            return true;
        }
        catch
        {
            for (var i = moved.Count - 1; i >= 0; i--)
            {
                var (original, backup) = moved[i];
                File.Move(SafeChild(game, backup), SafeChild(game, original));
            }
            throw;
        }
        finally
        {
            // Only this invocation's unfinished staging file is deleted, never an existing mod.
            if (staged != null && File.Exists(staged))
                File.Delete(SafeChild(game, staged));
        }
    }

    /// <summary>Explicitly disabled: keep exactly one disabled Core and install nothing. The bundled jar is not needed.</summary>
    private static bool Disable(string game, string mods, string destination, bool forge, CancellationToken cancellationToken)
    {
        if (!Directory.Exists(mods)) return false;
        var enabled = Directory.EnumerateFiles(mods).Where(p => p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase))
            .Select(p => SafeChild(game, p)).Where(p => IsCore(p, forge)).OrderBy(p => SamePath(p, destination) ? 0 : 1).ToList();
        if (enabled.Count == 0) return false;
        cancellationToken.ThrowIfCancellationRequested();
        var disabledPath = SafeChild(game, destination + ".disabled");
        // An existing theladscore.jar.disabled (for example next to a copy v1.2.2 reinstalled) is kept as the disabled copy.
        var rename = File.Exists(disabledPath) || Directory.Exists(disabledPath) ? null : enabled[0];
        var moved = new List<(string Original, string Backup)>();
        try
        {
            var backups = enabled.Where(p => p != rename).ToList();
            if (backups.Count > 0)
            {
                var folder = SafeChild(game, Path.Combine(game, "mods-disabled", $"{DateTime.UtcNow:yyyyMMddTHHmmssfffZ}-{Guid.NewGuid():N}"));
                Directory.CreateDirectory(folder);
                foreach (var core in backups)
                {
                    var backup = SafeChild(game, Path.Combine(folder, Path.GetFileName(core)));
                    File.Move(core, backup);
                    moved.Add((core, backup));
                }
            }
            if (rename != null) File.Move(rename, disabledPath);
            return true;
        }
        catch
        {
            for (var i = moved.Count - 1; i >= 0; i--)
                File.Move(SafeChild(game, moved[i].Backup), SafeChild(game, moved[i].Original));
            throw;
        }
    }

    private static bool IsJar(string path) =>
        path.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || path.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase);

    /// <summary>Core is identified by its id for the profile's loader: fabric.mod.json, or mcmod.info on Forge (1.8.9). Unreadable
    /// jars, and the other loader's Core, are simply not Core.</summary>
    private static bool IsCore(string path, bool forge)
    {
        try
        {
            using var stream = File.OpenRead(path);
            if (forge) return FabricModMetadata.ReadForgeJar(stream)?.Id == CoreModId;
            using var metadata = ReadMetadata(stream);
            return metadata != null && GetModId(metadata.RootElement) == CoreModId;
        }
        catch (InvalidDataException) { return false; }
        catch (JsonException) { return false; }
    }

    /// <summary>"'path' is the Fabric Lads Core, but Minecraft 1.8.9 runs on Forge" (or the reverse), else null.</summary>
    private static string? OtherLoadersCore(string path, bool forge, string minecraftVersion) => IsCore(path, !forge)
        ? $"'{path}' is the {(forge ? "Fabric" : "Forge")} Lads Core, but Minecraft {minecraftVersion} runs on {(forge ? "Forge" : "Fabric")}"
        : null;

    private static JsonDocument? ReadMetadata(Stream stream)
    {
        using var zip = new ZipArchive(stream, ZipArchiveMode.Read, leaveOpen: true);
        var entries = zip.Entries.Where(e => e.FullName == "fabric.mod.json").ToList();
        if (entries.Count == 0) return null;
        if (entries.Count != 1 || entries[0].Length > 1024 * 1024)
            throw new InvalidDataException("The jar has ambiguous or oversized Fabric metadata.");
        using var metadata = entries[0].Open();
        return JsonDocument.Parse(metadata);
    }

    private static string? GetModId(JsonElement root) =>
        root.ValueKind == JsonValueKind.Object && root.TryGetProperty("id", out var id)
            && id.ValueKind == JsonValueKind.String ? id.GetString() : null;

    private static bool MatchesExactVersion(JsonElement value, string version)
    {
        if (value.ValueKind == JsonValueKind.String)
            return value.GetString() == version || value.GetString() == "=" + version;
        return value.ValueKind == JsonValueKind.Array && value.GetArrayLength() > 0
            && value.EnumerateArray().All(item => MatchesExactVersion(item, version));
    }

    private static bool SamePath(string left, string right) =>
        string.Equals(left, right, OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal);

    private static string SafeChild(string root, string candidate)
    {
        var fullPath = Path.GetFullPath(candidate);
        var relative = Path.GetRelativePath(root, fullPath);
        if (Path.IsPathRooted(relative) || relative == ".." || relative.StartsWith(".." + Path.DirectorySeparatorChar, StringComparison.Ordinal))
            throw new IOException($"Mod path '{fullPath}' escapes '{root}'.");

        // Lexical containment alone is insufficient when any parent is a junction/symlink.
        for (string? current = fullPath; current != null; current = Path.GetDirectoryName(current))
        {
            try
            {
                if ((File.GetAttributes(current) & FileAttributes.ReparsePoint) != 0)
                    throw new IOException($"Mod installation cannot traverse the link '{current}'. Use a physical bundle/game directory.");
            }
            catch (FileNotFoundException) { }
            catch (DirectoryNotFoundException) { }
        }
        return fullPath;
    }
}
