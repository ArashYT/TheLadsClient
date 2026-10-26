using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.IO.Compression;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using CmlLib.Core.Auth;

namespace TheLadsLauncher.Services;

/// <summary>
/// Experimental: Horizon (github.com/ludovic111/horizon, MIT), Minecraft 26.3 rewritten in Rust, started instead of the Lads
/// Client for a 26.3 profile. Off by default; Dev Launcher builds or accounts invited with /experimental_invite only. None of
/// LadsCore runs in it, so none of the Lads anti-ban gating does either: the player accepts that risk when turning it on.
/// </summary>
public static class HorizonEngine
{
    public const string MinecraftVersion = "26.3";
    public const string InviteId = "experimental";
    // ponytail: one pinned release (they ship several a day); bump Tag + ZipSha256 together after testing a newer one.
    public const string Tag = "v0.2.75";
    public const string ZipSha256 = "aa7cb138a34471072433a422261ac2bf5bcf7e34834b004b0e15cfd51c8cb211";
    const string ZipUrl = "https://github.com/ludovic111/horizon/releases/download/" + Tag + "/horizon-windows-x64.zip";
    // research-lock.json of that release: the 26.3 server jar its preparation accepts.
    public const string ServerJarSha1 = "33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c";

    public const string RiskWarning =
        "Horizon is NOT the Lads Client. It is a separate, unofficial Minecraft 26.3 rewritten in Rust by another developer " +
        "(github.com/ludovic111/horizon).\n\n" +
        "• None of the Lads Client runs in it: no HUD mods, cosmetics, emotes, friends or ban protection.\n" +
        "• Servers can see it is not the normal game. Joining servers with it CAN GET YOUR ACCOUNT BANNED.\n" +
        "• It gets your Minecraft login so it can join servers.\n" +
        "• Its worlds are kept apart from your Lads worlds. It may crash or lose things.\n\n" +
        "When on, Play on a 26.3 profile starts Horizon. USE AT YOUR OWN RISK.";

    public static bool Applies(string? minecraftVersion) => minecraftVersion == MinecraftVersion;

    public static string Root(string baseDirectory) => Path.Combine(baseDirectory, "horizon");
    public static string GameDirectory(string baseDirectory) => Path.Combine(Root(baseDirectory), Tag, "horizon-windows-x64");
    public static string Home(string baseDirectory) => Path.Combine(Root(baseDirectory), "home");
    static string Resources(string game) => Path.Combine(game, ".local", "horizon-client", MinecraftVersion);
    static string PreparedMarker(string game) => Path.Combine(game, ".lads-prepared");

    public static bool IsPrepared(string baseDirectory) => File.Exists(PreparedMarker(GameDirectory(baseDirectory)));

    static readonly HttpClient Http = CreateHttp();

