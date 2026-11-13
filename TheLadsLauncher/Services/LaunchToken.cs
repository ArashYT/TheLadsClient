using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace TheLadsLauncher.Services;

/// <summary>Keeps the account's access token off the game's command line, where Task Manager, other programs and JVM crash
/// dumps can read it: the game gets <see cref="Placeholder"/> as --accessToken and the real token as the first line of its
/// stdin, which TheLadsCore's session mixin swaps in. A game without that Core keeps the token on the command line.
/// The second line of stdin is <see cref="AccountSwitchPipe.HandOff"/>: how that game reaches this launcher for an in-place account switch.</summary>
public static class LaunchToken
{
    public const string Placeholder = "lads-stdin-token";

    // Keyed by the start info, not the Process: a relaunch with the same start info (the QA harness's one retry after a mod-loading race)
    // must get the token too, or the game waits on stdin forever (Arash-PC int QA, 2026-10-11).
    // The hand-off key is made in Send, per started process, and revoked when that process exits.
    private static readonly ConditionalWeakTable<ProcessStartInfo, Hidden> Pending = new();
    private sealed record Hidden(string Token, string? HandOffDirectory);

    /// <summary>Call on the built game process before Start; <see cref="Send"/> after Start. The token stays on the command
    /// line when no Core in the game's mods reads stdin. <paramref name="accountHandOff"/> false: the game gets no way to ask for other accounts' tokens.</summary>
    public static void Hide(Process process, string gameDirectory, string? accessToken, bool accountHandOff = true)
    {
        var start = process.StartInfo;
        if (string.IsNullOrEmpty(accessToken) || !CoreReadsStdin(Path.Combine(gameDirectory, "mods"))) return;
        string arguments = Regex.Replace(start.Arguments, @"(--accessToken\s+)(""[^""]*""|\S+)", "${1}" + Placeholder);
        if (accessToken.Length > 1) arguments = arguments.Replace(accessToken, Placeholder);
        if (arguments == start.Arguments) return;
        start.Arguments = arguments;
        start.RedirectStandardInput = true;
        start.StandardInputEncoding = new UTF8Encoding(false);
        Pending.AddOrUpdate(start, new Hidden(accessToken, accountHandOff ? gameDirectory : null));
    }

    /// <summary>Writes the token <see cref="Hide"/> took off the command line to the started game, off the caller's thread.</summary>
    public static void Send(Process process)
    {
        if (!Pending.TryGetValue(process.StartInfo, out var hidden)) return;
        string lines = hidden.Token;
        if (hidden.HandOffDirectory != null)
        {
            string handOff = AccountSwitchPipe.HandOff(hidden.HandOffDirectory);
            lines += "\n" + handOff;
            process.EnableRaisingEvents = true;
            process.Exited += (_, _) => AccountSwitchPipe.Revoke(handOff[(handOff.LastIndexOf(' ') + 1)..]);
        }
        var input = process.StandardInput;
        _ = Task.Run(async () =>
        {
            try { await input.WriteAsync(lines + "\n"); }
            catch (IOException) { } // the game exited first
            finally { try { input.Close(); } catch (IOException) { } }
        });
    }

    /// <summary>Whether an enabled Core jar in <paramref name="modsDirectory"/> has the session mixin that reads stdin.</summary>
    public static bool CoreReadsStdin(string modsDirectory) => ModpackCoreSync.CoreJars(modsDirectory).Any(jar =>
    {
        try
        {
            using var zip = ZipFile.OpenRead(jar);
            return zip.Entries.Any(e => e.FullName.StartsWith("com/thelads/core/", StringComparison.Ordinal)
                                        && e.FullName.EndsWith("/mixin/SessionTokenMixin.class", StringComparison.Ordinal));
        }
        catch (Exception ex) when (ex is IOException or InvalidDataException or UnauthorizedAccessException) { return false; }
    });
}
