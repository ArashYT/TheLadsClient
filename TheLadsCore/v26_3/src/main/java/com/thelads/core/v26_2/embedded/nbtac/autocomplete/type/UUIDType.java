// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type;

import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public class UUIDType implements Type
{
	public static final UUIDType RANDOM = new UUIDType(true);
	public static final UUIDType UNKNOWN = new UUIDType(false);
	private final boolean random;

	private UUIDType(boolean random)
	{
		this.random = random;
	}

	@Override public @Nullable SuggestionList getSuggestions(SuggestionListContext ctx)
	{
		if (!random)
		{
			return ArrayType.INT.getSuggestions(ctx);
 		}
		else
		{
			UUID randomUUID = UUID.randomUUID();
			int uuidInt0 = (int)randomUUID.getLeastSignificantBits();
			int uuidInt1 = (int)(randomUUID.getLeastSignificantBits() >>> 32);
			int uuidInt2 = (int)randomUUID.getMostSignificantBits();
			int uuidInt3 = (int)(randomUUID.getMostSignificantBits() >>> 32);

			String uuidString = String.format(Locale.ROOT,
					"[I;%d, %d, %d, %d]", uuidInt3, uuidInt2, uuidInt1, uuidInt0);

			SuggestionList list = new SuggestionList(ctx.parsed().pos);
			list.addRaw(uuidString, "[#random_uuid]");
			return list;
		}
	}

	@Override public String getSubtext()
	{
		return "[uuid]";
	}

	@Override public PrimitiveType getPrimitive()
	{
		return PrimitiveType.INT_ARRAY;
	}
}
