using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>ModSync, the launcher's part (the game's part: TheLadsCore client/social/modsync). Before a launch it writes the
/// profile's own mod jars with their hashes and, when Modrinth or CurseForge has the very same file, where to download it: a friend
/// who joins this player's world fetches what they miss. After the game closed itself to start again with a host's mods, it checks
/// every synced jar once more and puts them, with the profile's mods and settings, in a modpack of their own ("&lt;world&gt; - &lt;owner&gt;")
/// that it starts instead. The player's own profile is never touched.</summary>
public static class ModSyncService
{
    /// <summary>In .lads-mod-cache: the list the game publishes, and what earlier launches learned about each jar.</summary>
    public const string ListFile = "modsync.json", SourcesFile = "modsync-sources.json";
    /// <summary>In the game folder: the game's restart request, and the folder that holds one sub-folder of jars per host.</summary>
    public const string RelaunchFile = "lads-modsync.json", SyncFolder = "lads-modsync";
    public const int MaxMods = 512;
    public const long MaxJar = 256L * 1024 * 1024;
    private static readonly HashSet<string> ModrinthHosts = new(StringComparer.OrdinalIgnoreCase) { "cdn.modrinth.com" };
    private static readonly HashSet<string> CurseForgeHosts = new(StringComparer.OrdinalIgnoreCase) { "edge.forgecdn.net", "mediafilez.forgecdn.net" };
    private static readonly JsonSerializerOptions Json = new() { PropertyNamingPolicy = JsonNamingPolicy.CamelCase, PropertyNameCaseInsensitive = true };

    /// <summary>One own jar as the game publishes it (ModSyncEntry in the mod). Source is "modrinth", "curseforge" or "".</summary>
    public sealed record Mod(string Id, string Name, string Version, string File, long Size, string Sha1, string Sha512, string Source, string Url);
    private sealed record ModList(int V, List<Mod> Mods);
    /// <summary>What is known about one jar (keyed by name, size and write time): its hashes and, once the sites were asked, its site.</summary>
    public sealed record Known(string Sha1, string Sha512, uint Fingerprint, bool Asked, string Source, string Url);
    /// <summary>A checked restart request: the host's UUID, the folder of checked jars, and the host's world and username (may be empty).</summary>
    public sealed record Relaunch(string Host, string Folder, string World = "", string Owner = "");
    /// <summary>Whether a Modpacks-page instance takes part in ModSync: the launcher's own Fabric instances that run the Lads Core. Another
    /// launcher's instance is never written to, Forge-family loaders are not synced, and a Lads Experience is a fixed pack the join check
    /// matches by pack id (the game calls it a "pack" profile), so its mods are never swapped.</summary>
    public static bool Applies(ModpackInstance instance) =>
        !instance.IsExternal && string.Equals(instance.Loader, "fabric", StringComparison.OrdinalIgnoreCase) && instance.LadsCore == true
        && !ExperiencesService.IsExperienceInstance(instance.Id);

    /// <summary>Before a Modpacks-page launch: drops a restart request nobody took and, for an instance that <see cref="Applies"/>, writes
    /// its mod list as <see cref="WriteListAsync"/> does for a profile. A list that cannot be written only costs the sync (logged).</summary>
    public static async Task PrepareLaunchAsync(ModpackInstance instance, ModInventoryService inventories, string bundleRoot, ContentCatalog? catalog,
        Action<string> log, CancellationToken token = default)
    {
        if (instance.IsExternal) return;
        DropRelaunch(instance.GameDirectory);
        if (!Applies(instance)) return;
        try
        {
            var inventory = await inventories.BuildAsync(bundleRoot, instance.GameDirectory, instance.McVersion, token);
            await WriteListAsync(inventory, catalog, log, token);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            log($"ModSync: the mod list of '{instance.Name}' was not written ({e.Message}).");
        }
    }

