// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.suggestions;

import net.minecraft.resources.Identifier;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.parser.ParserType;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.tag.NbtTag;
import org.jetbrains.annotations.Nullable;

public class IdTagSuggestion extends TagSuggestion
{
	public IdTagSuggestion(NbtTag tag, @Nullable Identifier id, ParserType parserType, int priority)
	{
		super(tag, id != null ? id.toString() : "error", parserType, StringType.ID_TAG, priority);

		if (id != null && id.getNamespace().equals("minecraft"))
		{
			matching.add(withHiddenNamespace ? id.toString() : id.getPath());
		}
	}
}
