/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.hud.HudElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class CpsHudElement
extends HudElement {
    public CpsHudElement() {
        this.x = 5;
        this.y = 265;
        this.width = 110;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        this.drawBackground(g);
        int l = CpsTracker.get().leftCps();
        int r = CpsTracker.get().rightCps();
        int show = this.optCycle("Show", 0);
        boolean label = this.optBool("Show label", true);
        Object text = show == 1 ? (label ? "L " + l + " CPS" : String.valueOf(l)) : (show == 2 ? (label ? "R " + r + " CPS" : String.valueOf(r)) : (label ? "L " + l + "  R " + r + "  CPS" : l + " / " + r));
        this.drawCenteredText(g, (String)text);
    }
}

