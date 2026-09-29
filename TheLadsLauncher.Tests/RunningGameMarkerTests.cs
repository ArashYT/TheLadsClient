using System.Diagnostics;
using System.Text.Json.Nodes;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class RunningGameMarkerTests
{
    private static Process ExitedProcess()
    {
        var process = Process.Start(new ProcessStartInfo("cmd.exe", "/c exit 0") { UseShellExecute = false, CreateNoWindow = true })!;
        process.WaitForExit();
        return process;
    }

    [Fact]
    public void LiveJavaGameIsRunningAndKeepsItsLoadedMods()
    {
        using var dir = new TestDirectory();
        using var java = RunningJava.Start(dir.Path);
        if (java == null) return; // no Java on this machine
        RunningGameMarker.Write(dir.Path, java.Process.Id, java.Process.StartTime.ToUniversalTime(), new[] { "theladscore", "sodium" });
        var info = RunningGameMarker.GetRunning(dir.Path);
        Assert.Equal(new[] { "theladscore", "sodium" }, info!.LoadedMods);
        var json = JsonNode.Parse(File.ReadAllText(RunningGameMarker.PathFor(dir.Path)))!;
        Assert.Equal(java.Process.Id, (int)json["pid"]!);
        Assert.NotNull(json["startedAt"]);

        // Same pid and name but a different start time is pid reuse, not our game.
        RunningGameMarker.Write(dir.Path, java.Process.Id, java.Process.StartTime.ToUniversalTime().AddMinutes(-1), new[] { "x" });
        Assert.False(RunningGameMarker.IsRunning(dir.Path));
        Assert.False(File.Exists(RunningGameMarker.PathFor(dir.Path)));
    }

    [Theory]
    [InlineData("exited")]
    [InlineData("not-java")]
    [InlineData("malformed")]
    public void StaleMarkersAreDeleted(string kind)
    {
        using var dir = new TestDirectory();
        var path = RunningGameMarker.PathFor(dir.Path);
        switch (kind)
        {
            case "exited":
                using (var process = ExitedProcess())
                    RunningGameMarker.Write(dir.Path, process.Id, process.StartTime.ToUniversalTime(), Array.Empty<string>());
                break;
            case "not-java":
                var self = Process.GetCurrentProcess();
                RunningGameMarker.Write(dir.Path, self.Id, self.StartTime.ToUniversalTime(), Array.Empty<string>());
                break;
            default:
                File.WriteAllText(path, "{\"pid\":");
                break;
        }
        Assert.False(RunningGameMarker.IsRunning(dir.Path));
        Assert.False(File.Exists(path));
        Assert.False(RunningGameMarker.IsRunning(dir.Path)); // missing marker
    }

    [Fact]
    public void DeleteOnlyRemovesTheCallersOwnMarker()
    {
        using var dir = new TestDirectory();
        RunningGameMarker.Write(dir.Path, 1234, DateTime.UtcNow, Array.Empty<string>());
        RunningGameMarker.Delete(dir.Path, 999);
        Assert.True(File.Exists(RunningGameMarker.PathFor(dir.Path)));
        RunningGameMarker.Delete(dir.Path, 1234);
        Assert.False(File.Exists(RunningGameMarker.PathFor(dir.Path)));
    }

    // UI-4: two copies of one profile ("Allow launching multiple copies"); the second one closes first.
    [Fact]
    public void ClosingOneCopyKeepsTheMarkerForTheCopyStillRunning()
    {
        using var dir = new TestDirectory();
        Directory.CreateDirectory(Path.Combine(dir.Path, "a"));
        Directory.CreateDirectory(Path.Combine(dir.Path, "b"));
        using var first = RunningJava.Start(Path.Combine(dir.Path, "a"));
        if (first == null) return; // no Java on this machine
        using var second = RunningJava.Start(Path.Combine(dir.Path, "b"))!;
        RunningGameMarker.Write(dir.Path, first.Process.Id, first.Process.StartTime.ToUniversalTime(), new[] { "theladscore", "sodium" });
        RunningGameMarker.Write(dir.Path, second.Process.Id, second.Process.StartTime.ToUniversalTime(), new[] { "theladscore" });
        Assert.Equal(second.Process.Id, RunningGameMarker.GetRunning(dir.Path)!.Pid);

        RunningGameMarker.Delete(dir.Path, second.Process.Id);

        var still = RunningGameMarker.GetRunning(dir.Path);
        Assert.Equal(first.Process.Id, still?.Pid);
        Assert.Equal(new[] { "theladscore", "sodium" }, still!.LoadedMods);
        RunningGameMarker.Delete(dir.Path, first.Process.Id);
        Assert.False(File.Exists(RunningGameMarker.PathFor(dir.Path)));
    }

    [Fact]
    public async Task SessionSetsEnvironmentAndReconcilesServerEditsWhenTheGameExits()
    {
        using var dir = new TestDirectory();
        var game = Path.Combine(dir.Path, "profile");
        var shared = new SharedContentService(Path.Combine(dir.Path, "global"), Path.Combine(dir.Path, "backups"));
        var start = new ProcessStartInfo("cmd.exe", "/c ping -n 2 127.0.0.1 >nul") { UseShellExecute = false, CreateNoWindow = true };
        GameSession.Configure(start, game, shared.Root);
        Assert.Equal(game, start.Environment["THELADS_DIR"]);
        Assert.Equal(shared.Root, start.Environment[SharedContentService.RootEnvironmentVariable]);

        // Profile without LadsCore: it gets a copy, the game adds a server to that copy.
        var list = ServerListFile.CreateEmpty();
        list.Add(new NbtCompound { ["name"] = new NbtString("A"), ["ip"] = new NbtString("a.example") });
        Directory.CreateDirectory(shared.Root);
        await list.WriteAsync(shared.ServersFile);
        await shared.PrepareProfileAsync(game, "1.21.1", null, coreEnabled: false);
        list.Add(new NbtCompound { ["name"] = new NbtString("New"), ["ip"] = new NbtString("new.example") });
        await list.WriteAsync(Path.Combine(game, "servers.dat"));

        var messages = new List<string>();
        var exitWorkDone = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        using var process = Process.Start(start)!;
        // afterExit runs once the marker is deleted, the server list is reconciled and every message has been reported.
        GameSession.Attach(process, game, new[] { "theladscore" }, message => { lock (messages) messages.Add(message); }, shared,
            afterExit: () => { exitWorkDone.TrySetResult(); return Task.CompletedTask; });
        Assert.True(File.Exists(RunningGameMarker.PathFor(game)));
        await process.WaitForExitAsync();

        await exitWorkDone.Task.WaitAsync(TimeSpan.FromSeconds(15));
        Assert.False(File.Exists(RunningGameMarker.PathFor(game)));
        Assert.Contains(ServerListFile.Read(shared.ServersFile).Entries, e => e.Ip == "new.example");
        lock (messages) Assert.Contains(messages, m => m.Contains("Saved the server list changes"));
    }

    [Fact]
    public async Task CallersExitWorkRunsOnceAfterTheMarkerIsGone()
    {
        using var dir = new TestDirectory();
        var game = Path.Combine(dir.Path, "profile");
        Directory.CreateDirectory(game);
        var shared = new SharedContentService(Path.Combine(dir.Path, "global"), Path.Combine(dir.Path, "backups"));
        var calls = 0;
        var markerLeftAtExitWork = true;
        var done = new TaskCompletionSource();
        using var process = Process.Start(new ProcessStartInfo("cmd.exe", "/c ping -n 2 127.0.0.1 >nul") { UseShellExecute = false, CreateNoWindow = true })!;
        GameSession.Attach(process, game, Array.Empty<string>(), _ => { }, shared, afterExit: () =>
        {
            Interlocked.Increment(ref calls);
            markerLeftAtExitWork = File.Exists(RunningGameMarker.PathFor(game));
            done.TrySetResult();
            return Task.CompletedTask;
        });
        await process.WaitForExitAsync();
        await done.Task.WaitAsync(TimeSpan.FromSeconds(15));
        await Task.Delay(200); // a second call would have happened by now
        Assert.Equal(1, calls);
        Assert.False(markerLeftAtExitWork);
    }
}
