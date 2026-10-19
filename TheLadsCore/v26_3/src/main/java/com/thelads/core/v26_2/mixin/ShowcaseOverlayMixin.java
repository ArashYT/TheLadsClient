package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.showcase.ShowcaseDirector26;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Showcase director only: its drawn mouse cursor on top of every screen, so menu videos show the mouse travel and click. */
@Mixin(Screen.class)
public abstract class ShowcaseOverlayMixin {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void lads$showcaseCursor(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ShowcaseDirector26.drawCursor(g);
    }
}
