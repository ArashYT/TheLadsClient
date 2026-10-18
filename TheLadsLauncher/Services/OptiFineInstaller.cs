using System;
using System.Collections.Concurrent;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>
/// OptiFine for Minecraft 1.8.9 (Forge). Its licence forbids redistribution, so the launcher never ships it: at a 1.8.9 launch
/// it reads a fresh download link from optifine.net, downloads the jar, checks the pinned size and SHA-256, keeps it in the
/// launcher's data folder (cache\optifine) and copies it into the profile's mods folder. It never blocks a launch: after any
/// failure the game starts without OptiFine and the caller shows the returned warning. On the Mods page it is switched like a
/// pack mod (id "optifine"); a differently named OptiFine jar of your own is used instead of the download.
/// </summary>
public static class OptiFineInstaller
{
    public const string ModId = "optifine";
    /// <summary>The opt-in 1.8.9 renderer in client-mods.json (Ionium: Celeritas for Forge 1.8.9); OptiFine is off while it is on.</summary>
    public const string IoniumModId = "ionium";
    public const string Version = "1.8.9_HD_U_M6_pre2";

    /// <summary>The one jar the launcher accepts. Tests and the sandbox forced-failure run pass another.</summary>
    public sealed record Pin(string FileName, string Sha256, long Size);

    /// <summary>OptiFine 1.8.9 HD U M5, as fetched and checked in spike S2 (artifacts\1.4.0\spikes\s2\REPORT.md).</summary>
    public static readonly Pin M5 = new("OptiFine_1.8.9_HD_U_M5.jar",
        "2739760a70fca6ae5c7dc817bcc6283f18f55e38fa23778da7f387a6b724b2c4", 2_585_014);

    /// <summary>OptiFine 1.8.9 HD U M6_pre2, as fetched from optifine.net.</summary>
    public static readonly Pin M6Pre2 = new("preview_OptiFine_1.8.9_HD_U_M6_pre2.jar",
        "d57b7fd7bd519bbe4fdbf36cceebfb64d459b7713e1afb0bf6549c21b476a028", 2_584_186);

    public static readonly Pin DefaultPin = M6Pre2;

    private static readonly HttpClient Http = CreateClient();

