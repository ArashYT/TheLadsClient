using System.Diagnostics;
using System.Text.RegularExpressions;

/// <summary>
/// Shared-server QA (LADS_VERIFY_SERVER_WORLD=qa_hud etc.): the LadsQA Paper server on Arash-HP (C:\Servers\LadsQA, port 25590,
/// ViaVersion/Backwards/Rewind for 1.8.9-26.3) instead of a singleplayer world. Started on demand, stopped when the last player leaves.
/// Singleplayer stays for SP-only modules (Stack Refill, use-while-mining), SP/LAN safety-rule paths and perf benchmarks: leave the env unset.
/// </summary>
static class QaServer
{
    const string Dir = @"C:\Servers\LadsQA";

    /// <summary>The Multiverse lane world, or null for a singleplayer run.</summary>
    public static string? World { get; } = Validated(Environment.GetEnvironmentVariable("LADS_VERIFY_SERVER_WORLD"), @"\A[a-z0-9_]{1,32}\z", "LADS_VERIFY_SERVER_WORLD");

    public static string Address { get; } = Validated(Environment.GetEnvironmentVariable("LADS_VERIFY_SERVER"), @"\A[A-Za-z0-9.-]{1,64}:[0-9]{1,5}\z", "LADS_VERIFY_SERVER") ?? "192.168.2.69:25590";

    static string? Validated(string? value, string pattern, string name) =>
        string.IsNullOrWhiteSpace(value) ? null : Regex.IsMatch(value.Trim(), pattern) ? value.Trim() : throw new ArgumentException(name + " has an invalid value '" + value + "'.");

    /// <summary>Runs a script block on Arash-HP (no window).</summary>
    static async Task<string> Hp(string script, CancellationToken ct)
    {
        var info = new ProcessStartInfo("powershell.exe") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
        foreach (var a in new[] { "-NoProfile", "-NonInteractive", "-Command", "Invoke-Command -ComputerName Arash-HP -ScriptBlock { " + script + " }" }) info.ArgumentList.Add(a);
        using var p = Process.Start(info)!;
        string output = await p.StandardOutput.ReadToEndAsync(ct);
        await p.WaitForExitAsync(ct);
        return output.Trim();
    }

    // Only validated names/worlds reach this, so the quoting stays simple.
    static Task<string> Rcon(string command, CancellationToken ct) => Hp($"& '{Dir}\\rcon.ps1' '{command}'", ct);

    /// <summary>
    /// The lane world's QA setup, server-side (idempotent, every run): the 32x32 grass platform at y=64 (as set up on LadsQA) with the spawn on it, because the
    /// superflat ground is at y=-61 and 1.8.9 (ViaRewind) renders nothing below y=0; day and clear weather held, no mobs, with the
    /// 26.3 gamerule names (the probes' old doDaylightCycle etc. are rejected by this server).
    /// </summary>
    static Task<string> SetUpWorld(string world, CancellationToken ct)
    {
        string inWorld = "execute in minecraft:" + world + " run ";
        string[] commands =
        {
            inWorld + "forceload add -16 -16 15 15", inWorld + "fill -16 64 -16 15 64 15 minecraft:grass_block", inWorld + "forceload remove -16 -16 15 15",
            $"mv setspawn {world}:0.5,65,0.5 --unsafe", inWorld + "setworldspawn 0 65 0",
            inWorld + "gamerule advance_time false", inWorld + "gamerule advance_weather false", inWorld + "gamerule spawn_mobs false",
            inWorld + "time set day", inWorld + "weather clear",
        };
        return Hp(string.Join("; ", commands.Select(c => $"& '{Dir}\\rcon.ps1' '{c}'")), ct);
    }

    /// <summary>Starts the server if it is down, whitelists and ops the QA player, then moves it to the lane world as soon as it joins.</summary>
    public static async Task PrepareAsync(string player, CancellationToken ct)
    {
        Console.WriteLine("QA server: " + await Hp($"& '{Dir}\\start.ps1'", ct));
        await Rcon("whitelist add " + player, ct);
        await Rcon("op " + player, ct);
        string world = World!;
        Console.WriteLine("QA server world setup: " + (await SetUpWorld(world, ct)).ReplaceLineEndings(" | "));
        _ = Task.Run(async () =>
        {
            // ponytail: polls `list` every second for up to 5 min; a join event hook on the server would be exact.
            for (int i = 0; i < 300 && !ct.IsCancellationRequested; i++)
            {
                try
                {
                    if (Regex.IsMatch(await Rcon("list", ct), @"\b" + player + @"\b"))
                    {
                        Console.WriteLine($"QA server: {player} joined; " + await Rcon($"mv tp {player} {world}", ct));
                        return;
                    }
                }
                catch (Exception e) when (e is not OperationCanceledException) { Console.WriteLine("QA server poll: " + e.Message); }
                await Task.Delay(1000, ct);
            }
        }, ct);
    }

    /// <summary>Stops the server once nobody is online (the last QA client is done).</summary>
    public static async Task ReleaseAsync()
    {
        try
        {
            string list = await Rcon("list", CancellationToken.None);
            if (Regex.IsMatch(list, @"There are 0\b")) Console.WriteLine("QA server: last client done, " + await Hp($"& '{Dir}\\stop.ps1'", CancellationToken.None));
            else Console.WriteLine("QA server stays up: " + list);
        }
        catch (Exception e) { Console.WriteLine("QA server release failed: " + e.Message); }
    }
}
