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
    private const int PartLimit = 250_000;
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(20) };
    private static readonly Regex Secrets = new(
        @"(eyJ[\w-]{8,}\.[\w-]{8,}\.[\w-]{8,})|((?:accessToken|access_token|token:|Session ID is)[""'=: ]*)[^\s""',]{16,}",
        RegexOptions.IgnoreCase | RegexOptions.Compiled);

    public static async Task<string?> SendAsync(string gameDirectory, string minecraftVersion, int exitCode)
    {
        try
        {
            var fresh = DateTime.Now.AddMinutes(-10);
            string? Newest(string dir, string pattern) => Directory.Exists(dir)
                ? Directory.GetFiles(dir, pattern).Where(f => File.GetLastWriteTime(f) > fresh).OrderByDescending(File.GetLastWriteTime).FirstOrDefault()
                : null;
            var payload = new
            {
                launcher = Program.Version,
                minecraft = minecraftVersion,
                exitCode,
                crash = Read(Newest(Path.Combine(gameDirectory, "crash-reports"), "*.txt"), tail: false),
                hsErr = Read(Newest(gameDirectory, "hs_err_pid*.log"), tail: false),
                log = Read(Path.Combine(gameDirectory, "logs", "latest.log"), tail: true),
            };
            using var request = new HttpRequestMessage(HttpMethod.Post, Endpoint) { Content = JsonContent.Create(payload) };
            request.Headers.UserAgent.ParseAdd($"TheLadsLauncher/{Program.Version}");
            using var response = await Http.SendAsync(request).ConfigureAwait(false);
            return response.IsSuccessStatusCode ? null : $"the server answered {(int)response.StatusCode}";
        }
        catch (Exception ex)
        {
            return ex.Message;
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
        return Secrets.Replace(text, m => m.Groups[1].Success ? "<token>" : m.Groups[2].Value + "<token>");
    }
}
