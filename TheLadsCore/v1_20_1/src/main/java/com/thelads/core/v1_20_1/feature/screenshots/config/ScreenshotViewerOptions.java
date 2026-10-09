// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots.config;

import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewerUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every gallery option with the names and defaults of the 26.3 gallery. */
public final class ScreenshotViewerOptions {
   private static final List<ConfigOption<?>> ALL = new ArrayList<>();

   private static <T> ConfigOption<T> add(ConfigOption<T> option) {
      ALL.add(option);
      return option;
   }

   public static final ConfigOption<Boolean> SHOW_BUTTON_IN_GAME_PAUSE_MENU = add(ConfigOption.bool("show_button_in_game_pause_menu", "ingame", true));
   public static final ConfigOption<WidgetPositionOption.WidgetPosition> PAUSE_MENU_BUTTON_POSITION = add(
      WidgetPositionOption.option("pause_menu_button_position", "ingame")
   );
   public static final ConfigOption<Boolean> SHOW_BUTTON_ON_TITLE_SCREEN = add(ConfigOption.bool("show_button_on_title_screen", "ingame", true));
   public static final ConfigOption<WidgetPositionOption.WidgetPosition> TITLE_SCREEN_BUTTON_POSITION = add(
      WidgetPositionOption.option("title_screen_button_position", "ingame")
   );
   public static final ConfigOption<Boolean> REDIRECT_SCREENSHOT_CHAT_LINKS = add(ConfigOption.bool("redirect_screenshot_chat_links", "ingame", false));
   public static final ConfigOption<File> SCREENSHOTS_FOLDER = add(
      FileOption.option("screenshots_folder", ScreenshotViewerUtils::getVanillaScreenshotsFolder, "ingui")
   );
   public static final ConfigOption<ScreenshotListOrder> DEFAULT_LIST_ORDER = add(
      ConfigOption.enumeration("default_list_order", "ingui", ScreenshotListOrder.class, ScreenshotListOrder.ASCENDING)
   );
   public static final ConfigOption<Boolean> PROMPT_WHEN_DELETING_SCREENSHOT = add(ConfigOption.bool("prompt_when_deleting_screenshot", "ingui", true));
   public static final ConfigOption<Boolean> ENABLE_SCREENSHOT_ENLARGEMENT_ANIMATION = add(ConfigOption.bool("enable_screenshot_enlargement_animation", "ingui", true));
   public static final ConfigOption<Boolean> DISPLAY_HINT_TOOLTIP = add(ConfigOption.bool("display_hint_tooltip", "ingui", true));
   public static final ConfigOption<Boolean> RENDER_WIDE_PROPERTIES_BUTTON = add(ConfigOption.bool("render_wide_properties_button", "ingui", true));
   public static final ConfigOption<Boolean> INVERT_ZOOM_DIRECTION = add(ConfigOption.bool("invert_zoom_direction", "ingui", false));
   public static final ConfigOption<Integer> INITIAL_SCREENSHOT_AMOUNT_PER_ROW = add(ConfigOption.integer("initial_screenshot_amount_per_row", "ingui", 4, 2, 8));
   public static final ConfigOption<Integer> SCREEN_SCROLL_SPEED = add(ConfigOption.integer("screen_scroll_speed", "ingui", 10, 1, 50));
   public static final ConfigOption<ARGBColor> SCREENSHOT_ELEMENT_BACKGROUND_COLOR = add(
      ARGBColor.option("screenshot_element_background_color", ARGBColor.WHITE, "ingui")
   );
   public static final ConfigOption<VisibilityState> SCREENSHOT_ELEMENT_TEXT_VISIBILITY = add(
      ConfigOption.enumeration("screenshot_element_text_visibility", "ingui", VisibilityState.class, VisibilityState.VISIBLE)
   );
   public static final ConfigOption<ARGBColor> SCREENSHOT_ELEMENT_TEXT_COLOR = add(
      ARGBColor.option("screenshot_element_text_color", ARGBColor.WHITE, "ingui")
   );
   public static final ConfigOption<Boolean> RENDER_SCREENSHOT_ELEMENT_FONT_SHADOW = add(ConfigOption.bool("render_screenshot_element_font_shadow", "ingui", true));
   public static final ConfigOption<File> THUMBNAIL_FOLDER = add(
      FileOption.option("thumbnail_folder", ScreenshotViewerUtils::getDefaultThumbnailFolder, "screenshot_thumbnails")
   );
   public static final ConfigOption<CompressionRatio> COMPRESSION_RATIO = add(
      ConfigOption.enumeration("compression_ratio", "screenshot_thumbnails", CompressionRatio.class, CompressionRatio.NONE)
   );

   private ScreenshotViewerOptions() {}

   public static List<ConfigOption<?>> all() {
      return Collections.unmodifiableList(ALL);
   }
}
