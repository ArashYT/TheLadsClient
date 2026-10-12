package com.thelads.core.v1_8_9.feature;

import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.scoreboard.Score;

public final class ScoreboardCache189 {
    public static Collection<Score> lastSortedScoresRef = null;
    public static ArrayList<Score> cachedFilteredScores = null;
    public static ArrayList<Score> cachedSkippedScores = null;

    private ScoreboardCache189() {}

    public static void reset() {
        lastSortedScoresRef = null;
        cachedFilteredScores = null;
        cachedSkippedScores = null;
    }
}
