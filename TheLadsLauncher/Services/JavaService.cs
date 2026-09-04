using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public class JavaService : IJavaService
{
    private static JavaService? _instance;
    public static JavaService Instance => _instance ??= new JavaService(PathService.Instance);

    private readonly IPathService _pathService;
    private readonly HttpClient _httpClient;

    public JavaService(IPathService pathService, HttpClient? httpClient = null)
    {
        _pathService = pathService;
        _httpClient = httpClient ?? new HttpClient();
    }

    public async Task<string?> DetectInstalledJavaAsync(int majorVersion)
    {
        return await Task.Run(() =>
        {
            // 1. Check managed runtime directory first
            var runtimeDir = _pathService.GetJavaRuntimeDirectory(majorVersion);
            if (Directory.Exists(runtimeDir))
            {
                var exe = FindJavaExeInDir(runtimeDir);
                if (!string.IsNullOrEmpty(exe) && File.Exists(exe))
                {
                    var ver = GetJavaMajorVersion(exe);
                    if (ver == majorVersion) return exe;
                }
            }

            // 2. Scan system paths
            var candidates = ScanAllSystemJavas();
            foreach (var cand in candidates)
            {
                var ver = GetJavaMajorVersion(cand);
                if (ver == majorVersion) return cand;
            }

            return null;
        });
    }

    public async Task<string> EnsureJavaAsync(
        int majorVersion,
        IProgress<double>? progress = null,
        Action<string>? statusCallback = null,
        CancellationToken cancellationToken = default)
    {
        statusCallback?.Invoke($"Detecting Java {majorVersion}...");
        var detected = await DetectInstalledJavaAsync(majorVersion);
        if (!string.IsNullOrEmpty(detected))
        {
            statusCallback?.Invoke($"Found Java {majorVersion}: {detected}");
            return detected;
        }

        statusCallback?.Invoke($"Java {majorVersion} not found. Preparing automatic download from Adoptium...");
        return await DownloadAndInstallAdoptiumJavaAsync(majorVersion, progress, statusCallback, cancellationToken);
    }

    public async Task<string> DownloadAndInstallAdoptiumJavaAsync(
        int majorVersion,
        IProgress<double>? progress = null,
        Action<string>? statusCallback = null,
        CancellationToken cancellationToken = default)
    {
        var targetRuntimeDir = _pathService.GetJavaRuntimeDirectory(majorVersion);
        var tempZip = Path.Combine(_pathService.RuntimesDirectory, $"temp_jdk_{majorVersion}.zip");

        try
        {
            _pathService.EnsureDirectories();
            statusCallback?.Invoke($"Connecting to Adoptium REST API for Java {majorVersion}...");

            var apiUrl = $"https://api.adoptium.net/v3/assets/latest/{majorVersion}/hotspot?os=windows&architecture=x64&image_type=jdk";
            string downloadUrl = "";

            try
            {
                var responseJson = await _httpClient.GetStringAsync(apiUrl, cancellationToken);
                using var doc = JsonDocument.Parse(responseJson);
                var root = doc.RootElement;
                if (root.ValueKind == JsonValueKind.Array && root.GetArrayLength() > 0)
                {
                    var first = root[0];
                    if (first.TryGetProperty("binary", out var binary) &&
                        binary.TryGetProperty("package", out var pkg) &&
                        pkg.TryGetProperty("link", out var linkElem))
                    {
                        downloadUrl = linkElem.GetString() ?? "";
                    }
                }
            }
            catch (Exception ex)
            {
                statusCallback?.Invoke($"Adoptium API query notice: {ex.Message}. Using direct fallback mirror...");
            }

            if (string.IsNullOrEmpty(downloadUrl))
            {
                downloadUrl = majorVersion switch
                {
                    21 => "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip",
                    25 => "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip",
                    _ => throw new InvalidOperationException($"Unsupported Java version {majorVersion} for automatic download.")
                };
            }

            statusCallback?.Invoke($"Downloading Java {majorVersion} runtime...");
            if (File.Exists(tempZip)) File.Delete(tempZip);

            using (var response = await _httpClient.GetAsync(downloadUrl, HttpCompletionOption.ResponseHeadersRead, cancellationToken))
            {
                response.EnsureSuccessStatusCode();
                var totalBytes = response.Content.Headers.ContentLength ?? -1L;

                using (var contentStream = await response.Content.ReadAsStreamAsync(cancellationToken))
                using (var fileStream = new FileStream(tempZip, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true))
                {
                    var buffer = new byte[81920];
                    long totalRead = 0;
                    int bytesRead;

                    while ((bytesRead = await contentStream.ReadAsync(buffer, 0, buffer.Length, cancellationToken)) > 0)
                    {
                        await fileStream.WriteAsync(buffer, 0, bytesRead, cancellationToken);
                        totalRead += bytesRead;

                        if (totalBytes > 0)
                        {
                            var pct = (double)totalRead / totalBytes * 100.0;
                            progress?.Report(pct);
                            statusCallback?.Invoke($"Downloading Java {majorVersion} ({pct:F0}%)...");
                        }
                    }
                }
            }

            statusCallback?.Invoke($"Extracting Java {majorVersion} into managed directory...");
            if (Directory.Exists(targetRuntimeDir))
            {
                try { Directory.Delete(targetRuntimeDir, true); } catch { }
            }
            Directory.CreateDirectory(targetRuntimeDir);

            await Task.Run(() =>
            {
                ZipFile.ExtractToDirectory(tempZip, targetRuntimeDir, true);
            }, cancellationToken);

            try { File.Delete(tempZip); } catch { }

            var exePath = FindJavaExeInDir(targetRuntimeDir);
            if (string.IsNullOrEmpty(exePath) || !File.Exists(exePath))
            {
                throw new FileNotFoundException($"Could not locate java.exe in extracted archive at {targetRuntimeDir}");
            }

            statusCallback?.Invoke($"Java {majorVersion} installed successfully: {exePath}");
            return exePath;
        }
        catch (Exception)
        {
            if (File.Exists(tempZip))
            {
                try { File.Delete(tempZip); } catch { }
            }
            throw;
        }
    }

    public IReadOnlyList<string> ScanAllSystemJavas()
    {
        var found = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        // 1. JAVA_HOME
        var javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrEmpty(javaHome))
        {
            var exe = Path.Combine(javaHome, "bin", "java.exe");
            if (File.Exists(exe)) found.Add(exe);
        }

        // 2. PATH
        var pathEnv = Environment.GetEnvironmentVariable("PATH");
        if (!string.IsNullOrEmpty(pathEnv))
        {
            foreach (var part in pathEnv.Split(';', StringSplitOptions.RemoveEmptyEntries))
            {
                var exe = Path.Combine(part.Trim(), "java.exe");
                if (File.Exists(exe)) found.Add(exe);
            }
        }

        // 3. Known Program Files and LocalAppData directories
        var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        var programFilesX86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
        var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);

        var searchRoots = new List<string>
        {
            Path.Combine(programFiles, "Eclipse Adoptium"),
            Path.Combine(programFiles, "Java"),
            Path.Combine(programFiles, "Microsoft"),
            Path.Combine(programFiles, "Zulu"),
            Path.Combine(programFiles, "BellSoft"),
            Path.Combine(programFiles, "Semeru"),
            Path.Combine(programFilesX86, "Java"),
            Path.Combine(localAppData, "Programs", "Eclipse Adoptium")
        };

        foreach (var root in searchRoots)
        {
            if (!Directory.Exists(root)) continue;
            try
            {
                foreach (var sub in Directory.GetDirectories(root))
                {
                    var exe = Path.Combine(sub, "bin", "java.exe");
                    if (File.Exists(exe)) found.Add(exe);
                }
            }
            catch { }
        }

        return found.ToList();
    }

    public int? GetJavaMajorVersion(string javaExecutablePath)
    {
        if (!File.Exists(javaExecutablePath)) return null;

        // Method 1: Fast release file inspection (avoids process spawn)
        try
        {
            var binDir = Path.GetDirectoryName(javaExecutablePath);
            if (!string.IsNullOrEmpty(binDir))
            {
                var jdkRoot = Path.GetDirectoryName(binDir);
                if (!string.IsNullOrEmpty(jdkRoot))
                {
                    var releaseFile = Path.Combine(jdkRoot, "release");
                    if (File.Exists(releaseFile))
                    {
                        var lines = File.ReadAllLines(releaseFile);
                        foreach (var line in lines)
                        {
                            if (line.StartsWith("JAVA_VERSION=", StringComparison.OrdinalIgnoreCase))
                            {
                                var val = line.Substring("JAVA_VERSION=".Length).Trim('\"', '\'', ' ');
                                var match = Regex.Match(val, @"^(?:1\.)?(?<major>\d+)");
                                if (match.Success && int.TryParse(match.Groups["major"].Value, out int maj))
                                {
                                    return maj;
                                }
                            }
                        }
                    }
                }
            }
        }
        catch { }

        // Method 2: Process execution (java.exe -version)
        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = javaExecutablePath,
                Arguments = "-version",
                RedirectStandardError = true,
                RedirectStandardOutput = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };

            using var process = Process.Start(psi);
            if (process != null)
            {
                string output = process.StandardError.ReadToEnd() + " " + process.StandardOutput.ReadToEnd();
                process.WaitForExit(3000);

                var match = Regex.Match(output, @"version ""(?:1\.)?(?<major>\d+)");
                if (match.Success && int.TryParse(match.Groups["major"].Value, out int ver))
                {
                    return ver;
                }
            }
        }
        catch { }

        return null;
    }

    private static string? FindJavaExeInDir(string dir)
    {
        var direct = Path.Combine(dir, "bin", "java.exe");
        if (File.Exists(direct)) return direct;

        try
        {
            foreach (var sub in Directory.GetDirectories(dir))
            {
                var subDirect = Path.Combine(sub, "bin", "java.exe");
                if (File.Exists(subDirect)) return subDirect;
            }
        }
        catch { }

        return null;
    }
}
