package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consolidates 'skeletonaifix': Prevents skeletons from circle-strafing indefinitely when shooting bows.
 */
@Mixin(RangedBowAttackGoal.class)
public abstract class MixinRangedBowAttackGoal {
    @Shadow
    private int strafingTime;

    @Inject(method = "tick", at = @At("HEAD"))
    private void ladssmp$disableSkeletonBowStrafing(CallbackInfo ci) {
        if (LadsConfig.get().features.skeletonAiFix) {
            this.strafingTime = Integer.MIN_VALUE;
        }
    }
}
