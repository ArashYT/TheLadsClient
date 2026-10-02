package com.thelads.core.v26_2.embedded.lazyai.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.embedded.lazyai.LazyAi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Brain sensors of far mobs rescan less often (scan rate x2 to x4). */
@Mixin(Sensor.class)
public abstract class SensorMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/ai/sensing/Sensor;scanRate:I"))
    private int lads$lazyScanRate(int scanRate, @Local(argsOnly = true) LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return scanRate;
        int multiplier = LazyAi.multiplier(mob);
        if (multiplier > 1) LazyAi.countSensor();
        return scanRate * multiplier;
    }
}
