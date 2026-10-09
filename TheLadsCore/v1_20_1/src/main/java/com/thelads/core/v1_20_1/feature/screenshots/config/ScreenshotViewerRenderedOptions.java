// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.config;

import com.thelads.core.v1_20_1.feature.screenshots.screen.ConfigureButtonPlacementScreen;
import com.thelads.core.v1_20_1.feature.screenshots.screen.IconButtonWidget;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerTexts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** What the gallery settings screen lists, in order: the option, its label and description, and the widget that edits it. */
public final class ScreenshotViewerRenderedOptions {
   /** One row of the settings screen. */
   public record RenderedOption<T>(ConfigOption<T> option, Component name, Component description, Function<ConfigAccess, AbstractWidget> widgetFactory) {
   }

   private static final List<RenderedOption<?>> OPTIONS = new ArrayList<>();
   private static final Map<String, Component> CATEGORY_NAMES = new LinkedHashMap<>();

   public static List<RenderedOption<?>> options() {
      return Collections.unmodifiableList(OPTIONS);
   }

   public static Component categoryName(String category) {
      return CATEGORY_NAMES.getOrDefault(category, Component.literal(category));
   }

   private static <T> void add(ConfigOption<T> option, String key, Function<ConfigAccess, AbstractWidget> widgetFactory) {
      String translation = ScreenshotViewerTexts.translation("config", key);
      OPTIONS.add(new RenderedOption<>(option, Component.translatable(translation), Component.translatable(translation + ".desc"), widgetFactory));
   }

   private static void bool(ConfigOption<Boolean> option) {
      add(option, option.name(), config -> createBoolWidget(config, option));
   }

   private static void integer(ConfigOption<Integer> option) {
      add(option, option.name(), config -> createIntWidget(config, option));
   }

   private static <E extends Enum<E>> void enumeration(ConfigOption<E> option, Class<E> type) {
      add(option, option.name(), config -> createEnumWidget(config, option, type));
   }

   static {
      bool(ScreenshotViewerOptions.SHOW_BUTTON_IN_GAME_PAUSE_MENU);
      add(ScreenshotViewerOptions.PAUSE_MENU_BUTTON_POSITION, "pause_menu_button_position",
         config -> WidgetPositionOption.createWidget(
            config,
            ScreenshotViewerOptions.PAUSE_MENU_BUTTON_POSITION,
            () -> new PauseScreen(true),
            ConfigureButtonPlacementScreen.WidgetRemover.ofPredicate(IconButtonWidget.class::isInstance),
            () -> Minecraft.getInstance().player != null && config.getOrFallback(ScreenshotViewerOptions.SHOW_BUTTON_IN_GAME_PAUSE_MENU, true)
         ));
      bool(ScreenshotViewerOptions.SHOW_BUTTON_ON_TITLE_SCREEN);
      add(ScreenshotViewerOptions.TITLE_SCREEN_BUTTON_POSITION, "title_screen_button_position",
         config -> WidgetPositionOption.createWidget(
            config,
            ScreenshotViewerOptions.TITLE_SCREEN_BUTTON_POSITION,
            () -> new TitleScreen(false),
            ConfigureButtonPlacementScreen.WidgetRemover.ofPredicate(IconButtonWidget.class::isInstance),
            () -> config.getOrFallback(ScreenshotViewerOptions.SHOW_BUTTON_ON_TITLE_SCREEN, true)
         ));
      bool(ScreenshotViewerOptions.REDIRECT_SCREENSHOT_CHAT_LINKS);
      CATEGORY_NAMES.put("ingame", ScreenshotViewerTexts.translatable("config_category", "ingame"));

      add(ScreenshotViewerOptions.SCREENSHOTS_FOLDER, "screenshots_folder",
         config -> FileOption.createDirectoryWidget(config, ScreenshotViewerOptions.SCREENSHOTS_FOLDER));
      enumeration(ScreenshotViewerOptions.DEFAULT_LIST_ORDER, ScreenshotListOrder.class);
      bool(ScreenshotViewerOptions.PROMPT_WHEN_DELETING_SCREENSHOT);
      bool(ScreenshotViewerOptions.ENABLE_SCREENSHOT_ENLARGEMENT_ANIMATION);
      bool(ScreenshotViewerOptions.RENDER_WIDE_PROPERTIES_BUTTON);
      bool(ScreenshotViewerOptions.DISPLAY_HINT_TOOLTIP);
      bool(ScreenshotViewerOptions.INVERT_ZOOM_DIRECTION);
      integer(ScreenshotViewerOptions.INITIAL_SCREENSHOT_AMOUNT_PER_ROW);
      integer(ScreenshotViewerOptions.SCREEN_SCROLL_SPEED);
      add(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_BACKGROUND_COLOR, "screenshot_element_background_color",
         config -> ARGBColor.createWidget(config, ScreenshotViewerOptions.SCREENSHOT_ELEMENT_BACKGROUND_COLOR));
      enumeration(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_VISIBILITY, VisibilityState.class);
      add(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_COLOR, "screenshot_element_text_color",
         config -> ARGBColor.createWidget(config, ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_COLOR));
      bool(ScreenshotViewerOptions.RENDER_SCREENSHOT_ELEMENT_FONT_SHADOW);
      CATEGORY_NAMES.put("ingui", ScreenshotViewerTexts.translatable("config_category", "ingui"));

      add(ScreenshotViewerOptions.THUMBNAIL_FOLDER, "thumbnail_folder",
         config -> FileOption.createDirectoryWidget(config, ScreenshotViewerOptions.THUMBNAIL_FOLDER));
      enumeration(ScreenshotViewerOptions.COMPRESSION_RATIO, CompressionRatio.class);
      CATEGORY_NAMES.put("screenshot_thumbnails", ScreenshotViewerTexts.translatable("config_category", "screenshot_thumbnails"));
   }

