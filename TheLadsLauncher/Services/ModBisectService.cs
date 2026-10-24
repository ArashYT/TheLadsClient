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
    public List<string> AllCandidateFiles { get; set; } = new();
    public List<string> CurrentSuspects { get; set; } = new();
    public List<string> CurrentActiveHalf { get; set; } = new();
    public List<string> OriginallyDisabledFiles { get; set; } = new();
    public int Step { get; set; } = 1;
    public int TotalEstimatedSteps { get; set; } = 1;
    public BisectState State { get; set; } = BisectState.Inactive;
    public string? CulpritFile { get; set; }
}

/// <summary>
/// Binary search (bisect) debugging tool for mods: automatically isolates crash-causing or low-FPS mods
/// by dividing candidate mods into halves until the culprit mod is identified, then restoring the profile state.
/// </summary>
public static class ModBisectService
{
    public const string SessionFileName = "mod-bisect-session.json";

    public static string SessionPath(string gameDirectory) =>
        Path.Combine(gameDirectory, ".lads-mod-cache", SessionFileName);

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
    /// Starts a bisect session with candidate mod filenames from mods/.
    /// </summary>
    public static ModBisectSession StartBisect(string gameDirectory, IReadOnlyList<string>? specificCandidates = null)
    {
        var modsDir = Path.Combine(gameDirectory, "mods");
        if (!Directory.Exists(modsDir)) Directory.CreateDirectory(modsDir);

        var allModFiles = Directory.GetFiles(modsDir, "*.jar*")
            .Select(Path.GetFileName)
            .Where(f => !string.IsNullOrEmpty(f) && !f!.StartsWith("theladscore", StringComparison.OrdinalIgnoreCase))
            .Select(f => f!)
            .ToList();

        var originallyDisabled = allModFiles
            .Where(f => f.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase))
            .ToList();

        var candidates = specificCandidates?.ToList()
            ?? allModFiles.Where(f => !f.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)).ToList();

        if (candidates.Count == 0)
            throw new InvalidOperationException("No candidate mods to bisect. Enable or add mods first.");

        int totalSteps = Math.Max(1, (int)Math.Ceiling(Math.Log2(candidates.Count)));

        var session = new ModBisectSession
        {
            GameDirectory = gameDirectory,
            AllCandidateFiles = candidates,
            CurrentSuspects = new List<string>(candidates),
            OriginallyDisabledFiles = originallyDisabled,
            Step = 1,
            TotalEstimatedSteps = totalSteps,
            State = BisectState.InProgress
        };

        ApplyStepPartition(session);
        SaveSession(session);
        return session;
    }

    /// <summary>
    /// Records whether the problem still occurs with the currently active half of mods.
    /// </summary>
    public static ModBisectSession RecordStepResult(string gameDirectory, bool problemStillOccurs)
    {
        var session = LoadSession(gameDirectory)
            ?? throw new InvalidOperationException("No active bisect session found.");

        if (session.State != BisectState.InProgress)
            return session;

        if (problemStillOccurs)
        {
            // Culprit is inside the active half
            session.CurrentSuspects = new List<string>(session.CurrentActiveHalf);
        }
        else
        {
            // Culprit is inside the inactive half
            session.CurrentSuspects = session.CurrentSuspects
                .Except(session.CurrentActiveHalf, StringComparer.OrdinalIgnoreCase)
                .ToList();
        }

        if (session.CurrentSuspects.Count <= 1)
        {
            session.State = BisectState.FoundCulprit;
            session.CulpritFile = session.CurrentSuspects.FirstOrDefault();
            SaveSession(session);
            return session;
        }

        session.Step++;
        ApplyStepPartition(session);
        SaveSession(session);
        return session;
    }

    private static void ApplyStepPartition(ModBisectSession session)
    {
        int halfCount = Math.Max(1, session.CurrentSuspects.Count / 2);
        session.CurrentActiveHalf = session.CurrentSuspects.Take(halfCount).ToList();
        var disabledHalf = session.CurrentSuspects.Skip(halfCount).ToHashSet(StringComparer.OrdinalIgnoreCase);

        var modsDir = Path.Combine(session.GameDirectory, "mods");
        if (!Directory.Exists(modsDir)) return;

        // In the suspects pool: active half is enabled (.jar), disabled half is .jar.disabled
        foreach (var mod in session.CurrentSuspects)
        {
            string baseName = mod.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)
                ? mod[..^".disabled".Length] : mod;

            string enabledPath = Path.Combine(modsDir, baseName);
            string disabledPath = Path.Combine(modsDir, baseName + ".disabled");

            if (disabledHalf.Contains(mod) || disabledHalf.Contains(baseName))
            {
                // Should be disabled
                if (File.Exists(enabledPath)) File.Move(enabledPath, disabledPath, overwrite: true);
            }
            else
            {
                // Should be enabled
                if (File.Exists(disabledPath)) File.Move(disabledPath, enabledPath, overwrite: true);
            }
        }
    }

    /// <summary>
    /// Finishes or cancels the bisect session and restores the mod files to their original states.
    /// If keepCulpritDisabled is true and a culprit was found, that culprit is left disabled.
    /// </summary>
    public static void FinishOrCancel(string gameDirectory, bool keepCulpritDisabled = true)
    {
        var session = LoadSession(gameDirectory);
        var modsDir = Path.Combine(gameDirectory, "mods");

        if (session != null && Directory.Exists(modsDir))
        {
            var origDisabledSet = new HashSet<string>(session.OriginallyDisabledFiles.Select(f =>
                f.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase) ? f[..^".disabled".Length] : f),
                StringComparer.OrdinalIgnoreCase);

            string? culpritBase = session.CulpritFile != null
                ? (session.CulpritFile.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)
                    ? session.CulpritFile[..^".disabled".Length]
                    : session.CulpritFile)
                : null;

            foreach (var mod in session.AllCandidateFiles.Concat(session.OriginallyDisabledFiles).Distinct(StringComparer.OrdinalIgnoreCase))
            {
                string baseName = mod.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase) ? mod[..^".disabled".Length] : mod;
                string enabledPath = Path.Combine(modsDir, baseName);
                string disabledPath = Path.Combine(modsDir, baseName + ".disabled");

                bool shouldBeDisabled = origDisabledSet.Contains(baseName);
                if (keepCulpritDisabled && culpritBase != null && string.Equals(baseName, culpritBase, StringComparison.OrdinalIgnoreCase))
                {
                    shouldBeDisabled = true;
                }

                if (shouldBeDisabled)
                {
                    if (File.Exists(enabledPath)) File.Move(enabledPath, disabledPath, overwrite: true);
                }
                else
                {
                    if (File.Exists(disabledPath)) File.Move(disabledPath, enabledPath, overwrite: true);
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
