// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac.api;

import java.util.List;

/**
 * List of suggestions to show at a given cursor position
 * @param suggestions mutable list of suggestions
 * @param cursor position of suggestions cursor
 */
public record NBTacSuggestionList(List<NBTacSuggestion> suggestions, int cursor) {}
