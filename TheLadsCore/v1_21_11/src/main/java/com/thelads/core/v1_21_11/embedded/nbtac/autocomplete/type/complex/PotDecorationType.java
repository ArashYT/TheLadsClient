// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.DecoratedPotPattern;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.suggestions.IdSuggestion;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v1_21_11.embedded.nbtac.mixin.fields.DecoratedPotPatternsFields;
import com.thelads.core.v1_21_11.embedded.nbtac.utils.RegistryUtils;

import java.util.Map;

public class PotDecorationType extends ComplexType
{
	public static final PotDecorationType INSTANCE = new PotDecorationType();

	private PotDecorationType()
	{
		super(PrimitiveType.STRING);
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		list.add(new IdSuggestion(RegistryUtils.ITEM.getKey(Items.BRICK), "[#pot_decoration]", ctx.parserType(), 1, false));

		Map<Item, ResourceKey<DecoratedPotPattern>> itemToPotTexture = DecoratedPotPatternsFields.getITEM_TO_POT_TEXTURE();
		if (itemToPotTexture == null) { return; }

		for (Item item : itemToPotTexture.keySet())
		{
			list.add(new IdSuggestion(RegistryUtils.ITEM.getKey(item), "[#pot_decoration]", ctx.parserType()));
		}
	}
}
