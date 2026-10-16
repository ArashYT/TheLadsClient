package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks;
import com.thelads.core.v26_2.feature.survivaltweaks.SearchForFoodGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public abstract class SurvivalAnimalFoodMixin extends Mob {
    protected SurvivalAnimalFoodMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void lads$addSearchForFoodGoal(EntityType<? extends Animal> entityType, Level level, CallbackInfo ci) {
        if (NativeSurvivalTweaks.active() && !level.isClientSide()) {
            this.goalSelector.addGoal(3, new SearchForFoodGoal((Animal) (Object) this, 1.15D, 12.0f));
        }
    }
}
