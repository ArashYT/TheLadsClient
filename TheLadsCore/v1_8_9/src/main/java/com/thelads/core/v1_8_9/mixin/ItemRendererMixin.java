package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.LegacySwing189;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LegacySwing (LegacySwing189), as 26.x LegacySwingMixin/LegacyEquipMixin: the legacy swing replaces vanilla's whole swing,
 * its translation too (26.x cancels swingArm, which holds both), and using an item no longer pops the hand down. Switching
 * items keeps vanilla's lower-and-raise.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "transformFirstPersonItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsSwing(float equipProgress, float swingProgress, CallbackInfo ci) {
        if (LegacySwing189.transform(equipProgress, swingProgress)) ci.cancel();
    }

    @Inject(method = "doItemUsedTransformations", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsNoVanillaSwingMove(float swingProgress, CallbackInfo ci) {
        if (LegacySwing189.enabled()) ci.cancel();
    }

    @Inject(method = {"resetEquippedProgress", "resetEquippedProgress2"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void ladsNoUsePop(CallbackInfo ci) {
        if (LegacySwing189.enabled()) ci.cancel();
    }
}
