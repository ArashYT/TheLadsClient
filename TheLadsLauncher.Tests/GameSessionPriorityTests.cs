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
}
