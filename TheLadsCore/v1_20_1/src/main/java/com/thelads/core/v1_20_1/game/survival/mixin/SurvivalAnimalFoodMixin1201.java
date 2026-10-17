package com.thelads.core.v1_20_1.game.survival.mixin;

import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import com.thelads.core.v1_20_1.game.survival.SearchForFoodGoal1201;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SurvivalTweaks: Animals eat floor food. Every new animal gets the SearchForFood goal. */
@Mixin(Animal.class)
public abstract class SurvivalAnimalFoodMixin1201 extends Mob {
    protected SurvivalAnimalFoodMixin1201(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void lads$addSearchForFoodGoal(EntityType<? extends Animal> entityType, Level level, CallbackInfo ci) {
        if (NativeSurvivalTweaks1201.active() && !level.isClientSide()) {
            this.goalSelector.addGoal(3, new SearchForFoodGoal1201((Animal) (Object) this, 1.15D, 12.0f));
        }
    }
}
