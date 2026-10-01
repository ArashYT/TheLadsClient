// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.parser;

import org.jetbrains.annotations.Nullable;

public class ParsedList extends ParsedCollection<ParsedValue>
{
	public ParsedList(@Nullable ParsedTag parent, int pos)
	{
		super(parent, pos);
	}
}
