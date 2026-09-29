// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import com.thelads.core.v26_2.feature.food.api.handler.EventHandler;

public class TooltipOverlayEvent
{
	/**
	 * If cancelled, will stop all rendering from happening.
	 */
	public static class Pre extends TooltipOverlayEvent
	{
		public Pre(ItemStack itemStack, FoodProperties defaultFood, FoodProperties modifiedFood)
		{
			super(itemStack, defaultFood, modifiedFood);
		}

		public static Event<EventHandler<Pre>> EVENT = EventHandler.createArrayBacked();
	}

	/**
	 * If cancelled, will reserve space for the food values, but will not
	 * render them.
	 */
	public static class Render extends TooltipOverlayEvent
	{
		public Render(ItemStack itemStack, int x, int y, GuiGraphicsExtractor context, FoodProperties defaultFood, FoodProperties modifiedFood)
		{
			super(itemStack, defaultFood, modifiedFood);
			this.context = context;
			this.x = x;
			this.y = y;
		}

		public int x;
		public int y;
		public GuiGraphicsExtractor context;

		public static Event<EventHandler<Render>> EVENT = EventHandler.createArrayBacked();
	}

	private TooltipOverlayEvent(ItemStack itemStack, FoodProperties defaultFood, FoodProperties modifiedFood)
	{
		this.itemStack = itemStack;
		this.defaultFood = defaultFood;
		this.modifiedFood = modifiedFood;
	}

	public final FoodProperties defaultFood;
	public final FoodProperties modifiedFood;

	public final ItemStack itemStack;

	public boolean isCanceled = false;
}
