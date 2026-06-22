/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.util.Mth
 */
package squeek.appleskin.helpers;

import net.minecraft.util.Mth;

public class ColorHelper {
    public static int argbFromRGBA(float r, float g, float b, float a) {
        return Mth.floor((double)((double)a * 255.0)) << 24 | Mth.floor((double)((double)r * 255.0)) << 16 | Mth.floor((double)((double)g * 255.0)) << 8 | Mth.floor((double)((double)b * 255.0));
    }
}

