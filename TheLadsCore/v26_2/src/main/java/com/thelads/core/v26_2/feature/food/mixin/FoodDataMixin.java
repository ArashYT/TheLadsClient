// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import com.thelads.core.v26_2.feature.food.helpers.ExhaustionHelper;

@Mixin(FoodData.class)
public class FoodDataMixin implements ExhaustionHelper.ExhaustionManipulator
{
	@Shadow
	private float exhaustionLevel;

	@Override
	public void ladsSetFoodExhaustion(float value)
	{
		this.exhaustionLevel = value;
	}

	@Override
	public float ladsFoodExhaustion()
	{
		return this.exhaustionLevel;
	}
}
