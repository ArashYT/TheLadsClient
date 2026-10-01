// Adapted from NBT Autocomplete 2.1 for Minecraft 26.2 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.suggestions.IdSuggestion;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;
import org.jetbrains.annotations.Nullable;

public class RegistryKeyType extends ComplexType
{
	private final @Nullable ResourceKey<Registry<Object>> registryKey;
	private final Contents contents;

	public RegistryKeyType(@Nullable String arg)
	{
		this(arg, Contents.KEYS);
	}

	public RegistryKeyType(@Nullable String arg, Contents contents)
	{
		super(PrimitiveType.STRING);
		Identifier id = arg != null ? Identifier.parse(arg) : null;
		this.registryKey = id != null ? ResourceKey.createRegistryKey(id) : null;
		this.contents = contents;
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		if (registryKey == null) { return; }
		String subtext = "[#" + registryKey.identifier().getPath() + "]";

		if (Minecraft.getInstance().level == null) { return; }
		Registry<?> registry = Minecraft.getInstance().level.registryAccess().lookup(registryKey).orElse(null);
		if (registry == null) { return; }

		if (contents.includeKeys)
		{
			registry.keySet().forEach((id) -> list.add(new IdSuggestion(id, subtext, ctx.parserType(), 0, contents.keysAsTags)));
		}
		if (contents.includeTags)
		{
			registry.getTags().forEach((t) -> list.add(new IdSuggestion(t.key().location(), subtext, ctx.parserType(), 0, true)));
		}
	}

	public enum Contents
	{
		KEYS(true, false, false),
		TAGS(false, true, false),
		BOTH(true, true, false),
		BOTH_PREFIXED(true, true, true);

		public final boolean includeKeys;
		public final boolean includeTags;
		public final boolean keysAsTags;

		Contents(boolean includeKeys, boolean includeTags, boolean keysAsTags)
		{
			this.includeKeys = includeKeys;
			this.includeTags = includeTags;
			this.keysAsTags = keysAsTags;
		}
	}
}
