// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.network.chat.TextColor;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.suggestions.StringSuggestion;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v1_21_11.embedded.nbtac.mixin.fields.TextColorFields;

import java.util.Locale;
import java.util.Map;

public class TextColorType extends ComplexType
{
	public static final TextColorType INSTANCE = new TextColorType();

	private TextColorType()
	{
		super(PrimitiveType.STRING);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		Map<String, TextColor> colorMap = TextColorFields.getNAMED_COLORS();
		if (colorMap == null) { return; }

		for (Map.Entry<String, TextColor> entry : colorMap.entrySet())
		{
			String subtext = String.format(Locale.ROOT, "(#%06X) [#text_color]", entry.getValue().getValue());
			list.add(new StringSuggestion(entry.getKey(), subtext, ctx.parserType()));
		}
	}
}
