package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.OldAnimations;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * LegacySwing on 1.8.9, as on the other versions: the Legacy Console Edition swing of the held item (OldAnimations.legacySwing),
 * where 1.8.9 turns it at once. ItemRendererMixin calls it in place of vanilla's swing (and its translation), and stops the pop
 * after using an item.
 */
public final class LegacySwing189 {
    /** QA only (Probe145): frames drawn with the legacy swing. */
    public static long frames;

    private LegacySwing189() {}

    public static boolean enabled() {
        Module module = ModuleManager.getInstance().getModule("LegacySwing");
        return module != null && module.isEnabled();
    }

    /** ItemRenderer.transformFirstPersonItem with the legacy swing; false leaves it to vanilla. */
    public static boolean transform(float equipProgress, float swingProgress) {
        if (swingProgress <= 0 || !enabled()) return false;
        OldAnimations.legacySwingHand(OldAnimations189.GL, 1, equipProgress, swingProgress); // to the end of transformFirstPersonItem
        frames++;
        return true;
    }
}
