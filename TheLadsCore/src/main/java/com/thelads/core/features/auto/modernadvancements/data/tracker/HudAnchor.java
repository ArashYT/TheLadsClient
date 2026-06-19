package com.thelads.core.features.auto.modernadvancements.data.tracker;

public enum HudAnchor {
   TOP_LEFT,
   TOP_CENTER,
   TOP_RIGHT,
   CENTER_LEFT,
   CENTER,
   CENTER_RIGHT,
   BOTTOM_LEFT,
   BOTTOM_CENTER,
   BOTTOM_RIGHT;

   public int getRefX(int screenW) {
      return switch (this) {
         case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> 0;
         case TOP_CENTER, CENTER, BOTTOM_CENTER -> screenW / 2;
         case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> screenW;
      };
   }

   public int getRefY(int screenH) {
      return switch (this) {
         case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0;
         case CENTER_LEFT, CENTER, CENTER_RIGHT -> screenH / 2;
         case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenH;
      };
   }

   public int getPivotX(int boxW) {
      return switch (this) {
         case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> 0;
         case TOP_CENTER, CENTER, BOTTOM_CENTER -> boxW / 2;
         case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> boxW;
      };
   }

   public int getPivotY(int boxH) {
      return switch (this) {
         case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0;
         case CENTER_LEFT, CENTER, CENTER_RIGHT -> boxH / 2;
         case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> boxH;
      };
   }

   public String symbol() {
      return switch (this) {
         case TOP_LEFT -> "↖";
         case TOP_CENTER -> "↑";
         case TOP_RIGHT -> "↗";
         case CENTER_LEFT -> "←";
         case CENTER -> "·";
         case CENTER_RIGHT -> "→";
         case BOTTOM_LEFT -> "↙";
         case BOTTOM_CENTER -> "↓";
         case BOTTOM_RIGHT -> "↘";
      };
   }
}
