package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
public abstract class SurvivalMobFoodPickupMixin {
    @WrapMethod(method = "canHoldItem")
    private boolean lads$allowAnimalFoodPickup(ItemStack stack, Operation<Boolean> original) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks.active() && mod != null && mod.isAnimalsEatFloorFoodEnabled()
                && (Object) this instanceof Animal animal) {
            return !animal.isBaby() && animal.getAge() == 0 && animal.canFallInLove() && animal.isFood(stack);
        }
        return original.call(stack);
    }

    @WrapWithCondition(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;pickUpItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/item/ItemEntity;)V"
            )
    )
    private boolean lads$animalConsumeFloorFood(Mob instance, ServerLevel level, ItemEntity itemEntity) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks.active() && mod != null && mod.isAnimalsEatFloorFoodEnabled()
                && instance instanceof Animal animal
                && animal.isFood(itemEntity.getItem())) {
            if (!animal.isInLove()) {
                ItemStack stack = itemEntity.getItem();
                animal.onItemPickup(itemEntity);
                animal.take(itemEntity, 1);
                stack.shrink(1);
                if (stack.isEmpty()) {
                    itemEntity.discard();
                }
                Player thrower = itemEntity.getOwner() instanceof Player p ? p : null;
                animal.setInLove(thrower);
            }
            return false;
        }
        return true;
    }

    @WrapMethod(method = "canReplaceCurrentItem")
    private boolean lads$preventAnimalFoodEquipmentReplace(ItemStack newItem, ItemStack currentItem, EquipmentSlot slot, Operation<Boolean> original) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks.active() && mod != null && mod.isAnimalsEatFloorFoodEnabled()
                && (Object) this instanceof Animal animal
                && animal.isFood(newItem)) {
            return false;
        }
        return original.call(newItem, currentItem, slot);
    }
}
