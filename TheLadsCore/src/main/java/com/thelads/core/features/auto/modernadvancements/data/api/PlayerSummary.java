package com.thelads.core.features.auto.modernadvancements.data.api;

import java.util.UUID;

public record PlayerSummary(UUID uuid, String name, int completed, int total) {
   public float percentage() {
      return this.total > 0 ? (float)this.completed / this.total : 0.0F;
   }
}
