// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type;

import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.parser.ParsedArray;
import org.jetbrains.annotations.Nullable;

public class ArrayType implements Type
{
	public static final ArrayType BYTE = new ArrayType(PrimitiveType.BYTE_ARRAY, PrimitiveType.BYTE, 'B');
	public static final ArrayType INT = new ArrayType(PrimitiveType.INT_ARRAY, PrimitiveType.INT, 'I');
	public static final ArrayType LONG = new ArrayType(PrimitiveType.LONG_ARRAY, PrimitiveType.LONG, 'L');
	public static final ArrayType BLOCK_POS = INT;
	private final PrimitiveType primitive;
	private final PrimitiveType elementType;
	private final String opening;

	private ArrayType(PrimitiveType primitive, PrimitiveType elementType, char typeSymbol)
	{
		this.primitive = primitive;
		this.elementType = elementType;
		this.opening = "[" + typeSymbol + ";";
	}

	@Override public @Nullable SuggestionList getSuggestions(SuggestionListContext ctx)
	{
		if (!(ctx.parsed() instanceof ParsedArray parsed) || parsed.isEmpty())
		{
			// ctx.expectedOperators() could be null
			return new SuggestionList(ctx.parsed().pos).withOperators(opening);
		}
		if (parsed.isClosed()) { return null; }

		// e.g. [I;123
		SuggestionList list = elementType.getSuggestions(ctx.child(parsed.getLast()));
		return list != null ? list : new SuggestionList(ctx.reader().getCursor()).withOperators(",", "]");
	}

	@Override public PrimitiveType getPrimitive()
	{
		return primitive;
	}
}
