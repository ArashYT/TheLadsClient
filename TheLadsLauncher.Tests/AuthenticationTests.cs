using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using CmlLib.Core.Auth;
using CmlLib.Core.Auth.Microsoft.Sessions;
using Microsoft.Identity.Client;
using TheLadsLauncher.Services;
using XboxAuthNet.Game.Accounts;
using Xunit;

namespace TheLadsLauncher.Tests;

public sealed class TestDirectory : IDisposable
{
    public string Path { get; } = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "LadsClientTests", Guid.NewGuid().ToString("N"));
    public TestDirectory() => Directory.CreateDirectory(Path);
    public void Dispose() => SafeFileOps.DeleteTree(Path);
}

/// <summary>No test reads the real Lunar Client folder: whatever resolves it gets a folder that does not exist (Lunar not
/// installed). A test that needs Lunar passes a sandbox as ProfileService.LunarRoot.</summary>
internal static class NoRealLunar
{
    [System.Runtime.CompilerServices.ModuleInitializer]
    internal static void Initialize() => Environment.SetEnvironmentVariable(GameOptionsService.LunarEnvironmentVariable,
        System.IO.Path.Combine(System.IO.Path.GetTempPath(), "LadsClientTests", "no-lunar-" + Guid.NewGuid().ToString("N")));
}

public class AuthenticationTests
{
    [Fact]
    public void OfflineUuidMatchesJavaKnownVector()
    {
        Assert.Equal("b50ad385-829d-3141-a216-7e7d7539ba7f", AccountIdentity.OfflineUuid("Notch"));
        var session = AccountIdentity.CreateOfflineSession(" Notch ");
        Assert.Equal("Notch", session.Username);
        Assert.Equal("legacy", session.UserType);
        Assert.Equal("0", session.AccessToken);
        Assert.Equal(AccountIdentity.OfflineUuid("Notch"), session.UUID);
        Assert.NotEqual(session.UUID, AccountIdentity.OfflineUuid("notch"));
    }

    [Theory]
    [InlineData("")]
    [InlineData("ab")]
    [InlineData("a name")]
    [InlineData("../../account")]
    [InlineData("abcdefghijklmnopq")]
    public void InvalidOfflineNamesAreRejected(string username) =>
        Assert.Throws<ArgumentException>(() => AccountIdentity.CreateOfflineSession(username));

    [Fact]
    public async Task UnknownAccountNeverFallsBackToOffline()
    {
        using var dir = new TestDirectory();
        var service = new AuthService(new PathService(dir.Path));
        await Assert.ThrowsAsync<InvalidOperationException>(() => service.ResolveSessionAsync("Missing"));
        await service.AddOfflineAccountAsync("Notch");
        Assert.Equal(AccountIdentity.OfflineUuid("Notch"), (await service.ResolveSessionAsync("Notch")).UUID);
        var reloaded = new AuthService(new PathService(dir.Path));
        Assert.Equal("Notch", reloaded.ActiveAccount);
    }

    [Fact]
    public async Task LaunchExportsContainChosenAltWithoutCredentials()
    {
        using var dir = new TestDirectory();
        var session = new MSession { Username = "Alt", UUID = Guid.NewGuid().ToString(), AccessToken = "SECRET-TEST-TOKEN", UserType = "msa" };
        await AccountExportService.WriteLaunchAsync(dir.Path, session, false, new[]
        {
            new AccountSummary("Main", Guid.NewGuid().ToString(), "microsoft"),
            new AccountSummary("Alt", session.UUID, "microsoft")
        });
        string profile = await File.ReadAllTextAsync(System.IO.Path.Combine(dir.Path, "lads_profile.json"));
        string accounts = await File.ReadAllTextAsync(System.IO.Path.Combine(dir.Path, "lads_accounts.json"));
        Assert.DoesNotContain("SECRET-TEST-TOKEN", profile + accounts);
        Assert.DoesNotContain("accessToken", profile + accounts);
        Assert.Equal("Alt", JsonNode.Parse(profile)!["username"]!.GetValue<string>());
        var list = JsonSerializer.Deserialize<AccountSummary[]>(accounts)!;
        Assert.Single(list.Where(a => a.selected));
        Assert.Equal("Alt", list.Single(a => a.selected).username);
        Assert.Empty(Directory.GetFiles(dir.Path, "*.tmp"));
    }

