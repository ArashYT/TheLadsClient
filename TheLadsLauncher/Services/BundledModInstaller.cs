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
        var bundle = Path.GetFullPath(bundleRoot);
        var game = Path.GetFullPath(gameDir);
        var source = SafeChild(bundle, Path.Combine(bundle, "game-mods", minecraftVersion, "theladscore.jar"));
        var mods = SafeChild(game, Path.Combine(game, "mods"));
        var destination = SafeChild(game, Path.Combine(mods, "theladscore.jar"));
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
        using (var metadata = ReadMetadata(input))
        {
            var root = metadata?.RootElement;
            if (root == null || GetModId(root.Value) != CoreModId)
                throw new InvalidDataException($"Bundled jar '{source}' must declare Fabric mod id '{CoreModId}'. Rebuild/deploy the correct core.");
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
            foreach (var jar in Directory.EnumerateFiles(mods).Where(p => p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)))
            {
                cancellationToken.ThrowIfCancellationRequested();
                var safeJar = SafeChild(game, jar);
                bool isCore;
                try
                {
                    using var stream = File.OpenRead(safeJar);
                    using var metadata = ReadMetadata(stream);
                    isCore = metadata != null && GetModId(metadata.RootElement) == CoreModId;
                }
                catch (InvalidDataException) { isCore = false; }
                catch (JsonException) { isCore = false; }

                if (SamePath(safeJar, destination))
                {
                    if (!isCore)
                        throw new IOException($"'{destination}' is not a verified {CoreModId} jar. Move it aside manually; other mods will not be overwritten.");
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
