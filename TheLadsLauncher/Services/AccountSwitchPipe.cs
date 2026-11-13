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
    private const int MaxRequestBytes = 4096;
    private const int AcceptConcurrency = 2;
    private static readonly TimeSpan ResolveTimeout = TimeSpan.FromSeconds(30);
    private static readonly TimeSpan ClientDrainTimeout = TimeSpan.FromSeconds(2);
    private static readonly UTF8Encoding Utf8NoBom = new(false);
    private static readonly ConcurrentDictionary<string, string> GameDirectories = new();

    /// <summary>The line a game started from <paramref name="gameDirectory"/> gets on stdin: "pipe key".</summary>
    public static string HandOff(string gameDirectory)
    {
        string key = RandomHex();
        GameDirectories[key] = gameDirectory;
        return PipeName + " " + key;
    }

    /// <summary>Ends a game's key (its process exited): the pipe no longer answers it.</summary>
    public static void Revoke(string key) => GameDirectories.TryRemove(key, out _);

    /// <summary>One request line to one reply line. <paramref name="resolve"/> gets the token; <paramref name="switched"/> is told a game switched (its directory);
    /// <paramref name="remove"/> removes a saved account for a game's switcher (an empty error means done).</summary>
    public static async Task<string> AnswerAsync(string? line, Func<AccountSwitchRequest, CancellationToken, Task<AccountTokenReply>> resolve, Action<string> switched, CancellationToken token,
        Func<AccountSwitchRequest, CancellationToken, Task<AccountTokenReply>>? remove = null)
    {
        AccountTokenReply reply;
        try
        {
            using var doc = JsonDocument.Parse(line ?? "");
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                reply = AccountTokenReply.Refuse("The launcher did not understand this game.");
            }
            else
            {
                string key = root.GetProperty("key").GetString() ?? "";
                string op = (root.GetProperty("op").GetString() ?? "").Trim().ToLowerInvariant();
                if (!GameDirectories.TryGetValue(key, out string? gameDirectory))
                {
                    reply = AccountTokenReply.Refuse("The launcher did not recognise this game. Start it again from the launcher.");
                }
                else if (op == "switched")
                {
                    switched(gameDirectory);
                    reply = AccountTokenReply.Refuse("");
                }
                else if (op == "token" || (op == "remove" && remove != null))
                {
                    string username = root.GetProperty("username").GetString()?.Trim() ?? "";
                    string uuid = root.GetProperty("uuid").GetString()?.Trim() ?? "";
                    string rawType = root.GetProperty("type").GetString()?.Trim() ?? "";
                    string type = string.Equals(rawType, "microsoft", StringComparison.OrdinalIgnoreCase) ? "microsoft"
                        : string.Equals(rawType, "offline", StringComparison.OrdinalIgnoreCase) ? "offline"
                        : rawType.ToLowerInvariant();
                    reply = string.IsNullOrWhiteSpace(username)
                        ? AccountTokenReply.Refuse("The launcher did not understand this game.")
                        : await (op == "token" ? resolve : remove!)(new AccountSwitchRequest(username, uuid, type), token);
                }
                else
                {
                    reply = AccountTokenReply.Refuse("The launcher did not understand this game.");
                }
            }
        }
        catch (Exception ex) when (ex is JsonException or KeyNotFoundException or InvalidOperationException)
        {
            reply = AccountTokenReply.Refuse("The launcher did not understand this game.");
        }
        return JsonSerializer.Serialize(reply.Token != null ? new { token = reply.Token } : (object)new { error = reply.Error });
    }

    /// <summary>Serves the pipe for as long as the launcher runs. A request that takes long does not hold up another game's.</summary>
    public static void StartListener(Func<AccountSwitchRequest, CancellationToken, Task<AccountTokenReply>> resolve, Action<string> switched, string? pipeName = null, CancellationToken cancellationToken = default,
        Func<AccountSwitchRequest, CancellationToken, Task<AccountTokenReply>>? remove = null)
    {
        string targetPipe = pipeName ?? PipeName;
        for (int i = 0; i < AcceptConcurrency; i++)
        {
            _ = AcceptLoopAsync(targetPipe, resolve, switched, cancellationToken, remove);
        }
    }

    private static Task AcceptLoopAsync(string pipeName, Func<AccountSwitchRequest, CancellationToken, Task<AccountTokenReply>> resolve, Action<string> switched, CancellationToken cancellationToken,
        Func<AccountSwitchRequest, CancellationToken, Task<AccountTokenReply>>? remove) =>
        Task.Run(async () =>
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                NamedPipeServerStream? server = null;
                try
                {
                    server = new NamedPipeServerStream(pipeName, PipeDirection.InOut, NamedPipeServerStream.MaxAllowedServerInstances,
                        PipeTransmissionMode.Byte, PipeOptions.Asynchronous | PipeOptions.CurrentUserOnly);
                    await server.WaitForConnectionAsync(cancellationToken);
                    var connected = server;
                    server = null;
                    _ = ServeAsync(connected, resolve, switched, cancellationToken, remove);
                }
                catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested) { server?.Dispose(); break; }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
                {
                    server?.Dispose();
                    try { await Task.Delay(100, cancellationToken); }
                    catch (OperationCanceledException) { break; }
                }
            }
        });

    private static async Task ServeAsync(NamedPipeServerStream pipe, Func<AccountSwitchRequest, CancellationToken, Task<AccountTokenReply>> resolve, Action<string> switched, CancellationToken listenerToken,
        Func<AccountSwitchRequest, CancellationToken, Task<AccountTokenReply>>? remove)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(listenerToken);
        timeout.CancelAfter(ResolveTimeout);
        await using (pipe)
        {
            try
            {
                string? line = await ReadLineAsync(pipe, timeout.Token);
                string reply;
                try { reply = await AnswerAsync(line, resolve, switched, timeout.Token, remove); }
                catch (OperationCanceledException) when (!listenerToken.IsCancellationRequested)
                {
                    reply = JsonSerializer.Serialize(new { error = "The launcher took too long. Try again." });
                }
                byte[] bytes = Utf8NoBom.GetBytes(reply + "\n");
                await pipe.WriteAsync(bytes, CancellationToken.None);
                await pipe.FlushAsync(CancellationToken.None);

                // Wait briefly for the client to close its handle after reading '\n' so Windows does not tear down
                // the pipe buffer before synchronous Java RandomAccessFile clients finish reading long JWT replies.
                using var drain = CancellationTokenSource.CreateLinkedTokenSource(listenerToken);
                drain.CancelAfter(ClientDrainTimeout);
                byte[] eof = new byte[1];
                try { while (await pipe.ReadAsync(eof, drain.Token) > 0) { } }
                catch (Exception ex) when (ex is IOException or OperationCanceledException or ObjectDisposedException) { }
            }
            catch (Exception ex) when (ex is IOException or OperationCanceledException or ObjectDisposedException) { } // the game went away
        }
    }

    private static async Task<string?> ReadLineAsync(Stream stream, CancellationToken token)
    {
        using var line = new MemoryStream(256);
        byte[] one = new byte[1];
        while (line.Length < MaxRequestBytes)
        {
            if (await stream.ReadAsync(one, token) == 0) return line.Length == 0 ? null : Utf8NoBom.GetString(line.GetBuffer(), 0, (int)line.Length).TrimEnd('\r');
            if (one[0] == (byte)'\n') return Utf8NoBom.GetString(line.GetBuffer(), 0, (int)line.Length).TrimEnd('\r');
            line.WriteByte(one[0]);
        }
        return null;
    }

    private static string RandomHex() => Convert.ToHexString(RandomNumberGenerator.GetBytes(16));
}
