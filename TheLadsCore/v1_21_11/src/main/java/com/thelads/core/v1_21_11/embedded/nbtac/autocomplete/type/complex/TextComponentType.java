// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.complex;

import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.NbtTagMap;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.parser.ParsedList;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.parser.ParsedPrimitive;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.compound.TextCompoundType;
import com.thelads.core.v1_21_11.embedded.nbtac.config.ModConfig;
import org.jetbrains.annotations.Nullable;

public class TextComponentType extends ComplexType
{
	public static final TextComponentType INSTANCE = new TextComponentType();

	private TextComponentType()
	{
		super(PrimitiveType.COMPOUND);
	}

	@Override public @Nullable SuggestionList getSuggestions(SuggestionListContext ctx)
	{
		if (ctx.getRemaining().isEmpty())
		{
			// it will call getBasicSuggestions()
			return super.getSuggestions(ctx);
		}

		return switch (ctx.parsed())
		{
			case ParsedCompound ignore -> TextCompoundType.INSTANCE.getSuggestions(ctx);
			case ParsedList ignore -> TextCompoundType.LIST_INSTANCE.getSuggestions(ctx);
			case ParsedPrimitive ignore -> PrimitiveType.STRING.getSuggestions(ctx);
			default -> SuggestionList.empty();
		};
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		list.addRaw(ModConfig.defaultQuotationMark.val.getStr(false), "(simple string) [#text_component]", 3);
		list.addRaw("{", "(text component) [#text_component]", 2);
		list.addRaw("[", "(test component list) [#text_component]", 1);
	}

	@Override public @Nullable NbtTagMap getSuggestionsTagMap(ParsedCompound parsed)
	{
		return TextCompoundType.INSTANCE.getSuggestionsTagMap(parsed);
	}
}
