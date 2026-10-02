// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex;

import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.Type;
import org.jetbrains.annotations.Nullable;

public abstract class ComplexType implements Type
{
	protected final PrimitiveType primitive;

	public ComplexType(PrimitiveType primitive)
	{
		this.primitive = primitive;
	}

	@Override public @Nullable SuggestionList getSuggestions(SuggestionListContext ctx)
	{
		SuggestionList list = new SuggestionList(ctx.parsed().pos);
		getBasicSuggestions(ctx, list);
		return list.matchOrFiler(ctx.getRemaining());
	}

	public abstract void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list);

	@Override public PrimitiveType getPrimitive()
	{
		return primitive;
	}
}
