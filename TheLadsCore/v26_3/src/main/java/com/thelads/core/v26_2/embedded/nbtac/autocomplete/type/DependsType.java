// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type;

import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DependsType implements Type
{
	private final String tagName;
	private final List<String> values;
	private final List<Type> types;

	public DependsType(List<Type> types, List<String> args)
	{
		this.tagName = args.getFirst();
		this.values = args.subList(1, args.size());
		this.types = types;
		if (values.size() != types.size()) { throw new RuntimeException(); }
	}

	@Override public @Nullable SuggestionList getSuggestions(SuggestionListContext ctx)
	{
		ParsedTag parentTag = ctx.parsed().parentTag;
		if (parentTag == null) { return SuggestionList.empty(); }

		String val = parentTag.parentCompound.getStrVal(tagName);
		if (val == null) { return SuggestionList.empty(); }

		int index = values.indexOf(val);
		if (index == -1) { index = values.indexOf("minecraft:" + val); }

		return index != -1 ? types.get(index).getSuggestions(ctx) : SuggestionList.empty();
	}

	@Override public String getSubtext()
	{
		return "[depends]";
	}

	@Override public PrimitiveType getPrimitive()
	{
		return PrimitiveType.UNKNOWN;
	}
}
