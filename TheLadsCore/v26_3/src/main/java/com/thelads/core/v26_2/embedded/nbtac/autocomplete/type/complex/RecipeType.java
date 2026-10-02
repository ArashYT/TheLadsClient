// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.suggestions.IdSuggestion;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;

public class RecipeType extends ComplexType
{
	public static final RecipeType INSTANCE = new RecipeType();

	private RecipeType()
	{
		super(PrimitiveType.STRING);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) { return; }

		for (RecipeHolder<?> recipeHolder : server.getRecipeManager().getRecipes())
		{
			list.add(new IdSuggestion(recipeHolder.id().identifier(), null, ctx.parserType()));
		}
	}
}
