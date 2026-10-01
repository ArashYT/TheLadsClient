// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.complex;

import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_11.embedded.nbtac.autocomplete.type.Type;

import java.util.Locale;

public class PlayerInventorySlotType extends ComplexType
{
	public PlayerInventorySlotType(Type type)
	{
		super(type.getPrimitive());
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		for (int i = 0; i < 9; i++)
		{
			String subtext = String.format(Locale.ROOT, "(Hotbar %d) [#inventory_slot]", i + 1);
			list.addRaw(i + primitive.suffix, subtext);
		}

		for (int i = 9; i < 35; i++)
		{
			int row = ((i - 9) / 9) + 1;
			int column = ((i - 9) % 9) + 1;
			String subtext = String.format(Locale.ROOT, "(Storage %d:%d) [#inventory_slot]", row, column);
			list.addRaw(i + primitive.suffix, subtext);
		}

		list.addRaw("100" + primitive.suffix, "(Feet) [#inventory_slot]");
		list.addRaw("101" + primitive.suffix, "(Legs) [#inventory_slot]");
		list.addRaw("102" + primitive.suffix, "(Chest) [#inventory_slot]");
		list.addRaw("103" + primitive.suffix, "(Head) [#inventory_slot]");
		list.addRaw("-106" + primitive.suffix, "(Off-hand) [#inventory_slot]");
	}
}
