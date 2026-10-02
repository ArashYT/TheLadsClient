// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.config.enums;

import com.thelads.core.v1_21_1.embedded.nbtac.config.ModConfig;
import org.jetbrains.annotations.Nullable;

public enum JsonStringSuggestion
{
	NONE,
	RECOMMENDED,
	DEFAULT_FOR_STRINGS,
	LEGACY_SEPARATED,
	LEGACY_MERGED,
	LEGACY_BACKSLASH;

	public @Nullable String get()
	{
		return switch (this)
		{
			case NONE -> null;
			case RECOMMENDED, DEFAULT_FOR_STRINGS -> ModConfig.defaultQuotationMark.val.getStr(false);
			case LEGACY_SEPARATED -> "' \"";
			case LEGACY_MERGED -> "'\"";
			case LEGACY_BACKSLASH -> "\"\\\"";
		};
	}
}
