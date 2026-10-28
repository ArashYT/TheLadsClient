using System.Diagnostics;
using System.Net;
using TheLadsLauncher;
using Xunit;

[Collection(TimingSensitive.Name)]   // the priority tests change the whole process's priority, so no other test may run beside them
public class AutoUpdaterTests
{
    private sealed class Backend : ILauncherUpdateBackend
    {
        public bool IsInstalled { get; set; } = true;
        public string? PendingVersion { get; set; }
        public int Downloads, Applies;
        public bool FailDownload, FailApply;
        public Exception? DownloadError;
        public TaskCompletionSource? Wait;
        public ProcessPriorityClass PriorityDuringDownload, PriorityAtApply;
        public string[]? AppliedArgs;
        public Action? OnApply;
        public int Raisers;
        public CancellationToken RaiseToken;
        public Task KeepUpdateExeHighAsync(CancellationToken stop)
        {
            Raisers++;
            RaiseToken = stop;
            return Task.CompletedTask;
        }
        public async Task<bool> DownloadLatestAsync(Action<string> report)
        {
            Downloads++;
            PriorityDuringDownload = Process.GetCurrentProcess().PriorityClass;
            if (Wait != null) await Wait.Task;
            if (DownloadError != null) throw DownloadError;
            if (FailDownload) throw new IOException("Corrupt package");
            PendingVersion = "1.2.2";
            return true;
        }
        public void ApplyAndRestart(string[] restartArgs)
        {
            Applies++;
            AppliedArgs = restartArgs;
            PriorityAtApply = Process.GetCurrentProcess().PriorityClass;
            OnApply?.Invoke();
            if (FailApply) throw new IOException("Install locked");
        }
    }

    [Fact]
    public async Task DownloadsAndRestartsWithoutAConfirmation()
    {
        var backend = new Backend();
        await new AutoUpdater(backend).PollAsync(() => false, _ => { });
        Assert.Equal(1, backend.Downloads);
        Assert.Equal(1, backend.Applies);
    }

    [Fact]
    public async Task OnceOpenItDownloadsThenAsksInsteadOfRestarting()
    {
        var backend = new Backend();
        var updater = new AutoUpdater(backend) { AskBeforeInstall = true };
        var offered = new List<string>();
        updater.Ready += offered.Add;
        await updater.PollAsync(() => false, _ => { });
        await updater.PollAsync(() => false, _ => { });
        Assert.Equal(1, backend.Downloads);
        Assert.Equal(0, backend.Applies);
        Assert.Equal(new[] { "1.2.2", "1.2.2" }, offered);
        Assert.False(updater.InstallNow(() => true, _ => { }));   // Minecraft or sign-in still running
        Assert.Equal(0, backend.Applies);
        Assert.True(updater.InstallNow(() => false, _ => { }));
        Assert.Equal(1, backend.Applies);
    }

    [Fact]
    public void InstallNowNeedsAVerifiedUpdateAndReportsAFailedInstall()
    {
        var backend = new Backend();
        var messages = new List<string>();
        Assert.False(new AutoUpdater(backend).InstallNow(() => false, messages.Add));
        backend.PendingVersion = "1.2.2";
        backend.FailApply = true;
        Assert.False(new AutoUpdater(backend).InstallNow(() => false, messages.Add));
        Assert.Contains(messages, message => message.Contains("Install locked"));
    }

    [Fact]
    public async Task WaitsForGameOrLoginThenAppliesWithoutRedownloading()
    {
        var backend = new Backend();
        var updater = new AutoUpdater(backend);
        await updater.PollAsync(() => true, _ => { });
        Assert.Equal(0, backend.Applies);
        await updater.PollAsync(() => false, _ => { });
        Assert.Equal(1, backend.Downloads);
        Assert.Equal(1, backend.Applies);
    }

