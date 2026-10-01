// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.suggestions.IdSuggestion;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v1_21_1.embedded.nbtac.utils.Fields;

import java.util.List;

public class MapDecorationTypeType extends ComplexType
{
	public static final MapDecorationTypeType INSTANCE = new MapDecorationTypeType();

	private MapDecorationTypeType()
	{
		super(PrimitiveType.STRING);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		List<Holder> decorationTypes = Fields.getStaticFields(MapDecorationTypes.class, Holder.class);
		for (Holder<MapDecorationType> holder : decorationTypes)
		{
			ResourceKey<?> key = holder.unwrapKey().orElse(null);
			if (key != null) { list.add(new IdSuggestion(key.location(), "[#map_decoration_type]", ctx.parserType())); }
		}
	}
}
