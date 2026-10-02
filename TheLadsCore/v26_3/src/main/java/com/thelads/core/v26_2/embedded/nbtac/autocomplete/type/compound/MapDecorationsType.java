// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.compound;

import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedTag;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.tag.GeneratedNbtTag;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.Type;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public class MapDecorationsType extends ComplexCompoundType
{
	public static final MapDecorationsType INSTANCE = new MapDecorationsType();
	private static final Random RNG = new Random();

	@Override public void getBasicCompoundSuggestions(@Nullable ParsedCompound parsed, NbtTagMap map)
	{
		if (parsed == null) { return; }

		// 36^6 > 2^31
		String newDecorationId = Long.toString(Math.abs(RNG.nextInt()), Math.min(Character.MAX_RADIX, 36));
		Type tagType = CompoundType.fromName("compound/nbtac:map_decoration");

		// There's issue with potential tag duplications but considering there are 2^31 variants
		// I don't think it's worth preventing.
		// It should be done this way (with random names) instead of for example tag1, tag2, tag3...
		// as changing decoration data without changing tag name won't change it.
		map.add(new GeneratedNbtTag(newDecorationId, tagType).withSubtext((s) -> "[#random_tag]"));

		for (ParsedTag tag : parsed.getAll())
		{
			if (tag.key != null) { map.add(new GeneratedNbtTag(tag.key, tagType).withSubtext((s) -> "[#random_tag]")); }
		}
	}
}
