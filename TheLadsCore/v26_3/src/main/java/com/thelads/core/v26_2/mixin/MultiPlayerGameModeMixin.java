package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.DropConfirmationHelper;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.DropConfirmationModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "dropItem(Lnet/minecraft/client/player/LocalPlayer;Z)V", at = @At("HEAD"), cancellable = true)
    private void lads$dropConfirm(LocalPlayer player, boolean entireStack, CallbackInfo ci) {
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            Module m = ModuleManager.getInstance().getModule(DropConfirmationModule.NAME);
            if (m instanceof DropConfirmationModule dcm && dcm.isEnabled()) {
                boolean isEnchanted = held.isEnchanted();
                boolean isValuable = isEnchanted || (!dcm.isEnchantOnly() && DropConfirmationHelper.isValuableName(held.getItem().getDescriptionId()));
                if (!DropConfirmationHelper.checkCanDrop(System.currentTimeMillis(), dcm.getTimeoutMillis(), isValuable, true)) {
                    Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("\u00a7cPress drop key again to confirm!"), false);
                    ci.cancel();
                }
            }
        }
    }
}
