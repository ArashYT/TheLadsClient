package com.thelads.core.v1_21_11.mixin.hud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_21_11.feature.NativeAutohide;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A picture submitted inside a faded scope (Xaero's minimap) is blitted at that opacity. */
@Mixin(PictureInPictureRenderer.class)
public class AutohidePictureMixin {
    @Unique private float ladsPictureAlpha = 1;
    @Inject(method = "blitTexture", at = @At("HEAD"))
    private void ladsPicture(PictureInPictureRenderState state, GuiRenderState target, CallbackInfo ci) {
        Float alpha = NativeAutohide.PICTURES.remove(state);
        ladsPictureAlpha = alpha == null ? 1 : alpha;
    }
    @WrapOperation(method = "blitTexture", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;submitBlitToCurrentLayer(Lnet/minecraft/client/gui/render/state/BlitRenderState;)V"))
    private void ladsFadePicture(GuiRenderState state, BlitRenderState blit, Operation<Void> original) {
        original.call(state, ladsPictureAlpha < 1 ? NativeAutohide.fade(blit, ladsPictureAlpha) : blit);
    }
}
