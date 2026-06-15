package com.thelads.core.modules;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;

public class FullbrightModule extends Module {
    private Double savedGamma = null;
    private boolean applied = false;
    private Double acceptedGamma = null;

    // Gamma values: High=3.0, Very High=5.0, Max=10.0, Infinite=100.0
    private static final double[] GAMMA_LEVELS = {3.0, 5.0, 10.0, 100.0};

    public FullbrightModule() {
        super("Fullbright", "Boost brightness to maximum (gamma).");
    }

    public void tick(Minecraft mc) {
        if (mc == null || mc.options == null) return;

        if (isEnabled() && !applied) {
            savedGamma = mc.options.gamma().get();
            mc.options.gamma().set(getTargetGamma());
            // 26.1.2 validates gamma — remember what the option actually accepted
            // (it may clamp/reject) so we don't re-set a rejected value every tick.
            acceptedGamma = mc.options.gamma().get();
            applied = true;
        } else if (!isEnabled() && applied) {
            if (savedGamma != null) mc.options.gamma().set(savedGamma);
            applied = false;
            acceptedGamma = null;
        } else if (isEnabled() && applied) {
            // Re-assert only if something else changed the value since we set it.
            Double current = mc.options.gamma().get();
            if (acceptedGamma == null || current == null || !current.equals(acceptedGamma)) {
                mc.options.gamma().set(getTargetGamma());
                acceptedGamma = mc.options.gamma().get();
            }
        }
    }

    private double getTargetGamma() {
        Option o = getOption("Level");
        if (o instanceof CycleOption) {
            int idx = ((CycleOption) o).getIndex();
            if (idx >= 0 && idx < GAMMA_LEVELS.length) return GAMMA_LEVELS[idx];
        }
        return 10.0;
    }
}
