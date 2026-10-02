// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.compound;

import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedCompound;
import org.jetbrains.annotations.Nullable;

public abstract class ComplexCompoundType extends AbstractCompoundType
{
	@Override public @Nullable NbtTagMap getSuggestionsTagMap(@Nullable ParsedCompound parsed)
	{
		NbtTagMap tagMap = new NbtTagMap();
		getBasicCompoundSuggestions(parsed, tagMap);
		return tagMap;
	}

	protected abstract void getBasicCompoundSuggestions(@Nullable ParsedCompound parsed, NbtTagMap map);
}
