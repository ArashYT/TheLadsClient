package com.thelads.core.v26_2.cosmetics.anim.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.cosmetics.anim.EmoteAnim26;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes in first person: the dancing arms are drawn with the hands; the normal hand/item lowers while they show (EmoteHandHeightMixin26). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class EmoteHandsMixin26 {
    @Inject(method = "submitHandsWithItems", at = @At("TAIL"))
    private void lads$emoteArms(float partialTicks, PoseStack pose, SubmitNodeCollector collector, PlayerRenderState player,
                                FirstPersonHandsAndItemsRenderState state, CallbackInfo callback) {
        EmoteAnim26.firstPersonArms(pose, collector, player, state);
    }
}
