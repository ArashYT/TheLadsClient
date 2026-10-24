package com.thelads.core.v1_20_1.game.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Toggle Sprint: aiStep reads the Sprint key twice (double tap and held); both see what NativeFeatures1201.movement decided. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void lads$dropConfirm(boolean entireStack, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        // Slot Lock: the held hotbar slot is locked, so nothing is dropped (and nothing sent).
        if (com.thelads.core.client.InventoryTweaks.locked(((LocalPlayer) (Object) this).getInventory().selected)) {
            cir.setReturnValue(false);
            return;
        }
        net.minecraft.world.item.ItemStack held = ((LocalPlayer) (Object) this).getMainHandItem();
        if (!held.isEmpty()) {
            com.thelads.core.config.Module m = com.thelads.core.config.ModuleManager.getInstance().getModule(com.thelads.core.modules.DropConfirmationModule.NAME);
            if (m instanceof com.thelads.core.modules.DropConfirmationModule dcm && dcm.isEnabled()) {
                boolean isEnchanted = held.isEnchanted();
                boolean isValuable = isEnchanted || (!dcm.isEnchantOnly() && com.thelads.core.client.DropConfirmationHelper.isValuableName(held.getDescriptionId()));
                if (!com.thelads.core.client.DropConfirmationHelper.checkCanDrop(System.currentTimeMillis(), dcm.getTimeoutMillis(), isValuable, true)) {
                    net.minecraft.client.Minecraft.getInstance().gui.setOverlayMessage(net.minecraft.network.chat.Component.literal("\u00a7cPress drop key again to confirm!"), false);
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @WrapOperation(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"), require = 2)
    private boolean lads$sprintKey(KeyMapping key, Operation<Boolean> original) {
        return NativeFeatures1201.sprintKeyDown(key, original.call(key));
    }

    @Inject(method = "aiStep()V", at = @At("RETURN"))
    private void lads$movementDone(CallbackInfo callback) {
        NativeFeatures1201.movementDone();
    }
}