    [Fact]
    public async Task DefaultAccountPersistsAndRemovalSelectsExactlyOneRemainingAccount()
    {
        using var dir = new TestDirectory();
        var paths = new PathService(dir.Path);
        var service = new AuthService(paths);
        await service.AddOfflineAccountAsync("FirstPlayer");
        await service.AddOfflineAccountAsync("SecondPlayer");
        service.ActiveAccount = "FirstPlayer";
        var reloaded = new AuthService(paths);
        Assert.Equal("FirstPlayer", reloaded.ActiveAccount);
        Assert.Equal("FirstPlayer", Assert.Single(reloaded.GetAccounts().Where(a => a.Selected)).Username);
        Assert.True(reloaded.RemoveAccount("FirstPlayer"));
        var afterRemoval = new AuthService(paths);
        Assert.Equal("SecondPlayer", afterRemoval.ActiveAccount);
        Assert.Equal("SecondPlayer", Assert.Single(afterRemoval.GetAccounts().Where(a => a.Selected)).Username);
        Assert.True(afterRemoval.RemoveAccount("SecondPlayer"));
        var empty = new AuthService(paths);
        Assert.Null(empty.ActiveAccount);
        Assert.Empty(empty.GetAccounts());
    }

    [Fact]
    public void AccountCacheIsEncryptedAndSurvivesReload()
    {
        using var dir = new TestDirectory();
        string path = System.IO.Path.Combine(dir.Path, "accounts.dat");
        var storage = new ProtectedAccountStorage(path);
        storage.Write(JsonNode.Parse("{\"token\":\"SECRET-TEST-TOKEN\"}")!, null);
        Assert.DoesNotContain("SECRET-TEST-TOKEN", Encoding.UTF8.GetString(File.ReadAllBytes(path)));
        Assert.Equal("SECRET-TEST-TOKEN", new ProtectedAccountStorage(path).ReadAsJsonNode()!["token"]!.GetValue<string>());
        Assert.Empty(Directory.GetFiles(dir.Path, "*.tmp"));
    }

    [Fact]
    public void UnreadableCacheIsPreservedAndDoesNotRestoreLegacyAccounts()
    {
        using var dir = new TestDirectory();
        string path = System.IO.Path.Combine(dir.Path, "accounts.dat");
        string legacy = System.IO.Path.Combine(dir.Path, "legacy.json");
        File.WriteAllText(path, "damaged-data");
        File.WriteAllText(legacy, "{\"old\":{}}");
        var storage = new ProtectedAccountStorage(path, legacy);
        Assert.Empty(storage.ReadAsJsonNode()!.AsObject());
        Assert.NotNull(storage.RecoveryNotice);
        Assert.Equal("damaged-data", File.ReadAllText(Assert.Single(Directory.GetFiles(dir.Path, "*.unreadable-*"))));
        Assert.Empty(new ProtectedAccountStorage(path, legacy).ReadAsJsonNode()!.AsObject());
    }

    [Fact]
    public void LegacyMicrosoftAccountRetainsItsType()
    {
        var account = JsonSerializer.Deserialize<TheLadsLauncher.Models.AccountItem>("{\"username\":\"Tester\",\"accountType\":\"microsoft\"}")!;
        Assert.Equal("microsoft", account.AccountType);
        string written = JsonSerializer.Serialize(account);
        Assert.Contains("\"type\":\"microsoft\"", written);
        Assert.DoesNotContain("accountType", written);
    }

    [Fact]
    public void LegacyMigrationPreservesOriginalAndDoesNotResurrectClearedAccounts()
    {
        using var dir = new TestDirectory();
        string legacy = System.IO.Path.Combine(dir.Path, "legacy.json");
        string current = System.IO.Path.Combine(dir.Path, "accounts.dat");
        File.WriteAllText(legacy, "{\"account\":{\"token\":\"test\"}}");
        var storage = new ProtectedAccountStorage(current, legacy);
        Assert.NotNull(storage.ReadAsJsonNode()!["account"]);
        Assert.Equal("{\"account\":{\"token\":\"test\"}}", File.ReadAllText(legacy));
        storage.Write(new JsonObject(), null);
        Assert.Empty(new ProtectedAccountStorage(current, legacy).ReadAsJsonNode()!.AsObject());
    }

