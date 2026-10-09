package com.thelads.core.v1_20_1.game.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.OldAnimations;
import com.thelads.core.config.ModuleManager;
import net.minecraft.util.Mth;

/**
 * Legacy Swing (module "LegacySwing"): Legacy Console Edition's swing in place of vanilla's, as 26.3's LegacySwing. Motion adapted
 * from NILLYY's Legacy Console Edition Swing Animation 1.0.0 (bundled CC0 license), shared as OldAnimations.legacySwing.
 */
public final class LegacySwing1201 {
    private LegacySwing1201() {}

    public static boolean enabled() {
        var module = ModuleManager.getInstance().getModule("LegacySwing");
        return module != null && module.isEnabled();
    }

    /**
     * applyItemArmAttackTransform, at its head: Legacy Swing in place of vanilla's swing. Vanilla moved the item by the swing
     * translation before the arm transform (a plain translation, so undoing it here leaves the hand place exact), then turned it.
     * False leaves vanilla's.
     */
    public static boolean apply(PoseStack pose, float progress, int side) {
        if (!enabled() || progress <= 0) return false;
        float root = Mth.sqrt(progress);
        pose.translate(-side * (-0.4f * Mth.sin(root * (float) Math.PI)), -(0.2f * Mth.sin(root * (float) Math.PI * 2)),
            -(-0.2f * Mth.sin(progress * (float) Math.PI)));
        OldAnimations.legacySwing(NativeOldAnimations1201.sink(pose), side, progress);
        return true;
    }
}
