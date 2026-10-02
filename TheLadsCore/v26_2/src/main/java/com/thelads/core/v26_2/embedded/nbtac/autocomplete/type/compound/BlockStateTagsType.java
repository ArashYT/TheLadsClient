// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.compound;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.properties.Property;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.tag.GeneratedNbtTag;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex.EnumType;
import com.thelads.core.v26_2.embedded.nbtac.utils.RegistryUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BlockStateTagsType extends ComplexCompoundType
{
	private final @Nullable Identifier id;

	public BlockStateTagsType(@Nullable String id)
	{
		if (id != null)
		{
			if (id.startsWith("block/")) { id = id.substring(6); }
			else if (id.startsWith("item/")) { id = id.substring(5); }
		}
		this.id = id != null ? Identifier.tryParse(id) : null;
	}

	@Override protected void getBasicCompoundSuggestions(@Nullable ParsedCompound parsed, NbtTagMap map)
	{
		if (id == null) { return; }

		Item blockItem = RegistryUtils.ITEM.get(id);
		if (!(blockItem instanceof BlockItem)) { return; }

		for (Property<?> property : ((BlockItem)blockItem).getBlock().defaultBlockState().getProperties())
		{
			EnumType type = new EnumType(List.of(PrimitiveType.STRING), buildEnumList(property), false);
			map.add(new GeneratedNbtTag(property.getName(), type));
		}
	}

	private static <T extends Comparable<T>> List<String> buildEnumList(Property<?> property)
	{
		List<String> list = new ArrayList<>();
		for (T possibleValue : ((Property<T>)property).getPossibleValues())
		{
			list.add(((Property<T>)property).getName(possibleValue));
		}
		return list;
	}
}
