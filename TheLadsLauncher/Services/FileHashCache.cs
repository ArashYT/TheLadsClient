using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace TheLadsLauncher.Services;

/// <summary>
/// File hashes kept on disk per game folder, keyed by full path, size and last write time (the same test the in-memory
/// caches use). A fresh launcher otherwise re-reads every mod jar on each Play: about 1.7 s of the 2 s launch prep on
/// arash-legion with the 26.3 pack. A changed jar gets a new size or write time, so its hash is computed again.
/// </summary>
internal static class FileHashCache
{
    public const string FileName = ".lads-hash-cache.json";
    private static readonly object Gate = new();
    private static readonly Dictionary<string, Dictionary<string, Entry>> Folders = new(StringComparer.OrdinalIgnoreCase);
    private static readonly HashSet<string> Dirty = new(StringComparer.OrdinalIgnoreCase);

    private sealed record Entry(long Size, long Ticks, string Hash);

    /// <summary>The stored hash of <paramref name="file"/> when its size and write time still match, else null.</summary>
    public static string? Get(string game, string algorithm, FileInfo file)
    {
        lock (Gate)
            return Folder(game).TryGetValue(Key(algorithm, file), out var e) && e.Size == file.Length && e.Ticks == file.LastWriteTimeUtc.Ticks ? e.Hash : null;
    }

    public static void Put(string game, string algorithm, FileInfo file, string hash)
    {
        lock (Gate)
        {
            Folder(game)[Key(algorithm, file)] = new(file.Length, file.LastWriteTimeUtc.Ticks, hash);
            Dirty.Add(Path.GetFullPath(game));
        }
    }

    /// <summary>Writes the folder's cache when it changed, dropping files that are gone. A failed write only costs the next launch a re-hash.</summary>
    public static void Save(string game)
    {
        lock (Gate)
        {
            var root = Path.GetFullPath(game);
            if (!Dirty.Remove(root)) return;
            var folder = Folder(game);
            foreach (var gone in folder.Keys.Where(k => !File.Exists(k[(k.IndexOf('|') + 1)..])).ToList()) folder.Remove(gone);
            try
            {
                Directory.CreateDirectory(root);
                var path = Path.Combine(root, FileName);
                var temp = path + ".tmp";
                File.WriteAllText(temp, JsonSerializer.Serialize(folder));
                File.Move(temp, path, overwrite: true);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        }
    }

    private static string Key(string algorithm, FileInfo file) => algorithm + "|" + Path.GetFullPath(file.FullName);

    private static Dictionary<string, Entry> Folder(string game)
    {
        var root = Path.GetFullPath(game);
        if (Folders.TryGetValue(root, out var folder)) return folder;
        folder = new(StringComparer.OrdinalIgnoreCase);
        try
        {
            var path = Path.Combine(root, FileName);
            if (File.Exists(path) && JsonSerializer.Deserialize<Dictionary<string, Entry>>(File.ReadAllText(path)) is { } stored)
                foreach (var (k, v) in stored) if (v?.Hash is { Length: > 0 }) folder[k] = v;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { }
        Folders[root] = folder;
        return folder;
    }
}
