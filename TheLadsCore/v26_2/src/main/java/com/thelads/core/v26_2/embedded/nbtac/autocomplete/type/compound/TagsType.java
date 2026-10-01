// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.compound;

import net.minecraft.resources.Identifier;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagManager;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.tag.GeneratedNbtTag;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.Type;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex.IdType;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex.RegistryKeyType;
import org.jetbrains.annotations.Nullable;

public class TagsType extends ComplexCompoundType
{
	private final @Nullable String id;
	private final @Nullable String keyId;
	private final String idKey;

	public TagsType(@Nullable String id, @Nullable String keyId, @Nullable String idKey)
	{
		if (id != null)
		{
			id = id.replace("block/item/", "block/");
			id = id.replace("entity/item/", "entity/");
		}
		this.id = id;
		this.keyId = keyId;
		this.idKey = idKey;
	}

	@Override protected void getBasicCompoundSuggestions(@Nullable ParsedCompound parsed, NbtTagMap map)
	{
		if (idKey != null)
		{
			Type type = null;
			if (keyId != null)
			{
				type = new RegistryKeyType(keyId);
			}
			else if (id != null)
			{
				String finalId = id;
				if (id.startsWith("block/"))
				{
					Identifier requiredId = Identifier.tryParse(id.substring(id.indexOf('/') + 1));
					if (requiredId != null) { finalId = NbtTagManager.blockToBlockEntityMap.getOrDefault(requiredId, id); }
				}
				type = new IdType(finalId.substring(id.indexOf('/') + 1));
			}

			map.add(new GeneratedNbtTag(idKey, type, 200, null));
		}

		if (id == null) { return; }
		NbtTagMap tagMap = NbtTagManager.get(idWithNamespace(id));
		map.addAll(tagMap);
	}

	private static String idWithNamespace(String idStr)
	{
		int slashPos = idStr.indexOf('/');
		if (slashPos == -1) { return idStr; } // shouldn't happen

		String prefix = idStr.substring(0, slashPos + 1);
		Identifier id = Identifier.tryParse(idStr.substring(slashPos + 1));
		if (id == null) { return idStr; }

		return prefix + id;
	}
}
