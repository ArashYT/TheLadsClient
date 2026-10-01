// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete;

public enum DataSource
{
	SYNTHETIC(0), // priority shouldn't matter for them
	BUILTIN(1),
	API(2),
	USER_DEFINED(3);

	public final int priority;

	DataSource(int priority)
	{
		this.priority = priority;
	}
}
