// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type;

import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import org.jetbrains.annotations.Nullable;

public class EmptyType implements Type
{
	public static final EmptyType INSTANCE = new EmptyType();

	@Override public @Nullable SuggestionList getSuggestions(SuggestionListContext ctx)
	{
		return SuggestionList.empty();
	}

	@Override public PrimitiveType getPrimitive()
	{
		return PrimitiveType.UNKNOWN;
	}
}
