// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.config;

import com.google.gson.JsonObject;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ConfigureButtonPlacementScreen;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerTexts;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

public final class WidgetPositionOption {
   private WidgetPositionOption() {}

   public record WidgetPosition(int x, int y) {
   }

   public static ConfigOption<WidgetPosition> option(String name, String category) {
      return ConfigOption.custom(name, category, WidgetPosition.class, () -> null, value -> {
         JsonObject json = new JsonObject();
         json.addProperty("x", value.x());
         json.addProperty("y", value.y());
         return json;
      }, json -> {
         JsonObject object = json.getAsJsonObject();
         return new WidgetPosition(object.get("x").getAsInt(), object.get("y").getAsInt());
      });
   }

   public static AbstractWidget createWidget(
      ConfigAccess access,
      ConfigOption<WidgetPosition> option,
      Supplier<Screen> configuringScreenFactory,
      ConfigureButtonPlacementScreen.WidgetRemover remover,
      BooleanSupplier canEdit
   ) {
      Minecraft client = Minecraft.getInstance();
      Button btn = Button.builder(
            ScreenshotViewerTexts.EDIT_WIDGET_PLACEMENT,
            button -> client.setScreen(new ConfigureButtonPlacementScreen(client.screen, access, option, configuringScreenFactory, remover))
         )
         .width(100)
         .build();
      btn.active = canEdit.getAsBoolean();
      return btn;
   }
}
