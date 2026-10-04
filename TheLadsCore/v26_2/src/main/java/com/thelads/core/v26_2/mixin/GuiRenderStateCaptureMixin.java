package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.HudCapture;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the HUD's build calls for HudCapture. None of these calls another, and the renderer-only *ToCurrentLayer adds are not needed. */
@Mixin(GuiRenderState.class)
public class GuiRenderStateCaptureMixin {
    @Inject(method = "nextStratum", at = @At("HEAD"), require = 1)
    private void lads$nextStratum(CallbackInfo ci) { HudCapture.record(GuiRenderState::nextStratum); }

    @Inject(method = "blurBeforeThisStratum", at = @At("HEAD"), require = 1)
    private void lads$blur(CallbackInfo ci) { HudCapture.record(GuiRenderState::blurBeforeThisStratum); }

    @Inject(method = "up", at = @At("HEAD"), require = 1)
    private void lads$up(CallbackInfo ci) { HudCapture.record(GuiRenderState::up); }

    @Inject(method = "addItem", at = @At("HEAD"), require = 1)
    private void lads$item(GuiItemRenderState item, CallbackInfo ci) { HudCapture.record(state -> state.addItem(item)); }

    @Inject(method = "addText", at = @At("HEAD"), require = 1)
    private void lads$text(GuiTextRenderState text, CallbackInfo ci) { HudCapture.record(state -> state.addText(text)); }

    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"), require = 1)
    private void lads$picture(PictureInPictureRenderState picture, CallbackInfo ci) { HudCapture.record(state -> HudCapture.replayPicture(state, picture)); }

    /** Recorded unfaded whichever HEAD injector runs first: the replay fades it again in its recorded Autohide opacity (HudCapture). */
    @Inject(method = "addGuiElement", at = @At("HEAD"), require = 1)
    private void lads$element(GuiElementRenderState element, CallbackInfo ci) {
        GuiElementRenderState plain = element instanceof com.thelads.core.v26_2.feature.FadedElement faded ? faded.element() : element;
        HudCapture.record(state -> state.addGuiElement(plain));
    }
}
