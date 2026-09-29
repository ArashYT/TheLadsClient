// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.helpers;

import net.minecraft.util.Mth;

public class ColorHelper
{
	public static int argbFromRGBA(float r, float g, float b, float a)
	{
		return (Mth.floor(a * 255.0) << 24) |
			(Mth.floor(r * 255.0) << 16) |
			(Mth.floor(g * 255.0) << 8) |
			Mth.floor(b * 255.0);
	}
}
