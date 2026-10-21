package com.thelads.core.v1_8_9.gui;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.OptiFine189Module;
import com.thelads.core.v1_8_9.feature.OptiFineShaderLink189;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.world.WorldServer;

/**
 * OptiFine 1.8.9 settings GUI:
 * One unified screen with sub-pages:
 * - Details
 * - Animations
 * - Quality
 * - Performance
 * - Other
 * Drawn using the existing Lads dark GUI style, with instant option cycling,
 * zero-allocation hot paths, tooltips on hover, and full config persistence.
 */
public class GuiOptiFineSettings189 extends GuiScreen {
    public enum SubPage {
        DETAILS("Details"),
        ANIMATIONS("Animations"),
        QUALITY("Quality"),
        PERFORMANCE("Performance"),
        OTHER("Other");

        private final String title;
        SubPage(String title) { this.title = title; }
        public String getTitle() { return title; }
    }

    private final GuiScreen parent;
    private SubPage currentSubPage;
    private final OptiFine189Module module;
    private final Map<GuiButton, List<String>> tooltips = new HashMap<>();

    // Button IDs
    private static final int ID_DONE = 200;
    private static final int ID_TAB_DETAILS = 301;
    private static final int ID_TAB_ANIM = 302;
    private static final int ID_TAB_QUALITY = 303;
    private static final int ID_TAB_PERF = 304;
    private static final int ID_TAB_OTHER = 305;

    private static final int ID_ALL_ON = 401;
    private static final int ID_ALL_OFF = 402;
    private static final int ID_RESET_DEFAULTS = 403;

    public GuiOptiFineSettings189(GuiScreen parent, SubPage initialPage) {
        this.parent = parent;
        this.currentSubPage = initialPage != null ? initialPage : SubPage.DETAILS;
        OptiFine189Module m = OptiFine189Module.getInstance();
        if (m == null) {
            Module mod = ModuleManager.getInstance().getModule(OptiFine189Module.NAME);
            m = mod instanceof OptiFine189Module ? (OptiFine189Module) mod : new OptiFine189Module();
        }
        this.module = m;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        tooltips.clear();

        // 1. Top tabs
        int tabWidth = 60;
        int tabSpacing = 2;
        int totalTabsWidth = 5 * tabWidth + 4 * tabSpacing;
        int tabStartX = (this.width - totalTabsWidth) / 2;
        int tabY = 12;

        addTabButton(ID_TAB_DETAILS, tabStartX, tabY, tabWidth, 20, "Details", SubPage.DETAILS);
        addTabButton(ID_TAB_ANIM, tabStartX + (tabWidth + tabSpacing), tabY, tabWidth, 20, "Anim", SubPage.ANIMATIONS);
        addTabButton(ID_TAB_QUALITY, tabStartX + 2 * (tabWidth + tabSpacing), tabY, tabWidth, 20, "Quality", SubPage.QUALITY);
        addTabButton(ID_TAB_PERF, tabStartX + 3 * (tabWidth + tabSpacing), tabY, tabWidth, 20, "Perf", SubPage.PERFORMANCE);
        addTabButton(ID_TAB_OTHER, tabStartX + 4 * (tabWidth + tabSpacing), tabY, tabWidth, 20, "Other", SubPage.OTHER);

        // 2. Sub-page options
        switch (currentSubPage) {
            case DETAILS:
                initDetailsPage();
                break;
            case ANIMATIONS:
                initAnimationsPage();
                break;
            case QUALITY:
                initQualityPage();
                break;
            case PERFORMANCE:
                initPerformancePage();
                break;
            case OTHER:
                initOtherPage();
                break;
        }

        // 3. Bottom Done button
        buttonList.add(new GuiButton(ID_DONE, this.width / 2 - 100, this.height - 27, 200, 20, I18n.format("gui.done")));
    }

    private void addTabButton(int id, int x, int y, int w, int h, String text, SubPage page) {
        GuiButton btn = new GuiButton(id, x, y, w, h, text);
        btn.enabled = (currentSubPage != page);
        buttonList.add(btn);
    }

