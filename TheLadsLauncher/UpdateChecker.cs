using System;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using Avalonia.Threading;

namespace TheLadsLauncher;

public class UpdateInfo
{
    public string LatestVersion { get; set; } = "1.0.0";
    public string DownloadUrl { get; set; } = "";
    public string Changelog { get; set; } = "";
}

public class UpdateChecker
{
    public static async Task<UpdateInfo?> CheckForUpdatesAsync(string updateUrl, string currentVersion)
    {
        if (string.IsNullOrWhiteSpace(updateUrl))
            return null;

        try
        {
            using var client = new HttpClient();
            client.Timeout = TimeSpan.FromSeconds(5);
            var response = await client.GetStringAsync(updateUrl);
            var info = JsonSerializer.Deserialize<UpdateInfo>(response, new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
            
            if (info != null && IsNewerVersion(currentVersion, info.LatestVersion))
            {
                return info;
            }
        }
        catch { }
        return null;
    }

    private static bool IsNewerVersion(string current, string latest)
    {
        if (Version.TryParse(current, out var v1) && Version.TryParse(latest, out var v2))
        {
            return v2 > v1;
        }
        return false;
    }
}
