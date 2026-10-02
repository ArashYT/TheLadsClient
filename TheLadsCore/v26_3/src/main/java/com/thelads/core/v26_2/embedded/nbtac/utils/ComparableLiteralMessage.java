// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.utils;

import com.mojang.brigadier.LiteralMessage;

public class ComparableLiteralMessage extends LiteralMessage
{
	public ComparableLiteralMessage(String string)
	{
		super(string);
	}

	@Override public boolean equals(Object obj)
	{
		if (this == obj) { return true; }
		if (!(obj instanceof ComparableLiteralMessage)) { return false; }
		return getString().equals(((ComparableLiteralMessage)obj).getString());
	}

	@Override public int hashCode()
	{
		return getString().hashCode();
	}
}