    // ==========================================
    // DETAILS PAGE
    // ==========================================
    private void initDetailsPage() {
        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int startY = 38;
        int rowH = 24;

        // Row 0: Clouds & Show Capes
        addOptionButton(501, leftX, startY, 150, 20, getCloudsLabel(), () -> {
            mc.gameSettings.setOptionValue(GameSettings.Options.RENDER_CLOUDS, 1);
            mc.gameSettings.saveOptions();
        }, "Clouds", "Fast - lower quality, faster", "Fancy - higher quality, slower", "OFF - no clouds, fastest");

        addOptionButton(502, rightX, startY, 150, 20, getCapesLabel(), () -> {
            mc.gameSettings.switchModelPartEnabled(EnumPlayerModelParts.CAPE);
            mc.gameSettings.saveOptions();
        }, "Show Capes", "Toggles visibility of your player cape");

        // Row 1: Trees & Rain / Snow
        addOptionButton(503, leftX, startY + rowH, 150, 20, "Trees: " + module.ofTrees.getValue(), () -> {
            module.ofTrees.setIndex((module.ofTrees.getIndex() + 1) % 4);
            module.sync();
            ConfigManager.saveLater();
            mc.renderGlobal.loadRenderers();
        }, "Trees", "Fast - opaque leaf blocks (faster)", "Fancy - transparent leaf blocks (looks better)", "Smart - fancy nearby, fast far away");

        addOptionButton(504, rightX, startY + rowH, 150, 20, "Rain & Snow: " + module.ofRain.getValue(), () -> {
            module.ofRain.setIndex((module.ofRain.getIndex() + 1) % 4);
            module.sync();
            ConfigManager.saveLater();
        }, "Rain & Snow", "Fast - minimal precipitation particles", "Fancy - full precipitation rendering", "OFF - disables rain and snow display");

        // Row 2: Sky & Stars
        addOptionButton(505, leftX, startY + 2 * rowH, 150, 20, "Sky: " + boolText(module.ofSky.get()), () -> {
            module.ofSky.set(!module.ofSky.get());
            module.sync();
            ConfigManager.saveLater();
        }, "Sky", "ON - sky dome is visible", "OFF - sky dome is hidden (boosts FPS)");

        addOptionButton(506, rightX, startY + 2 * rowH, 150, 20, "Stars: " + boolText(module.ofStars.get()), () -> {
            module.ofStars.set(!module.ofStars.get());
            module.sync();
            ConfigManager.saveLater();
        }, "Stars", "ON - night sky stars are visible", "OFF - stars are hidden");

        // Row 3: Sun & Moon, Held Item Tooltips
        addOptionButton(507, leftX, startY + 3 * rowH, 150, 20, "Sun & Moon: " + boolText(module.ofSunMoon.get()), () -> {
            module.ofSunMoon.set(!module.ofSunMoon.get());
            module.sync();
            ConfigManager.saveLater();
        }, "Sun & Moon", "ON - sun and moon are visible", "OFF - sun and moon are hidden");

        addOptionButton(508, rightX, startY + 3 * rowH, 150, 20, "Held Tooltips: " + boolText(mc.gameSettings.heldItemTooltips), () -> {
            mc.gameSettings.heldItemTooltips = !mc.gameSettings.heldItemTooltips;
            mc.gameSettings.saveOptions();
        }, "Held Item Tooltips", "Shows held item name above hotbar when switching slots");

        // Row 4: Fog & Fog Start
        addOptionButton(509, leftX, startY + 4 * rowH, 150, 20, "Fog: " + module.ofFogType.getValue(), () -> {
            module.ofFogType.setIndex((module.ofFogType.getIndex() + 1) % 3);
            module.sync();
            ConfigManager.saveLater();
        }, "Fog", "Fast - faster exponential fog", "Fancy - realistic linear fog", "OFF - no fog, maximum visibility and FPS");

        addOptionButton(510, rightX, startY + 4 * rowH, 150, 20, "Fog Start: " + String.format("%.1f", module.ofFogStart.getValue()), () -> {
            double cur = module.ofFogStart.getValue();
            cur += 0.2;
            if (cur > 0.85) cur = 0.2;
            module.ofFogStart.setValue(Math.round(cur * 10.0) / 10.0);
            module.sync();
            ConfigManager.saveLater();
        }, "Fog Start", "Distance multiplier where fog begins", "0.2 - near player", "0.8 - far from player");

        // Row 5: Dropped Items & Vignette
        addOptionButton(511, leftX, startY + 5 * rowH, 150, 20, "Dropped Items: " + module.ofDroppedItems.getValue(), () -> {
            module.ofDroppedItems.setIndex((module.ofDroppedItems.getIndex() + 1) % 3);
            module.sync();
            ConfigManager.saveLater();
        }, "Dropped Items", "Fast - 2D billboard items (faster)", "Fancy - 3D rotating items (looks better)");

        addOptionButton(512, rightX, startY + 5 * rowH, 150, 20, "Vignette: " + module.ofVignette.getValue(), () -> {
            module.ofVignette.setIndex((module.ofVignette.getIndex() + 1) % 3);
            module.sync();
            ConfigManager.saveLater();
        }, "Vignette", "Fast - disabled screen edge darkening", "Fancy - smooth dark edge overlay");

        // Row 6: Entity Shadows
        addOptionButton(513, leftX, startY + 6 * rowH, 150, 20, "Entity Shadows: " + boolText(mc.gameSettings.entityShadows), () -> {
            mc.gameSettings.entityShadows = !mc.gameSettings.entityShadows;
            mc.gameSettings.saveOptions();
        }, "Entity Shadows", "Toggles round shadows underneath players and mobs");
    }