    static HttpClient CreateHttp()
    {
        var http = new HttpClient { Timeout = TimeSpan.FromMinutes(10) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd("TheLadsLauncher");
        return http;
    }

    /// <summary>Downloads the pinned release once (checked against its SHA-256) and returns the folder with horizon-client.exe.</summary>
    public static async Task<string> InstallAsync(string baseDirectory, Action<string> status, CancellationToken token)
    {
        string game = GameDirectory(baseDirectory);
        if (File.Exists(Path.Combine(game, "horizon-client.exe"))) return game;
        string root = Root(baseDirectory);
        Directory.CreateDirectory(root);
        string zip = Path.Combine(root, Tag + ".zip.part");
        status($"Downloading Horizon {Tag}...");
        await DownloadAsync(ZipUrl, zip, token);
        if (!string.Equals(await Sha256Async(zip, token), ZipSha256, StringComparison.OrdinalIgnoreCase))
        {
            File.Delete(zip);
            throw new InvalidOperationException($"The Horizon {Tag} download does not match its pinned checksum and was deleted.");
        }
        string staging = Path.Combine(root, Tag + ".tmp");
        if (Directory.Exists(staging)) Directory.Delete(staging, true);
        status("Unpacking Horizon...");
        await Task.Run(() => ZipFile.ExtractToDirectory(zip, staging), token);
        File.Delete(zip);
        Directory.Move(staging, Path.Combine(root, Tag));
        return game;
    }

    /// <summary>
    /// First run: Horizon builds its resources from the player's own vanilla 26.3 files (<paramref name="minecraftDirectory"/>,
    /// installed by the launcher) with Java 25 and the official server jar, then copies the sounds. Returns the resources folder.
    /// </summary>
    public static async Task<string> PrepareAsync(string game, string minecraftDirectory, string javaPath, Action<string> status, CancellationToken token)
    {
        string resources = Resources(game);
        if (File.Exists(PreparedMarker(game)) && Directory.Exists(resources)) return resources;
        string serverJar = Path.Combine(Path.GetDirectoryName(Path.GetDirectoryName(game))!, $"server-{MinecraftVersion}.jar");
        if (!File.Exists(serverJar) || !string.Equals(await Sha1Async(serverJar, token), ServerJarSha1, StringComparison.OrdinalIgnoreCase))
        {
            status("Downloading the official 26.3 server jar...");
            string versionJson = Path.Combine(minecraftDirectory, "versions", MinecraftVersion, MinecraftVersion + ".json");
            using var doc = JsonDocument.Parse(await File.ReadAllTextAsync(versionJson, token));
            string url = doc.RootElement.GetProperty("downloads").GetProperty("server").GetProperty("url").GetString()!;
            if (!url.StartsWith("https://piston-data.mojang.com/", StringComparison.Ordinal))
                throw new InvalidOperationException("The 26.3 version file names an unexpected server jar host.");
            await DownloadAsync(url, serverJar + ".part", token);
            if (!string.Equals(await Sha1Async(serverJar + ".part", token), ServerJarSha1, StringComparison.OrdinalIgnoreCase))
            {
                File.Delete(serverJar + ".part");
                throw new InvalidOperationException("The 26.3 server jar does not match the one Horizon expects.");
            }
            File.Move(serverJar + ".part", serverJar, true);
        }
        string javaHome = Path.GetDirectoryName(Path.GetDirectoryName(javaPath))!;
        string log = Path.Combine(game, "lads-prepare.log");
        File.Delete(log);
        status("Preparing Horizon from your Minecraft 26.3 (first time only, a few minutes)...");
        await RunAsync(game, javaHome, log, status, token, "--prepare", "--server-jar", serverJar, "--minecraft-dir", minecraftDirectory);
        status("Copying sounds for Horizon...");
        await RunAsync(game, javaHome, log, status, token, "sounds", "--minecraft-dir", minecraftDirectory);
        if (!Directory.Exists(resources))
            throw new InvalidOperationException($"Horizon's preparation finished without its resources. See {log}");
        File.WriteAllText(PreparedMarker(game), Tag);
        return resources;
    }

    static async Task RunAsync(string game, string javaHome, string log, Action<string> status, CancellationToken token, params string[] args)
    {
        var psi = new ProcessStartInfo(Path.Combine(game, "horizon-prepare.exe"))
        {
            WorkingDirectory = game,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        foreach (var arg in args) psi.ArgumentList.Add(arg);
        psi.Environment["JAVA_HOME"] = javaHome;
        psi.Environment["PATH"] = Path.Combine(javaHome, "bin") + Path.PathSeparator + Environment.GetEnvironmentVariable("PATH");
        using var process = Process.Start(psi) ?? throw new InvalidOperationException("Could not start horizon-prepare.");
        var sink = TextWriter.Synchronized(new StreamWriter(log, append: true));
        void Line(string? line)
        {
            if (string.IsNullOrWhiteSpace(line)) return;
            sink.WriteLine(line);
            status("Horizon: " + (line.Length > 120 ? line[..120] + "…" : line));
        }
        process.OutputDataReceived += (_, e) => Line(e.Data);
        process.ErrorDataReceived += (_, e) => Line(e.Data);
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();
        try { await process.WaitForExitAsync(token); }
        catch (OperationCanceledException) { try { process.Kill(true); } catch { } throw; }
        finally { sink.Dispose(); }
        if (process.ExitCode != 0)
            throw new InvalidOperationException($"Horizon's preparation failed (exit {process.ExitCode}). See {log}");
    }

    /// <summary>Starts horizon-client.exe; a Microsoft account goes over as Horizon's one-shot session file (read, then deleted).</summary>
    public static Process Start(string baseDirectory, string game, string resources, MSession session, bool offline, Func<bool>? sendCrashReports = null)
    {
        string home = Home(baseDirectory);
        Directory.CreateDirectory(home);
        InstallLadsMods(home, Path.Combine(AppContext.BaseDirectory, "Assets", "horizon", "lads_hud.hmod"));
        var psi = new ProcessStartInfo(Path.Combine(game, "horizon-client.exe"))
        {
            WorkingDirectory = home,
            UseShellExecute = false,
            CreateNoWindow = true,
        };
        psi.Environment["HORIZON_HOME"] = home;
        psi.ArgumentList.Add("--resources");
        psi.ArgumentList.Add(resources);
        if (offline)
        {
            psi.ArgumentList.Add("--name");
            psi.ArgumentList.Add(session.Username ?? "Player");
        }
        else
        {
            psi.ArgumentList.Add("--session-file");
            psi.ArgumentList.Add(WriteSessionFile(home, session));
        }
        DateTime startedUtc = DateTime.UtcNow;
        var process = Process.Start(psi) ?? throw new InvalidOperationException("Could not start Horizon.");
        try { startedUtc = process.StartTime.ToUniversalTime(); } catch { }

        process.EnableRaisingEvents = true;
        process.Exited += async (_, _) =>
        {
            try
            {
                int exitCode = 0;
                try { exitCode = process.ExitCode; } catch { }
                await SendNewCrashReportsAsync(home, startedUtc, exitCode, sendCrashReports).ConfigureAwait(false);
            }
            catch
            {
            }
        };

        return process;
    }

    /// <summary>
    /// Crash reports in Horizon home (crash-reports/crash-*-client.txt) newer than <paramref name="started"/>, oldest first.
    /// </summary>
    public static string[] NewCrashReports(string home, DateTime started)
    {
        string dir = Path.Combine(home, "crash-reports");
        if (!Directory.Exists(dir)) return Array.Empty<string>();
        DateTime startedUtc = started.ToUniversalTime();
        return Directory.GetFiles(dir, "crash-*-client.txt")
            .Where(f => File.GetLastWriteTimeUtc(f) >= startedUtc)
            .OrderBy(File.GetLastWriteTimeUtc)
            .ToArray();
    }

    /// <summary>
    /// When Horizon exits, sends any new crash reports to the bot through CrashReporter if "Send crash reports" is enabled.
    /// </summary>
    public static async Task<int> SendNewCrashReportsAsync(
        string home,
        DateTime started,
        int exitCode,
        Func<bool>? isEnabled = null,
        Func<string, Task<string?>>? reportSender = null)
    {
        bool allowed = isEnabled != null ? isEnabled() : LauncherSettings.Load().SendCrashReports;
        if (!allowed) return 0;

        var reports = NewCrashReports(home, started);
        if (reports.Length == 0) return 0;

        int sent = 0;
        foreach (var report in reports)
        {
            if (reportSender != null)
            {
                await reportSender(report).ConfigureAwait(false);
            }
            else
            {
                await CrashReporter.SendAsync(home, home, MinecraftVersion, exitCode, report).ConfigureAwait(false);
            }
            sent++;
        }
        return sent;
    }

    /// <summary>
    /// Copies the bundled Lads HUD mod into Horizon's mod folder when missing or changed, and turns it on in horizon-mods.txt the
    /// first time only: a mod.lads_hud line (true or false) already there is the player's choice and stays.
    /// </summary>
    public static void InstallLadsMods(string home, string bundledHmod)
    {
        if (!File.Exists(bundledHmod)) return;
        string dir = Path.Combine(home, "horizon-mods");
        string target = Path.Combine(dir, "lads_hud.hmod");
        byte[] bytes = File.ReadAllBytes(bundledHmod);
        if (!File.Exists(target) || !SHA256.HashData(File.ReadAllBytes(target)).AsSpan().SequenceEqual(SHA256.HashData(bytes)))
        {
            Directory.CreateDirectory(dir);
            File.WriteAllBytes(target + ".part", bytes);
            File.Move(target + ".part", target, true);
        }
        string settings = Path.Combine(home, "horizon-mods.txt");
        string text = File.Exists(settings) ? File.ReadAllText(settings) : "";
        if (!Array.Exists(text.Split('\n'), line => line.TrimStart().StartsWith("mod.lads_hud:", StringComparison.Ordinal)))
            File.AppendAllText(settings, (text.Length == 0 || text.EndsWith('\n') ? "" : "\n") + "mod.lads_hud:true\n");
    }

    public static string WriteSessionFile(string home, MSession session)
    {
        string dir = Path.Combine(home, "sessions");
        Directory.CreateDirectory(dir);
        // A game that died before reading its file leaves the token on disk: never keep an old one.
        foreach (var old in Directory.GetFiles(dir, "session-*.json")) try { File.Delete(old); } catch (IOException) { }
        string file = Path.Combine(dir, $"session-{Guid.NewGuid():N}.json");
        using var stream = new FileStream(file, FileMode.CreateNew, FileAccess.Write, FileShare.None);
        JsonSerializer.Serialize(stream, new
        {
            name = session.Username,
            uuid = session.UUID,
            access_token = session.AccessToken,
            user_type = "msa",
            xuid = session.Xuid ?? "",
        });
        return file;
    }

    static async Task DownloadAsync(string url, string file, CancellationToken token)
    {
        using var response = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, token);
        response.EnsureSuccessStatusCode();
        await using var output = new FileStream(file, FileMode.Create, FileAccess.Write, FileShare.None, 81920, useAsync: true);
        await response.Content.CopyToAsync(output, token);
    }

    public static async Task<string> Sha256Async(string file, CancellationToken token)
    {
        await using var stream = File.OpenRead(file);
        return Convert.ToHexString(await SHA256.HashDataAsync(stream, token));
    }

    static async Task<string> Sha1Async(string file, CancellationToken token)
    {
        await using var stream = File.OpenRead(file);
        return Convert.ToHexString(await SHA1.HashDataAsync(stream, token));
    }
}
