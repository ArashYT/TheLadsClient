// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.parser;

public enum ParserType
{
	VALUE(false, false),
	PATH(false, true),
	JSON(true, false);

	public final boolean requiresDoubleQuotes;
	public final boolean requiresNamespace;

	ParserType(boolean requiresDoubleQuotes, boolean requiresNamespace)
	{
		this.requiresDoubleQuotes = requiresDoubleQuotes;
		this.requiresNamespace = requiresNamespace;
	}
}
