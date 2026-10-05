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

/**
 * Records the HUD's build calls for HudCapture, each with a fingerprint of what it draws (a changing HUD is animating). None of these
 * calls another, and the renderer-only *ToCurrentLayer adds are not needed.
 */
@Mixin(GuiRenderState.class)
public class GuiRenderStateCaptureMixin {
    @Inject(method = "nextStratum", at = @At("HEAD"), require = 1)
    private void lads$nextStratum(CallbackInfo ci) { HudCapture.record(GuiRenderState::nextStratum, () -> 1); }

    @Inject(method = "blurBeforeThisStratum", at = @At("HEAD"), require = 1)
    private void lads$blur(CallbackInfo ci) { HudCapture.record(GuiRenderState::blurBeforeThisStratum, () -> 2); }

    @Inject(method = "up", at = @At("HEAD"), require = 1)
    private void lads$up(CallbackInfo ci) { HudCapture.record(GuiRenderState::up, () -> 3); }

    /** An item: its model (what it looks like now) and where. */
    @Inject(method = "addItem", at = @At("HEAD"), require = 1)
    private void lads$item(GuiItemRenderState item, CallbackInfo ci) {
        HudCapture.record(state -> state.addItem(item), () -> java.util.Objects.hash(item.itemStackRenderState().getModelIdentity(), item.pose(), item.x(), item.y()));
    }

    /** Text: its characters with their styles, and where, in what colour. */
    @Inject(method = "addText", at = @At("HEAD"), require = 1)
    private void lads$text(GuiTextRenderState text, CallbackInfo ci) {
        HudCapture.record(state -> state.addText(text), () -> {
            var fields = (GuiTextRenderStateAccessor) (Object) text;
            int[] print = {java.util.Objects.hash(text.pose, fields.lads$x(), fields.lads$y(), fields.lads$color(), fields.lads$background(), fields.lads$shadow())};
            fields.lads$text().accept((index, style, codePoint) -> { print[0] = 31 * (31 * print[0] + codePoint) + java.util.Objects.hashCode(style.getColor()); return true; });
            return print[0];
        });
    }

    /** A picture: where it is (what it shows is the renderer's: Xaero's minimap, the paper doll). */
    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"), require = 1)
    private void lads$picture(PictureInPictureRenderState picture, CallbackInfo ci) {
        HudCapture.record(state -> HudCapture.replayPicture(state, picture), () -> java.util.Objects.hash(picture.getClass(), picture.bounds()));
    }

    /**
     * Recorded unfaded whichever HEAD injector runs first: the replay fades it again in its recorded Autohide opacity (HudCapture).
     * Vanilla's elements are records (texture, pose, corners, UVs, colour); any other kind counts by where it is.
     */
    @Inject(method = "addGuiElement", at = @At("HEAD"), require = 1)
    private void lads$element(GuiElementRenderState element, CallbackInfo ci) {
        GuiElementRenderState plain = element instanceof com.thelads.core.v26_2.feature.FadedElement faded ? faded.element() : element;
        HudCapture.record(state -> state.addGuiElement(plain),
            () -> plain instanceof Record ? plain.hashCode() : java.util.Objects.hash(plain.getClass(), plain.bounds()));
    }
}
