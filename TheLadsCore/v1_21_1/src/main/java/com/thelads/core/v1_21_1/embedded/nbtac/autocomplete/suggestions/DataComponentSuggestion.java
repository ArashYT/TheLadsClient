// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.suggestions;

import net.minecraft.resources.ResourceLocation;
import com.thelads.core.v1_21_1.embedded.nbtac.autocomplete.tag.NbtTag;
import com.thelads.core.v1_21_1.embedded.nbtac.config.ModConfig;
import org.jetbrains.annotations.Nullable;

public class DataComponentSuggestion extends CustomSuggestion
{
	private final String withNamespace;
	private final @Nullable String withoutNamespace;

	public DataComponentSuggestion(NbtTag tag, boolean addEqualSign)
	{
		super(tag.getSubtext(), tag.getPriority(null));

		ResourceLocation id = tag.getNameAsId();
		if (id == null) { id = ResourceLocation.parse("error:error");  }

		this.withNamespace = id + (addEqualSign ? "=" : "");
		this.withoutNamespace = id.getNamespace().equals("minecraft") ? (id.getPath() + (addEqualSign ? "=" : "")) : null;
	}

	@Override public String getText()
	{
		return (ModConfig.hideMcNamespaceInComponents.val && withoutNamespace != null) ? withoutNamespace : withNamespace;
	}

	@Override public boolean match(String str)
	{
		return str.equals(withNamespace) || str.equals(withoutNamespace);
	}

	@Override public boolean matchPrefix(String prefix)
	{
		return matchPrefix(getText(), prefix) || (withoutNamespace != null && matchPrefix(withoutNamespace, prefix));
	}
}
