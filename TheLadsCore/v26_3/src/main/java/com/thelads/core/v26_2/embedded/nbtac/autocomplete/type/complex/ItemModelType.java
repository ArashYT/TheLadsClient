// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.suggestions.IdSuggestion;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v26_2.embedded.nbtac.mixin.fields.ModelManagerFields;

public class ItemModelType extends ComplexType
{
	public static final ItemModelType INSTANCE = new ItemModelType();

	private ItemModelType()
	{
		super(PrimitiveType.STRING);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		ModelManager modelManager = Minecraft.getInstance().getModelManager();
		for (Identifier id : ((ModelManagerFields)modelManager).nbtac$getBakedItemStackModels().keySet())
		{
			list.add(new IdSuggestion(id, "[#item_model]", ctx.parserType()));
		}
	}
}
