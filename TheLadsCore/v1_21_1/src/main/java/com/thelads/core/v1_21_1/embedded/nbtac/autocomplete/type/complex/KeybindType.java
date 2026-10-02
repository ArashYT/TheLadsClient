// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.suggestions.StringSuggestion;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v1_21_1.embedded.nbtac.mixin.fields.KeyMappingFields;

import java.util.Map;

public class KeybindType extends ComplexType
{
	//TODO: do something about suggestion list going out of the screen
	public static final KeybindType INSTANCE = new KeybindType();

	private KeybindType()
	{
		super(PrimitiveType.STRING);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		Map<String, KeyMapping> keyMap = KeyMappingFields.nbtac$getALL();
		if (keyMap == null) { return; }

		for (String str : keyMap.keySet())
		{
			String subtext = "\"" + Component.translatable(str).getString() + "\" [#keybind]";
			list.add(new StringSuggestion(str, subtext, ctx.parserType()));
		}
	}
}
