// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex;

import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;

import java.util.Random;

public class LongSeedType extends ComplexType
{
	public static final LongSeedType INSTANCE = new LongSeedType();
	private static final Random RNG = new Random();

	private LongSeedType()
	{
		super(PrimitiveType.LONG);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		long random = RNG.nextLong();
		if (random == 0) { random = 123; } // chance is about 1/10^19, but it isn't 0

		list.addRaw("0", "(random) [#seed]", 1); // when it's set to 0, game will use random number
		list.addRaw(random + "l", "(constant) [#seed]");
	}
}
