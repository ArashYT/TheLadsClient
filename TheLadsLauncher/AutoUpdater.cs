using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Velopack;
using Velopack.Exceptions;
using Velopack.Locators;
using Velopack.Sources;

namespace TheLadsLauncher;

/// <summary>The latest release cannot be installed as published: it has no valid signature, or its package does not match the signed
/// one. Retrying soon cannot change that, so checks back off for an hour and nothing large is downloaded for it.</summary>
public sealed class UpdateRejectedException(string message, Exception? inner = null) : Exception(message, inner);

public interface ILauncherUpdateBackend
{
    bool IsInstalled { get; }
    string? PendingVersion { get; }
    Task<bool> DownloadLatestAsync(Action<string> report);
    void ApplyAndRestart(string[] restartArgs);
    /// <summary>Keeps the updater's own child processes at High until <paramref name="stop"/> is cancelled; never throws.</summary>
    Task KeepUpdateExeHighAsync(CancellationToken stop);
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
    private readonly IReadOnlyList<string> _publicKeys;
    private string? _verified; // the downloaded version whose signature this process checked
    private UpdateSignature.Manifest? _rejected; // a signed package whose download did not match it: this process never fetches it again

#if LADS_QA_FEED
    // QA builds only (-p:DefineConstants=LADS_QA_FEED): a local feed folder holding the latest release's files and signature, and a throwaway key.
    public VelopackUpdateBackend() : this(Environment.GetEnvironmentVariable("LADS_QA_UPDATE_FEED") ?? throw new InvalidOperationException("LADS_QA_UPDATE_FEED is not set.")) { }

    private VelopackUpdateBackend(string feed) : this(new UpdateManager(feed),
        new UpdateManager(feed, new UpdateOptions { MaximumDeltasBeforeFallback = -1 }),
        (_, name) => File.ReadAllBytesAsync(Path.Combine(feed, name)),
        new[] { Environment.GetEnvironmentVariable("LADS_QA_UPDATE_KEY") ?? throw new InvalidOperationException("LADS_QA_UPDATE_KEY is not set.") }) { }
#else
    public VelopackUpdateBackend() : this(new SimpleWebSource(FeedUrl)) { }

    private VelopackUpdateBackend(SimpleWebSource source) : this(new UpdateManager(source),
        new UpdateManager(source, new UpdateOptions { MaximumDeltasBeforeFallback = -1 }),
        (version, name) => Http.GetByteArrayAsync($"{RepositoryUrl}/releases/download/v{version}/{name}"), UpdateSignature.ReleasePublicKeys) { }
#endif

    /// <param name="fullOnly">Same feed as <paramref name="manager"/> but never uses deltas (a negative <c>MaximumDeltasBeforeFallback</c>).</param>
    /// <param name="fetchSigned">(version, file name) -> that release's signature file (see <see cref="UpdateSignature"/>).</param>
    public VelopackUpdateBackend(UpdateManager manager, UpdateManager fullOnly, Func<string, string, Task<byte[]>> fetchSigned, IReadOnlyList<string> publicKeys)
    {
        _manager = manager;
        _fullOnly = fullOnly;
        _fetchSigned = fetchSigned;
        _publicKeys = publicKeys;
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
        try { _verified = await DownloadAndVerifyAsync(_manager, update, LastDownloadWasDelta, report); }
        catch (InvalidDataException) when (LastDownloadWasDelta) // the rebuilt package is already deleted; a full one may still verify
        {
            report("Delta update did not verify; downloading the full package…");
            LastDownloadWasDelta = false;
            var full = await _fullOnly.CheckForUpdatesAsync() ?? throw new InvalidOperationException("The full update package is no longer available.");
            _verified = await DownloadAndVerifyAsync(_fullOnly, full, false, report);
        }
        return true;
    }

