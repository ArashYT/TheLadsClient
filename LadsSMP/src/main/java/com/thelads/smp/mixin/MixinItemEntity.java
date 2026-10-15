package com.thelads.smp.mixin;

import com.thelads.smp.disabler.ItemDisablerSystem;
import com.thelads.smp.features.ConsolidatedFeatures;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pathway 1: Prevents disabled items from being picked up or remaining as world entities,
 * and implements the consolidated DespawningEggsHatch mechanic.
 */
@Mixin(ItemEntity.class)
public abstract class MixinItemEntity extends Entity {
    @Shadow
    private int age;

    @Shadow
    public abstract ItemStack getItem();

    protected MixinItemEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void ladssmp$preventDisabledPickup(Player player, CallbackInfo ci) {
        ItemStack stack = this.getItem();
        if (ItemDisablerSystem.isDisabled(stack) && !ItemDisablerSystem.shouldBypass(player)) {
            if (!this.level().isClientSide()) {
                ItemDisablerSystem.writeAuditLog("Blocked world pickup of disabled item " + ItemDisablerSystem.getItemIdString(stack)
                        + " by " + player.getScoreboardName() + " at " + this.blockPosition());
                this.discard();
            }
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ladssmp$onTick(CallbackInfo ci) {
        if (this.level().isClientSide()) return;

        ItemStack stack = this.getItem();
        if (ItemDisablerSystem.isDisabled(stack)) {
            ItemDisablerSystem.writeAuditLog("Discarded world ItemEntity of disabled item "
                    + ItemDisablerSystem.getItemIdString(stack) + " at " + this.blockPosition());
            this.discard();
            ci.cancel();
            return;
        }

        // Consolidated Feature: Despawning Eggs Hatch (when age reaches 5999, right before vanilla 6000 despawn)
        if (this.age == 5999) {
            ConsolidatedFeatures.tryHatchDespawningEgg((ItemEntity) (Object) this);
        }
    }
}
