package com.thelads.core.v26_2.cosmetics.anim.mixin;

import com.thelads.core.v26_2.cosmetics.anim.ShowcaseEmote26;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Showcase only (ShowcaseEmote26): first person keeps the hand with the HUD hidden (F1), as the clean shots need. */
@Mixin(GameRenderer.class)
public class ShowcaseHandsMixin26 {
    @Redirect(method = "renderItemInHand", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;isHudHidden:Z"))
    private boolean lads$showcaseHands(GuiRenderState state) {
        return state.isHudHidden && !ShowcaseEmote26.handsWithHudHidden();
    }
}