// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.parser;

import com.thelads.core.v1_21_1.embedded.nbtac.utils.SimpleStringReader;
import org.jetbrains.annotations.Nullable;

public class ParsedPrimitive extends ParsedValue
{
	public @Nullable String val;
	public boolean closed = false;
	public char quoteType = '\0';

	public ParsedPrimitive(@Nullable ParsedTag parent, int pos)
	{
		super(parent, pos);
	}

	public void setFromReader(SimpleStringReader.StringResults results)
	{
		this.val = results.str;
		this.closed = results.closed;
		this.quoteType = results.quoteType;
	}
}
