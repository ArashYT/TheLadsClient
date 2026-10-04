using System;
using System.Buffers.Binary;
using System.IO;
using System.IO.Pipes;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>
/// Discord Rich Presence for the launcher: the open page, a dialog, or the game being started. It talks to the Discord desktop
/// app over its local pipe (discord-ipc-0..9; frames of opcode and length, little-endian, then JSON), the protocol Lads Core's
/// IpcDiscordTransport uses in game. While a game runs the launcher disconnects, so the game's own activity is the only one.
/// ponytail: a Discord restart while idle is noticed at the next change, not at once.
/// </summary>
public static class DiscordPresence
{
    /// <summary>The Lads Discord application (Core's DiscordRpcModule uses the same one): Discord shows its name and icon.</summary>
    public const string ApplicationId = "1556191716925640744";
    public const string Site = "https://ladsclient.arashyt.ca";
    /// <summary>Discord takes at most 5 activity updates per 20 seconds; changes in between become the latest one.</summary>
    static readonly TimeSpan UpdateGap = TimeSpan.FromSeconds(5);
    static readonly long Started = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    static readonly object Gate = new();
    static readonly SemaphoreSlim Changed = new(0, 1);
    static bool _enabled, _gameRunning;
    static string _page = "Home";
    static string? _launching, _dialog;
    static Task? _worker;

    public static Action<string> Log { get; set; } = _ => { };

    public static void Enable(bool on)
    {
        lock (Gate)
        {
            _enabled = on;
            if (on) _worker ??= Task.Run(RunAsync);
        }
        Signal();
    }
    public static void Page(string page) { lock (Gate) _page = page; Signal(); }
    /// <summary>The game version being started, shown over everything else until null.</summary>
    public static void Launching(string? version) { lock (Gate) _launching = version; Signal(); }
    /// <summary>An open dialog's window title, shown over the page until null.</summary>
    public static void Dialog(string? title) { lock (Gate) _dialog = title?.Replace("The Lads Client — ", ""); Signal(); }
    public static void GameRunning(bool running) { lock (Gate) _gameRunning = running; Signal(); }

    public static string PageLabel(string page) => page switch
    {
        "Home" => "On the home page",
        "Worlds" => "Browsing worlds",
        "Servers" => "Browsing servers",
        "Modpacks" => "Browsing modpacks",
        "Profiles" => "Managing profiles",
        "Accounts" => "Managing accounts",
        "Skins" => "Changing skins",
        "Settings" => "In settings",
        "Mods" => "Managing mods",
        "BrowseMods" => "Browsing mods",
        "ModSettings" => "In mod settings",
        "Packs" => "Browsing resource packs",
        "Shaders" => "Browsing shader packs",
        "DataPacks" => "Browsing data packs",
        "Files" => "Browsing game files",
        "Gallery" => "Viewing screenshots",
        "Logs" => "Reading logs",
        _ => "In the launcher",
    };

    static void Signal() { try { Changed.Release(); } catch (SemaphoreFullException) { } }

    static (string Details, string State)? Wanted()
    {
        lock (Gate)
        {
            if (!_enabled || _gameRunning) return null;
            if (_launching != null) return ($"Launching Minecraft {_launching}", "Getting the game ready");
            return ("In the launcher", string.IsNullOrWhiteSpace(_dialog) ? PageLabel(_page) : _dialog);
        }
    }

    static async Task RunAsync()
    {
        NamedPipeClientStream? pipe = null;
        (string, string)? sent = null;
        var retry = TimeSpan.FromSeconds(5);
        while (true)
        {
            var want = Wanted();
            try
            {
                if (want == null)
                {
                    // Discord removes an activity as soon as its pipe closes.
                    pipe?.Dispose(); pipe = null; sent = null;
                }
                else if (want != sent)
                {
                    if (pipe == null)
                    {
                        pipe = await ConnectAsync();
                        if (pipe == null) { await Changed.WaitAsync(retry); retry = TimeSpan.FromSeconds(Math.Min(60, retry.TotalSeconds * 2)); continue; }
                        retry = TimeSpan.FromSeconds(5);
                    }
                    await SetActivityAsync(pipe, want.Value);
                    sent = want;
                    await Task.Delay(UpdateGap);
                    continue;
                }
            }
            catch (Exception e) when (e is IOException or TimeoutException or OperationCanceledException or InvalidDataException or JsonException)
            {
                Log($"[Discord] {e.Message} Retrying.");
                pipe?.Dispose(); pipe = null; sent = null;
                await Task.Delay(retry);
                retry = TimeSpan.FromSeconds(Math.Min(60, retry.TotalSeconds * 2));
                continue;
            }
            await Changed.WaitAsync();
        }
    }

