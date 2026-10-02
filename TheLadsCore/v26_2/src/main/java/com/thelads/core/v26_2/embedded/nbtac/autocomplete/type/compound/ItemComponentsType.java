// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.compound;

import net.minecraft.resources.Identifier;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.DataComponentManager;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v26_2.embedded.nbtac.utils.RegistryUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ItemComponentsType extends ComplexCompoundType
{
	private final @Nullable Identifier id;

	public ItemComponentsType(@Nullable String id)
	{
		this.id = id != null ? Identifier.tryParse(id) : null;
	}

	@Override protected void getBasicCompoundSuggestions(@Nullable ParsedCompound parsed, NbtTagMap map)
	{
		DataComponentManager.loadTagMap(map, "", Set.of(), id != null ? RegistryUtils.ITEM.get(id) : null);
	}
}
