// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.complex;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.SuggestionList;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.suggestions.IdSuggestion;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.type.PrimitiveType;
import com.thelads.core.v26_2.embedded.nbtac.mixin.fields.AtlasEntryFields;
import com.thelads.core.v26_2.embedded.nbtac.mixin.fields.AtlasManagerFields;
import com.thelads.core.v26_2.embedded.nbtac.mixin.fields.TextureAtlasFields;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class SpriteType extends ComplexType
{
	private final @Nullable Identifier atlasId;

	public SpriteType(String atlasId)
	{
		super(PrimitiveType.STRING);
		this.atlasId = atlasId != null ? Identifier.tryParse(atlasId) : null;
	}

	@Override public void getBasicSuggestions(SuggestionListContext ctx, SuggestionList list)
	{
		if (atlasId == null) { return; }

		Map<Identifier, AtlasEntryFields> atlasById = ((AtlasManagerFields)Minecraft.getInstance().getAtlasManager()).nbtac$getAtlasById();
		AtlasEntryFields atlasEntry = atlasById.get(atlasId);
		if (atlasEntry == null) { return; }

		Collection<Identifier> sprites = ((TextureAtlasFields)atlasEntry.nbtac$getAtlas()).nbtac$getTexturesByName().keySet();
		sprites.forEach((id) -> list.add(new IdSuggestion(id, "[#sprite]", ctx.parserType())));
	}
}