    /// <param name="delta">Velopack may rebuild the package from a delta, which then matches the signed one by content only.</param>
    /// <returns>The version of the downloaded package, which has been verified; a package that fails is deleted.</returns>
    private async Task<string> DownloadAndVerifyAsync(UpdateManager manager, UpdateInfo update, bool delta, Action<string> report)
    {
        var asset = update.TargetFullRelease;
        string version = asset.Version.ToString();
        // The signed statement first: a few hundred bytes decide whether up to ~250 MB are downloaded (and a delta rebuilt) at all.
        var statement = await FetchStatementAsync(asset);
        try { await manager.DownloadUpdatesAsync(update, percent => report($"Downloading v{version}: {percent}%")); }
        catch (ChecksumFailedException ex) // the file on the server is not the one its feed (and so the signature) names
        {
            _rejected = statement;
            throw new UpdateRejectedException($"The v{version} package on the update server is damaged; it is not downloaded again.", ex);
        }
        string package = Path.Combine(VelopackLocator.Current.PackagesDir!, asset.FileName);
        try
        {
            // Hashing ~250 MB (and decompressing every entry for the content hash) takes seconds: never on the UI thread.
            await Task.Run(() => UpdateSignature.VerifyPackage(statement, package, delta));
        }
        catch (Exception ex)
        {
            try { File.Delete(package); } catch (IOException) { } // never leave an unsigned package to be applied later
            if (ex is InvalidDataException && !delta) // the full package itself differs from the signed one
            {
                _rejected = statement;
                throw new UpdateRejectedException(ex.Message, ex);
            }
            throw;
        }
        return version;
    }

    /// <summary>The release's signed statement for <paramref name="asset"/>, checked before any package is downloaded.</summary>
    /// <exception cref="UpdateRejectedException">No signature, not a release key's, or not for this package.</exception>
    private async Task<UpdateSignature.Manifest> FetchStatementAsync(VelopackAsset asset)
    {
        string version = asset.Version.ToString();
        byte[] manifest, signature;
        try
        {
            manifest = await _fetchSigned(version, UpdateSignature.ManifestName);
            signature = await _fetchSigned(version, UpdateSignature.SignatureName);
        }
        catch (Exception ex) when (ex is HttpRequestException { StatusCode: HttpStatusCode.NotFound } or FileNotFoundException)
        {
            throw new UpdateRejectedException($"v{version} is not signed by The Lads yet; it was not downloaded.", ex);
        }
        UpdateSignature.Manifest statement;
        try { statement = UpdateSignature.VerifyStatement(manifest, signature, _publicKeys, version, asset.FileName); }
        catch (InvalidDataException ex) { throw new UpdateRejectedException(ex.Message, ex); }
        // Velopack checks a full download against the feed's hash, so a feed naming another package only ever fetches one that fails.
        if (!string.IsNullOrEmpty(asset.SHA256) && !string.Equals(asset.SHA256, statement.sha256, StringComparison.OrdinalIgnoreCase))
            throw new UpdateRejectedException($"The v{version} update feed names another package than the signed one; it was not downloaded.");
        if (statement == _rejected)
            throw new UpdateRejectedException($"v{version} did not match its signature; it is not downloaded again.");
        return statement;
    }

    /// <summary>Starts the silent installer, which waits for this process to exit, applies the update and relaunches with <paramref name="restartArgs"/>.</summary>
    public void ApplyAndRestart(string[] restartArgs)
    {
        _manager.WaitExitThenApplyUpdates(PendingVersion != null ? _manager.UpdatePendingRestart!
            : throw new InvalidOperationException("No verified update is ready."), silent: true, restart: true, restartArgs);
        Environment.Exit(0);
    }

