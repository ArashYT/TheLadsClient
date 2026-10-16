package com.thelads.smp.mixin;

import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pathways 2, 3, and 5:
 * - Periodically sweeps player inventory & equipment slots for disabled items
 * - Prevents dropping disabled items into the world to transfer to other players or hoppers
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    @Inject(method = "tick", at = @At("TAIL"))
    private void ladssmp$periodicInventorySanitization(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        int interval = Math.max(5, ItemDisablerSystem.getConfig().inventorySweepIntervalTicks);
        if (self.tickCount % interval == 0) {
            ItemDisablerSystem.sanitizePlayerInventory(self, "periodic_tick");
        }
    }

    @Inject(
            method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ladssmp$preventDisabledDrop(ItemStack stack, boolean throwRandomly, Prediction prediction, CallbackInfoReturnable<ItemEntity> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (ItemDisablerSystem.isDisabled(stack) && !ItemDisablerSystem.shouldBypass(self)) {
            ItemDisablerSystem.handleDisabledStackFound(self, stack, "player_drop");
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "triggerDimensionChangeTriggers", at = @At("TAIL"))
    private void ladssmp$onDimensionChanged(net.minecraft.server.level.ServerLevel origin, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        com.thelads.smp.features.NetherPortalFixManager.onPlayerChangedDimension(self, origin, self.level());
    }
}
