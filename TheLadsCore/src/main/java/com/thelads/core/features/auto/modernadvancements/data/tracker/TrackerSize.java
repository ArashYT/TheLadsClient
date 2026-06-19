package com.thelads.core.features.auto.modernadvancements.data.tracker;

public enum TrackerSize {
   SMALL(140, 200),
   MEDIUM(200, 300),
   LARGE(260, 400);

   public final int width;
   public final int maxHeight;

   private TrackerSize(int width, int maxHeight) {
      this.width = width;
      this.maxHeight = maxHeight;
   }
}
