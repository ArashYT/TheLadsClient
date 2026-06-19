package com.thelads.core.features.auto.modernadvancements.data.toast;

public enum ToastBackgroundStyle {
   SOLID,
   GRADIENT,
   TRANSPARENT;

   public String label() {
      return switch (this) {
         case SOLID -> "Solid";
         case GRADIENT -> "Gradient";
         case TRANSPARENT -> "Transparent";
      };
   }
}
