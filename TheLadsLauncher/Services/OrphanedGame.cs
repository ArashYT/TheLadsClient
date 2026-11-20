using System;

namespace TheLadsLauncher.Services;

/// <summary>What a starting launcher makes of a running-game marker it did not write itself (the launcher that started the game
/// is gone: closed, killed or crashed). Pure decisions; MainWindow acts on them.</summary>
public enum OrphanedGameState
{
    /// <summary>The game is still running: the launcher watches it again, so its exit is handled like any other.</summary>
    Running,
    /// <summary>The game ended and left crash evidence written since it started: a crash nobody saw.</summary>
    MissedCrash,
    /// <summary>The game ended without crash evidence: only the stale marker is removed.</summary>
    Gone,
}

public static class OrphanedGame
{
    /// <summary>A missed crash older than this is reported but never relaunched: an old crash must not start a game by surprise.</summary>
    public static readonly TimeSpan MaxRelaunchAge = TimeSpan.FromMinutes(2);

    public static OrphanedGameState Classify(bool stillRunning, DateTime? newestEvidence) =>
        stillRunning ? OrphanedGameState.Running : newestEvidence != null ? OrphanedGameState.MissedCrash : OrphanedGameState.Gone;

    /// <summary>Whether a missed crash whose newest evidence file was written at <paramref name="newestEvidence"/> (local time) may be auto-relaunched.</summary>
    public static bool MayRelaunch(DateTime newestEvidence, DateTime now) => now - newestEvidence <= MaxRelaunchAge;
}
