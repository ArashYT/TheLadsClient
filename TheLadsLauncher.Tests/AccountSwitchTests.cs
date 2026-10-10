using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>The in-game account switcher hand-off: the request the game writes and the launcher acts on when the game has exited.</summary>
public class AccountSwitchTests
{
    private static void Request(string gameDirectory, string json) =>
        File.WriteAllText(Path.Combine(gameDirectory, AccountSwitch.RequestFile), json);

    [Fact]
    public void PrepareClearsARequestLeftByAnEarlierRun()
    {
        using var dir = new TestDirectory();
        Request(dir.Path, """{"username":"Notch","uuid":"b50ad385-829d-3141-a216-7e7d7539ba7f","type":"offline"}""");

        AccountSwitch.Prepare(dir.Path);

        Assert.False(File.Exists(Path.Combine(dir.Path, AccountSwitch.RequestFile)));
        Assert.Null(AccountSwitch.TakeRequest(dir.Path, _ => { }));
    }

    [Fact]
    public void RequestIsReadOnceAndDeleted()
    {
        using var dir = new TestDirectory();
        Request(dir.Path, """{"username":" Notch ","uuid":"b50ad385-829d-3141-a216-7e7d7539ba7f","type":"offline"}""");

        var request = AccountSwitch.TakeRequest(dir.Path, _ => { });
        Assert.NotNull(request);
        Assert.Equal("Notch", request!.Username);
        Assert.False(File.Exists(Path.Combine(dir.Path, AccountSwitch.RequestFile)));
        Assert.Null(AccountSwitch.TakeRequest(dir.Path, _ => { }));
    }

    [Theory]
    [InlineData("""{"username":""}""", "empty")]
    [InlineData("""{"username":"   "}""", "empty")]
    [InlineData("""{"username":42}""", "Could not read")]
    [InlineData("""{"other":"Notch"}""", "Could not read")]
    [InlineData("{\"username\":", "Could not read")]
    public void RefusedRequestIsDeletedAndSaysWhy(string json, string reason)
    {
        using var dir = new TestDirectory();
        var log = new List<string>();
        Request(dir.Path, json);

        Assert.Null(AccountSwitch.TakeRequest(dir.Path, log.Add));

        Assert.False(File.Exists(Path.Combine(dir.Path, AccountSwitch.RequestFile)));
        Assert.Contains(log, line => line.Contains(reason, StringComparison.OrdinalIgnoreCase));
    }

    [Fact]
    public void ApplySelectsExistingAccount()
    {
        using var dir = new TestDirectory();
        var settings = new LauncherSettings();
        var accounts = new List<AccountSummary>
        {
            new("Steve", "11111111-1111-1111-1111-111111111111", "microsoft"),
            new("Notch", AccountIdentity.OfflineUuid("Notch"), "offline")
        };
        var log = new List<string>();
        Request(dir.Path, """{"username":"Notch","uuid":"b50ad385-829d-3141-a216-7e7d7539ba7f","type":"offline"}""");

        var applied = AccountSwitch.Apply(dir.Path, accounts, settings, log.Add);

        Assert.NotNull(applied);
        Assert.Equal("Notch", applied!.username);
        Assert.False(File.Exists(Path.Combine(dir.Path, AccountSwitch.RequestFile)));
        Assert.Contains(log, line => line.Contains("Next launch will use Notch", StringComparison.OrdinalIgnoreCase));
    }

    [Fact]
    public void ApplyAddsNewOfflineAccountToSettings()
    {
        using var dir = new TestDirectory();
        var settings = new LauncherSettings();
        var accounts = new List<AccountSummary>
        {
            new("Steve", "11111111-1111-1111-1111-111111111111", "microsoft")
        };
        var log = new List<string>();
        string offlineUuid = AccountIdentity.OfflineUuid("NewPlayer");
        Request(dir.Path, $$"""{"username":"NewPlayer","uuid":"{{offlineUuid}}","type":"offline"}""");

        var applied = AccountSwitch.Apply(dir.Path, accounts, settings, log.Add);

        Assert.NotNull(applied);
        Assert.Equal("NewPlayer", applied!.username);
        Assert.Equal("offline", applied.type);
        Assert.Contains("NewPlayer", settings.OfflineAccounts);
        Assert.False(File.Exists(Path.Combine(dir.Path, AccountSwitch.RequestFile)));
        Assert.Contains(log, line => line.Contains("Next launch will use NewPlayer", StringComparison.OrdinalIgnoreCase));
    }

