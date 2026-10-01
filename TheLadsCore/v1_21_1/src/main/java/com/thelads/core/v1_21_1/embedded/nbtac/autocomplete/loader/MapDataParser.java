// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.loader;

import net.minecraft.resources.ResourceLocation;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.NbtTagManager;

public class MapDataParser extends DataParser
{
	public MapDataParser(String filename, boolean dataAsFilename)
	{
		super(filename, dataAsFilename);
	}

	public void parseBlockToBlockEntityMap()
	{
		for (Entry entry : parseLines())
		{
			String val = "block/" + ResourceLocation.parse(entry.header);
			entry.lines.forEach((l) -> NbtTagManager.blockToBlockEntityMap.put(parseLine(l), val));
		}
	}

	private static ResourceLocation parseLine(String line)
	{
		return ResourceLocation.parse(line.substring(1)); // substring(1) - remove '+' sign
	}
}
