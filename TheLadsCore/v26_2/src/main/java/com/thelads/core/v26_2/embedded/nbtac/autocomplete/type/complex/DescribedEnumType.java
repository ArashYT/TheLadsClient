// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex;

import com.mojang.datafixers.util.Pair;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.suggestions.CustomSuggestion;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.Type;

import java.util.ArrayList;
import java.util.List;

public class DescribedEnumType extends ComplexType
{
	private final List<Pair<String, String>> elements = new ArrayList<>();

	public DescribedEnumType(List<Type> types, List<String> args)
	{
		super(types.isEmpty() ? PrimitiveType.STRING : types.getFirst().getPrimitive());
		for (int i = 0; i < args.size() / 2; i++)
		{
			elements.add(Pair.of(args.get(i * 2), args.get((i * 2) + 1)));
		}
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		elements.forEach((e) -> list.add(CustomSuggestion.fromType(e.getFirst(),
				"<" + e.getSecond() + ">", this, ctx.parserType(), 0)));
	}
}
