// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.string;

import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.Type;
import org.jetbrains.annotations.Nullable;

public abstract class ComplexStringType implements Type
{
	@Override public @Nullable SuggestionList getSuggestions(SuggestionListContext ctx)
	{
		SuggestionList list = new SuggestionList(ctx.parsed().pos);
		int offset = getBasicSuggestions(ctx, list);
		if (offset == -1) { return null; }
		if (offset == -2) { return ctx.expectedOperators(); }

		list.cursor += offset;
		return list;
	}

	public abstract int getBasicSuggestions(SuggestionListContext ctx, SuggestionList list);

	@Override public PrimitiveType getPrimitive()
	{
		return PrimitiveType.STRING;
	}

	protected static String escapeStr(String str, char quote)
	{
		StringBuilder builder = new StringBuilder();
		for (char ch : str.toCharArray())
		{
			if (ch == '\\')
			{
				builder.append("\\\\");
			}
			else if (ch == quote)
			{
				builder.append('\\');
				builder.append(quote);
			}
			else
			{
				builder.append(ch);
			}
		}
		return builder.toString();
	}
}
