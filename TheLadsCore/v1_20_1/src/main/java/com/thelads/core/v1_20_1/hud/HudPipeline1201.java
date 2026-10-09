package com.thelads.core.v1_20_1.hud;

import com.thelads.core.v1_20_1.feature.NativeAutohide1201;
import com.thelads.core.v1_20_1.feature.NativeKillBanner1201;
import net.minecraft.client.gui.GuiGraphics;

/** What the Lads HUD does around vanilla's in-game GUI each frame (called from GameRendererHudMixin). */
public final class HudPipeline1201 {
    private HudPipeline1201() {}

    public static void beforeGui(GuiGraphics graphics) {
        NativeAutohide1201.scopeOpacity = 1;
    }

    /** After the whole vanilla GUI (and the mods' HUDs drawn with it): the Lads HUD, then the kill banner above it. */
    public static void afterGui(GuiGraphics graphics) {
        try {
            NativeAutohide1201.renderLadsHud(graphics);
        } catch (RuntimeException failure) {
            Hud1201.LOG.error("Lads HUD failed to render", failure);
        } finally {
            NativeAutohide1201.scopeOpacity = 1;
        }
        NativeKillBanner1201.render(graphics);
    }
}
