using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.IO.Pipes;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>A fresh access token, or the reason there is none. Never prints the token.</summary>
public sealed class AccountTokenReply
{
    public string? Token { get; private init; }
    public string? Error { get; private init; }
    public static AccountTokenReply Of(string token) => new() { Token = token };
    public static AccountTokenReply Refuse(string error) => new() { Error = error };
    public override string ToString() => Error ?? "token";
}

/// <summary>
/// A running game's way to switch account in place. The launcher keeps owning the accounts and their refresh tokens (DPAPI, ProtectedAccountStorage);
/// a game started with <see cref="LaunchToken"/> gets a per-launch key on stdin, beside its launch token, and with it can ask this pipe for a fresh Minecraft
/// access token of a saved account. The pipe only opens to this Windows user (CurrentUserOnly), its name is random per launcher run, and the key is never on disk.
/// </summary>
public static class AccountSwitchPipe
{
    public static readonly string PipeName = "TheLadsClient-Account-" + RandomHex();
    private const int MaxRequestChars = 4096;
    private static readonly TimeSpan ResolveTimeout = TimeSpan.FromSeconds(30);
    private static readonly ConcurrentDictionary<string, string> GameDirectories = new();

    /// <summary>The line a game started from <paramref name="gameDirectory"/> gets on stdin: "pipe key".</summary>
    public static string HandOff(string gameDirectory)
    {
        string key = RandomHex();
        GameDirectories[key] = gameDirectory;
        return PipeName + " " + key;
    }

    /// <summary>One request line to one reply line. <paramref name="resolve"/> gets the token; <paramref name="switched"/> is told a game switched (its directory).</summary>
    public static async Task<string> AnswerAsync(string? line, Func<AccountSwitchRequest, CancellationToken, Task<AccountTokenReply>> resolve, Action<string> switched, CancellationToken token)
    {
        AccountTokenReply reply;
        try
        {
            using var doc = JsonDocument.Parse(line ?? "");
            var root = doc.RootElement;
            string key = root.GetProperty("key").GetString() ?? "";
            string op = root.GetProperty("op").GetString() ?? "";
            if (!GameDirectories.TryGetValue(key, out string? gameDirectory)) reply = AccountTokenReply.Refuse("The launcher did not recognise this game. Start it again from the launcher.");
            else if (op == "switched")
            {
                switched(gameDirectory);
                reply = AccountTokenReply.Refuse("");
            }
            else if (op == "token")
            {
                string username = root.GetProperty("username").GetString()?.Trim() ?? "";
                string uuid = root.GetProperty("uuid").GetString()?.Trim() ?? "";
                string type = root.GetProperty("type").GetString()?.Trim() ?? "";
                reply = await resolve(new AccountSwitchRequest(username, uuid, type), token);
            }
            else reply = AccountTokenReply.Refuse("The launcher did not understand this game.");
        }
        catch (Exception ex) when (ex is JsonException or KeyNotFoundException or InvalidOperationException)
        {
            reply = AccountTokenReply.Refuse("The launcher did not understand this game.");
        }
        return JsonSerializer.Serialize(reply.Token != null ? new { token = reply.Token } : (object)new { error = reply.Error });
    }

    /// <summary>Serves the pipe for as long as the launcher runs. A request that takes long does not hold up another game's.</summary>
    public static void StartListener(Func<AccountSwitchRequest, CancellationToken, Task<AccountTokenReply>> resolve, Action<string> switched, string? pipeName = null, CancellationToken cancellationToken = default)
    {
        _ = Task.Run(async () =>
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                NamedPipeServerStream? server = null;
                try
                {
                    server = new NamedPipeServerStream(pipeName ?? PipeName, PipeDirection.InOut, NamedPipeServerStream.MaxAllowedServerInstances,
                        PipeTransmissionMode.Byte, PipeOptions.Asynchronous | PipeOptions.CurrentUserOnly);
                    await server.WaitForConnectionAsync(cancellationToken);
                    var connected = server;
                    server = null;
                    _ = ServeAsync(connected, resolve, switched);
                }
                catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested) { server?.Dispose(); break; }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
                {
                    server?.Dispose();
                    try { await Task.Delay(500, cancellationToken); }
                    catch (OperationCanceledException) { break; }
                }
            }
        });
    }

    private static async Task ServeAsync(NamedPipeServerStream pipe, Func<AccountSwitchRequest, CancellationToken, Task<AccountTokenReply>> resolve, Action<string> switched)
    {
        using var timeout = new CancellationTokenSource(ResolveTimeout);
        await using (pipe)
        {
            try
            {
                string? line = await ReadLineAsync(pipe, timeout.Token);
                string reply;
                try { reply = await AnswerAsync(line, resolve, switched, timeout.Token); }
                catch (OperationCanceledException) { reply = JsonSerializer.Serialize(new { error = "The launcher took too long. Try again." }); }
                byte[] bytes = new UTF8Encoding(false).GetBytes(reply + "\n");
                await pipe.WriteAsync(bytes, CancellationToken.None);
                await pipe.FlushAsync(CancellationToken.None);
            }
            catch (Exception ex) when (ex is IOException or OperationCanceledException or ObjectDisposedException) { } // the game went away
        }
    }

    private static async Task<string?> ReadLineAsync(Stream stream, CancellationToken token)
    {
        var line = new StringBuilder();
        byte[] one = new byte[1];
        while (line.Length < MaxRequestChars)
        {
            if (await stream.ReadAsync(one, token) == 0) return null;
            if (one[0] == '\n') return line.ToString();
            line.Append((char)one[0]);
        }
        return null;
    }

    private static string RandomHex() => Convert.ToHexString(RandomNumberGenerator.GetBytes(16));
}
