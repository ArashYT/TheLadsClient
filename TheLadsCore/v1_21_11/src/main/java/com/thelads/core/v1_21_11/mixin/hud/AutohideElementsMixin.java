package com.thelads.core.v1_21_11.mixin.hud;

import com.thelads.core.v1_21_11.feature.FadedElement;
import com.thelads.core.v1_21_11.feature.NativeAutohide;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Faded Autohide scopes (the 26.x mixin on 1.21.11's submit* names): partial alpha on elements, nothing at all when hidden. */
@Mixin(GuiRenderState.class)
public class AutohideElementsMixin {
    @ModifyVariable(method = "submitGuiElement", at = @At("HEAD"), argsOnly = true)
    private GuiElementRenderState ladsFade(GuiElementRenderState element) {
        return NativeAutohide.scopeOpacity < 1 ? new FadedElement(element, NativeAutohide.scopeOpacity) : element;
    }
    @Inject(method = {"submitGuiElement", "submitItem", "submitText", "submitPicturesInPictureState"}, at = @At("HEAD"), cancellable = true)
    private void ladsFullyHidden(CallbackInfo ci) { if (NativeAutohide.scopeOpacity == 0) ci.cancel(); }
    /** A faded picture (Xaero's minimap) keeps its opacity for the blit; entity pictures (a paper doll) fade themselves. */
    @Inject(method = "submitPicturesInPictureState", at = @At("HEAD"))
    private void ladsFadePicture(PictureInPictureRenderState state, CallbackInfo ci) {
        if (NativeAutohide.scopeOpacity > 0 && NativeAutohide.scopeOpacity < 1 && !(state instanceof GuiEntityRenderState))
            NativeAutohide.PICTURES.put(state, NativeAutohide.scopeOpacity);
    }
}
