// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47), Minecraft 26.2. See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots.config;

import io.github.lgatodu47.catconfigmc.RenderedConfigOptionAccess;
import io.github.lgatodu47.catconfigmc.RenderedConfigOptionBuilder;
import com.thelads.core.v26_2.feature.screenshots.screen.ConfigureButtonPlacementScreen;
import com.thelads.core.v26_2.feature.screenshots.screen.IconButtonWidget;
import com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotViewerTexts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;

public class ScreenshotViewerRenderedOptions {
   private static final RenderedConfigOptionBuilder BUILDER = new RenderedConfigOptionBuilder();

   public static RenderedConfigOptionAccess access() {
      return BUILDER;
   }

   static {
      BUILDER.ofBoolean(ScreenshotViewerOptions.SHOW_BUTTON_IN_GAME_PAUSE_MENU)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "show_button_in_game_pause_menu"))
         .build();
      BUILDER.option(ScreenshotViewerOptions.PAUSE_MENU_BUTTON_POSITION)
         .setWidgetFactory(
            config -> WidgetPositionOption.createWidget(
               config,
               ScreenshotViewerOptions.PAUSE_MENU_BUTTON_POSITION,
               () -> new PauseScreen(true),
               ConfigureButtonPlacementScreen.WidgetRemover.ofPredicate(IconButtonWidget.class::isInstance),
               () -> Minecraft.getInstance().player != null && (Boolean)config.getOrFallback(ScreenshotViewerOptions.SHOW_BUTTON_IN_GAME_PAUSE_MENU, true)
            )
         )
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "pause_menu_button_position"))
         .build();
      BUILDER.ofBoolean(ScreenshotViewerOptions.SHOW_BUTTON_ON_TITLE_SCREEN)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "show_button_on_title_screen"))
         .build();
      BUILDER.option(ScreenshotViewerOptions.TITLE_SCREEN_BUTTON_POSITION)
         .setWidgetFactory(
            config -> WidgetPositionOption.createWidget(
               config,
               ScreenshotViewerOptions.TITLE_SCREEN_BUTTON_POSITION,
               () -> new TitleScreen(false),
               ConfigureButtonPlacementScreen.WidgetRemover.ofPredicate(IconButtonWidget.class::isInstance),
               () -> (Boolean)config.getOrFallback(ScreenshotViewerOptions.SHOW_BUTTON_ON_TITLE_SCREEN, true)
            )
         )
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "title_screen_button_position"))
         .build();
      BUILDER.ofBoolean(ScreenshotViewerOptions.REDIRECT_SCREENSHOT_CHAT_LINKS)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "redirect_screenshot_chat_links"))
         .build();
      BUILDER.withCategoryName("ingame", ScreenshotViewerTexts.translatable("config_category", "ingame"));
      BUILDER.option(ScreenshotViewerOptions.SCREENSHOTS_FOLDER)
         .setWidgetFactory(config -> FileOption.createDirectoryWidget(config, ScreenshotViewerOptions.SCREENSHOTS_FOLDER))
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "screenshots_folder"))
         .build();
      BUILDER.ofEnum(ScreenshotViewerOptions.DEFAULT_LIST_ORDER, ScreenshotListOrder.class)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "default_list_order"))
         .build();
      BUILDER.ofBoolean(ScreenshotViewerOptions.PROMPT_WHEN_DELETING_SCREENSHOT)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "prompt_when_deleting_screenshot"))
         .build();
      BUILDER.ofBoolean(ScreenshotViewerOptions.ENABLE_SCREENSHOT_ENLARGEMENT_ANIMATION)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "enable_screenshot_enlargement_animation"))
         .build();
      BUILDER.ofBoolean(ScreenshotViewerOptions.RENDER_WIDE_PROPERTIES_BUTTON)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "render_wide_properties_button"))
         .build();
      BUILDER.ofBoolean(ScreenshotViewerOptions.DISPLAY_HINT_TOOLTIP)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "display_hint_tooltip"))
         .build();
      BUILDER.ofBoolean(ScreenshotViewerOptions.INVERT_ZOOM_DIRECTION)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "invert_zoom_direction"))
         .build();
      BUILDER.ofInt(ScreenshotViewerOptions.INITIAL_SCREENSHOT_AMOUNT_PER_ROW)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "initial_screenshot_amount_per_row"))
         .build();
      BUILDER.ofInt(ScreenshotViewerOptions.SCREEN_SCROLL_SPEED)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "screen_scroll_speed"))
         .build();
      BUILDER.option(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_BACKGROUND_COLOR)
         .setWidgetFactory(config -> ARGBColor.createWidget(config, ScreenshotViewerOptions.SCREENSHOT_ELEMENT_BACKGROUND_COLOR))
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "screenshot_element_background_color"))
         .build();
      BUILDER.ofEnum(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_VISIBILITY, VisibilityState.class)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "screenshot_element_text_visibility"))
         .build();
      BUILDER.option(ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_COLOR)
         .setWidgetFactory(config -> ARGBColor.createWidget(config, ScreenshotViewerOptions.SCREENSHOT_ELEMENT_TEXT_COLOR))
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "screenshot_element_text_color"))
         .build();
      BUILDER.ofBoolean(ScreenshotViewerOptions.RENDER_SCREENSHOT_ELEMENT_FONT_SHADOW)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "render_screenshot_element_font_shadow"))
         .build();
      BUILDER.withCategoryName("ingui", ScreenshotViewerTexts.translatable("config_category", "ingui"));
      BUILDER.option(ScreenshotViewerOptions.THUMBNAIL_FOLDER)
         .setWidgetFactory(config -> FileOption.createDirectoryWidget(config, ScreenshotViewerOptions.THUMBNAIL_FOLDER))
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "thumbnail_folder"))
         .build();
      BUILDER.ofEnum(ScreenshotViewerOptions.COMPRESSION_RATIO, CompressionRatio.class)
         .setCommonTranslationKey(ScreenshotViewerTexts.translation("config", "compression_ratio"))
         .build();
      BUILDER.withCategoryName("screenshot_thumbnails", ScreenshotViewerTexts.translatable("config_category", "screenshot_thumbnails"));
   }
}
