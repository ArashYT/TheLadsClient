// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.tag;

import com.mojang.brigadier.Message;
import net.minecraft.resources.Identifier;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.Type;
import com.thelads.core.v26_2.embedded.nbtac.utils.ComparableLiteralMessage;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public abstract class NbtTag
{
	private final String name;
	private final Type type;

	public NbtTag(String name, Type type)
	{
		this.name = name;
		this.type = type;
	}

	public String getName()
	{
		return name;
	}

	public Type getType()
	{
		return type;
	}

	public String getSubtext()
	{
		return type.getSubtext();
	}

	public Message getTooltip()
	{
		return new ComparableLiteralMessage(String.format(Locale.ROOT, "%s§r §8%s", name, getSubtext()));
	}

	public abstract int getPriority(@Nullable ParsedCompound compound);

	public abstract @Nullable Identifier getNameAsId();
}
