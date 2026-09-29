// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.config;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.lgatodu47.catconfig.ConfigAccess;
import io.github.lgatodu47.catconfig.ConfigOption;
import io.github.lgatodu47.catconfig.ValueSerializationHelper;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record FileOption(String name, @Nullable Supplier<File> defValue, @Nullable String optCategory) implements ConfigOption<File> {
   @Nullable
   public File defaultValue() {
      return this.defValue == null ? null : this.defValue.get();
   }

   public Optional<String> category() {
      return Optional.ofNullable(this.optCategory);
   }

   public Class<File> type() {
      return File.class;
   }

   public void write(JsonWriter writer, @NotNull File value, ValueSerializationHelper helper) throws IOException {
      writer.value(value.getAbsolutePath());
   }

   public File read(JsonReader reader, ValueSerializationHelper helper) throws IOException {
      return new File(reader.nextString());
   }

   public static AbstractWidget createDirectoryWidget(ConfigAccess config, ConfigOption<File> option) {
      FileOption.FilePathWidget widget = new FileOption.FilePathWidget(Minecraft.getInstance().font, 0, 0, 200, 20, Component.empty());
      Supplier<File> defaultValue = () -> Objects.requireNonNull((File)option.defaultValue());
      widget.setValue(config.get(option).orElseGet(defaultValue).getAbsolutePath());
      AtomicBoolean corrected = new AtomicBoolean(false);
      widget.addFormatter(
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

      public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
         boolean res = super.mouseClicked(click, doubled);
         if (res && this.acceptChangesListener != null) {
            this.acceptChangesListener.run();
         }

         return res;
      }

      public boolean keyPressed(KeyEvent input) {
         if (!this.canConsumeInput()) {
            return false;
         } else if (input.key() == 257 && this.acceptChangesListener != null) {
            this.acceptChangesListener.run();
            return true;
         } else {
            return super.keyPressed(input);
         }
      }

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