    /// <summary>The player's settings a ModSync modpack starts with: copied once, when it is made.</summary>
    public static readonly string[] SettingsFiles = { "options.txt", "optionsof.txt", "optionsshaders.txt", "servers.dat" };
    private sealed record Request(string? Host, string? World, string? Owner, List<RequestMod>? Mods);
    private sealed record RequestMod(string? File, long Size, string? Sha512);

    /// <summary>Writes <see cref="ListFile"/> for this launch: the enabled jars the player added themselves. With a
    /// <paramref name="catalog"/>, jars not asked about yet are looked up by hash (at most 8 s; a failure only means friends get
    /// those jars from this PC, and they are asked about again at the next launch).</summary>
    public static async Task<IReadOnlyList<Mod>> WriteListAsync(ModInventory inventory, ContentCatalog? catalog, Action<string> log, CancellationToken token = default)
    {
        string cache = Path.Combine(inventory.GameDirectory, ".lads-mod-cache");
        Directory.CreateDirectory(cache);
        var known = Read<Dictionary<string, Known>>(Path.Combine(cache, SourcesFile)) ?? new();
        var jars = new Dictionary<string, Known>();
        var rows = new List<(ModInventoryEntry Entry, string Key, FileInfo File)>();
        foreach (var entry in inventory.Entries)
        {
            if (entry.Ownership != ModOwnership.User || entry.Status != ModEntryStatus.Installed || !entry.EnabledOnDisk || entry.FilePath == null) continue;
            var file = new FileInfo(entry.FilePath);
            if (rows.Count >= MaxMods || !file.Exists || file.Length <= 0 || file.Length > MaxJar || file.Name.Length > 160
                || !file.Name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)) continue;
            string key = $"{file.Name}|{file.Length}|{file.LastWriteTimeUtc.Ticks}";
            if (jars.ContainsKey(key)) continue;
            if (!known.TryGetValue(key, out var jar) || jar.Sha512 is not { Length: 128 } || jar.Sha1 is not { Length: 40 })
            {
                byte[] bytes = await File.ReadAllBytesAsync(entry.FilePath, token);
                jar = new Known(Hex(SHA1.HashData(bytes)), Hex(SHA512.HashData(bytes)), ContentCatalog.CurseForgeFingerprint(bytes), false, "", "");
            }
            jars[key] = jar;
            rows.Add((entry, key, file));
        }
        if (catalog != null) await AskSitesAsync(jars, catalog, log, token);
        var mods = rows.Select(row =>
        {
            var jar = jars[row.Key];
            return new Mod(row.Entry.Id, row.Entry.DisplayName, row.Entry.Version ?? "", row.File.Name, row.File.Length, jar.Sha1, jar.Sha512, jar.Source, jar.Url);
        }).ToList();
        await LockFiles.WriteAtomicallyAsync(Path.Combine(cache, SourcesFile), JsonSerializer.SerializeToUtf8Bytes(jars, Json), token);
        await LockFiles.WriteAtomicallyAsync(Path.Combine(cache, ListFile), JsonSerializer.SerializeToUtf8Bytes(new ModList(1, mods), Json), token);
        return mods;
    }

    private static async Task AskSitesAsync(Dictionary<string, Known> jars, ContentCatalog catalog, Action<string> log, CancellationToken token)
    {
        var unasked = jars.Where(jar => !jar.Value.Asked).ToList();
        if (unasked.Count == 0) return;
        using var limit = CancellationTokenSource.CreateLinkedTokenSource(token);
        limit.CancelAfter(TimeSpan.FromSeconds(8));
        try
        {
            var modrinth = await catalog.ModrinthFilesAsync(unasked.Select(jar => jar.Value.Sha1).Distinct().ToList(), limit.Token);
            var rest = unasked.Where(jar => Site(jar.Value, modrinth.GetValueOrDefault(jar.Value.Sha1), null).Source == "").ToList();
            // Without a CurseForge key those jars stay unasked: a later launch with a key still finds them.
            var curseForge = catalog.HasCurseForgeKey && rest.Count > 0
                ? await catalog.CurseForgeMatchesAsync(rest.Select(jar => jar.Value.Fingerprint).Distinct().ToList(), limit.Token) : null;
            foreach (var (key, jar) in unasked)
            {
                var (source, url) = Site(jar, modrinth.GetValueOrDefault(jar.Sha1), curseForge?.GetValueOrDefault(jar.Fingerprint));
                jars[key] = jar with { Asked = source != "" || curseForge != null, Source = source, Url = url };
            }
        }
        catch (Exception e) when (!token.IsCancellationRequested)
        {
            log($"ModSync: Modrinth/CurseForge could not be asked about your own mods ({e.Message}). Friends who join get those jars from your PC.");
        }
    }

    /// <summary>Where the sites have this exact jar. An answer counts only when its SHA-1 is the jar's (a Modrinth version can hold
    /// other files, and a CurseForge fingerprint is a weak hash) and its address is https on that site's own CDN.</summary>
    public static (string Source, string Url) Site(Known jar, ContentFile? modrinth, ContentFile? curseForge)
    {
        if (Same(jar, modrinth) && OnHost(modrinth!.Url, ModrinthHosts) is { } m) return ("modrinth", m);
        if (Same(jar, curseForge) && OnHost(curseForge!.Url, CurseForgeHosts) is { } c) return ("curseforge", c);
        return ("", "");
    }

    private static bool Same(Known jar, ContentFile? file) => file?.Sha1 != null && file.Sha1.Equals(jar.Sha1, StringComparison.OrdinalIgnoreCase)
        && (file.Sha512 == null || file.Sha512.Equals(jar.Sha512, StringComparison.OrdinalIgnoreCase));

    private static string? OnHost(string? url, HashSet<string> hosts) =>
        Uri.TryCreate(url, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps && uri.IsDefaultPort && uri.UserInfo.Length == 0
        && uri.Query.Length == 0 && uri.Fragment.Length == 0 && hosts.Contains(uri.Host) ? uri.AbsoluteUri : null;

    /// <summary>The game's request to start again with a host's mods (<see cref="RelaunchFile"/>), read and deleted. Null when there
    /// is none or it is refused (<paramref name="log"/> says why): the host must be a UUID, and every named jar must be a plain file
    /// in that host's folder with the size and SHA-512 the request gives. Files in that folder the request does not name are
    /// removed (an older sync with the same host), so only checked jars can load.</summary>
    public static Relaunch? TakeRelaunch(string game, Action<string> log)
    {
        string file = Path.Combine(game, RelaunchFile);
        if (!File.Exists(file)) return null;
        var request = Read<Request>(file);
        try { File.Delete(file); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { } // read once already; the next launch deletes it
        const string plain = " The game starts without the host's mods.";
        if (request?.Mods == null || request.Mods.Count == 0 || request.Mods.Count > MaxMods || !Guid.TryParseExact(request.Host, "D", out var host))
        {
            log("ModSync: the game's restart request could not be read." + plain);
            return null;
        }
        string root = Path.Combine(game, SyncFolder), folder = Path.Combine(root, host.ToString("D"));
        if (!Directory.Exists(folder) || SafeFileOps.IsLink(root) || SafeFileOps.IsLink(folder) || Directory.EnumerateDirectories(folder).Any())
        {
            log("ModSync: the synced mods folder is missing or not a plain folder." + plain);
            return null;
        }
        var named = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var mod in request.Mods)
        {
            string path = Path.Combine(folder, mod.File ?? "");
            if (!SafeFileOps.IsPlainFileName(mod.File) || !mod.File!.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || mod.Sha512 is not { Length: 128 }
                || !File.Exists(path) || SafeFileOps.IsLink(path) || new FileInfo(path).Length != mod.Size
                || !Sha512(path).Equals(mod.Sha512, StringComparison.OrdinalIgnoreCase))
            {
                log($"ModSync: '{mod.File}' is not the file the host listed (hash check failed)." + plain);
                return null;
            }
            named.Add(mod.File);
        }
        foreach (string other in Directory.EnumerateFiles(folder).Where(f => !named.Contains(Path.GetFileName(f))).ToList())
        {
            try { File.Delete(other); }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                log($"ModSync: could not remove the unchecked file '{Path.GetFileName(other)}'." + plain);
                return null;
            }
        }
        log($"ModSync: starting again with {named.Count} mod(s) from the host's world.");
        return new Relaunch(host.ToString("D"), folder, request.World ?? "", request.Owner ?? "");
    }

    /// <summary>"&lt;world&gt; - &lt;owner&gt;": control characters dropped, each part capped, a placeholder for a missing one.</summary>
    public static string PackName(Relaunch relaunch) => WorldPart(relaunch) + " - " + Clean(relaunch.Owner, 32, "Host");

    /// <summary>The world part of <see cref="PackName"/>: what tells one world of a host from another.</summary>
    public static string WorldPart(Relaunch relaunch) => Clean(relaunch.World, 48, "Friend's world");

    private static string Clean(string? text, int max, string fallback)
    {
        var clean = new string((text ?? "").Where(c => !char.IsControl(c)).ToArray()).Trim();
        if (clean.Length > max) clean = clean[..max].Trim();
        return clean.Length == 0 ? fallback : clean;
    }

    /// <summary>In the instance folder: the jar names ModSync put in the modpack's mods folder, so the next sync knows which ones are its own.</summary>
    public const string ManagedFile = ".lads-modsync-managed.json";

    /// <summary>The modpack made for this host and world before (an owner who renamed does not make a new one), if any.</summary>
    public static ModpackInstance? FindInstance(string dataDirectory, Relaunch relaunch)
    {
        string name = PackName(relaunch), world = WorldPart(relaunch);
        return Modpacks.List(dataDirectory).FirstOrDefault(i => !i.IsExternal && i.ModSyncHost == relaunch.Host
            && (i.ModSyncWorld != null ? string.Equals(i.ModSyncWorld, world, StringComparison.OrdinalIgnoreCase) : i.Name == name));
    }

    /// <summary>The modpack a ModSync restart starts: the one made for this host and world before, else a new one with the player's
    /// settings (<see cref="SettingsFiles"/> and config/) copied from <paramref name="game"/>. An existing one keeps its settings and
    /// its own files and is updated in place: the profile's enabled jars and the host's checked jars (a jar of the same name is the
    /// host's) are added, replaced when they changed, and the ones ModSync put there earlier that are no longer wanted are removed.
    /// The Lads Core stays on.</summary>
    public static ModpackInstance BuildInstance(string dataDirectory, Relaunch relaunch, string game, string mcVersion, string loaderVersion, Action<string> log)
    {
        string name = PackName(relaunch);
        var instance = FindInstance(dataDirectory, relaunch);
        bool made = instance == null;
        if (instance == null)
        {
            instance = Modpacks.NewInstance(dataDirectory, name);
            instance.Author = string.IsNullOrWhiteSpace(relaunch.Owner) ? null : relaunch.Owner;
            foreach (var file in SettingsFiles)
            {
                string from = Path.Combine(game, file);
                if (File.Exists(from) && !SafeFileOps.IsLink(from)) File.Copy(from, Path.Combine(instance.GameDirectory, file), overwrite: true);
            }
            CopyFolder(Path.Combine(game, "config"), Path.Combine(instance.GameDirectory, "config"));
            log($"ModSync: made the modpack '{name}' with your settings.");
        }
        instance.ModSyncHost = relaunch.Host;
        instance.ModSyncWorld = WorldPart(relaunch);
        instance.McVersion = mcVersion;
        instance.Loader = "fabric";
        instance.LoaderVersion = loaderVersion;
        instance.LadsCore = true;
        string mods = Path.Combine(instance.GameDirectory, "mods");
        Directory.CreateDirectory(mods);

        // What the pack should hold: the profile's enabled jars, then the host's (same name: the host's).
        var wanted = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        string profileMods = Path.Combine(game, "mods");
        if (Directory.Exists(profileMods))
            foreach (var jar in Directory.EnumerateFiles(profileMods, "*.jar").Where(f => !SafeFileOps.IsLink(f) && !IsCore(f)))
                wanted[Path.GetFileName(jar)] = jar;
        var hostJars = Directory.EnumerateFiles(relaunch.Folder, "*.jar").Where(f => !IsCore(f)).ToList();
        foreach (var jar in hostJars) wanted[Path.GetFileName(jar)] = jar;
        int host = hostJars.Count, own = wanted.Count - host;

        // Ours from before: the recorded names; a modpack made before the record existed counts all its jars as ours.
        string record = Path.Combine(instance.Directory, ManagedFile);
        var managed = Read<List<string>>(record) ?? Directory.EnumerateFiles(mods).Where(IsJar).Select(f => Path.GetFileName(f).Replace(".jar.disabled", ".jar", StringComparison.OrdinalIgnoreCase)).ToList();
        int added = 0, updated = 0, removed = 0;
        foreach (var (file, source) in wanted)
        {
            string target = Path.Combine(mods, file), off = target + ".disabled"; // a jar the player switched off stays off
            string dest = !File.Exists(target) && File.Exists(off) ? off : target;
            if (!File.Exists(dest)) added++;
            else if (SameFile(source, dest)) continue;
            else updated++;
            File.Copy(source, dest, overwrite: true);
        }
        foreach (var gone in managed.Where(m => !wanted.ContainsKey(m) && SafeFileOps.IsPlainFileName(m) && !IsCore(m)))
            foreach (var path in new[] { Path.Combine(mods, gone), Path.Combine(mods, gone) + ".disabled" })
                if (File.Exists(path)) { File.Delete(path); removed++; }
        File.WriteAllText(record, JsonSerializer.Serialize(wanted.Keys.OrderBy(k => k, StringComparer.OrdinalIgnoreCase)));
        Modpacks.Save(instance);
        log(made ? $"ModSync: '{name}' has your {own} mod(s) and {host} from the host."
            : $"ModSync: '{name}' updated in place: {added} added, {updated} updated, {removed} removed ({own} of yours, {host} from the host).");
        return instance;
    }

    private static bool IsCore(string file) => Path.GetFileName(file).Equals("theladscore.jar", StringComparison.OrdinalIgnoreCase);

    private static bool SameFile(string a, string b) =>
        new FileInfo(a).Length == new FileInfo(b).Length && Sha512(a).Equals(Sha512(b), StringComparison.OrdinalIgnoreCase);

    private static bool IsJar(string file) => file.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || file.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase);

    private static void CopyFolder(string from, string to)
    {
        if (!Directory.Exists(from) || SafeFileOps.IsLink(from)) return;
        Directory.CreateDirectory(to);
        foreach (var file in Directory.EnumerateFiles(from).Where(f => !SafeFileOps.IsLink(f)))
            File.Copy(file, Path.Combine(to, Path.GetFileName(file)), overwrite: true);
        foreach (var dir in Directory.EnumerateDirectories(from)) CopyFolder(dir, Path.Combine(to, Path.GetFileName(dir)));
    }

    /// <summary>A launch that is not a ModSync restart drops a request nobody took (the launcher was closed when the game asked).</summary>
    public static void DropRelaunch(string game)
    {
        try { File.Delete(Path.Combine(game, RelaunchFile)); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
    }

    /// <summary>The JVM argument of a ModSync modpack's first launch: the mod rejoins that host.</summary>
    public static string JvmArgument(string host) => "-Dlads.modsync.host=" + host;

    private static string Sha512(string path)
    {
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
        return Hex(SHA512.HashData(stream));
    }

    private static string Hex(byte[] bytes) => Convert.ToHexString(bytes).ToLowerInvariant();

    private static T? Read<T>(string path) where T : class
    {
        try { return File.Exists(path) ? JsonSerializer.Deserialize<T>(File.ReadAllText(path), Json) : null; }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { return null; }
    }
}
