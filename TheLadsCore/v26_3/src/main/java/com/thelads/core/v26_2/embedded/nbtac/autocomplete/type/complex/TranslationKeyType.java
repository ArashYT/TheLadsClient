// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.suggestions.StringSuggestion;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v26_2.embedded.nbtac.mixin.fields.ClientLanguageFields;

public class TranslationKeyType extends ComplexType
{
	public static final TranslationKeyType INSTANCE = new TranslationKeyType();

	public TranslationKeyType()
	{
		super(PrimitiveType.STRING);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		Language lang = Language.getInstance();
		if (lang instanceof ClientLanguage)
		{
			for (String key : ((ClientLanguageFields)lang).nbtac$getStorage().keySet())
			{
				list.add(new StringSuggestion(key, "[#translation_key]", ctx.parserType()));
			}
		}
	}
}