    // ==========================================
    // ANIMATIONS PAGE (3 Columns)
    // ==========================================
    private void initAnimationsPage() {
        int colW = 100;
        int colSpacing = 4;
        int totalW = 3 * colW + 2 * colSpacing;
        int startX = (this.width - totalW) / 2;
        int startY = 36;
        int rowH = 21;

        // Column 0
        addAnimButton(601, startX, startY, colW, 19, "Water", module.animWater);
        addAnimButton(602, startX, startY + rowH, colW, 19, "Lava", module.animLava);
        addAnimButton(603, startX, startY + 2 * rowH, colW, 19, "Fire", module.animFire);
        addAnimButton(604, startX, startY + 3 * rowH, colW, 19, "Portal", module.animPortal);
        addAnimButton(605, startX, startY + 4 * rowH, colW, 19, "Redstone", module.animRedstone);
        addAnimButton(606, startX, startY + 5 * rowH, colW, 19, "Explosion", module.animExplosion);

        // Column 1
        int c1X = startX + colW + colSpacing;
        addAnimButton(607, c1X, startY, colW, 19, "Flame", module.animFlame);
        addAnimButton(608, c1X, startY + rowH, colW, 19, "Smoke", module.animSmoke);
        addAnimButton(609, c1X, startY + 2 * rowH, colW, 19, "Void", module.animVoidParticles);
        addAnimButton(610, c1X, startY + 3 * rowH, colW, 19, "Water Part.", module.animWaterParticles);
        addAnimButton(611, c1X, startY + 4 * rowH, colW, 19, "Rain Splash", module.animRainSplash);
        addAnimButton(612, c1X, startY + 5 * rowH, colW, 19, "Portal Part.", module.animPortalParticles);

        // Column 2
        int c2X = startX + 2 * (colW + colSpacing);
        addAnimButton(613, c2X, startY, colW, 19, "Potion Part.", module.animPotionParticles);
        addAnimButton(614, c2X, startY + rowH, colW, 19, "Drip Water", module.animDrippingWaterLava);
        addAnimButton(615, c2X, startY + 2 * rowH, colW, 19, "Terrain", module.animTerrain);
        addAnimButton(616, c2X, startY + 3 * rowH, colW, 19, "Textures", module.animTextures);
        addAnimButton(617, c2X, startY + 4 * rowH, colW, 19, "Firework", module.animFirework);

        // Vanilla particles setting
        addOptionButton(618, c2X, startY + 5 * rowH, colW, 19, "Part: " + getParticlesLabel(), () -> {
            mc.gameSettings.setOptionValue(GameSettings.Options.PARTICLES, 1);
            mc.gameSettings.saveOptions();
        }, "Particles", "Master particle density: All, Decreased, or Minimal");

        // Bulk action buttons above Done
        int bulkY = this.height - 48;
        buttonList.add(new GuiButton(ID_ALL_ON, this.width / 2 - 105, bulkY, 100, 18, "All ON"));
        buttonList.add(new GuiButton(ID_ALL_OFF, this.width / 2 + 5, bulkY, 100, 18, "All OFF"));
    }

