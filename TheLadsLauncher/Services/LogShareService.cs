using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public sealed record LogUploadResult(bool Success, string? Url, string? ErrorMessage);

/// <summary>
/// User-facing one-click log sanitizer and uploader (to public mclo.gs), returning a shareable link.
/// </summary>
public static class LogShareService
{
    private const string MclogsEndpoint = "https://api.mclo.gs/1/log";
    private static readonly HttpClient DefaultHttp = new() { Timeout = TimeSpan.FromSeconds(20) };

    private static readonly Regex EmailRegex = new(
        @"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}",
        RegexOptions.Compiled);

    private static readonly Regex Ipv4Regex = new(
        @"\b(?:\d{1,3}\.){3}\d{1,3}\b",
        RegexOptions.Compiled);

    // Matches colon-separated hexadecimal candidate tokens for IPv6 evaluation
    private static readonly Regex Ipv6CandidateRegex = new(
        @"(?:(?<=\s|^|[\[\(\""',])(?:[0-9a-fA-F]{1,4}:){1,7}[0-9a-fA-F]{1,4}(?=\s|$|[\]\)\""',])|(?<=\s|^|[\[\(\""',])::(?:[0-9a-fA-F]{1,4})?(?=\s|$|[\]\)\""',])|(?<=\s|^|[\[\(\""',])[0-9a-fA-F]{1,4}::(?:[0-9a-fA-F]{1,4}:?)*(?=\s|$|[\]\)\""',]))",
        RegexOptions.Compiled);

    private static readonly Regex BearerTokenRegex = new(
        @"(?i)\b(bearer\s+)[a-zA-Z0-9_\-\.]+",
        RegexOptions.Compiled);

    private static readonly Regex AccessTokenArgRegex = new(
        @"(?i)(--accessToken\s+)[^\s]+",
        RegexOptions.Compiled);

    private static readonly Regex SessionIdArgRegex = new(
        @"(?i)(-Dminecraft\.session\.id=)[^\s]+",
        RegexOptions.Compiled);

    private static readonly Regex WindowsHomeRegex = new(
        @"(?i)[a-zA-Z]:\\Users\\[^\s\\/""',]+",
        RegexOptions.Compiled);

    private static readonly Regex LinuxHomeRegex = new(
        @"(?i)/home/[^\s/\\""',]+",
        RegexOptions.Compiled);

    private static readonly Regex MacHomeRegex = new(
        @"(?i)/Users/[^\s/\\""',]+",
        RegexOptions.Compiled);

    /// <summary>
    /// Scrubs access tokens, session IDs, bearer tokens, --accessToken arguments, IPv4 and IPv6 addresses,
    /// email addresses, and user home directories (Windows, Linux, macOS).
    /// The player's UUID is explicitly preserved for support identification.
    /// </summary>
    public static string SanitizeLog(string rawLog)
    {
        if (string.IsNullOrEmpty(rawLog)) return "";

        // First apply CrashReporter's base scrubbing (current user profile, environment usernames, JWT tokens, etc.)
        string scrubbed = CrashReporter.Scrub(rawLog);

        // Scrub email addresses
        scrubbed = EmailRegex.Replace(scrubbed, "<email-hidden>");

        // Scrub bearer tokens
        scrubbed = BearerTokenRegex.Replace(scrubbed, "$1<token>");

        // Scrub command-line access tokens and session IDs
        scrubbed = AccessTokenArgRegex.Replace(scrubbed, "$1<token>");
        scrubbed = SessionIdArgRegex.Replace(scrubbed, "$1<token>");

        // Scrub user paths across Windows, Linux, and macOS
        scrubbed = WindowsHomeRegex.Replace(scrubbed, "%USERPROFILE%");
        scrubbed = LinuxHomeRegex.Replace(scrubbed, "/home/<user>");
        scrubbed = MacHomeRegex.Replace(scrubbed, "/Users/<user>");

        // Scrub IPv4 addresses (preserving local 127.0.0.1 and 0.0.0.0)
        scrubbed = Ipv4Regex.Replace(scrubbed, m =>
        {
            var ip = m.Value;
            if (ip is "127.0.0.1" or "0.0.0.0") return ip;
            return "<ip-hidden>";
        });

        // Scrub IPv6 addresses (preserving loopback ::1) using IPAddress parser to protect timestamps
        scrubbed = Ipv6CandidateRegex.Replace(scrubbed, m =>
        {
            string candidate = m.Value.Trim();
            if (IPAddress.TryParse(candidate, out var ip) && ip.AddressFamily == AddressFamily.InterNetworkV6)
            {
                if (IPAddress.IsLoopback(ip)) return candidate;
                return "<ip-hidden>";
            }
            return m.Value;
        });

        return scrubbed;
    }

    /// <summary>
    /// Sanitizes and uploads the log to public paste service (mclo.gs) returning a share link.
    /// </summary>
    public static async Task<LogUploadResult> UploadLogAsync(string rawLog, HttpClient? httpClient = null)
    {
        string clean = SanitizeLog(rawLog);
        if (string.IsNullOrWhiteSpace(clean))
            return new LogUploadResult(false, null, "Log is empty.");

        var http = httpClient ?? DefaultHttp;
        try
        {
            var content = new FormUrlEncodedContent(new[]
            {
                new KeyValuePair<string, string>("content", clean)
            });

            using var response = await http.PostAsync(MclogsEndpoint, content);
            string json = await response.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;

            if (root.TryGetProperty("success", out var successProp) && successProp.GetBoolean())
            {
                string? url = root.TryGetProperty("url", out var urlProp) ? urlProp.GetString() : null;
                return new LogUploadResult(true, url, null);
            }

            string? err = root.TryGetProperty("error", out var errProp) ? errProp.GetString() : "Unknown upload error";
            return new LogUploadResult(false, null, err);
        }
        catch (Exception ex)
        {
            return new LogUploadResult(false, null, ex.Message);
        }
    }
}
