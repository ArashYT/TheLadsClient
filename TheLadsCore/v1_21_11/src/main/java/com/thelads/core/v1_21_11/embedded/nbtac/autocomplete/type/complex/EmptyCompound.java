// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.complex;

import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.PrimitiveType;

public class EmptyCompound extends ComplexType
{
	public static final EmptyCompound INSTANCE = new EmptyCompound();

	private EmptyCompound()
	{
		super(PrimitiveType.COMPOUND);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		list.addRaw("{}", "[#empty_compound]");
	}
}
