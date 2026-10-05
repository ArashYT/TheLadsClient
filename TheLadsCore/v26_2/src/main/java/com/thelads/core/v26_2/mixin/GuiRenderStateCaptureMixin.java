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
 * Records the HUD's build calls for HudCapture, each with what it draws and where and how (HudFrameCap: something that keeps moving,
 * resizing, turning, fading or changing colour is animating; new content is not). None of these calls another, and the renderer-only
 * *ToCurrentLayer adds are not needed.
 */
@Mixin(GuiRenderState.class)
public class GuiRenderStateCaptureMixin {
    @Inject(method = "nextStratum", at = @At("HEAD"), require = 1)
    private void lads$nextStratum(CallbackInfo ci) { if (HudCapture.record(GuiRenderState::nextStratum)) HudCapture.drew(1, 0); }

    @Inject(method = "blurBeforeThisStratum", at = @At("HEAD"), require = 1)
    private void lads$blur(CallbackInfo ci) { if (HudCapture.record(GuiRenderState::blurBeforeThisStratum)) HudCapture.drew(2, 0); }

    @Inject(method = "up", at = @At("HEAD"), require = 1)
    private void lads$up(CallbackInfo ci) { if (HudCapture.record(GuiRenderState::up)) HudCapture.drew(3, 0); }

    /** An item: its model (what it looks like now); where, how big and turned. */
    @Inject(method = "addItem", at = @At("HEAD"), require = 1)
    private void lads$item(GuiItemRenderState item, CallbackInfo ci) {
        if (HudCapture.record(state -> state.addItem(item)))
            HudCapture.drew(java.util.Objects.hashCode(item.itemStackRenderState().getModelIdentity()), java.util.Objects.hash(item.pose(), item.x(), item.y()));
    }

    /** Text: its characters; where, how big and turned, and its colours (a subtitle fading to grey, chroma text). */
    @Inject(method = "addText", at = @At("HEAD"), require = 1)
    private void lads$text(GuiTextRenderState text, CallbackInfo ci) {
        if (!HudCapture.record(state -> state.addText(text))) return;
        var fields = (GuiTextRenderStateAccessor) (Object) text;
        int[] what = {1}, how = {java.util.Objects.hash(text.pose, fields.lads$x(), fields.lads$y(), fields.lads$color(), fields.lads$background(), fields.lads$shadow())};
        fields.lads$text().accept((index, style, codePoint) -> {
            what[0] = 31 * what[0] + codePoint;
            how[0] = 31 * how[0] + java.util.Objects.hashCode(style.getColor());
            return true;
        });
        HudCapture.drew(what[0], how[0]);
    }

    /** A picture: its kind, and where and how big (what it shows is the renderer's: Xaero's minimap, the paper doll). */
    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"), require = 1)
    private void lads$picture(PictureInPictureRenderState picture, CallbackInfo ci) {
        if (HudCapture.record(state -> HudCapture.replayPicture(state, picture)))
            HudCapture.drew(picture.getClass().hashCode(), java.util.Objects.hashCode(picture.bounds()));
    }

    /**
     * Recorded unfaded whichever HEAD injector runs first: the replay fades it again in its recorded Autohide opacity (HudCapture).
     * Elements count by kind: vanilla's are records (texture, pose, corners, UVs, colour), so any change to one of them is a step;
     * HudFrameCap only takes it for an animation when it goes on. Any other kind counts by where it is and how big.
     */
    @Inject(method = "addGuiElement", at = @At("HEAD"), require = 1)
    private void lads$element(GuiElementRenderState element, CallbackInfo ci) {
        GuiElementRenderState plain = element instanceof com.thelads.core.v26_2.feature.FadedElement faded ? faded.element() : element;
        if (HudCapture.record(state -> state.addGuiElement(plain)))
            HudCapture.drew(plain.getClass().hashCode(), plain instanceof Record ? plain.hashCode() : java.util.Objects.hashCode(plain.bounds()));
    }
}