    /// <summary>Velopack rebuilds a delta in a child "Update.exe patch", which Windows starts at Normal even under a High launcher
    /// (only Idle/BelowNormal are inherited); this raises it, polling every 250 ms off the UI thread.</summary>
    public async Task KeepUpdateExeHighAsync(CancellationToken stop)
    {
        try
        {
            string path = Path.GetFullPath(VelopackLocator.Current.UpdateExePath ?? throw new InvalidOperationException("not installed"));
            string name = Path.GetFileNameWithoutExtension(path);
            while (true)
            {
                await Task.Delay(250, stop).ConfigureAwait(false);   // the rebuild starts after the download, never in the first 250 ms
                foreach (var process in Process.GetProcessesByName(name))
                {
                    using (process)
                    {
                        try
                        {
                            if (process.PriorityClass != ProcessPriorityClass.High && string.Equals(process.MainModule?.FileName, path, StringComparison.OrdinalIgnoreCase))
                                process.PriorityClass = ProcessPriorityClass.High;
                        }
                        catch (Exception ex) when (ex is Win32Exception or InvalidOperationException) { } // exited meanwhile, or not ours to read
                    }
                }
            }
        }
        catch (Exception) { } // cancelled, or no Update.exe to find: the rebuild just runs at Normal
    }
}

// Startup and the timer share this coordinator. Only verified packages are applied.
public sealed class AutoUpdater
{
    /// <summary>This build's version; read here, not from Program, because tools/UpdateProbe compiles this file without Program.</summary>
    private static readonly string CurrentVersion = typeof(AutoUpdater).Assembly.GetName().Version?.ToString(3) ?? "?";
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
                if (value)
                {
                    _downloadScope?.Lower(ProcessPriorityClass.BelowNormal);
                    StopKeepingUpdateExeHigh();
                }
            }
        }
    }
    private bool _askBeforeInstall;
    private ProcessPriority.ScopeHandle? _downloadScope;
    private CancellationTokenSource? _keepUpdateExeHigh; // set while the High startup download runs
    private void StopKeepingUpdateExeHigh() // under _priorityLock
    {
        _keepUpdateExeHigh?.Cancel();
        _keepUpdateExeHigh?.Dispose();
        _keepUpdateExeHigh = null;
    }
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

    public async Task PollAsync(Func<bool> isBusy, Action<string> report, bool disabled = false, bool force = false)
    {
        if (disabled || !await _gate.WaitAsync(0)) return;
        try
        {
            if (!_backend.IsInstalled)
            {
                if (force) report($"Launcher is up to date (v{CurrentVersion}).");
                return;
            }
            if (DateTimeOffset.UtcNow < _retryAfter)
            {
                if (force) report($"Update server busy; try again after {_retryAfter.ToLocalTime():t}.");
                return;
            }
            if (_backend.PendingVersion == null)
            {
                if (!force && DateTimeOffset.UtcNow < _nextCheck) return;
                _nextCheck = DateTimeOffset.UtcNow.AddMinutes(10);
                report("Checking for launcher updates…");
                // High only while the startup screen is waiting for the update; an open launcher may have a game running next to it.
                lock (_priorityLock)
                {
                    _downloadScope = ProcessPriority.Scope(_askBeforeInstall ? ProcessPriorityClass.BelowNormal : ProcessPriorityClass.High);
                    // A BelowNormal launcher's children inherit it; a High one's start at Normal, so the delta rebuild is raised by hand.
                    if (!_askBeforeInstall) _ = _backend.KeepUpdateExeHighAsync((_keepUpdateExeHigh = new()).Token);
                }
                try { if (!await _backend.DownloadLatestAsync(report)) { report(force ? $"Launcher is up to date (v{CurrentVersion})." : ""); return; } }
                finally { lock (_priorityLock) { _downloadScope?.Dispose(); _downloadScope = null; StopKeepingUpdateExeHigh(); } }
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
            bool busy = IsRateLimited(ex), rejected = ex is UpdateRejectedException;
            report(busy ? "Update server is busy; will retry in an hour." : rejected ? $"{ex.Message} Will check again in an hour."
                : $"Automatic update failed; will retry. {ex.Message}");
            // Retrying a rate-limited server every 2 minutes keeps its quota at zero; a refused release changes only when the owner fixes it.
            _nextCheck = DateTimeOffset.UtcNow.AddMinutes(busy || rejected ? 60 : 2);
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
