package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.feature.HudCapture;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the HUD's build calls for HudCapture. None of these calls another, and the renderer-only *ToCurrentLayer submits are not needed. */
@Mixin(GuiRenderState.class)
public class GuiRenderStateCaptureMixin {
    @Inject(method = "nextStratum", at = @At("HEAD"))
    private void lads$nextStratum(CallbackInfo ci) { HudCapture.record(GuiRenderState::nextStratum); }

    @Inject(method = "blurBeforeThisStratum", at = @At("HEAD"))
    private void lads$blur(CallbackInfo ci) { HudCapture.record(GuiRenderState::blurBeforeThisStratum); }

    @Inject(method = "up", at = @At("HEAD"))
    private void lads$up(CallbackInfo ci) { HudCapture.record(GuiRenderState::up); }

    @Inject(method = "submitItem", at = @At("HEAD"))
    private void lads$item(GuiItemRenderState item, CallbackInfo ci) { HudCapture.record(state -> state.submitItem(item)); }

    @Inject(method = "submitText", at = @At("HEAD"))
    private void lads$text(GuiTextRenderState text, CallbackInfo ci) { HudCapture.record(state -> state.submitText(text)); }

    @Inject(method = "submitPicturesInPictureState", at = @At("HEAD"))
    private void lads$picture(PictureInPictureRenderState picture, CallbackInfo ci) { HudCapture.record(state -> state.submitPicturesInPictureState(picture)); }

    @Inject(method = "submitGuiElement", at = @At("HEAD"))
    private void lads$element(GuiElementRenderState element, CallbackInfo ci) { HudCapture.record(state -> state.submitGuiElement(element)); }
}