    [Fact]
    public async Task FreshSessionAvoidsEveryNetworkCall()
    {
        using var dir = new TestDirectory();
        var backend = new FakeBackend(dir.Path, DateTime.UtcNow.AddHours(1));
        var service = new MicrosoftAccountService(backend);
        for (int i = 0; i < 20; i++) Assert.Equal("Tester", (await service.AuthenticateSilently(backend.Account)).Username);
        Assert.Equal(0, backend.RefreshCalls);
    }

    [Fact]
    public async Task LicenseVerificationIsCachedOnlyAfterSuccess()
    {
        using var dir = new TestDirectory();
        var backend = new FakeBackend(dir.Path, DateTime.UtcNow.AddHours(1));
        int calls = 0;
        bool allow = false;
        var service = new MicrosoftAccountService(backend, verifyLicense: (_, _) =>
        {
            calls++;
            return allow ? Task.CompletedTask : Task.FromException(new AccountVerificationException("No license"));
        });
        await Assert.ThrowsAsync<AccountVerificationException>(() => service.AuthenticateSilently(backend.Account));
        await Assert.ThrowsAsync<AccountVerificationException>(() => service.AuthenticateSilently(backend.Account));
        Assert.Equal(2, calls);
        allow = true;
        await service.AuthenticateSilently(backend.Account);
        await service.AuthenticateSilently(backend.Account);
        Assert.Equal(3, calls);
    }

    [Fact]
    public async Task ExpiredSessionRefreshesOnceAcrossConcurrentRequests()
    {
        using var dir = new TestDirectory();
        var backend = new FakeBackend(dir.Path, DateTime.UtcNow.AddMinutes(-1));
        var service = new MicrosoftAccountService(backend);
        await Task.WhenAll(Enumerable.Range(0, 12).Select(_ => service.AuthenticateSilently(backend.Account)));
        Assert.Equal(1, backend.RefreshCalls);
        Assert.Equal(1, backend.MaximumConcurrentCalls);
    }

