// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.compound;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantments;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.tag.GeneratedNbtTag;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v1_21_11.embedded.nbtac.utils.Fields;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EnchantmentsType extends ComplexCompoundType
{
	public static final EnchantmentsType INSTANCE = new EnchantmentsType();

	@Override public void getBasicCompoundSuggestions(@Nullable ParsedCompound parsed, NbtTagMap map)
	{
		List<ResourceKey> enchantments = Fields.getStaticFields(Enchantments.class, ResourceKey.class);
		for (ResourceKey<?> resourceKey : enchantments)
		{
			Identifier id = resourceKey.identifier();
			map.add(new GeneratedNbtTag(id.toString(), PrimitiveType.INT, 0, id));
		}
	}
}
