package com.thelads.core.mixin.alwayson.letmedespawn;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.features.alwayson.letmedespawn.LadsEquipmentTracker;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.EquipmentDispenseItemBehavior;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={EquipmentDispenseItemBehavior.class})
public class EquipmentDispenseItemBehaviorMixin {
    @Inject(method={"dispenseEquipment(Lnet/minecraft/core/dispenser/BlockSource;Lnet/minecraft/world/item/ItemStack;)Z"}, at={@At(value="INVOKE", target="Lnet/minecraft/world/entity/LivingEntity;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V", shift=At.Shift.AFTER)})
    private static void letmedespawn$afterEquip(BlockSource source, ItemStack dispensed, CallbackInfoReturnable<Boolean> cir, @Local LivingEntity target, @Local EquipmentSlot slot) {
        if (target instanceof Mob) {
            Mob mob = (Mob)target;
            ((LadsEquipmentTracker) mob).lads$markSlotAsPicked(slot, true);
        }
    }
}
