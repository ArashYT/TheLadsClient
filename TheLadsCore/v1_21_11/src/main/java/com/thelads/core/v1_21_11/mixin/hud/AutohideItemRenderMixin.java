package com.thelads.core.v1_21_11.mixin.hud;

import com.thelads.core.v1_21_11.feature.FadedItem;
import com.thelads.core.v1_21_11.feature.NativeAutohide;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Items are drawn from the GUI item atlas later in the frame; their blit takes the opacity the item was submitted at. */
@Mixin(GuiRenderer.class)
public class AutohideItemRenderMixin {
    @Unique private float ladsItemAlpha = 1;
    @Inject(method = "submitBlitFromItemAtlas", at = @At("HEAD"))
    private void ladsItem(GuiItemRenderState item, float u, float v, int size, int atlasSize, CallbackInfo ci) {
        ladsItemAlpha = ((FadedItem) (Object) item).ladsOpacity();
    }
    @Redirect(method = "submitBlitFromItemAtlas", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;submitBlitToCurrentLayer(Lnet/minecraft/client/gui/render/state/BlitRenderState;)V"))
    private void ladsFadeItem(GuiRenderState state, BlitRenderState blit) {
        state.submitBlitToCurrentLayer(ladsItemAlpha < 1 ? NativeAutohide.fade(blit, ladsItemAlpha) : blit);
    }
}
