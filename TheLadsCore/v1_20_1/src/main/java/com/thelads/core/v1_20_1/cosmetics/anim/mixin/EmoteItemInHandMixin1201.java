package com.thelads.core.v1_20_1.cosmetics.anim.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.cosmetics.anim.EmoteAnim1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes in first person: while the emote moves the arms the normal hand/item lowers as for an item swap (vanilla raises it again
 * afterwards), and the dancing arms are drawn with the hands.
 */
@Mixin(ItemInHandRenderer.class)
public class EmoteItemInHandMixin1201 {
    @Shadow private float mainHandHeight, oMainHandHeight, offHandHeight, oOffHandHeight;

    @Inject(method = "tick", at = @At("TAIL"))
    private void lads$emoteLowersHands(CallbackInfo callback) {
        if (!EmoteAnim1201.handsDown(Minecraft.getInstance().player)) return;
        mainHandHeight = Math.max(0, oMainHandHeight - 0.4f);
        offHandHeight = Math.max(0, oOffHandHeight - 0.4f);
    }

    @Inject(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V"))
    private void lads$emoteArms(float partialTicks, PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer player, int light,
                                CallbackInfo callback) {
        EmoteAnim1201.firstPersonArms((ItemInHandRenderer) (Object) this, pose, buffers, player, light, partialTicks);
    }
}
