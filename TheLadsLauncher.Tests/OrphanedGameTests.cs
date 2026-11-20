using System;
using System.IO;
using TheLadsLauncher.Services;
using Xunit;

public class OrphanedGameTests
{
    [Fact]
    public void RunningGameIsWatchedAndEndedOnesAreCrashOrGone()
    {
        Assert.Equal(OrphanedGameState.Running, OrphanedGame.Classify(true, null));
        Assert.Equal(OrphanedGameState.Running, OrphanedGame.Classify(true, DateTime.Now)); // dying JVM: its exit is handled when it comes
        Assert.Equal(OrphanedGameState.MissedCrash, OrphanedGame.Classify(false, DateTime.Now));
        Assert.Equal(OrphanedGameState.Gone, OrphanedGame.Classify(false, null));
    }

    [Fact]
    public void OnlyAFreshMissedCrashMayRelaunch()
    {
        var now = new DateTime(2026, 11, 19, 19, 30, 0);
        Assert.True(OrphanedGame.MayRelaunch(now.AddSeconds(-30), now));
        Assert.True(OrphanedGame.MayRelaunch(now.AddMinutes(-2), now));
        Assert.False(OrphanedGame.MayRelaunch(now.AddMinutes(-2).AddSeconds(-1), now));
        Assert.False(OrphanedGame.MayRelaunch(now.AddHours(-2), now));
    }

    [Fact]
    public void NewestEvidenceIsTheNewestCrashFileSinceTheStartAndIgnoresOlderOnes()
    {
        string game = Path.Combine(Path.GetTempPath(), "lads-orphan-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(Path.Combine(game, "crash-reports"));
        try
        {
            var start = DateTime.Now.AddHours(-3);
            void Write(string relative, DateTime time)
            {
                string path = Path.Combine(game, relative);
                File.WriteAllText(path, "x");
                File.SetLastWriteTime(path, time);
            }
            Assert.Null(CrashReporter.NewestEvidence(game, start));
            Write("crash-reports/crash-old.txt", start.AddMinutes(-5));          // from an earlier session
            Write("hs_err_pid1.log", start.AddMinutes(-1));
            Assert.Null(CrashReporter.NewestEvidence(game, start));
            Write("crash-reports/crash-new.txt", start.AddMinutes(10));
            Write("hs_err_pid2.log", start.AddHours(2));                          // older than 10 minutes by now: still evidence of this session
            Assert.Equal(File.GetLastWriteTime(Path.Combine(game, "hs_err_pid2.log")), CrashReporter.NewestEvidence(game, start));
            Assert.Null(CrashReporter.NewestEvidence(Path.Combine(game, "missing"), start));
        }
        finally { Directory.Delete(game, true); }
    }

    [Fact]
    public void MarkerReadAllKeepsADeadGamesMarkerForTheOrphanCheck()
    {
        string game = Directory.CreateDirectory(Path.Combine(Path.GetTempPath(), "lads-marker-" + Guid.NewGuid().ToString("N"))).FullName;
        try
        {
            File.WriteAllText(RunningGameMarker.PathFor(game), "{\"pid\":2147483000,\"startedAt\":\"2026-11-19T17:03:52Z\",\"loadedMods\":[\"a\"]}");
            var all = RunningGameMarker.ReadAll(game);
            Assert.Single(all);
            Assert.Equal(2147483000, all[0].Pid);
            Assert.False(RunningGameMarker.IsAlive(all[0]));
            Assert.True(File.Exists(RunningGameMarker.PathFor(game)));   // GetRunning would delete it
            RunningGameMarker.Delete(game, 2147483000);
            Assert.False(File.Exists(RunningGameMarker.PathFor(game)));
        }
        finally { Directory.Delete(game, true); }
    }

    [Fact]
    public void CrashHookQuotesThePathAndSkipsWhatItCannotStart()
    {
        string dir = Directory.CreateDirectory(Path.Combine(Path.GetTempPath(), "lads hook " + Guid.NewGuid().ToString("N"))).FullName;
        try
        {
            string exe = Path.Combine(dir, "TheLadsLauncher.exe");
            File.WriteAllText(exe, "");
            Assert.Equal($"-XX:OnError=\"{exe}\" --game-crashed", CrashHook.Argument(exe));
            string dotnet = Path.Combine(dir, "dotnet.exe");
            File.WriteAllText(dotnet, "");
            Assert.Null(CrashHook.Argument(dotnet));                  // dev run through dotnet
            Assert.Null(CrashHook.Argument(Path.Combine(dir, "missing.exe")));
            Assert.Null(CrashHook.Argument(null));
            string percent = Path.Combine(dir, "a%pb.exe");
            File.WriteAllText(percent, "");
            Assert.Null(CrashHook.Argument(percent));                 // the JVM would replace %p
        }
        finally { Directory.Delete(dir, true); }
    }
}
