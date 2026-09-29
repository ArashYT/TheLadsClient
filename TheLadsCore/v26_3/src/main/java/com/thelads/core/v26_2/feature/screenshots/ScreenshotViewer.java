// SPDX-License-Identifier: MIT
// Adapted from Screenshot Viewer 1.3.6 (LGatodu47). See ScreenshotViewer-LICENSE.txt.
package com.thelads.core.v26_2.feature.screenshots;

import com.thelads.core.config.ActionOption;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.screenshots.config.*;
import com.thelads.core.v26_2.feature.screenshots.screen.*;
import com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots.ManageScreenshotsScreen;
import io.github.lgatodu47.catconfig.CatConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.*;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Native gallery; external ownership wins while the old jar remains installed. */
public final class ScreenshotViewer {
    public static final String MODID = "lads_screenshots";
    public static final Identifier SCREENSHOT_VIEWER_ICON = Identifier.fromNamespaceAndPath(MODID, "widget/icons/screenshot_viewer");
    private static ScreenshotViewer instance;
    private final CatConfig config;
    private final ScreenshotThumbnailManager thumbnailManager;
    private final KeyMapping openScreenshotsScreenKey;
    private ScreenshotViewer() {
        ScreenshotViewerConfig.importLegacy();
        config = new ScreenshotViewerConfig();
        thumbnailManager = new ScreenshotThumbnailManager(config);
        openScreenshotsScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            ScreenshotViewerTexts.translation("key", "open_screenshots_screen"), GLFW.GLFW_KEY_F10, KeyMapping.Category.MISC));
    }
    public static void register() {
        if (instance != null || FabricLoader.getInstance().isModLoaded("screenshot_viewer")
            || FabricLoader.getInstance().isModLoaded("decentscreenshot")) return;
        instance = new ScreenshotViewer();
        ModuleSupport.registerBuiltIn("BetterScreenshots");
        var module = NativeQualityOfLife.module("BetterScreenshots");
        ActionOption browse = new ActionOption("Browse Screenshots", "Open gallery");
        browse.setAction(() -> open(Minecraft.getInstance().gui.screen()));
        module.addOption(browse);
        ActionOption settings = new ActionOption("Gallery Settings", "Configure gallery");
        settings.setAction(() -> Minecraft.getInstance().gui.setScreen(new ScreenshotViewerConfigScreen(Minecraft.getInstance().gui.screen())));
        module.addOption(settings);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots.NativeScreenshotsProbe.tick();
            while (instance.openScreenshotsScreenKey.consumeClick()) {
                Screen screen = client.gui.screen();
                if (active() && (screen == null || screen instanceof TitleScreen || screen instanceof PauseScreen)) open(screen);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> addEntry(screen));
    }
    public static boolean available() { return instance != null; }
    public static boolean active() { return available() && NativeQualityOfLife.enabled("BetterScreenshots"); }
    public static boolean open(Screen parent) {
        if (!active()) return false;
        instance.thumbnailManager.configUpdated();
        Minecraft.getInstance().gui.setScreen(new ManageScreenshotsScreen(parent));
        return true;
    }
    /** Also used by the placement editor on its preview screen. */
    public static void addEntry(Screen screen) {
        if (!active() || !(screen instanceof TitleScreen || screen instanceof PauseScreen)) return;
        boolean title = screen instanceof TitleScreen;
        if (!instance.config.getOrFallback(title ? ScreenshotViewerOptions.SHOW_BUTTON_ON_TITLE_SCREEN : ScreenshotViewerOptions.SHOW_BUTTON_IN_GAME_PAUSE_MENU, true)) return;
        var buttons = Screens.getWidgets(screen);
        if (buttons.stream().anyMatch(IconButtonWidget.class::isInstance)) return;
        AbstractWidget reference = buttons.isEmpty() ? null : buttons.getFirst();
        var position = instance.config.get(title ? ScreenshotViewerOptions.TITLE_SCREEN_BUTTON_POSITION : ScreenshotViewerOptions.PAUSE_MENU_BUTTON_POSITION);
        int x = position.map(WidgetPositionOption.WidgetPosition::x).orElse(title ? screen.width - 28 : (reference == null ? screen.width / 2 + 108 : reference.getWidth() + 4));
        int y = position.map(WidgetPositionOption.WidgetPosition::y).orElse(title ? 8 : 0);
        if (reference != null && (position.isPresent() || !title)) { x += reference.getX(); y += reference.getY(); }
        IconButtonWidget button = new IconButtonWidget(Math.clamp(x, 0, Math.max(0, screen.width - 20)), Math.clamp(y, 0, Math.max(0, screen.height - 20)),
            20, 20, ScreenshotViewerTexts.MANAGE_SCREENSHOTS, SCREENSHOT_VIEWER_ICON, ignored -> open(screen));
        button.setTooltip(Tooltip.create(ScreenshotViewerTexts.MANAGE_SCREENSHOTS));
        buttons.add(button);
    }
    public CatConfig getConfig() { return config; }
    public ScreenshotThumbnailManager getThumbnailManager() { return thumbnailManager; }
    public KeyMapping getOpenScreenshotsScreenKey() { return openScreenshotsScreenKey; }
    public static ScreenshotViewer getInstance() { return instance; }
}
