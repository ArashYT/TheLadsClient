using System;
using System.Threading;
using System.Threading.Tasks;
using Velopack;
using Velopack.Sources;

namespace TheLadsLauncher;

public interface ILauncherUpdateBackend
{
    bool IsInstalled { get; }
    string? PendingVersion { get; }
    Task<bool> DownloadLatestAsync(Action<string> report);
    void ApplyAndRestart();
}

public sealed class VelopackUpdateBackend : ILauncherUpdateBackend
{
    public const string RepositoryUrl = "https://github.com/ArashYT/TheLadsClient";
    private readonly UpdateManager _manager;
    public VelopackUpdateBackend() : this(new UpdateManager(new GithubSource(RepositoryUrl, null, false))) { }
    public VelopackUpdateBackend(UpdateManager manager) => _manager = manager;
    public bool IsInstalled => _manager.IsInstalled;
    public string? PendingVersion => _manager.UpdatePendingRestart?.Version.ToString();

    public async Task<bool> DownloadLatestAsync(Action<string> report)
    {
        var update = await _manager.CheckForUpdatesAsync();
        if (update == null) return false;
        var version = update.TargetFullRelease.Version;
        await _manager.DownloadUpdatesAsync(update, percent => report($"Downloading v{version}: {percent}%"));
        return true;
    }

    public void ApplyAndRestart() => _manager.ApplyUpdatesAndRestart(_manager.UpdatePendingRestart
        ?? throw new InvalidOperationException("No verified update is ready."));
}

// Startup and the timer share this coordinator. Only verified packages are applied.
public sealed class AutoUpdater
{
    private readonly ILauncherUpdateBackend _backend;
    private readonly SemaphoreSlim _gate = new(1, 1);
    private DateTimeOffset _nextCheck;
    private DateTimeOffset _retryAfter;
    public AutoUpdater(ILauncherUpdateBackend backend) => _backend = backend;

    /// <summary>Once the launcher is open, a verified update is offered through <see cref="Ready"/> instead of restarting on its own.</summary>
    public bool AskBeforeInstall { get; set; }
    /// <summary>Raised with the version whenever a verified update waits for the user's click.</summary>
    public event Action<string>? Ready;

    /// <summary>The user asked to update: installs and restarts unless Minecraft or a sign-in is still running.</summary>
    public bool InstallNow(Func<bool> isBusy, Action<string> report)
    {
        string? version = _backend.PendingVersion;
        if (version == null || isBusy() || !_gate.Wait(0)) return false;
        try
        {
            report($"Installing v{version} and restarting…");
            _backend.ApplyAndRestart();
            return true;
        }
        catch (Exception ex)
        {
            report($"Update could not be installed; try again. {ex.Message}");
            return false;
        }
        finally { _gate.Release(); }
    }

    public async Task PollAsync(Func<bool> isBusy, Action<string> report, bool disabled = false)
    {
        if (disabled || !_backend.IsInstalled || DateTimeOffset.UtcNow < _retryAfter || !await _gate.WaitAsync(0)) return;
        try
        {
            if (_backend.PendingVersion == null)
            {
                if (DateTimeOffset.UtcNow < _nextCheck) return;
                _nextCheck = DateTimeOffset.UtcNow.AddMinutes(10);
                report("Checking for launcher updates…");
                if (!await _backend.DownloadLatestAsync(report)) { report(""); return; }
            }
            if (AskBeforeInstall)
            {
                report("");
                Ready?.Invoke(_backend.PendingVersion!);
                return;
            }
            if (isBusy())
            {
                report($"v{_backend.PendingVersion} ready — installs automatically when Minecraft and sign-in finish.");
                return;
            }
            report($"Installing v{_backend.PendingVersion} and restarting…");
            _backend.ApplyAndRestart();
        }
        catch (Exception ex)
        {
            report($"Automatic update failed; will retry. {ex.Message}");
            _nextCheck = DateTimeOffset.UtcNow.AddMinutes(2);
            _retryAfter = _nextCheck;
        }
        finally { _gate.Release(); }
    }
}
