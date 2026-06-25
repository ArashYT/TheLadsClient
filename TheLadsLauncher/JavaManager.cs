using System;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;

namespace TheLadsLauncher;

public class JavaManager
{
    private static readonly HttpClient _httpClient = new HttpClient();

    static JavaManager()
    {
        _httpClient.DefaultRequestHeaders.Add("User-Agent", "TheLadsLauncher/1.0");
    }

    /// <summary>
    /// Downloads and extracts the required Java version, returning the path to java.exe.
    /// </summary>
    public static async Task<string> DownloadJavaAsync(int requiredVersion, Action<string> progressCallback)
    {
        string javaDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), 
            "The Lads Client", "Java", $"jdk-{requiredVersion}");

        string expectedJavaExe = Path.Combine(javaDir, "bin", "java.exe");

        // If it's already downloaded, return it immediately
        if (File.Exists(expectedJavaExe))
        {
            return expectedJavaExe;
        }

        // 1. Get the download link from Adoptium API
        progressCallback?.Invoke($"Finding latest Java {requiredVersion} release...");
        string apiUrl = $"https://api.adoptium.net/v3/assets/latest/{requiredVersion}/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse";
        
        string downloadUrl = null;
        try
        {
            var responseJson = await _httpClient.GetStringAsync(apiUrl);
            using var doc = JsonDocument.Parse(responseJson);
            var root = doc.RootElement;
            if (root.ValueKind == JsonValueKind.Array && root.GetArrayLength() > 0)
            {
                var pkg = root[0].GetProperty("binary").GetProperty("package");
                downloadUrl = pkg.GetProperty("link").GetString();
            }
        }
        catch (Exception ex)
        {
            throw new Exception($"Failed to find Java {requiredVersion} download link: {ex.Message}");
        }

        if (string.IsNullOrEmpty(downloadUrl))
        {
            throw new Exception($"Could not parse Java {requiredVersion} download link from Adoptium API.");
        }

        // 2. Download the zip
        progressCallback?.Invoke($"Downloading Java {requiredVersion} (this may take a minute)...");
        string zipPath = Path.Combine(Path.GetTempPath(), $"java{requiredVersion}.zip");
        
        try
        {
            using var response = await _httpClient.GetAsync(downloadUrl, HttpCompletionOption.ResponseHeadersRead);
            response.EnsureSuccessStatusCode();
            using var fs = new FileStream(zipPath, FileMode.Create, FileAccess.Write, FileShare.None);
            await response.Content.CopyToAsync(fs);
        }
        catch (Exception ex)
        {
            if (File.Exists(zipPath)) File.Delete(zipPath);
            throw new Exception($"Failed to download Java {requiredVersion}: {ex.Message}");
        }

        // 3. Extract the zip
        progressCallback?.Invoke($"Extracting Java {requiredVersion}...");
        try
        {
            if (Directory.Exists(javaDir))
            {
                Directory.Delete(javaDir, true);
            }
            Directory.CreateDirectory(javaDir);

            // Extract to a temp folder first because the zip contains a root folder (e.g., jdk-21.0.2+13-jre)
            string extractTemp = Path.Combine(Path.GetTempPath(), $"java{requiredVersion}_ext");
            if (Directory.Exists(extractTemp)) Directory.Delete(extractTemp, true);
            
            ZipFile.ExtractToDirectory(zipPath, extractTemp);

            // Find the actual root directory inside the extracted folder
            var extractedDirs = Directory.GetDirectories(extractTemp);
            if (extractedDirs.Length > 0)
            {
                string innerRoot = extractedDirs[0];
                // Move contents from innerRoot to javaDir
                foreach (var dir in Directory.GetDirectories(innerRoot, "*", SearchOption.AllDirectories))
                {
                    Directory.CreateDirectory(dir.Replace(innerRoot, javaDir));
                }
                foreach (var file in Directory.GetFiles(innerRoot, "*.*", SearchOption.AllDirectories))
                {
                    File.Copy(file, file.Replace(innerRoot, javaDir), true);
                }
            }

            // Cleanup
            Directory.Delete(extractTemp, true);
            File.Delete(zipPath);
        }
        catch (Exception ex)
        {
            throw new Exception($"Failed to extract Java {requiredVersion}: {ex.Message}");
        }

        // 4. Verify
        if (!File.Exists(expectedJavaExe))
        {
            throw new Exception($"Java {requiredVersion} extraction succeeded, but java.exe was not found at {expectedJavaExe}");
        }

        return expectedJavaExe;
    }
}
