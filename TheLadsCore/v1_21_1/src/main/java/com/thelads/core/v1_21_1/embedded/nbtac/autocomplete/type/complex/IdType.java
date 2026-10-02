// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.resources.ResourceLocation;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.suggestions.IdSuggestion;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.PrimitiveType;
import org.jetbrains.annotations.Nullable;

public class IdType extends ComplexType
{
	private final @Nullable ResourceLocation id;

	public IdType(@Nullable String id)
	{
		super(PrimitiveType.STRING);
		this.id = id != null ? ResourceLocation.tryParse(id) : null;
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		if (id != null) { list.add(new IdSuggestion(id, "[#id]", ctx.parserType())); }
	}
}
