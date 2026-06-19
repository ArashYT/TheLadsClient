package com.thelads.core.features.auto.modernadvancements.data.toast;

public enum ToastFrameStyle {
   AUTO,
   UNIFORM,
   NONE;

   public String label() {
      return switch (this) {
         case AUTO -> "Per Type";
         case UNIFORM -> "Uniform";
         case NONE -> "None";
      };
   }
}
