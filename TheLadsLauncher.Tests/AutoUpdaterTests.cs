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
        public async Task<bool> DownloadLatestAsync(Action<string> report)
        {
            Downloads++;
            if (Wait != null) await Wait.Task;
            if (FailDownload) throw new IOException("Corrupt package");
            PendingVersion = "1.2.2";
            return true;
        }
        public void ApplyAndRestart()
        {
            Applies++;
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
