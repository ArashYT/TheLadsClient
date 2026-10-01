// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.tag;

import net.minecraft.resources.ResourceLocation;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.type.Type;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class GeneratedNbtTag extends NbtTag
{
	private final int priority;
	private final @Nullable ResourceLocation nameAsId;
	private Function<String, String> subtext = Function.identity();

	public GeneratedNbtTag(String name, Type type)
	{
		this(name, type, 0, null);
	}

	public GeneratedNbtTag(NbtTag toCopy, int priority, @Nullable ResourceLocation nameAsId)
	{
		this(toCopy.getName(), toCopy.getType(), priority, nameAsId);
	}

	public GeneratedNbtTag(String name, Type type, int priority, @Nullable ResourceLocation nameAsId)
	{
		super(name, type);
		this.priority = priority;
		this.nameAsId = nameAsId;
	}

	public GeneratedNbtTag withSubtext(Function<String, String> subtext)
	{
		this.subtext = subtext;
		return this;
	}

	@Override public String getSubtext()
	{
		return subtext.apply(super.getSubtext());
	}

	@Override public int getPriority(@Nullable ParsedCompound compound)
	{
		return priority;
	}

	@Override public @Nullable ResourceLocation getNameAsId()
	{
		return nameAsId;
	}
}
