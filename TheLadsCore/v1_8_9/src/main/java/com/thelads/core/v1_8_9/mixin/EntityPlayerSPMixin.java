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
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"), require = 1)
    private boolean ladsSprintKey(KeyBinding binding) {
        return Toggles189.isKeyDown(binding);
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$interceptChat(String message, CallbackInfo ci) {
        if (message != null && message.startsWith("/lads:social ")) {
            com.thelads.core.v1_8_9.social.SocialBridge189.handleChatAction(message.substring("/lads:social ".length()).trim());
            ci.cancel();
            return;
        }
        if (HypixelMods.handleOutgoingCommand(message)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "sendChatMessage", at = @At("HEAD"), argsOnly = true, require = 1)
    private String lads$expandEmotes(String message) {
        return HypixelMods.handleOutgoingChat(message);
    }
}
