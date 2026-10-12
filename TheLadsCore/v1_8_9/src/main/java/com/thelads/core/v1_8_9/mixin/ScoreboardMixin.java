package com.thelads.core.v1_8_9.mixin;

import com.google.common.collect.Lists;
import com.thelads.core.v1_8_9.feature.ScoreObjectiveCache189;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Scoreboard.class)
public abstract class ScoreboardMixin {
    @Shadow @Final private Map<String, Map<ScoreObjective, Score>> entitiesScoreObjectives;

    @Unique private int ladsScoreboardVersion = 0;

    @Inject(method = {"func_96536_a", "func_96516_a", "func_178820_a", "removeObjectiveFromEntity", "removeObjective", "onScoreObjectiveAdded", "onScoreObjectiveRemoved", "setObjectiveInDisplaySlot"}, at = @At("HEAD"))
    private void ladsOnScoreboardModified(CallbackInfo ci) {
        ladsScoreboardVersion++;
    }

    @Inject(method = "removeObjective", at = @At("HEAD"))
    private void ladsOnObjectiveRemoved(ScoreObjective objective, CallbackInfo ci) {
        if (objective instanceof ScoreObjectiveCache189) {
            ((ScoreObjectiveCache189) objective).ladsClearCachedScores();
        }
    }

    @Inject(method = "getValueFromObjective", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 1))
    private void ladsOnScoreCreated(CallbackInfoReturnable<Score> cir) {
        ladsScoreboardVersion++;
    }

    @Inject(method = "getSortedScores", at = @At("HEAD"), cancellable = true)
    private void ladsGetSortedScores(ScoreObjective objective, CallbackInfoReturnable<Collection<Score>> cir) {
        if (objective instanceof ScoreObjectiveCache189) {
            List<Score> cached = ((ScoreObjectiveCache189) objective).ladsGetCachedScores(ladsScoreboardVersion);
            if (cached != null) {
                cir.setReturnValue(cached);
                return;
            }
        }
        ArrayList<Score> list = Lists.newArrayList();
        for (Map<ScoreObjective, Score> map : this.entitiesScoreObjectives.values()) {
            Score score = map.get(objective);
            if (score != null) {
                list.add(score);
            }
        }
        Collections.sort(list, Score.scoreComparator);
        List<Score> immutable = Collections.unmodifiableList(list);
        if (objective instanceof ScoreObjectiveCache189) {
            ((ScoreObjectiveCache189) objective).ladsSetCachedScores(immutable, ladsScoreboardVersion);
        }
        cir.setReturnValue(immutable);
    }
}
