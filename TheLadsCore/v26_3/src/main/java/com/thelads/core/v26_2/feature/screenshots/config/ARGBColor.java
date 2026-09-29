// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.config;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.github.lgatodu47.catconfig.ConfigAccess;
import io.github.lgatodu47.catconfig.ConfigOption;
import io.github.lgatodu47.catconfig.ValueSerializationHelper;
import io.github.lgatodu47.catconfigmc.OldEditBox;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

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

   public static AbstractWidget createWidget(ConfigAccess config, ConfigOption<ARGBColor> option) {
      ARGBColor.ColorTextField widget = new ARGBColor.ColorTextField(Minecraft.getInstance().font, 0, 0, 80, 20, Component.empty());
      widget.setMaxLength(9);
      widget.setValue(config.get(option).map(ARGBColor::toHex).orElse("#"));
      widget.setTextPredicate(s -> s.startsWith("#") && (s.substring(1).isEmpty() || fromHex(s).isPresent()));
      widget.setResponder(s -> {
         if (!s.isEmpty() && !s.substring(1).isEmpty()) {
            fromHex(s).ifPresent(color -> config.put(option, color));
         } else {
            config.put(option, null);
         }
      });
      return widget;
   }

   public static class ColorTextField extends OldEditBox {
      public ColorTextField(Font renderer, int x, int y, int width, int height, Component text) {
         super(renderer, x, y, width, height, text);
      }

      public boolean keyPressed(KeyEvent input) {
         if (this.canConsumeInput() && input.isPaste()) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard.startsWith("#") && clipboard.length() < 8) {
               clipboard = clipboard.substring(1);
            }

            this.insertText(clipboard);
            return true;
         } else {
            return super.keyPressed(input);
         }
      }

      public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
         super.extractWidgetRenderState(context, mouseX, mouseY, delta);
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

   public record Option(String name, @Nullable ARGBColor defaultValue, @Nullable String optCategory) implements ConfigOption<ARGBColor> {
      public Optional<String> category() {
         return Optional.ofNullable(this.optCategory);
      }

      public Class<ARGBColor> type() {
         return ARGBColor.class;
      }

      public void write(JsonWriter writer, @NonNull ARGBColor value, ValueSerializationHelper helper) throws IOException {
         writer.value(value.value());
      }

      public ARGBColor read(JsonReader reader, ValueSerializationHelper helper) throws IOException {
         return reader.peek().equals(JsonToken.STRING)
            ? TextColor.parseColor(reader.nextString()).result().map(textColor -> new ARGBColor(0xFF000000 | textColor.getValue())).orElse(null)
            : new ARGBColor(reader.nextInt());
      }
   }
}
