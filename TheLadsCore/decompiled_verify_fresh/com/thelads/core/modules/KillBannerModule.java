/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.modules;

import com.thelads.core.config.ColorOption;
import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.modules.killbanner.KillBannerRenderer;

public class KillBannerModule
extends Module {
    public final ColorOption textColor = new ColorOption("Text Color", false, -65536);
    public final CycleOption bannerStyle = new CycleOption("Style", 0, "Base", "Reaver");

    public KillBannerModule() {
        super("KillBanner", "Show a Valorant-style kill banner on player kills.");
        this.addOption(this.bannerStyle);
        this.addOption(this.textColor);
    }

    public void handleTestKillBannerCommand() {
        KillBannerRenderer.triggerKill();
    }
}

