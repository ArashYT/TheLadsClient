// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.suggestions;

import net.minecraft.resources.Identifier;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParserType;
import org.jetbrains.annotations.Nullable;

public class IdSuggestion extends StringSuggestion
{
	public IdSuggestion(@Nullable Identifier id, @Nullable String subtext, ParserType parserType)
	{
		this(id, subtext, parserType, 0, false);
	}

	public IdSuggestion(@Nullable Identifier id, @Nullable String subtext, ParserType parserType, int priority, boolean isTagId)
	{
		super((isTagId ? "#" : "") + (id != null ? id.toString() : "error"), subtext,
				parserType, parserType.requiresNamespace ? StringType.FULL_ID : StringType.ID, priority);

		if (id != null)
		{
			matching.add(id.toString());
			if (isTagId) { matching.add("#" + id); }

			if (id.getNamespace().equals("minecraft"))
			{
				matching.add(id.getPath());
				if (isTagId) { matching.add("#" + id.getPath()); }
			}
		}
	}
}
