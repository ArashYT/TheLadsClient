using System;
using System.IO;
using System.Text.Json;
using CmlLib.Core.Auth;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

public class HorizonEngineTests : IDisposable
{
    private readonly string _home = Path.Combine(Path.GetTempPath(), "thelads_horizon_test_" + Guid.NewGuid().ToString("N"));

    public void Dispose()
    {
        if (Directory.Exists(_home)) Directory.Delete(_home, true);
    }

    [Fact]
    public void OnlyFor263()
    {
        Assert.True(HorizonEngine.Applies("26.3"));
        Assert.False(HorizonEngine.Applies("26.2"));
        Assert.False(HorizonEngine.Applies("1.8.9"));
        Assert.False(HorizonEngine.Applies(null));
        Assert.False(new LauncherSettings().UseHorizon);
    }

    string DropLadsDevExe()
    {
        string exe = HorizonEngine.LadsDevExe(_home);
        Directory.CreateDirectory(Path.GetDirectoryName(exe)!);
        File.WriteAllText(exe, "exe");
        return exe;
    }

    [Fact]
    public void ClientExe_ReleaseBuild_IgnoresADroppedInLadsExe()
    {
        DropLadsDevExe();

        Assert.Equal(Path.Combine("game", "horizon-client.exe"), HorizonEngine.ClientExe(_home, "game", devBuild: false));
    }

    [Fact]
    public void ClientExe_DevBuild_UsesTheDroppedInLadsExe()
    {
        string exe = DropLadsDevExe();

        Assert.Equal(exe, HorizonEngine.ClientExe(_home, "game", devBuild: true));
    }

    [Fact]
    public void ClientExe_DevBuild_WithoutADroppedInExe_UsesTheOfficialOne()
    {
        Assert.Equal(Path.Combine("game", "horizon-client.exe"), HorizonEngine.ClientExe(_home, "game", devBuild: true));
    }

    [Fact]
    public void SessionFile_HasHorizonsFields_AndReplacesALeftoverOne()
    {
        var session = new MSession("Steve", "access-token-1", "0123456789abcdef0123456789abcdef") { Xuid = "2535" };
        string first = HorizonEngine.WriteSessionFile(_home, session);
        string second = HorizonEngine.WriteSessionFile(_home, session);

        Assert.False(File.Exists(first));
        using var doc = JsonDocument.Parse(File.ReadAllText(second));
        var root = doc.RootElement;
        Assert.Equal("Steve", root.GetProperty("name").GetString());
        Assert.Equal("0123456789abcdef0123456789abcdef", root.GetProperty("uuid").GetString());
        Assert.Equal("access-token-1", root.GetProperty("access_token").GetString());
        Assert.Equal("msa", root.GetProperty("user_type").GetString());
        Assert.Equal("2535", root.GetProperty("xuid").GetString());
    }

    string Bundled(string content)
    {
        string file = Path.Combine(_home, "bundle", "lads_hud.hmod");
        Directory.CreateDirectory(Path.GetDirectoryName(file)!);
        File.WriteAllText(file, content);
        return file;
    }

    [Fact]
    public void InstallLadsMods_FirstCall_CopiesTheModAndTurnsItOn()
    {
        HorizonEngine.InstallLadsMods(_home, Bundled("hmod-v1"));

        Assert.Equal("hmod-v1", File.ReadAllText(Path.Combine(_home, "horizon-mods", "lads_hud.hmod")));
        Assert.Equal("mod.lads_hud:true\n", File.ReadAllText(Path.Combine(_home, "horizon-mods.txt")));
    }

    [Fact]
    public void InstallLadsMods_KeepsThePlayersChoiceAndOtherLines()
    {
        string settings = Path.Combine(_home, "horizon-mods.txt");
        Directory.CreateDirectory(_home);
        File.WriteAllText(settings, "mod.other:true\nmod.lads_hud:false");
        string bundled = Bundled("hmod-v1");

        HorizonEngine.InstallLadsMods(_home, bundled);
        HorizonEngine.InstallLadsMods(_home, bundled);

        Assert.Equal("mod.other:true\nmod.lads_hud:false", File.ReadAllText(settings));
    }

    [Fact]
    public void InstallLadsMods_ReplacesAChangedCopy()
    {
        HorizonEngine.InstallLadsMods(_home, Bundled("hmod-v1"));
        HorizonEngine.InstallLadsMods(_home, Bundled("hmod-v2"));

        string installed = Path.Combine(_home, "horizon-mods", "lads_hud.hmod");
        Assert.Equal("hmod-v2", File.ReadAllText(installed));
        Assert.False(File.Exists(installed + ".part"));
    }

    [Fact]
    public void InstallLadsMods_MissingBundledFile_DoesNothing()
    {
        HorizonEngine.InstallLadsMods(_home, Path.Combine(_home, "nope", "lads_hud.hmod"));

        Assert.False(Directory.Exists(_home));
    }