    /// <summary>The first Discord pipe that answers the handshake with READY, or null when Discord is not running.</summary>
    static async Task<NamedPipeClientStream?> ConnectAsync()
    {
        for (int i = 0; i < 10; i++)
        {
            var pipe = new NamedPipeClientStream(".", $"discord-ipc-{i}", PipeDirection.InOut, PipeOptions.Asynchronous);
            try { await pipe.ConnectAsync(100); }
            catch (Exception e) when (e is TimeoutException or IOException) { pipe.Dispose(); continue; }
            await WriteAsync(pipe, 0, new JsonObject { ["v"] = 1, ["client_id"] = ApplicationId });
            var (op, ready) = await ReadAsync(pipe);
            if (op == 1 && (string?)ready["evt"] == "READY") { Log("[Discord] Connected."); return pipe; }
            pipe.Dispose();
            throw new InvalidDataException($"Discord refused the connection: {ready["message"]}");
        }
        return null;
    }

    public static JsonObject Activity(string details, string state) => new()
    {
        ["details"] = details,
        ["state"] = state,
        ["timestamps"] = new JsonObject { ["start"] = Started },
        ["buttons"] = new JsonArray(new JsonObject { ["label"] = "Get The Lads Client", ["url"] = Site }),
    };

    static async Task SetActivityAsync(Stream pipe, (string Details, string State) activity)
    {
        await WriteAsync(pipe, 1, new JsonObject
        {
            ["cmd"] = "SET_ACTIVITY",
            ["args"] = new JsonObject { ["pid"] = Environment.ProcessId, ["activity"] = Activity(activity.Details, activity.State) },
            ["nonce"] = Guid.NewGuid().ToString(),
        });
        while (true)
        {
            var (op, reply) = await ReadAsync(pipe);
            if (op == 3) { await WriteAsync(pipe, 4, reply); continue; } // ping: pong
            if (op == 2) throw new IOException($"Discord closed the connection: {reply["message"]}");
            if (op != 1 || (string?)reply["cmd"] != "SET_ACTIVITY") continue;
            if ((string?)reply["evt"] == "ERROR") throw new InvalidDataException($"Discord refused the activity: {reply["data"]?["message"]}");
            return;
        }
    }

    public static async Task WriteAsync(Stream pipe, int op, JsonObject json)
    {
        byte[] body = Encoding.UTF8.GetBytes(json.ToJsonString());
        // One write per frame, header included.
        var frame = new byte[8 + body.Length];
        BinaryPrimitives.WriteInt32LittleEndian(frame, op);
        BinaryPrimitives.WriteInt32LittleEndian(frame.AsSpan(4), body.Length);
        body.CopyTo(frame, 8);
        await pipe.WriteAsync(frame);
        await pipe.FlushAsync();
    }

    public static async Task<(int Op, JsonObject Json)> ReadAsync(Stream pipe)
    {
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        var header = new byte[8];
        await pipe.ReadExactlyAsync(header, timeout.Token);
        int op = BinaryPrimitives.ReadInt32LittleEndian(header), length = BinaryPrimitives.ReadInt32LittleEndian(header.AsSpan(4));
        if (length is < 0 or > 1 << 20) throw new InvalidDataException($"Bad Discord frame length {length}.");
        var body = new byte[length];
        await pipe.ReadExactlyAsync(body, timeout.Token);
        return (op, JsonNode.Parse(body) as JsonObject ?? new JsonObject());
    }
}
