// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.thelads.core.v26_2.feature.food.client.TooltipOverlayHandler;

import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackMixin
{
	@com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "getTooltipLines")
	private List<Component> getTooltipFromItem(Item.TooltipContext context, Player player, TooltipFlag type, com.llamalad7.mixinextras.injector.wrapoperation.Operation<List<Component>> call)
	{
        List<Component> original = call.call(context, player, type);
		if (TooltipOverlayHandler.INSTANCE == null || original.isEmpty()
			|| !com.thelads.core.v26_2.feature.food.FoodOverlayConfig.enabled()
			|| !com.thelads.core.v26_2.feature.food.helpers.FoodHelper.isFood((ItemStack) (Object) this)) return original;
		// Wrap the completed tooltip, including lines added by other mods' upstream RETURN callbacks.
		var result = new java.util.ArrayList<Component>(original);
		TooltipOverlayHandler.INSTANCE.onItemTooltip((ItemStack) (Object) this, player, context, type, result);
		return result;
	}
}
