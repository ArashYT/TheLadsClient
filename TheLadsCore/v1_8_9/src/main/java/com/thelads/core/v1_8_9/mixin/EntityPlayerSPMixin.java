package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.hypixel.HypixelMods;
import com.thelads.core.v1_8_9.feature.Toggles189;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Toggle Sprint &amp; Sneak: onLivingUpdate's reads of the Sprint key see the toggle, so its own sprint rules apply to it.
 * Hypixel Mods: intercept outgoing /session command and expand MVP++ chat emotes for non-MVP++ players.
 */
@Mixin(EntityPlayerSP.class)
public abstract class EntityPlayerSPMixin {
    @org.spongepowered.asm.mixin.Shadow protected int sprintToggleTimer;

    @Inject(method = "dropOneItem(Z)Lnet/minecraft/entity/item/EntityItem;", at = @At("HEAD"), cancellable = true)
    private void lads$dropConfirm(boolean dropAll, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.entity.item.EntityItem> cir) {
        net.minecraft.item.ItemStack held = ((EntityPlayerSP) (Object) this).getHeldItem();
        if (held != null) {
            com.thelads.core.config.Module m = com.thelads.core.config.ModuleManager.getInstance().getModule(com.thelads.core.modules.DropConfirmationModule.NAME);
            if (m instanceof com.thelads.core.modules.DropConfirmationModule && m.isEnabled()) {
                com.thelads.core.modules.DropConfirmationModule dcm = (com.thelads.core.modules.DropConfirmationModule) m;
                boolean isEnchanted = held.isItemEnchanted();
                boolean isValuable = isEnchanted || (!dcm.isEnchantOnly() && com.thelads.core.client.DropConfirmationHelper.isValuableName(held.getItem().getUnlocalizedName()));
                if (!com.thelads.core.client.DropConfirmationHelper.checkCanDrop(System.currentTimeMillis(), dcm.getTimeoutMillis(), isValuable, true)) {
                    net.minecraft.client.Minecraft.getMinecraft().ingameGUI.setRecordPlaying("\u00a7cPress drop key again to confirm!", false);
                    cir.setReturnValue(null);
                }
            }
        }
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void lads$disableDoubleTap(CallbackInfo ci) {
        if (com.thelads.core.v1_8_9.feature.Options189.bool(com.thelads.core.modules.ToggleSprintModule.NAME, "Disable double-tap sprint", false)) {
            this.sprintToggleTimer = 0;
        }
    }
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"), require = 1)
    private boolean ladsSprintKey(KeyBinding binding) {
        return Toggles189.isKeyDown(binding);
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$interceptChat(String message, CallbackInfo ci) {
        if (HypixelMods.handleOutgoingCommand(message)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "sendChatMessage", at = @At("HEAD"), argsOnly = true, require = 1)
    private String lads$expandEmotes(String message) {
        return HypixelMods.handleOutgoingChat(message);
    }
}