    [Fact]
    public async Task ForceRefreshAndCancellationAreRespected()
    {
        using var dir = new TestDirectory();
        var backend = new FakeBackend(dir.Path, DateTime.UtcNow.AddHours(1));
        var cacheReady = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var service = new MicrosoftAccountService(backend, cacheReady.Task);
        using var cancelled = new CancellationTokenSource();
        var pending = service.AuthenticateSilently(backend.Account, cancelled.Token);
        cancelled.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => pending);
        Assert.Equal(0, backend.RefreshCalls);
        cacheReady.SetResult();
        await service.AuthenticateSilently(backend.Account, forceRefresh: true);
        Assert.Equal(1, backend.RefreshCalls);
    }

    [Fact]
    public async Task WrongBrowserAccountCannotLaunchAsSelectedIdentity()
    {
        using var dir = new TestDirectory();
        var backend = new FakeBackend(dir.Path, DateTime.UtcNow.AddHours(1)) { InteractiveUuid = Guid.NewGuid().ToString() };
        var service = new MicrosoftAccountService(backend);
        var error = await Assert.ThrowsAsync<InvalidOperationException>(() => service.AuthenticateInteractively(backend.Account));
        Assert.Contains("different Minecraft account", error.Message);
    }

    [Fact]
    public async Task ClearRemovesLoadedAndPersistedAccounts()
    {
        using var dir = new TestDirectory();
        var backend = new FakeBackend(dir.Path, DateTime.UtcNow.AddHours(1));
        var service = new MicrosoftAccountService(backend);
        await service.ClearAsync();
        Assert.Empty(service.AccountManager.GetAccounts());
        await Assert.ThrowsAsync<InvalidOperationException>(() => service.AuthenticateSilently(backend.Account));
        var disk = new JsonXboxGameAccountManager(System.IO.Path.Combine(dir.Path, "accounts.json"), JEGameAccount.FromSessionStorage, null);
        Assert.Empty(disk.GetAccounts());
    }

    [Fact]
    public void OnlyInteractionFailuresReopenSignIn()
    {
        Assert.True(MicrosoftAccountService.NeedsInteractiveLogin(new MsalUiRequiredException("invalid_grant", "test")));
        Assert.False(MicrosoftAccountService.NeedsInteractiveLogin(new HttpRequestException("offline")));
        Assert.False(MicrosoftAccountService.NeedsInteractiveLogin(new OperationCanceledException()));
        Assert.False(MicrosoftAccountService.NeedsInteractiveLogin(new MsalException("invalid_client", "test")));
        Assert.DoesNotContain("SECRET-TEST-TOKEN", MicrosoftAccountService.DescribeError(new Exception("SECRET-TEST-TOKEN")));
    }

    [Fact]
    public void IncompleteMicrosoftSessionCannotReportSuccess() =>
        Assert.Throws<InvalidOperationException>(() => MicrosoftAccountService.ValidateSession(new MSession { Username = "Tester", UUID = Guid.NewGuid().ToString(), AccessToken = "0" }));

    [Fact]
    public async Task ExplicitRefreshReauthenticatesExpiredConsentAsTheSameAccount()
    {
        using var dir = new TestDirectory();
        var backend = new FakeBackend(dir.Path, DateTime.UtcNow.AddHours(1))
            { SilentFailure = new MsalUiRequiredException("invalid_grant", "test") };
        var service = new MicrosoftAccountService(backend);
        Assert.Equal(backend.Account.Identifier, (await service.RefreshOrSignInAsync(backend.Account)).UUID);
        Assert.Equal(1, backend.RefreshCalls);
        Assert.Equal(1, backend.InteractiveCalls);
        backend.InteractiveUuid = Guid.NewGuid().ToString("N");
        await Assert.ThrowsAsync<InvalidOperationException>(() => service.RefreshOrSignInAsync(backend.Account));
    }

    [Fact]
    public async Task RefreshNetworkFailureDoesNotOpenAnUnnecessarySignInWindow()
    {
        using var dir = new TestDirectory();
        var backend = new FakeBackend(dir.Path, DateTime.UtcNow.AddHours(1)) { SilentFailure = new HttpRequestException("offline") };
        var service = new MicrosoftAccountService(backend);
        await Assert.ThrowsAsync<HttpRequestException>(() => service.RefreshOrSignInAsync(backend.Account));
        Assert.Equal(0, backend.InteractiveCalls);
        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => service.RefreshOrSignInAsync(backend.Account, cancellation.Token));
        Assert.Equal(0, backend.InteractiveCalls);
    }

    private sealed class FakeBackend : IMicrosoftLoginBackend
    {
        public IXboxGameAccountManager AccountManager { get; }
        public JEGameAccount Account { get; }
        public int RefreshCalls;
        public int MaximumConcurrentCalls;
        private int _active;
        public string? InteractiveUuid;
        public Exception? SilentFailure;
        public int InteractiveCalls;

        public FakeBackend(string directory, DateTime expires)
        {
            AccountManager = new JsonXboxGameAccountManager(System.IO.Path.Combine(directory, "accounts.json"), JEGameAccount.FromSessionStorage, null);
            AccountManager.GetAccounts();
            Account = (JEGameAccount)AccountManager.NewAccount();
            Account.SessionStorage.Set("JEProfile", new JEProfile { Username = "Tester", UUID = Guid.NewGuid().ToString("N") });
            SetToken(expires);
        }

        private void SetToken(DateTime expires)
        {
            string body = Convert.ToBase64String(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new { exp = new DateTimeOffset(expires).ToUnixTimeSeconds() }))).TrimEnd('=').Replace('+', '-').Replace('/', '_');
            Account.SessionStorage.Set("JEToken", new JEToken { AccessToken = "e30." + body + ".c2ln", ExpiresOn = expires });
        }

        public async Task<MSession> AuthenticateSilently(IXboxGameAccount account, CancellationToken cancellationToken)
        {
            Interlocked.Increment(ref RefreshCalls);
            int active = Interlocked.Increment(ref _active);
            MaximumConcurrentCalls = Math.Max(MaximumConcurrentCalls, active);
            try
            {
                await Task.Delay(30, cancellationToken);
                if (SilentFailure != null) throw SilentFailure;
                SetToken(DateTime.UtcNow.AddHours(1));
                return Account.ToLauncherSession();
            }
            finally { Interlocked.Decrement(ref _active); }
        }

        public Task<MSession> AuthenticateInteractively(CancellationToken cancellationToken)
        {
            Interlocked.Increment(ref InteractiveCalls);
            var result = Account.ToLauncherSession();
            result.UUID = InteractiveUuid ?? result.UUID;
            return Task.FromResult(result);
        }
        public Task Signout(IXboxGameAccount account) { foreach (var key in account.SessionStorage.Keys.ToArray()) account.SessionStorage.Remove(key); AccountManager.SaveAccounts(); return Task.CompletedTask; }
    }
}
