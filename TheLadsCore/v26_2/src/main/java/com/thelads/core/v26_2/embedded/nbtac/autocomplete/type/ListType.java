// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type;

import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedList;
import org.jetbrains.annotations.Nullable;

public class ListType implements Type
{
	private final Type elementType;

	public ListType(Type elementType)
	{
		this.elementType = (elementType != null ? elementType : PrimitiveType.UNKNOWN);
	}

	@Override public SuggestionList getSuggestions(SuggestionListContext ctx)
	{
		if (!(ctx.parsed() instanceof ParsedList parsed) || parsed.isEmpty())
		{
			// ctx.expectedOperators() could be null
			return new SuggestionList(ctx.parsed().pos).withOperators("[");
		}
		if (parsed.isClosed()) { return null; }

		// e.g. [123
		SuggestionList list = elementType.getSuggestions(ctx.child(parsed.getLast()));
		return list != null ? list : new SuggestionList(ctx.reader().getCursor()).withOperators(",", "]");
	}

	@Override public PrimitiveType getPrimitive()
	{
		return PrimitiveType.LIST;
	}

	@Override public NbtTagMap getMutableTagMap()
	{
		return elementType.getMutableTagMap();
	}

	@Override public @Nullable NbtTagMap getSuggestionsTagMap(ParsedCompound parsed)
	{
		return elementType.getSuggestionsTagMap(parsed);
	}

	@Override public void setTagMap(@Nullable NbtTagMap subcompound)
	{
		elementType.setTagMap(subcompound);
	}

	public Type getElementType()
	{
		return elementType;
	}
}
