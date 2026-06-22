package com.thelads.core.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.thelads.core.modules.XaeroWorldMapModule;

public class XaeroMinimapHudElement extends HudElement {
    public XaeroMinimapHudElement() {
        this.x = 10;
        this.y = 10;
        this.width = 64;
        this.height = 64;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.thelads.core.client.gui.DraggableHudScreen) {
            drawBackground(g);
            drawCenteredText(g, "Xaero's Minimap");
        } else {
            XaeroWorldMapModule mapModule = XaeroWorldMapModule.getInstance();
            if (mapModule != null && mapModule.isEnabled()) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mapModule.needsUpdate() || (mc.level != null && mc.level.getGameTime() % 40 == 0)) {
                    mapModule.updateMap(mc);
                }

                net.minecraft.resources.Identifier texture = mapModule.getTextureLocation();
                if (texture != null) {
                    drawBackground(g);
                    g.blit(texture, x, y, x + width, y + height, 0.0f, 1.0f, 0.0f, 1.0f);
                    int px = x + width / 2;
                    int py = y + height / 2;
                    g.fill(px - 2, py - 2, px + 2, py + 2, 0xFFFF0000);
                }
            }
        }
    }
}