    [Fact]
    public void RefusedAccountLeavesSettingsAlone()
    {
        using var dir = new TestDirectory();
        var settings = new LauncherSettings();
        var accounts = new List<AccountSummary>
        {
            new("Steve", "11111111-1111-1111-1111-111111111111", "microsoft")
        };
        var log = new List<string>();
        // Offline request with mismatched UUID:
        Request(dir.Path, """{"username":"Imposter","uuid":"00000000-0000-0000-0000-000000000000","type":"offline"}""");

        var applied = AccountSwitch.Apply(dir.Path, accounts, settings, log.Add);

        Assert.Null(applied);
        Assert.Empty(settings.OfflineAccounts);
        Assert.False(File.Exists(Path.Combine(dir.Path, AccountSwitch.RequestFile)));
        Assert.Contains(log, line => line.Contains("Could not apply", StringComparison.OrdinalIgnoreCase));
    }

    [Fact]
    public void UnsavedMicrosoftAccountIsRefused()
    {
        using var dir = new TestDirectory();
        var settings = new LauncherSettings();
        var accounts = new List<AccountSummary>
        {
            new("Steve", "11111111-1111-1111-1111-111111111111", "microsoft")
        };
        var log = new List<string>();
        Request(dir.Path, """{"username":"Alex","uuid":"22222222-2222-2222-2222-222222222222","type":"microsoft"}""");

        var applied = AccountSwitch.Apply(dir.Path, accounts, settings, log.Add);

        Assert.Null(applied);
        Assert.False(File.Exists(Path.Combine(dir.Path, AccountSwitch.RequestFile)));
        Assert.Contains(log, line => line.Contains("Could not apply", StringComparison.OrdinalIgnoreCase));
    }

    /// <summary>The path the launcher takes when a game exits: the process ends, the post-exit step reads the
    /// game's request. A modpack game (Zero SMP) runs from its own folder, so the request lies there, not in a profile folder.</summary>
    [Fact]
    public async Task ExitedGameWithARequestFromZeroSmpFolderAppliesAccountSwitch()
    {
        using var dir = new TestDirectory();
        var settings = new LauncherSettings();
        var accounts = new List<AccountSummary> { new("MainPlayer", "11111111-1111-1111-1111-111111111111", "microsoft") };
        var zeroSmp = Path.Combine(dir.Path, "launcher", "modpacks", "zero-smp-1.20.1", "minecraft");
        Directory.CreateDirectory(zeroSmp);
        AccountSwitch.Prepare(zeroSmp);
        string notchUuid = AccountIdentity.OfflineUuid("Notch");
        Request(zeroSmp, $$"""{"username":"Notch","uuid":"{{notchUuid}}","type":"offline","lastUpdated":"2026-10-10T04:40:48Z"}""");
        var shared = new SharedContentService(Path.Combine(dir.Path, "global"), Path.Combine(dir.Path, "backups"));
        var switchResult = new TaskCompletionSource<string>();
        using var process = Process.Start(new ProcessStartInfo("cmd.exe", "/c exit 0") { UseShellExecute = false, CreateNoWindow = true })!;

        GameSession.Attach(process, zeroSmp, Array.Empty<string>(), _ => { }, shared, afterExit: () =>
        {
            var next = AccountSwitch.Apply(zeroSmp, accounts, settings, _ => { });
            switchResult.TrySetResult(next == null ? "none" : next.username);
            return Task.CompletedTask;
        });

        Assert.Equal("Notch", await switchResult.Task.WaitAsync(TimeSpan.FromSeconds(30)));
        Assert.False(File.Exists(Path.Combine(zeroSmp, AccountSwitch.RequestFile)));
        Assert.Contains("Notch", settings.OfflineAccounts);
    }

