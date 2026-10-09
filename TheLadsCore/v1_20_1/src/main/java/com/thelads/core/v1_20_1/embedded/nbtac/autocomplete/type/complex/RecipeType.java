// Adapted from NBT Autocomplete 2.1 for Minecraft 1.20.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import com.thelads.core.v1_20_1.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_20_1.embedded.nbtac.autocomplete.suggestions.IdSuggestion;
import com.thelads.core.v1_20_1.embedded.nbtac.autocomplete.type.PrimitiveType;

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

		for (Recipe<?> recipe : server.getRecipeManager().getRecipes())
		{
			list.add(new IdSuggestion(recipe.getId(), null, ctx.parserType()));
		}
	}
}
