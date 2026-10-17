package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.SurvivalTweaks189;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SurvivalTweaks Animals eat floor food: every server-side animal gets the task (priority 3, as 26.x); it checks the option itself. */
@Mixin(EntityAnimal.class)
public abstract class SurvivalAnimalMixin extends EntityAgeable {
    private SurvivalAnimalMixin(World world) {
        super(world);
    }

    @Inject(method = "<init>", at = @At("RETURN"), require = 1) // Mixin 0.7: RETURN in constructors
    private void ladsEatFloorFood(World world, CallbackInfo ci) {
        if (world != null && !world.isRemote) tasks.addTask(3, new SurvivalTweaks189.EatFloorFood((EntityAnimal) (Object) this));
    }
}