    private void addAnimButton(int id, int x, int y, int w, int h, String label, com.thelads.core.config.BoolOption opt) {
        addOptionButton(id, x, y, w, h, label + ": " + boolText(opt.get()), () -> {
            opt.set(!opt.get());
            module.sync();
            ConfigManager.saveLater();
        }, label + " Animation", "Toggles dynamic animation for " + label);
    }

    // ==========================================
    // QUALITY PAGE
    // ==========================================
    private void initQualityPage() {
        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int startY = 38;
        int rowH = 24;

        // Row 0: Mipmap Levels & Clear Water
        addOptionButton(701, leftX, startY, 150, 20, "Mipmap Levels: " + mc.gameSettings.mipmapLevels, () -> {
            mc.gameSettings.setOptionValue(GameSettings.Options.MIPMAP_LEVELS, 1);
            mc.gameSettings.saveOptions();
            mc.renderGlobal.loadRenderers();
        }, "Mipmap Levels", "Visual smoothing for distant textures (0 to 4)");

        addOptionButton(702, rightX, startY, 150, 20, "Clear Water: " + boolText(module.ofClearWater.get()), () -> {
            module.ofClearWater.set(!module.ofClearWater.get());
            module.sync();
            ConfigManager.saveLater();
        }, "Clear Water", "Disables murky underwater fog for crystal clear underwater vision");

        // Row 1: Smooth Biomes
        addOptionButton(703, leftX, startY + rowH, 150, 20, "Smooth Biomes: " + boolText(module.ofSmoothBiomes.get()), () -> {
            module.ofSmoothBiomes.set(!module.ofSmoothBiomes.get());
            module.sync();
            ConfigManager.saveLater();
            mc.renderGlobal.loadRenderers();
        }, "Smooth Biomes", "ON - blends color transitions smoothly across biomes", "OFF - single-sample color, faster world meshing");
    }

    // ==========================================
    // PERFORMANCE PAGE
    // ==========================================
    private void initPerformancePage() {
        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int startY = 38;
        int rowH = 24;

        // Row 0: Smooth FPS & Fast Render
        addOptionButton(801, leftX, startY, 150, 20, "Smooth FPS: " + boolText(module.ofSmoothFps.get()), () -> {
            module.ofSmoothFps.set(!module.ofSmoothFps.get());
            module.sync();
            ConfigManager.saveLater();
        }, "Smooth FPS", "Flushes the GPU buffer queue to remove frame pacing stutter");

        addOptionButton(802, rightX, startY, 150, 20, "Fast Render: " + boolText(OptiFineShaderLink189.isFastRenderOn()), () -> {
            boolean on = !OptiFineShaderLink189.isFastRenderOn();
            OptiFineShaderLink189.setFastRender(on);
            mc.renderGlobal.loadRenderers();
        }, "Fast Render", "Hardware accelerated chunk rendering path (powered by Ionium / VBOs)");

        // Row 1: Chunk Updates & Dynamic Updates
        addOptionButton(803, leftX, startY + rowH, 150, 20, "Chunk Updates: " + (int) module.ofChunkUpdates.getValue(), () -> {
            double cur = module.ofChunkUpdates.getValue() + 1.0;
            if (cur > 5.0) cur = 1.0;
            module.ofChunkUpdates.setValue(cur);
            module.sync();
            ConfigManager.saveLater();
        }, "Chunk Updates", "Chunk updates uploaded per frame", "1 - smoother FPS", "3..5 - faster loading when flying");

        addOptionButton(804, rightX, startY + rowH, 150, 20, "Dynamic Updates: " + boolText(module.ofDynamicUpdates.get()), () -> {
            module.ofDynamicUpdates.set(!module.ofDynamicUpdates.get());
            module.sync();
            ConfigManager.saveLater();
        }, "Dynamic Updates", "Loads more chunks per frame when the player stands still");
    }

