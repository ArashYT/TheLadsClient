// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.compound;

import net.minecraft.resources.ResourceLocation;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.DataComponentManager;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v1_21_1.embedded.nbtac.utils.RegistryUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ItemComponentsType extends ComplexCompoundType
{
	private final @Nullable ResourceLocation id;

	public ItemComponentsType(@Nullable String id)
	{
		this.id = id != null ? ResourceLocation.tryParse(id) : null;
	}

	@Override protected void getBasicCompoundSuggestions(@Nullable ParsedCompound parsed, NbtTagMap map)
	{
		DataComponentManager.loadTagMap(map, "", Set.of(), id != null ? RegistryUtils.ITEM.get(id) : null);
	}
}
