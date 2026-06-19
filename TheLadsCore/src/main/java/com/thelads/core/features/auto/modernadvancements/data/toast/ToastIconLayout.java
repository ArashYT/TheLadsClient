package com.thelads.core.features.auto.modernadvancements.data.toast;

public enum ToastIconLayout {
   LEFT_SMALL,
   LEFT_LARGE;

   public String label() {
      return switch (this) {
         case LEFT_SMALL -> "Small (Left)";
         case LEFT_LARGE -> "Large (Left)";
      };
   }
}
