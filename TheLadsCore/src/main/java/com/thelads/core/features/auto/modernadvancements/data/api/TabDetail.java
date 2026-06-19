package com.thelads.core.features.auto.modernadvancements.data.api;

import java.util.List;
import net.minecraft.resources.Identifier;

public record TabDetail(Identifier tabRootId, String tabTitle, int total, List<Identifier> completedIds) {
}
