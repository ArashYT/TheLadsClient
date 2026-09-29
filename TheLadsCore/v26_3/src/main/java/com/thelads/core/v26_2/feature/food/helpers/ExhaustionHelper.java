// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.helpers;

import net.minecraft.world.entity.player.Player;

public class ExhaustionHelper
{
	public interface ExhaustionManipulator
	{
		float ladsFoodExhaustion();

		void ladsSetFoodExhaustion(float exhaustion);
	}

	public static float getExhaustion(Player player)
	{
		return ((ExhaustionManipulator) player.getFoodData()).ladsFoodExhaustion();
	}

	public static void setExhaustion(Player player, float exhaustion)
	{
		((ExhaustionManipulator) player.getFoodData()).ladsSetFoodExhaustion(exhaustion);
	}
}
