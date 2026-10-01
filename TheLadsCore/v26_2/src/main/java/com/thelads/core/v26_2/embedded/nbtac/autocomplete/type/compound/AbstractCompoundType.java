// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.compound;

import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedTag;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.tag.NbtTag;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.Type;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractCompoundType implements Type
{
	@Override public @Nullable SuggestionList getSuggestions(SuggestionListContext ctx)
	{
		if (!(ctx.parsed() instanceof ParsedCompound parsed) || parsed.isEmpty())
		{
			// ctx.expectedOperators() could be null
			return new SuggestionList(ctx.parsed().pos).withOperators("{");
		}
		if (parsed.isClosed()) { return null; }

		ParsedTag lastTag = parsed.getLast();
		int lastTagPos = parsed.getLastPos();

		NbtTagMap tagMap = getSuggestionsTagMap(parsed);
		if (tagMap == null) { return SuggestionList.empty(); }

		if (ctx.expectedOperators() != null)
		{
			if (lastTag.key == null)
			{
				throw new RuntimeException("This should not happen!");
			}
			else if (lastTag.val == null) // e.g. {SomeKey
			{
				return tagMap.containsKey(lastTag.key)
						? ctx.expectedOperators()
						: tagMap.suggestionsForKeyPrefix(ctx.parserType(), parsed, lastTag.key, lastTagPos, true);
			}
		}
		else
		{
			if (lastTag.key == null) // e.g. { or {SomeKey:123,
			{
				return tagMap.suggestionsForKeyPrefix(ctx.parserType(), parsed, "", lastTagPos, true);
			}
		}

		// e.g. {SomeKey: or {SomeKey:123
		NbtTag tag = tagMap.get(lastTag.key);
		SuggestionList valList = tag != null ? tag.getType().getSuggestions(ctx.child(lastTag.val)) : SuggestionList.empty();
		return valList != null ? valList : new SuggestionList(ctx.reader().getCursor()).withOperators(",", "}");
	}

	@Override public PrimitiveType getPrimitive()
	{
		return PrimitiveType.COMPOUND;
	}
}
