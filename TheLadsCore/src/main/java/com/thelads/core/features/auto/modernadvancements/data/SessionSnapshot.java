package com.thelads.core.features.auto.modernadvancements.data;

import java.util.Map;
import net.minecraft.resources.Identifier;

public record SessionSnapshot(
   Identifier tabId, Identifier advancementId, int scrollX, int scrollY, float zoom, boolean detailOpen, Map<Identifier, int[]> tabScrolls, String searchQuery
) {
}
