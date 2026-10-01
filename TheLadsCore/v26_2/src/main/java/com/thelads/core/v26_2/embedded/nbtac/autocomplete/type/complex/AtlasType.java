// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.suggestions.IdSuggestion;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v26_2.embedded.nbtac.mixin.fields.AtlasManagerFields;

import java.util.Collection;

public class AtlasType extends ComplexType
{
	public static final AtlasType INSTANCE = new AtlasType();

	private AtlasType()
	{
		super(PrimitiveType.STRING);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		Collection<Identifier> atlases = ((AtlasManagerFields)Minecraft.getInstance().getAtlasManager()).nbtac$getAtlasById().keySet();
		atlases.forEach((id) -> list.add(new IdSuggestion(id, "[#atlas]", ctx.parserType())));
	}
}
