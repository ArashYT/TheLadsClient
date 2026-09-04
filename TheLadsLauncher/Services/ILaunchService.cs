using System;
using System.Diagnostics;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Models;

namespace TheLadsLauncher.Services;

public interface ILaunchService
{
    Task<Process?> LaunchAsync(
        LauncherProfile profile,
        string username,
        LauncherSettings settings,
        IProgress<double>? progress = null,
        Action<string>? statusCallback = null,
        CancellationToken cancellationToken = default);
}