    [Theory]
    [InlineData(HttpStatusCode.Forbidden, false)]
    [InlineData(HttpStatusCode.TooManyRequests, false)]
    [InlineData(HttpStatusCode.Forbidden, true)]   // Velopack may wrap the HTTP error
    public async Task ARateLimitedCheckBacksOffForAnHour(HttpStatusCode status, bool wrapped)
    {
        var error = new HttpRequestException("rate limit", null, status);
        var backend = new Backend { DownloadError = wrapped ? new InvalidOperationException("check failed", error) : error };
        var messages = new List<string>();
        var updater = new AutoUpdater(backend);
        var start = DateTimeOffset.UtcNow;
        await updater.PollAsync(() => false, messages.Add);
        Assert.True(updater.RetryAfter - start >= TimeSpan.FromMinutes(59));
        Assert.Contains("Update server is busy; will retry in an hour.", messages);
    }

    [Fact]
    public async Task OtherFailuresStillRetryAfterTwoMinutes()
    {
        var backend = new Backend { DownloadError = new HttpRequestException("not found", null, HttpStatusCode.NotFound) };
        var messages = new List<string>();
        var updater = new AutoUpdater(backend);
        var start = DateTimeOffset.UtcNow;
        await updater.PollAsync(() => false, messages.Add);
        Assert.True(updater.RetryAfter - start <= TimeSpan.FromMinutes(3));
        Assert.Contains(messages, message => message.StartsWith("Automatic update failed; will retry."));
        Assert.DoesNotContain("Update server is busy; will retry in an hour.", messages);
    }

    /// <summary>An unsigned or mis-signed latest release does not change in 2 minutes: checking it again sooner only costs requests.</summary>
    [Fact]
    public async Task ARejectedReleaseBacksOffForAnHour()
    {
        var backend = new Backend { DownloadError = new UpdateRejectedException("v1.2.2 is not signed by The Lads yet; it was not downloaded.") };
        var messages = new List<string>();
        var updater = new AutoUpdater(backend);
        var start = DateTimeOffset.UtcNow;
        await updater.PollAsync(() => false, messages.Add);
        await updater.PollAsync(() => false, messages.Add);
        Assert.True(updater.RetryAfter - start >= TimeSpan.FromMinutes(59));
        Assert.Equal(1, backend.Downloads);
        Assert.Equal(0, backend.Applies);
        Assert.Contains("v1.2.2 is not signed by The Lads yet; it was not downloaded. Will check again in an hour.", messages);
        Assert.DoesNotContain(messages, message => message.StartsWith("Automatic update failed"));
    }

    [Fact]
    public async Task FailedVerificationNeverAppliesAndRetriesAreThrottled()
    {
        var backend = new Backend { FailDownload = true };
        var messages = new List<string>();
        var updater = new AutoUpdater(backend);
        await updater.PollAsync(() => false, messages.Add);
        await updater.PollAsync(() => false, messages.Add);
        Assert.Equal(0, backend.Applies);
        Assert.Equal(1, backend.Downloads);
        Assert.Contains(messages, message => message.Contains("Corrupt package"));
    }

    [Fact]
    public async Task FailedApplyDoesNotRetryEveryTimerTick()
    {
        var backend = new Backend { PendingVersion = "1.2.2", FailApply = true };
        var updater = new AutoUpdater(backend);
        await updater.PollAsync(() => false, _ => { });
        await updater.PollAsync(() => false, _ => { });
        Assert.Equal(1, backend.Applies);
    }

