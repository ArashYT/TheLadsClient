package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.ScoreObjectiveCache189;
import java.util.List;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ScoreObjective.class)
public abstract class ScoreObjectiveMixin implements ScoreObjectiveCache189 {
    @Unique private List<Score> ladsCachedSortedScores;
    @Unique private int ladsCachedScoreboardVersion = -1;

    @Override
    public List<Score> ladsGetCachedScores(int currentScoreboardVersion) {
        if (ladsCachedScoreboardVersion == currentScoreboardVersion && ladsCachedSortedScores != null) {
            return ladsCachedSortedScores;
        }
        return null;
    }

    @Override
    public void ladsSetCachedScores(List<Score> scores, int version) {
        this.ladsCachedSortedScores = scores;
        this.ladsCachedScoreboardVersion = version;
    }

    @Override
    public void ladsClearCachedScores() {
        this.ladsCachedSortedScores = null;
        this.ladsCachedScoreboardVersion = -1;
    }
}
