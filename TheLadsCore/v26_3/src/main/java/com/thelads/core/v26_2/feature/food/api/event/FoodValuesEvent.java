// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import com.thelads.core.v26_2.feature.food.api.handler.EventHandler;

/**
 * Can be used to customize the displayed hunger/saturation values of foods.
 * Called whenever the food values of items are being determined.
 */
public class FoodValuesEvent
{
	public FoodValuesEvent(Player player, ItemStack itemStack, FoodProperties defaultFoodValues, FoodProperties modifiedFoodComponent)
	{
		this.player = player;
		this.itemStack = itemStack;
		this.defaultFoodComponent = defaultFoodValues;
		this.modifiedFoodComponent = modifiedFoodComponent;
	}

	public FoodProperties defaultFoodComponent;
	public FoodProperties modifiedFoodComponent;
	public final ItemStack itemStack;
	public final Player player;

	public static Event<EventHandler<FoodValuesEvent>> EVENT = EventHandler.createArrayBacked();
}
