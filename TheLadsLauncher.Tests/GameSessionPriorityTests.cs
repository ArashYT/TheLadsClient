using System.Diagnostics;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

[Collection(TimingSensitive.Name)]
public class GameSessionPriorityTests
{
    // Normalize the test runner to Normal first (as AutoUpdaterTests does): CI runs the job BelowNormal, and an external priority manager
    // like Process Lasso can promote a BelowNormal child mid-test while leaving testhost BelowNormal.
    private static async Task WithNormalPriority(Func<Task> test)
    {
        using var launcher = Process.GetCurrentProcess();
        var original = launcher.PriorityClass;
        ProcessPriority.TrySet(launcher, ProcessPriorityClass.Normal);
        try { await test(); }
        finally { ProcessPriority.TrySet(launcher, original); }
    }

    [Fact]
    public Task GameRunsAboveNormalWhileLoadingThenNormal() => WithNormalPriority(async () =>
    {
        Assert.False(GameSession.StartupFinished(null));
        Assert.True(GameSession.StartupFinished("[12:00:00] [Sound Library Loader/INFO]: Sound engine started"));
        using var dir = new TestDirectory();
        var game = Path.Combine(dir.Path, "profile");
        Directory.CreateDirectory(game);
        var shared = new SharedContentService(Path.Combine(dir.Path, "global"), Path.Combine(dir.Path, "backups"));
        // About 3 s of "loading", the line, then about 3 s of "play".
        using var process = Process.Start(new ProcessStartInfo("cmd.exe", "/c ping -n 4 127.0.0.1 >nul & echo Sound engine started & ping -n 4 127.0.0.1 >nul")
            { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true })!;
        GameSession.Attach(process, game, Array.Empty<string>(), _ => { }, shared);
        process.BeginOutputReadLine();
        // Owner, 2026-11-16: AboveNormal while loading, Normal once loaded (never High: that froze Discord on 2026-10-10).
        process.Refresh();
        Assert.Equal(ProcessPriorityClass.AboveNormal, process.PriorityClass);
        await Task.Delay(4500);
        process.Refresh();
        Assert.Equal(ProcessPriorityClass.Normal, process.PriorityClass);
        await process.WaitForExitAsync();
    });

    [Fact]
    public Task AGameThatNeverReportsLoadedDropsToNormalAfterTheLimit() => WithNormalPriority(async () =>
    {
        using var dir = new TestDirectory();
        var game = Path.Combine(dir.Path, "profile");
        Directory.CreateDirectory(game);
        var shared = new SharedContentService(Path.Combine(dir.Path, "global"), Path.Combine(dir.Path, "backups"));
        var limit = GameSession.LoadingBoostLimit;
        GameSession.LoadingBoostLimit = TimeSpan.FromSeconds(2);
        try
        {
            using var process = Process.Start(new ProcessStartInfo("cmd.exe", "/c ping -n 6 127.0.0.1 >nul")
                { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true })!;
            GameSession.Attach(process, game, Array.Empty<string>(), _ => { }, shared);
            process.BeginOutputReadLine();
            process.Refresh();
            Assert.Equal(ProcessPriorityClass.AboveNormal, process.PriorityClass);
            await Task.Delay(3500);
            process.Refresh();
            Assert.Equal(ProcessPriorityClass.Normal, process.PriorityClass);
            await process.WaitForExitAsync();
        }
        finally { GameSession.LoadingBoostLimit = limit; }
    });

    [Fact]
    public Task AGameStartedWhileAnUpdateDownloadsKeepsThePriorityTheLauncherHadBefore() => WithNormalPriority(async () =>
    {
        using var dir = new TestDirectory();
        var game = Path.Combine(dir.Path, "profile");
        Directory.CreateDirectory(game);
        var shared = new SharedContentService(Path.Combine(dir.Path, "global"), Path.Combine(dir.Path, "backups"));
        Process process;
        using (ProcessPriority.Scope(ProcessPriorityClass.BelowNormal))   // the background update download
        {
            process = Process.Start(new ProcessStartInfo("cmd.exe", "/c ping -n 3 127.0.0.1 >nul")
                { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true })!;
            Assert.Equal(ProcessPriorityClass.BelowNormal, process.PriorityClass);   // Windows hands a child the lowered class
            GameSession.Attach(process, game, Array.Empty<string>(), _ => { }, shared);
            process.Refresh();
            Assert.Equal(ProcessPriorityClass.AboveNormal, process.PriorityClass);   // Normal again, plus the loading boost
        }
        Assert.Null(ProcessPriority.ScopedFrom);
        using (process) await process.WaitForExitAsync();
    });

    [Fact]
    public async Task CancelStopsTheWholeGameTreeAndItsExitIsNotACrash()
    {
        using var dir = new TestDirectory();
        var game = Path.Combine(dir.Path, "profile");
        Directory.CreateDirectory(game);
        var shared = new SharedContentService(Path.Combine(dir.Path, "global"), Path.Combine(dir.Path, "backups"));
        // cmd and its child ping stand in for the starting game (about a minute); both hold the output pipe.
        using var process = Process.Start(new ProcessStartInfo("cmd.exe", "/c ping -n 60 127.0.0.1")
            { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true })!;
        var cancelledAtExit = new TaskCompletionSource<bool>();
        GameSession.Attach(process, game, Array.Empty<string>(), _ => { }, shared,
            afterExit: () => { cancelledAtExit.TrySetResult(GameSession.WasCancelled(process)); return Task.CompletedTask; });
        string? line;
        do line = await process.StandardOutput.ReadLineAsync(); while (line == ""); // ping runs once it prints
        Assert.NotNull(line);
        Assert.False(GameSession.WasCancelled(process));

        GameSession.Cancel(process);

        Assert.True(await cancelledAtExit.Task.WaitAsync(TimeSpan.FromSeconds(10)));
        Assert.NotEqual(0, process.ExitCode); // killed: without the flag the launcher would report a crash
        // The pipe closes only when ping is gone too.
        await process.StandardOutput.ReadToEndAsync().WaitAsync(TimeSpan.FromSeconds(10));
    }
}
