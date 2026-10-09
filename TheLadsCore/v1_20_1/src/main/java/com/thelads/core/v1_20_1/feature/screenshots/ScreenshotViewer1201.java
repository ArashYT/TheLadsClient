// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v1_20_1.feature.screenshots;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerConfig;
import com.thelads.core.v1_20_1.feature.screenshots.config.ScreenshotViewerOptions;
import com.thelads.core.v1_20_1.feature.screenshots.config.WidgetPositionOption;
import com.thelads.core.v1_20_1.feature.screenshots.screen.IconButtonWidget;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerConfigScreen;
import com.thelads.core.v1_20_1.feature.screenshots.screen.ScreenshotViewerTexts;
import com.thelads.core.v1_20_1.feature.screenshots.screen.manage_screenshots.ManageScreenshotsScreen;
import com.thelads.core.v1_20_1.gui.ModPresence1201;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.Mth;

/** Native gallery; external ownership wins while the old jar remains installed. */
public final class ScreenshotViewer1201 {
    public static final String MODID = "lads_screenshots";
    private static ScreenshotViewer1201 instance;
    private final ScreenshotViewerConfig config;
    private final ScreenshotThumbnailManager thumbnailManager;
    private final KeyMapping openScreenshotsScreenKey;

    private ScreenshotViewer1201() {
        config = new ScreenshotViewerConfig();
        thumbnailManager = new ScreenshotThumbnailManager(config);
        openScreenshotsScreenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            ScreenshotViewerTexts.translation("key", "open_screenshots_screen"), InputConstants.Type.KEYSYM, InputConstants.KEY_F10, KeyMapping.CATEGORY_MISC));
    }

    /** Registers the BetterScreenshots module, the F10 key, the title and pause screen button and the settings; call once from the client entrypoint. */
    public static void register() {
        if (instance != null || ModPresence1201.isLoaded("screenshot_viewer") || ModPresence1201.isLoaded("decentscreenshot")) return;
        instance = new ScreenshotViewer1201();
        ModuleSupport.registerBuiltIn("BetterScreenshots");
        Module module = NativeFeatures1201.module("BetterScreenshots");
        if (module != null) {
            ActionOption browse = new ActionOption("Browse Screenshots", "Open gallery");
            browse.setAction(() -> open(Minecraft.getInstance().screen));
            module.addOption(browse);
            ActionOption settings = new ActionOption("Gallery Settings", "Configure gallery");
            settings.setAction(() -> Minecraft.getInstance().setScreen(new ScreenshotViewerConfigScreen(Minecraft.getInstance().screen)));
            module.addOption(settings);
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            NativeScreenshotToast.releaseExpired();
            while (instance.openScreenshotsScreenKey.consumeClick()) {
                Screen screen = client.screen;
                if (active() && (screen == null || screen instanceof TitleScreen || screen instanceof PauseScreen)) open(screen);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> addEntry(screen));
    }

    public static boolean available() { return instance != null; }

    public static boolean active() { return available() && NativeFeatures1201.enabled("BetterScreenshots"); }

    /** The Chat module's "Screenshot Link Buttons" switch (on unless the Chat module is off or the option is). */
    public static boolean chatLinkButtons() {
        Module chat = NativeFeatures1201.module("Chat");
        return chat != null && chat.isEnabled() && (!(chat.getOption("Screenshot Link Buttons") instanceof BoolOption option) || option.get());
    }

    public static boolean open(Screen parent) {
        if (!active()) return false;
        instance.thumbnailManager.configUpdated();
        Minecraft.getInstance().setScreen(new ManageScreenshotsScreen(parent));
        return true;
    }

    /** Also used by the placement editor on its preview screen. */
    public static void addEntry(Screen screen) {
        if (!active() || !(screen instanceof TitleScreen || screen instanceof PauseScreen)) return;
        boolean title = screen instanceof TitleScreen;
        if (!instance.config.getOrFallback(title ? ScreenshotViewerOptions.SHOW_BUTTON_ON_TITLE_SCREEN : ScreenshotViewerOptions.SHOW_BUTTON_IN_GAME_PAUSE_MENU, true)) return;
        List<AbstractWidget> buttons = Screens.getButtons(screen);
        if (buttons.stream().anyMatch(IconButtonWidget.class::isInstance)) return;
        AbstractWidget reference = buttons.isEmpty() ? null : buttons.get(0);
        var position = instance.config.get(title ? ScreenshotViewerOptions.TITLE_SCREEN_BUTTON_POSITION : ScreenshotViewerOptions.PAUSE_MENU_BUTTON_POSITION);
        int x = position.map(WidgetPositionOption.WidgetPosition::x).orElse(title ? screen.width - 28 : (reference == null ? screen.width / 2 + 108 : reference.getWidth() + 4));
        int y = position.map(WidgetPositionOption.WidgetPosition::y).orElse(title ? 8 : 0);
        if (reference != null && (position.isPresent() || !title)) { x += reference.getX(); y += reference.getY(); }
        IconButtonWidget button = new IconButtonWidget(Mth.clamp(x, 0, Math.max(0, screen.width - 20)), Mth.clamp(y, 0, Math.max(0, screen.height - 20)),
            20, 20, ScreenshotViewerTexts.MANAGE_SCREENSHOTS, IconButtonWidget.SCREENSHOT_VIEWER_ICON, ignored -> open(screen));
        button.setTooltip(Tooltip.create(ScreenshotViewerTexts.MANAGE_SCREENSHOTS));
        buttons.add(button);
    }

    public ScreenshotViewerConfig getConfig() { return config; }
    public ScreenshotThumbnailManager getThumbnailManager() { return thumbnailManager; }
    public KeyMapping getOpenScreenshotsScreenKey() { return openScreenshotsScreenKey; }
    public static ScreenshotViewer1201 getInstance() { return instance; }
}
