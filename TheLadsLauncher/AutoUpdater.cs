using System;
using System.IO;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Velopack;
using Velopack.Locators;
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
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(30) };
    private readonly UpdateManager _manager;
    private readonly UpdateManager _fullOnly;
    private readonly Func<string, string, Task<byte[]>> _fetchSigned;
    private readonly string _publicKey;
    private string? _verified; // the downloaded version whose signature this process checked

    public VelopackUpdateBackend() : this(new GithubSource(RepositoryUrl, null, false)) { }

    private VelopackUpdateBackend(GithubSource source) : this(new UpdateManager(source),
        new UpdateManager(source, new UpdateOptions { MaximumDeltasBeforeFallback = -1 }),
        (version, name) => Http.GetByteArrayAsync($"{RepositoryUrl}/releases/download/v{version}/{name}"), UpdateSignature.ReleasePublicKey) { }

    /// <param name="fullOnly">Same feed as <paramref name="manager"/> but never uses deltas (a negative <c>MaximumDeltasBeforeFallback</c>).</param>
    /// <param name="fetchSigned">(version, file name) -> that release's signature file (see <see cref="UpdateSignature"/>).</param>
    public VelopackUpdateBackend(UpdateManager manager, UpdateManager fullOnly, Func<string, string, Task<byte[]>> fetchSigned, string publicKey)
    {
        _manager = manager;
        _fullOnly = fullOnly;
        _fetchSigned = fetchSigned;
        _publicKey = publicKey;
    }

    public bool IsInstalled => _manager.IsInstalled;
    /// <summary>A downloaded update counts only once its signature checked out.</summary>
    public string? PendingVersion => _manager.UpdatePendingRestart?.Version.ToString() is { } v && v == _verified ? v : null;
    /// <summary>Whether the verified package of the last <see cref="DownloadLatestAsync"/> was rebuilt from deltas (false after a fallback to the full package).</summary>
    public bool LastDownloadWasDelta { get; private set; }

    public async Task<bool> DownloadLatestAsync(Action<string> report)
    {
        var update = await _manager.CheckForUpdatesAsync();
        if (update == null) return false;
        LastDownloadWasDelta = update.DeltasToTarget.Length > 0;
        try { _verified = await DownloadAndVerifyAsync(_manager, update, report); }
        catch (InvalidDataException) when (LastDownloadWasDelta) // the rebuilt package is already deleted; a full one may still verify
        {
            report("Delta update did not verify; downloading the full package…");
            LastDownloadWasDelta = false;
            var full = await _fullOnly.CheckForUpdatesAsync() ?? throw new InvalidOperationException("The full update package is no longer available.");
            _verified = await DownloadAndVerifyAsync(_fullOnly, full, report);
        }
        return true;
    }

    /// <returns>The version of the downloaded package, which has been verified; a package that fails is deleted.</returns>
    private async Task<string> DownloadAndVerifyAsync(UpdateManager manager, UpdateInfo update, Action<string> report)
    {
        var asset = update.TargetFullRelease;
        string version = asset.Version.ToString();
        await manager.DownloadUpdatesAsync(update, percent => report($"Downloading v{version}: {percent}%"));
        string package = Path.Combine(VelopackLocator.Current.PackagesDir!, asset.FileName);
        try
        {
            UpdateSignature.Verify(await _fetchSigned(version, UpdateSignature.ManifestName),
                await _fetchSigned(version, UpdateSignature.SignatureName), _publicKey, version, package);
        }
        catch
        {
            try { File.Delete(package); } catch (IOException) { } // never leave an unsigned package to be applied later
            throw;
        }
        return version;
    }

    public void ApplyAndRestart() => _manager.ApplyUpdatesAndRestart(PendingVersion != null ? _manager.UpdatePendingRestart!
        : throw new InvalidOperationException("No verified update is ready."));
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