    [Fact]
    public void NewCrashReports_ReturnsOnlyNewClientCrashFiles_OldestFirst()
    {
        string crashDir = Path.Combine(_home, "crash-reports");
        Directory.CreateDirectory(crashDir);

        DateTime baseTime = DateTime.UtcNow.AddMinutes(-30);
        string oldReport = Path.Combine(crashDir, "crash-2026-10-26_10.00.00-client.txt");
        File.WriteAllText(oldReport, "old crash");
        File.SetLastWriteTimeUtc(oldReport, baseTime.AddMinutes(-5));

        DateTime startTime = baseTime;

        string newReport1 = Path.Combine(crashDir, "crash-2026-10-26_10.05.00-client.txt");
        File.WriteAllText(newReport1, "new crash 1");
        File.SetLastWriteTimeUtc(newReport1, startTime.AddMinutes(5));

        string newReport2 = Path.Combine(crashDir, "crash-2026-10-26_10.10.00-client.txt");
        File.WriteAllText(newReport2, "new crash 2");
        File.SetLastWriteTimeUtc(newReport2, startTime.AddMinutes(10));

        // Files that should be ignored because of pattern
        string serverReport = Path.Combine(crashDir, "crash-2026-10-26_10.08.00-server.txt");
        File.WriteAllText(serverReport, "server crash");
        File.SetLastWriteTimeUtc(serverReport, startTime.AddMinutes(8));

        string otherFile = Path.Combine(crashDir, "hs_err_pid1234.log");
        File.WriteAllText(otherFile, "hs_err");
        File.SetLastWriteTimeUtc(otherFile, startTime.AddMinutes(8));

        var result = HorizonEngine.NewCrashReports(_home, startTime);

        Assert.Equal(2, result.Length);
        Assert.Equal(newReport1, result[0]);
        Assert.Equal(newReport2, result[1]);
    }

    [Fact]
    public void NewCrashReports_MissingDirectory_ReturnsEmpty()
    {
        var result = HorizonEngine.NewCrashReports(Path.Combine(_home, "nonexistent"), DateTime.UtcNow);
        Assert.Empty(result);
    }

    [Fact]
    public async System.Threading.Tasks.Task SendNewCrashReports_WhenDisabled_SendsZero()
    {
        string crashDir = Path.Combine(_home, "crash-reports");
        Directory.CreateDirectory(crashDir);
        DateTime startTime = DateTime.UtcNow.AddMinutes(-5);

        string report = Path.Combine(crashDir, "crash-2026-10-26_12.00.00-client.txt");
        File.WriteAllText(report, "crash content");
        File.SetLastWriteTimeUtc(report, startTime.AddMinutes(1));

        int callCount = 0;
        int sent = await HorizonEngine.SendNewCrashReportsAsync(
            _home,
            startTime,
            1,
            isEnabled: () => false,
            reportSender: r => { callCount++; return System.Threading.Tasks.Task.FromResult<string?>(null); });

        Assert.Equal(0, sent);
        Assert.Equal(0, callCount);
    }

    [Fact]
    public async System.Threading.Tasks.Task SendNewCrashReports_WhenEnabled_SendsAllNewReports()
    {
        string crashDir = Path.Combine(_home, "crash-reports");
        Directory.CreateDirectory(crashDir);
        DateTime startTime = DateTime.UtcNow.AddMinutes(-5);

        string report1 = Path.Combine(crashDir, "crash-2026-10-26_12.00.00-client.txt");
        File.WriteAllText(report1, "crash 1");
        File.SetLastWriteTimeUtc(report1, startTime.AddMinutes(1));

        string report2 = Path.Combine(crashDir, "crash-2026-10-26_12.02.00-client.txt");
        File.WriteAllText(report2, "crash 2");
        File.SetLastWriteTimeUtc(report2, startTime.AddMinutes(2));

        var sentFiles = new System.Collections.Generic.List<string>();
        int sent = await HorizonEngine.SendNewCrashReportsAsync(
            _home,
            startTime,
            1,
            isEnabled: () => true,
            reportSender: r => { sentFiles.Add(r); return System.Threading.Tasks.Task.FromResult<string?>(null); });

        Assert.Equal(2, sent);
        Assert.Equal(2, sentFiles.Count);
        Assert.Equal(report1, sentFiles[0]);
        Assert.Equal(report2, sentFiles[1]);
    }

    [Fact]
    public void ScrubSessionFiles_DeletesExistingSessionFiles()
    {
        var session = new MSession("Alex", "token-xyz", "0123456789abcdef0123456789abcdef");
        string file1 = HorizonEngine.WriteSessionFile(_home, session);
        Assert.True(File.Exists(file1));

        HorizonEngine.ScrubSessionFiles(_home);
        Assert.False(File.Exists(file1));
    }

    [Fact]
    public void Start_WhenProcessFailsToStart_DeletesSessionFile()
    {
        var session = new MSession("Player", "token-fail", "0123456789abcdef0123456789abcdef");
        string nonexistentGame = Path.Combine(_home, "nonexistent-game");

        Assert.ThrowsAny<Exception>(() =>
            HorizonEngine.Start(_home, nonexistentGame, Path.Combine(_home, "resources"), session, offline: false));

        string sessionsDir = Path.Combine(_home, "sessions");
        Assert.True(!Directory.Exists(sessionsDir) || Directory.GetFiles(sessionsDir, "session-*.json").Length == 0);
    }
}
