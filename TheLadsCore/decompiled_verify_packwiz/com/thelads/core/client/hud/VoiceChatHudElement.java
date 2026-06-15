/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class VoiceChatHudElement
extends HudElement {
    public VoiceChatHudElement() {
        this.x = 0;
        this.y = 0;
        this.width = 100;
        this.height = 32;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        if (Minecraft.getInstance().screen instanceof DraggableHudScreen) {
            this.drawBackground(g);
            this.drawCenteredText(g, "Voice Chat");
        }
    }
}