    // ==========================================
    // OTHER PAGE
    // ==========================================
    private void initOtherPage() {
        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int startY = 38;
        int rowH = 24;

        // Row 0: Lagometer & Profiler
        addOptionButton(901, leftX, startY, 150, 20, "Lagometer: " + boolText(mc.gameSettings.showLagometer), () -> {
            mc.gameSettings.showLagometer = !mc.gameSettings.showLagometer;
            mc.gameSettings.saveOptions();
        }, "Lagometer", "Frame time graph overlay at bottom of screen (toggled with Alt+F3)");

        addOptionButton(902, rightX, startY, 150, 20, "Profiler: " + boolText(mc.gameSettings.showDebugProfilerChart), () -> {
            mc.gameSettings.showDebugProfilerChart = !mc.gameSettings.showDebugProfilerChart;
            mc.gameSettings.saveOptions();
        }, "Debug Profiler", "Performance pie chart breakdown (toggled with Shift+F3)");

        // Row 1: Weather & Time (Singleplayer only)
        addOptionButton(903, leftX, startY + rowH, 150, 20, "Weather: " + (mc.isIntegratedServerRunning() ? boolText(module.ofWeather.get()) : "SP Only"), () -> {
            if (mc.isIntegratedServerRunning()) {
                module.ofWeather.set(!module.ofWeather.get());
                module.sync();
                WorldServer ws = mc.getIntegratedServer().worldServerForDimension(0);
                if (ws != null) {
                    ws.getWorldInfo().setRaining(!module.ofWeather.get());
                }
            }
        }, "Weather", "Toggles rain and storming on/off (singleplayer only)");

        addOptionButton(904, rightX, startY + rowH, 150, 20, "Time: " + (mc.isIntegratedServerRunning() ? module.ofTime.getValue() : "SP Only"), () -> {
            if (mc.isIntegratedServerRunning()) {
                module.ofTime.setIndex((module.ofTime.getIndex() + 1) % 3);
                module.sync();
                WorldServer ws = mc.getIntegratedServer().worldServerForDimension(0);
                if (ws != null) {
                    int t = module.ofTime.getIndex();
                    if (t == 1) ws.setWorldTime(1000); // Day
                    else if (t == 2) ws.setWorldTime(14000); // Night
                }
            }
        }, "Time", "Locks world time: Default, Day Only, or Night Only (singleplayer only)");

        // Row 2: Fullscreen & 3D Anaglyph
        addOptionButton(905, leftX, startY + 2 * rowH, 150, 20, "Fullscreen: " + boolText(mc.isFullScreen()), () -> {
            mc.toggleFullscreen();
        }, "Fullscreen", "Toggles full screen display mode (F11)");

        addOptionButton(906, rightX, startY + 2 * rowH, 150, 20, "3D Anaglyph: " + boolText(mc.gameSettings.anaglyph), () -> {
            mc.gameSettings.setOptionValue(GameSettings.Options.ANAGLYPH, 1);
            mc.gameSettings.saveOptions();
            mc.renderGlobal.loadRenderers();
        }, "3D Anaglyph", "Stereoscopic 3D rendering for red-cyan glasses");

        // Row 3: Show FPS & Show GL Errors
        addOptionButton(907, leftX, startY + 3 * rowH, 150, 20, "Show FPS: " + boolText(isFpsHudEnabled()), () -> {
            Module fps = ModuleManager.getInstance().getModule("FPS");
            if (fps != null) {
                fps.setEnabled(!fps.isEnabled());
                ConfigManager.saveLater();
            }
        }, "Show FPS", "Shows your current framerate counter directly on screen");

        addOptionButton(908, rightX, startY + 3 * rowH, 150, 20, "GL Errors: " + boolText(module.ofShowGlErrors.get()), () -> {
            module.ofShowGlErrors.set(!module.ofShowGlErrors.get());
            module.sync();
            ConfigManager.saveLater();
        }, "Show GL Errors", "Shows OpenGL error codes in game chat when detected");

        // Row 4: Reset Video Settings
        addOptionButton(ID_RESET_DEFAULTS, this.width / 2 - 100, startY + 4 * rowH + 6, 200, 20, "Reset Video Settings...", () -> {
            module.resetDefaults();
            ConfigManager.saveLater();
            initGui();
        }, "Reset Video Settings", "Restores all OptiFine settings to their default values");
    }

