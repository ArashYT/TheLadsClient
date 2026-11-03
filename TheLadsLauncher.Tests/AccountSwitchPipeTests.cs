using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Pipes;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>A running game's in-place account switch: what the launcher answers on the pipe.</summary>
public class AccountSwitchPipeTests
{
    private const string Secret = "eyJ.secret-token_3";

    private static string Request(string key, string op = "token", string type = "microsoft", string username = "Steve", string uuid = "11111111111111111111111111111111") =>
        JsonSerializer.Serialize(new { key, op, username, uuid, type });

    private static string KeyOf(string handOff) => handOff.Split(' ')[1];

    private static Task<string> Answer(string? line, Action<string>? switched = null) =>
        AccountSwitchPipe.AnswerAsync(line, (_, _) => Task.FromResult(AccountTokenReply.Of(Secret)), switched ?? (_ => { }), CancellationToken.None);

    [Fact]
    public async Task AKnownKeyGetsTheToken()
    {
        string reply = await Answer(Request(KeyOf(AccountSwitchPipe.HandOff("game"))));
        Assert.Equal(Secret, JsonDocument.Parse(reply).RootElement.GetProperty("token").GetString());
    }

    [Theory]
    [InlineData("""{"key":"wrong","op":"token","username":"Steve","uuid":"1","type":"microsoft"}""")]
    [InlineData("""{"op":"token"}""")]
    [InlineData("not json")]
    [InlineData("null")]
    [InlineData("[]")]
    [InlineData("")]
    [InlineData(null)]
    public async Task AnythingElseGetsNoToken(string? line)
    {
        string reply = await Answer(line);
        Assert.DoesNotContain(Secret, reply);
        Assert.True(JsonDocument.Parse(reply).RootElement.TryGetProperty("error", out _));
    }

    [Fact]
    public async Task EmptyUsernameOrUnknownOpGetsNoToken()
    {
        string key = KeyOf(AccountSwitchPipe.HandOff("game"));
        string blankUser = await Answer(Request(key, "token", "microsoft", "   "));
        Assert.DoesNotContain(Secret, blankUser);
        Assert.True(JsonDocument.Parse(blankUser).RootElement.TryGetProperty("error", out _));

        string badOp = await Answer(Request(key, "delete"));
        Assert.DoesNotContain(Secret, badOp);
        Assert.True(JsonDocument.Parse(badOp).RootElement.TryGetProperty("error", out _));
    }

    [Fact]
    public async Task UppercaseMicrosoftTypeAndOpAreNormalisedForResolver()
    {
        string key = KeyOf(AccountSwitchPipe.HandOff("game"));
        AccountSwitchRequest? captured = null;
        string reply = await AccountSwitchPipe.AnswerAsync(
            Request(key, op: "TOKEN", type: "Microsoft", username: "Arash", uuid: "069a79f444e94726a5befca90e38aaf5"),
            (req, _) =>
            {
                captured = req;
                return Task.FromResult(req.Type == "microsoft" ? AccountTokenReply.Of(Secret) : AccountTokenReply.Refuse("wrong type"));
            },
            _ => { },
            CancellationToken.None);

        Assert.NotNull(captured);
        Assert.Equal("microsoft", captured!.Type);
        Assert.Equal("Arash", captured.Username);
        Assert.Equal(Secret, JsonDocument.Parse(reply).RootElement.GetProperty("token").GetString());
    }

    [Fact]
    public async Task SwitchedNamesTheGameDirectoryOfTheKey()
    {
        string? told = null;
        await Answer(Request(KeyOf(AccountSwitchPipe.HandOff("the game folder")), "switched"), dir => told = dir);
        Assert.Equal("the game folder", told);
    }

    [Fact]
    public void AReplyDoesNotPrintItsToken() => Assert.DoesNotContain(Secret, AccountTokenReply.Of(Secret).ToString());

