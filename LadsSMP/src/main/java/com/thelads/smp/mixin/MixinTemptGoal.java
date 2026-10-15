package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consolidates 'noanimaltemptdelay': Eliminates the 100-tick cooldown when TemptGoal stops.
 */
@Mixin(TemptGoal.class)
public abstract class MixinTemptGoal {
    @Shadow
    private int calmDown;

    @Inject(method = "stop", at = @At("TAIL"))
    private void ladssmp$clearTemptCalmDown(CallbackInfo ci) {
        if (LadsConfig.get().features.noAnimalTemptDelay) {
            this.calmDown = 0;
        }
    }
}
