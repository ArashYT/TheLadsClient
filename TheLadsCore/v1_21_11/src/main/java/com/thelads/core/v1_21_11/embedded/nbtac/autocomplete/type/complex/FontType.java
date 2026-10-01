// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.resources.Identifier;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.suggestions.IdSuggestion;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v1_21_11.embedded.nbtac.mixin.fields.FontManagerFields;
import com.thelads.core.v1_21_11.embedded.nbtac.mixin.fields.MinecraftFields;

public class FontType extends ComplexType
{
	public static final FontType INSTANCE = new FontType();

	private FontType()
	{
		super(PrimitiveType.STRING);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		FontManager fontManager = ((MinecraftFields)Minecraft.getInstance()).nbtac$getFontManager();
		for (Identifier id : ((FontManagerFields)fontManager).nbtac$getFontSets().keySet())
		{
			list.add(new IdSuggestion(id, "[#font]", ctx.parserType()));
		}
	}
}
