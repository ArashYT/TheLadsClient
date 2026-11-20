using System;
using System.IO;

namespace TheLadsLauncher.Services;

/// <summary>
/// The JVM's -XX:OnError hook: when the game dies natively (driver fault, JVM bug) with no launcher left to see it, the dying JVM starts
/// the launcher with <see cref="Flag"/>. A launcher that is already running ignores it (it is watching the game); otherwise one starts and
/// finds the dead game through its running marker (<see cref="OrphanedGame"/>).
/// </summary>
public static class CrashHook
{
    public const string Flag = "--game-crashed";

    /// <summary>The JVM option for <paramref name="launcherExe"/>, or null when there is no launcher exe to start: a run through
    /// <c>dotnet</c>, a missing file, or a path the JVM would change (it replaces "%p" in the command with the process id). The quoted
    /// path holds spaces (<c>C:\Program Files</c>, a user name with a space); the JVM runs the text as one command line.</summary>
    public static string? Argument(string? launcherExe)
    {
        if (string.IsNullOrWhiteSpace(launcherExe) || launcherExe.Contains('%') || launcherExe.Contains('"') || !File.Exists(launcherExe)) return null;
        if (Path.GetFileNameWithoutExtension(launcherExe).Equals("dotnet", StringComparison.OrdinalIgnoreCase)) return null;
        return $"-XX:OnError=\"{launcherExe}\" {Flag}";
    }
}
