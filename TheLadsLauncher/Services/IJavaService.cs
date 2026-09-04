using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

public interface IJavaService
{
    Task<string?> DetectInstalledJavaAsync(int majorVersion);
    Task<string> EnsureJavaAsync(int majorVersion, IProgress<double>? progress = null, Action<string>? statusCallback = null, CancellationToken cancellationToken = default);
    Task<string> DownloadAndInstallAdoptiumJavaAsync(int majorVersion, IProgress<double>? progress = null, Action<string>? statusCallback = null, CancellationToken cancellationToken = default);
    IReadOnlyList<string> ScanAllSystemJavas();
    int? GetJavaMajorVersion(string javaExecutablePath);
}
