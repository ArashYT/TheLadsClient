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
}
