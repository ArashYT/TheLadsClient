using System.Diagnostics;
using TheLadsLauncher;
using Xunit;

public class AutoUpdaterTests
{
    private sealed class Backend : ILauncherUpdateBackend
    {
        public bool IsInstalled { get; set; } = true;
        public string? PendingVersion { get; set; }
        public int Downloads, Applies;
        public bool FailDownload, FailApply;
        public TaskCompletionSource? Wait;
        public ProcessPriorityClass PriorityDuringDownload;
        public string[]? AppliedArgs;
        public Action? OnApply;
        public async Task<bool> DownloadLatestAsync(Action<string> report)
        {
            Downloads++;
            PriorityDuringDownload = Process.GetCurrentProcess().PriorityClass;
            if (Wait != null) await Wait.Task;
            if (FailDownload) throw new IOException("Corrupt package");
            PendingVersion = "1.2.2";
            return true;
        }
        public void ApplyAndRestart(string[] restartArgs)
        {
            Applies++;
            AppliedArgs = restartArgs;
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
    public Task PriorityIsRestoredWhenTheDownloadFails() => WithNormalPriority(async () =>
    {
        var backend = new Backend { FailDownload = true };
        await new AutoUpdater(backend).PollAsync(() => false, _ => { });
        Assert.Equal(ProcessPriorityClass.High, backend.PriorityDuringDownload);
        Assert.Equal(ProcessPriorityClass.Normal, Process.GetCurrentProcess().PriorityClass);
    });

    [Fact]
    public async Task PrepareRestartRunsBeforeApplyAndItsArgsReachTheBackend()
    {
        var backend = new Backend { PendingVersion = "1.2.2" };
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
