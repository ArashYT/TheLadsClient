using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public sealed record MrpackFile(string Path, string? Sha1, string? Sha512, IReadOnlyList<string> Downloads, long? Size, bool ClientUnsupported);

public sealed record MrpackIndex(string Name, string VersionId, string? Summary, string MinecraftVersion, string Loader, string LoaderVersion,
    IReadOnlyList<MrpackFile> Files);

/// <summary>
/// Modrinth's .mrpack format (https://support.modrinth.com/en/articles/8802351): modrinth.index.json lists files to download
/// (each with sha1/sha512) and the game/loader versions; overrides/ then client-overrides/ are copied over the game folder.
/// </summary>
public static class Mrpack
{
    public const string RecordFile = ".lads-pack-files.json";
    private static readonly (string Key, string Loader)[] LoaderKeys =
        { ("fabric-loader", "fabric"), ("quilt-loader", "quilt"), ("forge", "forge"), ("neoforge", "neoforge") };

    public static MrpackIndex ParseIndex(Stream json)
    {
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;
        if (root.TryGetProperty("formatVersion", out var format) && format.GetInt32() != 1)
            throw new InvalidDataException($"Unsupported .mrpack format version {format.GetInt32()}.");
        if (Str(root, "game") != "minecraft") throw new InvalidDataException("This .mrpack is not a Minecraft modpack.");
        var deps = root.GetProperty("dependencies");
        var minecraft = Str(deps, "minecraft") ?? throw new InvalidDataException("The .mrpack does not name its Minecraft version.");
        GameVersionPolicy.ValidateMinecraftVersion(minecraft);
        var loaders = LoaderKeys.Where(l => Str(deps, l.Key) != null).ToList();
        if (loaders.Count > 1) throw new InvalidDataException("The .mrpack names more than one mod loader.");
        var files = new List<MrpackFile>();
        foreach (var f in root.TryGetProperty("files", out var list) ? list.EnumerateArray() : Enumerable.Empty<JsonElement>())
        {
            f.TryGetProperty("hashes", out var hashes);
            bool unsupported = f.TryGetProperty("env", out var env) && Str(env, "client") == "unsupported";
            files.Add(new MrpackFile(Str(f, "path") ?? throw new InvalidDataException("A file in the .mrpack has no path."),
                hashes.ValueKind == JsonValueKind.Object ? Str(hashes, "sha1") : null,
                hashes.ValueKind == JsonValueKind.Object ? Str(hashes, "sha512") : null,
                f.TryGetProperty("downloads", out var urls) ? urls.EnumerateArray().Select(u => u.GetString() ?? "").ToList() : new List<string>(),
                f.TryGetProperty("fileSize", out var size) ? size.GetInt64() : null, unsupported));
        }
        return new MrpackIndex(Str(root, "name") ?? "Modpack", Str(root, "versionId") ?? "", Str(root, "summary"), minecraft,
            loaders.Count == 0 ? "vanilla" : loaders[0].Loader, loaders.Count == 0 ? "" : Str(deps, loaders[0].Key)!, files);
    }

    public static MrpackIndex ReadIndex(string mrpackFile)
    {
        using var zip = ZipFile.OpenRead(mrpackFile);
        using var stream = (zip.GetEntry("modrinth.index.json") ?? throw new InvalidDataException("Not a .mrpack: modrinth.index.json is missing.")).Open();
        return ParseIndex(stream);
    }

    /// <summary>
    /// The full path of <paramref name="relative"/> inside <paramref name="root"/>. Refuses absolute paths, drive letters and
    /// other ':' uses (alternate data streams), '.' and '..' segments, and segments ending in a dot or space, which Windows
    /// trims (".. " would become "..").
    /// </summary>
    public static string SafePath(string root, string relative)
    {
        if (string.IsNullOrWhiteSpace(relative) || relative.Contains(':') || relative.Contains('\0')
            || relative[0] is '/' or '\\' || Path.IsPathRooted(relative)
            || relative.Split('/', '\\').Any(segment => segment.EndsWith('.') || segment.EndsWith(' ')))
            throw new InvalidDataException($"Unsafe path '{relative}' in the modpack.");
        var full = Path.GetFullPath(Path.Combine(root, relative));
        if (!SafeFileOps.IsSameOrInside(full, root) || SafeFileOps.PathsEqual(full, root))
            throw new InvalidDataException($"Unsafe path '{relative}' in the modpack.");
        return full;
    }

