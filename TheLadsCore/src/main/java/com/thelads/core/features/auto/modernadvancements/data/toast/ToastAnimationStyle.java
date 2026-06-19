package com.thelads.core.features.auto.modernadvancements.data.toast;

public enum ToastAnimationStyle {
   SLIDE,
   FADE,
   POP,
   NONE;

   public String label() {
      return switch (this) {
         case SLIDE -> "Slide";
         case FADE -> "Fade";
         case POP -> "Pop";
         case NONE -> "None";
      };
   }
}
