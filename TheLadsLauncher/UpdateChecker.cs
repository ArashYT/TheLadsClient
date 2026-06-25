using System;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Threading.Tasks;
using System.Linq;

namespace TheLadsLauncher;

public class UpdateInfo
{
    public string LatestVersion { get; set; } = "";
    public string DownloadUrl { get; set; } = "";
    public string Changelog { get; set; } = "";
}

public class UpdateChecker
{
    public static async Task<UpdateInfo?> CheckForUpdatesAsync(string repoName, string currentVersion)
    {
        try
        {
            using var client = new HttpClient();
            client.Timeout = TimeSpan.FromSeconds(10);
            client.DefaultRequestHeaders.UserAgent.Add(new ProductInfoHeaderValue("TheLadsLauncher", currentVersion));
            
            // Fetch all releases (includes pre-releases), API returns newest first
            var response = await client.GetStringAsync($"https://api.github.com/repos/{repoName}/releases");
            using var document = JsonDocument.Parse(response);
            var rootArray = document.RootElement;
            
            if (rootArray.ValueKind != JsonValueKind.Array || rootArray.GetArrayLength() == 0) return null;
            var root = rootArray[0];
            
            string tagName = root.GetProperty("tag_name").GetString() ?? "";
            string body = root.GetProperty("body").GetString() ?? "";
            
            // Normalize version strings by removing 'v' prefix
            string latestVersion = tagName.TrimStart('v');
            string currentVerClean = currentVersion.TrimStart('v');
            
            if (IsNewerVersion(currentVerClean, latestVersion))
            {
                var assets = root.GetProperty("assets").EnumerateArray();
                string downloadUrl = "";
                foreach (var asset in assets)
                {
                    string name = asset.GetProperty("name").GetString() ?? "";
                    if (name.Equals("TheLadsLauncher.exe", StringComparison.OrdinalIgnoreCase))
                    {
                        downloadUrl = asset.GetProperty("browser_download_url").GetString() ?? "";
                        break; 
                    }
                }
                
                if (!string.IsNullOrEmpty(downloadUrl))
                {
                    return new UpdateInfo
                    {
                        LatestVersion = latestVersion,
                        DownloadUrl = downloadUrl,
                        Changelog = body
                    };
                }
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
