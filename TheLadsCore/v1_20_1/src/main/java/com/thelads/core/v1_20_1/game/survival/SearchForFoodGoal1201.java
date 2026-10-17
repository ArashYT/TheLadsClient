package com.thelads.core.v1_20_1.game.survival;

import com.thelads.core.modules.SurvivalTweaksModule;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;

/** AI goal (the 26.3 SearchForFoodGoal): breedable animals walk toward and eat food items lying on the ground. */
public final class SearchForFoodGoal1201 extends Goal {
    private final Animal animal;
    private final double speedModifier;
    private final float within;
    private final boolean pickupAllowedByDefault;
    private ItemEntity target;

    public SearchForFoodGoal1201(Animal animal, double speedModifier, float within) {
        this.animal = animal;
        this.speedModifier = speedModifier;
        this.within = within;
        this.pickupAllowedByDefault = animal.canPickUpLoot();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (mod == null || !mod.isAnimalsEatFloorFoodEnabled()) {
            return false;
        }
        if (animal.isBaby() || animal.getAge() != 0 || !animal.canFallInLove()) {
            return false;
        }
        List<ItemEntity> items = animal.level().getEntitiesOfClass(
                ItemEntity.class,
                animal.getBoundingBox().inflate(within, 4.0D, within),
                item -> animal.isFood(item.getItem())
        );
        if (items.isEmpty()) {
            return false;
        }
        target = items.get(0);
        for (int i = 1; i < items.size(); i++) {
            ItemEntity candidate = items.get(i);
            if (animal.distanceToSqr(candidate) < animal.distanceToSqr(target)) {
                target = candidate;
            }
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        return mod != null && mod.isAnimalsEatFloorFoodEnabled()
                && !animal.getNavigation().isDone()
                && target != null
                && target.isAlive()
                && target.distanceToSqr(animal) < (double) (within * within);
    }

    @Override
    public void start() {
        if (!pickupAllowedByDefault) {
            animal.setCanPickUpLoot(true);
        }
        if (target != null) {
            animal.getNavigation().moveTo(target, speedModifier);
        }
    }

    @Override
    public void stop() {
        target = null;
        if (!pickupAllowedByDefault) {
            animal.setCanPickUpLoot(false);
        }
    }
}
