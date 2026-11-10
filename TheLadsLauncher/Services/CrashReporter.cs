using System;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Json;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>
/// Sends a crashed Lads Client game's fresh crash report, newest JVM hs_err file and the tail of latest.log to the bot
/// (bot.arashyt.ca/crash), which posts it to staff on Discord. Only for the client profiles, never modpacks or Experiences.
/// The Windows user folder and anything token-shaped are scrubbed first. Never throws: returns null when sent, else why not.
/// </summary>
public static class CrashReporter
{
    private const string Endpoint = "https://bot.arashyt.ca/crash";
    private const int PartLimit = 200_000;
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(20) };
    private static readonly Regex Secrets = new(
        @"(eyJ[\w-]{8,}\.[\w-]{8,}\.[\w-]{8,})|((?:accessToken|access_token|token:|Session ID is)[""'=: ]*)[^\s""',]{16,}",
        RegexOptions.IgnoreCase | RegexOptions.Compiled);

    /// <summary>
    /// The section Lads Crash Catcher adds to a crash report it caught (common CrashCatcher.MARKER): the game left the world and kept
    /// running, so it exits normally later and those reports are sent from there (<see cref="CaughtReports"/>).
    /// </summary>
    public const string CaughtMarker = "-- Lads Crash Catcher --";

    /// <summary>Crash reports in <paramref name="gameDirectory"/> carrying <see cref="CaughtMarker"/>, written since <paramref name="since"/>, oldest first.</summary>
    public static string[] CaughtReports(string gameDirectory, DateTime since)
    {
        string dir = Path.Combine(gameDirectory, "crash-reports");
        if (!Directory.Exists(dir)) return Array.Empty<string>();
        return Directory.GetFiles(dir, "*.txt").Where(f => File.GetLastWriteTime(f) >= since && Marked(f)).OrderBy(File.GetLastWriteTime).ToArray();
    }

    /// <summary>
    /// Whether a non-zero exit is a real crash. Closing the window (X, Alt+F4, Quit Game) can end the JVM with a non-zero code,
    /// so that alone is no crash: it needs a crash report or hs_err log written since <paramref name="since"/>, a Windows fault
    /// status (0xC0000000 and up), a death within <paramref name="startupWindow"/> of starting, or an uncaught exception in latest.log.
    /// </summary>
    public static bool IsRealCrash(string gameDirectory, int exitCode, DateTime? since, TimeSpan uptime, TimeSpan startupWindow)
    {
        if (exitCode == 0) return false;
        if ((uint)exitCode >= 0xC0000000u) return true;
        if (since is not { } start) return true;
        if (uptime < startupWindow) return true;
        try
        {
            string reports = Path.Combine(gameDirectory, "crash-reports");
            if (Directory.Exists(reports) && Directory.GetFiles(reports, "*.txt").Any(f => File.GetLastWriteTime(f) >= start)) return true;
            if (Directory.GetFiles(gameDirectory, "hs_err_pid*.log").Any(f => File.GetLastWriteTime(f) >= start)) return true;
            string log = Path.Combine(gameDirectory, "logs", "latest.log");
            if (File.Exists(log) && File.GetLastWriteTime(log) >= start && Read(log, true).Contains("Exception in thread", StringComparison.Ordinal)) return true;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { return true; }
        return false;
    }

    private static bool Marked(string path)
    {
        try { return File.ReadLines(path).Any(line => line.Contains(CaughtMarker, StringComparison.Ordinal)); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { return false; }
    }

    /// <summary>
    /// Sends the game's crash. <paramref name="caughtReport"/>: a crash Lads Crash Catcher caught (the game then exited normally), sent
    /// as its own report without the JVM or Windows fault files, which belong to no crash then.
    /// </summary>
    public static async Task<string?> SendAsync(string gameDirectory, string? workingDirectory, string minecraftVersion, int exitCode, string? caughtReport = null)
    {
        try
        {
            var fresh = DateTime.Now.AddMinutes(-10);
            string? Newest(string dir, string pattern) => Directory.Exists(dir)
                ? Directory.GetFiles(dir, pattern).Where(f => File.GetLastWriteTime(f) > fresh).OrderByDescending(File.GetLastWriteTime).FirstOrDefault()
                : null;
            bool caught = caughtReport != null;
            if (!caught) await Task.Delay(4000).ConfigureAwait(false); // Windows Error Reporting logs the fault a moment after the process is gone
            string crash = Read(caught ? caughtReport : Newest(Path.Combine(gameDirectory, "crash-reports"), "*.txt"), tail: false);
            string hsErr = caught ? "" : Read(Newest(gameDirectory, "hs_err_pid*.log") ?? Newest(string.IsNullOrEmpty(workingDirectory) ? gameDirectory : workingDirectory, "hs_err_pid*.log")
                ?? Newest(Path.GetTempPath(), "hs_err_pid*.log"), tail: false);
            string log = Read(Path.Combine(gameDirectory, "logs", "latest.log"), tail: true);
            if (crash.Length == 0 && hsErr.Length == 0 && log.Contains("/INFO]: Stopping!", StringComparison.Ordinal))
                return "the game shut down normally";
            var payload = new
            {
                launcher = Program.Version,
                minecraft = minecraftVersion,
                exitCode,
                crash,
                hsErr,
                log,
                windowsEvent = caught ? "" : Scrub(WindowsFaults()),
            };
            using var request = new HttpRequestMessage(HttpMethod.Post, Endpoint) { Content = JsonContent.Create(payload) };
            request.Headers.UserAgent.ParseAdd(LauncherHttp.UserAgent);
            using var response = await Http.SendAsync(request).ConfigureAwait(false);
            return response.IsSuccessStatusCode ? null : $"the server answered {(int)response.StatusCode}";
        }
        catch (Exception ex)
        {
            return ex.Message;
        }
    }

    /// <summary>
    /// Windows Application Error / WER events for java from the last 10 minutes: a native crash that left no hs_err file still
    /// names its faulting module here (GPU driver, OpenAL, LWJGL, jvm.dll). "" when none or off Windows.
    /// </summary>
    public static string WindowsFaults()
    {
        if (!OperatingSystem.IsWindows()) return "";
        try
        {
            var start = new System.Diagnostics.ProcessStartInfo("wevtutil.exe")
            {
                UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true,
            };
            foreach (var a in new[] { "qe", "Application", "/q:*[System[(EventID=1000 or EventID=1001) and TimeCreated[timediff(@SystemTime) <= 600000]]]", "/rd:true", "/c:10", "/f:text" })
                start.ArgumentList.Add(a);
            using var process = System.Diagnostics.Process.Start(start)!;
            string text = process.StandardOutput.ReadToEnd();
            process.WaitForExit(5000);
            return string.Join("\n", text.Split("Event[", StringSplitOptions.RemoveEmptyEntries)
                .Where(e => e.Contains("java", StringComparison.OrdinalIgnoreCase))
                .Select(e => "Event[" + e.Trim())).Trim();
        }
        catch
        {
            return "";
        }
    }

    /// <summary>The file scrubbed and cut to <see cref="PartLimit"/> characters (its start, or its end for a log); "" when missing.</summary>
    public static string Read(string? path, bool tail)
    {
        if (path == null || !File.Exists(path)) return "";
        string text;
        using (var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
        using (var reader = new StreamReader(stream)) text = reader.ReadToEnd();
        text = Scrub(text);
        if (text.Length <= PartLimit) return text;
        return tail ? "[...]\n" + text[^PartLimit..] : text[..PartLimit] + "\n[...]";
    }

    public static string Scrub(string text)
    {
        string home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        string user = Path.GetFileName(home);
        if (home.Length > 3)
        {
            text = text.Replace(home, "%USERPROFILE%", StringComparison.OrdinalIgnoreCase)
                       .Replace(home.Replace('\\', '/'), "%USERPROFILE%", StringComparison.OrdinalIgnoreCase);
        }
        if (user.Length > 2) text = Regex.Replace(text, @"(?<=[\\/])" + Regex.Escape(user) + @"(?=[\\/])", "<user>", RegexOptions.IgnoreCase);
        text = Regex.Replace(text, @"^(USERNAME|USERDOMAIN\w*|COMPUTERNAME|LOGONSERVER|ONEDRIVE\w*)=[^\r\n]*", "$1=<hidden>", RegexOptions.Multiline | RegexOptions.IgnoreCase);
        return Secrets.Replace(text, m => m.Groups[1].Success ? "<token>" : m.Groups[2].Value + "<token>");
    }
}
