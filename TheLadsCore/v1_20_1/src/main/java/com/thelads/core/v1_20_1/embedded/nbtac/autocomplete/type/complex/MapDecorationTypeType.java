// Adapted from NBT Autocomplete 2.1 for Minecraft 1.20.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.world.level.saveddata.maps.MapDecoration;
import com.thelads.core.v1_20_1.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_20_1.embedded.nbtac.autocomplete.suggestions.StringSuggestion;
import com.thelads.core.v1_20_1.embedded.nbtac.autocomplete.type.PrimitiveType;

import java.util.Locale;

public class MapDecorationTypeType extends ComplexType
{
	public static final MapDecorationTypeType INSTANCE = new MapDecorationTypeType();

	private MapDecorationTypeType()
	{
		super(PrimitiveType.STRING);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		for (MapDecoration.Type type : MapDecoration.Type.values())
		{
			list.add(new StringSuggestion(type.name().toLowerCase(Locale.ROOT), "[#map_decoration_type]", ctx.parserType()));
		}
	}
}
