// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.compound;

import net.minecraft.resources.ResourceLocation;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.NbtTagManager;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.parser.ParsedCompound;
import org.jetbrains.annotations.Nullable;

public class BrainType extends ComplexCompoundType
{
	private final @Nullable ResourceLocation entityId;

	public BrainType(@Nullable String entityId)
	{
		this.entityId = entityId != null ? ResourceLocation.tryParse(entityId) : null;
	}

	@Override protected void getBasicCompoundSuggestions(@Nullable ParsedCompound parsed, NbtTagMap map)
	{
		if (entityId == null || !entityId.getNamespace().equals("minecraft")) { return; }
		map.addAll(NbtTagManager.get("_entity/minecraft:_brain/" + entityId.getPath()));
	}
}
