using System;
using System.Diagnostics;
using System.IO;
using System.Net;
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
    void ApplyAndRestart(string[] restartArgs);
}

public sealed class VelopackUpdateBackend : ILauncherUpdateBackend
{
    public const string RepositoryUrl = "https://github.com/ArashYT/TheLadsClient";
    /// <summary>Static release downloads: github.com/.../releases/latest/download/ redirects to the newest release's files and, unlike api.github.com (60 calls/hour per IP), has no rate limit.</summary>
    public const string FeedUrl = RepositoryUrl + "/releases/latest/download/";
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(30) };
    private readonly UpdateManager _manager;
    private readonly UpdateManager _fullOnly;
    private readonly Func<string, string, Task<byte[]>> _fetchSigned;
    private readonly string _publicKey;
    private string? _verified; // the downloaded version whose signature this process checked

#if LADS_QA_FEED
    // QA builds only (-p:DefineConstants=LADS_QA_FEED): a local feed folder holding the latest release's files and signature, and a throwaway key.
    public VelopackUpdateBackend() : this(Environment.GetEnvironmentVariable("LADS_QA_UPDATE_FEED") ?? throw new InvalidOperationException("LADS_QA_UPDATE_FEED is not set.")) { }

    private VelopackUpdateBackend(string feed) : this(new UpdateManager(feed),
        new UpdateManager(feed, new UpdateOptions { MaximumDeltasBeforeFallback = -1 }),
        (_, name) => File.ReadAllBytesAsync(Path.Combine(feed, name)),
        Environment.GetEnvironmentVariable("LADS_QA_UPDATE_KEY") ?? throw new InvalidOperationException("LADS_QA_UPDATE_KEY is not set.")) { }
#else
    public VelopackUpdateBackend() : this(new SimpleWebSource(FeedUrl)) { }

    private VelopackUpdateBackend(SimpleWebSource source) : this(new UpdateManager(source),
        new UpdateManager(source, new UpdateOptions { MaximumDeltasBeforeFallback = -1 }),
        (version, name) => Http.GetByteArrayAsync($"{RepositoryUrl}/releases/download/v{version}/{name}"), UpdateSignature.ReleasePublicKey) { }
#endif

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
    /// <summary>Velopack was offered a delta path for the last <see cref="DownloadLatestAsync"/>; Velopack may still have fallen back to the
    /// full package internally (e.g. a client 2+ versions behind, whose base the newest delta does not patch). False after our own
    /// fallback to the full package.</summary>
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

    /// <summary>Starts the silent installer, which waits for this process to exit, applies the update and relaunches with <paramref name="restartArgs"/>.</summary>
    public void ApplyAndRestart(string[] restartArgs)
    {
        _manager.WaitExitThenApplyUpdates(PendingVersion != null ? _manager.UpdatePendingRestart!
            : throw new InvalidOperationException("No verified update is ready."), silent: true, restart: true, restartArgs);
        Environment.Exit(0);
    }
}

// Startup and the timer share this coordinator. Only verified packages are applied.
public sealed class AutoUpdater
{
    private readonly ILauncherUpdateBackend _backend;
    private readonly SemaphoreSlim _gate = new(1, 1);
    private DateTimeOffset _nextCheck;
    private DateTimeOffset _retryAfter;
    /// <summary>No check starts before this moment (set after a failed one).</summary>
    public DateTimeOffset RetryAfter => _retryAfter;
    public AutoUpdater(ILauncherUpdateBackend backend) => _backend = backend;

    /// <summary>Once the launcher is open, a verified update is offered through <see cref="Ready"/> instead of restarting on its own.</summary>
    public bool AskBeforeInstall
    {
        get => _askBeforeInstall;
        set
        {
            lock (_priorityLock)
            {
                _askBeforeInstall = value;
                // The startup screen may stop waiting for a slow check; a download that then continues next to an open launcher must not stay High.
                if (value) _downloadScope?.Lower(ProcessPriorityClass.BelowNormal);
            }
        }
    }
    private bool _askBeforeInstall;
    private ProcessPriority.ScopeHandle? _downloadScope;
    private readonly object _priorityLock = new();
    /// <summary>Raised with the version whenever a verified update waits for the user's click.</summary>
    public event Action<string>? Ready;
    /// <summary>Called with the pending version right before every restart; returns the command-line args the new launcher starts with.</summary>
    public Func<string, string[]>? PrepareRestart { get; set; }

    private void Apply(string version, Action<string> report)
    {
        string[] args;
        try { args = PrepareRestart?.Invoke(version) ?? Array.Empty<string>(); }
        catch (Exception ex)   // only window placement is lost; the update itself must still apply
        {
            report($"Could not prepare the restart ({ex.Message}); updating without it.");
            args = Array.Empty<string>();
        }
        _backend.ApplyAndRestart(args);
    }

    /// <summary>The user asked to update: installs and restarts unless Minecraft or a sign-in is still running.</summary>
    public bool InstallNow(Func<bool> isBusy, Action<string> report)
    {
        string? version = _backend.PendingVersion;
        if (version == null || isBusy() || !_gate.Wait(0)) return false;
        try
        {
            report($"Installing v{version} and restarting…");
            Apply(version, report);
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
                // High only while the startup screen is waiting for the update; an open launcher may have a game running next to it.
                lock (_priorityLock)
                    _downloadScope = ProcessPriority.Scope(_askBeforeInstall ? ProcessPriorityClass.BelowNormal : ProcessPriorityClass.High);
                try { if (!await _backend.DownloadLatestAsync(report)) { report(""); return; } }
                finally { lock (_priorityLock) { _downloadScope?.Dispose(); _downloadScope = null; } }
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
            Apply(_backend.PendingVersion!, report);
        }
        catch (Exception ex)
        {
            bool busy = IsRateLimited(ex);
            report(busy ? "Update server is busy; will retry in an hour." : $"Automatic update failed; will retry. {ex.Message}");
            _nextCheck = DateTimeOffset.UtcNow.AddMinutes(busy ? 60 : 2);   // retrying a rate-limited server every 2 minutes keeps its quota at zero
            _retryAfter = _nextCheck;
        }
        finally { _gate.Release(); }
    }

    /// <summary>A 403 or 429 from the update server, possibly wrapped by Velopack.</summary>
    private static bool IsRateLimited(Exception? ex)
    {
        for (; ex != null; ex = ex.InnerException)
            if (ex is HttpRequestException { StatusCode: HttpStatusCode.Forbidden or HttpStatusCode.TooManyRequests }) return true;
        return false;
    }
}
