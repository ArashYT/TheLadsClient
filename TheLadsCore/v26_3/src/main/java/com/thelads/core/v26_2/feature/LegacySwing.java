package com.thelads.core.v26_2.feature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
/** Motion adapted from NILLYY's Legacy Console Edition Swing Animation 1.0.0 (bundled CC0 license).
 * Reference: https://www.youtube.com/watch?v=kTVpIkeOod4 */
public final class LegacySwing {
    private LegacySwing() {}
    public static boolean apply(PoseStack pose,float progress,int side){
        if(!NativeQualityOfLife.enabled("LegacySwing")||progress<=0)return false;
        float t=progress*progress*progress*progress;
        float arc=Mth.sin(Mth.sqrt(t)*(float)Math.PI);
        float lift=Mth.sin(Mth.sqrt(t)*(float)Math.PI*2);
        float depth=Mth.sin(t*(float)Math.PI);
        pose.translate(side*-arc*.55F,lift*.25F,-depth*.2F);
        pose.rotate(Axis.YP.rotationDegrees(side*(45+arc*-20)));
        pose.rotate(Axis.ZP.rotationDegrees(side*arc*-20));
        pose.rotate(Axis.XP.rotationDegrees(arc*-80));
        pose.rotate(Axis.YP.rotationDegrees(side*-45));
        return true;
    }
}
