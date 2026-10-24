using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public sealed record LogUploadResult(bool Success, string? Url, string? ErrorMessage);

/// <summary>
/// User-facing one-click log sanitizer and uploader (e.g. to mclo.gs), returning a shareable link.
/// </summary>
public static class LogShareService
{
    private const string MclogsEndpoint = "https://api.mclo.gs/1/log";
    private static readonly HttpClient DefaultHttp = new() { Timeout = TimeSpan.FromSeconds(20) };
    private static readonly Regex IpRegex = new(@"\b(?:\d{1,3}\.){3}\d{1,3}\b", RegexOptions.Compiled);

    /// <summary>
    /// Scrubs usernames, user folders, access tokens, environment variables and raw IP addresses from the log text.
    /// </summary>
    public static string SanitizeLog(string rawLog)
    {
        if (string.IsNullOrEmpty(rawLog)) return "";
        string scrubbed = CrashReporter.Scrub(rawLog);
        // Replace IPv4 addresses (preserving local 127.0.0.1 and 0.0.0.0)
        scrubbed = IpRegex.Replace(scrubbed, m =>
        {
            var ip = m.Value;
            if (ip is "127.0.0.1" or "0.0.0.0") return ip;
            return "<ip-hidden>";
        });
        return scrubbed;
    }

    /// <summary>
    /// Sanitizes and uploads the log to a public paste service (mclo.gs) returning a share link.
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
