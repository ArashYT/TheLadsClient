// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.compound;

import net.minecraft.resources.Identifier;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagMap;
import org.jetbrains.annotations.Nullable;

public class EntityCompoundType extends CompoundType
{
	public final @Nullable Identifier entityId; // or block entity

	public EntityCompoundType(@Nullable NbtTagMap tagMap, String entityId)
	{
		super(tagMap);
		this.entityId = Identifier.tryParse(entityId);
	}
}
