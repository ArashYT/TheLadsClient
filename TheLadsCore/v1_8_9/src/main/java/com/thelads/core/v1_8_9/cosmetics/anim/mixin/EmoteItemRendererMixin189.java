package com.thelads.core.v1_8_9.cosmetics.anim.mixin;

import com.thelads.core.v1_8_9.cosmetics.anim.EmoteAnim189;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes in first person: while the emote moves the arms the normal hand/item lowers as for an item swap (vanilla raises it again
 * afterwards), and the dancing arms are drawn after the hand.
 */
@Mixin(ItemRenderer.class)
public abstract class EmoteItemRendererMixin189 {
    @Shadow private float equippedProgress;
    @Shadow private float prevEquippedProgress;

    @Inject(method = "updateEquippedItem", at = @At("RETURN"), require = 1)
    private void ladsEmoteLowersHand(CallbackInfo ci) {
        if (EmoteAnim189.handsDown()) equippedProgress = Math.max(0, prevEquippedProgress - 0.4F);
    }

    @Inject(method = "renderItemInFirstPerson", at = @At("RETURN"), require = 1)
    private void ladsEmoteArms(float partialTicks, CallbackInfo ci) {
        EmoteAnim189.firstPersonArms(partialTicks);
    }
}
