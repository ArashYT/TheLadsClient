package com.thelads.core.features.auto.modernadvancements.data.layout;

import java.util.LinkedHashMap;
import java.util.Map;

public class TabLayoutOverride {
   public Map<String, int[]> advancements = new LinkedHashMap<>();

   public boolean isEmpty() {
      return this.advancements == null || this.advancements.isEmpty();
   }
}
