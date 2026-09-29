// Adapted from TabTweaks 1.5.11 by MicrocontrollersDev, LGPL-3.0-only.
// Source faa19c704c3c967e1cf0f0355791f9d90d47a1c5; corresponding source in META-INF/lads-sources/tabtweaks.
package com.thelads.core.v26_2.feature.tabtweaks.mixin;

import com.thelads.core.v26_2.feature.tabtweaks.Head;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerFaceExtractor.class)
public abstract class PlayerFaceRendererMixin implements Head {
    @Shadow
    private static void extractHat(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int size, boolean flip, int color) { }

    @Unique
    @Override
    public void ladsTab$draw(GuiGraphicsExtractor guiGraphics, Identifier skinTexture, int x, int y, int size, boolean drawHat, boolean upsideDown, int color) {
        int i = 8 + (upsideDown ? 8 : 0);
        int j = 8 * (upsideDown ? -1 : 1);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture, x, y, size, size, 8, i, 8, j, 64, 64);
        if (drawHat) {
            guiGraphics.pose().pushMatrix();
            try {
                guiGraphics.pose().translate(-0.5F, -0.5F);
                extractHat(guiGraphics, skinTexture, x, y, 9, upsideDown, color);
            } finally { guiGraphics.pose().popMatrix(); }
        }
    }
}
