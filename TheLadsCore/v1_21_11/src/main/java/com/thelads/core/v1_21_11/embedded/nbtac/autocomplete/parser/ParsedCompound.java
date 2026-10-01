// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.parser;

import org.jetbrains.annotations.Nullable;

public class ParsedCompound extends ParsedCollection<ParsedTag>
{
	public ParsedCompound(@Nullable ParsedTag parent, int pos)
	{
		super(parent, pos);
	}

	public @Nullable ParsedTag get(String key)
	{
		for (ParsedTag tag : list)
		{
			if (key.equals(tag.key)) { return tag; }
		}
		return null;
	}

	public boolean containsKey(String key)
	{
		return get(key) != null;
	}

	public @Nullable String getStrVal(String key)
	{
		ParsedTag tag = get(key);
		return (tag != null && tag.val instanceof ParsedPrimitive primitive) ? primitive.val : null;
	}
}
