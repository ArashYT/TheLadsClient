package com.thelads.core.v1_21_11.embedded.lazyai.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_21_11.embedded.lazyai.LazyAi;
import com.thelads.core.v1_21_11.embedded.lazyai.LazyMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin implements LazyMob {
    @Unique private int lads$lazyInterval = LazyAi.VANILLA_INTERVAL;
    @Unique private int lads$lazyRecheckAt;

    @Override
    public int lads$lazyInterval() {
        return lads$lazyInterval;
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void lads$updateLazyInterval(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (mob.tickCount >= lads$lazyRecheckAt) {
            lads$lazyRecheckAt = mob.tickCount + LazyAi.recheckTicks() + Math.floorMod(mob.getId(), 5);
            lads$lazyInterval = LazyAi.distanceInterval(mob);
        }
        LazyAi.report(mob.level().getGameTime());
    }

    @WrapOperation(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/sensing/Sensing;tick()V"))
    private void lads$lazySensing(Sensing sensing, Operation<Void> original) {
        if (LazyAi.skipsEvaluation((Mob) (Object) this)) LazyAi.countSensing();
        else original.call(sensing);
    }

    @WrapOperation(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"))
    private void lads$lazyGoals(GoalSelector selector, Operation<Void> original) {
        if (LazyAi.skipsEvaluation((Mob) (Object) this)) {
            LazyAi.countGoals();
            selector.tickRunningGoals(false);
        } else {
            original.call(selector);
        }
    }
}
