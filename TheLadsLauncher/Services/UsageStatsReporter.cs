using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public sealed record UsageHeartbeatPayload(
    [property: JsonPropertyName("install_id")] string InstallId,
    [property: JsonPropertyName("launcher_version")] string LauncherVersion,
    [property: JsonPropertyName("client_versions_used")] IReadOnlyList<string> ClientVersionsUsed,
    [property: JsonPropertyName("os")] string Os
);

/// <summary>
/// Sends an anonymous heartbeat on startup and periodically while the launcher is open
/// to https://bot.arashyt.ca/usage. Contains no player names, Minecraft UUIDs, or IP addresses.
/// Fire-and-forget, 5 second timeout, never blocks startup, never throws.
/// </summary>
public static class UsageStatsReporter
{
    public const string Endpoint = "https://bot.arashyt.ca/usage";
    private static readonly HttpClient DefaultHttp = new() { Timeout = TimeSpan.FromSeconds(5) };

    public static UsageHeartbeatPayload? BuildPayload(
        LauncherSettings settings,
        IEnumerable<string>? clientVersions = null,
        string? launcherVersion = null,
        string? os = null,
        bool? isSandbox = null)
    {
        // QA/sandbox runs (THELADS_DIR set) are not real installs.
        bool isSandboxEffective = isSandbox ?? !string.IsNullOrEmpty(Environment.GetEnvironmentVariable("THELADS_DIR"));
        if (settings == null || !settings.SendAnonymousUsageStats || isSandboxEffective)
        {
            return null;
        }

        string installId = settings.GetOrCreateInstallId();
        string version = launcherVersion ?? Program.Version ?? settings.LauncherVersion ?? "1.0.0";
        string systemOs = os ?? (OperatingSystem.IsWindows() ? "windows" : OperatingSystem.IsMacOS() ? "macos" : OperatingSystem.IsLinux() ? "linux" : "unknown");

        var versions = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        if (clientVersions != null)
        {
            foreach (var v in clientVersions)
            {
                if (!string.IsNullOrWhiteSpace(v)) versions.Add(v.Trim());
            }
        }
        else
        {
            if (settings.ClientVersionsUsed != null)
            {
                foreach (var v in settings.ClientVersionsUsed)
                {
                    if (!string.IsNullOrWhiteSpace(v)) versions.Add(v.Trim());
                }
            }

            // Fallback: check configured profiles if no versions recorded yet
            if (versions.Count == 0 && ProfileService.Instance != null)
            {
                try
                {
                    foreach (var p in ProfileService.Instance.GetProfiles())
                    {
                        if (!string.IsNullOrWhiteSpace(p.MinecraftVersion))
                        {
                            versions.Add(p.MinecraftVersion.Trim());
                        }
                    }
                }
                catch { }
            }
        }

        return new UsageHeartbeatPayload(
            InstallId: installId,
            LauncherVersion: version,
            ClientVersionsUsed: versions.OrderBy(v => v).ToList(),
            Os: systemOs
        );
    }

    public static void RecordClientVersionUsed(LauncherSettings settings, string? minecraftVersion)
    {
        if (settings == null || string.IsNullOrWhiteSpace(minecraftVersion)) return;
        string clean = minecraftVersion.Trim();
        lock (settings.ClientVersionsUsed)
        {
            if (!settings.ClientVersionsUsed.Contains(clean, StringComparer.OrdinalIgnoreCase))
            {
                settings.ClientVersionsUsed.Add(clean);
                settings.Save();
            }
        }
    }

    public static async Task<bool> SendHeartbeatAsync(
        LauncherSettings settings,
        HttpClient? httpClient = null,
        CancellationToken cancellationToken = default,
        bool? isSandbox = null)
    {
        try
        {
            var payload = BuildPayload(settings, isSandbox: isSandbox);
            if (payload == null) return false;

            var client = httpClient ?? DefaultHttp;
            using var cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            cts.CancelAfter(TimeSpan.FromSeconds(5));

            using var request = new HttpRequestMessage(HttpMethod.Post, Endpoint)
            {
                Content = JsonContent.Create(payload)
            };
            request.Headers.UserAgent.ParseAdd($"TheLadsLauncher/{payload.LauncherVersion}");

            using var response = await client.SendAsync(request, cts.Token).ConfigureAwait(false);
            return response.IsSuccessStatusCode;
        }
        catch
        {
            // Never throws: fire-and-forget
            return false;
        }
    }
}
