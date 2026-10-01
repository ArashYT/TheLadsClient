// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.suggestions.IdSuggestion;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v26_2.embedded.nbtac.mixin.fields.DecoratedPotRendererFields;
import com.thelads.core.v26_2.embedded.nbtac.utils.RegistryUtils;

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

		Map<ResourceKey<Item>, SpriteId> itemToPotTexture = DecoratedPotRendererFields.getDECORATED_POT_SPRITES();
		if (itemToPotTexture == null) { return; }

		for (ResourceKey<Item> item : itemToPotTexture.keySet())
		{
			list.add(new IdSuggestion(item.identifier(), "[#pot_decoration]", ctx.parserType()));
		}
	}
}
