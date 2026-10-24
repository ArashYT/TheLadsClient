using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace TheLadsLauncher.Services;

public enum BisectState
{
    Inactive,
    InProgress,
    FoundCulprit,
    Completed
}

public sealed class ModBisectSession
{
    public string GameDirectory { get; set; } = "";
    public string BackupDirectory { get; set; } = "";
    public string DisabledDirectory { get; set; } = "";
    public List<string> AllCandidateFiles { get; set; } = new();
    public List<string> CurrentSuspects { get; set; } = new();
    public List<string> CurrentActiveHalf { get; set; } = new();
    public List<string> CurrentDisabledHalf { get; set; } = new();
    public int Step { get; set; } = 1;
    public int TotalEstimatedSteps { get; set; } = 1;
    public BisectState State { get; set; } = BisectState.Inactive;
    public string? CulpritFile { get; set; }
}

/// <summary>
/// Binary search (bisect) mod isolation tool.
/// Creates a timestamped backup before touching mods, moves disabled candidates into .lads-mod-cache/bisect-disabled
/// (never overwrites or renames in place), and cleanly restores original mods on Finish/Cancel.
/// </summary>
public static class ModBisectService
{
    public const string SessionFileName = "mod-bisect-session.json";

    public static string SessionPath(string gameDirectory) =>
        Path.Combine(gameDirectory, ".lads-mod-cache", SessionFileName);

    public static string DisabledModsDirectory(string gameDirectory) =>
        Path.Combine(gameDirectory, ".lads-mod-cache", "bisect-disabled");

    public static bool HasActiveSession(string gameDirectory) =>
        File.Exists(SessionPath(gameDirectory));

    public static ModBisectSession? LoadSession(string gameDirectory)
    {
        var path = SessionPath(gameDirectory);
        if (!File.Exists(path)) return null;
        try
        {
            var json = File.ReadAllText(path);
            return JsonSerializer.Deserialize<ModBisectSession>(json);
        }
        catch
        {
            return null;
        }
    }

    public static void SaveSession(ModBisectSession session)
    {
        var path = SessionPath(session.GameDirectory);
        var dir = Path.GetDirectoryName(path);
        if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
        var json = JsonSerializer.Serialize(session, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(path, json);
    }

    /// <summary>
    /// Starts a bisect session. Backs up the entire mods folder to .lads-mod-cache/bisect-backup-&lt;timestamp&gt;/
    /// and isolates the first partition in .lads-mod-cache/bisect-disabled.
    /// </summary>
    public static ModBisectSession StartBisect(string gameDirectory, IReadOnlyList<string>? specificCandidates = null)
    {
        var modsDir = Path.Combine(gameDirectory, "mods");
        if (!Directory.Exists(modsDir)) Directory.CreateDirectory(modsDir);

        // 1. Copy the entire mods folder to a timestamped backup before any modifications
        var timestamp = DateTime.UtcNow.ToString("yyyyMMdd-HHmmss");
        var backupDir = Path.Combine(gameDirectory, ".lads-mod-cache", $"bisect-backup-{timestamp}");
        Directory.CreateDirectory(backupDir);

        foreach (var file in Directory.GetFiles(modsDir))
        {
            var fileName = Path.GetFileName(file);
            File.Copy(file, Path.Combine(backupDir, fileName), overwrite: true);
        }

        // 2. Prepare disabled candidate staging directory
        var disabledDir = DisabledModsDirectory(gameDirectory);
        if (Directory.Exists(disabledDir))
        {
            // Restore any leftover files from aborted runs back to mods before starting fresh
            foreach (var file in Directory.GetFiles(disabledDir))
            {
                var dest = Path.Combine(modsDir, Path.GetFileName(file));
                if (!File.Exists(dest)) File.Move(file, dest);
            }
        }
        Directory.CreateDirectory(disabledDir);

        // 3. Collect active candidate mods (only .jar files, excluding theladscore and .disabled)
        var candidates = specificCandidates?.ToList() ?? Directory.GetFiles(modsDir, "*.jar")
            .Select(Path.GetFileName)
            .Where(f => !string.IsNullOrEmpty(f)
                        && !f!.StartsWith("theladscore", StringComparison.OrdinalIgnoreCase)
                        && !f.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase))
            .Select(f => f!)
            .OrderBy(f => f)
            .ToList();

        if (candidates.Count == 0)
            throw new InvalidOperationException("No candidate mods to bisect. Add or enable mods in the profile first.");

        int totalSteps = Math.Max(1, (int)Math.Ceiling(Math.Log2(candidates.Count)));

        var session = new ModBisectSession
        {
            GameDirectory = gameDirectory,
            BackupDirectory = backupDir,
            DisabledDirectory = disabledDir,
            AllCandidateFiles = new List<string>(candidates),
            CurrentSuspects = new List<string>(candidates),
            Step = 1,
            TotalEstimatedSteps = totalSteps,
            State = BisectState.InProgress
        };

        ApplyStepPartition(session, modsDir, disabledDir);
        SaveSession(session);
        return session;
    }

