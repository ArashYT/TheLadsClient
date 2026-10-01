// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.suggestions;

import com.mojang.brigadier.Message;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.parser.ParserType;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.tag.NbtTag;
import org.jetbrains.annotations.Nullable;

public class TagSuggestion extends StringSuggestion
{
	private final Message tooltip;

	protected TagSuggestion(NbtTag tag, String tagName, ParserType parserType, StringType stringType, int priority)
	{
		super(tagName, tag.getSubtext(), parserType, stringType, priority);
		this.tooltip = tag.getTooltip();
	}

	public static TagSuggestion create(NbtTag tag, ParserType parserType, @Nullable ParsedCompound compound)
	{
		return tag.getNameAsId() != null
				? new IdTagSuggestion(tag, tag.getNameAsId(), parserType, tag.getPriority(compound))
				: new TagSuggestion(tag, tag.getName(), parserType, StringType.TAG, tag.getPriority(compound));
	}

	@Override public @Nullable Message getTooltip()
	{
		return tooltip;
	}
}
