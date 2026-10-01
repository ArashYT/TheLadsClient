// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.compound;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagManager;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v26_2.embedded.nbtac.utils.RegistryUtils;
import org.jetbrains.annotations.Nullable;

public class SpawnEggType extends ComplexCompoundType
{
	private final @Nullable Identifier id;

	public SpawnEggType(@Nullable String id)
	{
		if (id != null && id.startsWith("item/")) { id = id.substring(5); }
		this.id = id != null ? Identifier.tryParse(id) : null;
	}

	@Override protected void getBasicCompoundSuggestions(@Nullable ParsedCompound parsed, NbtTagMap map)
	{
		if (id == null) { return; }
		Item item = RegistryUtils.ITEM.get(id);

		try
		{
			if (item instanceof SpawnEggItem)
			{
				String key = RegistryUtils.ENTITY_TYPE.getKey(((SpawnEggItem)item).getType(new ItemStack(item))).toString();
				NbtTagMap spawnEggTags = NbtTagManager.get("entity/" + key);
				map.addAll(spawnEggTags);
			}
		}
		catch (Exception ignore) {}
	}
}
