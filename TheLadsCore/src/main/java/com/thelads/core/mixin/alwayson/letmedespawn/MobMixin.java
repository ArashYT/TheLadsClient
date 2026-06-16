package com.thelads.core.mixin.alwayson.letmedespawn;

import com.thelads.core.features.alwayson.letmedespawn.LadsEquipmentTracker;
import com.thelads.core.features.alwayson.letmedespawn.LetMeDespawn;
import java.util.Set;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Mob.class}, priority=1010)
public abstract class MobMixin extends LivingEntity implements LadsEquipmentTracker {
    @Shadow
    private boolean persistenceRequired;

    private final boolean[] lads$pickedSlots = new boolean[EquipmentSlot.values().length];

    protected MobMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean lads$isSlotPicked(EquipmentSlot slot) {
        if (slot == null) return false;
        return lads$pickedSlots[slot.ordinal()];
    }

    @Override
    public void lads$markSlotAsPicked(EquipmentSlot slot, boolean picked) {
        if (slot != null) {
            lads$pickedSlots[slot.ordinal()] = picked;
        }
    }

    @Override
    public boolean lads$hasAnyPickedEquipment() {
        for (boolean b : lads$pickedSlots) {
            if (b) return true;
        }
        return false;
    }

    @Override
    public void lads$setPersistenceRequired(boolean value) {
        this.persistenceRequired = value;
    }

    @Inject(at={@At("TAIL")}, method="addAdditionalSaveData")
    private void letmedespawn$writeCustomDataToNbt(net.minecraft.nbt.CompoundTag compoundTag, CallbackInfo ci) {
        int mask = 0;
        for (int i = 0; i < lads$pickedSlots.length; i++) {
            if (lads$pickedSlots[i]) {
                mask |= (1 << i);
            }
        }
        compoundTag.putInt("lads$pickedSlots", mask);
    }
    
    @Inject(at={@At("HEAD")}, method="readAdditionalSaveData")
    private void letmedespawn$readCustomDataFromNbt(net.minecraft.nbt.CompoundTag compoundTag, CallbackInfo ci) {
        if (compoundTag.contains("lads$pickedSlots")) {
            int mask = compoundTag.getInt("lads$pickedSlots").orElse(0);
            for (int i = 0; i < lads$pickedSlots.length; i++) {
                lads$pickedSlots[i] = (mask & (1 << i)) != 0;
            }
        }
    }

    @Inject(at={@At(value="TAIL")}, method={"setItemSlotAndDropWhenKilled(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"})
    private void letmedespawn$setItemSlotAndDropWhenKilled(EquipmentSlot slot, ItemStack itemStack, CallbackInfo info) {
        Mob entity = (Mob)(Object)this;
        LetMeDespawn.setPersistence(entity, slot);
    }

    @Redirect(method={"checkDespawn()V"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/Mob;discard()V"))
    private void letmedespawn$yeetusCheckus(Mob instance) {
        boolean shouldDiscard = true;
        Set<String> persistenceEnablers = LetMeDespawn.config.getPersistenceEnablers();
        if (!persistenceEnablers.isEmpty() && ((LadsEquipmentTracker) instance).lads$hasAnyPickedEquipment()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (!((LadsEquipmentTracker) instance).lads$isSlotPicked(slot)) continue;
                ItemStack itemStack = instance.getItemBySlot(slot);
                if (itemStack.isEmpty()) continue;
                String itemKey = LetMeDespawn.getItemKey(itemStack, instance);
                if (persistenceEnablers.contains(itemKey)) {
                    shouldDiscard = false;
                    break;
                }
            }
        }
        if (shouldDiscard) {
            LetMeDespawn.dropEquipmentOnDiscard(instance);
            instance.discard();
        }
    }
}
