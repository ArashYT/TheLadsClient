// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.config.enums;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import com.thelads.core.v26_2.embedded.nbtac.utils.RegistryUtils;
import org.jetbrains.annotations.Nullable;

public enum UnknownItemComponents
{
	RELEVANT_BY_DEFAULT,
	RELEVANT_WITHIN_NAMESPACE,
	IRRELEVANT_BY_DEFAULT;

	public int getPriority(Identifier componentId, @Nullable Item item)
	{
		switch (this)
		{
			case RELEVANT_BY_DEFAULT:
				return 0;

			case IRRELEVANT_BY_DEFAULT:
				return -1;

			case RELEVANT_WITHIN_NAMESPACE:
				Identifier itemId = item != null ? RegistryUtils.ITEM.getKey(item) : null;
				return (itemId != null && componentId.getNamespace().equals(itemId.getNamespace())) ? 0 : -1;

			default:
				throw new IllegalStateException("Unexpected value: " + this);
		}
	}
}
