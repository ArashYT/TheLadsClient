package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.Toggles189;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Toggle Sprint &amp; Sneak: onLivingUpdate's reads of the Sprint key see the toggle, so its own sprint rules apply to it.
 * Smooth swing: mid-swing clicks queue a pending swing without resetting progress, starting the next swing on completion.
 */
@Mixin(EntityPlayerSP.class)
public abstract class EntityPlayerSPMixin {
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"), require = 1)
    private boolean ladsSprintKey(KeyBinding binding) {
        return Toggles189.isKeyDown(binding);
    }

    @Inject(method = "swingItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsSwingItem(CallbackInfo ci) {
        if (OldAnimations189.onPlayerSwingItem((EntityPlayerSP) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "onUpdate", at = @At("RETURN"), require = 1)
    private void ladsCheckPendingSwing(CallbackInfo ci) {
        OldAnimations189.checkPendingSwing((EntityPlayerSP) (Object) this);
    }
}