    /// <summary>Every hash the pack gives must match, and it must give at least one.</summary>
    public static bool HashMatches(string file, string? sha1, string? sha512)
    {
        if (sha1 == null && sha512 == null) return false;
        using var stream = File.OpenRead(file);
        if (sha512 != null && !Convert.ToHexString(SHA512.HashData(stream)).Equals(sha512, StringComparison.OrdinalIgnoreCase)) return false;
        stream.Position = 0;
        return sha1 == null || Convert.ToHexString(SHA1.HashData(stream)).Equals(sha1, StringComparison.OrdinalIgnoreCase);
    }

    private static string Sha1Of(string file)
    {
        using var stream = File.OpenRead(file);
        return Convert.ToHexString(SHA1.HashData(stream)).ToLowerInvariant();
    }

    /// <summary>
    /// Installs or updates the pack into &lt;instance&gt;/minecraft. A file the previous version installed and the user has
    /// since changed is never overwritten or removed; files the pack never installed (saves, options, own mods) are not touched,
    /// even when the new version brings a file of the same name. A mod the user switched off (".jar.disabled") stays off.
    /// The record of installed files is written last; a run that fails also records the files it wrote.
    /// </summary>
    public static async Task<MrpackIndex> InstallAsync(string mrpackFile, string instanceDirectory, HttpClient http,
        Action<string>? status, CancellationToken token)
    {
        var game = Path.Combine(instanceDirectory, "minecraft");
        Directory.CreateDirectory(game);
        var recordPath = Path.Combine(instanceDirectory, RecordFile);
        bool updating = File.Exists(recordPath);
        var previous = updating
            ? JsonSerializer.Deserialize<Dictionary<string, string>>(await File.ReadAllTextAsync(recordPath, token)) ?? new()
            : new Dictionary<string, string>();
        var installed = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

        using var zip = ZipFile.OpenRead(mrpackFile);
        MrpackIndex index;
        using (var stream = (zip.GetEntry("modrinth.index.json") ?? throw new InvalidDataException("Not a .mrpack: modrinth.index.json is missing.")).Open())
            index = ParseIndex(stream);

        // Every path is checked before anything is written.
        var files = index.Files.Where(f => !f.ClientUnsupported).Select(f => (File: f, Target: SafePath(game, f.Path))).ToList();
        if (files.DistinctBy(f => f.Target, StringComparer.OrdinalIgnoreCase).Count() != files.Count)
            throw new InvalidDataException("The modpack lists the same file twice.");
        var overrides = zip.Entries.Where(e => !e.FullName.EndsWith('/'))
            .Select(e => (Entry: e, Name: e.FullName.Replace('\\', '/')))
            .Select(e => (e.Entry, Order: e.Name.StartsWith("overrides/") ? 0 : e.Name.StartsWith("client-overrides/") ? 1 : -1, e.Name))
            .Where(e => e.Order >= 0).OrderBy(e => e.Order)
            .Select(e => (e.Entry, Relative: e.Name[(e.Name.IndexOf('/') + 1)..])).ToList();
        foreach (var o in overrides) SafePath(game, o.Relative);

        // A kept file keeps its recorded hash, so it still counts as the user's at the next update; one the pack never installed stays unrecorded.
        void Record(string key, string target, bool kept)
        {
            string? sha1 = !kept ? Sha1Of(target) : previous.TryGetValue(key, out var old) ? old : null;
            if (sha1 != null) lock (installed) installed[key] = sha1;
        }

        try
        {
            int done = 0;
            await Parallel.ForEachAsync(files, new ParallelOptions { MaxDegreeOfParallelism = 6, CancellationToken = token }, async (item, ct) =>
            {
                var key = Key(item.File.Path);
                var target = OnDisk(item.Target);
                bool kept = false;
                if (!(File.Exists(target) && HashMatches(target, item.File.Sha1, item.File.Sha512)) && !(kept = UserChanged(target, key, previous, updating)))
                    await DownloadAsync(http, item.File, target, ct);
                Record(key, target, kept);
                status?.Invoke($"Downloading files {Interlocked.Increment(ref done)}/{files.Count}...");
            });

            status?.Invoke("Copying pack settings...");
            foreach (var (entry, relative) in overrides)
            {
                token.ThrowIfCancellationRequested();
                var key = Key(relative);
                var target = OnDisk(SafePath(game, relative));
                // A file this run already wrote (overrides/ before client-overrides/) is the pack's, not the user's.
                bool kept = UserChanged(target, key, previous, updating && !installed.ContainsKey(key));
                if (!kept)
                {
                    Directory.CreateDirectory(Path.GetDirectoryName(target)!);
                    entry.ExtractToFile(target, overwrite: true);
                }
                Record(key, target, kept);
            }

            // Files of the previous version that this one no longer has, unless the user changed them.
            foreach (var (key, sha1) in previous)
            {
                if (installed.ContainsKey(key)) continue;
                var target = OnDisk(SafePath(game, key));
                if (File.Exists(target) && Sha1Of(target) == sha1) File.Delete(target);
            }
        }
        catch
        {
            // The disk now holds old and new files: record both, so the next update replaces or removes them instead of taking them for the user's.
            var merged = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            foreach (var (key, sha1) in previous.Concat(installed)) merged[key] = sha1;
            try { File.WriteAllText(recordPath, JsonSerializer.Serialize(merged)); }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { } // the original failure is the one to report
            throw;
        }

        await File.WriteAllTextAsync(recordPath, JsonSerializer.Serialize(installed), token);
        return index;
    }