    // ==========================================
    // HELPERS & ACTION HANDLING
    // ==========================================
    private static class ActionButton extends GuiButton {
        final Runnable action;
        ActionButton(int id, int x, int y, int w, int h, String text, Runnable action) {
            super(id, x, y, w, h, text);
            this.action = action;
        }
    }

    private void addOptionButton(int id, int x, int y, int w, int h, String label, Runnable action, String title, String... lines) {
        ActionButton btn = new ActionButton(id, x, y, w, h, label, action);
        buttonList.add(btn);
        List<String> list = new ArrayList<>();
        list.add("§e" + title);
        for (String line : lines) {
            list.add("§7" + line);
        }
        tooltips.put(btn, list);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) return;

        if (button.id == ID_DONE) {
            mc.displayGuiScreen(parent);
            return;
        }

        // Tab switches
        if (button.id == ID_TAB_DETAILS) {
            currentSubPage = SubPage.DETAILS;
            initGui();
            return;
        }
        if (button.id == ID_TAB_ANIM) {
            currentSubPage = SubPage.ANIMATIONS;
            initGui();
            return;
        }
        if (button.id == ID_TAB_QUALITY) {
            currentSubPage = SubPage.QUALITY;
            initGui();
            return;
        }
        if (button.id == ID_TAB_PERF) {
            currentSubPage = SubPage.PERFORMANCE;
            initGui();
            return;
        }
        if (button.id == ID_TAB_OTHER) {
            currentSubPage = SubPage.OTHER;
            initGui();
            return;
        }

        // Bulk animations
        if (button.id == ID_ALL_ON) {
            module.setAllAnimations(true);
            ConfigManager.saveLater();
            initGui();
            return;
        }
        if (button.id == ID_ALL_OFF) {
            module.setAllAnimations(false);
            ConfigManager.saveLater();
            initGui();
            return;
        }

        // Custom action buttons
        if (button instanceof ActionButton) {
            ((ActionButton) button).action.run();
            initGui();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "OptiFine: " + currentSubPage.getTitle() + " Settings", this.width / 2, 2, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Render hovered button tooltip
        for (GuiButton btn : buttonList) {
            if (btn.isMouseOver() && tooltips.containsKey(btn)) {
                drawHoveringText(tooltips.get(btn), mouseX, mouseY);
                break;
            }
        }
    }

    private static String boolText(boolean val) {
        return val ? "§aON" : "§cOFF";
    }

    private String getCloudsLabel() {
        int clouds = mc.gameSettings.clouds;
        String val = clouds == 0 ? "OFF" : clouds == 1 ? "Fast" : "Fancy";
        return "Clouds: " + val;
    }

    private String getCapesLabel() {
        boolean on = mc.gameSettings.getModelParts().contains(EnumPlayerModelParts.CAPE);
        return "Show Capes: " + boolText(on);
    }

    private String getParticlesLabel() {
        int p = mc.gameSettings.particleSetting;
        return p == 0 ? "All" : p == 1 ? "Decreased" : "Minimal";
    }

    private boolean isFpsHudEnabled() {
        Module fps = ModuleManager.getInstance().getModule("FPS");
        return fps != null && fps.isEnabled();
    }
}
