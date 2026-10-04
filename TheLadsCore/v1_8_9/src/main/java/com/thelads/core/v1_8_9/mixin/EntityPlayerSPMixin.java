package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Toggles189;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Toggle Sprint &amp; Sneak: onLivingUpdate's reads of the Sprint key see the toggle, so its own sprint rules apply to it. */
@Mixin(EntityPlayerSP.class)
public abstract class EntityPlayerSPMixin {
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"), require = 1)
    private boolean ladsSprintKey(KeyBinding binding) {
        return Toggles189.isKeyDown(binding);
    }
}
