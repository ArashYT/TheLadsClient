// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

public record ARGBColor(int value) {
   public static final ARGBColor WHITE = new ARGBColor(-1);

   public int alpha() {
      return this.value() >>> 24;
   }

   public int red() {
      return this.value() >> 16 & 0xFF;
   }

   public int green() {
      return this.value() >> 8 & 0xFF;
   }

   public int blue() {
      return this.value() & 0xFF;
   }

   public String toHex() {
      return String.format("#%08X", this.value());
   }

   public static Optional<ARGBColor> fromHex(String hexString) {
      if (hexString.startsWith("#")) {
         hexString = hexString.substring(1);
      }

      try {
         int value = Integer.parseUnsignedInt(hexString.substring(0, Math.min(hexString.length(), 8)), 16);
         return Optional.of(new ARGBColor(value));
      } catch (Throwable var2) {
         return Optional.empty();
      }
   }

   public static ARGBColor fromChannels(int a, int r, int g, int b) {
      return new ARGBColor((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF);
   }

   public static ConfigOption<ARGBColor> option(String name, ARGBColor defaultValue, String category) {
      return ConfigOption.custom(name, category, ARGBColor.class, () -> defaultValue, color -> new JsonPrimitive(color.value()), ARGBColor::readJson);
   }

   private static ARGBColor readJson(JsonElement json) {
      if (json.getAsJsonPrimitive().isString()) {
         TextColor parsed = TextColor.parseColor(json.getAsString());
         return parsed == null ? null : new ARGBColor(0xFF000000 | parsed.getValue());
      }
      return new ARGBColor(json.getAsInt());
   }

   public static AbstractWidget createWidget(ConfigAccess config, ConfigOption<ARGBColor> option) {
      ARGBColor.ColorTextField widget = new ARGBColor.ColorTextField(Minecraft.getInstance().font, 0, 0, 80, 20, Component.empty());
      widget.setMaxLength(9);
      widget.setValue(config.get(option).map(ARGBColor::toHex).orElse("#"));
      widget.setFilter(s -> s.startsWith("#") && (s.substring(1).isEmpty() || fromHex(s).isPresent()));
      widget.setResponder(s -> {
         if (!s.isEmpty() && !s.substring(1).isEmpty()) {
            fromHex(s).ifPresent(color -> config.put(option, color));
         } else {
            config.put(option, null);
         }
      });
      return widget;
   }

   public static class ColorTextField extends EditBox {
      public ColorTextField(Font renderer, int x, int y, int width, int height, Component text) {
         super(renderer, x, y, width, height, text);
      }

      @Override
      public boolean keyPressed(int key, int scanCode, int modifiers) {
         if (this.canConsumeInput() && Screen.isPaste(key)) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard.startsWith("#") && clipboard.length() < 8) {
               clipboard = clipboard.substring(1);
            }

            this.insertText(clipboard);
            return true;
         } else {
            return super.keyPressed(key, scanCode, modifiers);
         }
      }

      @Override
      public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
         super.renderWidget(context, mouseX, mouseY, delta);
         if (this.isVisible()) {
            String text = this.getValue();
            if (!text.isEmpty()) {
               context.fill(this.getX(), this.getY(), this.getX() + 3, this.getY() + this.height / 2, -1);
               context.fill(this.getX(), this.getY() + this.height / 2, this.getX() + 3, this.getY() + this.height, -16777216);

               try {
                  int color = Integer.parseUnsignedInt(text.substring(1), 16);
                  context.fill(this.getX(), this.getY(), this.getX() + 3, this.getY() + this.height, color);
               } catch (NumberFormatException var7) {
               }
            }
         }
      }
   }
}