   private ScreenshotViewerRenderedOptions() {}

   /** A true/false button that shows the stored value. */
   private static final class BooleanButton extends Button {
      private final ConfigAccess config;
      private final ConfigOption<Boolean> option;

      BooleanButton(ConfigAccess config, ConfigOption<Boolean> option) {
         super(0, 0, 100, 20, Component.empty(), button -> config.put(option, config.get(option).map(b -> !b).orElse(false)), DEFAULT_NARRATION);
         this.config = config;
         this.option = option;
      }

      @Override
      public Component getMessage() {
         return config.get(option).map(Object::toString).map(Component::nullToEmpty).orElseGet(super::getMessage);
      }
   }

   private static AbstractWidget createBoolWidget(ConfigAccess config, ConfigOption<Boolean> option) {
      return new BooleanButton(config, option);
   }

   private static AbstractWidget createIntWidget(ConfigAccess config, ConfigOption<Integer> option) {
      int space = 10;
      if (option.min() != null && option.max() != null) {
         space = Math.max(digits(option.min()) + (option.min() < 0 ? 1 : 0), digits(option.max()));
      }
      EditBox widget = new EditBox(Minecraft.getInstance().font, 0, 0, Mth.clamp(space * 10, 20, 100), 20, Component.empty());
      widget.setMaxLength(space);
      widget.setValue(config.get(option).map(String::valueOf).orElse(""));
      widget.setFilter(s -> {
         if (s.isEmpty() || s.equals("-")) return true;
         try {
            Integer.parseInt(s);
            return true;
         } catch (NumberFormatException notNumber) {
            return false;
         }
      });
      widget.setResponder(s -> {
         if (s.isEmpty() || s.equals("-")) {
            config.put(option, null);
            return;
         }
         try {
            int parsed = Integer.parseInt(s);
            int clamped = parsed;
            if (option.min() != null) clamped = Math.max(clamped, option.min());
            if (option.max() != null) clamped = Math.min(clamped, option.max());
            if (parsed != clamped) widget.setValue(String.valueOf(clamped));
            config.put(option, clamped);
         } catch (NumberFormatException ignored) {
         }
      });
      return widget;
   }

   private static int digits(int value) {
      return String.valueOf(Math.abs(value)).length();
   }

   private static <E extends Enum<E>> AbstractWidget createEnumWidget(ConfigAccess config, ConfigOption<E> option, Class<E> type) {
      E[] constants = type.getEnumConstants();
      E initial = config.getOrFallback(option, constants[0]);
      return CycleButton.<E>builder(value -> Component.literal(value.toString().toUpperCase()))
         .withValues(constants)
         .withInitialValue(initial)
         .displayOnlyValue()
         .create(0, 0, 100, 20, Component.empty(), (button, value) -> config.put(option, value));
   }
}
