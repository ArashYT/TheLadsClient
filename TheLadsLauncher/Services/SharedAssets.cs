using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using CmlLib.Core;
using CmlLib.Core.Files;

namespace TheLadsLauncher.Services;

/// <summary>
/// Minecraft's assets (sounds, languages, textures) are files named by their SHA-1, so the same file is identical in every game
/// folder. Before a version's files are installed, the assets another folder on this PC already holds (a Lads profile, the modpack
/// cache, the official launcher's .minecraft) are hard-linked into the folder being installed instead of downloaded again: no second
/// download and, on one drive, no second copy on disk. The other folders are only read and every file is hash-checked before it is
/// taken. Nothing here is fatal: the normal install downloads whatever is still missing.
/// </summary>
public static class SharedAssets
{
    public sealed record Result(int Linked, int Copied, int Missing)
    {
        public int Reused => Linked + Copied;
    }

    /// <summary>Every assets folder this PC may already have: the modpack cache, each Lads profile, the official launcher.</summary>
    public static List<string> Stores(string dataDirectory, string? officialMinecraft = null)
    {
        var stores = new List<string> { Path.Combine(ModpackLauncher.CacheRoot(dataDirectory), "assets") };
        try
        {
            string profiles = Path.Combine(dataDirectory, "profiles");
            if (Directory.Exists(profiles))
                stores.AddRange(Directory.GetDirectories(profiles).Select(profile => Path.Combine(profile, "assets")));
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        try
        {
            stores.Add(Path.Combine(officialMinecraft ?? SharedContentService.ResolveDefaultRoot(), "assets")); // .minecraft, or a sandbox's stand-in
        }
        catch (InvalidOperationException) { } // an unusable LADS_GLOBAL_MINECRAFT_DIR
        return stores.Where(store => Directory.Exists(Path.Combine(store, "objects"))).ToList();
    }

    /// <summary>Brings the assets of <paramref name="versionId"/> that this PC already has into the launcher's assets folder.
    /// Null when there was nothing to look at (no other folder, no asset index).</summary>
    public static async Task<Result?> SeedAsync(MinecraftLauncher launcher, string versionId, string dataDirectory, HttpClient http,
        Action<string>? status, CancellationToken token)
    {
        try
        {
            string assets = Path.GetFullPath(launcher.MinecraftPath.Assets);
            var stores = Stores(dataDirectory).Where(store => !SamePath(store, assets)).ToList();
            if (stores.Count == 0) return null;

            AssetMetadata? index = null;
            for (var version = await VersionAsync(launcher, versionId, token); version != null && index == null; version = version.ParentVersion)
                index = version.AssetIndex;
            if (index?.Id is not { Length: > 0 } id || id.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0) return null;

            string indexFile = Path.Combine(assets, "indexes", id + ".json");
            if (!File.Exists(indexFile)) await FetchIndexAsync(indexFile, id, index, stores, http, token);
            if (!File.Exists(indexFile)) return null;

            var result = await Task.Run(() => Seed(assets, indexFile, stores, token), token);
            if (result.Reused > 0) status?.Invoke($"Reused {result.Reused} game files already on this PC.");
            return result;
        }
        catch (Exception) when (!token.IsCancellationRequested)
        {
            return null; // the install downloads everything, as before
        }
    }

    private static async Task<CmlLib.Core.Version.IVersion?> VersionAsync(MinecraftLauncher launcher, string versionId, CancellationToken token)
    {
        try
        {
            return await launcher.GetVersionAsync(versionId, token);
        }
        catch (Exception) when (!token.IsCancellationRequested)
        {
            await launcher.GetAllVersionsAsync(token); // a loader's manifest written a moment ago
            return await launcher.GetVersionAsync(versionId, token);
        }
    }

    /// <summary>The index from another folder (hash-checked against the version's), else Mojang's.</summary>
    private static async Task FetchIndexAsync(string indexFile, string id, AssetMetadata index, List<string> stores, HttpClient http, CancellationToken token)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(indexFile)!);
        string part = indexFile + ".part";
        string? sha1 = index.GetSha1();
        if (IsSha1(sha1))
        {
            foreach (var store in stores)
            {
                string source = Path.Combine(store, "indexes", id + ".json");
                if (!File.Exists(source) || !HashIs(source, sha1!)) continue;
                File.Copy(source, part, true);
                File.Move(part, indexFile, true);
                return;
            }
        }
        if (!Uri.TryCreate(index.Url, UriKind.Absolute, out var url) || url.Scheme != Uri.UriSchemeHttps) return;
        await File.WriteAllBytesAsync(part, await http.GetByteArrayAsync(url, token), token);
        if (IsSha1(sha1) && !HashIs(part, sha1!))
        {
            File.Delete(part);
            return;
        }
        File.Move(part, indexFile, true);
    }

    /// <summary>Links (or, across drives, copies) every object the index names that <paramref name="assets"/> lacks and one of
    /// <paramref name="stores"/> holds with the right size and hash.</summary>
    internal static Result Seed(string assets, string indexFile, IReadOnlyList<string> stores, CancellationToken token)
    {
        var wanted = new Dictionary<string, long>(StringComparer.Ordinal);
        using (var document = JsonDocument.Parse(File.ReadAllBytes(indexFile)))
        {
            if (!document.RootElement.TryGetProperty("objects", out var objects) || objects.ValueKind != JsonValueKind.Object) return new Result(0, 0, 0);
            foreach (var entry in objects.EnumerateObject())
            {
                if (entry.Value.ValueKind != JsonValueKind.Object || !entry.Value.TryGetProperty("hash", out var hash)
                    || hash.ValueKind != JsonValueKind.String || !IsSha1(hash.GetString())) continue;
                long size = entry.Value.TryGetProperty("size", out var length) && length.TryGetInt64(out var bytes) ? bytes : -1;
                wanted[hash.GetString()!.ToLowerInvariant()] = size;
            }
        }

        int linked = 0, copied = 0, missing = 0;
        Parallel.ForEach(wanted, new ParallelOptions { MaxDegreeOfParallelism = 4, CancellationToken = token }, item =>
        {
            string relative = Path.Combine("objects", item.Key[..2], item.Key);
            string target = Path.Combine(assets, relative);
            if (File.Exists(target)) return; // the install checks what is already here
            foreach (var store in stores)
            {
                try
                {
                    string source = Path.Combine(store, relative);
                    var info = new FileInfo(source);
                    if (!info.Exists || (item.Value >= 0 && info.Length != item.Value) || !HashIs(source, item.Key)) continue;
                    Directory.CreateDirectory(Path.GetDirectoryName(target)!);
                    if (CreateHardLink(target, source, IntPtr.Zero))
                    {
                        Interlocked.Increment(ref linked);
                        return;
                    }
                    File.Copy(source, target + ".part", true); // another drive, or a file system without links
                    File.Move(target + ".part", target, true);
                    Interlocked.Increment(ref copied);
                    return;
                }
                catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
            }
            Interlocked.Increment(ref missing);
        });
        return new Result(linked, copied, missing);
    }

    private static bool IsSha1(string? text) => text is { Length: 40 } && text.All(Uri.IsHexDigit);

    private static bool HashIs(string file, string sha1)
    {
        using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete, 81920, FileOptions.SequentialScan);
        return string.Equals(Convert.ToHexString(SHA1.HashData(stream)), sha1, StringComparison.OrdinalIgnoreCase);
    }

    private static bool SamePath(string a, string b) => string.Equals(Path.GetFullPath(a).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar),
        Path.GetFullPath(b).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar), StringComparison.OrdinalIgnoreCase);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CreateHardLink(string lpFileName, string lpExistingFileName, IntPtr lpSecurityAttributes);
}
