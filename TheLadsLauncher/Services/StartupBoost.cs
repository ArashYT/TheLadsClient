using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using CmlLib.Core.ProcessBuilder;

namespace TheLadsLauncher.Services;

/// <summary>
/// Adds our own startup agent (TheLadsBoost: replays the transformed class bytes of the last start and preloads them)
/// to a game launch. Only the jar shipped with the launcher, checked against <see cref="JarSha256"/>, is ever added, and
/// only after <see cref="LaunchService.EnsureSecurityJvmArguments"/> refused every user-supplied agent. Turned off by the
/// StartupBoost setting, LADS_BOOST=0, or a file named "off" in the game's .lads-boost folder (read by the agent itself).
/// </summary>
public static class StartupBoost
{
    /// <summary>SHA-256 of boost/lads-boost.jar. Update together with the jar (StartupBoostTests checks they match).</summary>
    public const string JarSha256 = "F2CFB81E93901D34F55A73ADDE11083BC901EB0C8979796380181F0915F60240";

    /// <summary>Fabric (Knot) and 1.8.9 Forge on RetroFuturaBootstrap: the two class loaders the agent knows how to patch.</summary>
    private static readonly HashSet<string> Versions = new(StringComparer.Ordinal) { "26.3", "1.20.1", "1.8.9" };

    public static string JarPath => Path.Combine(AppContext.BaseDirectory, "boost", "lads-boost.jar");

    public static bool Supports(string? minecraftVersion) => minecraftVersion != null && Versions.Contains(minecraftVersion);

    public static bool Enabled(LauncherSettings settings) =>
        settings.StartupBoost && Environment.GetEnvironmentVariable("LADS_BOOST") != "0";

    /// <summary>Returns the arguments to append, or none (with the reason in <paramref name="skipped"/>).</summary>
    public static string[] Arguments(LauncherSettings settings, string? gameDirectory, string? minecraftVersion, string jarPath, out string? skipped)
    {
        skipped = null;
        if (!Enabled(settings)) { skipped = "off in settings"; return Array.Empty<string>(); }
        if (!Supports(minecraftVersion)) { skipped = "not used for " + minecraftVersion; return Array.Empty<string>(); }
        if (string.IsNullOrEmpty(gameDirectory)) { skipped = "no game directory"; return Array.Empty<string>(); }
        string? hash = Sha256(jarPath);
        if (!string.Equals(hash, JarSha256, StringComparison.OrdinalIgnoreCase))
        {
            skipped = hash == null ? "agent jar missing" : "agent jar does not match its known hash";
            return Array.Empty<string>();
        }
        return new[]
        {
            "-javaagent:" + jarPath,
            "-Dlads.boost.dir=" + Path.Combine(gameDirectory, ".lads-boost"),
        };
    }

    /// <summary>Call right after <see cref="LaunchService.EnsureSecurityJvmArguments"/>.</summary>
    public static void Apply(MLaunchOption options, LauncherSettings settings, string? gameDirectory, string? minecraftVersion, Action<string>? log = null)
    {
        string[] args;
        string? skipped;
        try { args = Arguments(settings, gameDirectory, minecraftVersion, JarPath, out skipped); }
        catch (Exception ex) { args = Array.Empty<string>(); skipped = ex.Message; }
        if (args.Length == 0)
        {
            if (Supports(minecraftVersion)) log?.Invoke("[Launcher] Startup boost skipped: " + skipped);
            return;
        }
        var list = new List<MArgument>(options.ExtraJvmArguments ?? Array.Empty<MArgument>());
        foreach (var a in args) list.Add(new MArgument(a)); // one value each: paths may hold spaces
        options.ExtraJvmArguments = list.ToArray();
        log?.Invoke("[Launcher] Startup boost on");
    }

    private static string? Sha256(string path)
    {
        try
        {
            using var s = File.OpenRead(path);
            return Convert.ToHexString(SHA256.HashData(s));
        }
        catch (IOException) { return null; }
        catch (UnauthorizedAccessException) { return null; }
    }
}