    [Fact]
    public void InvalidOfflineUsernameIsRefused()
    {
        using var dir = new TestDirectory();
        var settings = new LauncherSettings();
        var accounts = new List<AccountSummary>();
        var log = new List<string>();
        Request(dir.Path, """{"username":"ab","uuid":"123","type":"offline"}""");

        var applied = AccountSwitch.Apply(dir.Path, accounts, settings, log.Add);

        Assert.Null(applied);
        Assert.Empty(settings.OfflineAccounts);
        Assert.False(File.Exists(Path.Combine(dir.Path, AccountSwitch.RequestFile)));
        Assert.Contains(log, line => line.Contains("Offline usernames must contain", StringComparison.OrdinalIgnoreCase));
    }

    [Fact]
    public void ApplyMatchesMicrosoftAccountCaseInsensitiveAndWithoutHyphens()
    {
        using var dir = new TestDirectory();
        var settings = new LauncherSettings();
        var accounts = new List<AccountSummary>
        {
            new("Steve", "c8ca54dc-01e3-4bb3-824a-35e09bb3aa13", "microsoft")
        };
        var log = new List<string>();
        Request(dir.Path, """{"username":"steve","uuid":"c8ca54dc01e34bb3824a35e09bb3aa13","type":"microsoft"}""");

        var applied = AccountSwitch.Apply(dir.Path, accounts, settings, log.Add);

        Assert.NotNull(applied);
        Assert.Equal("Steve", applied!.username);
        Assert.False(File.Exists(Path.Combine(dir.Path, AccountSwitch.RequestFile)));
    }

    [Fact]
    public async Task ExitedGameWithBothAccountAndVersionSwitchFromZeroSmpFolderAppliesBoth()
    {
        using var dir = new TestDirectory();
        var profiles = new ProfileService(new PathService(Path.Combine(dir.Path, "launcher")), new SharedContentService(Path.Combine(dir.Path, "global")));
        var settings = new LauncherSettings();
        var accounts = new List<AccountSummary> { new("MainPlayer", "11111111-1111-1111-1111-111111111111", "microsoft") };
        var zeroSmp = Path.Combine(dir.Path, "launcher", "modpacks", "zero-smp-1.20.1", "minecraft");
        Directory.CreateDirectory(zeroSmp);
        VersionSwitch.Prepare(zeroSmp, "Zero SMP");
        AccountSwitch.Prepare(zeroSmp);
        File.WriteAllText(Path.Combine(zeroSmp, VersionSwitch.RequestFile), """{"version":"1.8.9","requested":"2026-10-10T04:40:48Z"}""");
        string notchUuid = AccountIdentity.OfflineUuid("Notch");
        File.WriteAllText(Path.Combine(zeroSmp, AccountSwitch.RequestFile),
            $$"""{"username":"Notch","uuid":"{{notchUuid}}","type":"offline","lastUpdated":"2026-10-10T04:40:48Z"}""");

        var shared = new SharedContentService(Path.Combine(dir.Path, "global"), Path.Combine(dir.Path, "backups"));
        var exitResult = new TaskCompletionSource<string>();
        using var process = Process.Start(new ProcessStartInfo("cmd.exe", "/c exit 0") { UseShellExecute = false, CreateNoWindow = true })!;

        GameSession.Attach(process, zeroSmp, Array.Empty<string>(), _ => { }, shared, afterExit: () =>
        {
            var nextAcct = AccountSwitch.Apply(zeroSmp, accounts, settings, _ => { });
            var nextVer = VersionSwitch.Apply(zeroSmp, profiles, _ => { });
            exitResult.TrySetResult($"{nextAcct?.username}|{nextVer?.MinecraftVersion}");
            return Task.CompletedTask;
        });

        Assert.Equal("Notch|1.8.9", await exitResult.Task.WaitAsync(TimeSpan.FromSeconds(30)));
        Assert.False(File.Exists(Path.Combine(zeroSmp, AccountSwitch.RequestFile)));
        Assert.False(File.Exists(Path.Combine(zeroSmp, VersionSwitch.RequestFile)));
        Assert.Contains("Notch", settings.OfflineAccounts);
        Assert.Equal("1.8.9", profiles.GetActiveProfile().MinecraftVersion);
    }
}
