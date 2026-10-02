// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.compound;

import net.minecraft.resources.ResourceLocation;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.NbtTagManager;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.parser.ParsedCompound;
import org.jetbrains.annotations.Nullable;

public class CompoundType extends AbstractCompoundType
{
	private @Nullable NbtTagMap tagMap = null;

	public static AbstractCompoundType fromName(@Nullable String name)
	{
		if (name == null) { return new CompoundType(null); }

		if (name.startsWith("storage/"))
		{
			return new StorageCompoundType(ResourceLocation.tryParse(name.substring(8)));
		}

		NbtTagMap tagMap = NbtTagManager.get(name);
		return (name.startsWith("entity/") || name.startsWith("block/"))
				? new EntityCompoundType(tagMap, name.substring(name.indexOf('/') + 1))
				: new CompoundType(tagMap);
	}

	public CompoundType() {}

	protected CompoundType(@Nullable NbtTagMap tagMap)
	{
		this.tagMap = tagMap;
	}

	@Override public NbtTagMap getMutableTagMap()
	{
		if (tagMap == null) { tagMap = new NbtTagMap(); }
		return tagMap;
	}

	@Override public void setTagMap(@Nullable NbtTagMap subcompound)
	{
		tagMap = subcompound;
	}

	@Override public @Nullable NbtTagMap getSuggestionsTagMap(ParsedCompound parsed)
	{
		return tagMap;
	}

	public boolean hasTagMap()
	{
		return tagMap != null;
	}
}