    [Fact]
    public async Task ConcurrentChecksCannotDownloadOrApplyTwice()
    {
        var backend = new Backend { Wait = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously) };
        var updater = new AutoUpdater(backend);
        var first = updater.PollAsync(() => false, _ => { });
        await updater.PollAsync(() => false, _ => { });
        backend.Wait.SetResult();
        await first;
        Assert.Equal(1, backend.Downloads);
        Assert.Equal(1, backend.Applies);
    }

    [Theory]
    [InlineData(false, false)]
    [InlineData(true, true)]
    public async Task DevelopmentAndDisabledCopiesDoNotUpdate(bool installed, bool disabled)
    {
        var backend = new Backend { IsInstalled = installed };
        await new AutoUpdater(backend).PollAsync(() => false, _ => { }, disabled);
        Assert.Equal(0, backend.Downloads);
        Assert.Equal(0, backend.Applies);
    }

    // The priority tests share the process priority; xUnit runs the tests of one class one after another.
    private static async Task WithNormalPriority(Func<Task> test)
    {
        var process = Process.GetCurrentProcess();
        var original = process.PriorityClass;
        process.PriorityClass = ProcessPriorityClass.Normal;
        try { await test(); }
        finally { process.PriorityClass = original; }
    }

    [Fact]
    public Task StartupDownloadRunsHighAndOpenLauncherDownloadRunsBelowNormal() => WithNormalPriority(async () =>
    {
        var startup = new Backend();
        await new AutoUpdater(startup).PollAsync(() => true, _ => { });
        Assert.Equal(ProcessPriorityClass.High, startup.PriorityDuringDownload);
        Assert.Equal(ProcessPriorityClass.Normal, Process.GetCurrentProcess().PriorityClass);   // restored before the apply step
        var open = new Backend();
        await new AutoUpdater(open) { AskBeforeInstall = true }.PollAsync(() => true, _ => { });
        Assert.Equal(ProcessPriorityClass.BelowNormal, open.PriorityDuringDownload);
        Assert.Equal(ProcessPriorityClass.Normal, Process.GetCurrentProcess().PriorityClass);
    });

    [Fact]
    public Task OnlyTheHighStartupDownloadKeepsVelopacksUpdateExeHigh() => WithNormalPriority(async () =>
    {
        var startup = new Backend { Wait = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously) };
        var poll = new AutoUpdater(startup).PollAsync(() => true, _ => { });
        Assert.Equal(1, startup.Raisers);
        Assert.False(startup.RaiseToken.IsCancellationRequested);   // raising while the download and its delta rebuild run
        startup.Wait.SetResult();
        await poll;
        Assert.True(startup.RaiseToken.IsCancellationRequested);    // stopped with the High scope

        var open = new Backend();
        await new AutoUpdater(open) { AskBeforeInstall = true }.PollAsync(() => true, _ => { });
        Assert.Equal(0, open.Raisers);   // BelowNormal is inherited by the child: nothing to raise

        var slow = new Backend { Wait = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously) };
        var updater = new AutoUpdater(slow);
        var slowPoll = updater.PollAsync(() => true, _ => { });
        Assert.False(slow.RaiseToken.IsCancellationRequested);
        updater.AskBeforeInstall = true;   // the startup screen stopped waiting; the download goes on BelowNormal
        Assert.True(slow.RaiseToken.IsCancellationRequested);
        slow.Wait.SetResult();
        await slowPoll;
    });

    [Fact]
    public Task PriorityIsRestoredWhenTheDownloadFails() => WithNormalPriority(async () =>
    {
        var backend = new Backend { FailDownload = true };
        await new AutoUpdater(backend).PollAsync(() => false, _ => { });
        Assert.Equal(ProcessPriorityClass.High, backend.PriorityDuringDownload);
        Assert.Equal(ProcessPriorityClass.Normal, Process.GetCurrentProcess().PriorityClass);
    });

    [Fact]
    public Task AnOpenLauncherLowersAHighDownloadTheMomentItBecomesInteractive() => WithNormalPriority(async () =>
    {
        // The startup screen stops waiting after 10 s; a slow check then finishes next to the open launcher.
        var backend = new Backend { Wait = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously) };
        var updater = new AutoUpdater(backend);
        var poll = updater.PollAsync(() => true, _ => { });
        Assert.Equal(1, backend.Downloads);
        Assert.Equal(ProcessPriorityClass.High, Process.GetCurrentProcess().PriorityClass);
        updater.AskBeforeInstall = true;
        Assert.Equal(ProcessPriorityClass.BelowNormal, Process.GetCurrentProcess().PriorityClass);
        backend.Wait.SetResult();
        await poll;
        Assert.Equal(ProcessPriorityClass.Normal, Process.GetCurrentProcess().PriorityClass);
        updater.AskBeforeInstall = true;   // no download running: nothing to lower
        Assert.Equal(ProcessPriorityClass.Normal, Process.GetCurrentProcess().PriorityClass);
    });

    [Fact]
    public Task PrepareRestartRunsBeforeApplyAndItsArgsReachTheBackend() => WithNormalPriority(async () =>
    {
        var backend = new Backend();   // downloads first (High), so the restored priority at apply time means something
        bool prepared = false, preparedFirst = false;
        string? versionSeen = null;
        backend.OnApply = () => preparedFirst = prepared;
        var updater = new AutoUpdater(backend)
        {
            PrepareRestart = version => { prepared = true; versionSeen = version; return new[] { "--restore-window", "1,2,3,4,Normal" }; }
        };
        await updater.PollAsync(() => false, _ => { });
        Assert.Equal(new[] { "--restore-window", "1,2,3,4,Normal" }, backend.AppliedArgs);
        Assert.True(preparedFirst);
        Assert.Equal("1.2.2", versionSeen);
        Assert.Equal(ProcessPriorityClass.High, backend.PriorityDuringDownload);
        Assert.Equal(ProcessPriorityClass.Normal, backend.PriorityAtApply);   // restored before the restart hook and the apply
    });

    [Fact]
    public async Task AThrowingRestartHookStillAppliesTheUpdateWithoutArgs()
    {
        var messages = new List<string>();
        var polled = new Backend { PendingVersion = "1.2.2" };
        await new AutoUpdater(polled) { PrepareRestart = _ => throw new InvalidOperationException("no window") }.PollAsync(() => false, messages.Add);
        Assert.Equal(1, polled.Applies);
        Assert.Empty(polled.AppliedArgs!);
        Assert.Single(messages, message => message.Contains("no window"));
        var clicked = new Backend { PendingVersion = "1.2.2" };
        Assert.True(new AutoUpdater(clicked) { PrepareRestart = _ => throw new InvalidOperationException("no window") }.InstallNow(() => false, _ => { }));
        Assert.Equal(1, clicked.Applies);
        Assert.Empty(clicked.AppliedArgs!);
    }

    [Fact]
    public void InstallNowAlsoPreparesTheRestartAndNoHookMeansNoArgs()
    {
        var hooked = new Backend { PendingVersion = "1.2.2" };
        string? versionSeen = null;
        Assert.True(new AutoUpdater(hooked) { PrepareRestart = v => { versionSeen = v; return new[] { "--x" }; } }.InstallNow(() => false, _ => { }));
        Assert.Equal(new[] { "--x" }, hooked.AppliedArgs);
        Assert.Equal("1.2.2", versionSeen);
        var plain = new Backend { PendingVersion = "1.2.2" };
        Assert.True(new AutoUpdater(plain).InstallNow(() => false, _ => { }));
        Assert.Empty(plain.AppliedArgs!);
    }

    [Fact]
    public void OfflineNotesUseNumericVersionOrderAndIgnoreTemplates()
    {
        var directory = Path.Combine(Path.GetTempPath(), "lads-notes-" + Guid.NewGuid());
        Directory.CreateDirectory(directory);
        try
        {
            File.WriteAllText(Path.Combine(directory, "1.9.0.md"), "older");
            File.WriteAllText(Path.Combine(directory, "1.10.0.md"), "newer");
            File.WriteAllText(Path.Combine(directory, "TEMPLATE.md"), "template");
            var notes = ReleaseNotes.Read(directory);
            Assert.Equal(new[] { "1.10.0", "1.9.0" }, notes.Select(note => note.Version));
            Assert.Equal("newer", notes[0].Markdown);
        }
        finally { Directory.Delete(directory, true); }
    }
}
