package com.thelads.core.v26_2.mixin.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.modules.ItemDespawnModule;
import com.thelads.core.v26_2.feature.PvpVisuals26;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loot Beams (this item's beam is queued for the frame) and Item Despawn (the item is skipped on its blink's off frames) on 26.3. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityVisualsMixin26 {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void ladsBeamAndBlink(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        PvpVisuals26.Visual visual = (PvpVisuals26.Visual) state;
        PvpVisuals26.queueBeam(state.x, state.y, state.z, visual.lads$rarity());
        if (ItemDespawnModule.hidden(visual.lads$age(), ItemDespawnModule.LIFETIME)) ci.cancel();
    }
}
