// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.config.enums;

public enum DefaultQuotationMark
{
	SINGLE,
	DOUBLE;

	public char getChar(boolean isRawJson)
	{
		return (this == SINGLE && !isRawJson) ? '\'' : '"';
	}

	public String getStr(boolean isRawJson)
	{
		return (this == SINGLE && !isRawJson) ? "'" : "\"";
	}
}
