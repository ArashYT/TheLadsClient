// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.string;

import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.parser.CustomTagParser;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.parser.ParsedPrimitive;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.ListType;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.Type;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.complex.TextComponentType;
import com.thelads.core.v1_21_1.embedded.nbtac.config.ModConfig;

public class JsonTextComponentType extends ComplexStringType
{
	public static final JsonTextComponentType INSTANCE = new JsonTextComponentType();

	@Override public int getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		String str = null;
		char strQuote = '\0';
		if (ctx.parsed() instanceof ParsedPrimitive parsed)
		{
			str = parsed.val;
			strQuote = parsed.quoteType;
			if (parsed.closed) { return -1; } // getSuggestions should return null
		}
		if (str == null) { str = ""; }

		Type type = null;
		if (!str.isEmpty() && strQuote == '\'')
		{
			if (str.charAt(0) == '{') { type = TextComponentType.INSTANCE; }
			else if (str.charAt(0) == '[') { type = new ListType(TextComponentType.INSTANCE); }
		}

		if (type == null)
		{
			if (strQuote == '"' || !str.isEmpty()) { return 0; }

			String jsonSuggestion = ModConfig.jsonStringSuggestion.val.get();
			if (jsonSuggestion != null) { list.addRaw(jsonSuggestion, "(simple string) [#json_text]", 3); }
			list.addRaw("'{", "(json structure) [#json_text]", 2);
			list.addRaw("'[", "(json list) [#json_text]", 1);
			return 0;
		}
		else
		{
			CustomTagParser parser = CustomTagParser.forJson(str, type);
			SuggestionList suggestions = parser.parse();
			if (parser.wasSuccessful()) { return -2; } // return ctx.expectedOperators(), which is "'"

			suggestions.forEach(list::add);
			return suggestions.cursor + 1;
		}
	}
}
