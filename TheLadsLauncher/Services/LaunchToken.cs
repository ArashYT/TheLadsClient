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
/// stdin, which TheLadsCore's session mixin swaps in. A game without that Core keeps the token on the command line.</summary>
public static class LaunchToken
{
    public const string Placeholder = "lads-stdin-token";

    private static readonly ConditionalWeakTable<Process, string> Pending = new();

    /// <summary>Call on the built game process before Start; <see cref="Send"/> after Start. The token stays on the command
    /// line when no Core in the game's mods reads stdin.</summary>
    public static void Hide(Process process, string gameDirectory, string? accessToken)
    {
        var start = process.StartInfo;
        if (string.IsNullOrEmpty(accessToken) || !CoreReadsStdin(Path.Combine(gameDirectory, "mods"))) return;
        string arguments = Regex.Replace(start.Arguments, @"(--accessToken\s+)(""[^""]*""|\S+)", "${1}" + Placeholder);
        if (accessToken.Length > 1) arguments = arguments.Replace(accessToken, Placeholder);
        if (arguments == start.Arguments) return;
        start.Arguments = arguments;
        start.RedirectStandardInput = true;
        start.StandardInputEncoding = new UTF8Encoding(false);
        Pending.AddOrUpdate(process, accessToken);
    }

    /// <summary>Writes the token <see cref="Hide"/> took off the command line to the started game, off the caller's thread.</summary>
    public static void Send(Process process)
    {
        if (!Pending.TryGetValue(process, out var token)) return;
        Pending.Remove(process);
        var input = process.StandardInput;
        _ = Task.Run(async () =>
        {
            try { await input.WriteAsync(token + "\n"); }
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
