package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Render Tweaks, Bobbing Disconnect: View Bobbing moves only your hand. setupViewBobbing runs for the camera (setupCameraTransform)
 * and again for the hand (renderHand); only the camera's call is skipped. OptiFine keeps both calls: require = 0 keeps vanilla otherwise.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererBobMixin189 {
    @Shadow protected abstract void setupViewBobbing(float partialTicks);

    @Redirect(method = "setupCameraTransform", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V"), require = 0)
    private void ladsCameraBob(EntityRenderer renderer, float partialTicks) {
        if (!PvpVisuals.bobbingDisconnect) setupViewBobbing(partialTicks);
    }
}
