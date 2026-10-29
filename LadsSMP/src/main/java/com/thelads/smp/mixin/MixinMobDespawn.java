package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.features.PickedEquipmentTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Consolidates 'letmedespawn': Prevents mobs that pick up items from becoming permanently persistent
 * (unless custom-named), and drops their picked-up equipment when they despawn.
 */
@Mixin(Mob.class)
public abstract class MixinMobDespawn implements PickedEquipmentTracker {

    @Shadow
    private boolean persistenceRequired;

    @Unique
    private int ladssmp$pickedUpSlotsBitmask;

    @Override
    public void ladssmp$markSlotPicked(EquipmentSlot slot) {
        if (slot != null) {
            this.ladssmp$pickedUpSlotsBitmask |= (1 << slot.ordinal());
        }
    }

    @Override
    public boolean ladssmp$isSlotPicked(EquipmentSlot slot) {
        return slot != null && (this.ladssmp$pickedUpSlotsBitmask & (1 << slot.ordinal())) != 0;
    }

    @Override
    public boolean ladssmp$hasPickedEquipment() {
        return this.ladssmp$pickedUpSlotsBitmask != 0;
    }

    @Inject(method = "setItemSlotAndDropWhenKilled", at = @At("TAIL"))
    private void ladssmp$onSetItemSlotAndDropWhenKilled(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (!LadsConfig.get().features.letMeDespawn) return;
        Mob self = (Mob) (Object) this;
        this.ladssmp$markSlotPicked(slot);
        this.persistenceRequired = self.hasCustomName();
    }

    @Inject(
            method = "addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V",
            at = @At("RETURN")
    )
    private void ladssmp$savePickedSlots(ValueOutput output, CallbackInfo ci) {
        if (this.ladssmp$pickedUpSlotsBitmask != 0) {
            output.putInt("ladssmp_picked_slots", this.ladssmp$pickedUpSlotsBitmask);
        }
    }

    @Inject(
            method = "readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V",
            at = @At("RETURN")
    )
    private void ladssmp$loadPickedSlots(ValueInput input, CallbackInfo ci) {
        this.ladssmp$pickedUpSlotsBitmask = input.getIntOr("ladssmp_picked_slots", 0);
    }

    @Inject(method = "equipItemIfPossible", at = @At("RETURN"))
    private void ladssmp$onEquipItemIfPossible(ServerLevel level, ItemStack itemStack, CallbackInfoReturnable<ItemStack> cir) {
        if (!LadsConfig.get().features.letMeDespawn) return;
        ItemStack equippedStack = cir.getReturnValue();
        if (equippedStack != null && !equippedStack.isEmpty()) {
            Mob self = (Mob) (Object) this;
            EquipmentSlot slot = self.getEquipmentSlotForItem(equippedStack);
            this.ladssmp$markSlotPicked(slot);
            this.persistenceRequired = self.hasCustomName();
        }
    }

    @Redirect(
            method = "checkDespawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;discard()V"
            )
    )
    private void ladssmp$dropPickedItemsOnDespawn(Mob instance) {
        if (LadsConfig.get().features.letMeDespawn && instance.level() instanceof ServerLevel serverLevel) {
            if (instance instanceof Enderman enderman && enderman.getCarriedBlock() != null) {
                Block.dropResources(enderman.getCarriedBlock(), serverLevel, instance.blockPosition());
            }
            if (this.ladssmp$hasPickedEquipment()) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (this.ladssmp$isSlotPicked(slot)) {
                        ItemStack stack = instance.getItemBySlot(slot);
                        if (!stack.isEmpty()) {
                            instance.spawnAtLocation(serverLevel, stack);
                            instance.setItemSlot(slot, ItemStack.EMPTY);
                        }
                    }
                }
            }
        }
        instance.discard();
    }
}