    /// <summary>
    /// Records whether the problem still reproduces with the currently enabled active half.
    /// </summary>
    public static ModBisectSession RecordStepResult(string gameDirectory, bool stillReproduces)
    {
        var session = LoadSession(gameDirectory)
            ?? throw new InvalidOperationException("No active bisect session found.");

        if (session.State != BisectState.InProgress)
            return session;

        var modsDir = Path.Combine(gameDirectory, "mods");
        var disabledDir = DisabledModsDirectory(gameDirectory);

        if (stillReproduces)
        {
            // The problem reproduced: culprit is among the currently active half
            // Candidate files in disabledDir are innocent; move them back to modsDir
            foreach (var file in session.CurrentDisabledHalf)
            {
                var src = Path.Combine(disabledDir, file);
                var dst = Path.Combine(modsDir, file);
                if (File.Exists(src) && !File.Exists(dst))
                {
                    File.Move(src, dst);
                }
            }

            session.CurrentSuspects = new List<string>(session.CurrentActiveHalf);
        }
        else
        {
            // The problem did not reproduce: active half is innocent, culprit is in disabled half
            // Move active half to disabledDir, move disabled suspects back to modsDir to partition
            foreach (var file in session.CurrentActiveHalf)
            {
                var src = Path.Combine(modsDir, file);
                var dst = Path.Combine(disabledDir, file);
                if (File.Exists(src) && !File.Exists(dst))
                {
                    File.Move(src, dst);
                }
            }

            foreach (var file in session.CurrentDisabledHalf)
            {
                var src = Path.Combine(disabledDir, file);
                var dst = Path.Combine(modsDir, file);
                if (File.Exists(src) && !File.Exists(dst))
                {
                    File.Move(src, dst);
                }
            }

            session.CurrentSuspects = new List<string>(session.CurrentDisabledHalf);
        }

        if (session.CurrentSuspects.Count <= 1)
        {
            session.State = BisectState.FoundCulprit;
            session.CulpritFile = session.CurrentSuspects.FirstOrDefault();

            // Return all innocent files from disabledDir to modsDir
            foreach (var file in Directory.GetFiles(disabledDir))
            {
                var fileName = Path.GetFileName(file);
                if (!string.Equals(fileName, session.CulpritFile, StringComparison.OrdinalIgnoreCase))
                {
                    var dst = Path.Combine(modsDir, fileName);
                    if (!File.Exists(dst)) File.Move(file, dst);
                }
            }

            SaveSession(session);
            return session;
        }

        session.Step++;
        ApplyStepPartition(session, modsDir, disabledDir);
        SaveSession(session);
        return session;
    }

    private static void ApplyStepPartition(ModBisectSession session, string modsDir, string disabledDir)
    {
        int halfCount = Math.Max(1, session.CurrentSuspects.Count / 2);
        session.CurrentActiveHalf = session.CurrentSuspects.Take(halfCount).ToList();
        session.CurrentDisabledHalf = session.CurrentSuspects.Skip(halfCount).ToList();

        // Ensure active half is in modsDir
        foreach (var file in session.CurrentActiveHalf)
        {
            var src = Path.Combine(disabledDir, file);
            var dst = Path.Combine(modsDir, file);
            if (File.Exists(src) && !File.Exists(dst)) File.Move(src, dst);
        }

        // Move inactive half to disabledDir (without in-place renaming or overwriting)
        foreach (var file in session.CurrentDisabledHalf)
        {
            var src = Path.Combine(modsDir, file);
            var dst = Path.Combine(disabledDir, file);
            if (File.Exists(src) && !File.Exists(dst)) File.Move(src, dst);
        }
    }

    /// <summary>
    /// Finishes or cancels the bisect session and restores the starting mod set.
    /// If keepCulpritDisabled is true and a culprit was found, that culprit is left in bisect-disabled.
    /// </summary>
    public static void FinishOrCancel(string gameDirectory, bool keepCulpritDisabled = true)
    {
        var session = LoadSession(gameDirectory);
        var modsDir = Path.Combine(gameDirectory, "mods");
        var disabledDir = DisabledModsDirectory(gameDirectory);

        if (Directory.Exists(disabledDir))
        {
            foreach (var file in Directory.GetFiles(disabledDir))
            {
                var fileName = Path.GetFileName(file);
                if (keepCulpritDisabled && session?.CulpritFile != null &&
                    string.Equals(fileName, session.CulpritFile, StringComparison.OrdinalIgnoreCase))
                {
                    // Leave culprit in disabled folder
                    continue;
                }

                var dst = Path.Combine(modsDir, fileName);
                if (!File.Exists(dst))
                {
                    File.Move(file, dst);
                }
            }
        }

        var path = SessionPath(gameDirectory);
        if (File.Exists(path))
        {
            try { File.Delete(path); } catch { }
        }
    }
}