    private static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromMinutes(3) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("TheLadsClient/1.4");
        return client;
    }

    /// <summary>Whether a file in Mods has a launcher-managed OptiFine name (enabled or switched off).</summary>
    public static bool IsLaunchersFile(string fileName, Pin? currentPin = null) =>
        fileName.Equals(currentPin?.FileName, StringComparison.OrdinalIgnoreCase) || fileName.Equals((currentPin?.FileName) + ".disabled", StringComparison.OrdinalIgnoreCase)
        || fileName.Equals(DefaultPin.FileName, StringComparison.OrdinalIgnoreCase) || fileName.Equals(DefaultPin.FileName + ".disabled", StringComparison.OrdinalIgnoreCase)
        || fileName.Equals(M5.FileName, StringComparison.OrdinalIgnoreCase) || fileName.Equals(M5.FileName + ".disabled", StringComparison.OrdinalIgnoreCase);

    /// <summary>
    /// Makes the profile load the verified OptiFine, unless it is switched off or an OptiFine jar of your own is enabled.
    /// Returns null when there is nothing to warn about, otherwise the warning (the game then starts without OptiFine).
    /// Only the launch's own cancellation is thrown.
    /// </summary>
    public static async Task<string?> InstallAsync(string launcherDirectory, string gameDirectory, Pin? pin = null,
        Action<string>? status = null, CancellationToken cancellationToken = default, HttpClient? httpClient = null)
    {
        pin ??= DefaultPin;
        string? target = null;
        try
        {
            var game = Path.GetFullPath(gameDirectory);
            var mods = ClientModInstaller.SafeChild(game, Path.Combine(game, "mods"));
            target = ClientModInstaller.SafeChild(game, Path.Combine(mods, pin.FileName));
            Directory.CreateDirectory(mods);
            // Forge also loads mods/1.8.9/, so an OptiFine kept there counts as your own.
            var versionMods = ClientModInstaller.SafeChild(game, Path.Combine(mods, GameVersionPolicy.ForgeMinecraftVersion));
            var copies = new[] { mods, versionMods }.Where(Directory.Exists).SelectMany(Directory.EnumerateFiles)
                .Where(p => p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)
                    || p.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase))
                .Where(p => ModInventoryService.ScanFor(p, GameVersionPolicy.ForgeMinecraftVersion, cancellationToken).Info?.Id == ModId).ToList();
            var enabled = copies.Where(p => p.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)).ToList();
            // Ionium (the opt-in Celeritas renderer) refuses to start next to OptiFine: while it is on, every OptiFine is switched off,
            // and the marker brings it back once Ionium is off again (the disk state alone would keep it off).
            var offForIonium = ClientModInstaller.SafeChild(game, Path.Combine(game, ".lads-mod-cache", "optifine-off-for-ionium"));
            if (ModPreferences.Load(game).GetEnabled(IoniumModId, null) == true)
            {
                Directory.CreateDirectory(Path.GetDirectoryName(offForIonium)!);
                File.WriteAllText(offForIonium, "");
                foreach (var jar in enabled)
                {
                    var off = jar + ".disabled";
                    if (File.Exists(off))
                    {
                        var backup = ClientModInstaller.SafeChild(game, Path.Combine(game, ".lads-mod-cache", $"previous-{ModId}-{Guid.NewGuid():N}.jar"));
                        Directory.CreateDirectory(Path.GetDirectoryName(backup)!);
                        File.Move(off, backup);
                    }
                    File.Move(jar, off);
                }
                status?.Invoke("OptiFine is off while Ionium renders this profile.");
                return null;
            }
            var backFromIonium = File.Exists(offForIonium);
            if (backFromIonium) File.Delete(offForIonium);
            // As for pack mods: the Mods page choice, else the disk state (only switched-off copies: off).
            if (!(ModPreferences.Load(game).GetEnabled(ModId, null) ?? (backFromIonium || copies.Count == 0 || enabled.Count > 0)))
            {
                status?.Invoke("OptiFine is switched off for this profile.");
                return null;
            }
            // An enabled OptiFine jar of your own (any other name) is used instead: Forge must never get two.
            var own = enabled.FirstOrDefault(p => !SafeFileOps.PathsEqual(p, target) && !IsLaunchersFile(Path.GetFileName(p), pin));
            // Move aside any older or alternative launcher OptiFine copies (e.g. M5 when migrating to M6_pre2, or disabled/extra copies).
            foreach (var copy in copies.Where(c => !SafeFileOps.PathsEqual(c, target) && IsLaunchersFile(Path.GetFileName(c), pin)))
            {
                var backup = ClientModInstaller.SafeChild(game, Path.Combine(game, ".lads-mod-cache", $"previous-{ModId}-{Guid.NewGuid():N}.jar"));
                Directory.CreateDirectory(Path.GetDirectoryName(backup)!);
                File.Move(copy, backup);
                status?.Invoke($"Moved older '{Path.GetFileName(copy)}' out of Mods to '{backup}'.");
            }
            if (own == null && await VerifiedAsync(target, pin, cancellationToken))
            {
                status?.Invoke($"{pin.FileName} is installed (SHA-256 verified).");
                return null;
            }
            // The launcher's file name with other bytes (damaged or replaced), or next to your own OptiFine: kept as a backup
            // (the cache restores the launcher's copy), never loaded.
            if (File.Exists(target))
            {
                var backup = ClientModInstaller.SafeChild(game, Path.Combine(game, ".lads-mod-cache", $"previous-{ModId}-{Guid.NewGuid():N}.jar"));
                Directory.CreateDirectory(Path.GetDirectoryName(backup)!);
                File.Move(target, backup);
                status?.Invoke($"Moved '{pin.FileName}' out of Mods to '{backup}'.");
            }
            if (own != null)
            {
                status?.Invoke($"Using your own OptiFine '{Path.GetFileName(own)}'.");
                return null;
            }
            var cached = Path.Combine(Path.GetFullPath(launcherDirectory), "cache", "optifine", pin.FileName);
            if (await VerifiedAsync(cached, pin, cancellationToken)) status?.Invoke($"Using the verified {pin.FileName} from the launcher cache.");
            else await DownloadAsync(cached, pin, status, httpClient ?? Http, cancellationToken);
            // Staged in Mods (Forge loads only .jar and .zip files), checked again, then renamed into place.
            var staged = Path.Combine(mods, $".lads-optifine-{Guid.NewGuid():N}.tmp");
            try
            {
                File.Copy(cached, staged);
                if (!await VerifiedAsync(staged, pin, cancellationToken))
                    throw new InvalidDataException("the copy in Mods does not match the pinned SHA-256");
                File.Move(staged, target);
            }
            finally { if (File.Exists(staged)) File.Delete(staged); }
            status?.Invoke($"Installed {pin.FileName} (SHA-256 verified).");
            return null;
        }
        catch (Exception e) when (!cancellationToken.IsCancellationRequested)
        {
            // Nothing unchecked is left in Mods, unless the file there could not even be checked or moved.
            var outcome = target != null && File.Exists(target)
                ? $"'{pin.FileName}' could not be checked or moved out of Mods: remove it if the game does not start"
                : "Minecraft starts without it";
            return $"OptiFine was not installed ({e.Message.TrimEnd('.')}). {outcome}; the next launch tries again.";
        }
    }

    /// <summary>The download link on optifine.net's adloadx page for <paramref name="fileName"/>, with the page's x token, or null.</summary>
    public static string? DownloadUrl(string page, string fileName)
    {
        var link = Regex.Match(page, @"href=['""](downloadx\?f=" + Regex.Escape(fileName) + @"&(?:amp;)?x=[0-9a-fA-F]+)['""]");
        return link.Success ? "https://optifine.net/" + link.Groups[1].Value.Replace("&amp;", "&") : null;
    }

    private static async Task DownloadAsync(string cached, Pin pin, Action<string>? status, HttpClient http, CancellationToken token)
    {
        status?.Invoke($"Downloading {pin.FileName} from optifine.net...");
        Directory.CreateDirectory(Path.GetDirectoryName(cached)!);
        var temp = cached + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
            timeout.CancelAfter(TimeSpan.FromSeconds(30));
            // The x token changes, so the page is read for every download and the link is never kept.
            var page = await http.GetStringAsync("https://optifine.net/adloadx?f=" + Uri.EscapeDataString(pin.FileName), timeout.Token);
            var url = DownloadUrl(page, pin.FileName) ?? throw new InvalidDataException("optifine.net's download page has no link for " + pin.FileName);
            timeout.CancelAfter(TimeSpan.FromSeconds(30));
            using var response = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, timeout.Token);
            response.EnsureSuccessStatusCode();
            // A stale or wrong token still answers 200, with a short web page.
            if (string.Equals(response.Content.Headers.ContentType?.MediaType, "text/html", StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException("optifine.net sent a web page instead of the jar");
            timeout.CancelAfter(TimeSpan.FromMinutes(10));
            await using (var input = await response.Content.ReadAsStreamAsync(timeout.Token))
            await using (var output = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, true))
                if (await ClientModInstaller.CopyWithStallTimeoutAsync(input, output, pin.Size, TimeSpan.FromSeconds(30), "OptiFine", timeout.Token) != pin.Size)
                    throw new InvalidDataException("the download is incomplete");
            if (!await VerifiedAsync(temp, pin, token))
                throw new InvalidDataException("the download does not match the pinned SHA-256");
            File.Move(temp, cached, overwrite: true);
        }
        catch (OperationCanceledException) when (!token.IsCancellationRequested)
        {
            throw new TimeoutException("optifine.net did not answer in time");
        }
        finally
        {
            if (File.Exists(temp)) File.Delete(temp);
        }
    }

    private static readonly ConcurrentDictionary<string, (long Size, DateTime MtimeUtc, bool Verified)> _optiFineVerifyCache = new(StringComparer.OrdinalIgnoreCase);

    // Size first, then the SHA-256 of the bytes.
    private static async Task<bool> VerifiedAsync(string path, Pin pin, CancellationToken token)
    {
        if (!File.Exists(path)) return false;
        var info = new FileInfo(path);
        if (info.Length != pin.Size) return false;

        string cacheKey = path + "|" + pin.Sha256;
        if (_optiFineVerifyCache.TryGetValue(cacheKey, out var entry) && entry.Size == info.Length && entry.MtimeUtc == info.LastWriteTimeUtc)
        {
            return entry.Verified;
        }

        await using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read | FileShare.Delete, 81920, true);
        bool verified = Convert.ToHexString(await SHA256.HashDataAsync(stream, token)).Equals(pin.Sha256, StringComparison.OrdinalIgnoreCase);
        if (verified)
        {
            _optiFineVerifyCache[cacheKey] = (info.Length, info.LastWriteTimeUtc, true);
        }
        return verified;
    }
}
