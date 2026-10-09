// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.config;

import com.google.gson.JsonPrimitive;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

public final class FileOption {
   private FileOption() {}

   public static ConfigOption<File> option(String name, @Nullable Supplier<File> defaultValue, String category) {
      return ConfigOption.custom(name, category, File.class, defaultValue == null ? () -> null : defaultValue,
         file -> new JsonPrimitive(file.getAbsolutePath()), json -> new File(json.getAsString()));
   }

   public static AbstractWidget createDirectoryWidget(ConfigAccess config, ConfigOption<File> option) {
      FileOption.FilePathWidget widget = new FileOption.FilePathWidget(Minecraft.getInstance().font, 0, 0, 200, 20, Component.empty());
      Supplier<File> defaultValue = () -> Objects.requireNonNull(option.defaultValue());
      widget.setValue(config.get(option).orElseGet(defaultValue).getAbsolutePath());
      AtomicBoolean corrected = new AtomicBoolean(false);
      widget.setFormatter(
         (text, firstCharacterIndex) -> FormattedCharSequence.forward(text, corrected.get() ? Style.EMPTY.withColor(ChatFormatting.RED) : Style.EMPTY)
      );
      widget.setAcceptChangesListener(() -> {
         File target = new File(widget.getValue());
         if (target.exists() && target.isAbsolute() && target.isDirectory() && target.canRead()) {
            config.put(option, target);
            corrected.set(false);
         } else {
            widget.setValue(defaultValue.get().getAbsolutePath());
            corrected.set(true);
         }
      });
      return widget;
   }

   public static class FilePathWidget extends EditBox {
      protected Runnable acceptChangesListener;

      public FilePathWidget(Font renderer, int x, int y, int width, int height, Component text) {
         super(renderer, x, y, width, height, text);
         this.setMaxLength(Integer.MAX_VALUE);
      }

      @Override
      public boolean mouseClicked(double mouseX, double mouseY, int button) {
         boolean res = super.mouseClicked(mouseX, mouseY, button);
         if (res && this.acceptChangesListener != null) {
            this.acceptChangesListener.run();
         }

         return res;
      }

      @Override
      public boolean keyPressed(int key, int scanCode, int modifiers) {
         if (!this.canConsumeInput()) {
            return false;
         } else if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN && this.acceptChangesListener != null) {
            this.acceptChangesListener.run();
            return true;
         } else {
            return super.keyPressed(key, scanCode, modifiers);
         }
      }

      @Override
      public void setFocused(boolean focused) {
         super.setFocused(focused);
         if (!focused && this.acceptChangesListener != null) {
            this.acceptChangesListener.run();
         }
      }

      public void setAcceptChangesListener(Runnable acceptChangesListener) {
         this.acceptChangesListener = acceptChangesListener;
      }
   }
}
