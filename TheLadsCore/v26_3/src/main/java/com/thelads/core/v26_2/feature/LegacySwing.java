package com.thelads.core.v26_2.feature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.OldAnimations;
/** Motion adapted from NILLYY's Legacy Console Edition Swing Animation 1.0.0 (bundled CC0 license), shared as OldAnimations.legacySwing.
 * Reference: https://www.youtube.com/watch?v=kTVpIkeOod4 */
public final class LegacySwing {
    private LegacySwing() {}
    /** swingArm HEAD (LegacySwingMixin): Legacy Swing in place of vanilla's swing; false leaves vanilla's. */
    public static boolean apply(PoseStack pose,float progress,int side){
        if(!NativeQualityOfLife.enabled("LegacySwing")||progress<=0)return false;
        OldAnimations.legacySwing(NativeOldAnimations.sink(pose),side,progress);
        return true;
    }
}
