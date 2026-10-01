package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;

/**
 * LegacySwing on 1.8.9, as on 26.x (feature/LegacySwing): the Legacy Console Edition swing of the held item. Progress^4, so the
 * item winds up slowly and snaps through, swung further across (0.55) and lifted on the way, where 1.8.9 turns it at once.
 * ItemRendererMixin calls it, and keeps the item up instead of the re-equip dip (26.x LegacySwingMixin).
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
        float t = swingProgress * swingProgress * swingProgress * swingProgress;
        float arc = MathHelper.sin(MathHelper.sqrt_float(t) * (float) Math.PI);
        float lift = MathHelper.sin(MathHelper.sqrt_float(t) * (float) Math.PI * 2);
        float depth = MathHelper.sin(t * (float) Math.PI);
        GlStateManager.translate(0.56f, -0.52f - equipProgress * 0.6f, -0.71999997f); // vanilla's hand place
        GlStateManager.translate(-arc * 0.55f, lift * 0.25f, -depth * 0.2f);
        GlStateManager.rotate(45 - arc * 20, 0, 1, 0); // 1.8.9 keeps its 45° hand turn after the swing; 26.x undoes it
        GlStateManager.rotate(arc * -20, 0, 0, 1);
        GlStateManager.rotate(arc * -80, 1, 0, 0);
        GlStateManager.scale(0.4f, 0.4f, 0.4f);
        frames++;
        return true;
    }
}
