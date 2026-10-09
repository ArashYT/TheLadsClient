package com.thelads.core.v1_20_1.feature;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.v1_20_1.gui.DraggableHudScreen1201;
import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import com.thelads.core.v1_20_1.gui.ServerDiscoveryScreen1201;
import com.thelads.core.v1_20_1.gui.SkinChangerScreen1201;
import com.thelads.core.v1_20_1.gui.WorldSourcesScreen1201;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: with a ".lads-qa-capture-menus" file in the game folder (optionally listing scenes, comma separated), opens each Lads menu
 * through its real screen class and the real buttons, waits for it to render, and saves the actual framebuffer to
 * screenshots/menus-qa/&lt;scene&gt;.png. Scenes that need a world (pause, hud-editor, settings:module:Fullbright, ...) come last and
 * run in a flat creative world "LadsMenusQA" in the sandbox. The log ends with "Lads menus QA END: n passed, m failed".
 */
public final class MenusQa1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");
    private static final List<String> DEFAULT_SCENES = List.of("title", "more", "switch", "account", "quit", "settings", "settings:HUD",
        "settings:Gameplay", "settings:Performance", "settings:Server", "settings:mods", "settings:colors", "options", "video", "keybinds",
        "skin", "skinchanger", "multiplayer", "discover", "singleplayer", "folders",
        "pause", "hud-editor", "settings:module:Fullbright");
    private static List<String> scenes;
    private static int index = -1, frames, passed, failed;
    private static long sceneStart, worldReadyAt, worldWaitFrom;
    private static boolean running, captured, opened, worldRequested, ownWorld, envUsed;
    private static Screen shown;
    private static Path out;

    private MenusQa1201() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MenusQa1201::tick);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (running) ScreenEvents.afterRender(screen).register((s, graphics, mx, my, dt) -> frame(client, s));
        });
    }

    private static boolean needsWorld(String scene) {
        return scene.equals("pause") || scene.equals("hud-editor") || scene.contains("module:");
    }

    private static void tick(Minecraft mc) {
        if (!running) {
            Path request = mc.gameDirectory.toPath().resolve(".lads-qa-capture-menus");
            // The scene list comes from a marker file in the game folder, or from the LADS_QA_MENUS environment variable (a harness
            // that rebuilds the game folder, like Zero SMP's, keeps no marker); the variable is read once.
            String fromEnv = envUsed ? null : System.getenv("LADS_QA_MENUS");
            boolean marker = Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS);
            if ((!marker && (fromEnv == null || fromEnv.isBlank())) || mc.getOverlay() != null) return;
            try {
                String text = marker ? Files.readString(request).strip() : fromEnv.strip();
                ownWorld = text.contains("own-world");
                List<String> listed = Arrays.stream(text.split("[,\\s]+")).filter(s -> !s.isBlank() && !s.equals("own-world")).toList();
                List<String> wanted = listed.isEmpty() ? DEFAULT_SCENES : listed;
                // Title scenes start on the title screen; a list of world scenes only starts in the world (the harness opens it).
                boolean inWorld = mc.level != null && mc.player != null && mc.screen == null;
                boolean anyTitle = wanted.stream().anyMatch(s -> !needsWorld(s));
                if (anyTitle ? !(mc.screen instanceof TitleScreen) : !inWorld && !ownWorld) return;
                if (marker) Files.delete(request);
                else envUsed = true;
                scenes = new ArrayList<>(wanted.stream().filter(s -> !needsWorld(s)).toList());
                scenes.addAll(wanted.stream().filter(MenusQa1201::needsWorld).toList());
                out = mc.gameDirectory.toPath().resolve("screenshots/menus-qa");
                Files.createDirectories(out);
            } catch (Exception failure) {
                LOGGER.error("Lads menus QA: cannot start", failure);
                return;
            }
            running = true;
            index = -1;
            LOGGER.info("Lads menus QA BEGIN: {}", scenes);
        }
        if (index >= 0 && captured) {
            next(mc);
            return;
        }
        if (index >= 0 && System.currentTimeMillis() - sceneStart > 40_000L) {
            fail(mc, "timed out waiting for the screen to render");
            return;
        }
        if (index < 0 || opened) {
            if (index < 0) next(mc);
            return;
        }
        // The scene's screen is requested but not yet shown (a world is loading).
        open(mc);
    }

    private static void next(Minecraft mc) {
        index++;
        captured = false;
        opened = false;
        frames = 0;
        sceneStart = System.currentTimeMillis();
        if (index >= scenes.size()) {
            LOGGER.info("Lads menus QA END: {} passed, {} failed", passed, failed);
            running = false;
            // Hand the game back as the harness expects it: on the title screen, or in the world without a screen.
            if (mc.level == null) mc.setScreen(new TitleScreen());
            else mc.setScreen(null);
            return;
        }
        open(mc);
    }

    private static void fail(Minecraft mc, String why) {
        failed++;
        LOGGER.error("Lads menus QA scene {} FAILED: {}", scenes.get(index), why);
        captured = true;
        next(mc);
    }

    private static boolean press(Screen screen, String label) {
        for (var child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getString().equals(label) && button.active) {
                button.onPress();
                return true;
            }
        }
        return false;
    }

    private static void open(Minecraft mc) {
        String scene = scenes.get(index);
        // A scene named "<scene>-nobatch" draws the Lads artwork call by call (no drawManaged), to compare with the batched path.
        boolean unbatched = scene.endsWith("-nobatch");
        com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201.noBatch = unbatched;
        if (unbatched) scene = scene.substring(0, scene.length() - "-nobatch".length());
        try {
            if (needsWorld(scene)) {
                if (mc.level == null || mc.player == null) {
                    if (ownWorld) requestWorld(mc);
                    if (worldWaitFrom == 0) worldWaitFrom = System.currentTimeMillis();
                    if (System.currentTimeMillis() - worldWaitFrom > 300_000L) {
                        fail(mc, "no world to photograph this scene in");
                        return;
                    }
                    sceneStart = System.currentTimeMillis(); // the world (opened by the harness or by us) gets its own time
                    return;
                }
                if (mc.screen != null && !(mc.screen instanceof PauseScreen) && !(mc.screen instanceof DraggableHudScreen1201)
                    && !(mc.screen instanceof LadsSettingsScreen1201)) mc.setScreen(null);
                if (worldReadyAt == 0) worldReadyAt = System.currentTimeMillis();
                if (System.currentTimeMillis() - worldReadyAt < 4000L) return; // let the terrain and the HUD settle
            } else if (mc.level != null) {
                return;
            }
            Screen title = new TitleScreen();
            switch (scene) {
                case "title" -> mc.setScreen(title);
                case "more" -> openFromTitle(mc, "More...");
                case "switch" -> openFromTitle(mc, "Switch Versions");
                case "account" -> openFromTitle(mc, "Switch");
                case "quit" -> openFromTitle(mc, net.minecraft.network.chat.Component.translatable("menu.quit").getString());
                case "settings" -> openFromTitle(mc, "Lads Mods");
                case "options" -> mc.setScreen(new OptionsScreen(title, mc.options));
                case "video" -> com.thelads.core.v1_20_1.gui.VideoSettingsRelay1201.open(title);
                case "keybinds" -> mc.setScreen(new KeyBindsScreen(title, mc.options));
                case "skin" -> mc.setScreen(new SkinCustomizationScreen(title, mc.options));
                case "skinchanger" -> mc.setScreen(new SkinChangerScreen1201(title));
                case "multiplayer" -> mc.setScreen(new JoinMultiplayerScreen(title));
                case "discover" -> mc.setScreen(new ServerDiscoveryScreen1201(title));
                case "singleplayer" -> mc.setScreen(new SelectWorldScreen(title));
                case "folders" -> mc.setScreen(new WorldSourcesScreen1201(title));
                case "pause" -> mc.setScreen(new PauseScreen(true));
                case "hud-editor" -> mc.setScreen(new DraggableHudScreen1201(null));
                default -> {
                    if (scene.startsWith("settings:")) {
                        LadsSettingsScreen1201 settings = new LadsSettingsScreen1201(needsWorld(scene) ? null : title);
                        mc.setScreen(settings);
                        String what = scene.substring("settings:".length());
                        if (what.equals("mods")) settings.openMods();
                        else if (what.equals("colors")) settings.openGlobalColors();
                        else if (what.startsWith("module:")) settings.openModule(what.substring("module:".length()));
                        else {
                            pendingClick = "category:" + what;
                            pendingClickFrames = 4;
                        }
                    } else throw new IllegalArgumentException("unknown scene " + scene);
                }
            }
            opened = true;
            frames = 0;
            sceneStart = System.currentTimeMillis();
        } catch (Throwable failure) {
            LOGGER.error("Lads menus QA scene {} threw", scene, failure);
            failed++;
            captured = true;
        }
    }

    private static void openFromTitle(Minecraft mc, String button) {
        Screen title = new TitleScreen();
        mc.setScreen(title);
        // The title lays itself out on its first rendered frame; press once it exists.
        pendingPress = button;
        pendingFrames = 5;
        pendingTitle = title;
    }

    private static String pendingPress, pendingClick;
    private static int pendingClickFrames;
    private static int pendingFrames;
    private static Screen pendingTitle;

    private static void frame(Minecraft mc, Screen screen) {
        if (!running || captured || index < 0 || index >= scenes.size()) return;
        if (pendingPress != null && screen == pendingTitle) {
            if (--pendingFrames > 0) return;
            String label = pendingPress;
            pendingPress = null;
            if (!press(screen, label)) fail(mc, "no title button \"" + label + "\"");
            frames = 0;
            return;
        }
        if (pendingPress != null) return;
        if (!opened) return;
        if (pendingClick != null && screen instanceof LadsSettingsScreen1201 settings && --pendingClickFrames <= 0) {
            // The common menu lays out on its first rendered frames; the category button has bounds by now.
            var rect = settings.ui().controlBounds(pendingClick);
            pendingClick = null;
            if (rect != null) {
                settings.ui().mouseClicked(rect.x() + 3, rect.y() + 3, 0);
                settings.ui().mouseReleased(rect.x() + 3, rect.y() + 3, 0);
            }
            frames = 0;
            return;
        }
        frames++;
        long elapsed = System.currentTimeMillis() - sceneStart;
        if (frames < 45 || elapsed < 1500L) return;
        captured = true;
        String scene = scenes.get(index);
        try {
            NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            Path file = out.resolve(scene.replace(':', '-') + ".png");
            String shownScreen = screen.getClass().getSimpleName();
            writeWidgets(screen, out.resolve(scene.replace(':', '-') + ".txt"));
            Util.ioPool().execute(() -> {
                try (image) {
                    image.writeToFile(file);
                    LOGGER.info("Lads menus QA scene {} ok: {} ({}x{}, screen {})", scene, file, image.getWidth(), image.getHeight(), shownScreen);
                } catch (Exception failure) {
                    LOGGER.error("Lads menus QA scene {} could not save", scene, failure);
                }
            });
            passed++;
        } catch (Throwable failure) {
            failed++;
            LOGGER.error("Lads menus QA scene {} could not capture", scene, failure);
        }
    }

    /** Every widget of the screen as drawn: class, text, bounds, active (evidence for layout questions). */
    private static void writeWidgets(Screen screen, Path file) {
        try {
            var lines = new ArrayList<String>();
            lines.add("screen " + screen.getClass().getName() + " " + screen.width + "x" + screen.height);
            for (var child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    lines.add(widget.getClass().getName() + " | " + widget.getMessage().getString() + " | " + widget.getX() + "," + widget.getY() + " "
                        + widget.getWidth() + "x" + widget.getHeight() + " | active=" + widget.active + " visible=" + widget.visible);
                } else lines.add(child.getClass().getName());
            }
            Files.write(file, lines);
        } catch (Exception ignored) {}
    }

    private static void requestWorld(Minecraft mc) {
        if (worldRequested) return;
        worldRequested = true;
        try {
            if (mc.getLevelSource().levelExists("LadsMenusQA")) {
                mc.createWorldOpenFlows().loadLevel(new TitleScreen(), "LadsMenusQA");
            } else {
                var settings = new LevelSettings("LadsMenusQA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("LadsMenusQA", settings, new WorldOptions(1234L, false, false), access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
        } catch (Throwable failure) {
            LOGGER.error("Lads menus QA: cannot open the QA world", failure);
            worldRequested = false;
            fail(mc, "world failed");
        }
    }
}
