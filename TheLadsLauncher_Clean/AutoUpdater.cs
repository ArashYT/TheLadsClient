using System;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using System.Diagnostics;

namespace TheLadsLauncher;

public static class AutoUpdater
{
    public static string GithubRepo { get; set; } = "ArashYT/TheLadsClient";
    public static bool IsUpdateReady { get; private set; } = false;
    public static string UpdateExePath { get; private set; } = "";
    public static string LatestVersionString { get; private set; } = "";

    public static async Task<UpdateInfo?> CheckForUpdatesAsync(string currentVersion, string customUpdateUrl = "")
    {
        string url = !string.IsNullOrWhiteSpace(customUpdateUrl)
            ? customUpdateUrl
            : $"https://api.github.com/repos/{GithubRepo}/releases/latest";

        try
        {
            using var client = new HttpClient();
            client.DefaultRequestHeaders.Add("User-Agent", "TheLadsLauncher");
            client.Timeout = TimeSpan.FromSeconds(8);
            var response = await client.GetStringAsync(url);

            using var doc = JsonDocument.Parse(response);
            var root = doc.RootElement;

            string latestVer = "";
            string downloadUrl = "";
            string changelog = "";

            if (url.Contains("api.github.com"))
            {
                // Parse GitHub API format
                if (root.TryGetProperty("tag_name", out var tagEl))
                {
                    latestVer = tagEl.GetString()?.TrimStart('v') ?? "";
                }
                if (root.TryGetProperty("body", out var bodyEl))
                {
                    changelog = bodyEl.GetString() ?? "";
                }
                if (root.TryGetProperty("assets", out var assetsEl) && assetsEl.ValueKind == JsonValueKind.Array)
                {
                    foreach (var asset in assetsEl.EnumerateArray())
                    {
                        if (asset.TryGetProperty("name", out var nameEl) && nameEl.GetString() == "TheLadsLauncher.exe")
                        {
                            if (asset.TryGetProperty("browser_download_url", out var dlEl))
                            {
                                downloadUrl = dlEl.GetString() ?? "";
                                break;
                            }
                        }
                    }
                }
            }
            else
            {
                // Fallback to custom update URL format
                if (root.TryGetProperty("latestVersion", out var lv)) latestVer = lv.GetString() ?? "";
                if (root.TryGetProperty("downloadUrl", out var du)) downloadUrl = du.GetString() ?? "";
                if (root.TryGetProperty("changelog", out var cl)) changelog = cl.GetString() ?? "";
            }

            if (!string.IsNullOrEmpty(latestVer) && IsNewerVersion(currentVersion, latestVer))
            {
                return new UpdateInfo
                {
                    LatestVersion = latestVer,
                    DownloadUrl = downloadUrl,
                    Changelog = changelog
                };
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

    public static async Task<bool> DownloadUpdateAsync(string downloadUrl, string targetVersion)
    {
        if (string.IsNullOrEmpty(downloadUrl)) return false;
        try
        {
            string tempDir = Path.Combine(Path.GetTempPath(), "TheLadsLauncherUpdates");
            if (!Directory.Exists(tempDir)) Directory.CreateDirectory(tempDir);

            string targetFile = Path.Combine(tempDir, "update.exe");
            using var client = new HttpClient();
            client.Timeout = TimeSpan.FromMinutes(5);
            var bytes = await client.GetByteArrayAsync(downloadUrl);
            await File.WriteAllBytesAsync(targetFile, bytes);

            UpdateExePath = targetFile;
            LatestVersionString = targetVersion;
            IsUpdateReady = true;
            return true;
        }
        catch { }
        return false;
    }

    public static void ApplyUpdateAndRestart()
    {
        if (!IsUpdateReady || string.IsNullOrEmpty(UpdateExePath) || !File.Exists(UpdateExePath)) return;

        try
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string currentExe = Process.GetCurrentProcess().MainModule?.FileName ?? Path.Combine(baseDir, "TheLadsLauncher.exe");
            string updateExe = Path.Combine(baseDir, "update.exe");

            // Copy temp file to app dir as update.exe
            File.Copy(UpdateExePath, updateExe, true);

            // Write update.bat
            string batPath = Path.Combine(baseDir, "update.bat");
            string batContent = $@"@echo off
timeout /t 1 /nobreak > nul
copy /y ""{updateExe}"" ""{currentExe}""
del ""{updateExe}""
start """" ""{currentExe}""
del ""%~f0""
";
            File.WriteAllText(batPath, batContent);

            // Run update.bat in background
            var startInfo = new ProcessStartInfo
            {
                FileName = batPath,
                CreateNoWindow = true,
                UseShellExecute = false,
                WorkingDirectory = baseDir
            };
            Process.Start(startInfo);

            // Exit launcher
            Environment.Exit(0);
        }
        catch { }
    }
}
