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
        com.thelads.core.v26_2.feature.HudTexture.added((GuiRenderState) (Object) this);
        if (HudCapture.record(state -> state.addItem(item)))
            HudCapture.drew(java.util.Objects.hashCode(item.itemStackRenderState().getModelIdentity()), java.util.Objects.hash(item.pose(), item.x(), item.y()));
    }

    private static final class TextAccumulator implements net.minecraft.util.FormattedCharSink {
        int what;
        int how;

        @Override
        public boolean accept(int index, net.minecraft.network.chat.Style style, int codePoint) {
            what = 31 * what + codePoint;
            var color = style.getColor();
            how = 31 * how + (color != null ? color.hashCode() : 0);
            return true;
        }
    }

    private static final TextAccumulator ACCUMULATOR = new TextAccumulator();

    /** Text: its characters; where, how big and turned, and its colours (a subtitle fading to grey, chroma text). */
    @Inject(method = "addText", at = @At("HEAD"), require = 1)
    private void lads$text(GuiTextRenderState text, CallbackInfo ci) {
        com.thelads.core.v26_2.feature.HudTexture.added((GuiRenderState) (Object) this);
        if (!HudCapture.record(state -> state.addText(text))) return;
        var fields = (GuiTextRenderStateAccessor) (Object) text;
        int how = 1;
        how = 31 * how + (text.pose != null ? text.pose.hashCode() : 0);
        how = 31 * how + fields.lads$x();
        how = 31 * how + fields.lads$y();
        how = 31 * how + fields.lads$color();
        how = 31 * how + fields.lads$background();
        how = 31 * how + (fields.lads$shadow() ? 1231 : 1237);

        TextAccumulator acc = ACCUMULATOR;
        acc.what = 1;
        acc.how = how;
        fields.lads$text().accept(acc);
        HudCapture.drew(acc.what, acc.how);
    }

    /** A picture: its kind, and where and how big (what it shows is the renderer's: Xaero's minimap, the paper doll). */
    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"), require = 1)
    private void lads$picture(PictureInPictureRenderState picture, CallbackInfo ci) {
        com.thelads.core.v26_2.feature.HudTexture.added((GuiRenderState) (Object) this);
        if (HudCapture.record(state -> HudCapture.replayPicture(state, picture)))
            HudCapture.drew(picture.getClass().hashCode(), java.util.Objects.hashCode(picture.bounds()));
    }

    /**
     * Recorded unfaded whichever HEAD injector runs first: the replay fades it again in its recorded Autohide opacity (HudCapture).
     * Elements count by kind: vanilla's are records (texture, pose, corners, UVs, colour), so any change to one of them is a step;
     * HudFrameCap only takes it for an animation when it goes on. Any other kind counts by where it is and how big.
     */
    @Inject(method = "addGuiElement", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$element(GuiElementRenderState element, CallbackInfo ci) {
        GuiElementRenderState plain = element instanceof com.thelads.core.v26_2.feature.FadedElement faded ? faded.element() : element;
        if (HudCapture.record(state -> state.addGuiElement(plain)))
            HudCapture.drew(plain.getClass().hashCode(), plain instanceof Record ? plain.hashCode() : java.util.Objects.hashCode(plain.bounds()));
        // HUD texture (HudTexture): blending that reads what is under it is drawn live every frame, not baked into the image
        if (com.thelads.core.v26_2.feature.HudTexture.keepLive((GuiRenderState) (Object) this, element)) ci.cancel();
        else com.thelads.core.v26_2.feature.HudTexture.added((GuiRenderState) (Object) this);
    }
}
