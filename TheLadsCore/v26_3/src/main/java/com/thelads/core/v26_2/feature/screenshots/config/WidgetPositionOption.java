// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.config;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.lgatodu47.catconfig.ConfigAccess;
import io.github.lgatodu47.catconfig.ConfigOption;
import io.github.lgatodu47.catconfig.ValueSerializationHelper;
import com.thelads.core.v26_2.feature.screenshots.screen.ConfigureButtonPlacementScreen;
import com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotViewerTexts;
import java.io.IOException;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record WidgetPositionOption(String name, @Nullable WidgetPositionOption.WidgetPosition defaultValue, @Nullable String optCategory)
   implements ConfigOption<WidgetPositionOption.WidgetPosition> {
   public Optional<String> category() {
      return Optional.ofNullable(this.optCategory);
   }

   public Class<WidgetPositionOption.WidgetPosition> type() {
      return WidgetPositionOption.WidgetPosition.class;
   }

   public void write(JsonWriter writer, @NotNull WidgetPositionOption.WidgetPosition value, ValueSerializationHelper helper) throws IOException {
      writer.beginObject();
      writer.name("x");
      writer.value(value.x());
      writer.name("y");
      writer.value(value.y());
      writer.endObject();
   }

   public WidgetPositionOption.WidgetPosition read(JsonReader reader, ValueSerializationHelper helper) throws IOException {
      reader.beginObject();
      reader.nextName();
      int x = reader.nextInt();
      reader.nextName();
      int y = reader.nextInt();
      reader.endObject();
      return new WidgetPositionOption.WidgetPosition(x, y);
   }

   public static AbstractWidget createWidget(
      ConfigAccess access,
      ConfigOption<WidgetPositionOption.WidgetPosition> option,
      Supplier<Screen> configuringScreenFactory,
      ConfigureButtonPlacementScreen.WidgetRemover remover,
      BooleanSupplier canEdit
   ) {
      Minecraft client = Minecraft.getInstance();
      Button btn = new Builder(
            ScreenshotViewerTexts.EDIT_WIDGET_PLACEMENT,
            button -> client.gui.setScreen(new ConfigureButtonPlacementScreen(client.gui.screen(), access, option, configuringScreenFactory, remover))
         )
         .width(100)
         .build();
      btn.active = canEdit.getAsBoolean();
      return btn;
   }

   public record WidgetPosition(int x, int y) {
   }
}
