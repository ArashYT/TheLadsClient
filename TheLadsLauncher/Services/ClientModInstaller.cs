using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>Installs a reviewed, pinned Fabric pack. Upstream jars are downloaded directly, never repackaged.</summary>
public static class ClientModInstaller
{
    public sealed record Entry(string ProjectId, string ProjectSlug, string Name, string ModId,
        string VersionId, string Version, string FileName, string Url, string Sha512, long Size,
        string License, string? SourceUrl, string ProjectUrl);
    public sealed record Manifest(string MinecraftVersion, List<Entry> Mods, bool ResolveThroughApi = false);
    private sealed record Mod(string Id, string Name, List<string> Provides, Dictionary<string, string> Depends);
    private sealed record Package(string? Id, List<Mod> Mods);
    private sealed record LocalJar(string Path, string Hash, bool Disabled, Package Package);
    private sealed record Change(string Destination, string? Staged, string? PreviousHash, string? NewHash, string BackupName, string Status);
    private sealed record Move(string From, string To, string Hash);
    private const long MaximumJarSize = 128 * 1024 * 1024;
    private static readonly JsonSerializerOptions Json = new() { PropertyNameCaseInsensitive = true, WriteIndented = true };
    private static readonly HttpClient Http = CreateClient();
    private static readonly SemaphoreSlim InstallLock = new(1, 1);
    private static readonly StringComparer Paths = OperatingSystem.IsWindows() ? StringComparer.OrdinalIgnoreCase : StringComparer.Ordinal;
    private static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromMinutes(3) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("TheLadsClient/1.1");
        return client;
    }

    public static async Task InstallAsync(string bundleRoot, string gameDir, string minecraftVersion,
        Action<string>? status = null, CancellationToken cancellationToken = default, HttpClient? httpClient = null)
    {
        GameVersionPolicy.ValidateMinecraftVersion(minecraftVersion);
        cancellationToken.ThrowIfCancellationRequested();
        var bundle = Path.GetFullPath(bundleRoot);
        var game = Path.GetFullPath(gameDir);
        var manifestPath = SafeChild(bundle, Path.Combine(bundle, "game-mods", minecraftVersion, "client-mods.json"));
        if (!File.Exists(manifestPath))
        {
            if (GameVersionPolicy.RequiresBundledCore(minecraftVersion))
                throw new FileNotFoundException($"Client mod manifest for {minecraftVersion} is missing. Reinstall the complete launcher folder.", manifestPath);
            return;
        }
        var manifest = JsonSerializer.Deserialize<Manifest>(await File.ReadAllTextAsync(manifestPath, cancellationToken), Json)
            ?? throw new InvalidDataException("Empty client mod manifest.");
        if (manifest.MinecraftVersion != minecraftVersion || manifest.Mods is null || manifest.Mods.Count > 256
            || manifest.Mods.Any(m => m is null)
            || manifest.Mods.Select(m => m.ModId).Distinct(StringComparer.Ordinal).Count() != manifest.Mods.Count)
            throw new InvalidDataException("Client mod manifest does not match this Minecraft version or contains duplicate mod IDs.");
        foreach (var entry in manifest.Mods) Validate(entry);

        await InstallLock.WaitAsync(cancellationToken);
        // Only unique files created by this invocation may be cleaned up.
        var staged = new Dictionary<string, string?>(Paths);
        try
        {
            var mods = SafeChild(game, Path.Combine(game, "mods"));
            var cache = SafeChild(game, Path.Combine(game, ".lads-mod-cache"));
            Directory.CreateDirectory(mods);
            Directory.CreateDirectory(cache);
            var receiptPath = SafeChild(game, Path.Combine(cache, "installed.json"));
            var receiptBytes = File.Exists(receiptPath) ? await File.ReadAllBytesAsync(receiptPath, cancellationToken) : null;
            var originalReceipt = receiptBytes is null ? new Dictionary<string, string>()
                : JsonSerializer.Deserialize<Dictionary<string, string>>(receiptBytes, Json)
                    ?? throw new InvalidDataException("Empty installed-mod receipt.");
            if (originalReceipt.Any(p => !ValidId(p.Key) || !ValidHash(p.Value)))
                throw new InvalidDataException("Invalid installed-mod receipt.");
            var receipt = new Dictionary<string, string>(originalReceipt, StringComparer.Ordinal);
            var receiptHash = receiptBytes is null ? null : Hash(receiptBytes);
            var inventory = await ReadInventory(game, mods, cancellationToken);
            var active = inventory.Where(j => !j.Disabled).ToList();
            var disabledIds = inventory.Where(j => j.Disabled && j.Package.Id != null)
                .Select(j => j.Package.Id!).ToHashSet(StringComparer.Ordinal);
            var changes = new List<Change>();
            var desired = manifest.Mods.Select(m => m.ModId).ToHashSet(StringComparer.Ordinal);

            // Plan retirements now, but do not move anything until every download and dependency verifies.
            foreach (var oldId in receipt.Keys.Where(id => !desired.Contains(id)).ToList())
            {
                var old = SafeChild(game, Path.Combine(mods, "lads-" + oldId + ".jar"));
                var existing = active.SingleOrDefault(j => Paths.Equals(j.Path, old));
                if (existing != null)
                {
                    if (!SameHash(existing.Hash, receipt[oldId]))
                        throw new IOException($"Retired mod '{old}' has been modified. Move it out of Mods before launching the updated pack; your file was preserved.");
                    changes.Add(new(old, null, existing.Hash, null, "retired-" + oldId + "-" + existing.Hash, $"Retired {oldId}."));
                }
                receipt.Remove(oldId);
            }

            var pending = new List<(Entry Entry, LocalJar? Previous)>();
            foreach (var entry in manifest.Mods)
            {
                cancellationToken.ThrowIfCancellationRequested();
                var destination = SafeChild(game, Path.Combine(mods, "lads-" + entry.ModId + ".jar"));
                var existing = active.Where(j => j.Package.Id == entry.ModId).ToList();
                if (disabledIds.Contains(entry.ModId))
                {
                    if (existing.Count > 0)
                        throw new IOException($"{entry.Name} has both enabled and disabled jars in '{mods}'. Keep one copy; your files were preserved.");
                    continue;
                }
                if (existing.Count > 1)
                    throw new IOException($"Duplicate {entry.Name} mods in '{mods}'. Keep one compatible version before launching.");
                var previous = existing.SingleOrDefault();
                if (previous != null)
                {
                    if (SameHash(previous.Hash, entry.Sha512))
                    {
                        // Repair legacy interrupted installs only at the reserved, hash-verified managed path.
                        if (Paths.Equals(previous.Path, destination)) receipt[entry.ModId] = entry.Sha512;
                        else receipt.Remove(entry.ModId);
                        continue;
                    }
                    if (!Paths.Equals(previous.Path, destination)
                        || !receipt.TryGetValue(entry.ModId, out var priorHash) || !SameHash(previous.Hash, priorHash))
                        throw new IOException($"Your existing {entry.Name} jar differs from the tested {minecraftVersion} pack. Move '{previous.Path}' out of Mods, then retry; your file has been preserved.");
                }
                else if (Exists(destination))
                    throw new IOException($"Cannot replace unrecognized file '{destination}'. Move it aside and retry.");
                pending.Add((entry, previous));
            }

            using (var slots = new SemaphoreSlim(4))
                await Task.WhenAll(pending.Select(async item =>
                {
                    await slots.WaitAsync(cancellationToken);
                    try { await Download(game, cache, item.Entry, httpClient ?? Http, status, cancellationToken,
                        manifest.ResolveThroughApi, minecraftVersion); }
                    finally { slots.Release(); }
                }));

            var resulting = active.Where(j => !changes.Any(c => Paths.Equals(c.Destination, j.Path))
                && !pending.Any(p => p.Previous != null && Paths.Equals(p.Previous.Path, j.Path)))
                .SelectMany(j => j.Package.Mods).ToList();
            foreach (var (entry, previous) in pending)
            {
                var destination = SafeChild(game, Path.Combine(mods, "lads-" + entry.ModId + ".jar"));
                var temp = SafeChild(game, destination + "." + Guid.NewGuid().ToString("N") + ".tmp");
                var cached = SafeChild(game, Path.Combine(cache, entry.Sha512 + ".jar"));
                await using (var input = new FileStream(cached, FileMode.Open, FileAccess.Read, FileShare.Read, 81920, true))
                await using (var output = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, true))
                {
                    staged.Add(temp, null);
                    await input.CopyToAsync(output, cancellationToken);
                    await output.FlushAsync(cancellationToken);
                }
                var actual = await HashAsync(game, temp, cancellationToken);
                staged[temp] = actual;
                if (!SameHash(actual, entry.Sha512))
                    throw new InvalidDataException($"Cached download changed for {entry.Name}. Retry the launch.");
                var package = ReadPackage(temp, cancellationToken);
                if (package.Id != entry.ModId) throw new InvalidDataException($"Wrong Fabric mod downloaded for {entry.Name}.");
                resulting.AddRange(package.Mods);
                receipt[entry.ModId] = entry.Sha512;
                changes.Add(new(destination, temp, previous?.Hash, actual, "previous-" + entry.ModId, $"Installed {entry.Name}."));
            }

            ValidateDependencies(resulting, inventory.Where(j => j.Disabled).SelectMany(j => j.Package.Mods), minecraftVersion);
            if (receipt.Count != originalReceipt.Count
                || receipt.Any(p => !originalReceipt.TryGetValue(p.Key, out var hash) || !SameHash(hash, p.Value)))
            {
                var temp = SafeChild(game, Path.Combine(cache, "receipt-" + Guid.NewGuid().ToString("N") + ".tmp"));
                var bytes = JsonSerializer.SerializeToUtf8Bytes(receipt, Json);
                await using (var output = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, true))
                {
                    staged.Add(temp, null);
                    await output.WriteAsync(bytes, cancellationToken);
                    await output.FlushAsync(cancellationToken);
                }
                staged[temp] = Hash(bytes);
                changes.Add(new(receiptPath, temp, receiptHash, staged[temp], "previous-receipt", "Saved client mod receipt."));
            }

            status?.Invoke("Preparing client mod changes...");
            // Detect changes made in the Mods page or by another process while downloads were in flight.
            var current = await ReadInventory(game, mods, cancellationToken);
            if (current.Count != inventory.Count || current.Any(j => !inventory.Any(old =>
                Paths.Equals(old.Path, j.Path) && SameHash(old.Hash, j.Hash))))
                throw new IOException("The Mods folder changed during installation. Retry the launch; your changes were preserved.");
            await Expect(game, receiptPath, receiptHash, cancellationToken);
            status?.Invoke("Applying client mod changes...");
            cancellationToken.ThrowIfCancellationRequested();

            // There is no portable atomic rename of multiple files. Finish or roll back this bounded
            // commit before observing cancellation; never leave a cancelled install with a stale receipt.
            await Commit(game, cache, changes, status);
            status?.Invoke($"Client mods ready for Minecraft {minecraftVersion}.");
        }
        finally
        {
            foreach (var temp in staged) Cleanup(game, temp.Key, temp.Value);
            InstallLock.Release();
        }
    }

    private static async Task Download(string game, string cache, Entry entry, HttpClient client,
        Action<string>? status, CancellationToken token, bool resolveThroughApi, string minecraftVersion)
    {
        var cached = SafeChild(game, Path.Combine(cache, entry.Sha512 + ".jar"));
        if (Exists(cached))
        {
            if (!SameHash(await HashAsync(game, cached, token), entry.Sha512))
                throw new InvalidDataException($"Cached file '{cached}' is damaged. Move it aside and retry; it was preserved.");
            if (ReadPackage(cached, token).Id != entry.ModId)
                throw new InvalidDataException($"Wrong Fabric mod cached for {entry.Name}.");
            return;
        }
        status?.Invoke($"Downloading {entry.Name} {entry.Version}...");
        if (resolveThroughApi)
            await ModrinthReleaseVerifier.VerifyAsync(client, entry, minecraftVersion, token);
        var temp = SafeChild(game, cached + "." + Guid.NewGuid().ToString("N") + ".tmp");
        var created = false;
        try
        {
            // ResponseHeadersRead does not apply HttpClient.Timeout to the body.
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
            timeout.CancelAfter(TimeSpan.FromMinutes(3));
            var downloadToken = timeout.Token;
            using var response = await client.GetAsync(entry.Url, HttpCompletionOption.ResponseHeadersRead, downloadToken);
            response.EnsureSuccessStatusCode();
            if (response.Content.Headers.ContentLength is long length && length != entry.Size)
                throw new InvalidDataException($"Unexpected download size for {entry.Name}.");
            await using (var input = await response.Content.ReadAsStreamAsync(downloadToken))
            await using (var output = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, true))
            {
                created = true;
                var buffer = new byte[81920]; long total = 0; int count;
                while ((count = await input.ReadAsync(buffer, downloadToken)) > 0)
                {
                    total += count;
                    if (total > entry.Size) throw new InvalidDataException($"Oversized download for {entry.Name}.");
                    await output.WriteAsync(buffer.AsMemory(0, count), downloadToken);
                }
                if (total != entry.Size) throw new InvalidDataException($"Incomplete download for {entry.Name}.");
            }
            if (!SameHash(await HashAsync(game, temp, downloadToken), entry.Sha512))
                throw new InvalidDataException($"Download verification failed for {entry.Name}. Retry the launch.");
            if (ReadPackage(temp, downloadToken).Id != entry.ModId)
                throw new InvalidDataException($"Wrong Fabric mod downloaded for {entry.Name}.");
            // Never overwrite even an unexpected cache file created during the download.
            File.Move(SafeChild(game, temp), SafeChild(game, cached));
            created = false;
        }
        finally { if (created) Cleanup(game, temp, null); }
    }

    private static async Task Commit(string game, string cache, List<Change> changes, Action<string>? status)
    {
        var moves = new List<Move>();
        try
        {
            foreach (var change in changes)
            {
                await Expect(game, change.Destination, change.PreviousHash, CancellationToken.None);
                if (change.Staged != null)
                    await Expect(game, change.Staged, change.NewHash, CancellationToken.None);
                if (change.PreviousHash != null)
                {
                    var extension = Path.GetExtension(change.Destination);
                    var backup = SafeChild(game, Path.Combine(cache, change.BackupName + "-" + Guid.NewGuid().ToString("N") + extension));
                    File.Move(SafeChild(game, change.Destination), backup);
                    moves.Add(new(change.Destination, backup, change.PreviousHash));
                }
                if (change.Staged != null)
                {
                    File.Move(SafeChild(game, change.Staged), SafeChild(game, change.Destination));
                    moves.Add(new(change.Staged, change.Destination, change.NewHash!));
                }
                status?.Invoke(change.Status);
            }
        }
        catch (Exception failure)
        {
            var errors = new List<Exception> { failure };
            for (var i = moves.Count - 1; i >= 0; i--)
            {
                var move = moves[i];
                try
                {
                    // Refuse to overwrite or discard content changed by somebody else during rollback.
                    await Expect(game, move.To, move.Hash, CancellationToken.None);
                    await Expect(game, move.From, null, CancellationToken.None);
                    File.Move(SafeChild(game, move.To), SafeChild(game, move.From));
                }
                catch (Exception rollbackFailure) { errors.Add(rollbackFailure); }
            }
            if (errors.Count > 1)
                throw new IOException($"Client mod rollback could not finish because files changed or became inaccessible. " +
                    $"Original files are preserved in '{cache}'. Close Minecraft and restore those backups before retrying.",
                    new AggregateException(errors));
            throw;
        }
    }

    private static void Validate(Entry entry)
    {
        if (!ValidId(entry.ModId) || !ValidHash(entry.Sha512)
            || entry.Size <= 0 || entry.Size > MaximumJarSize
            || !Uri.TryCreate(entry.Url, UriKind.Absolute, out var url) || url.Scheme != "https"
            || url.Host != "cdn.modrinth.com" || !string.IsNullOrEmpty(url.UserInfo))
            throw new InvalidDataException("Invalid client mod download metadata.");
    }

    private static async Task<List<LocalJar>> ReadInventory(string game, string mods, CancellationToken token)
    {
        var result = new List<LocalJar>();
        foreach (var file in Directory.EnumerateFiles(SafeChild(game, mods)).Where(p =>
            p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || p.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase)))
        {
            token.ThrowIfCancellationRequested();
            var path = SafeChild(game, file);
            var hash = await HashAsync(game, path, token);
            result.Add(new(path, hash, path.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase), ReadPackage(path, token)));
        }
        return result;
    }

    private static Package ReadPackage(string path, CancellationToken token)
    {
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
        var mods = new List<Mod>();
        long remainingBytes = MaximumJarSize;
        var remainingJars = 1024;
        using var zip = new ZipArchive(stream, ZipArchiveMode.Read);
        var id = ReadPackage(zip, mods, token, 0, ref remainingBytes, ref remainingJars);
        return new(id, mods);
    }

    private static string? ReadPackage(ZipArchive zip, List<Mod> mods, CancellationToken token,
        int depth, ref long remainingBytes, ref int remainingJars)
    {
        token.ThrowIfCancellationRequested();
        if (depth > 8 || --remainingJars < 0) throw new InvalidDataException("Fabric nested-jar limit exceeded.");
        var entries = zip.Entries.Where(e => e.FullName == "fabric.mod.json").ToList();
        if (entries.Count == 0) return null; // Ordinary libraries embedded by some Fabric mods.
        if (entries.Count != 1 || entries[0].Length > 1024 * 1024)
            throw new InvalidDataException("Ambiguous or oversized Fabric metadata.");
        using var stream = entries[0].Open();
        using var document = ReadFabricMetadata(stream);
        var root = document.RootElement;
        var id = Text(root, "id");
        if (!ValidId(id)) throw new InvalidDataException("Invalid Fabric mod ID.");
        var environment = Text(root, "environment");
        if (environment == "server") return id;
        var provides = new List<string>();
        if (root.TryGetProperty("provides", out var aliases))
            foreach (var alias in aliases.EnumerateArray())
            {
                var value = alias.GetString();
                if (!ValidId(value)) throw new InvalidDataException($"Invalid provided mod ID in {id}.");
                provides.Add(value!);
            }
        var depends = new Dictionary<string, string>(StringComparer.Ordinal);
        if (root.TryGetProperty("depends", out var dependencies))
            foreach (var dependency in dependencies.EnumerateObject())
            {
                if (!ValidId(dependency.Name)) throw new InvalidDataException($"Invalid dependency in {id}.");
                depends.Add(dependency.Name, dependency.Value.ValueKind == JsonValueKind.String
                    ? dependency.Value.GetString()! : dependency.Value.GetRawText());
            }
        mods.Add(new(id!, Text(root, "name") ?? id!, provides, depends));
        if (root.TryGetProperty("jars", out var nestedJars))
            foreach (var nested in nestedJars.EnumerateArray())
            {
                token.ThrowIfCancellationRequested();
                var name = Text(nested, "file") ?? throw new InvalidDataException($"Missing nested jar path in {id}.");
                // Entries stay in memory; their names are never used as filesystem paths.
                var matches = zip.Entries.Where(e => e.FullName == name).ToList();
                if (matches.Count != 1 || matches[0].Length > remainingBytes)
                    throw new InvalidDataException($"Missing, ambiguous or oversized nested jar '{name}' in {id}.");
                remainingBytes -= matches[0].Length;
                using var input = matches[0].Open();
                using var bytes = new MemoryStream();
                var buffer = new byte[81920];
                int count;
                while ((count = input.Read(buffer, 0, buffer.Length)) != 0)
                {
                    token.ThrowIfCancellationRequested();
                    if (bytes.Length + count > matches[0].Length)
                        throw new InvalidDataException($"Oversized nested jar '{name}' in {id}.");
                    bytes.Write(buffer, 0, count);
                }
                bytes.Position = 0;
                using var nestedZip = new ZipArchive(bytes, ZipArchiveMode.Read);
                ReadPackage(nestedZip, mods, token, depth + 1, ref remainingBytes, ref remainingJars);
            }
        return id;
    }

    private static void ValidateDependencies(IEnumerable<Mod> enabled, IEnumerable<Mod> disabled, string minecraftVersion)
    {
        var mods = enabled.ToList();
        var available = mods.SelectMany(m => m.Provides.Append(m.Id)).ToHashSet(StringComparer.Ordinal);
        // These are supplied by the selected launch runtime, not by jars in Mods.
        available.UnionWith(new[] { "minecraft", "java", "fabricloader" });
        // The modern Fabric loaders required by the supported core profiles bundle MixinExtras
        // inside fabric-loader.jar (0.19.3 ships META-INF/jars/mixinextras-fabric-0.5.4.jar).
        // It is intentionally absent from the pinned Mods directory.
        if (GameVersionPolicy.RequiresBundledCore(minecraftVersion)) available.Add("mixinextras");
        var disabledIds = disabled.SelectMany(m => m.Provides.Append(m.Id)).ToHashSet(StringComparer.Ordinal);
        var missing = mods.SelectMany(m => m.Depends.Where(d => !available.Contains(d.Key))
            .Select(d => $"{m.Name} ({m.Id}) requires {d.Key} {d.Value}, which is " +
                (disabledIds.Contains(d.Key) ? "disabled" : "missing"))).Distinct().ToList();
        if (missing.Count > 0)
            throw new InvalidDataException("Client mod dependencies are not satisfied: " + string.Join("; ", missing) +
                ". Enable/install the required dependency or disable its dependent mod in Mods, then retry. Your files were preserved.");
        // Fabric itself resolves version predicates, alternative nested versions and incompatibilities.
    }

    private static string? Text(JsonElement value, string name) =>
        value.ValueKind == JsonValueKind.Object && value.TryGetProperty(name, out var text)
            && text.ValueKind == JsonValueKind.String ? text.GetString() : null;

    // Fabric's Gson reader accepts literal line breaks in description strings. Normalize
    // only string control characters for System.Text.Json; downloaded jar bytes stay intact.
    private static JsonDocument ReadFabricMetadata(Stream stream)
    {
        using var reader = new StreamReader(stream, Encoding.UTF8, true, 4096, leaveOpen: true);
        var source = reader.ReadToEnd();
        var normalized = new StringBuilder(source.Length);
        bool quoted = false, escaped = false;
        foreach (char ch in source)
        {
            if (quoted && ch < 0x20)
                normalized.Append("\\u").Append(((int)ch).ToString("x4"));
            else normalized.Append(ch);
            if (!escaped && ch == '"') quoted = !quoted;
            escaped = quoted && !escaped && ch == '\\';
        }
        return JsonDocument.Parse(normalized.ToString());
    }
    private static bool ValidId(string? value) => Regex.IsMatch(value ?? "", "^[a-z][a-z0-9_-]{1,63}$");
    private static bool ValidHash(string? value) => Regex.IsMatch(value ?? "", "^[a-fA-F0-9]{128}$");
    private static bool SameHash(string left, string right) => left.Equals(right, StringComparison.OrdinalIgnoreCase);
    private static string Hash(byte[] bytes) => Convert.ToHexString(SHA512.HashData(bytes)).ToLowerInvariant();

    private static async Task<string> HashAsync(string game, string path, CancellationToken token)
    {
        await using var stream = new FileStream(SafeChild(game, path), FileMode.Open, FileAccess.Read, FileShare.Read, 81920, true);
        return Convert.ToHexString(await SHA512.HashDataAsync(stream, token)).ToLowerInvariant();
    }

    private static async Task Expect(string game, string path, string? expected, CancellationToken token)
    {
        path = SafeChild(game, path);
        if (expected is null ? Exists(path) : !File.Exists(path) || !SameHash(await HashAsync(game, path, token), expected))
            throw new IOException($"Mod installation path '{path}' changed or is occupied. Retry after checking Mods; existing content was preserved.");
    }

    private static bool Exists(string path)
    {
        try { File.GetAttributes(path); return true; }
        catch (FileNotFoundException) { return false; }
        catch (DirectoryNotFoundException) { return false; }
    }

    private static string SafeChild(string root, string path)
    {
        var full = Path.GetFullPath(path);
        var relative = Path.GetRelativePath(root, full);
        if (Path.IsPathRooted(relative) || relative == ".." || relative.StartsWith(".." + Path.DirectorySeparatorChar, StringComparison.Ordinal))
            throw new IOException($"Mod path '{full}' escapes '{root}'.");
        for (string? current = full; current != null; current = Path.GetDirectoryName(current))
        {
            try
            {
                if ((File.GetAttributes(current) & FileAttributes.ReparsePoint) != 0)
                    throw new IOException($"Managed mod path is a link: '{current}'. Use a regular profile directory.");
            }
            catch (FileNotFoundException) { }
            catch (DirectoryNotFoundException) { }
        }
        return full;
    }

    private static void Cleanup(string game, string path, string? expectedHash)
    {
        try
        {
            path = SafeChild(game, path);
            if (!File.Exists(path)) return;
            if (expectedHash != null)
            {
                using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
                if (!SameHash(Convert.ToHexString(SHA512.HashData(stream)), expectedHash)) return;
            }
            File.Delete(path);
        }
        catch (IOException) { } // Preserve inaccessible or externally changed staging files.
        catch (UnauthorizedAccessException) { }
    }
}