    [Fact]
    public async Task TheRealPipeAnswersTheGame()
    {
        string pipe = "TheLadsClient-AccountTest-" + Guid.NewGuid().ToString("N");
        using var stop = new CancellationTokenSource();
        AccountSwitchPipe.StartListener((_, _) => Task.FromResult(AccountTokenReply.Of(Secret)), _ => { }, pipe, stop.Token);
        using var client = new NamedPipeClientStream(".", pipe, PipeDirection.InOut, PipeOptions.CurrentUserOnly);
        await client.ConnectAsync(5000);
        var writer = new StreamWriter(client) { AutoFlush = true };
        await writer.WriteLineAsync(Request(KeyOf(AccountSwitchPipe.HandOff("game"))));
        string? reply = await new StreamReader(client).ReadLineAsync();
        stop.Cancel();
        Assert.Equal(Secret, JsonDocument.Parse(reply!).RootElement.GetProperty("token").GetString());
    }

    [Fact]
    public async Task ExpiredMicrosoftAccountRefreshesPersistsEncryptedTokenAndUpdatesActiveAccount()
    {
        using var dir = new TestDirectory();
        string gameDir = Path.Combine(dir.Path, "instance");
        Directory.CreateDirectory(gameDir);
        string storagePath = Path.Combine(dir.Path, "accounts.dat");
        var storage = new ProtectedAccountStorage(storagePath);

        const string msUuid = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        string refreshedJwt = "eyJhbGciOiJIUzI1NiJ9." + new string('A', 1400) + ".sig_refreshed";

        storage.Write(new JsonObject
        {
            ["steve"] = new JsonObject
            {
                ["username"] = "Steve",
                ["uuid"] = msUuid,
                ["accessToken"] = "expired-jwt",
                ["expiresAt"] = DateTimeOffset.UtcNow.AddHours(-2).ToString("O")
            }
        }, null);

        var settings = new LauncherSettings();
        var savedAccounts = new List<AccountSummary>
        {
            new("OldAccount", "22222222-2222-2222-2222-222222222222", "microsoft"),
            new("Steve", msUuid, "microsoft")
        };
        var logs = new List<string>();
        var activeSwitched = new TaskCompletionSource<AccountSummary?>(TaskCreationOptions.RunContinuationsAsynchronously);
        int refreshCalls = 0;

        string pipe = "TheLadsClient-MsRefresh-" + Guid.NewGuid().ToString("N");
        string key = KeyOf(AccountSwitchPipe.HandOff(gameDir));
        using var stop = new CancellationTokenSource();

        AccountSwitchPipe.StartListener(
            (req, _) =>
            {
                if (req.Type != "microsoft") return Task.FromResult(AccountTokenReply.Refuse("Unexpected offline request."));
                var cache = storage.ReadAsJsonNode()?.AsObject();
                var entry = cache?["steve"]?.AsObject();
                if (entry == null || !string.Equals(entry["uuid"]?.GetValue<string>()?.Replace("-", ""), req.Uuid.Replace("-", ""), StringComparison.OrdinalIgnoreCase))
                    return Task.FromResult(AccountTokenReply.Refuse($"{req.Username} is no longer saved in the launcher."));

                var expiresAt = DateTimeOffset.Parse(entry["expiresAt"]!.GetValue<string>());
                if (expiresAt <= DateTimeOffset.UtcNow.AddMinutes(5))
                {
                    Interlocked.Increment(ref refreshCalls);
                    entry["accessToken"] = refreshedJwt;
                    entry["expiresAt"] = DateTimeOffset.UtcNow.AddHours(24).ToString("O");
                    storage.Write(cache!, null);
                }
                return Task.FromResult(AccountTokenReply.Of(entry["accessToken"]!.GetValue<string>()));
            },
            switchedDir =>
            {
                var applied = AccountSwitch.Apply(switchedDir, savedAccounts, settings, logs.Add, offlineAllowed: false);
                activeSwitched.TrySetResult(applied);
            },
            pipe,
            stop.Token);

        try
        {
            // Connect using synchronous Win32 pipe path semantics (matching Java RandomAccessFile("\\.\pipe\...", "rw")).
            string tokenReplyLine;
            using (var rawPipe = new NamedPipeClientStream(".", pipe, PipeDirection.InOut, PipeOptions.CurrentUserOnly))
            {
                await rawPipe.ConnectAsync(5000);
                byte[] reqBytes = Encoding.UTF8.GetBytes(Request(key, "token", "Microsoft", "Steve", msUuid.Replace("-", "")) + "\n");
                await rawPipe.WriteAsync(reqBytes);
                await rawPipe.FlushAsync();

                // Read in small chunks to exercise server drain behavior on large (~1.4 KB) Microsoft JWT tokens.
                using var ms = new MemoryStream();
                byte[] chunk = new byte[64];
                while (true)
                {
                    int n = await rawPipe.ReadAsync(chunk);
                    if (n <= 0) break;
                    int nl = Array.IndexOf(chunk, (byte)'\n', 0, n);
                    if (nl >= 0)
                    {
                        ms.Write(chunk, 0, nl);
                        break;
                    }
                    ms.Write(chunk, 0, n);
                }
                tokenReplyLine = Encoding.UTF8.GetString(ms.ToArray()).Trim();
            }

            Assert.Equal(refreshedJwt, JsonDocument.Parse(tokenReplyLine).RootElement.GetProperty("token").GetString());
            Assert.Equal(1, refreshCalls);

            // Verify DPAPI storage persisted the refreshed token encrypted at rest.
            string rawDiskText = Encoding.UTF8.GetString(File.ReadAllBytes(storagePath));
            Assert.DoesNotContain(refreshedJwt, rawDiskText);
            Assert.Equal(refreshedJwt, storage.ReadAsJsonNode()?["steve"]?["accessToken"]?.GetValue<string>());

            // Immediately write lads_next_account.json and notify "switched" on a second pipe connection.
            File.WriteAllText(
                Path.Combine(gameDir, AccountSwitch.RequestFile),
                JsonSerializer.Serialize(new { username = "Steve", uuid = msUuid, type = "microsoft" }));

            using (var switchedPipe = new NamedPipeClientStream(".", pipe, PipeDirection.InOut, PipeOptions.CurrentUserOnly))
            {
                await switchedPipe.ConnectAsync(5000);
                byte[] swBytes = Encoding.UTF8.GetBytes(Request(key, "switched") + "\n");
                await switchedPipe.WriteAsync(swBytes);
                using var reader = new StreamReader(switchedPipe, Encoding.UTF8, false, 256, leaveOpen: true);
                _ = await reader.ReadLineAsync();
            }

            var active = await activeSwitched.Task.WaitAsync(TimeSpan.FromSeconds(5));
            Assert.NotNull(active);
            Assert.Equal("Steve", active!.username);
            Assert.Equal("microsoft", active.type);
            Assert.False(File.Exists(Path.Combine(gameDir, AccountSwitch.RequestFile)));
        }
        finally
        {
            stop.Cancel();
        }
    }

    [Fact]
    public void AccountSwitchMatchesRenamedMicrosoftAccountByUuidAndCaseInsensitiveType()
    {
        var settings = new LauncherSettings();
        var accounts = new List<AccountSummary>
        {
            new("NewProfileName", "069a79f4-44e9-4726-a5be-fca90e38aaf5", "microsoft")
        };
        var logs = new List<string>();

        var applied = AccountSwitch.ValidateAndApply(
            new AccountSwitchRequest("OldProfileName", "069a79f444e94726a5befca90e38aaf5", "Microsoft"),
            accounts,
            settings,
            logs.Add,
            offlineAllowed: false);

        Assert.NotNull(applied);
        Assert.Equal("NewProfileName", applied!.username);
    }
}
