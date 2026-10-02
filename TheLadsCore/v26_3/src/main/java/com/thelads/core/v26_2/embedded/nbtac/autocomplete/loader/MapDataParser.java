// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.loader;

import net.minecraft.resources.Identifier;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagManager;

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
			String val = "block/" + Identifier.parse(entry.header);
			entry.lines.forEach((l) -> NbtTagManager.blockToBlockEntityMap.put(parseLine(l), val));
		}
	}

	private static Identifier parseLine(String line)
	{
		return Identifier.parse(line.substring(1)); // substring(1) - remove '+' sign
	}
}