    private static string Key(string relative) => relative.Replace('\\', '/');

    // A mod the user switched off is kept (and updated) under its ".disabled" name.
    private static string OnDisk(string target) => !File.Exists(target) && File.Exists(target + ".disabled") ? target + ".disabled" : target;

    // The user's file: changed since the previous version installed it, or (unlisted) there although that version never installed it,
    // like the options.txt Minecraft wrote or a config a mod made on first launch.
    private static bool UserChanged(string target, string key, Dictionary<string, string> previous, bool unlisted) =>
        File.Exists(target) && (previous.TryGetValue(key, out var sha1) ? Sha1Of(target) != sha1 : unlisted);

    private static async Task DownloadAsync(HttpClient http, MrpackFile file, string target, CancellationToken token)
    {
        if (file.Sha1 == null && file.Sha512 == null) throw new InvalidDataException($"'{file.Path}' has no hash in the modpack.");
        Directory.CreateDirectory(Path.GetDirectoryName(target)!);
        var temp = target + ".download";
        Exception? last = null;
        foreach (var url in file.Downloads)
        {
            if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps) { last = new InvalidDataException($"'{url}' is not an https download."); continue; }
            try
            {
                using (var response = await http.GetAsync(uri, HttpCompletionOption.ResponseHeadersRead, token))
                {
                    response.EnsureSuccessStatusCode();
                    await using var input = await response.Content.ReadAsStreamAsync(token);
                    await using var output = new FileStream(temp, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);
                    await ClientModInstaller.CopyWithStallTimeoutAsync(input, output, file.Size ?? 1L << 31, TimeSpan.FromSeconds(60), file.Path, token);
                }
                if (!HashMatches(temp, file.Sha1, file.Sha512)) throw new InvalidDataException($"'{file.Path}' does not match the modpack's hash.");
                File.Move(temp, target, overwrite: true);
                return;
            }
            catch (Exception e) when (e is HttpRequestException or InvalidDataException or IOException or TimeoutException && !token.IsCancellationRequested)
            {
                last = e;
            }
            finally { if (File.Exists(temp)) File.Delete(temp); }
        }
        throw new InvalidDataException($"Could not download '{file.Path}': {last?.Message ?? "the modpack gives no download for it."}", last);
    }

    private static string? Str(JsonElement e, string name) =>
        e.ValueKind == JsonValueKind.Object && e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;
}
