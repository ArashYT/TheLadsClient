// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser;

import org.jetbrains.annotations.Nullable;

public abstract class ParsedValue
{
	public final @Nullable ParsedTag parentTag;
	public final int pos;

	public ParsedValue(@Nullable ParsedTag parent, int pos)
	{
		this.parentTag = parent;
		this.pos = pos;
	}
}
