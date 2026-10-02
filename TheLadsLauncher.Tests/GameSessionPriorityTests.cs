using System.Diagnostics;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

[Collection(TimingSensitive.Name)]
public class GameSessionPriorityTests
{
    [Fact]
    public async Task StartupRunsBoostedAndReturnsToNormalOnceLoaded()
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
        process.Refresh();
        // Realtime when the launcher is elevated, otherwise Windows grants High.
        Assert.Contains(process.PriorityClass, new[] { ProcessPriorityClass.RealTime, ProcessPriorityClass.High });
        await Task.Delay(4500);
        process.Refresh();
        Assert.Equal(ProcessPriorityClass.Normal, process.PriorityClass);
        await process.WaitForExitAsync();
    }

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
