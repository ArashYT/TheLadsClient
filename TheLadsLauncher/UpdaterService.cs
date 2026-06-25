using System;
using System.IO;
using System.Net.Http;
using System.Threading.Tasks;
using System.Diagnostics;
using Avalonia.Threading;

namespace TheLadsLauncher;

public class UpdaterService
{
    public static async Task DownloadAndApplyUpdateAsync(string downloadUrl, Action<double> onProgress)
    {
        try
        {
            using var client = new HttpClient();
            client.DefaultRequestHeaders.UserAgent.Add(new System.Net.Http.Headers.ProductInfoHeaderValue("TheLadsLauncher", "Updater"));
            var response = await client.GetAsync(downloadUrl, HttpCompletionOption.ResponseHeadersRead);
            response.EnsureSuccessStatusCode();

            var totalBytes = response.Content.Headers.ContentLength ?? -1L;
            var canReportProgress = totalBytes != -1;

            var tempExePath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "TheLadsLauncher_update.exe");
            
            using (var stream = await response.Content.ReadAsStreamAsync())
            using (var fileStream = new FileStream(tempExePath, FileMode.Create, FileAccess.Write, FileShare.None))
            {
                var buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;
                
                while ((bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length)) > 0)
                {
                    await fileStream.WriteAsync(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    if (canReportProgress)
                    {
                        var progress = (double)totalRead / totalBytes * 100;
                        Dispatcher.UIThread.Post(() => onProgress(progress));
                    }
                }
            }

            ApplyUpdateAndRestart(tempExePath);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Update failed: {ex.Message}");
        }
    }

    private static void ApplyUpdateAndRestart(string tempExePath)
    {
        var currentExePath = Process.GetCurrentProcess().MainModule.FileName;
        var batPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "update.bat");

        string batContent = $@"
@echo off
timeout /t 2 /nobreak >nul
del ""{currentExePath}""
move /y ""{tempExePath}"" ""{currentExePath}""
start """" ""{currentExePath}""
del ""%~f0""
";
        File.WriteAllText(batPath, batContent);

        var processInfo = new ProcessStartInfo
        {
            FileName = batPath,
            UseShellExecute = true,
            CreateNoWindow = true,
            WindowStyle = ProcessWindowStyle.Hidden
        };

        Process.Start(processInfo);
        Environment.Exit(0);
    }
}
