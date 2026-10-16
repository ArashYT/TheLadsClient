package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.features.PickedEquipmentTracker;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.EquipmentDispenseItemBehavior;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Consolidates 'letmedespawn': Tracks dispenser-equipped items on mobs without forcing permanent persistence unless custom-named.
 */
@Mixin(EquipmentDispenseItemBehavior.class)
public abstract class MixinEquipmentDispenseItemBehavior {

    @Redirect(
            method = "dispenseEquipment",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;setPersistenceRequired()V"
            )
    )
    private static void ladssmp$handleDispenseEquipmentPersistence(Mob mob, BlockSource blockSource, ItemStack stack) {
        if (!LadsConfig.get().features.letMeDespawn) {
            mob.setPersistenceRequired();
            return;
        }
        if (mob instanceof PickedEquipmentTracker tracker) {
            EquipmentSlot slot = mob.getEquipmentSlotForItem(stack);
            tracker.ladssmp$markSlotPicked(slot);
        }
        if (mob.hasCustomName()) {
            mob.setPersistenceRequired();
        }
    }
}
