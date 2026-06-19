package com.thelads.core.features.auto.modernadvancements.data.toast;

public enum ToastSize {
   SMALL(160, 32),
   MEDIUM(220, 48),
   LARGE(300, 64);

   public final int width;
   public final int height;

   private ToastSize(int width, int height) {
      this.width = width;
      this.height = height;
   }

   public String label() {
      return switch (this) {
         case SMALL -> "Small";
         case MEDIUM -> "Medium";
         case LARGE -> "Large";
      };
   }
}
