package com.thelads.core.v1_8_9.feature;

import java.util.List;
import net.minecraft.scoreboard.Score;

public interface ScoreObjectiveCache189 {
    List<Score> ladsGetCachedScores(int currentScoreboardVersion);
    void ladsSetCachedScores(List<Score> scores, int version);
    void ladsClearCachedScores();
}
