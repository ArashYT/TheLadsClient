/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.modules;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;

public class FullbrightModule
extends Module {
    private Double savedGamma = null;
    private boolean applied = false;
    private Double acceptedGamma = null;
    private static final double[] GAMMA_LEVELS = new double[]{3.0, 5.0, 10.0, 100.0};

    public FullbrightModule() {
        super("Fullbright", "Boost brightness to maximum (gamma).");
    }

    public void tick(Minecraft mc) {
        if (mc == null || mc.options == null) {
            return;
        }
        if (this.isEnabled() && !this.applied) {
            this.savedGamma = (Double)mc.options.gamma().get();
            mc.options.gamma().set((Object)this.getTargetGamma());
            this.acceptedGamma = (Double)mc.options.gamma().get();
            this.applied = true;
        } else if (!this.isEnabled() && this.applied) {
            if (this.savedGamma != null) {
                mc.options.gamma().set((Object)this.savedGamma);
            }
            this.applied = false;
            this.acceptedGamma = null;
        } else if (this.isEnabled() && this.applied) {
            Double current = (Double)mc.options.gamma().get();
            if (this.acceptedGamma == null || current == null || !current.equals(this.acceptedGamma)) {
                mc.options.gamma().set((Object)this.getTargetGamma());
                this.acceptedGamma = (Double)mc.options.gamma().get();
            }
        }
    }

    private double getTargetGamma() {
        int idx;
        Option o = this.getOption("Level");
        if (o instanceof CycleOption && (idx = ((CycleOption)o).getIndex()) >= 0 && idx < GAMMA_LEVELS.length) {
            return GAMMA_LEVELS[idx];
        }
        return 10.0;
    }
}

