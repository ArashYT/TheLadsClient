// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.suggestions;

import org.jetbrains.annotations.Nullable;

public class RawSuggestion extends CustomSuggestion
{
	public final String text;

	public RawSuggestion(String text, @Nullable String subtext)
	{
		this(text, subtext, 0);
	}

	public RawSuggestion(String text, @Nullable String subtext, int priority)
	{
		super(subtext, priority);
		this.text = text;
	}

	@Override public String getText()
	{
		return text;
	}

	@Override public boolean match(String str)
	{
		return text.equals(str);
	}

	@Override public boolean matchPrefix(String prefix)
	{
		return matchPrefix(text, prefix);
	}
}
