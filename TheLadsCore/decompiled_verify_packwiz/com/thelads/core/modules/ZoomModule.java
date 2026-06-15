/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.util.Mth
 */
package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public class ZoomModule
extends Module {
    private static ZoomModule instance;
    private float currentFovMultiplier = 1.0f;
    private float prevFovMultiplier = 1.0f;
    private float targetFovMultiplier = 1.0f;
    private final BoolOption smoothZoom = new BoolOption("Smooth Zoom", true);
    private final BoolOption scrollZoom = new BoolOption("Scroll to Zoom", true);

    public ZoomModule() {
        super("Zoom", "Hold the zoom key to narrow your FOV.");
        this.addOption(this.smoothZoom);
        this.addOption(this.scrollZoom);
        instance = this;
    }

    public static ZoomModule getInstance() {
        return instance;
    }

    public void tick(Minecraft mc, boolean keyDown) {
        boolean active;
        if (mc == null) {
            return;
        }
        this.prevFovMultiplier = this.currentFovMultiplier;
        boolean bl = active = this.isEnabled() && keyDown;
        if (!active) {
            this.targetFovMultiplier = 1.0f;
        } else if (this.targetFovMultiplier == 1.0f) {
            this.targetFovMultiplier = 0.25f;
        }
        this.currentFovMultiplier = this.smoothZoom.get() ? Mth.lerp((float)0.3f, (float)this.currentFovMultiplier, (float)this.targetFovMultiplier) : this.targetFovMultiplier;
    }

    public float getFovMultiplier(float tickDelta) {
        if (this.smoothZoom.get()) {
            return Mth.lerp((float)tickDelta, (float)this.prevFovMultiplier, (float)this.currentFovMultiplier);
        }
        return this.currentFovMultiplier;
    }

    public boolean isActive() {
        return this.targetFovMultiplier != 1.0f;
    }

    public void onScroll(double amount) {
        if (!this.isEnabled() || !this.scrollZoom.get()) {
            return;
        }
        if (amount > 0.0) {
            this.targetFovMultiplier = Mth.clamp((float)(this.targetFovMultiplier - 0.05f), (float)0.05f, (float)0.8f);
        } else if (amount < 0.0) {
            this.targetFovMultiplier = Mth.clamp((float)(this.targetFovMultiplier + 0.05f), (float)0.05f, (float)0.8f);
        }
    }
}

