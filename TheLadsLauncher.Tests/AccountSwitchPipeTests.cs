using System;
using System.IO;
using System.IO.Pipes;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Services;
using Xunit;

namespace TheLadsLauncher.Tests;

/// <summary>A running game's in-place account switch: what the launcher answers on the pipe.</summary>
public class AccountSwitchPipeTests
{
    private const string Secret = "eyJ.secret-token_3";

    private static string Request(string key, string op = "token") =>
        JsonSerializer.Serialize(new { key, op, username = "Steve", uuid = "11111111111111111111111111111111", type = "microsoft" });

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
    [InlineData("")]
    [InlineData(null)]
    public async Task AnythingElseGetsNoToken(string? line)
    {
        string reply = await Answer(line);
        Assert.DoesNotContain(Secret, reply);
        Assert.True(JsonDocument.Parse(reply).RootElement.TryGetProperty("error", out _));
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
}
